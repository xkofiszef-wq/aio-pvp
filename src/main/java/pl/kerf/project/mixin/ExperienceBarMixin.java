package pl.kerf.project.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
//? if <26.2 {
import net.minecraft.client.gui.contextualbar.ExperienceBarRenderer;
//?} else {
/*import net.minecraft.client.gui.contextualbar.ExperienceBar;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.kerf.project.module.Modules;
import pl.kerf.project.render.LocatorBarHud;

/** Locator Bar z opcją "Na pasku XP": ikony waypointów rysują się na pasku doświadczenia. */
//? if <26.2 {
@Mixin(ExperienceBarRenderer.class)
//?} else {
/*@Mixin(ExperienceBar.class)
*///?}
public class ExperienceBarMixin {
	//? if <26.1.2 {
	@Inject(method = "render", at = @At("HEAD"))
	private void aio$locatorOnXp(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
	//?} else {
	/*@Inject(method = "extractRenderState", at = @At("HEAD"))
	private void aio$locatorOnXp(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
	*///?}
		Minecraft client = Minecraft.getInstance();
		if (Modules.LOCATOR_BAR.on() && Modules.LOCATOR_BAR.xpBar.on() && client.player != null
			&& client.player.connection.getWaypointManager().hasWaypoints()) {
			//? if <26.1.2 {
			LocatorBarHud.draw(context, tickCounter, client);
			//?} else {
			/*LocatorBarHud.draw(graphics, deltaTracker, client);
			*///?}
		}
	}
}
