package pl.kerf.project.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.kerf.project.module.Modules;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
	@Shadow
	private int itemUseCooldown;
	@Shadow
	public ClientPlayerEntity player;

	/**
	 * Fast XP: vanilla po każdym użyciu ustawia licznik na 4, czyli rzut co 4 ticki. Skracamy ją wyłącznie dla butelek XP;
	 * każdy rzut to nadal zwykłe użycie przedmiotu wysyłane do serwera.
	 */
	@Inject(method = "doItemUse", at = @At("RETURN"))
	private void aio$fastXp(CallbackInfo ci) {
		if (!Modules.FAST_XP.on() || this.player == null) {
			return;
		}

		ItemStack main = this.player.getMainHandStack();
		ItemStack off = this.player.getOffHandStack();
		// Offhand liczy się tylko, gdy main hand nie jest czymś, co samo by się "używało" (blok, jedzenie itp.).
		boolean throwing = main.isOf(Items.EXPERIENCE_BOTTLE) || off.isOf(Items.EXPERIENCE_BOTTLE) && (main.isEmpty() || main.isDamageable());
		if (throwing) {
			this.itemUseCooldown = Math.min(this.itemUseCooldown, Modules.FAST_XP.interval());
		}
	}
}
