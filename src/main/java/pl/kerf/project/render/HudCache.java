package pl.kerf.project.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.state.TexturedQuadGuiElementRenderState;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.client.util.Window;
import org.joml.Matrix3x2f;
import pl.kerf.project.module.Modules;

/**
 * HUD Caching: HUD gry (z elementami moda) rysuje się do osobnej tekstury najwyżej raz na 50 ms,
 * a w pozostałych klatkach wyświetla się tylko gotowa tekstura. To oszczędza zbieranie i rysowanie HUD-u w większości klatek.
 */
public final class HudCache {
	private static final long REFRESH_NANOS = 50_000_000L;
	private static SimpleFramebuffer cache;
	private static long last;
	/** Trwa rysowanie HUD-u do tekstury (elementy, które mają być co klatkę, pomijają się). */
	public static boolean capturing;
	/** GuiRenderer ma rysować do tekstury zamiast do głównego bufora. */
	public static boolean redirecting;

	private HudCache() {
	}

	public static boolean active(MinecraftClient client) {
		return Modules.OPTIMIZATION.active(Modules.OPTIMIZATION.hudCaching) && client.currentScreen == null && !client.options.hudHidden
			&& client.getWindow().getFramebufferWidth() > 0 && client.getWindow().getFramebufferHeight() > 0;
	}

	/** Bufor, do którego ma rysować GuiRenderer w tej chwili (null = zwykły). */
	public static Framebuffer target() {
		return redirecting ? cache : null;
	}

	public static boolean needsRefresh(Window window) {
		return cache == null || cache.textureWidth != window.getFramebufferWidth() || cache.textureHeight != window.getFramebufferHeight()
			|| System.nanoTime() - last >= REFRESH_NANOS;
	}

	/** Przygotowuje (i czyści) teksturę do nowego rysunku HUD-u. */
	public static void prepare(Window window) {
		int width = window.getFramebufferWidth();
		int height = window.getFramebufferHeight();
		if (cache == null) {
			cache = new SimpleFramebuffer("AIO HUD cache", width, height, true);
		} else if (cache.textureWidth != width || cache.textureHeight != height) {
			cache.resize(width, height);
		}

		RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(cache.getColorAttachment(), 0, cache.getDepthAttachment(), 1.0);
		last = System.nanoTime();
	}

	/** Dodaje do HUD-u gotową teksturę na cały ekran (obrócona w pionie, bo tekstura bufora ma początek na dole). */
	public static void addQuad(DrawContext context) {
		TextureSetup setup = TextureSetup.of(cache.getColorAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.NEAREST));
		context.state.addSimpleElement(new TexturedQuadGuiElementRenderState(RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA, setup, new Matrix3x2f(),
			0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight(), 0.0F, 1.0F, 1.0F, 0.0F, -1, null));
	}

	/** Zwalnia teksturę (wyłączenie opcji, zamknięcie gry). */
	public static void release() {
		if (cache != null) {
			cache.delete();
			cache = null;
		}
	}
}
