package pl.kerf.project.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.kerf.project.module.Modules;

@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin {
	private static final String FIRST_PERSON = "renderFirstPersonItem";
	private static final String RENDER_ITEM =
		"Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V";

	/** Vanilla chowa ręce podczas wiosłowania; Boat Item View to wyłącza (tylko wygląd, używanie przedmiotów zostaje bez zmian). */
	@ModifyExpressionValue(method = "updateHeldItems", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isRiding()Z"))
	private boolean aio$keepItemsInBoat(boolean riding) {
		if (!riding || !Modules.BOAT_VIEW.on()) {
			return riding;
		}

		if (Modules.BOAT_VIEW.mapsOnly.on()) {
			ClientPlayerEntity player = MinecraftClient.getInstance().player;
			boolean holdsMap = player != null
				&& (player.getMainHandStack().contains(DataComponentTypes.MAP_ID) || player.getOffHandStack().contains(DataComponentTypes.MAP_ID));
			return !holdsMap;
		}

		return false;
	}

	/** Invisible Hand: vanilla pomija rękę przy niewidzialności; z funkcją rysuje się ją (przezroczystą). */
	@ModifyExpressionValue(
		method = {"renderMapInOneHand", "renderMapInBothHands"},
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isInvisible()Z")
	)
	private boolean aio$showMapHand(boolean invisible) {
		return invisible && !Modules.INVISIBLE_HAND.on();
	}

	@ModifyExpressionValue(method = FIRST_PERSON, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;isInvisible()Z"))
	private boolean aio$showHand(boolean invisible) {
		return invisible && !Modules.INVISIBLE_HAND.on();
	}

	private static Modules.HandTransform aio$transform(ItemStack item, Hand hand) {
		if (item.isOf(Items.SHIELD) && Modules.SHIELD.position()) {
			return Modules.SHIELD.transform;
		}

		if (Modules.VIEW_MODEL.on()) {
			return hand == Hand.MAIN_HAND ? Modules.VIEW_MODEL.main : Modules.VIEW_MODEL.off;
		}

		return null;
	}

	@Inject(method = FIRST_PERSON, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;push()V", shift = At.Shift.AFTER))
	private void aio$offsetItem(
		AbstractClientPlayerEntity player, float tickProgress, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress,
		MatrixStack matrices, OrderedRenderCommandQueue queue, int light, CallbackInfo ci
	) {
		Modules.HandTransform transform = aio$transform(item, hand);
		if (transform != null) {
			Arm arm = hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
			// X jest lustrzany dla lewej ręki, żeby dodatnia wartość zawsze znaczyła "na zewnątrz".
			int side = arm == Arm.RIGHT ? 1 : -1;
			matrices.translate(side * transform.x.f(), transform.y.f(), transform.z.f());
		}
	}

	/** 1.7 Animations: blok mieczem = miecz w głównej ręce, a w drugiej blokuje tarcza (tylko wygląd, blokuje dalej tarcza). */
	private static boolean aio$swordBlocking(AbstractClientPlayerEntity player) {
		return Modules.OLD_ANIMATIONS.on() && Modules.OLD_ANIMATIONS.swordBlock.on() && player.isUsingItem()
			&& player.getActiveHand() == Hand.OFF_HAND && player.getActiveItem().contains(DataComponentTypes.BLOCKS_ATTACKS)
			&& player.getMainHandStack().isIn(ItemTags.SWORDS);
	}

	/** 1.7 Animations: przy bloku mieczem tarcza w drugiej ręce się nie rysuje (w 1.7 blokowało się samym mieczem). */
	@Inject(method = FIRST_PERSON, at = @At("HEAD"), cancellable = true)
	private void aio$hideBlockingShield(
		AbstractClientPlayerEntity player, float tickProgress, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress,
		MatrixStack matrices, OrderedRenderCommandQueue queue, int light, CallbackInfo ci
	) {
		if (hand == Hand.OFF_HAND && aio$swordBlocking(player)) {
			ci.cancel();
		}
	}

	/**
	 * 1.7 Animations: poza bloku miecza. Vanilla doszła już do pozycji ręki i machnięcia (jak 1.8 transformFirstPersonItem),
	 * dokładamy obrót bloku; to te same liczby, których gra używa dla blokujących przedmiotów innych niż tarcza.
	 */
	@Inject(method = FIRST_PERSON, at = @At(value = "INVOKE", target = RENDER_ITEM))
	private void aio$swordBlockPose(
		AbstractClientPlayerEntity player, float tickProgress, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress,
		MatrixStack matrices, OrderedRenderCommandQueue queue, int light, CallbackInfo ci
	) {
		if (hand == Hand.MAIN_HAND && item.isIn(ItemTags.SWORDS) && aio$swordBlocking(player)) {
			int side = player.getMainArm() == Arm.RIGHT ? 1 : -1;
			matrices.translate(side * -0.14142136F, 0.08F, 0.14142136F);
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-102.25F));
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 13.365F));
			matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * 78.05F));
		}
	}

	/** 1.7 Animations: przedmiot nie opada po ciosie (od 1.9 opada razem z ładowaniem ataku); przy zmianie slotu dalej tak. */
	@ModifyExpressionValue(method = "updateHeldItems", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getHandEquippingProgress(F)F"))
	private float aio$noAttackDip(float progress) {
		return Modules.OLD_ANIMATIONS.on() && Modules.OLD_ANIMATIONS.noAttackDip.on() ? 1.0F : progress;
	}

	/** Skala tuż przed narysowaniem modelu, żeby nie przesuwała przedmiotu względem ręki. */
	@Inject(method = FIRST_PERSON, at = @At(value = "INVOKE", target = RENDER_ITEM))
	private void aio$scaleItem(
		AbstractClientPlayerEntity player, float tickProgress, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress,
		MatrixStack matrices, OrderedRenderCommandQueue queue, int light, CallbackInfo ci
	) {
		Modules.HandTransform transform = aio$transform(item, hand);
		if (transform != null) {
			float scale = transform.scale.f();
			matrices.scale(scale, scale, scale);
		}
	}
}
