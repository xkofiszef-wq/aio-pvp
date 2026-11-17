package pl.kerf.project.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import pl.kerf.project.module.Modules;

/** Logika rąk w pierwszej osobie (26.3 wydzieliło ją z renderera). */
@Mixin(FirstPersonHandsAndItems.class)
public class HandsTickMixin {
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

	/** 1.7 Animations: przedmiot nie opada po ciosie (od 1.9 opada razem z ładowaniem ataku); przy zmianie slotu dalej tak. */
	@ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getItemSwapScale(F)F"))
	private float aio$noAttackDip(float progress) {
		return Modules.OLD_ANIMATIONS.on() && Modules.OLD_ANIMATIONS.noAttackDip.on() ? 1.0F : progress;
	}
}
