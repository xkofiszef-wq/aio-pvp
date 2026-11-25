package pl.kerf.project.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.render.EntityGuiElementRenderer;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.PlayerSkinGuiElementRenderer;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import net.minecraft.client.render.command.RenderDispatcher;
import net.minecraft.client.render.fog.FogRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import pl.kerf.project.module.Modules;
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
	private MinecraftClient client;
	@Shadow
	@Final
	private FogRenderer fogRenderer;
	@Shadow
	@Final
	private OrderedRenderCommandQueueImpl orderedRenderCommandQueue;
	@Shadow
	@Final
	private RenderDispatcher entityRenderDispatcher;

	@Unique
	private GuiRenderState aio$cacheState;
	@Unique
	private GuiRenderer aio$cacheRenderer;

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V"))
	private void aio$cachedHud(InGameHud hud, DrawContext context, RenderTickCounter tickCounter, Operation<Void> original) {
		if (!HudCache.active(this.client)) {
			HudCache.release();
			original.call(hud, context, tickCounter);
			return;
		}

		if (this.aio$cacheRenderer == null) {
			VertexConsumerProvider.Immediate immediate = this.client.getBufferBuilders().getEntityVertexConsumers();
			this.aio$cacheState = new GuiRenderState();
			this.aio$cacheRenderer = new GuiRenderer(this.aio$cacheState, immediate, this.orderedRenderCommandQueue, this.entityRenderDispatcher,
				List.of(new EntityGuiElementRenderer(immediate, this.client.getEntityRenderDispatcher()), new PlayerSkinGuiElementRenderer(immediate)));
		}

		if (HudCache.needsRefresh(this.client.getWindow())) {
			HudCache.prepare(this.client.getWindow());
			this.aio$cacheState.clear();
			DrawContext cacheContext = new DrawContext(this.client, this.aio$cacheState, (int) this.client.mouse.getScaledX(this.client.getWindow()),
				(int) this.client.mouse.getScaledY(this.client.getWindow()));
			HudCache.capturing = true;
			try {
				original.call(hud, cacheContext, tickCounter);
			} finally {
				HudCache.capturing = false;
			}

			HudCache.redirecting = true;
			try {
				this.aio$cacheRenderer.render(this.fogRenderer.getFogBuffer(FogRenderer.FogType.NONE));
				this.aio$cacheRenderer.incrementFrame();
			} finally {
				HudCache.redirecting = false;
			}
		}

		HudCache.addQuad(context);
		Modules.MARKERS.render(context, this.client);
		Modules.PARTY.renderHeads(context, this.client);
	}
}
