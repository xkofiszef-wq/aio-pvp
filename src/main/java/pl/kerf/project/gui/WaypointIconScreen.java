package pl.kerf.project.gui;

import java.util.function.Consumer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import pl.kerf.project.Lang;
import pl.kerf.project.config.PixelGrid;
import pl.kerf.project.gui.theme.FlatButton;
import pl.kerf.project.gui.theme.Theme;

/** Mały edytor ikony waypointu: siatka pikseli i wybierak koloru jak w Blockbenchu; lewy przycisk rysuje, prawy gumkuje. */
public class WaypointIconScreen extends Screen {
	private static final int N = PixelGrid.SIZE;
	private static final int PICKER = 80;
	private final Screen parent;
	private final int[] grid;
	private final Consumer<String> onDone;
	private int color = 0xFFFFFFFF;
	private final ColorPicker picker = new ColorPicker(0xFFFFFFFF, argb -> this.color = argb);
	private int cell;
	private int canvasX;
	private int canvasY;
	private int sideX;
	private int recentY;
	private boolean painting;
	private int paintValue;

	public WaypointIconScreen(Screen parent, String icon, Consumer<String> onDone) {
		super(Text.literal(Lang.t("Ikona waypointu")));
		this.parent = parent;
		this.grid = PixelGrid.decode(icon).clone();
		this.onDone = onDone;
	}

	private int panelW() {
		return this.cell * N + 16 + PICKER + 8 + 6 + 8 + 16 + 64 + 16;
	}

	@Override
	protected void init() {
		this.cell = Math.max(4, Math.min(12, (this.height - 2 * Ui.PAD - 40) / N));
		int canvas = this.cell * N;
		int left = (this.width - this.panelW()) / 2;
		this.canvasX = left + 8;
		this.canvasY = Ui.PAD + 22;
		this.sideX = this.canvasX + canvas + 16;
		this.picker.layout(this.sideX, this.canvasY, PICKER);
		this.picker.setColor(this.color);
		int hexX = this.sideX + PICKER + 6 + 8 + 8;
		this.addDrawableChild(this.picker.hexField(this.textRenderer, hexX, this.canvasY, 64));
		this.recentY = this.canvasY + 28;
		int by = this.canvasY + PICKER + 8;
		this.addDrawableChild(FlatButton.builder(Text.literal(Lang.t("Wyczyść")), button -> java.util.Arrays.fill(this.grid, 0)).dimensions(this.sideX, by, 90, 20).build());
		this.addDrawableChild(FlatButton.builder(ScreenTexts.DONE, button -> this.close()).dimensions(this.sideX + 94, by, 90, 20).style(FlatButton.Style.PRIMARY).build());
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
		super.renderBackground(context, mouseX, mouseY, deltaTicks);
		Ui.panel(context, this.canvasX - 8, Ui.PAD, this.panelW(), this.cell * N + 22 + 16);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
		context.drawText(this.textRenderer, this.title, this.canvasX, Ui.PAD + 8, Theme.TEXT, false);
		int canvas = this.cell * N;
		context.fill(this.canvasX - 1, this.canvasY - 1, this.canvasX + canvas + 1, this.canvasY + canvas + 1, 0xFF000000);
		for (int y = 0; y < N; y++) {
			for (int x = 0; x < N; x++) {
				int px = this.canvasX + x * this.cell;
				int py = this.canvasY + y * this.cell;
				context.fill(px, py, px + this.cell, py + this.cell, (x + y) % 2 == 0 ? 0xFF2A2A30 : 0xFF32323A);
				if (this.grid[y * N + x] >>> 24 != 0) {
					context.fill(px, py, px + this.cell, py + this.cell, this.grid[y * N + x]);
				}
			}
		}

		this.picker.render(context);
		this.picker.renderRecent(context, this.sideX + PICKER + 6 + 8 + 8, this.recentY);
		context.drawText(this.textRenderer, Lang.t("Lewy: rysuj, prawy: gumka"), this.sideX, this.canvasY + PICKER + 32, Theme.TEXT, false);
		super.render(context, mouseX, mouseY, deltaTicks);
	}

	private boolean onCanvas(double mx, double my) {
		return Ui.inside(mx, my, this.canvasX, this.canvasY, this.cell * N, this.cell * N);
	}

	private void paint(double mx, double my) {
		this.grid[(int) ((my - this.canvasY) / this.cell) * N + (int) ((mx - this.canvasX) / this.cell)] = this.paintValue;
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		if (this.onCanvas(click.x(), click.y())) {
			this.painting = true;
			this.paintValue = click.button() == 1 ? 0 : this.color;
			this.paint(click.x(), click.y());
			return true;
		}

		if (this.picker.click(click.x(), click.y()) || this.picker.clickRecent(click.x(), click.y(), this.sideX + PICKER + 6 + 8 + 8, this.recentY)) {
			return true;
		}

		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseDragged(Click click, double offsetX, double offsetY) {
		if (this.picker.dragged(click.x(), click.y())) {
			return true;
		}

		if (this.painting && this.onCanvas(click.x(), click.y())) {
			this.paint(click.x(), click.y());
			return true;
		}

		return super.mouseDragged(click, offsetX, offsetY);
	}

	@Override
	public boolean mouseReleased(Click click) {
		this.painting = false;
		if (this.picker.release()) {
			return true;
		}

		return super.mouseReleased(click);
	}

	@Override
	public void close() {
		this.onDone.accept(PixelGrid.encode(this.grid));
		this.client.setScreen(this.parent);
	}
}
