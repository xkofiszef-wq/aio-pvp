package pl.kerf.project.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
//? if >=26.1.2 {
/*import net.minecraft.client.input.MouseButtonEvent;
*///?}
import net.minecraft.client.gui.GuiGraphics;
//? if >=26.1.2 {
/*import net.minecraft.client.gui.screens.Screen;
*///?}
import net.minecraft.client.gui.components.EditBox;
//? if <26.1.2 {
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
//?}
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import pl.kerf.project.Lang;
import pl.kerf.project.gui.theme.FlatButton;
import pl.kerf.project.gui.theme.Theme;
import pl.kerf.project.module.Modules;
import pl.kerf.project.module.Sounds;

/** Lista wszystkich dźwięków Minecrafta z wyszukiwarką i suwakiem głośności (0-200%) przy każdym. Prawy przycisk resetuje wiersz. */
public class SoundsScreen extends Screen {
	private static final int ROW = 20;
	private static final int SLIDER_W = 110;
	private final Screen parent;
	private final List<String> all = new ArrayList<>();
	private List<String> shown = List.of();
	private EditBox search;
	private String query = "";
	private int scroll;
	private int left;
	private int panelW;
	private int listTop;
	private String dragging;

	public SoundsScreen(Screen parent) {
		super(Component.literal(Lang.t("Dźwięki")));
		this.parent = parent;
		BuiltInRegistries.SOUND_EVENT.keySet().forEach(id -> this.all.add(id.toString()));
		this.all.sort(String::compareTo);
		this.filter();
	}

	private void filter() {
		String q = this.query.toLowerCase(Locale.ROOT).strip();
		this.shown = q.isEmpty() ? this.all : this.all.stream().filter(id -> id.contains(q)).toList();
		this.scroll = 0;
	}

	@Override
	protected void init() {
		this.panelW = Math.min(this.width - 12, 380);
		this.left = (this.width - this.panelW) / 2;
		int inner = this.left + 8;
		this.search = new EditBox(this.font, inner, Ui.PAD + 22, this.panelW - 16, 18, Component.literal(Lang.t("Szukaj dźwięku...")));
		this.search.setHint(Component.literal(Lang.t("Szukaj dźwięku...")));
		this.search.setValue(this.query);
		this.search.setResponder(text -> {
			this.query = text;
			this.filter();
		});
		this.addRenderableWidget(this.search);
		this.listTop = Ui.PAD + 46;
		this.addRenderableWidget(FlatButton.builder(CommonComponents.GUI_DONE, button -> this.onClose()).dimensions(inner, this.height - Ui.PAD - 26, this.panelW - 16, 20).build());
	}

	private int rows() {
		// Pod listą: tip (2 linie) i przycisk Gotowe.
		return Math.max(1, (this.height - Ui.PAD - 26 - 26 - this.listTop) / ROW);
	}

	@Override
	//? if <26.1.2 {
	public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
		super.renderBackground(context, mouseX, mouseY, deltaTicks);
	//?} else {
	/*public void extractBackground(GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
		super.extractBackground(context, mouseX, mouseY, deltaTicks);
	*///?}
		Ui.panel(context, this.left, Ui.PAD, this.panelW, this.height - 2 * Ui.PAD);
	}

	@Override
	//? if <26.1.2 {
	public void render(GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
	//?} else {
	/*public void extractRenderState(GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
	*///?}
		int inner = this.left + 8;
		context.drawString(this.font, this.title, inner, Ui.PAD + 8, Theme.TEXT, false);
		String count = this.shown.size() + " / " + this.all.size() + "  (" + Modules.SOUNDS.customized() + " " + Lang.t("zmienionych") + ")";
		context.drawString(this.font, count, inner + this.panelW - 16 - this.font.width(count), Ui.PAD + 8, 0xFFA0A4B0, false);
		this.scroll = Math.max(0, Math.min(this.scroll, Math.max(0, this.shown.size() - this.rows())));
		for (int i = 0; i < this.rows() && i + this.scroll < this.shown.size(); i++) {
			String id = this.shown.get(i + this.scroll);
			int ry = this.listTop + i * ROW;
			int percent = Modules.SOUNDS.percent(id);
			context.fill(inner, ry, inner + this.panelW - 16, ry + ROW - 2, percent == 100 ? Theme.SURFACE : 0x30FFFFFF);
			context.drawString(this.font, Ui.ellipsis(this.font, id, this.panelW - 16 - SLIDER_W - 50), inner + 4, ry + 5, percent == 100 ? Theme.TEXT : Theme.ACCENT, false);
			int sx = this.sliderX();
			context.fill(sx, ry + 8, sx + SLIDER_W, ry + 10, 0xFF3A3E4A);
			int knob = sx + Math.round(SLIDER_W * percent / (float) Sounds.MAX_PERCENT);
			context.fill(sx, ry + 8, knob, ry + 10, Theme.ACCENT);
			context.fill(knob - 2, ry + 5, knob + 2, ry + 13, 0xFFF2F4F8);
			String label = percent + "%";
			context.drawString(this.font, label, inner + this.panelW - 16 - 4 - this.font.width(label), ry + 5, Theme.TEXT, false);
		}

		// Tip pod listą.
		int tipY = this.height - Ui.PAD - 26 - 22;
		context.drawString(this.font, Lang.t("Nie wiesz, jak nazywa się dźwięk? Włącz napisy: Opcje, Ułatwienia dostępu,"), inner, tipY, 0xFFA0A4B0, false);
		context.drawString(this.font, Lang.t("Pokaż napisy. Pojawią się w prawym dolnym rogu i podpowiedzą nazwę."), inner, tipY + 10, 0xFFA0A4B0, false);
		//? if <26.1.2 {
		super.render(context, mouseX, mouseY, deltaTicks);
		//?} else {
		/*super.extractRenderState(context, mouseX, mouseY, deltaTicks);
		*///?}
	}

	private int sliderX() {
		return this.left + this.panelW - 8 - 4 - 34 - SLIDER_W;
	}

	private int rowAt(double mx, double my) {
		if (mx < this.left + 8 || mx >= this.left + this.panelW - 8 || my < this.listTop) {
			return -1;
		}

		int row = (int) ((my - this.listTop) / ROW);
		return row < this.rows() && row + this.scroll < this.shown.size() ? row + this.scroll : -1;
	}

	private void setFromMouse(String id, double mx) {
		double fraction = Math.max(0.0, Math.min(1.0, (mx - this.sliderX()) / SLIDER_W));
		Modules.SOUNDS.set(id, (int) Math.round(fraction * Sounds.MAX_PERCENT / 5.0) * 5);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		int row = this.rowAt(click.x(), click.y());
		if (row >= 0) {
			String id = this.shown.get(row);
			if (click.button() == 1) {
				Modules.SOUNDS.set(id, 100);
				return true;
			}

			if (click.x() >= this.sliderX() - 4 && click.x() <= this.sliderX() + SLIDER_W + 4) {
				this.dragging = id;
				this.setFromMouse(id, click.x());
				return true;
			}
		}

		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
		if (this.dragging != null) {
			this.setFromMouse(this.dragging, click.x());
			return true;
		}

		return super.mouseDragged(click, offsetX, offsetY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent click) {
		this.dragging = null;
		return super.mouseReleased(click);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		this.scroll -= (int) Math.signum(verticalAmount) * 3;
		return true;
	}

	@Override
	public void onClose() {
		//? if <26.2 {
		this.minecraft.setScreen(this.parent);
		//?} else {
		/*this.minecraft.gui.setScreen(this.parent);
		*///?}
	}
}
