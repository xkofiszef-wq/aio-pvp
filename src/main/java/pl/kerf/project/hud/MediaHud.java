package pl.kerf.project.hud;

import pl.kerf.project.Lang;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import pl.kerf.project.config.Setting;
import pl.kerf.project.gui.theme.Draw;
import pl.kerf.project.media.MediaSession;
import pl.kerf.project.module.HudModule;

/**
 * Odtwarzacz multimediów: okładka, tytuł (przewijany, gdy za długi), wykonawca, przyciski i pasek postępu.
 * Przyciski da się klikać, gdy otwarty jest czat (wtedy kursor jest wolny).
 */
public final class MediaHud extends HudModule {
	private static final int HEIGHT = 50;
	private static final int ART = 40;

	public final Setting.Num width = this.add(new Setting.Num("width", "Szerokość", 170, 120, 260, 10, " px"));
	public final Setting.Bool showArt = this.add(new Setting.Bool("show_art", "Okładka", true));
	public final Setting.Bool showControls = this.add(new Setting.Bool("show_controls", "Przyciski", true));
	public final Setting.Bool hideIdle = this.add(new Setting.Bool("hide_idle", "Ukryj, gdy nic nie gra", true));
	public final Setting.Bool border = this.add(new Setting.Bool("border", "Obramowanie", true));
	public final Setting.Color borderColor = this.add(new Setting.Color("border_color", "Kolor obramowania", 0x30FFFFFF, true));
	public final Setting.Bool backgroundEnabled = this.add(new Setting.Bool("background_enabled", "Tło", true));
	public final Setting.Color background = this.add(new Setting.Color("background", "Kolor tła", 0xC0101016, true));
	public final Setting.Color accent = this.add(new Setting.Color("accent", "Kolor paska postępu", 0xFFE0506A, false));
	public final Setting.Num rounding = this.add(new Setting.Num("rounding", "Zaokrąglenie rogów", 0, 0, 8, 1, " px"));
	public final Setting.Color titleColor = this.add(new Setting.Color("title_color", "Kolor tytułu", 0xFFFFFFFF, false));
	public final Setting.Color artistColor = this.add(new Setting.Color("artist_color", "Kolor wykonawcy", 0xFFA0A0AA, false));
	public final Setting.Color buttonBackground = this.add(new Setting.Color("button_background", "Tło przycisków", 0x30FFFFFF, true));
	public final Setting.Color prevColor = this.add(new Setting.Color("prev_color", "Kolor przycisku Wstecz", 0xFFD0D0D8, false));
	public final Setting.Color playBackground = this.add(new Setting.Color("play_background", "Tło przycisku Play/Pauza", 0xFFE0506A, true));
	public final Setting.Color playColor = this.add(new Setting.Color("play_color", "Kolor ikony Play/Pauza", 0xFFFFFFFF, false));
	public final Setting.Color nextColor = this.add(new Setting.Color("next_color", "Kolor przycisku Dalej", 0xFFD0D0D8, false));

	/** Prostokąty przycisków na ekranie (do klikania przy otwartym czacie). */
	private final float[][] buttons = new float[3][4];

	public MediaHud() {
		super("media", "Media Player", "Co teraz gra (Spotify, przeglądarka...) z przyciskami. Tylko Windows.", false, 1.0F, 0.0F, 1.0F, 0.0F, -4.0F, 56.0F);
	}

	private boolean visible(boolean editing) {
		return editing || MediaSession.state().active() || !this.hideIdle.on();
	}

	@Override
	public int width(MinecraftClient client, boolean editing) {
		return this.visible(editing) ? this.width.i() : 0;
	}

	@Override
	public int height(MinecraftClient client, boolean editing) {
		return this.visible(editing) ? HEIGHT : 0;
	}

