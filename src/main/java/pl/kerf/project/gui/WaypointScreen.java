package pl.kerf.project.gui;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import pl.kerf.project.Lang;
import pl.kerf.project.config.PixelGrid;
import pl.kerf.project.gui.theme.FlatButton;
import pl.kerf.project.gui.theme.Theme;
import pl.kerf.project.module.Modules;
import pl.kerf.project.module.Waypoints;

/**
 * Menu waypointów: formularz (nazwa, współrzędne, kolor z wybieraka, opcjonalna ikona) i lista z usuwaniem.
 * Współrzędne wpisują się same z twojej pozycji; zmieniasz je tylko, gdy chcesz punkt gdzie indziej.
 */
public class WaypointScreen extends Screen {
	private static final int ROW = 22;
	private static final int LEFT_W = 150;
	private static final int PICKER = 70;
	private final Screen parent;
	// Stan formularza żyje w polach, bo init() odtwarza widżety (np. po powrocie z edytora ikony).
	private String name = "";
	private String x = "";
	private String y = "";
	private String z = "";
	private boolean useIcon;
	private String icon = "";
	private String message = "";
	private int messageColor = Theme.TEXT;
	private int scroll;
	private int left;
	private int panelW;
	private int formBottom;
	private int pickerRecentX;
	private int pickerRecentY;
	private final ColorPicker picker = new ColorPicker(0xFF30FFFF, argb -> this.color = argb);
	private int color = 0xFF30FFFF;

	public WaypointScreen(Screen parent) {
		super(Text.literal(Lang.t("Waypointy")));
		this.parent = parent;
		this.fillPosition();
	}


