package pl.kerf.project.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import pl.kerf.project.module.HudModule;
import pl.kerf.project.module.Modules;

/** Element, który edytor HUD umie przesuwać i skalować: element moda albo element vanilla (title, scoreboard...). */
abstract class EditorElement {
	final String name;
	final int accent;

	EditorElement(String name, int accent) {
		this.name = name;
		this.accent = accent;
	}

	/** Czy element jest na ekranie (ma rozmiar i jest włączony). */
	abstract boolean visible();

	abstract boolean movable();

	abstract float x();

	abstract float y();

	abstract float w();

	abstract float h();

	abstract float scale();

	abstract float minScale();

	abstract float maxScale();

	/** Ustawia lewy górny róg (łącznie z ramką). */
	abstract void setTopLeft(float x, float y);

	/** Zmienia skalę tak, żeby punkt (fx, fy) ekranu został w miejscu. */
	abstract void setScaleAround(float scale, float fx, float fy);

	abstract void reset();

	/** Przykładowy wygląd w edytorze (tylko elementy vanilla, elementy moda rysuje HUD). */
	void drawSample(DrawContext context, TextRenderer font) {
	}

	/** Element HUD-u moda. */
	static final class Hud extends EditorElement {
		final HudModule module;

		Hud(HudModule module) {
			super(module.name, pl.kerf.project.gui.theme.Theme.category(module.category));
			this.module = module;
		}

		private float margin() {
			return this.module.margin() * this.module.scale;
		}

		@Override
		boolean visible() {
			return this.module.on() && this.module.boundsW > 0.0F && this.module.boundsH > 0.0F;
		}

		@Override
		boolean movable() {
			return this.module.movable();
		}

		@Override
		float x() {
			return this.module.boundsX - this.margin();
		}

		@Override
		float y() {
			return this.module.boundsY - this.margin();
		}

		@Override
		float w() {
			return this.module.boundsW + 2 * this.margin();
		}

		@Override
		float h() {
			return this.module.boundsH + 2 * this.margin();
		}

		@Override
		float scale() {
			return this.module.scale;
		}

		@Override
		float minScale() {
			return 0.5F;
		}

		@Override
		float maxScale() {
			return 3.0F;
		}

		@Override
		void setTopLeft(float x, float y) {
			MinecraftClient client = MinecraftClient.getInstance();
			int sw = client.getWindow().getScaledWidth();
			int sh = client.getWindow().getScaledHeight();
			this.module.moveTo(x + this.margin(), y + this.margin(), sw, sh);
		}

		@Override
		void setScaleAround(float scale, float fx, float fy) {
			float oldScale = this.module.scale;
			float rx = (fx - this.x()) / Math.max(1.0F, this.w());
			float ry = (fy - this.y()) / Math.max(1.0F, this.h());
			this.module.setScale(scale);
			float ratio = this.module.scale / oldScale;
			this.module.boundsW *= ratio;
			this.module.boundsH *= ratio;
			this.setTopLeft(fx - rx * this.w(), fy - ry * this.h());
		}

		@Override
		void reset() {
			this.module.resetPosition();
		}
	}

	/**
	 * Element vanilla przesuwany przez HUD Tweaker. Pozycja na ekranie = kotwica + przesunięcie + lokalny punkt * skala,
	 * dokładnie tak jak przekształca go mixin w InGameHud.
	 */
	abstract static class Vanilla extends EditorElement {
		final Modules.Tweak tweak;

		Vanilla(String name, Modules.Tweak tweak) {
			super(name, 0xFFE0E0E0);
			this.tweak = tweak;
		}

		static int screenW() {
			return MinecraftClient.getInstance().getWindow().getScaledWidth();
		}

		static int screenH() {
			return MinecraftClient.getInstance().getWindow().getScaledHeight();
		}

		/** Naturalny punkt zaczepienia elementu w vanilla (środek title, prawa krawędź scoreboardu...). */
		abstract float anchorX();

		abstract float anchorY();

