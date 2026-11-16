package pl.kerf.project.gui;

import pl.kerf.project.Lang;

import pl.kerf.project.gui.theme.Theme;
import java.util.Locale;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import pl.kerf.project.gui.theme.FlatButton;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import pl.kerf.project.config.ConfigManager;
import pl.kerf.project.config.Setting;
import pl.kerf.project.module.Module;

/**
 * Wybór koloru: kwadrat nasycenie/jasność, po którym jeździsz kursorem, pasek odcienia, pasek krycia
 * (gdy ustawienie je ma), pole hex i gotowe kolory. Obok podgląd funkcji, którą kolor zmienia.
 */
public class ColorScreen extends Screen {
	private static final int[] PRESETS = {
		0xFF0000, 0xFF8000, 0xFFFF00, 0x00FF00, 0x00FFFF, 0x0050FF, 0x8000FF, 0xFF55FF, 0xFFFFFF, 0x000000
	};
	private static final String[] PRESET_NAMES = {
		"Czerwony", "Pomarańczowy", "Żółty", "Zielony", "Turkusowy", "Niebieski", "Fioletowy", "Różowy", "Biały", "Czarny"
	};
	private static final int BAR_W = 10;
	private static final int DRAG_NONE = 0;
	private static final int DRAG_SV = 1;
	private static final int DRAG_HUE = 2;
	private static final int DRAG_ALPHA = 3;

	private final Screen parent;
	private final Setting.Color setting;
	private final Module module;
	/** Stan HSV trzymany osobno, żeby odcień nie ginął przy szarościach i czerni. */
	private float hue;
	private float sat;
	private float val;
	private int alpha;
	private int dragging = DRAG_NONE;
	private TextFieldWidget hexField;
	private boolean syncing;

	// Układ.
	private int panelX;
	private int panelW;
	private int previewX;
	private int previewW;
	private int svX;
	private int svY;
	private int svSize;
	private int hueX;
	private int alphaX;
	private int presetsY;

	public ColorScreen(Screen parent, Setting.Color setting, Module module) {
		super(Text.literal(Lang.t(setting.label)));
		this.parent = parent;
		this.setting = setting;
		this.module = module;
		this.loadFromSetting();
	}

	public ColorScreen(Screen parent, Setting.Color setting) {
		this(parent, setting, null);
	}

	private void loadFromSetting() {
		int argb = this.setting.argb();
		float r = (argb >> 16 & 0xFF) / 255.0F;
		float g = (argb >> 8 & 0xFF) / 255.0F;
		float b = (argb & 0xFF) / 255.0F;
		float max = Math.max(r, Math.max(g, b));
		float min = Math.min(r, Math.min(g, b));
		float delta = max - min;
		this.val = max;
		this.sat = max == 0.0F ? 0.0F : delta / max;
		if (delta > 0.0F) {
			float h;
			if (max == r) {
				h = (g - b) / delta % 6.0F;
			} else if (max == g) {
				h = (b - r) / delta + 2.0F;
			} else {
				h = (r - g) / delta + 4.0F;
			}

			this.hue = (h / 6.0F + 1.0F) % 1.0F;
		}

		this.alpha = argb >>> 24;
	}

	private int rgb() {
		return MathHelper.hsvToRgb(this.hue, this.sat, this.val) & 0xFFFFFF;
	}

	private void store() {
		int a = this.setting.hasAlpha ? this.alpha : 0xFF;
		this.setting.set(a << 24 | this.rgb());
		this.syncing = true;
		this.hexField.setText(this.hex());
		this.syncing = false;
	}

	private String hex() {
		int value = this.setting.get();
		return this.setting.hasAlpha ? String.format(Locale.ROOT, "#%08X", value) : String.format(Locale.ROOT, "#%06X", value & 0xFFFFFF);
	}

