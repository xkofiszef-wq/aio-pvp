package pl.kerf.project.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import pl.kerf.project.gui.theme.Draw;
import pl.kerf.project.gui.theme.Theme;

/**
 * Wspólny wygląd pomocniczych ekranów (picker koloru, edytory). Korzysta z tej samej palety i prymitywów
 * co menu główne, więc wszystkie ekrany wyglądają spójnie.
 */
public final class Ui {
	public static final int PAD = 6;
	public static final int PANEL_BORDER = Theme.BORDER;
	public static final int INSET_BG = Theme.INSET;
	public static final int TEXT = Theme.TEXT;
	public static final int TEXT_DIM = Theme.TEXT_3;
	public static final int TEXT_SOFT = Theme.TEXT_2;
	public static final int ACCENT = Theme.ACCENT;
	/** Promień rogów dużych okien (o 2 px większy niż przycisków, bo okno je obejmuje); komponent "Panel szklany" w Figmie. */
	public static final int PANEL_R = 6;

	private Ui() {
	}

	/** Okno pomocnicze: mocniej kryjące niż menu, bo bywa nad światem bez rozmycia (edytor HUD). */
	public static void panel(DrawContext context, int x, int y, int w, int h) {
		Draw.shadow(context, x, y, w, h, PANEL_R, 4);
		Draw.card(context, x, y, w, h, PANEL_R, Theme.GLASS_STRONG, Theme.BORDER_STRONG);
	}

	public static void inset(DrawContext context, int x, int y, int w, int h) {
		Draw.round(context, x, y, w, h, 4, INSET_BG);
		Draw.outline(context, x, y, w, h, 4, Theme.BORDER);
	}

	public static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	/** Szachownica pod kolorami z kryciem, żeby było widać przezroczystość. */
	public static void checker(DrawContext context, int x, int y, int w, int h) {
		int cell = 4;
		for (int cy = 0; cy < h; cy += cell) {
			for (int cx = 0; cx < w; cx += cell) {
				boolean dark = (cx / cell + cy / cell) % 2 == 0;
				context.fill(x + cx, y + cy, x + Math.min(w, cx + cell), y + Math.min(h, cy + cell), dark ? 0xFF6A6E78 : 0xFF9EA3AD);
			}
		}
	}

	public static String ellipsis(TextRenderer font, String text, int width) {
		return Draw.fit(font, text, width);
	}
}
