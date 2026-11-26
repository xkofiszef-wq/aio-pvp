package pl.kerf.project.mixin;

//? if <26.2 {
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
//?}
import java.util.List;
//? if <26.2 {
import net.minecraft.client.DeltaTracker;
//?}
import net.minecraft.client.Minecraft;
//? if <26.2 {
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
//?}
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.pip.GuiEntityRenderer;
import net.minecraft.client.gui.render.pip.GuiSkinRenderer;
//? if <26.1.2 {
import net.minecraft.client.gui.render.state.GuiRenderState;
//?}
import net.minecraft.client.renderer.GameRenderer;
//? if <26.2 {
import net.minecraft.client.renderer.MultiBufferSource;
//?}
//? if =26.1.2 {
/*import net.minecraft.client.renderer.RenderBuffers;
*///?}
//? if <26.2 {
import net.minecraft.client.renderer.SubmitNodeStorage;
//?}
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
//? if <26.2 {
import net.minecraft.client.renderer.fog.FogRenderer;
//?}
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
//? if <26.1.2 {
import pl.kerf.project.module.Modules;
//?} else {
/*import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}
import pl.kerf.project.render.HudCache;

/**
 * HUD Caching: HUD gry rysuje się do tekstury co 50 ms, a w pozostałych klatkach wyświetla się gotowa tekstura.
 * Rysowanie do tekstury idzie przez osobny renderer GUI (własny stan, bufory i atlas przedmiotów), żeby nie ruszać
 * renderera menu w środku klatki: podwójne użycie tego samego powodowało miganie elementów HUD-u.
 */
@Mixin(GameRenderer.class)
public class GameRendererHudMixin {
	@Shadow
	@Final
	private Minecraft minecraft;
	@Shadow
	@Final
	//? if =26.1.2 {
	/*private RenderBuffers renderBuffers;
	@Shadow
	@Final
	*///?}
	//? if <26.2 {
	private FogRenderer fogRenderer;
	@Shadow
	@Final
	//?}
	//? if <26.1.2 {
	private SubmitNodeStorage submitNodeStorage;
	//?}
	//? if =26.1.2 || =26.3 {
	/*private FeatureRenderDispatcher featureRenderDispatcher;
	*///?}
	//? if <26.2 {
	@Shadow
	@Final
	//?}
	//? if =1.21.11 || =26.2 {
	private FeatureRenderDispatcher featureRenderDispatcher;
	//?}
	//? if =26.1.2 {
	/*private SubmitNodeStorage submitNodeStorage;
	*///?}

	//? if =1.21.11 || =26.2 {
	@Unique
	//?}
	//? if <26.1.2 {
	private GuiRenderState aio$cacheState;
	//?}
	//? if =1.21.11 || =26.1.2 || =26.3 {
	@Unique
	//?}
	private GuiRenderer aio$cacheRenderer;

	//? if <26.1.2 {
	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V"))
	private void aio$cachedHud(Gui hud, GuiGraphics context, DeltaTracker tickCounter, Operation<Void> original) {
		if (!HudCache.active(this.minecraft)) {
			HudCache.release();
			original.call(hud, context, tickCounter);
			return;
		}
	//?}
	//? if =26.1.2 {
	/*@WrapOperation(method = "extractGui", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;extractRenderState(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V"))
	private void aio$cachedHud(Gui gui, GuiGraphics graphics, DeltaTracker deltaTracker, Operation<Void> original) {
		HudCache.extract(this.minecraft, graphics, target -> original.call(gui, target, deltaTracker));
	}
	*///?}

		//? if <26.1.2 {
		if (this.aio$cacheRenderer == null) {
			MultiBufferSource.BufferSource immediate = this.minecraft.renderBuffers().bufferSource();
			this.aio$cacheState = new GuiRenderState();
			this.aio$cacheRenderer = new GuiRenderer(this.aio$cacheState, immediate, this.submitNodeStorage, this.featureRenderDispatcher,
				List.of(new GuiEntityRenderer(immediate, this.minecraft.getEntityRenderDispatcher()), new GuiSkinRenderer(immediate)));
		}

		if (HudCache.needsRefresh(this.minecraft.getWindow())) {
			HudCache.prepare(this.minecraft.getWindow());
			this.aio$cacheState.reset();
			GuiGraphics cacheContext = new GuiGraphics(this.minecraft, this.aio$cacheState, (int) this.minecraft.mouseHandler.getScaledXPos(this.minecraft.getWindow()),
				(int) this.minecraft.mouseHandler.getScaledYPos(this.minecraft.getWindow()));
			HudCache.capturing = true;
			try {
				original.call(hud, cacheContext, tickCounter);
			} finally {
				HudCache.capturing = false;
		//?}
	//? if >=26.2 {
	/*@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V"))
	*///?}
	//? if =26.1.2 {
	/*@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V"))
	*///?}
	//? if >=26.1.2 {
	/*private void aio$renderHudCache(CallbackInfo ci) {
		HudCache.renderPending(() -> {
			if (this.aio$cacheRenderer == null) {
	*///?}
				//? if >=26.2 {
				/*this.aio$cacheRenderer = new GuiRenderer(HudCache.STATE, this.featureRenderDispatcher,
					List.of(new GuiEntityRenderer(this.minecraft.getEntityRenderDispatcher()), new GuiSkinRenderer()));
				*///?}
				//? if =26.1.2 {
				/*MultiBufferSource.BufferSource bufferSource = this.renderBuffers.bufferSource();
				this.aio$cacheRenderer = new GuiRenderer(HudCache.STATE, bufferSource, this.submitNodeStorage, this.featureRenderDispatcher,
					List.of(new GuiEntityRenderer(bufferSource, this.minecraft.getEntityRenderDispatcher()), new GuiSkinRenderer(bufferSource)));
				*///?}
			}

			//? if <26.1.2 {
			HudCache.redirecting = true;
			try {
				this.aio$cacheRenderer.render(this.fogRenderer.getBuffer(FogRenderer.FogMode.NONE));
				this.aio$cacheRenderer.incrementFrameNumber();
			} finally {
				HudCache.redirecting = false;
			}
		}

		HudCache.addQuad(context);
		Modules.MARKERS.render(context, this.minecraft);
		Modules.PARTY.renderHeads(context, this.minecraft);
			//?}
			//? if >=26.2 {
			/*this.aio$cacheRenderer.render();
			*///?}
			//? if =26.1.2 {
			/*this.aio$cacheRenderer.render(this.fogRenderer.getBuffer(FogRenderer.FogMode.NONE));
			*///?}
			//? if >=26.1.2 {
			/*this.aio$cacheRenderer.endFrame();
		});
			*///?}
	}
}