	@Override
	protected void init() {
		boolean preview = this.module != null && this.width >= 360;
		this.previewW = preview ? Math.max(130, Math.min(200, this.width / 3)) : 0;
		this.previewX = this.width - Ui.PAD - this.previewW;
		int right = preview ? this.previewX - Ui.PAD : this.width - Ui.PAD;
		int bars = this.setting.hasAlpha ? 2 : 1;
		// Kwadrat tak duży, jak pozwala wysokość (pod nim jeszcze hex, kolory i przyciski).
		this.svSize = Math.max(60, Math.min(130, this.height - 2 * Ui.PAD - 22 - 26 - 24 - 28));
		int content = Math.max(this.svSize + bars * (BAR_W + 6), 10 * 20 - 2);
		this.panelW = Math.min(right - Ui.PAD, content + 20);
		this.panelX = preview ? Ui.PAD + Math.max(0, (right - Ui.PAD - this.panelW) / 2) : (this.width - this.panelW) / 2;
		int inner = this.panelX + (this.panelW - content) / 2;
		this.svX = inner;
		this.svY = Ui.PAD + 22;
		this.hueX = this.svX + this.svSize + 6;
		this.alphaX = this.hueX + BAR_W + 6;

		int hexY = this.svY + this.svSize + 6;
		this.hexField = new TextFieldWidget(this.textRenderer, inner + 26, hexY, content - 26, 18, Text.literal(Lang.t("Hex")));
		this.hexField.setMaxLength(9);
		this.hexField.setText(this.hex());
		this.hexField.setChangedListener(this::onHexTyped);
		this.hexField.setTooltip(Tooltip.of(Text.literal(Lang.t("Kod koloru, np. #3060FF") + (this.setting.hasAlpha ? Lang.t(" albo #803060FF z kryciem") : ""))));
		this.addDrawableChild(this.hexField);

		this.presetsY = hexY + 24;
		int presetX = inner + (content - (PRESETS.length * 20 - 2)) / 2;
		for (int i = 0; i < PRESETS.length; i++) {
			int rgb = PRESETS[i];
			this.addDrawableChild(FlatButton.builder(Text.empty(), button -> this.applyArgb(this.setting.get() & 0xFF000000 | rgb))
				.dimensions(presetX + i * 20, this.presetsY, 18, 18)
				.tooltip(Tooltip.of(Text.literal(Lang.t(PRESET_NAMES[i]))))
				.build());
		}

		int buttonsY = this.height - Ui.PAD - 26;
		int half = (this.panelW - 20) / 2;
		this.addDrawableChild(FlatButton.builder(Text.literal(Lang.t("Domyślny")), button -> {
			int current = this.setting.get();
			this.setting.reset();
			int def = this.setting.get();
			this.setting.set(current);
			this.applyArgb(def);
		}).dimensions(this.panelX + 8, buttonsY, half, 20).build());
		this.addDrawableChild(FlatButton.builder(ScreenTexts.DONE, button -> this.close()).dimensions(this.panelX + 12 + half, buttonsY, half, 20).style(FlatButton.Style.PRIMARY).build());
	}

	private void applyArgb(int argb) {
		this.setting.set(argb);
		this.loadFromSetting();
		this.syncing = true;
		this.hexField.setText(this.hex());
		this.syncing = false;
	}

	private void onHexTyped(String text) {
		if (this.syncing) {
			return;
		}

		String digits = text.trim().replace("#", "");
		if (!digits.matches("[0-9a-fA-F]{6}|[0-9a-fA-F]{8}")) {
			return;
		}

		int value = (int) Long.parseLong(digits, 16);
		if (digits.length() == 6) {
			// Sam RGB: krycie zostaje takie, jakie było.
			value = this.setting.get() & 0xFF000000 | value;
		} else if (!this.setting.hasAlpha) {
			value |= 0xFF000000;
		}

		this.setting.set(value);
		this.loadFromSetting();
	}

	// --- myszka ---

	private int dragTarget(double mx, double my) {
		if (Ui.inside(mx, my, this.svX, this.svY, this.svSize, this.svSize)) {
			return DRAG_SV;
		}

		if (Ui.inside(mx, my, this.hueX, this.svY, BAR_W, this.svSize)) {
			return DRAG_HUE;
		}

		if (this.setting.hasAlpha && Ui.inside(mx, my, this.alphaX, this.svY, BAR_W, this.svSize)) {
			return DRAG_ALPHA;
		}

		return DRAG_NONE;
	}

