package pl.kerf.project.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import pl.kerf.project.module.Modules;
import pl.kerf.project.render.HudCache;

/** HUD Caching: HUD gry rysuje się do tekstury co 50 ms, a w pozostałych klatkach wyświetla się gotowa tekstura. */
@Mixin(GameRenderer.class)
public class GameRendererHudMixin {
	@Shadow
	@Final
	private MinecraftClient client;
	@Shadow
	@Final
	private GuiRenderer guiRenderer;
	@Shadow
	@Final
	private FogRenderer fogRenderer;

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V"))
	private void aio$cachedHud(InGameHud hud, DrawContext context, RenderTickCounter tickCounter, Operation<Void> original) {
		if (!HudCache.active(this.client)) {
			HudCache.release();
			original.call(hud, context, tickCounter);
			return;
		}

		if (HudCache.needsRefresh(this.client.getWindow())) {
			HudCache.prepare(this.client.getWindow());
			HudCache.capturing = true;
			try {
				original.call(hud, context, tickCounter);
			} finally {
				HudCache.capturing = false;
			}

			// Zebrany HUD rysuje się od razu do tekstury, a stan rysowania jest czyszczony przed resztą klatki.
			HudCache.redirecting = true;
			try {
				this.guiRenderer.render(this.fogRenderer.getFogBuffer(FogRenderer.FogType.NONE));
				this.guiRenderer.incrementFrame();
			} finally {
				HudCache.redirecting = false;
			}

			context.state.clear();
		}

		HudCache.addQuad(context);
		Modules.MARKERS.render(context, this.client);
		Modules.PARTY.renderHeads(context, this.client);
	}
}
