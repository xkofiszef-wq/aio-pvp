package pl.kerf.project.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.contextualbar.LocatorBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.kerf.project.module.Modules;
import pl.kerf.project.render.LocatorBarHud;

/** Locator Bar: ikony waypointów rysuje mod (głowy, obwódki, nicki, kierunek). */
@Mixin(LocatorBar.class)
public class LocatorBarMixin {
	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void aio$draw(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		if (Modules.LOCATOR_BAR.on()) {
			LocatorBarHud.draw(graphics, deltaTracker, Minecraft.getInstance());
			ci.cancel();
		}
	}
}
