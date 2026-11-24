package pl.kerf.project.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Mały wybierak koloru jak w Blockbenchu: kwadrat nasycenie/jasność, pasek odcienia, pole hex i ostatnio użyte kolory.
 * Rysuje się i obsługuje mysz wewnątrz ekranu, który go trzyma (ekran woła render/click/drag/release);
 * pole hex jest zwykłym widżetem, który ekran dodaje sam.
 */
public final class ColorPicker {
	private static final int BAR_W = 8;
	private static final int RECENT_MAX = 10;
	/** Ostatnio używane kolory, wspólne dla wszystkich wybieraków (najnowszy pierwszy). */
	private static final List<Integer> RECENT = new ArrayList<>(List.of(0xFFFF3030, 0xFF30FF50, 0xFF3080FF, 0xFFFFE030, 0xFFFFFFFF, 0xFF000000));

	private final IntConsumer onChange;
	private EditBox hex;
	private boolean syncing;
	/** HSV trzymane osobno, żeby odcień nie ginął przy szarościach i czerni. */
	private float hue;
	private float sat = 1.0F;
	private float val = 1.0F;
	private int drag;
	private int x;
	private int y;
	private int size;

	public ColorPicker(int argb, IntConsumer onChange) {
		this.onChange = onChange;
		this.setColor(argb);
	}

	/** Pole kolor: nieprzezroczysty ARGB. */
	public int color() {
		return 0xFF000000 | Mth.hsvToRgb(this.hue, this.sat, this.val) & 0xFFFFFF;
	}

	public void setColor(int argb) {
		float r = (argb >> 16 & 0xFF) / 255.0F;
		float g = (argb >> 8 & 0xFF) / 255.0F;
		float b = (argb & 0xFF) / 255.0F;
		float max = Math.max(r, Math.max(g, b));
		float min = Math.min(r, Math.min(g, b));
		float d = max - min;
		this.val = max;
		this.sat = max == 0.0F ? 0.0F : d / max;
		if (d > 0.0F) {
			float h = max == r ? (g - b) / d % 6.0F : max == g ? (b - r) / d + 2.0F : (r - g) / d + 4.0F;
			this.hue = (h / 6.0F + 1.0F) % 1.0F;
		}

		this.syncHex();
	}

	/** Ustawia położenie: kwadrat size x size w (x, y), tuż obok pasek odcienia. */
	public void layout(int x, int y, int size) {
		this.x = x;
		this.y = y;
		this.size = size;
	}

	/** Pole hex do dodania na ekran (szerokość w px); zmiana tekstu zmienia kolor. */
	public EditBox hexField(Font font, int fx, int fy, int fw) {
		this.hex = new EditBox(font, fx, fy, fw, 18, Component.literal("Hex"));
		this.hex.setMaxLength(7);
		this.hex.setResponder(text -> {
			if (this.syncing) {
				return;
			}

			String digits = text.startsWith("#") ? text.substring(1) : text;
			if (digits.length() == 6) {
				try {
					this.setColorQuiet(0xFF000000 | Integer.parseInt(digits, 16));
					this.commit();
				} catch (NumberFormatException ignored) {
					// Niepełny albo błędny kod: czekamy na poprawny.
				}
			}
		});
		this.syncHex();
		return this.hex;
	}

	private void setColorQuiet(int argb) {
		boolean was = this.syncing;
		this.syncing = true;
		this.setColor(argb);
		this.syncing = was;
		this.onChange.accept(this.color());
	}

	private void syncHex() {
		if (this.hex != null) {
			this.syncing = true;
			this.hex.setValue(String.format("#%06x", this.color() & 0xFFFFFF));
			this.syncing = false;
		}
	}

	/** Zapamiętuje bieżący kolor na liście ostatnich (wołane po puszczeniu myszy i po wpisaniu hex). */
	private void commit() {
		RECENT.remove((Integer) this.color());
		RECENT.add(0, this.color());
		while (RECENT.size() > RECENT_MAX) {
			RECENT.remove(RECENT.size() - 1);
		}
	}

