package pl.kerf.project.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
//? if <26.2 {
import net.minecraft.client.gui.contextualbar.LocatorBarRenderer;
//?} else {
/*import net.minecraft.client.gui.contextualbar.LocatorBar;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.kerf.project.module.Modules;
import pl.kerf.project.render.LocatorBarHud;

/** Locator Bar: ikony waypointów rysuje mod (głowy, obwódki, nicki, kierunek). */
//? if <26.2 {
@Mixin(LocatorBarRenderer.class)
//?} else {
/*@Mixin(LocatorBar.class)
*///?}
public class LocatorBarMixin {
	//? if <26.1.2 {
	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void aio$draw(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
	//?} else {
	/*@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void aio$draw(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
	*///?}
		if (Modules.LOCATOR_BAR.on()) {
			//? if <26.1.2 {
			LocatorBarHud.draw(context, tickCounter, Minecraft.getInstance());
			//?} else {
			/*LocatorBarHud.draw(graphics, deltaTracker, Minecraft.getInstance());
			*///?}
			ci.cancel();
		}
	}
}
