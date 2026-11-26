package pl.kerf.project.gui;

import net.minecraft.client.gui.GuiGraphics;

/** Konfetti z pikseli (ikona przycisku "Party" w pasku bocznym): rozrzucone kolorowe kwadraciki 2x2 w polu 12x11. */
final class PartyIcon {
	/** x, y, kolor */
	private static final int[][] CONFETTI = {
		{5, 0, 0xFFFFD25E}, {1, 1, 0xFFE5675F}, {9, 1, 0xFF4FC3D9}, {4, 3, 0xFFFF9A3C},
		{7, 4, 0xFFB87CFF}, {1, 5, 0xFF6CC98E}, {5, 6, 0xFFE5675F}, {10, 6, 0xFFFFD25E},
		{3, 8, 0xFF4FC3D9}, {8, 9, 0xFF6CC98E}, {6, 10, 0xFFB87CFF}
	};

	private PartyIcon() {
	}

	static void draw(GuiGraphics context, int x, int y) {
		for (int[] piece : CONFETTI) {
			context.fill(x + piece[0], y + piece[1], x + piece[0] + 2, y + piece[1] + 2, piece[2]);
		}
	}
}
