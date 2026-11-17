package pl.kerf.project.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.kerf.project.module.Modules;

@Mixin(Minecraft.class)
public class MinecraftClientMixin {
	@Shadow
	private int rightClickDelay;
	@Shadow
	public LocalPlayer player;

	/**
	 * Fast XP: vanilla po każdym użyciu ustawia licznik na 4, czyli rzut co 4 ticki. Skracamy ją wyłącznie dla butelek XP;
	 * każdy rzut to nadal zwykłe użycie przedmiotu wysyłane do serwera.
	 */
	@Inject(method = "startUseItem", at = @At("RETURN"))
	private void aio$fastXp(CallbackInfo ci) {
		if (!Modules.FAST_XP.on() || this.player == null) {
			return;
		}

		ItemStack main = this.player.getMainHandItem();
		ItemStack off = this.player.getOffhandItem();
		// Offhand liczy się tylko, gdy main hand nie jest czymś, co samo by się "używało" (blok, jedzenie itp.).
		boolean throwing = main.is(Items.EXPERIENCE_BOTTLE) || off.is(Items.EXPERIENCE_BOTTLE) && (main.isEmpty() || main.isDamageableItem());
		if (throwing) {
			this.rightClickDelay = Math.min(this.rightClickDelay, Modules.FAST_XP.interval());
		}
	}
}
