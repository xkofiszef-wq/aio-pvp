package pl.kerf.project.gui;

import java.util.function.Consumer;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
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
		super(Component.literal(Lang.t("Ikona waypointu")));
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
		this.addRenderableWidget(this.picker.hexField(this.font, hexX, this.canvasY, 64));
		this.recentY = this.canvasY + 28;
		int by = this.canvasY + PICKER + 8;
		this.addRenderableWidget(FlatButton.builder(Component.literal(Lang.t("Wyczyść")), button -> java.util.Arrays.fill(this.grid, 0)).dimensions(this.sideX, by, 90, 20).build());
		this.addRenderableWidget(FlatButton.builder(CommonComponents.GUI_DONE, button -> this.onClose()).dimensions(this.sideX + 94, by, 90, 20).style(FlatButton.Style.PRIMARY).build());
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
		super.extractBackground(context, mouseX, mouseY, deltaTicks);
		Ui.panel(context, this.canvasX - 8, Ui.PAD, this.panelW(), this.cell * N + 22 + 16);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
		context.text(this.font, this.title, this.canvasX, Ui.PAD + 8, Theme.TEXT, false);
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
		context.text(this.font, Lang.t("Lewy: rysuj, prawy: gumka"), this.sideX, this.canvasY + PICKER + 32, Theme.TEXT, false);
		super.extractRenderState(context, mouseX, mouseY, deltaTicks);
	}

	private boolean onCanvas(double mx, double my) {
		return Ui.inside(mx, my, this.canvasX, this.canvasY, this.cell * N, this.cell * N);
	}

	private void paint(double mx, double my) {
		this.grid[(int) ((my - this.canvasY) / this.cell) * N + (int) ((mx - this.canvasX) / this.cell)] = this.paintValue;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
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
	public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
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
	public boolean mouseReleased(MouseButtonEvent click) {
		this.painting = false;
		if (this.picker.release()) {
			return true;
		}

		return super.mouseReleased(click);
	}

	@Override
	public void onClose() {
		this.onDone.accept(PixelGrid.encode(this.grid));
		this.minecraft.setScreen(this.parent);
	}
}
