package pl.kerf.project.mixin;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.pip.GuiEntityRenderer;
import net.minecraft.client.gui.render.pip.GuiSkinRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.kerf.project.render.HudCache;

/**
 * HUD Caching: zebrany HUD rysuje się do tekstury tuż przed GUI klatki, przez osobny renderer GUI (własny stan, bufory
 * i atlas przedmiotów), żeby nie ruszać głównego renderera: podwójne użycie tego samego powodowało miganie HUD-u.
 */
@Mixin(GameRenderer.class)
public class GameRendererHudMixin {
	@Shadow
	@Final
	private Minecraft minecraft;
	@Shadow
	@Final
	private FeatureRenderDispatcher featureRenderDispatcher;

	@Unique
	private GuiRenderer aio$cacheRenderer;

	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V"))
	private void aio$renderHudCache(CallbackInfo ci) {
		HudCache.renderPending(() -> {
			if (this.aio$cacheRenderer == null) {
				this.aio$cacheRenderer = new GuiRenderer(HudCache.STATE, this.featureRenderDispatcher,
					List.of(new GuiEntityRenderer(this.minecraft.getEntityRenderDispatcher()), new GuiSkinRenderer()));
			}

			this.aio$cacheRenderer.render();
			this.aio$cacheRenderer.endFrame();
		});
	}
}
