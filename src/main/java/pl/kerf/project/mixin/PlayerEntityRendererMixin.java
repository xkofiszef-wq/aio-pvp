package pl.kerf.project.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BlocksAttacksComponent;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.kerf.project.module.Modules;
import pl.kerf.project.render.HealthLabel;
import pl.kerf.project.render.LivingStateExt;

@Mixin(PlayerEntityRenderer.class)
public class PlayerEntityRendererMixin {
	/**
	 * Shield Fix (MC-238293): poza używania (tarcza, łuk, kusza, trójząb, luneta) rysuje się tylko gdy
	 * getItemUseTimeLeft() > 0. Gracz śledzony od połowy używania ma ten licznik = 0 (stan nie dosłany przy
	 * dołączeniu), choć flaga "używa" jest ustawiona. Wtedy udajemy dodatni licznik, żeby poza się pokazała.
	 */
	@ModifyExpressionValue(
		method = "getArmPose(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/Hand;)Lnet/minecraft/client/render/entity/model/BipedEntityModel$ArmPose;",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/PlayerLikeEntity;getItemUseTimeLeft()I")
	)
	private static int aio$fixUseAnim(int useTimeLeft, PlayerLikeEntity player, ItemStack stack, Hand hand) {
		if (useTimeLeft <= 0 && Modules.SHIELD.animFix() && player.isUsingItem() && player.getActiveHand() == hand) {
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
		method = "getArmPose(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/Hand;)Lnet/minecraft/client/render/entity/model/BipedEntityModel$ArmPose;",
		at = @At("RETURN")
	)
	private static BipedEntityModel.ArmPose aio$shieldDelay(BipedEntityModel.ArmPose pose, PlayerLikeEntity player, ItemStack stack, Hand hand) {
		if (pose == BipedEntityModel.ArmPose.BLOCK && Modules.SHIELD.delayFix() && player.getItemUseTimeLeft() > 0) {
			BlocksAttacksComponent blocks = stack.get(DataComponentTypes.BLOCKS_ATTACKS);
			int delay = blocks != null ? blocks.getBlockDelayTicks() : 5;
			if (player.getItemUseTime() < delay) {
				return BipedEntityModel.ArmPose.ITEM;
			}
		}

		return pose;
	}
	/** Dodatkowa etykieta ze zdrowiem, w tej samej macierzy co nick (więc uwzględnia wiersz punktacji pod nickiem). */
	@Inject(
		method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;pop()V")
	)
	private void aio$healthLabel(PlayerEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera, CallbackInfo ci) {
		Modules.Health module = Modules.HEALTH;
		if (!module.on() || state.displayName == null) {
			return;
		}

		LivingStateExt ext = (LivingStateExt) state;
		// Etykiety mają oś Y w dół: -10 to wiersz nad nickiem, +10 pod nim. Punktacja serwera pod nickiem
		// (state.playerName, np. "20 ❤") zajmuje wiersz pod nickiem, więc wtedy serca schodzą o wiersz niżej, pod nią.
		int below = state.playerName != null ? 20 : 10;
		int y = (state.extraEars ? -10 : 0) + (module.position.is(0) ? -10 : below) + module.offset.i();
		HealthLabel.submitting = true;
		try {
			queue.submitLabel(
				matrices, state.nameLabelPos, y, HealthLabel.build(ext.aio$health(), ext.aio$maxHealth(), ext.aio$absorption()),
				!state.sneaking, state.light, state.squaredDistanceToCamera, camera
			);
		} finally {
			HealthLabel.submitting = false;
		}
	}
}
