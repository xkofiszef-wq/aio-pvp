package pl.kerf.project.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Gwiazdka "ulubione" z pikseli (11x11, symetryczna w poziomie): pełna (wypełnienie + ciemniejszy obrys) dla ulubionych,
 * sam obrys dla pozostałych.
 */
final class StarIcon {
	static final int SIZE = 11;
	private static final String[] ROWS = {
		".....X.....",
		"....XXX....",
		"....XXX....",
		"XXXXXXXXXXX",
		".XXXXXXXXX.",
		"..XXXXXXX..",
		"..XXXXXXX..",
		".XXXXXXXXX.",
		".XXX...XXX.",
		"XXX.....XXX",
		"XX.......XX"
	};

	private StarIcon() {
	}

	private static boolean on(int row, int col) {
		return row >= 0 && row < ROWS.length && col >= 0 && col < ROWS[row].length() && ROWS[row].charAt(col) == 'X';
	}

	/** Ciemniejszy odcień koloru (obrys wypełnionej gwiazdki). */
	private static int darker(int argb) {
		int r = (argb >> 16 & 0xFF) * 60 / 100;
		int g = (argb >> 8 & 0xFF) * 60 / 100;
		int b = (argb & 0xFF) * 60 / 100;
		return argb & 0xFF000000 | r << 16 | g << 8 | b;
	}

	static void draw(GuiGraphicsExtractor context, int x, int y, boolean filled, int color) {
		for (int row = 0; row < ROWS.length; row++) {
			for (int col = 0; col < ROWS[row].length(); col++) {
				if (!on(row, col)) {
					continue;
				}

				boolean edge = !on(row - 1, col) || !on(row + 1, col) || !on(row, col - 1) || !on(row, col + 1);
				if (edge) {
					context.fill(x + col, y + row, x + col + 1, y + row + 1, filled ? darker(color) : color);
				} else if (filled) {
					context.fill(x + col, y + row, x + col + 1, y + row + 1, color);
				}
			}
		}
	}
}
