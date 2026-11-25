package pl.kerf.project.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.bar.LocatorBar;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.kerf.project.module.Modules;
import pl.kerf.project.render.LocatorBarHud;

/** Locator Bar: ikony waypointów rysuje mod (głowy, obwódki, nicki, kierunek). */
@Mixin(LocatorBar.class)
public class LocatorBarMixin {
	@Inject(method = "renderAddons", at = @At("HEAD"), cancellable = true)
	private void aio$draw(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
		if (Modules.LOCATOR_BAR.on()) {
			LocatorBarHud.draw(context, tickCounter, MinecraftClient.getInstance());
			ci.cancel();
		}
	}
}
