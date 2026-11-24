package pl.kerf.project.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.kerf.project.module.Modules;

@Mixin(LivingEntity.class)
public class LivingSwingMixin {
	/**
	 * Cooldown Fix (MC-218570): serwer przy machnięciu ręką zeruje ładowanie ataku (ServerPlayer.swingAndResetAttackStrength),
	 * a klient tylko przy ataku. Np. po zniszczeniu bloku wskaźnik pokazywał pełne naładowanie, a cios był słaby.
	 * W 26.3 machnięcie jest tylko w LivingEntity, więc sprawdzamy, czy to nasz gracz.
	 */
	@Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/component/SwingAnimation;Z)Z", at = @At("RETURN"))
	private void aio$syncCooldown(InteractionHand hand, SwingAnimation animation, boolean toSelf, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && Modules.COOLDOWN_FIX.on() && (Object) this instanceof LocalPlayer player) {
			player.resetOnlyAttackStrengthTicker();
		}
	}
}
