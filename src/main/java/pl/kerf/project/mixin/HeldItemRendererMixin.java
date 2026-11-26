package pl.kerf.project.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
//? if <26.3 {
import net.minecraft.client.player.AbstractClientPlayer;
//?}
import net.minecraft.client.player.LocalPlayer;
//? if <26.3 {
import net.minecraft.client.renderer.ItemInHandRenderer;
//?} else {
/*import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
*///?}
import net.minecraft.client.renderer.SubmitNodeCollector;
//? if >=26.3 {
/*import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
*///?}
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

//? if <26.3 {
@Mixin(ItemInHandRenderer.class)
//?} else {
/*/^* 26.3: rysowanie rąk w pierwszej osobie jest osobno (ten renderer), a ich logika (tick) w FirstPersonHandsAndItems, patrz HandsTickMixin. ^/
@Mixin(FirstPersonHandsAndItemsRenderer.class)
*///?}
public class HeldItemRendererMixin {
	//? if <26.1.2 {
	private static final String FIRST_PERSON = "renderArmWithItem";
	private static final String RENDER_ITEM =
		"Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V";
	//?}
	//? if >=26.2 {
	/*private static final String FIRST_PERSON = "submitArmWithItem";
	*///?}
	//? if =26.1.2 {
	/*private static final String FIRST_PERSON = "renderArmWithItem";
	*///?}
	//? if <26.3 {

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
	//?} else {
	/*private static final String ITEM_SUBMIT = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V";
	*///?}

	/** Invisible Hand: vanilla pomija rękę przy niewidzialności; z funkcją rysuje się ją (przezroczystą). */
	@ModifyExpressionValue(
		//? if <26.3 {
		method = {"renderOneHandedMap", "renderTwoHandedMap"},
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isInvisible()Z")
		//?} else {
		/*method = {"renderOneHandedMap", "renderTwoHandedMap", FIRST_PERSON},
		at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;isInvisible:Z")
		*///?}
	)
	//? if <26.3 {
	private boolean aio$showMapHand(boolean invisible) {
		return invisible && !Modules.INVISIBLE_HAND.on();
	}

	@ModifyExpressionValue(method = FIRST_PERSON, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;isInvisible()Z"))
	//?}
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

	//? if >=26.3 {
	/*private static HumanoidArm aio$arm(PlayerRenderState player, InteractionHand hand) {
		HumanoidArm main = player.avatarRenderState.mainArm;
		return hand == InteractionHand.MAIN_HAND ? main : main.getOpposite();
	}

	*///?}
	@Inject(method = FIRST_PERSON, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER))
	private void aio$offsetItem(
		//? if <26.3 {
		AbstractClientPlayer player, float tickProgress, float pitch, InteractionHand hand, float swingProgress, ItemStack item, float equipProgress,
		PoseStack matrices, SubmitNodeCollector queue, int light, CallbackInfo ci
		//?} else {
		/*PlayerRenderState player, FirstPersonHandsAndItemsRenderState state, float tickProgress, float pitch, InteractionHand hand, float swingProgress,
		ItemStack item, float equipProgress, PoseStack matrices, SubmitNodeCollector queue, int light, CallbackInfo ci
		*///?}
	) {
		Modules.HandTransform transform = aio$transform(item, hand);
		if (transform != null) {
			//? if <26.3 {
			HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
			//?}
			// X jest lustrzany dla lewej ręki, żeby dodatnia wartość zawsze znaczyła "na zewnątrz".
			//? if <26.3 {
			int side = arm == HumanoidArm.RIGHT ? 1 : -1;
			//?} else {
			/*int side = aio$arm(player, hand) == HumanoidArm.RIGHT ? 1 : -1;
			*///?}
			matrices.translate(side * transform.x.f(), transform.y.f(), transform.z.f());
		}
	}

	/** 1.7 Animations: blok mieczem = miecz w głównej ręce, a w drugiej blokuje tarcza (tylko wygląd, blokuje dalej tarcza). */
	//? if <26.3 {
	private static boolean aio$swordBlocking(AbstractClientPlayer player) {
		return Modules.OLD_ANIMATIONS.on() && Modules.OLD_ANIMATIONS.swordBlock.on() && player.isUsingItem()
	//?} else {
	/*private static boolean aio$swordBlocking() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && Modules.OLD_ANIMATIONS.on() && Modules.OLD_ANIMATIONS.swordBlock.on() && player.isUsingItem()
	*///?}
			&& player.getUsedItemHand() == InteractionHand.OFF_HAND && player.getUseItem().has(DataComponents.BLOCKS_ATTACKS)
			&& player.getMainHandItem().is(ItemTags.SWORDS);
	}

