package pl.kerf.project.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.bar.ExperienceBar;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.kerf.project.module.Modules;
import pl.kerf.project.render.LocatorBarHud;

/** Locator Bar z opcją "Na pasku XP": ikony waypointów rysują się na pasku doświadczenia. */
@Mixin(ExperienceBar.class)
public class ExperienceBarMixin {
	@Inject(method = "renderAddons", at = @At("HEAD"))
	private void aio$locatorOnXp(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (Modules.LOCATOR_BAR.on() && Modules.LOCATOR_BAR.xpBar.on() && client.player != null
			&& client.player.networkHandler.getWaypointHandler().hasWaypoint()) {
			LocatorBarHud.draw(context, tickCounter, client);
		}
	}
}
