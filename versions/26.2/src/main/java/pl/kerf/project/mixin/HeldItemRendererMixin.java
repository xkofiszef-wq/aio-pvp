package pl.kerf.project.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.kerf.project.module.Modules;

@Mixin(ItemInHandRenderer.class)
public class HeldItemRendererMixin {
	private static final String FIRST_PERSON = "submitArmWithItem";

	/** Vanilla chowa ręce podczas wiosłowania; Boat Item View to wyłącza (tylko wygląd, używanie przedmiotów zostaje bez zmian). */
	@ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isHandsBusy()Z"))
	private boolean aio$keepItemsInBoat(boolean riding) {
		if (!riding || !Modules.BOAT_VIEW.on()) {
			return riding;
		}

		if (Modules.BOAT_VIEW.mapsOnly.on()) {
			LocalPlayer player = Minecraft.getInstance().player;
			boolean holdsMap = player != null
				&& (player.getMainHandItem().has(DataComponents.MAP_ID) || player.getOffhandItem().has(DataComponents.MAP_ID));
			return !holdsMap;
		}

		return false;
	}

	/** Invisible Hand: vanilla pomija rękę przy niewidzialności; z funkcją rysuje się ją (przezroczystą). */
	@ModifyExpressionValue(
		method = {"renderOneHandedMap", "renderTwoHandedMap"},
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isInvisible()Z")
	)
	private boolean aio$showMapHand(boolean invisible) {
		return invisible && !Modules.INVISIBLE_HAND.on();
	}

	@ModifyExpressionValue(method = FIRST_PERSON, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;isInvisible()Z"))
	private boolean aio$showHand(boolean invisible) {
		return invisible && !Modules.INVISIBLE_HAND.on();
	}

	private static Modules.HandTransform aio$transform(ItemStack item, InteractionHand hand) {
		if (item.is(Items.SHIELD) && Modules.SHIELD.position()) {
			return Modules.SHIELD.transform;
		}

		if (Modules.VIEW_MODEL.on()) {
			return hand == InteractionHand.MAIN_HAND ? Modules.VIEW_MODEL.main : Modules.VIEW_MODEL.off;
		}

		return null;
	}

	@Inject(method = FIRST_PERSON, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER))
	private void aio$offsetItem(
		AbstractClientPlayer player, float tickProgress, float pitch, InteractionHand hand, float swingProgress, ItemStack item, float equipProgress,
		PoseStack matrices, SubmitNodeCollector queue, int light, CallbackInfo ci
	) {
		Modules.HandTransform transform = aio$transform(item, hand);
		if (transform != null) {
			HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
			// X jest lustrzany dla lewej ręki, żeby dodatnia wartość zawsze znaczyła "na zewnątrz".
			int side = arm == HumanoidArm.RIGHT ? 1 : -1;
			matrices.translate(side * transform.x.f(), transform.y.f(), transform.z.f());
		}
	}

	/** 1.7 Animations: blok mieczem = miecz w głównej ręce, a w drugiej blokuje tarcza (tylko wygląd, blokuje dalej tarcza). */
	private static boolean aio$swordBlocking(AbstractClientPlayer player) {
		return Modules.OLD_ANIMATIONS.on() && Modules.OLD_ANIMATIONS.swordBlock.on() && player.isUsingItem()
			&& player.getUsedItemHand() == InteractionHand.OFF_HAND && player.getUseItem().has(DataComponents.BLOCKS_ATTACKS)
			&& player.getMainHandItem().is(ItemTags.SWORDS);
	}

	/** 1.7 Animations: przy bloku mieczem tarcza w drugiej ręce się nie rysuje (w 1.7 blokowało się samym mieczem). */
	@Inject(method = FIRST_PERSON, at = @At("HEAD"), cancellable = true)
	private void aio$hideBlockingShield(
		AbstractClientPlayer player, float tickProgress, float pitch, InteractionHand hand, float swingProgress, ItemStack item, float equipProgress,
		PoseStack matrices, SubmitNodeCollector queue, int light, CallbackInfo ci
	) {
		if (hand == InteractionHand.OFF_HAND && aio$swordBlocking(player)) {
			ci.cancel();
		}
	}

	/**
	 * 1.7 Animations: poza bloku miecza. Vanilla doszła już do pozycji ręki i machnięcia (jak 1.8 transformFirstPersonItem),
	 * dokładamy obrót bloku; to te same liczby, których gra używa dla blokujących przedmiotów innych niż tarcza.
	 */
	@Inject(method = FIRST_PERSON, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"))
	private void aio$swordBlockPose(
		AbstractClientPlayer player, float tickProgress, float pitch, InteractionHand hand, float swingProgress, ItemStack item, float equipProgress,
		PoseStack matrices, SubmitNodeCollector queue, int light, CallbackInfo ci
	) {
		if (hand == InteractionHand.MAIN_HAND && item.is(ItemTags.SWORDS) && aio$swordBlocking(player)) {
			int side = player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
			matrices.translate(side * -0.14142136F, 0.08F, 0.14142136F);
			matrices.mulPose(Axis.XP.rotationDegrees(-102.25F));
			matrices.mulPose(Axis.YP.rotationDegrees(side * 13.365F));
			matrices.mulPose(Axis.ZP.rotationDegrees(side * 78.05F));
		}
	}

	/** 1.7 Animations: przedmiot nie opada po ciosie (od 1.9 opada razem z ładowaniem ataku); przy zmianie slotu dalej tak. */
	@ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getItemSwapScale(F)F"))
	private float aio$noAttackDip(float progress) {
		return Modules.OLD_ANIMATIONS.on() && Modules.OLD_ANIMATIONS.noAttackDip.on() ? 1.0F : progress;
	}

	/** Skala tuż przed narysowaniem modelu, żeby nie przesuwała przedmiotu względem ręki. */
	@Inject(method = FIRST_PERSON, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"))
	private void aio$scaleItem(
		AbstractClientPlayer player, float tickProgress, float pitch, InteractionHand hand, float swingProgress, ItemStack item, float equipProgress,
		PoseStack matrices, SubmitNodeCollector queue, int light, CallbackInfo ci
	) {
		Modules.HandTransform transform = aio$transform(item, hand);
		if (transform != null) {
			float scale = transform.scale.f();
			matrices.scale(scale, scale, scale);
		}
	}
}