	/** 1.7 Animations: przy bloku mieczem tarcza w drugiej ręce się nie rysuje (w 1.7 blokowało się samym mieczem). */
	@Inject(method = FIRST_PERSON, at = @At("HEAD"), cancellable = true)
	private void aio$hideBlockingShield(
		//? if <26.3 {
		AbstractClientPlayer player, float tickProgress, float pitch, InteractionHand hand, float swingProgress, ItemStack item, float equipProgress,
		PoseStack matrices, SubmitNodeCollector queue, int light, CallbackInfo ci
		//?} else {
		/*PlayerRenderState player, FirstPersonHandsAndItemsRenderState state, float tickProgress, float pitch, InteractionHand hand, float swingProgress,
		ItemStack item, float equipProgress, PoseStack matrices, SubmitNodeCollector queue, int light, CallbackInfo ci
		*///?}
	) {
		//? if <26.3 {
		if (hand == InteractionHand.OFF_HAND && aio$swordBlocking(player)) {
		//?} else {
		/*if (hand == InteractionHand.OFF_HAND && aio$swordBlocking()) {
		*///?}
			ci.cancel();
		}
	}

	/**
	 * 1.7 Animations: poza bloku miecza. Vanilla doszła już do pozycji ręki i machnięcia (jak 1.8 transformFirstPersonItem),
	 * dokładamy obrót bloku; to te same liczby, których gra używa dla blokujących przedmiotów innych niż tarcza.
	 */
	//? if <26.3 {
	@Inject(method = FIRST_PERSON, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"))
	//?} else {
	/*@Inject(method = FIRST_PERSON, at = @At(value = "INVOKE", target = ITEM_SUBMIT))
	*///?}
	private void aio$swordBlockPose(
		//? if <26.3 {
		AbstractClientPlayer player, float tickProgress, float pitch, InteractionHand hand, float swingProgress, ItemStack item, float equipProgress,
		PoseStack matrices, SubmitNodeCollector queue, int light, CallbackInfo ci
		//?} else {
		/*PlayerRenderState player, FirstPersonHandsAndItemsRenderState state, float tickProgress, float pitch, InteractionHand hand, float swingProgress,
		ItemStack item, float equipProgress, PoseStack matrices, SubmitNodeCollector queue, int light, CallbackInfo ci
		*///?}
	) {
		//? if <26.3 {
		if (hand == InteractionHand.MAIN_HAND && item.is(ItemTags.SWORDS) && aio$swordBlocking(player)) {
			int side = player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
		//?} else {
		/*if (hand == InteractionHand.MAIN_HAND && item.is(ItemTags.SWORDS) && aio$swordBlocking()) {
			int side = aio$arm(player, hand) == HumanoidArm.RIGHT ? 1 : -1;
		*///?}
			matrices.translate(side * -0.14142136F, 0.08F, 0.14142136F);
			//? if <26.3 {
			matrices.mulPose(Axis.XP.rotationDegrees(-102.25F));
			matrices.mulPose(Axis.YP.rotationDegrees(side * 13.365F));
			matrices.mulPose(Axis.ZP.rotationDegrees(side * 78.05F));
			//?} else {
			/*matrices.rotateDegrees(Axis.XP, -102.25F);
			matrices.rotateDegrees(Axis.YP, side * 13.365F);
			matrices.rotateDegrees(Axis.ZP, side * 78.05F);
			*///?}
		}
	}

	//? if <26.3 {
	/** 1.7 Animations: przedmiot nie opada po ciosie (od 1.9 opada razem z ładowaniem ataku); przy zmianie slotu dalej tak. */
	@ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getItemSwapScale(F)F"))
	private float aio$noAttackDip(float progress) {
		return Modules.OLD_ANIMATIONS.on() && Modules.OLD_ANIMATIONS.noAttackDip.on() ? 1.0F : progress;
	}

	//?}
	/** Skala tuż przed narysowaniem modelu, żeby nie przesuwała przedmiotu względem ręki. */
	//? if <26.3 {
	@Inject(method = FIRST_PERSON, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"))
	//?} else {
	/*@Inject(method = FIRST_PERSON, at = @At(value = "INVOKE", target = ITEM_SUBMIT))
	*///?}
	private void aio$scaleItem(
		//? if <26.3 {
		AbstractClientPlayer player, float tickProgress, float pitch, InteractionHand hand, float swingProgress, ItemStack item, float equipProgress,
		PoseStack matrices, SubmitNodeCollector queue, int light, CallbackInfo ci
		//?} else {
		/*PlayerRenderState player, FirstPersonHandsAndItemsRenderState state, float tickProgress, float pitch, InteractionHand hand, float swingProgress,
		ItemStack item, float equipProgress, PoseStack matrices, SubmitNodeCollector queue, int light, CallbackInfo ci
		*///?}
	) {
		Modules.HandTransform transform = aio$transform(item, hand);
		if (transform != null) {
			float scale = transform.scale.f();
			matrices.scale(scale, scale, scale);
		}
	}
}