		/** Prostokąt przykładowej zawartości względem kotwicy, przed skalowaniem. */
		abstract float[] local(TextRenderer font);

		/** Rysuje przykładową zawartość w lokalnych współrzędnych. */
		abstract void drawLocal(DrawContext context, TextRenderer font);

		/** Wyłączony HUD Tweaker = element na miejscu z vanilla; ramka w edytorze musi to uwzględniać. */
		private static boolean tweaking() {
			return Modules.HUD_TWEAKER.on();
		}

		private float offsetX() {
			return tweaking() ? this.tweak.x.f() : 0.0F;
		}

		private float offsetY() {
			return tweaking() ? this.tweak.y.f() : 0.0F;
		}

		private float[] local() {
			return this.local(MinecraftClient.getInstance().textRenderer);
		}

		@Override
		boolean visible() {
			return true;
		}

		@Override
		boolean movable() {
			return true;
		}

		@Override
		float x() {
			return this.anchorX() + this.offsetX() + this.local()[0] * this.scale();
		}

		@Override
		float y() {
			return this.anchorY() + this.offsetY() + this.local()[1] * this.scale();
		}

		@Override
		float w() {
			return this.local()[2] * this.scale();
		}

		@Override
		float h() {
			return this.local()[3] * this.scale();
		}

		@Override
		float scale() {
			return tweaking() ? this.tweak.scale.f() : 1.0F;
		}

		@Override
		float minScale() {
			return (float) this.tweak.scale.min;
		}

		@Override
		float maxScale() {
			return (float) this.tweak.scale.max;
		}

		@Override
		void setTopLeft(float x, float y) {
			Modules.HUD_TWEAKER.setEnabled(true);
			float[] local = this.local();
			this.tweak.x.set((double) (x - this.anchorX() - local[0] * this.scale()));
			this.tweak.y.set((double) (y - this.anchorY() - local[1] * this.scale()));
		}

		@Override
		void setScaleAround(float scale, float fx, float fy) {
			float rx = (fx - this.x()) / Math.max(1.0F, this.w());
			float ry = (fy - this.y()) / Math.max(1.0F, this.h());
			this.tweak.scale.set((double) scale);
			this.setTopLeft(fx - rx * this.w(), fy - ry * this.h());
		}

		@Override
		void reset() {
			this.tweak.x.reset();
			this.tweak.y.reset();
			this.tweak.scale.reset();
		}

		@Override
		void drawSample(DrawContext context, TextRenderer font) {
			float s = this.scale();
			context.getMatrices().pushMatrix();
			context.getMatrices().translate(this.anchorX() + this.offsetX(), this.anchorY() + this.offsetY());
			context.getMatrices().scale(s, s);
			this.drawLocal(context, font);
			context.getMatrices().popMatrix();
		}
	}

	static final class Title extends Vanilla {
		Title() {
			super("Title", Modules.HUD_TWEAKER.title);
		}

		@Override
		float anchorX() {
			return screenW() / 2.0F;
		}

		@Override
		float anchorY() {
			return screenH() / 2.0F;
		}

		@Override
		float[] local(TextRenderer font) {
			float w = Math.max(font.getWidth("Title") * 4, font.getWidth("Subtitle") * 2) + 8;
			return new float[] {-w / 2.0F, -42.0F, w, 70.0F};
		}

		@Override
		void drawLocal(DrawContext context, TextRenderer font) {
			context.getMatrices().pushMatrix();
			context.getMatrices().scale(4.0F, 4.0F);
			context.drawText(font, "Title", -font.getWidth("Title") / 2, -10, 0xC0FFFFFF, true);
			context.getMatrices().popMatrix();
			context.getMatrices().pushMatrix();
			context.getMatrices().scale(2.0F, 2.0F);
			context.drawText(font, "Subtitle", -font.getWidth("Subtitle") / 2, 5, 0xC0FFFFFF, true);
			context.getMatrices().popMatrix();
		}
	}

