package pl.kerf.project.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Gwiazdka "ulubione" według projektu użytkownika: obrys 22x21 komórek (lewa połowa z rysunku, prawa to jej lustrzane odbicie,
 * więc jest dokładnie symetryczna). Komórka ma pół piksela GUI, więc gwiazdka ma 11 px szerokości. Ulubiona jest wypełniona
 * złotem i ma ciemniejszy obrys, pozostałe mają sam szary obrys.
 */
final class StarIcon {
	/** Szerokość gwiazdki w pikselach GUI (komórka = 0,5 px). */
	static final int SIZE = 11;
	private static final int COLS = 22;
	private static final int ROWS = 21;
	/** Lewa połowa obrysu (kolumny 0-10), prawa jest odbiciem. */
	private static final String[] LEFT = {
		"..........X",
		".........X.",
		"........X..",
		"........X..",
		".......X...",
		".......X...",
		"......X....",
		".XXXXX.....",
		"X..........",
		"X..........",
		".X.........",
		"..X........",
		"...X.......",
		"....X......",
		"....X......",
		"...X.......",
		"...X......X",
		"..X......X.",
		"..X.....X..",
		"..X....X...",
		"..XXXXX...."
	};
	private static final boolean[][] OUTLINE = new boolean[ROWS][COLS];
	private static final boolean[][] INSIDE = new boolean[ROWS][COLS];

	static {
		for (int r = 0; r < ROWS; r++) {
			for (int c = 0; c < COLS / 2; c++) {
				boolean on = LEFT[r].charAt(c) == 'X';
				OUTLINE[r][c] = on;
				OUTLINE[r][COLS - 1 - c] = on;
			}
		}

		// Wnętrze = komórki nieosiągalne z brzegu bez przekraczania obrysu.
		boolean[][] outside = new boolean[ROWS][COLS];
		java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
		for (int r = 0; r < ROWS; r++) {
			for (int c = 0; c < COLS; c++) {
				if ((r == 0 || c == 0 || r == ROWS - 1 || c == COLS - 1) && !OUTLINE[r][c]) {
					outside[r][c] = true;
					queue.add(new int[] {r, c});
				}
			}
		}

		int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		while (!queue.isEmpty()) {
			int[] cell = queue.poll();
			for (int[] step : steps) {
				int r = cell[0] + step[0];
				int c = cell[1] + step[1];
				if (r >= 0 && r < ROWS && c >= 0 && c < COLS && !OUTLINE[r][c] && !outside[r][c]) {
					outside[r][c] = true;
					queue.add(new int[] {r, c});
				}
			}
		}

		for (int r = 0; r < ROWS; r++) {
			for (int c = 0; c < COLS; c++) {
				INSIDE[r][c] = !OUTLINE[r][c] && !outside[r][c];
			}
		}
	}

	private StarIcon() {
	}

	/** Ciemniejszy odcień koloru (obrys wypełnionej gwiazdki). */
	private static int darker(int argb) {
		int r = (argb >> 16 & 0xFF) * 60 / 100;
		int g = (argb >> 8 & 0xFF) * 60 / 100;
		int b = (argb & 0xFF) * 60 / 100;
		return argb & 0xFF000000 | r << 16 | g << 8 | b;
	}

	static void draw(GuiGraphicsExtractor context, int x, int y, boolean filled, int color) {
		context.pose().pushMatrix();
		context.pose().translate(x, y);
		context.pose().scale(0.5F, 0.5F);
		int edge = filled ? darker(color) : color;
		for (int r = 0; r < ROWS; r++) {
			for (int c = 0; c < COLS; c++) {
				if (OUTLINE[r][c]) {
					context.fill(c, r, c + 1, r + 1, edge);
				} else if (filled && INSIDE[r][c]) {
					context.fill(c, r, c + 1, r + 1, color);
				}
			}
		}

		context.pose().popMatrix();
	}
}
