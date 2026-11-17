package pl.kerf.project.gui;

import pl.kerf.project.Lang;

import pl.kerf.project.gui.theme.Theme;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import pl.kerf.project.gui.theme.FlatButton;
import com.mojang.blaze3d.platform.InputConstants;
import pl.kerf.project.config.ConfigManager;
import pl.kerf.project.config.PixelGrid;
import pl.kerf.project.module.Modules;
import pl.kerf.project.render.CustomCrosshairRenderer;

/**
 * Edytor celownika (część Crosshair Indicator): dwa rysunki pikselowe, "bez celu" i "na celu",
 * w dowolnych kolorach, z podglądem obu w prawdziwym rozmiarze.
 */
public class CrosshairEditorScreen extends Screen {
	private static final int N = PixelGrid.SIZE;
	private static final int TOOL_PENCIL = 0;
	private static final int TOOL_ERASER = 1;
	private static final int TOOL_FILL = 2;
	private static final int TOOL_PICKER = 3;
	private static final String[] TOOL_NAMES = {"Ołówek", "Gumka", "Wypełnij", "Pipeta"};
	private static final String[] STATE_NAMES = {"Bez celu", "Na celu"};
	private static final int[] PALETTE = {
		0xFFFFFFFF, 0xFF000000, 0xFFFF3030, 0xFF30FF50, 0xFF3080FF, 0xFFFFE030, 0xFF30FFFF, 0xFFFF40FF
	};
	/** Tła podglądu: niebo i trawa. */
	private static final int[] BACKGROUNDS = {0xFF7FA8FF, 0xFF5C8A3A};
	private static int tool = TOOL_PENCIL;
	private static boolean mirrorX = true;
	private static boolean mirrorY = true;
	/** 0 = bez celu, 1 = na celu. */
	private static int state;

	private final Screen parent;
	private final Modules.Crosshair module = Modules.CROSSHAIR;
	private final int[][] grids = new int[2][N * N];
	/** Historia: [stan, kopia siatki]. */
	private final Deque<int[][]> undo = new ArrayDeque<>();
	private boolean painting;
	private int paintColor;

	// Układ.
	private int toolsX;
	private int toolsW;
	private int centerLeft;
	private int centerRight;
	private int canvasX;
	private int canvasY;
	private int cell;
	private int previewX;
	private int previewW;

	public CrosshairEditorScreen(Screen parent) {
		super(Component.literal(Lang.t("Edytor celownika")));
		this.parent = parent;
		for (int i = 0; i < 2; i++) {
			System.arraycopy(this.module.grid(i == 1).get(), 0, this.grids[i], 0, N * N);
		}
	}

	private int[] grid() {
		return this.grids[state];
	}