	private void dragTo(double mx, double my) {
		float fx = MathHelper.clamp((float) (mx - this.svX) / (this.svSize - 1), 0.0F, 1.0F);
		float fy = MathHelper.clamp((float) (my - this.svY) / (this.svSize - 1), 0.0F, 1.0F);
		switch (this.dragging) {
			case DRAG_SV -> {
				this.sat = fx;
				this.val = 1.0F - fy;
			}
			case DRAG_HUE -> this.hue = Math.min(fy, 0.999F);
			case DRAG_ALPHA -> this.alpha = Math.round((1.0F - fy) * 255.0F);
			default -> {
				return;
			}
		}

		this.store();
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		if (click.button() == 0) {
			int target = this.dragTarget(click.x(), click.y());
			if (target != DRAG_NONE) {
				this.dragging = target;
				this.setFocused(null);
				this.dragTo(click.x(), click.y());
				return true;
			}
		}

		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseDragged(Click click, double offsetX, double offsetY) {
		if (this.dragging != DRAG_NONE) {
			this.dragTo(click.x(), click.y());
			return true;
		}

		return super.mouseDragged(click, offsetX, offsetY);
	}

	@Override
	public boolean mouseReleased(Click click) {
		this.dragging = DRAG_NONE;
		return super.mouseReleased(click);
	}

	// --- rysowanie ---

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
		super.renderBackground(context, mouseX, mouseY, deltaTicks);
		int h = this.height - 2 * Ui.PAD;
		Ui.panel(context, this.panelX, Ui.PAD, this.panelW, h);
		if (this.previewW > 0) {
			Ui.panel(context, this.previewX, Ui.PAD, this.previewW, h);
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
		context.drawTextWithShadow(this.textRenderer, this.title, this.panelX + 8, Ui.PAD + 7, Theme.TEXT);
		this.drawSaturationValue(context);
		this.drawHue(context);
		if (this.setting.hasAlpha) {
			this.drawAlpha(context);
		}

		// Próbka koloru obok pola hex.
		int swatchY = this.svY + this.svSize + 6;
		context.fill(this.svX - 1, swatchY - 1, this.svX + 21, swatchY + 19, 0xFF000000);
		Ui.checker(context, this.svX, swatchY, 20, 18);
		context.fill(this.svX, swatchY, this.svX + 20, swatchY + 18, this.setting.argb());

		super.render(context, mouseX, mouseY, deltaTicks);
		int presetX = this.hexField.getX() - 26 + (this.hexField.getWidth() + 26 - (PRESETS.length * 20 - 2)) / 2;
		for (int i = 0; i < PRESETS.length; i++) {
			int x = presetX + i * 20;
			context.fill(x + 3, this.presetsY + 3, x + 15, this.presetsY + 15, 0xFF000000 | PRESETS[i]);
		}

		if (this.previewW > 0) {
			int x = this.previewX + 6;
			int w = this.previewW - 12;
			context.drawTextWithShadow(this.textRenderer, Lang.t("Podgląd na żywo"), x, Ui.PAD + 7, Theme.TEXT_3);
			int boxY = Ui.PAD + 20;
			int boxH = Math.min(170, this.height - Ui.PAD - 34 - boxY);
			Ui.inset(context, x, boxY, w, boxH);
			context.enableScissor(x + 1, boxY + 1, x + w - 1, boxY + boxH - 1);
			Previews.draw(context, this.client, this.module, x, boxY, w, boxH);
			context.disableScissor();
		}
	}

	private void drawSaturationValue(DrawContext context) {
		int size = this.svSize;
		context.fill(this.svX - 1, this.svY - 1, this.svX + size + 1, this.svY + size + 1, 0xFF000000);
		// Kolumna po kolumnie: u góry pełna jasność przy danym nasyceniu, w dół do czerni.
		for (int i = 0; i < size; i++) {
			float s = i / (float) (size - 1);
			int top = 0xFF000000 | MathHelper.hsvToRgb(this.hue, s, 1.0F);
			context.fillGradient(this.svX + i, this.svY, this.svX + i + 1, this.svY + size, top, 0xFF000000);
		}

		int mx = this.svX + Math.round(this.sat * (size - 1));
		int my = this.svY + Math.round((1.0F - this.val) * (size - 1));
		int ring = this.val > 0.5F ? 0xFF000000 : 0xFFFFFFFF;
		context.drawStrokedRectangle(mx - 3, my - 3, 7, 7, ring);
		context.drawStrokedRectangle(mx - 2, my - 2, 5, 5, ring == 0xFF000000 ? 0xFFFFFFFF : 0xFF000000);
	}

	private void drawHue(DrawContext context) {
		int size = this.svSize;
		context.fill(this.hueX - 1, this.svY - 1, this.hueX + BAR_W + 1, this.svY + size + 1, 0xFF000000);
		for (int j = 0; j < size; j++) {
			int color = 0xFF000000 | MathHelper.hsvToRgb(j / (float) size, 1.0F, 1.0F);
			context.fill(this.hueX, this.svY + j, this.hueX + BAR_W, this.svY + j + 1, color);
		}

		int y = this.svY + Math.round(this.hue * (size - 1));
		this.marker(context, this.hueX, y);
	}

	private void drawAlpha(DrawContext context) {
		int size = this.svSize;
		context.fill(this.alphaX - 1, this.svY - 1, this.alphaX + BAR_W + 1, this.svY + size + 1, 0xFF000000);
		Ui.checker(context, this.alphaX, this.svY, BAR_W, size);
		int rgb = this.rgb();
		context.fillGradient(this.alphaX, this.svY, this.alphaX + BAR_W, this.svY + size, 0xFF000000 | rgb, rgb);
		int y = this.svY + Math.round((1.0F - this.alpha / 255.0F) * (size - 1));
		this.marker(context, this.alphaX, y);
	}

	private void marker(DrawContext context, int x, int y) {
		context.fill(x - 2, y - 1, x + BAR_W + 2, y + 2, 0xFF000000);
		context.fill(x - 1, y, x + BAR_W + 1, y + 1, 0xFFFFFFFF);
	}

	@Override
	public void close() {
		ConfigManager.save();
		this.client.setScreen(this.parent);
	}
}
