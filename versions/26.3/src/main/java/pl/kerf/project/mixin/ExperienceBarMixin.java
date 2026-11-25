package pl.kerf.project.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.contextualbar.ExperienceBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.kerf.project.module.Modules;
import pl.kerf.project.render.LocatorBarHud;

/** Locator Bar z opcją "Na pasku XP": ikony waypointów rysują się na pasku doświadczenia. */
@Mixin(ExperienceBar.class)
public class ExperienceBarMixin {
	@Inject(method = "extractRenderState", at = @At("HEAD"))
	private void aio$locatorOnXp(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		Minecraft client = Minecraft.getInstance();
		if (Modules.LOCATOR_BAR.on() && Modules.LOCATOR_BAR.xpBar.on() && client.player != null
			&& client.player.connection.getWaypointManager().hasWaypoints()) {
			LocatorBarHud.draw(graphics, deltaTracker, client);
		}
	}
}
