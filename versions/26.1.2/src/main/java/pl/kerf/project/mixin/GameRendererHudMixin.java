package pl.kerf.project.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.pip.GuiEntityRenderer;
import net.minecraft.client.gui.render.pip.GuiSkinRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.kerf.project.render.HudCache;

/**
 * HUD Caching: HUD gry zbiera się co 50 ms do osobnego stanu (HudCache), a zebrany rysuje się do tekstury tuż przed GUI klatki,
 * przez osobny renderer GUI (własny stan, bufory i atlas przedmiotów): podwójne użycie głównego powodowało miganie HUD-u.
 */
@Mixin(GameRenderer.class)
public class GameRendererHudMixin {
	@Shadow
	@Final
	private Minecraft minecraft;
	@Shadow
	@Final
	private RenderBuffers renderBuffers;
	@Shadow
	@Final
	private FogRenderer fogRenderer;
	@Shadow
	@Final
	private FeatureRenderDispatcher featureRenderDispatcher;
	@Shadow
	@Final
	private SubmitNodeStorage submitNodeStorage;

	@Unique
	private GuiRenderer aio$cacheRenderer;

	@WrapOperation(method = "extractGui", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"))
	private void aio$cachedHud(Gui gui, GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, Operation<Void> original) {
		HudCache.extract(this.minecraft, graphics, target -> original.call(gui, target, deltaTracker));
	}

	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V"))
	private void aio$renderHudCache(CallbackInfo ci) {
		HudCache.renderPending(() -> {
			if (this.aio$cacheRenderer == null) {
				MultiBufferSource.BufferSource bufferSource = this.renderBuffers.bufferSource();
				this.aio$cacheRenderer = new GuiRenderer(HudCache.STATE, bufferSource, this.submitNodeStorage, this.featureRenderDispatcher,
					List.of(new GuiEntityRenderer(bufferSource, this.minecraft.getEntityRenderDispatcher()), new GuiSkinRenderer(bufferSource)));
			}

			this.aio$cacheRenderer.render(this.fogRenderer.getBuffer(FogRenderer.FogMode.NONE));
			this.aio$cacheRenderer.endFrame();
		});
	}
}
