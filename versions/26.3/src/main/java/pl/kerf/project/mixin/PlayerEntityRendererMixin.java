package pl.kerf.project.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.kerf.project.module.Modules;
import pl.kerf.project.render.HealthLabel;
import pl.kerf.project.render.LivingStateExt;

@Mixin(AvatarRenderer.class)
public class PlayerEntityRendererMixin {
	/**
	 * Shield Fix (MC-238293): poza używania (tarcza, łuk, kusza, trójząb, luneta) rysuje się tylko gdy
	 * getItemUseTimeLeft() > 0. Gracz śledzony od połowy używania ma ten licznik = 0 (stan nie dosłany przy
	 * dołączeniu), choć flaga "używa" jest ustawiona. Wtedy udajemy dodatni licznik, żeby poza się pokazała.
	 */
	@ModifyExpressionValue(
		method = "getArmPose(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/client/model/HumanoidModel$ArmPose;",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Avatar;getUseItemRemainingTicks()I")
	)
	private static int aio$fixUseAnim(int useTimeLeft, Avatar player, ItemStack stack, InteractionHand hand) {
		if (useTimeLeft <= 0 && Modules.SHIELD.animFix() && player.isUsingItem() && player.getUsedItemHand() == hand) {
			return 1;
		}

		return useTimeLeft;
	}

	/**
	 * Shield Delay Fix: tarcza realnie blokuje dopiero po block_delay (vanilla 5 ticków), ale pozę bloku vanilla
	 * pokazuje od razu. Dopóki nie minął delay, pokazujemy zwykłe trzymanie (ITEM), nie blok. Pomija wymuszony
	 * przypadek z MC-238293 (itemUseTimeLeft == 0), bo tam gracz blokuje już od dawna.
	 */
	@ModifyReturnValue(
		method = "getArmPose(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/client/model/HumanoidModel$ArmPose;",
		at = @At("RETURN")
	)
	private static HumanoidModel.ArmPose aio$shieldDelay(HumanoidModel.ArmPose pose, Avatar player, ItemStack stack, InteractionHand hand) {
		if (pose == HumanoidModel.ArmPose.BLOCK && Modules.SHIELD.delayFix() && player.getUseItemRemainingTicks() > 0) {
			BlocksAttacks blocks = stack.get(DataComponents.BLOCKS_ATTACKS);
			int delay = blocks != null ? blocks.blockDelayTicks() : 5;
			if (player.getTicksUsingItem() < delay) {
				return HumanoidModel.ArmPose.ITEM;
			}
		}

		return pose;
	}
	/** Dodatkowa etykieta ze zdrowiem, w tej samej macierzy co nick (więc uwzględnia wiersz punktacji pod nickiem). */
	@Inject(
		method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V")
	)
	private void aio$healthLabel(AvatarRenderState state, PoseStack matrices, SubmitNodeCollector queue, CameraRenderState camera, CallbackInfo ci) {
		Modules.Health module = Modules.HEALTH;
		if (!module.on() || state.nameTag == null
			|| state.distanceToCameraSq > module.distance.get() * module.distance.get()) {
			return;
		}

		LivingStateExt ext = (LivingStateExt) state;
		// Etykiety mają oś Y w dół. W 26.x gra liczy od dołu: wiersz pod nickiem (scoreText, np. wynik albo "NPC")
		// jest w pozycji 0, a nick o wiersz wyżej. Serca "nad" idą wiersz nad nickiem, "pod" - wiersz pod najniższą etykietą.
		int above = state.scoreText != null ? -20 : -10;
		int y = (state.showExtraEars ? -10 : 0) + (module.position.is(0) ? above : 10) + module.offset.i();
		HealthLabel.submitting = true;
		try {
			queue.submitNameTag(
				matrices, state.nameTagAttachment, y, HealthLabel.build(ext.aio$health(), ext.aio$maxHealth(), ext.aio$absorption()),
				!state.isDiscrete, state.lightCoords, camera
			);
		} finally {
			HealthLabel.submitting = false;
		}
	}
}
