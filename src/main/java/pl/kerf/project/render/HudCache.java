package pl.kerf.project.render;

//? if =26.2 {
/*import com.mojang.blaze3d.GpuFormat;
*///?}
//? if >=26.3 {
/*import com.mojang.renderpearl.api.GpuFormat;
*///?}
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
//? if <26.3 {
import com.mojang.blaze3d.textures.FilterMode;
//?} else {
/*import com.mojang.renderpearl.api.textures.FilterMode;
*///?}
//? if >=26.1.2 {
/*import java.util.function.Consumer;
*///?}
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
//? if <26.1.2 {
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.BlitRenderState;
//?} else {
/*import net.minecraft.client.gui.render.GuiRenderer;
*///?}
import net.minecraft.client.renderer.RenderPipelines;
//? if <26.1.2 {
import org.joml.Matrix3x2f;
//?} else {
/*import net.minecraft.client.renderer.state.gui.GuiRenderState;
import pl.kerf.project.mixin.GuiGraphicsAccessor;
*///?}
import pl.kerf.project.module.Modules;

/**
 * HUD Caching: HUD gry (z elementami moda) rysuje się do osobnej tekstury najwyżej raz na 50 ms,
 * a w pozostałych klatkach wyświetla się tylko gotowa tekstura. To oszczędza zbieranie i rysowanie HUD-u w większości klatek.
 */
public final class HudCache {
	private static final long REFRESH_NANOS = 50_000_000L;
	//? if >=26.1.2 {
	/*/^* Stan GUI, do którego zbiera się HUD przy odświeżeniu tekstury. ^/
	public static final GuiRenderState STATE = new GuiRenderState();
	*///?}
	private static TextureTarget cache;
	private static long last;
	/** Trwa rysowanie HUD-u do tekstury (elementy, które mają być co klatkę, pomijają się). */
	public static boolean capturing;
	/** GuiRenderer ma rysować do tekstury zamiast do głównego bufora. */
	public static boolean redirecting;
	//? if >=26.1.2 {
	/*/^* HUD jest zebrany w STATE i czeka na narysowanie do tekstury przed GUI tej klatki. ^/
	public static boolean pending;
	*///?}

	private HudCache() {
	}

	public static boolean active(Minecraft client) {
		//? if <26.2 {
		return Modules.OPTIMIZATION.active(Modules.OPTIMIZATION.hudCaching) && client.screen == null && !client.options.hideGui
		//?} else {
		/*return Modules.OPTIMIZATION.active(Modules.OPTIMIZATION.hudCaching) && client.gui.screen() == null && !client.gui.hud.isHidden()
		*///?}
			&& client.getWindow().getWidth() > 0 && client.getWindow().getHeight() > 0;
	}

	/** Bufor, do którego ma rysować GuiRenderer w tej chwili (null = zwykły). */
	public static RenderTarget target() {
		return redirecting ? cache : null;
	}

	//? if <26.1.2 {
	public static boolean needsRefresh(Window window) {
		return cache == null || cache.width != window.getWidth() || cache.height != window.getHeight()
			|| System.nanoTime() - last >= REFRESH_NANOS;
	//?} else {
	/*/^* Zamiast zbierania HUD-u do GUI: co 50 ms zbiera go do STATE, a do GUI dodaje gotową teksturę i elementy rysowane co klatkę. ^/
	public static void extract(Minecraft client, GuiGraphics graphics, Consumer<GuiGraphics> original) {
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
	*///?}
				//? if =26.2 {
				/*cache = new TextureTarget("AIO HUD cache", width, height, true, GpuFormat.RGBA8_UNORM);
				*///?}
				//? if =26.1.2 {
				/*cache = new TextureTarget("AIO HUD cache", width, height, true);
				*///?}
				//? if >=26.3 {
				/*cache = new TextureTarget("AIO HUD cache", width, height, GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);
				*///?}
			//? if >=26.1.2 {
			/*} else if (cache.width != width || cache.height != height) {
				cache.resize(width, height);
			}

			*///?}
			//? if >=26.2 {
			/*RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(cache.getColorTexture(), GuiRenderer.CLEAR_COLOR, cache.getDepthTexture(), 0.0);
			*///?}
			//? if =26.1.2 {
			/*RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(cache.getColorTexture(), GuiRenderer.CLEAR_COLOR, cache.getDepthTexture(), 1.0);
			*///?}
			//? if >=26.1.2 {
			/*last = System.nanoTime();
			STATE.reset();
			GuiGraphics cacheGraphics = new GuiGraphics(client, STATE, (int) client.mouseHandler.getScaledXPos(window),
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
			*///?}
	}

	//? if <26.1.2 {
	/** Przygotowuje (i czyści) teksturę do nowego rysunku HUD-u. */
	public static void prepare(Window window) {
		int width = window.getWidth();
		int height = window.getHeight();
		if (cache == null) {
			cache = new TextureTarget("AIO HUD cache", width, height, true);
		} else if (cache.width != width || cache.height != height) {
			cache.resize(width, height);
	//?} else {
	/*/^* Rysuje zebrany HUD do tekstury przez podany renderer; wołane tuż przed rysowaniem GUI klatki. ^/
	public static void renderPending(Runnable render) {
		if (!pending || cache == null) {
			return;
	*///?}
		}

		//? if <26.1.2 {
		RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(cache.getColorTexture(), 0, cache.getDepthTexture(), 1.0);
		last = System.nanoTime();
		//?} else {
		/*pending = false;
		redirecting = true;
		try {
			render.run();
		} finally {
			redirecting = false;
		}
		*///?}
	}

	//? if <26.1.2 {
	/** Dodaje do HUD-u gotową teksturę na cały ekran (obrócona w pionie, bo tekstura bufora ma początek na dole). */
	public static void addQuad(GuiGraphics context) {
		TextureSetup setup = TextureSetup.singleTexture(cache.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
		context.guiRenderState.submitGuiElement(new BlitRenderState(RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA, setup, new Matrix3x2f(),
			0, 0, context.guiWidth(), context.guiHeight(), 0.0F, 1.0F, 1.0F, 0.0F, -1, null));
	}

	/** Zwalnia teksturę (wyłączenie opcji, zamknięcie gry). */
	//?}
	public static void release() {
		//? if >=26.1.2 {
		/*pending = false;
		*///?}
		if (cache != null) {
			cache.destroyBuffers();
			cache = null;
		}
	}
}
