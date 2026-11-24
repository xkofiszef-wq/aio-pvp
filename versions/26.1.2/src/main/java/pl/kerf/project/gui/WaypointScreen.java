package pl.kerf.project.gui;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
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
	/** Punkt, który właśnie edytujesz (null = dodajesz nowy). */
	private Waypoints.Point editing;

	public WaypointScreen(Screen parent) {
		super(Component.literal(Lang.t("Waypointy")));
		this.parent = parent;
		this.fillPosition();
	}


	/** Wpisuje twoją aktualną pozycję do pól X, Y, Z. */
	private void fillPosition() {
		Minecraft client = Minecraft.getInstance();
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
		this.addRenderableWidget(FlatButton.builder(Component.literal(Lang.t("Narysuj ikonę: ") + Lang.t(this.useIcon ? "Tak" : "Nie")), button -> {
			this.useIcon = !this.useIcon;
			if (this.useIcon && this.icon.isEmpty()) {
				this.minecraft.setScreen(new WaypointIconScreen(this, this.icon, value -> this.icon = value));
			} else {
				this.rebuildWidgets();
			}
		}).dimensions(inner, row, LEFT_W, 18).build());
		if (this.useIcon) {
			this.addRenderableWidget(FlatButton.builder(Component.literal(Lang.t("Edytuj ikonę")), button -> this.minecraft.setScreen(new WaypointIconScreen(this, this.icon, value -> this.icon = value)))
				.dimensions(inner, row + 22, LEFT_W - 22, 18).build());
		}

		// Przy edycji istniejącego punktu: "Zapisz" zmienia go, "Anuluj" wraca do dodawania nowego.
		String saveLabel = Lang.t(this.editing != null ? "Zapisz" : "Dodaj");
		int saveWidth = this.editing != null ? LEFT_W - 60 : LEFT_W;
		this.addRenderableWidget(FlatButton.builder(Component.literal(saveLabel), button -> this.add()).dimensions(inner, row + 44, saveWidth, 20).style(FlatButton.Style.PRIMARY).build());
		if (this.editing != null) {
			this.addRenderableWidget(FlatButton.builder(Component.literal(Lang.t("Anuluj")), button -> this.cancelEdit()).dimensions(inner + saveWidth + 4, row + 44, 56, 20).build());
		}
		this.formBottom = row + 44 + 20 + 14;

		// Wybierak koloru po prawej: kwadrat + pasek odcienia, a dalej pole hex i ostatnie kolory.
		int px = inner + LEFT_W + 14;
		this.picker.layout(px, top, PICKER);
		this.picker.setColor(this.color);
		int hexX = px + PICKER + 6 + 8 + 8;
		this.addRenderableWidget(this.picker.hexField(this.font, hexX, top, Math.max(50, inner + content - hexX)));
		this.pickerRecentX = hexX;
		this.pickerRecentY = top + 28;
		this.addRenderableWidget(FlatButton.builder(CommonComponents.GUI_DONE, button -> this.onClose()).dimensions(inner, this.height - Ui.PAD - 28, content, 20).build());
	}

	private void field(int fx, int fy, int fw, String hint, String value, int max, String regex, Consumer<String> setter) {
		EditBox field = new EditBox(this.font, fx, fy, fw, 18, Component.literal(hint));
		field.setMaxLength(max);
		field.setValue(value);
		field.setHint(Component.literal(hint));
		// Ta wersja Minecrafta nie ma filtra znaków w polu tekstowym: niepasujący tekst wracamy do ostatniego poprawnego.
		String[] last = {value};
		field.setResponder(text -> {
			if (regex != null && !text.matches(regex)) {
				field.setValue(last[0]);
				return;
			}

			last[0] = text;
			setter.accept(text);
		});
		this.addRenderableWidget(field);
	}

	private void add() {
		Minecraft client = Minecraft.getInstance();
		try {
			String icon = this.useIcon ? this.icon : "";
			int px = Integer.parseInt(this.x);
			int py = Integer.parseInt(this.y);
			int pz = Integer.parseInt(this.z);
			boolean edit = this.editing != null;
			String error = edit
				? Modules.WAYPOINTS.update(client, this.editing, this.name, px, py, pz, this.color, icon)
				: Modules.WAYPOINTS.create(client, this.name, px, py, pz, this.color, icon);
			this.message = error != null ? error : Lang.t(edit ? "Zapisano waypoint: " : "Dodano waypoint: ") + this.name;
			this.messageColor = error != null ? Theme.DANGER : Theme.SUCCESS;
			if (error == null) {
				this.editing = null;
				this.name = "";
				this.icon = "";
				this.useIcon = false;
				this.fillPosition();
				this.rebuildWidgets();
			}
		} catch (NumberFormatException e) {
			this.message = Lang.t("Podaj współrzędne X, Y i Z.");
			this.messageColor = Theme.DANGER;
		}
	}

	/** Klik w punkt na liście: jego dane trafiają do formularza, a "Dodaj" zmienia się w "Zapisz". */
	private void startEdit(Waypoints.Point point) {
		this.editing = point;
		this.name = point.name();
		this.x = String.valueOf(point.x());
		this.y = String.valueOf(point.y());
		this.z = String.valueOf(point.z());
		this.color = point.color();
		this.useIcon = !point.icon().isEmpty();
		this.icon = point.icon();
		this.message = Lang.t("Edytujesz: ") + point.name();
		this.messageColor = Theme.WARNING;
		this.rebuildWidgets();
	}

	private void cancelEdit() {
		this.editing = null;
		this.name = "";
		this.icon = "";
		this.useIcon = false;
		this.message = "";
		this.fillPosition();
		this.rebuildWidgets();
	}

	private int listTop() {
		return this.formBottom + 16;
	}

	private int visibleRows() {
		return Math.max(1, (this.height - Ui.PAD - 34 - this.listTop()) / ROW);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
		super.extractBackground(context, mouseX, mouseY, deltaTicks);
		Ui.panel(context, this.left, Ui.PAD, this.panelW, this.height - 2 * Ui.PAD);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
		int inner = this.left + 8;
		context.text(this.font, this.title, inner, Ui.PAD + 8, Theme.TEXT, false);
		this.picker.render(context);
		this.picker.renderRecent(context, this.pickerRecentX, this.pickerRecentY);
		this.drawIcon(context, this.useIcon ? this.icon : "", inner + LEFT_W - 18, Ui.PAD + 22 + 44 + 22 + 2);
		context.text(this.font, this.message, inner, this.formBottom - 10, this.messageColor, false);

		List<Waypoints.Point> list = Modules.WAYPOINTS.here(this.minecraft);
		int top = this.listTop();
		if (list.isEmpty()) {
			context.text(this.font, Lang.t("Brak waypointów w tym świecie."), inner, top + 4, Theme.TEXT, false);
		}

		this.scroll = Math.max(0, Math.min(this.scroll, Math.max(0, list.size() - this.visibleRows())));
		for (int i = 0; i < this.visibleRows() && i + this.scroll < list.size(); i++) {
			Waypoints.Point point = list.get(i + this.scroll);
			int ry = top + i * ROW;
			context.fill(inner, ry, inner + this.panelW - 16, ry + ROW - 2, point.equals(this.editing) ? Theme.SURFACE_HOVER : Theme.SURFACE);
			if (point.icon().isEmpty()) {
				context.fill(inner + 4, ry + 4, inner + 18, ry + 16, point.color());
			} else {
				this.drawIcon(context, point.icon(), inner + 2, ry + 1);
			}

			double distance = Math.sqrt(this.minecraft.player.distanceToSqr(point.x() + 0.5, point.y(), point.z() + 0.5));
			context.text(this.font, Ui.ellipsis(this.font, point.name(), this.panelW - 80), inner + 24, ry + 2, point.color(), false);
			context.text(this.font, point.x() + " " + point.y() + " " + point.z() + "  " + Math.round(distance) + "m", inner + 24, ry + 11, 0xFFA0A4B0, false);
			int dx = inner + this.panelW - 16 - 20;
			boolean hover = Ui.inside(mouseX, mouseY, dx, ry + 1, 18, ROW - 4);
			context.fill(dx, ry + 1, dx + 18, ry + ROW - 3, hover ? Theme.DANGER : Theme.SURFACE_HOVER);
			context.text(this.font, "X", dx + 6, ry + 6, Theme.TEXT, false);
		}

		super.extractRenderState(context, mouseX, mouseY, deltaTicks);
	}

	/** Ikona 15x15 w skali 1:1 (bez ikony nic nie rysuje). */
	private void drawIcon(GuiGraphicsExtractor context, String icon, int ix, int iy) {
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
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		int inner = this.left + 8;
		if (this.picker.click(click.x(), click.y())) {
			this.color = this.picker.color();
			return true;
		}

		if (this.picker.clickRecent(click.x(), click.y(), this.pickerRecentX, this.pickerRecentY)) {
			this.color = this.picker.color();
			return true;
		}

		List<Waypoints.Point> list = Modules.WAYPOINTS.here(this.minecraft);
		for (int i = 0; i < this.visibleRows() && i + this.scroll < list.size(); i++) {
			if (Ui.inside(click.x(), click.y(), inner + this.panelW - 16 - 20, this.listTop() + i * ROW + 1, 18, ROW - 4)) {
				Waypoints.Point removed = list.get(i + this.scroll);
				Modules.WAYPOINTS.delete(removed);
				if (removed.equals(this.editing)) {
					this.cancelEdit();
				}

				return true;
			}

			// Klik w resztę wiersza wybiera punkt do edycji.
			if (Ui.inside(click.x(), click.y(), inner, this.listTop() + i * ROW, this.panelW - 16 - 22, ROW - 2)) {
				this.startEdit(list.get(i + this.scroll));
				return true;
			}
		}

		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
		if (this.picker.dragged(click.x(), click.y())) {
			this.color = this.picker.color();
			return true;
		}

		return super.mouseDragged(click, offsetX, offsetY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent click) {
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
	public void onClose() {
		this.minecraft.setScreen(this.parent);
	}
}