	@Override
	protected void draw(DrawContext context, MinecraftClient client, RenderTickCounter tickCounter, boolean editing) {
		TextRenderer font = client.textRenderer;
		MediaSession.State state = MediaSession.state();
		boolean active = state.active();
		String title = active ? state.title() : Lang.t(MediaSession.supported() ? "Nic nie gra" : "Tylko Windows");
		String artist = active ? state.artist() : Lang.t("Włącz Spotify lub muzykę w przeglądarce");
		int w = this.width.i();
		if (this.backgroundEnabled.on()) {
			Draw.roundExact(context, 0, 0, w, HEIGHT, this.rounding.i(), this.background.argb());
		}
		if (this.border.on()) {
			Draw.outlineExact(context, 0, 0, w, HEIGHT, this.rounding.i(), this.borderColor.argb());
		}

		int textX = 6;
		if (this.showArt.on()) {
			int ay = (HEIGHT - ART) / 2;
			if (active && MediaSession.hasArt()) {
				context.getMatrices().pushMatrix();
				context.getMatrices().translate(5, ay);
				context.getMatrices().scale(ART / 64.0F, ART / 64.0F);
				context.drawTexture(RenderPipelines.GUI_TEXTURED, MediaSession.ART, 0, 0, 0.0F, 0.0F, 64, 64, 64, 64);
				context.getMatrices().popMatrix();
			} else {
				context.fill(5, ay, 5 + ART, ay + ART, 0xFF26262E);
				noteIcon(context, 5 + ART / 2, ay + ART / 2, 0xFF8A8A96);
			}

			textX = 5 + ART + 6;
		}

		int textW = w - textX - 5;
		marquee(context, font, title, textX, 6, textW, this.titleColor.argb());
		context.drawText(font, ellipsis(font, artist, textW), textX, 17, this.artistColor.argb(), false);

		int center = textX + textW / 2;
		// Przyciski: trzy kafelki 16 x 14 w odstępie 6 px (poprzedni, odtwórz/pauza, następny); klik liczy się na całym kafelku.
		this.buttons[0] = new float[] {-1, -1, 0, 0};
		if (this.showControls.on()) {
			int y = 26;
			int[] xs = {center - 30, center - 8, center + 14};
			for (int i = 0; i < 3; i++) {
				Draw.roundExact(context, xs[i], y, 16, 14, 3, i == 1 ? this.playBackground.argb() : this.buttonBackground.argb());
				this.storeButton(i, xs[i], y, 16, 14);
			}

			prevIcon(context, xs[0] + 8, y + 7, this.prevColor.argb());
			nextIcon(context, xs[2] + 8, y + 7, this.nextColor.argb());
			if (active && state.playing()) {
				context.fill(xs[1] + 5, y + 4, xs[1] + 7, y + 10, this.playColor.argb());
				context.fill(xs[1] + 9, y + 4, xs[1] + 11, y + 10, this.playColor.argb());
			} else {
				triangleRight(context, xs[1] + 6, y + 7, 3, this.playColor.argb());
			}
		}

		// Pasek postępu z czasem po obu stronach (mniejsza czcionka).
		double position = active ? MediaSession.position() : 0.0;
		double duration = active ? state.duration() : 0.0;
		String left = time(position);
		String right = time(duration);
		float small = 0.75F;
		int barY = HEIGHT - 7;
		int leftW = Math.round(font.getWidth(left) * small);
		int rightW = Math.round(font.getWidth(right) * small);
		smallText(context, font, left, textX, barY - 3, small);
		smallText(context, font, right, w - 5 - rightW, barY - 3, small);
		int barX1 = textX + leftW + 4;
		int barX2 = w - 5 - rightW - 4;
		context.fill(barX1, barY, barX2, barY + 2, 0x50FFFFFF);
		if (duration > 0.0 && barX2 > barX1) {
			int filled = barX1 + (int) Math.round((barX2 - barX1) * Math.min(1.0, position / duration));
			context.fill(barX1, barY, filled, barY + 2, this.accent.argb());
			context.fill(filled - 1, barY - 1, filled + 2, barY + 3, 0xFFFFFFFF);
		}
	}