	static final class Actionbar extends Vanilla {
		private static final String TEXT = "Actionbar: pasek akcji";

		Actionbar() {
			super("Actionbar", Modules.HUD_TWEAKER.actionbar);
		}

		@Override
		float anchorX() {
			return screenW() / 2.0F;
		}

		@Override
		float anchorY() {
			return screenH() - 68.0F;
		}

		@Override
		float[] local(TextRenderer font) {
			float w = font.getWidth(TEXT) + 4;
			return new float[] {-w / 2.0F, -6.0F, w, 13.0F};
		}

		@Override
		void drawLocal(DrawContext context, TextRenderer font) {
			int w = font.getWidth(TEXT);
			context.fill(-w / 2 - 2, -6, w / 2 + 2, 7, 0x60000000);
			context.drawText(font, TEXT, -w / 2, -4, 0xFFFFFFFF, true);
		}
	}

	static final class Scoreboard extends Vanilla {
		private static final String TITLE = "Scoreboard";
		private static final String[][] LINES = {{"Gracze", "24"}, {"Zabójstwa", "12"}, {"Śmierci", "3"}, {"Mapa", "Arena"}};

		Scoreboard() {
			super("Scoreboard", Modules.HUD_TWEAKER.scoreboard);
		}

		@Override
		float anchorX() {
			return screenW();
		}

		@Override
		float anchorY() {
			return screenH() / 2.0F;
		}

		private static int width(TextRenderer font) {
			int w = font.getWidth(TITLE);
			for (String[] line : LINES) {
				w = Math.max(w, font.getWidth(line[0]) + font.getWidth(": ") + font.getWidth(line[1]));
			}

			return w;
		}

		@Override
		float[] local(TextRenderer font) {
			// Ten sam układ co InGameHud: dół = środek ekranu + 1/3 wysokości wierszy.
			int rows = LINES.length;
			int bottom = rows * 9 / 3;
			int top = bottom - rows * 9 - 10;
			int w = width(font);
			return new float[] {-w - 5.0F, top, w + 4.0F, bottom - top};
		}

		@Override
		void drawLocal(DrawContext context, TextRenderer font) {
			int rows = LINES.length;
			int bottom = rows * 9 / 3;
			int u = bottom - rows * 9;
			int w = width(font);
			int left = -w - 3;
			context.fill(left - 2, u - 10, -1, u - 1, 0x66000000);
			context.fill(left - 2, u - 1, -1, bottom, 0x4D000000);
			context.drawText(font, TITLE, left + w / 2 - font.getWidth(TITLE) / 2, u - 9, 0xFFFFFFFF, false);
			for (int i = 0; i < rows; i++) {
				int y = u + i * 9;
				context.drawText(font, LINES[i][0], left, y, 0xFFFFFFFF, false);
				context.drawText(font, LINES[i][1], -1 - font.getWidth(LINES[i][1]), y, 0xFFFF5555, false);
			}
		}
	}

	static final class Bossbar extends Vanilla {
		private static final Identifier BACKGROUND = Identifier.ofVanilla("boss_bar/pink_background");
		private static final Identifier PROGRESS = Identifier.ofVanilla("boss_bar/pink_progress");

		Bossbar() {
			super("Bossbar", Modules.HUD_TWEAKER.bossbar);
		}

		@Override
		float anchorX() {
			return screenW() / 2.0F;
		}

		@Override
		float anchorY() {
			return 0.0F;
		}

		@Override
		float[] local(TextRenderer font) {
			return new float[] {-91.0F, 2.0F, 182.0F, 15.0F};
		}

		@Override
		void drawLocal(DrawContext context, TextRenderer font) {
			context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, BACKGROUND, -91, 12, 182, 5);
			context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, PROGRESS, 182, 5, 0, 0, -91, 12, 120, 5);
			context.drawText(font, "Bossbar", -font.getWidth("Bossbar") / 2, 3, 0xFFFFFFFF, true);
		}
	}
}
