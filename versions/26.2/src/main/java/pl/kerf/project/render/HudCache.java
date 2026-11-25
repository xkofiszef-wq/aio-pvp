package pl.kerf.project.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import pl.kerf.project.mixin.GuiGraphicsAccessor;
import pl.kerf.project.module.Modules;

/**
 * HUD Caching: HUD gry (z elementami moda) zbiera się i rysuje do osobnej tekstury najwyżej raz na 50 ms,
 * a w pozostałych klatkach do GUI trafia tylko gotowa tekstura. Znaczniki i głowy party dalej rysują się co klatkę.
 * Zbieranie idzie do własnego stanu GUI, a rysowanie przez osobny GuiRenderer (patrz GameRendererHudMixin).
 */
public final class HudCache {
	private static final long REFRESH_NANOS = 50_000_000L;
	/** Stan GUI, do którego zbiera się HUD przy odświeżeniu tekstury. */
	public static final GuiRenderState STATE = new GuiRenderState();
	private static TextureTarget cache;
	private static long last;
	/** Trwa zbieranie HUD-u do tekstury (elementy rysowane co klatkę pomijają się). */
	public static boolean capturing;
	/** GuiRenderer ma rysować do tekstury zamiast do głównego bufora. */
	public static boolean redirecting;
	/** HUD jest zebrany w STATE i czeka na narysowanie do tekstury przed GUI tej klatki. */
	public static boolean pending;

	private HudCache() {
	}

	public static boolean active(Minecraft client) {
		return Modules.OPTIMIZATION.active(Modules.OPTIMIZATION.hudCaching) && client.gui.screen() == null && !client.gui.hud.isHidden()
			&& client.getWindow().getWidth() > 0 && client.getWindow().getHeight() > 0;
	}

	/** Bufor, do którego ma rysować GuiRenderer w tej chwili (null = zwykły). */
	public static RenderTarget target() {
		return redirecting ? cache : null;
	}

	/** Zamiast zbierania HUD-u do GUI: co 50 ms zbiera go do STATE, a do GUI dodaje gotową teksturę i elementy rysowane co klatkę. */
	public static void extract(Minecraft client, GuiGraphicsExtractor graphics, Consumer<GuiGraphicsExtractor> original) {
		if (!active(client)) {
			release();
			original.accept(graphics);
			return;
		}

		Window window = client.getWindow();
		int width = window.getWidth();
		int height = window.getHeight();
		if (cache == null || cache.width != width || cache.height != height || System.nanoTime() - last >= REFRESH_NANOS) {
			if (cache == null) {
				cache = new TextureTarget("AIO HUD cache", width, height, true, GpuFormat.RGBA8_UNORM);
			} else if (cache.width != width || cache.height != height) {
				cache.resize(width, height);
			}

			RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(cache.getColorTexture(), GuiRenderer.CLEAR_COLOR, cache.getDepthTexture(), 0.0);
			last = System.nanoTime();
			STATE.reset();
			GuiGraphicsExtractor cacheGraphics = new GuiGraphicsExtractor(client, STATE, (int) client.mouseHandler.getScaledXPos(window),
				(int) client.mouseHandler.getScaledYPos(window));
			capturing = true;
			try {
				original.accept(cacheGraphics);
			} finally {
				capturing = false;
			}

			pending = true;
		}

		// Tekstura bufora ma początek na dole, więc v idzie od 1 do 0 (tak samo robi vanilla przy obrazkach w GUI).
		((GuiGraphicsAccessor) graphics).aio$innerBlit(RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA, cache.getColorTextureView(),
			RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST), 0, 0, graphics.guiWidth(), graphics.guiHeight(), 0.0F, 1.0F, 1.0F, 0.0F, -1);
		Modules.MARKERS.render(graphics, client);
		Modules.PARTY.renderHeads(graphics, client);
	}

	/** Rysuje zebrany HUD do tekstury przez podany renderer; wołane tuż przed rysowaniem GUI klatki. */
	public static void renderPending(Runnable render) {
		if (!pending || cache == null) {
			return;
		}

		pending = false;
		redirecting = true;
		try {
			render.run();
		} finally {
			redirecting = false;
		}
	}

	/** Zwalnia teksturę (wyłączenie opcji). */
	public static void release() {
		pending = false;
		if (cache != null) {
			cache.destroyBuffers();
			cache = null;
		}
	}
}