	private void storeButton(int index, int x, int y, int w, int h) {
		this.buttons[index] = new float[] {this.boundsX + x * this.scale, this.boundsY + y * this.scale, w * this.scale, h * this.scale};
	}

	/** Prostokąt przycisku (0 = poprzedni, 1 = odtwórz/pauza, 2 = następny) na ekranie: x, y, szerokość, wysokość. */
	public float[] buttonRect(int index) {
		return this.buttons[index].clone();
	}

	/** Klik w przycisk odtwarzacza (przy otwartym czacie). Zwraca true, gdy kliknięcie zostało obsłużone. */
	public boolean click(double mx, double my) {
		if (!this.on() || !this.showControls.on()) {
			return false;
		}

		String[] commands = {"prev", "toggle", "next"};
		for (int i = 0; i < 3; i++) {
			float[] b = this.buttons[i];
			if (b[2] > 0 && mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3]) {
				MediaSession.command(commands[i]);
				return true;
			}
		}

		return false;
	}

	/** Tytuł przewijany w kółko, gdy nie mieści się w szerokości. */
	private static void marquee(DrawContext context, TextRenderer font, String text, int x, int y, int width, int color) {
		int textW = font.getWidth(text);
		if (textW <= width) {
			context.drawText(font, text, x, y, color, false);
			return;
		}

		int gap = 30;
		int offset = (int) (System.currentTimeMillis() / 40L % (textW + gap));
		context.enableScissor(x, y - 1, x + width, y + 9);
		context.drawText(font, text, x - offset, y, color, false);
		context.drawText(font, text, x - offset + textW + gap, y, color, false);
		context.disableScissor();
	}

	private static String ellipsis(TextRenderer font, String text, int width) {
		if (font.getWidth(text) <= width) {
			return text;
		}

		return font.trimToWidth(text, Math.max(0, width - font.getWidth("..."))).stripTrailing() + "...";
	}

	private static void smallText(DrawContext context, TextRenderer font, String text, int x, int y, float scale) {
		context.getMatrices().pushMatrix();
		context.getMatrices().translate(x, y);
		context.getMatrices().scale(scale, scale);
		context.drawText(font, text, 0, 0, 0xFFB0B0BA, false);
		context.getMatrices().popMatrix();
	}

	private static String time(double seconds) {
		int total = (int) Math.max(0.0, seconds);
		int rest = total % 60;
		return total / 60 + (rest < 10 ? ":0" : ":") + rest;
	}

	/** Trójkąt "w prawo" o połowie wysokości h, z lewym bokiem w x i środkiem w cy. */
	private static void triangleRight(DrawContext context, int x, int cy, int h, int color) {
		for (int i = 0; i <= h; i++) {
			context.fill(x + i, cy - (h - i), x + i + 1, cy + (h - i) + 1, color);
		}
	}

	private static void triangleLeft(DrawContext context, int x, int cy, int h, int color) {
		for (int i = 0; i <= h; i++) {
			context.fill(x - i, cy - (h - i), x - i + 1, cy + (h - i) + 1, color);
		}
	}

	private static void prevIcon(DrawContext context, int cx, int cy, int color) {
		context.fill(cx - 4, cy - 3, cx - 3, cy + 4, color);
		triangleLeft(context, cx + 3, cy, 3, color);
	}

	private static void nextIcon(DrawContext context, int cx, int cy, int color) {
		triangleRight(context, cx - 3, cy, 3, color);
		context.fill(cx + 3, cy - 3, cx + 4, cy + 4, color);
	}

	private static void noteIcon(DrawContext context, int cx, int cy, int color) {
		context.fill(cx + 1, cy - 8, cx + 3, cy + 4, color);
		context.fill(cx + 3, cy - 8, cx + 7, cy - 6, color);
		context.fill(cx - 4, cy + 2, cx + 3, cy + 7, color);
	}
}