	/** Wpisuje twoją aktualną pozycję do pól X, Y, Z. */
	private void fillPosition() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player != null) {
			this.x = String.valueOf(client.player.getBlockX());
			this.y = String.valueOf(client.player.getBlockY());
			this.z = String.valueOf(client.player.getBlockZ());
		}
	}

	@Override
	protected void init() {
		this.panelW = Math.min(this.width - 12, 340);
		this.left = (this.width - this.panelW) / 2;
		int inner = this.left + 8;
		int content = this.panelW - 16;
		int top = Ui.PAD + 22;
		this.field(inner, top, LEFT_W, Lang.t("Nazwa"), this.name, 32, null, value -> this.name = value);
		int third = (LEFT_W - 8) / 3;
		this.field(inner, top + 22, third, "X", this.x, 8, "-?\\d*", value -> this.x = value);
		this.field(inner + third + 4, top + 22, third, "Y", this.y, 8, "-?\\d*", value -> this.y = value);
		this.field(inner + 2 * (third + 4), top + 22, third, "Z", this.z, 8, "-?\\d*", value -> this.z = value);
		int row = top + 44;
		this.addDrawableChild(FlatButton.builder(Text.literal(Lang.t("Narysuj ikonę: ") + Lang.t(this.useIcon ? "Tak" : "Nie")), button -> {
			this.useIcon = !this.useIcon;
			if (this.useIcon && this.icon.isEmpty()) {
				this.client.setScreen(new WaypointIconScreen(this, this.icon, value -> this.icon = value));
			} else {
				this.clearAndInit();
			}
		}).dimensions(inner, row, LEFT_W, 18).build());
		if (this.useIcon) {
			this.addDrawableChild(FlatButton.builder(Text.literal(Lang.t("Edytuj ikonę")), button -> this.client.setScreen(new WaypointIconScreen(this, this.icon, value -> this.icon = value)))
				.dimensions(inner, row + 22, LEFT_W - 22, 18).build());
		}

		this.addDrawableChild(FlatButton.builder(Text.literal(Lang.t("Dodaj")), button -> this.add()).dimensions(inner, row + 44, LEFT_W, 20).style(FlatButton.Style.PRIMARY).build());
		this.formBottom = row + 44 + 20 + 14;

		// Wybierak koloru po prawej: kwadrat + pasek odcienia, a dalej pole hex i ostatnie kolory.
		int px = inner + LEFT_W + 14;
		this.picker.layout(px, top, PICKER);
		this.picker.setColor(this.color);
		int hexX = px + PICKER + 6 + 8 + 8;
		this.addDrawableChild(this.picker.hexField(this.textRenderer, hexX, top, Math.max(50, inner + content - hexX)));
		this.pickerRecentX = hexX;
		this.pickerRecentY = top + 28;
		this.addDrawableChild(FlatButton.builder(ScreenTexts.DONE, button -> this.close()).dimensions(inner, this.height - Ui.PAD - 28, content, 20).build());
	}

	private void field(int fx, int fy, int fw, String hint, String value, int max, String regex, Consumer<String> setter) {
		TextFieldWidget field = new TextFieldWidget(this.textRenderer, fx, fy, fw, 18, Text.literal(hint));
		field.setMaxLength(max);
		if (regex != null) {
			field.setTextPredicate(text -> text.matches(regex));
		}

		field.setText(value);
		field.setPlaceholder(Text.literal(hint));
		field.setChangedListener(setter);
		this.addDrawableChild(field);
	}

	private void add() {
		MinecraftClient client = MinecraftClient.getInstance();
		try {
			String icon = this.useIcon ? this.icon : "";
			String error = Modules.WAYPOINTS.create(client, this.name, Integer.parseInt(this.x), Integer.parseInt(this.y), Integer.parseInt(this.z), this.color, icon);
			this.message = error != null ? error : Lang.t("Dodano waypoint: ") + this.name;
			this.messageColor = error != null ? Theme.DANGER : Theme.SUCCESS;
			if (error == null) {
				this.name = "";
				this.icon = "";
				this.useIcon = false;
				this.fillPosition();
				this.clearAndInit();
			}
		} catch (NumberFormatException e) {
			this.message = Lang.t("Podaj współrzędne X, Y i Z.");
			this.messageColor = Theme.DANGER;
		}
	}

	private int listTop() {
		return this.formBottom + 16;
	}

	private int visibleRows() {
		return Math.max(1, (this.height - Ui.PAD - 34 - this.listTop()) / ROW);
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
		super.renderBackground(context, mouseX, mouseY, deltaTicks);
		Ui.panel(context, this.left, Ui.PAD, this.panelW, this.height - 2 * Ui.PAD);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
		int inner = this.left + 8;
		context.drawText(this.textRenderer, this.title, inner, Ui.PAD + 8, Theme.TEXT, false);
		this.picker.render(context);
		this.picker.renderRecent(context, this.pickerRecentX, this.pickerRecentY);
		this.drawIcon(context, this.useIcon ? this.icon : "", inner + LEFT_W - 18, Ui.PAD + 22 + 44 + 22 + 2);
		context.drawText(this.textRenderer, this.message, inner, this.formBottom - 10, this.messageColor, false);

		List<Waypoints.Point> list = Modules.WAYPOINTS.here(this.client);
		int top = this.listTop();
		if (list.isEmpty()) {
			context.drawText(this.textRenderer, Lang.t("Brak waypointów w tym świecie."), inner, top + 4, Theme.TEXT, false);
		}

		this.scroll = Math.max(0, Math.min(this.scroll, Math.max(0, list.size() - this.visibleRows())));
		for (int i = 0; i < this.visibleRows() && i + this.scroll < list.size(); i++) {
			Waypoints.Point point = list.get(i + this.scroll);
			int ry = top + i * ROW;
			context.fill(inner, ry, inner + this.panelW - 16, ry + ROW - 2, Theme.SURFACE);
			if (point.icon().isEmpty()) {
				context.fill(inner + 4, ry + 4, inner + 18, ry + 16, point.color());
			} else {
				this.drawIcon(context, point.icon(), inner + 2, ry + 1);
			}

			double distance = Math.sqrt(this.client.player.squaredDistanceTo(point.x() + 0.5, point.y(), point.z() + 0.5));
			context.drawText(this.textRenderer, Ui.ellipsis(this.textRenderer, point.name(), this.panelW - 80), inner + 24, ry + 2, point.color(), false);
			context.drawText(this.textRenderer, point.x() + " " + point.y() + " " + point.z() + "  " + Math.round(distance) + "m", inner + 24, ry + 11, 0xFFA0A4B0, false);
			int dx = inner + this.panelW - 16 - 20;
			boolean hover = Ui.inside(mouseX, mouseY, dx, ry + 1, 18, ROW - 4);
			context.fill(dx, ry + 1, dx + 18, ry + ROW - 3, hover ? Theme.DANGER : Theme.SURFACE_HOVER);
			context.drawText(this.textRenderer, "X", dx + 6, ry + 6, Theme.TEXT, false);
		}

		super.render(context, mouseX, mouseY, deltaTicks);
	}

	/** Ikona 15x15 w skali 1:1 (bez ikony nic nie rysuje). */
	private void drawIcon(DrawContext context, String icon, int ix, int iy) {
		int[] pixels = PixelGrid.decode(icon);
		for (int i = 0; i < pixels.length; i++) {
			if (pixels[i] >>> 24 != 0) {
				int px = ix + i % PixelGrid.SIZE;
				int py = iy + i / PixelGrid.SIZE;
				context.fill(px, py, px + 1, py + 1, pixels[i]);
			}
		}
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		int inner = this.left + 8;
		if (this.picker.click(click.x(), click.y())) {
			this.color = this.picker.color();
			return true;
		}

		if (this.picker.clickRecent(click.x(), click.y(), this.pickerRecentX, this.pickerRecentY)) {
			this.color = this.picker.color();
			return true;
		}

		List<Waypoints.Point> list = Modules.WAYPOINTS.here(this.client);
		for (int i = 0; i < this.visibleRows() && i + this.scroll < list.size(); i++) {
			if (Ui.inside(click.x(), click.y(), inner + this.panelW - 16 - 20, this.listTop() + i * ROW + 1, 18, ROW - 4)) {
				Modules.WAYPOINTS.delete(list.get(i + this.scroll));
				return true;
			}
		}

		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseDragged(Click click, double offsetX, double offsetY) {
		if (this.picker.dragged(click.x(), click.y())) {
			this.color = this.picker.color();
			return true;
		}

		return super.mouseDragged(click, offsetX, offsetY);
	}

	@Override
	public boolean mouseReleased(Click click) {
		if (this.picker.release()) {
			return true;
		}

		return super.mouseReleased(click);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		this.scroll -= (int) Math.signum(verticalAmount);
		return true;
	}

	@Override
	public void close() {
		this.client.setScreen(this.parent);
	}
}
