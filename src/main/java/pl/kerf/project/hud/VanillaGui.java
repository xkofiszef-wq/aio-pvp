package pl.kerf.project.hud;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * Tła w stylu vanilla rysowane z atlasu GUI gry. Bo to te same sprite'y, których używa hotbar i ekwipunek,
 * texture pack, który je podmienia, zmienia też wygląd HUD-u moda.
 */
public final class VanillaGui {
	/** Odstęp między slotami w hotbarze. */
	public static final int SLOT_PITCH = 20;
	/** O ile ramka slotu wystaje poza ikonę przedmiotu 16x16. */
	public static final int SLOT_MARGIN = 3;
	public static final Identifier EFFECT_PANEL = Identifier.ofVanilla("container/inventory/effect_background");
	public static final Identifier EFFECT_PANEL_AMBIENT = Identifier.ofVanilla("container/inventory/effect_background_ambient");
	private static final Identifier HOTBAR = Identifier.ofVanilla("hud/hotbar");
	private static final int HOTBAR_W = 182;
	private static final int HOTBAR_H = 22;

	private VanillaGui() {
	}

	/**
	 * Rząd slotów jak w hotbarze. (itemX, itemY) to pozycja ikony pierwszego przedmiotu, kolejne co {@link #SLOT_PITCH}.
	 *
	 * <p>Hotbar ma czarną krawędź 1 px, potem powtarzalne komórki po 20 px. W poziomie składamy: lewa krawędź,
	 * N komórek, prawa krawędź. W pionie tekstura nie ma odpowiednika, więc każdy slot to osobne pudełko
	 * 22x22, a kolejne nachodzą na siebie o 2 px, przez co między slotami zostaje jedna czarna linia.
	 */
	public static void slots(DrawContext context, int itemX, int itemY, int count, boolean horizontal) {
		int x = itemX - SLOT_MARGIN;
		int y = itemY - SLOT_MARGIN;
		if (count <= 0) {
			return;
		}

		if (horizontal) {
			region(context, 0, x, y, 1);
			for (int i = 0; i < count; i++) {
				region(context, 1 + i % 9 * SLOT_PITCH, x + 1 + i * SLOT_PITCH, y, SLOT_PITCH);
			}

			region(context, HOTBAR_W - 1, x + 1 + count * SLOT_PITCH, y, 1);
			return;
		}

		for (int i = 0; i < count; i++) {
			int boxY = y + i * SLOT_PITCH;
			region(context, 0, x, boxY, 1);
			region(context, 1, x + 1, boxY, SLOT_PITCH);
			region(context, HOTBAR_W - 1, x + 1 + SLOT_PITCH, boxY, 1);
		}
	}

	private static void region(DrawContext context, int u, int x, int y, int width) {
		context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, HOTBAR, HOTBAR_W, HOTBAR_H, u, 0, x, y, width, HOTBAR_H);
	}

	/** Panel efektu jak w ekwipunku (tekstura 9-slice, więc dowolna szerokość). */
	public static void effectPanel(DrawContext context, int x, int y, int width, int height, boolean ambient) {
		context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, ambient ? EFFECT_PANEL_AMBIENT : EFFECT_PANEL, x, y, width, height);
	}
}
