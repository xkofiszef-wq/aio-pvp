//? if >=26.2 {
/*package pl.kerf.project.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import pl.kerf.project.render.HudCache;

/^* HUD Caching: HUD gry zbiera się co 50 ms do osobnego stanu, a w GUI zostaje gotowa tekstura (HudCache). ^/
@Mixin(Gui.class)
public class GuiHudCacheMixin {
	@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;extractRenderState(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V"))
	private void aio$cachedHud(Hud hud, GuiGraphics graphics, DeltaTracker deltaTracker, Operation<Void> original) {
		HudCache.extract(Minecraft.getInstance(), graphics, target -> original.call(hud, target, deltaTracker));
	}
}
*///?}