	@Override
	protected void init() {
		int top = Ui.PAD;
		int h = this.height - 2 * Ui.PAD;
		this.toolsW = 92;
		this.toolsX = Ui.PAD;
		this.previewW = this.width >= 380 ? Math.max(110, Math.min(160, this.width / 4)) : 0;
		this.previewX = this.width - Ui.PAD - this.previewW;
		this.centerLeft = this.toolsX + this.toolsW + Ui.PAD;
		this.centerRight = this.previewW > 0 ? this.previewX - Ui.PAD : this.width - Ui.PAD;
		// Nad siatką zakładki (22 px), pod nią przyciski (30 px).
		int available = Math.min(this.centerRight - this.centerLeft - 16, h - 22 - 18 - 34);
		this.cell = Math.max(4, available / N);
		int canvas = this.cell * N;
		this.canvasX = this.centerLeft + (this.centerRight - this.centerLeft - canvas) / 2;
		this.canvasY = top + 40 + Math.max(0, (h - 40 - 34 - canvas) / 2);

		int bx = this.centerLeft + 8;
		int count = 4;
		int bw = (this.centerRight - this.centerLeft - 16 - (count - 1) * 4) / count;
		int by = this.height - Ui.PAD - 26;
		this.addRenderableWidget(FlatButton.builder(Component.literal(Lang.t("Cofnij")), button -> this.undo()).dimensions(bx, by, bw, 20).build());
		this.addRenderableWidget(FlatButton.builder(Component.literal(Lang.t("Wyczyść")), button -> this.apply(new int[N * N])).dimensions(bx + (bw + 4), by, bw, 20).build());
		this.addRenderableWidget(FlatButton.builder(Component.literal(Lang.t("Kopiuj")), button -> this.apply(this.grids[1 - state].clone()))
			.dimensions(bx + 2 * (bw + 4), by, bw, 20)
			.tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(Lang.t("Kopiuje rysunek z zakładki \"") + Lang.t(STATE_NAMES[1 - state]) + Lang.t("\" do tej"))))
			.build());
		this.addRenderableWidget(FlatButton.builder(CommonComponents.GUI_DONE, button -> this.onClose()).dimensions(bx + 3 * (bw + 4), by, bw, 20).style(FlatButton.Style.PRIMARY).build());

		if (this.previewW > 0) {
			String[] names = {"Vanilla", "Kropka", "Plus z obwódką", "Plus z przerwą", "Krzyżyk X", "Kółko"};
			int py = this.height - Ui.PAD - 24 - (names.length - 1) * 20;
			for (int i = 0; i < names.length; i++) {
				int preset = i;
				this.addRenderableWidget(FlatButton.builder(Component.literal(Lang.t(names[i])), button -> this.apply(preset(preset, this.module.brush.argb())))
					.dimensions(this.previewX + 6, py + i * 20, this.previewW - 12, 18)
					.build());
			}
		}
	}

	// --- wzory (w kolorze pędzla, z czarną obwódką tam, gdzie ma ją wzór) ---

	private static int[] preset(int index, int color) {
		int[] g = new int[N * N];
		int c = N / 2;
		int black = 0xFF000000;
		switch (index) {
			case 1 -> {
				outline(g, c, c, black);
				set(g, c, c, color);
			}
			case 2 -> {
				for (int i = c - 4; i <= c + 4; i++) {
					outline(g, c, i, black);
					outline(g, i, c, black);
				}

				for (int i = c - 4; i <= c + 4; i++) {
					set(g, c, i, color);
					set(g, i, c, color);
				}
			}
			case 3 -> {
				for (int i = 2; i <= 4; i++) {
					set(g, c, c - i - 1, color);
					set(g, c, c + i + 1, color);
					set(g, c - i - 1, c, color);
					set(g, c + i + 1, c, color);
				}

				set(g, c, c, color);
			}
			case 4 -> {
				for (int i = -4; i <= 4; i++) {
					set(g, c + i, c + i, color);
					set(g, c + i, c - i, color);
				}
			}
			case 5 -> {
				for (int i = 0; i < 64; i++) {
					double a = i * Math.PI * 2.0 / 64.0;
					set(g, c + (int) Math.round(Math.cos(a) * 4.0), c + (int) Math.round(Math.sin(a) * 4.0), color);
				}

				set(g, c, c, color);
			}
			default -> {
				for (int i = c - 4; i <= c + 4; i++) {
					set(g, c, i, color);
					set(g, i, c, color);
				}
			}
		}

		return g;
	}

	private static void outline(int[] g, int x, int y, int color) {
		for (int dy = -1; dy <= 1; dy++) {
			for (int dx = -1; dx <= 1; dx++) {
				set(g, x + dx, y + dy, color);
			}
		}
	}

	private static void set(int[] g, int x, int y, int color) {
		if (x >= 0 && y >= 0 && x < N && y < N) {
			g[y * N + x] = color;
		}
	}

	// --- edycja ---

	private void pushUndo() {
		this.undo.push(new int[][] {{state}, this.grid().clone()});
		while (this.undo.size() > 100) {
			this.undo.removeLast();
		}
	}

	private void undo() {
		if (!this.undo.isEmpty()) {
			int[][] entry = this.undo.pop();
			System.arraycopy(entry[1], 0, this.grids[entry[0][0]], 0, N * N);
			this.save();
		}
	}

	private void apply(int[] newGrid) {
		this.pushUndo();
		System.arraycopy(newGrid, 0, this.grid(), 0, N * N);
		this.save();
	}

	private void save() {
		this.module.normalGrid.set(this.grids[0]);
		this.module.targetGrid.set(this.grids[1]);
	}

	/** Maluje piksel i jego lustrzane odbicia. */
	private void paint(int x, int y, int color) {
		int[] g = this.grid();
		set(g, x, y, color);
		if (mirrorX) {
			set(g, N - 1 - x, y, color);
		}

		if (mirrorY) {
			set(g, x, N - 1 - y, color);
		}

		if (mirrorX && mirrorY) {
			set(g, N - 1 - x, N - 1 - y, color);
		}
	}

	private void floodFill(int x, int y, int color) {
		int[] g = this.grid();
		int target = g[y * N + x];
		if (target == color) {
			return;
		}

		Deque<int[]> stack = new ArrayDeque<>();
		stack.push(new int[] {x, y});
		while (!stack.isEmpty()) {
			int[] p = stack.pop();
			if (p[0] < 0 || p[1] < 0 || p[0] >= N || p[1] >= N || g[p[1] * N + p[0]] != target) {
				continue;
			}

			g[p[1] * N + p[0]] = color;
			stack.push(new int[] {p[0] + 1, p[1]});
			stack.push(new int[] {p[0] - 1, p[1]});
			stack.push(new int[] {p[0], p[1] + 1});
			stack.push(new int[] {p[0], p[1] - 1});
		}
	}

	private int cellX(double mx) {
		return (int) Math.floor((mx - this.canvasX) / this.cell);
	}

	private int cellY(double my) {
		return (int) Math.floor((my - this.canvasY) / this.cell);
	}

	private boolean onCanvas(double mx, double my) {
		return Ui.inside(mx, my, this.canvasX, this.canvasY, this.cell * N, this.cell * N);
	}

	// --- zakładki ---

	private int tabW() {
		return Math.min(110, (this.centerRight - this.centerLeft - 20) / 2);
	}

	private int tabX(int index) {
		int total = this.tabW() * 2 + 4;
		return (this.centerLeft + this.centerRight) / 2 - total / 2 + index * (this.tabW() + 4);
	}

	private int tabY() {
		return Ui.PAD + 20;
	}

	// --- myszka i klawiatura ---

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		double mx = click.x();
		double my = click.y();
		for (int i = 0; i < 2; i++) {
			if (Ui.inside(mx, my, this.tabX(i), this.tabY(), this.tabW(), 16)) {
				state = i;
				this.rebuildWidgets();
				return true;
			}
		}

		if (this.clickTools(mx, my)) {
			return true;
		}

		if (this.onCanvas(mx, my)) {
			int x = this.cellX(mx);
			int y = this.cellY(my);
			boolean erase = ModuleKeys.button(click.button()) == 1 || tool == TOOL_ERASER;
			if (tool == TOOL_PICKER && ModuleKeys.button(click.button()) == 0) {
				int picked = this.grid()[y * N + x];
				if (picked >>> 24 != 0) {
					this.module.brush.set(picked);
				}

				tool = TOOL_PENCIL;
				return true;
			}

			this.pushUndo();
			if (tool == TOOL_FILL && ModuleKeys.button(click.button()) == 0) {
				this.floodFill(x, y, this.module.brush.argb());
			} else {
				this.painting = true;
				this.paintColor = erase ? 0 : this.module.brush.argb();
				this.paint(x, y, this.paintColor);
			}

			this.save();
			return true;
		}

		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
		if (this.painting && this.onCanvas(click.x(), click.y())) {
			this.paint(this.cellX(click.x()), this.cellY(click.y()), this.paintColor);
			this.save();
			return true;
		}

		return super.mouseDragged(click, offsetX, offsetY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent click) {
		this.painting = false;
		return super.mouseReleased(click);
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		if (input.key() == InputConstants.KEY_Z && (input.modifiers() & InputConstants.MOD_CONTROL) != 0) {
			this.undo();
			return true;
		}

		return super.keyPressed(input);
	}

	// --- panel narzędzi ---

	private int toolRowY(int index) {
		return Ui.PAD + 22 + index * 16;
	}

	private int mirrorY0() {
		return this.toolRowY(TOOL_NAMES.length) + 14;
	}

	private int brushY() {
		return this.mirrorY0() + 2 * 16 + 14;
	}

	private int paletteY() {
		return this.brushY() + 22;
	}

	private boolean clickTools(double mx, double my) {
		int x = this.toolsX + 6;
		int w = this.toolsW - 12;
		for (int i = 0; i < TOOL_NAMES.length; i++) {
			if (Ui.inside(mx, my, x, this.toolRowY(i), w, 14)) {
				tool = i;
				return true;
			}
		}

		if (Ui.inside(mx, my, x, this.mirrorY0(), w, 14)) {
			mirrorX = !mirrorX;
			return true;
		}

		if (Ui.inside(mx, my, x, this.mirrorY0() + 16, w, 14)) {
			mirrorY = !mirrorY;
			return true;
		}

		if (Ui.inside(mx, my, x, this.brushY(), w, 18)) {
			this.minecraft.gui.setScreen(new ColorScreen(this, this.module.brush, this.module));
			return true;
		}

		for (int i = 0; i < PALETTE.length; i++) {
			int px = x + i % 4 * 20;
			int py = this.paletteY() + i / 4 * 20;
			if (Ui.inside(mx, my, px, py, 18, 18)) {
				this.module.brush.set(PALETTE[i]);
				if (tool != TOOL_FILL) {
					tool = TOOL_PENCIL;
				}

				return true;
			}
		}

		return false;
	}

	// --- rysowanie ---

	@Override
	public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
		super.extractBackground(context, mouseX, mouseY, deltaTicks);
		int h = this.height - 2 * Ui.PAD;
		Ui.panel(context, this.toolsX, Ui.PAD, this.toolsW, h);
		Ui.panel(context, this.centerLeft, Ui.PAD, this.centerRight - this.centerLeft, h);
		if (this.previewW > 0) {
			Ui.panel(context, this.previewX, Ui.PAD, this.previewW, h);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
		this.drawTools(context, mouseX, mouseY);
		this.drawTabs(context, mouseX, mouseY);
		this.drawCanvas(context, mouseX, mouseY);
		if (this.previewW > 0) {
			this.drawPreview(context);
		}

		super.extractRenderState(context, mouseX, mouseY, deltaTicks);
	}

	private void drawTabs(GuiGraphicsExtractor context, int mouseX, int mouseY) {
		context.text(this.font, this.title, this.centerLeft + 8, Ui.PAD + 7, Theme.TEXT);
		if (!this.module.custom.on()) {
			// Krótka informacja w nagłówku, gdy "Własny celownik" jest wyłączony (rysunek nie pojawi się w grze).
			String warning = Lang.t("wyłączony w grze");
			int wx = this.centerRight - 8 - this.font.width(warning);
			if (wx > this.centerLeft + 16 + this.font.width(this.title)) {
				context.text(this.font, warning, wx, Ui.PAD + 7, 0xFFE0A050);
			}
		}
		for (int i = 0; i < 2; i++) {
			int x = this.tabX(i);
			int y = this.tabY();
			boolean active = state == i;
			boolean hover = Ui.inside(mouseX, mouseY, x, y, this.tabW(), 16);
			context.fill(x, y, x + this.tabW(), y + 16, active ? 0xFF2F4A6E : hover ? 0xFF33333F : 0xFF22222A);
			context.outline(x, y, this.tabW(), 16, active ? Theme.ACCENT : Theme.BORDER);
			// Miniatura rysunku z tej zakładki obok nazwy.
			CustomCrosshairRenderer.draw(context, this.grids[i], x + 4, y + 1, 1.0F);
			context.text(this.font, Lang.t(STATE_NAMES[i]), x + 22, y + 4, active ? Theme.TEXT : Theme.TEXT_2, false);
		}
	}

	private void drawTools(GuiGraphicsExtractor context, int mouseX, int mouseY) {
		int x = this.toolsX + 6;
		int w = this.toolsW - 12;
		context.text(this.font, Lang.t("Narzędzia"), x, Ui.PAD + 8, Theme.TEXT);
		for (int i = 0; i < TOOL_NAMES.length; i++) {
			this.toolButton(context, mouseX, mouseY, x, this.toolRowY(i), w, TOOL_NAMES[i], tool == i);
		}

		context.text(this.font, Lang.t("Lustro"), x, this.mirrorY0() - 11, Theme.TEXT_3);
		this.toolButton(context, mouseX, mouseY, x, this.mirrorY0(), w, Lang.t("Poziomo  ") + (mirrorX ? "ON" : "OFF"), mirrorX);
		this.toolButton(context, mouseX, mouseY, x, this.mirrorY0() + 16, w, Lang.t("Pionowo  ") + (mirrorY ? "ON" : "OFF"), mirrorY);

		context.text(this.font, Lang.t("Kolor"), x, this.brushY() - 11, Theme.TEXT_3);
		int by = this.brushY();
		boolean hover = Ui.inside(mouseX, mouseY, x, by, w, 18);
		context.fill(x, by, x + w, by + 18, 0xFF000000);
		Ui.checker(context, x + 1, by + 1, w - 2, 16);
		context.fill(x + 1, by + 1, x + w - 1, by + 17, this.module.brush.argb());
		context.outline(x, by, w, 18, hover ? 0xFFFFFFFF : Theme.BORDER);
		for (int i = 0; i < PALETTE.length; i++) {
			int px = x + i % 4 * 20;
			int py = this.paletteY() + i / 4 * 20;
			boolean current = this.module.brush.argb() == PALETTE[i];
			context.fill(px, py, px + 18, py + 18, current ? 0xFFFFFFFF : 0xFF000000);
			context.fill(px + 2, py + 2, px + 16, py + 16, PALETTE[i]);
		}

		int helpY = this.paletteY() + 46;
		if (helpY + 30 < this.height - Ui.PAD) {
			context.text(this.font, Lang.t("LPM: maluj"), x, helpY, 0xFF7A7A86);
			context.text(this.font, Lang.t("PPM: gumka"), x, helpY + 10, 0xFF7A7A86);
			context.text(this.font, Lang.t("Ctrl+Z: cofnij"), x, helpY + 20, 0xFF7A7A86);
		}
	}

	private void toolButton(GuiGraphicsExtractor context, int mouseX, int mouseY, int x, int y, int w, String label, boolean active) {
		boolean hover = Ui.inside(mouseX, mouseY, x, y, w, 14);
		context.fill(x, y, x + w, y + 14, active ? 0xFF2F4A6E : hover ? 0xFF33333F : 0xFF22222A);
		context.outline(x, y, w, 14, active ? Theme.ACCENT : Theme.BORDER);
		context.text(this.font, Lang.t(label), x + 5, y + 3, active ? Theme.TEXT : Theme.TEXT_2, false);
	}

	private void drawCanvas(GuiGraphicsExtractor context, int mouseX, int mouseY) {
		int size = this.cell * N;
		context.fill(this.canvasX - 1, this.canvasY - 1, this.canvasX + size + 1, this.canvasY + size + 1, 0xFF000000);
		// Szachownica = przezroczystość.
		for (int y = 0; y < N; y++) {
			for (int x = 0; x < N; x++) {
				int color = (x + y) % 2 == 0 ? 0xFF2A2A30 : 0xFF32323A;
				int px = this.canvasX + x * this.cell;
				int py = this.canvasY + y * this.cell;
				context.fill(px, py, px + this.cell, py + this.cell, color);
			}
		}

		CustomCrosshairRenderer.draw(context, this.grid(), this.canvasX, this.canvasY, this.cell);
		// Środkowe linie pomocnicze.
		int mid = this.canvasX + N / 2 * this.cell;
		int midY = this.canvasY + N / 2 * this.cell;
		context.outline(mid, this.canvasY, this.cell, size, 0x30FFFFFF);
		context.outline(this.canvasX, midY, size, this.cell, 0x30FFFFFF);
		if (this.onCanvas(mouseX, mouseY)) {
			int hx = this.canvasX + this.cellX(mouseX) * this.cell;
			int hy = this.canvasY + this.cellY(mouseY) * this.cell;
			context.outline(hx, hy, this.cell, this.cell, 0xFFFFFFFF);
		}
	}

	private void drawPreview(GuiGraphicsExtractor context) {
		int x = this.previewX + 6;
		context.text(this.font, Lang.t("Podgląd na żywo"), x, Ui.PAD + 8, Theme.TEXT_3);
		float scale = this.module.scale.f();
		int box = Math.max(24, (int) Math.ceil(N * scale) + 8);
		int y = Ui.PAD + 22;
		for (int s = 0; s < 2; s++) {
			context.text(this.font, Lang.t(STATE_NAMES[s]), x, y, s == state ? Theme.TEXT : Theme.TEXT_2);
			y += 10;
			for (int i = 0; i < BACKGROUNDS.length; i++) {
				int bx = x + i * (box + 4);
				context.fill(bx, y, bx + box, y + box, BACKGROUNDS[i]);
				float size = N * scale;
				CustomCrosshairRenderer.draw(context, this.grids[s], (float) Math.floor(bx + (box - size) / 2.0F), (float) Math.floor(y + (box - size) / 2.0F), scale);
			}

			y += box + 4;
		}

	}

	@Override
	public void onClose() {
		this.save();
		ConfigManager.save();
		this.minecraft.gui.setScreen(this.parent);
	}
}