	private int barX() {
		return this.x + this.size + 6;
	}

	/** Pierwsza kratka ostatnich kolorów (pod polem hex), 5 w rzędzie. */
	public void renderRecent(GuiGraphicsExtractor context, int rx, int ry) {
		for (int i = 0; i < RECENT.size(); i++) {
			int cx = rx + i % 5 * 14;
			int cy = ry + i / 5 * 14;
			context.fill(cx - 1, cy - 1, cx + 11, cy + 11, RECENT.get(i) == this.color() ? 0xFFFFFFFF : 0xFF000000);
			context.fill(cx, cy, cx + 10, cy + 10, RECENT.get(i));
		}
	}

	/** Klik w ostatnie kolory narysowane od (rx, ry); zwraca true, gdy trafił. */
	public boolean clickRecent(double mx, double my, int rx, int ry) {
		for (int i = 0; i < RECENT.size(); i++) {
			if (Ui.inside(mx, my, rx + i % 5 * 14, ry + i / 5 * 14, 10, 10)) {
				this.setColorQuiet(RECENT.get(i));
				this.syncHex();
				return true;
			}
		}

		return false;
	}

	public void render(GuiGraphicsExtractor context) {
		// Kwadrat: kolumna = nasycenie (0..1), od góry jasność 1 do dołu 0.
		context.fill(this.x - 1, this.y - 1, this.x + this.size + 1, this.y + this.size + 1, 0xFF000000);
		for (int i = 0; i < this.size; i++) {
			int top = 0xFF000000 | Mth.hsvToRgb(this.hue, i / (float) (this.size - 1), 1.0F) & 0xFFFFFF;
			context.fillGradient(this.x + i, this.y, this.x + i + 1, this.y + this.size, top, 0xFF000000);
		}

		int mx = this.x + Math.round(this.sat * (this.size - 1));
		int my = this.y + Math.round((1.0F - this.val) * (this.size - 1));
		context.outline(mx - 2, my - 2, 5, 5, 0xFFFFFFFF);
		context.outline(mx - 3, my - 3, 7, 7, 0xFF000000);
		// Pasek odcienia.
		int bx = this.barX();
		context.fill(bx - 1, this.y - 1, bx + BAR_W + 1, this.y + this.size + 1, 0xFF000000);
		for (int j = 0; j < this.size; j++) {
			context.fill(bx, this.y + j, bx + BAR_W, this.y + j + 1, 0xFF000000 | Mth.hsvToRgb(j / (float) this.size, 1.0F, 1.0F) & 0xFFFFFF);
		}

		int hy = this.y + Math.round(this.hue * (this.size - 1));
		context.outline(bx - 2, hy - 1, BAR_W + 4, 3, 0xFFFFFFFF);
	}

	/** Początek przeciągania, gdy klik trafił w kwadrat albo pasek. */
	public boolean click(double mx, double my) {
		if (Ui.inside(mx, my, this.x, this.y, this.size, this.size)) {
			this.drag = 1;
		} else if (Ui.inside(mx, my, this.barX(), this.y, BAR_W, this.size)) {
			this.drag = 2;
		} else {
			return false;
		}

		this.dragTo(mx, my);
		return true;
	}

	public boolean dragged(double mx, double my) {
		if (this.drag == 0) {
			return false;
		}

		this.dragTo(mx, my);
		return true;
	}

	public boolean release() {
		boolean was = this.drag != 0;
		this.drag = 0;
		if (was) {
			this.commit();
		}

		return was;
	}

	private void dragTo(double mx, double my) {
		float fx = Mth.clamp((float) (mx - this.x) / (this.size - 1), 0.0F, 1.0F);
		float fy = Mth.clamp((float) (my - this.y) / (this.size - 1), 0.0F, 1.0F);
		if (this.drag == 1) {
			this.sat = fx;
			this.val = 1.0F - fy;
		} else {
			this.hue = Math.min(fy, 0.999F);
		}

		this.syncHex();
		this.onChange.accept(this.color());
	}
}
