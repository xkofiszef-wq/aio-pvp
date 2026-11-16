package pl.kerf.project.gui.theme;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import org.joml.Matrix3x2fStack;
import pl.kerf.project.module.Module;

/**
 * Prymitywy rysowania interfejsu: zaokrąglone prostokąty, obramowania, cienie i ikony.
 * Wszystko z kilku wypełnień na kształt (bez pętli po pikselach), więc tanie nawet przy wielu kartach.
 */
public final class Draw {
	private Draw() {
	}

	/**
	 * Prostokąt z zaokrąglonymi rogami (styl z projektu w Figmie: przyciski i pola mają 4 px). Promienie 1-2 są
	 * pomijane, bo w pikselach GUI i tak nie dają widocznego łuku. Panele i kafelki mają 0, więc zostają kwadratowe.
	 */
	public static void round(DrawContext context, int x, int y, int w, int h, int r, int color) {
		roundExact(context, x, y, w, h, r < 3 ? 0 : r, color);
	}

	/** Jak {@link #round}, ale bez progu: zaokrągla też małe promienie (przyciski i przełącznik z projektu w Figmie). */
	public static void roundExact(DrawContext context, int x, int y, int w, int h, int r, int color) {
		if (w <= 0 || h <= 0) {
			return;
		}

		r = Math.min(r, Math.min(w, h) / 2);
		if (r <= 0) {
			context.fill(x, y, x + w, y + h, color);
			return;
		}

		context.fill(x, y + r, x + w, y + h - r, color);
		for (int i = 0; i < r; i++) {
			// Wcięcie każdego rzędu rogu: przybliżenie ćwiartki okręgu.
			int inset = r - (int) Math.round(Math.sqrt(r * r - (r - i - 0.5) * (r - i - 0.5)));
			context.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
			context.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color);
		}
	}

	/** Obramowanie o grubości 1 px (kwadratowe rogi, jak {@link #round}). */
	public static void outline(DrawContext context, int x, int y, int w, int h, int r, int color) {
		outlineExact(context, x, y, w, h, r < 3 ? 0 : r, color);
	}

	/** Jak {@link #outline}, ale bez progu promienia (para do {@link #roundExact}). */
	public static void outlineExact(DrawContext context, int x, int y, int w, int h, int r, int color) {
		r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
		context.fill(x + r, y, x + w - r, y + 1, color);
		context.fill(x + r, y + h - 1, x + w - r, y + h, color);
		context.fill(x, y + r, x + 1, y + h - r, color);
		context.fill(x + w - 1, y + r, x + w, y + h - r, color);
		for (int i = 0; i < r; i++) {
			int inset = r - (int) Math.round(Math.sqrt(r * r - (r - i - 0.5) * (r - i - 0.5)));
			int next = i + 1 < r ? r - (int) Math.round(Math.sqrt(r * r - (r - i - 1.5) * (r - i - 1.5))) : 0;
			int span = Math.max(1, inset - next);
			// Lewy i prawy brzeg rogu w tym rzędzie (górne i dolne rogi).
			context.fill(x + inset - span + 1, y + i, x + inset + 1, y + i + 1, color);
			context.fill(x + w - inset - 1, y + i, x + w - inset + span - 1, y + i + 1, color);
			context.fill(x + inset - span + 1, y + h - 1 - i, x + inset + 1, y + h - i, color);
			context.fill(x + w - inset - 1, y + h - 1 - i, x + w - inset + span - 1, y + h - i, color);
		}
	}

	/** Płaska karta: wypełnienie i cienka ramka. */
	public static void card(DrawContext context, int x, int y, int w, int h, int r, int fill, int border) {
		round(context, x, y, w, h, r, fill);
		outline(context, x, y, w, h, r, border);
	}

	/**
	 * Miękki cień wokół okna: same pierścienie na zewnątrz, bez wypełnienia, żeby nie przyciemniać
	 * półprzezroczystego szkła od spodu.
	 */
	public static void shadow(DrawContext context, int x, int y, int w, int h, int r, int size) {
		for (int i = 1; i <= size; i++) {
			int alpha = Math.round(70.0F * (1.0F - (float) i / (size + 1)));
			// Pierścienie cienia idą za kształtem okna: przy zaokrąglonym rośnie też ich promień.
			outline(context, x - i, y - i + 1, w + 2 * i, h + 2 * i, r > 0 ? r + i : 0, alpha << 24);
		}
	}

	/**
	 * Przełącznik z Figmy (komponent "Przełącznik"): pigułka 20x10 z ramką 1 px, gałka 8x8, włączony w kolorze akcentu
	 * motywu. Rysowany w pikselach ekranu, nie GUI, żeby łuki były gładkie jak w Figmie, a nie schodkowe.
	 * {@code t} = 0 (wył.) .. 1 (wł.).
	 */
	public static void toggle(DrawContext context, int x, int y, float t) {
		int s = Math.max(1, (int) MinecraftClient.getInstance().getWindow().getScaleFactor());
		Matrix3x2fStack matrices = context.getMatrices();
		matrices.pushMatrix();
		matrices.scale(1.0F / s, 1.0F / s);
		int px = x * s;
		int py = y * s;
		int w = 20 * s;
		int h = 10 * s;
		int knob = 8 * s;
		// Ramka = pigułka w kolorze ramki, na niej środek o 1 px GUI mniejszy.
		roundExact(context, px, py, w, h, h / 2, Theme.mix(Theme.BORDER_STRONG, Theme.ACCENT, t));
		roundExact(context, px + s, py + s, w - 2 * s, h - 2 * s, (h - 2 * s) / 2, Theme.mix(0xFF1A1D26, Theme.alpha(Theme.ACCENT, 0xE6), t));
		int kx = px + s + Math.round((w - knob - 2 * s) * Anim.ease(t));
		roundExact(context, kx, py + s, knob, knob, knob / 2, Theme.mix(0xFF8C92A0, 0xFFF0F1F5, t));
		matrices.popMatrix();
	}

	/** Tekst dopasowany do szerokości, z wielokropkiem. */
	public static String fit(TextRenderer font, String text, int width) {
		if (font.getWidth(text) <= width) {
			return text;
		}

		return font.trimToWidth(text, Math.max(0, width - font.getWidth("…"))).stripTrailing() + "…";
	}

	public static void text(DrawContext context, TextRenderer font, String text, int x, int y, int color) {
		context.drawText(font, text, x, y, color, false);
	}

	public static void text(DrawContext context, TextRenderer font, Text text, int x, int y, int color) {
		context.drawText(font, text, x, y, color, false);
	}

	// --- ikony (rysowane z masek 9x9, w kolorze podanym przez wywołującego) ---

	private static final String[] ICON_ALL = {
		"###...###", "###...###", "###...###", ".........", ".........", ".........", "###...###", "###...###", "###...###"
	};
	private static final String[] ICON_HUD = {
		"#########", "#.......#", "#.##....#", "#.##....#", "#.......#", "#....##.#", "#....##.#", "#.......#", "#########"
	};
	private static final String[] ICON_COMBAT = {
		".......##", "......###", ".....###.", "#...###..", "##.###...", ".####....", "..##.....", ".####....", "##..#...."
	};
	private static final String[] ICON_VISUALS = {
		".........", "...###...", ".##...##.", "#...#...#", "#..###..#", "#...#...#", ".##...##.", "...###...", "........."
	};
	private static final String[] ICON_ITEMS = {
		"...###...", ".##...##.", "#.......#", "##.....##", "#.##.##.#", "#...#...#", "#...#...#", ".##.#.##.", "...###..."
	};
	private static final String[] ICON_MISC = {
		"..#......", "#####....", "..#......", "......#..", "...######", "......#..", ".#.......", "######...", ".#......."
	};
	private static final String[] ICON_SEARCH = {
		".###.....", "#...#....", "#...#....", "#...#....", ".###.....", "....##...", ".....##..", "......##.", "........."
	};
	private static final String[] ICON_GEAR = {
		"...#.#...", ".#.###.#.", "..##.##..", "##.....##", ".#.....#.", "##.....##", "..##.##..", ".#.###.#.", "...#.#..."
	};
	private static final String[] ICON_CLOSE = {
		".........", ".#.....#.", "..#...#..", "...#.#...", "....#....", "...#.#...", "..#...#..", ".#.....#.", "........."
	};
	private static final String[] ICON_BACK = {
		".........", "...#.....", "..#......", ".#.......", "#########", ".#.......", "..#......", "...#.....", "........."
	};
	private static final String[] ICON_LAYOUT = {
		"####.####", "#..#.#..#", "####.####", ".........", "#########", "#.......#", "#########", ".........", "........."
	};

	public enum Icon {
		ALL(ICON_ALL), HUD(ICON_HUD), COMBAT(ICON_COMBAT), VISUALS(ICON_VISUALS), ITEMS(ICON_ITEMS), MISC(ICON_MISC),
		SEARCH(ICON_SEARCH), GEAR(ICON_GEAR), CLOSE(ICON_CLOSE), BACK(ICON_BACK), LAYOUT(ICON_LAYOUT);

		final String[] mask;

		Icon(String[] mask) {
			this.mask = mask;
		}

		public static Icon of(Module.Category category) {
			return switch (category) {
				case HUD -> HUD;
				case COMBAT -> COMBAT;
				case VISUALS -> VISUALS;
				case ITEMS -> ITEMS;
				case MISC -> MISC;
			};
		}
	}

	/** Ikona 9x9 w lewym górnym rogu (x, y). Kolejne piksele w rzędzie łączone w jedno wypełnienie. */
	public static void icon(DrawContext context, Icon icon, int x, int y, int color) {
		String[] mask = icon.mask;
		for (int row = 0; row < mask.length; row++) {
			String line = mask[row];
			int start = -1;
			for (int col = 0; col <= line.length(); col++) {
				boolean on = col < line.length() && line.charAt(col) == '#';
				if (on && start < 0) {
					start = col;
				} else if (!on && start >= 0) {
					context.fill(x + start, y + row, x + col, y + row + 1, color);
					start = -1;
				}
			}
		}
	}
}
