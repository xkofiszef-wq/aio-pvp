package pl.kerf.project.gui;

import pl.kerf.project.Lang;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;
import pl.kerf.project.config.ConfigManager;
import pl.kerf.project.gui.theme.Draw;
import pl.kerf.project.gui.theme.Theme;
import pl.kerf.project.module.HudModule;
import pl.kerf.project.module.Modules;

/**
 * Edytor HUD-u. Na ekranie są elementy HUD-u z ramkami, wymiarami i uchwytami w rogach. Mały przycisk
 * "+ HUDs" otwiera okienko z kafelkami (podgląd na żywo): klik dodaje/usuwa element, przeciągnięcie stawia go na ekranie.
 */
public class HudEditorScreen extends Screen {
	private static final int SNAP = 4;
	private static final int HANDLE = 5;
	private static final int TILE_H = 42;
	private static final String HINT = "Przeciągnij  ·  rogi: rozmiar  ·  kółko: skala  ·  PPM: reset  ·  strzałki: 1 px  ·  G: siatka  ·  S: przyciąganie";
	private static boolean snapping = true;
	private static boolean grid;
	private static boolean pickerOpen;

	private final Screen parent;
	private final List<EditorElement> elements = new ArrayList<>();
	private EditorElement selected;
	private EditorElement dragging;
	private float grabX;
	private float grabY;
	/** Zmiana rozmiaru: przeciągany róg (0..3) i stały, przeciwległy punkt. */
	private int resizeCorner = -1;
	private float fixedX;
	private float fixedY;
	private float startScale;
	private float startCornerX;
	private float startCornerY;
	private Float guideX;
	private Float guideY;
	private final long openedAt = System.currentTimeMillis();

	// Okienko HUDs.
	private TextFieldWidget search;
	private double pickerScroll;
	private HudModule pressedTile;
	private HudModule placing;
	private double pressX;
	private double pressY;

	/** Otwiera lub zamyka okienko HUDs (np. żeby edytor otworzył się od razu z nim). */
	public static void setPickerOpen(boolean open) {
		pickerOpen = open;
	}

	public HudEditorScreen(Screen parent) {
		super(Text.literal(Lang.t("Edytor HUD")));
		this.parent = parent;
	}

	@Override
	protected void init() {
		this.elements.clear();
		for (HudModule module : Modules.HUD) {
			this.elements.add(new EditorElement.Hud(module));
		}

		this.elements.add(new EditorElement.Title());
		this.elements.add(new EditorElement.Actionbar());
		this.elements.add(new EditorElement.Scoreboard());
		this.elements.add(new EditorElement.Bossbar());

		String query = this.search == null ? "" : this.search.getText();
		this.search = new TextFieldWidget(this.textRenderer, 0, 0, 100, 12, Text.literal(Lang.t("Szukaj")));
		this.search.setDrawsBackground(false);
		this.search.setPlaceholder(Text.literal(Lang.t("Szukaj HUD-ów…")).withColor(Theme.TEXT_4 & 0xFFFFFF));
		this.search.setText(query);
		this.search.setChangedListener(text -> this.pickerScroll = 0.0);
		this.addDrawableChild(this.search);
		this.layoutPicker();
	}

	// --- okienko HUDs: geometria ---

	/** Przycisk HUDs: sama ikona na prawej krawędzi, w połowie wysokości ekranu. */
	private static final int HUDS_SIZE = 17;

	private int hudsX() {
		return this.width - HUDS_SIZE - 4;
	}

	private int hudsY() {
		return this.height / 2 - HUDS_SIZE / 2;
	}

	private int pickerW() {
		return Math.min(180, this.width - HUDS_SIZE - 20);
	}

	/** Panel otwiera się tuż obok przycisku, żeby go nie zasłaniać. */
	private int pickerX() {
		return this.hudsX() - this.pickerW() - 4;
	}

	private int pickerY() {
		return 24;
	}

	private int pickerH() {
		return Math.min(210, this.height - this.pickerY() - 8);
	}

	private int tilesTop() {
		return this.pickerY() + 50;
	}

	private int tilesBottom() {
		return this.pickerY() + this.pickerH() - 6;
	}

	private int columns() {
		return this.pickerW() >= 150 ? 2 : 1;
	}

	private int tileW() {
		return (this.pickerW() - 16 - (this.columns() - 1) * 6) / this.columns();
	}

	private void layoutPicker() {
		this.search.visible = pickerOpen;
		this.search.setX(this.pickerX() + 22);
		this.search.setY(this.pickerY() + 18);
		this.search.setWidth(this.pickerW() - 34);
		if (!pickerOpen && this.getFocused() == this.search) {
			this.setFocused(null);
		}
	}

	private boolean overPicker(double mx, double my) {
		return pickerOpen && Ui.inside(mx, my, this.pickerX(), this.pickerY(), this.pickerW(), this.pickerH());
	}

	private boolean overHudsButton(double mx, double my) {
		return Ui.inside(mx, my, this.hudsX(), this.hudsY(), HUDS_SIZE, HUDS_SIZE);
	}

	private List<HudModule> pickerModules() {
		String query = this.search.getText().trim().toLowerCase(Locale.ROOT);
		List<HudModule> list = new ArrayList<>();
		for (HudModule module : Modules.HUD) {
			if (query.isEmpty() || module.name.toLowerCase(Locale.ROOT).contains(query) || module.description.toLowerCase(Locale.ROOT).contains(query)) {
				list.add(module);
			}
		}

		return list;
	}

	private int tileX(int index) {
		return this.pickerX() + 8 + index % this.columns() * (this.tileW() + 6);
	}

	private int tileY(int index) {
		return this.tilesTop() + index / this.columns() * (TILE_H + 6) - (int) this.pickerScroll;
	}

	private HudModule tileAt(double mx, double my) {
		if (my < this.tilesTop() || my >= this.tilesBottom()) {
			return null;
		}

		List<HudModule> modules = this.pickerModules();
		for (int i = 0; i < modules.size(); i++) {
			if (Ui.inside(mx, my, this.tileX(i), this.tileY(i), this.tileW(), TILE_H)) {
				return modules.get(i);
			}
		}

		return null;
	}

	private void clampPickerScroll() {
		int rows = (this.pickerModules().size() + this.columns() - 1) / this.columns();
		int content = rows * (TILE_H + 6) - 6;
		int max = Math.max(0, content - (this.tilesBottom() - this.tilesTop()));
		this.pickerScroll = Math.max(0.0, Math.min(max, this.pickerScroll));
	}

	// --- elementy: geometria ---

	private EditorElement at(double x, double y) {
		if (this.overPicker(x, y)) {
			return null;
		}

		// Od końca listy: element narysowany później leży wyżej, więc ma pierwszeństwo.
		for (int i = this.elements.size() - 1; i >= 0; i--) {
			EditorElement element = this.elements.get(i);
			if (element.visible() && element.movable()
				&& Ui.inside(x, y, (int) element.x(), (int) element.y(), (int) Math.ceil(element.w()), (int) Math.ceil(element.h()))) {
				return element;
			}
		}

		return null;
	}

	private static float cornerX(EditorElement element, int corner) {
		return corner % 2 == 0 ? element.x() : element.x() + element.w();
	}

	private static float cornerY(EditorElement element, int corner) {
		return corner < 2 ? element.y() : element.y() + element.h();
	}

	private int cornerAt(EditorElement element, double mx, double my) {
		if (element == null || !element.visible() || !element.movable()) {
			return -1;
		}

		for (int corner = 0; corner < 4; corner++) {
			if (Math.abs(mx - cornerX(element, corner)) <= HANDLE && Math.abs(my - cornerY(element, corner)) <= HANDLE) {
				return corner;
			}
		}

		return -1;
	}

	private EditorElement elementOf(HudModule module) {
		for (EditorElement element : this.elements) {
			if (element instanceof EditorElement.Hud hud && hud.module == module) {
				return element;
			}
		}

		return null;
	}

	// --- przyciąganie ---

	/** Linie, do których przyciągamy w osi X: krawędzie i środek ekranu, hotbar oraz krawędzie innych elementów. */
	private List<Float> targetsX(EditorElement moving) {
		List<Float> targets = new ArrayList<>(List.of(0.0F, this.width / 2.0F, (float) this.width, this.width / 2.0F - 91.0F, this.width / 2.0F + 91.0F));
		for (EditorElement element : this.elements) {
			if (element != moving && element.visible()) {
				targets.add(element.x());
				targets.add(element.x() + element.w());
				targets.add(element.x() + element.w() / 2.0F);
			}
		}

		return targets;
	}

	private List<Float> targetsY(EditorElement moving) {
		List<Float> targets = new ArrayList<>(List.of(0.0F, this.height / 2.0F, (float) this.height, this.height - 22.0F));
		for (EditorElement element : this.elements) {
			if (element != moving && element.visible()) {
				targets.add(element.y());
				targets.add(element.y() + element.h());
				targets.add(element.y() + element.h() / 2.0F);
			}
		}

		return targets;
	}

	/** Przesunięcie, które dosuwa najbliższą krawędź lub środek elementu do linii, albo null. */
	private static Float snapOffset(float start, float size, List<Float> targets) {
		Float best = null;
		for (float line : new float[] {start, start + size / 2.0F, start + size}) {
			for (float target : targets) {
				float diff = target - line;
				if (Math.abs(diff) <= SNAP && (best == null || Math.abs(diff) < Math.abs(best))) {
					best = diff;
				}
			}
		}

		return best;
	}

	private static Float guideLine(float start, float size, List<Float> targets) {
		for (float line : new float[] {start, start + size / 2.0F, start + size}) {
			for (float target : targets) {
				if (Math.abs(target - line) < 0.01F) {
					return target;
				}
			}
		}

		return null;
	}

	private void moveElement(EditorElement element, float x, float y) {
		this.guideX = null;
		this.guideY = null;
		if (snapping) {
			List<Float> tx = this.targetsX(element);
			List<Float> ty = this.targetsY(element);
			Float dx = snapOffset(x, element.w(), tx);
			Float dy = snapOffset(y, element.h(), ty);
			if (dx != null) {
				x += dx;
				this.guideX = guideLine(x, element.w(), tx);
			}

			if (dy != null) {
				y += dy;
				this.guideY = guideLine(y, element.h(), ty);
			}
		}

		if (grid && this.guideX == null) {
			x = Math.round(x / 10.0F) * 10.0F;
		}

		if (grid && this.guideY == null) {
			y = Math.round(y / 10.0F) * 10.0F;
		}

		element.setTopLeft(Math.max(0.0F, Math.min(this.width - element.w(), x)), Math.max(0.0F, Math.min(this.height - element.h(), y)));
	}

	private void resizeDragged(double mx, double my) {
		EditorElement element = this.dragging;
		// Rzut kursora na przekątną elementu: płynna skala niezależnie od kierunku ruchu.
		float vx = this.startCornerX - this.fixedX;
		float vy = this.startCornerY - this.fixedY;
		float length = vx * vx + vy * vy;
		if (length < 1.0F) {
			return;
		}

		float ratio = (float) (((mx - this.fixedX) * vx + (my - this.fixedY) * vy) / length);
		float scale = Math.max(element.minScale(), Math.min(element.maxScale(), this.startScale * ratio));
		element.setScaleAround(Math.round(scale * 20.0F) / 20.0F, this.fixedX, this.fixedY);
	}

	private void changeScale(EditorElement element, float delta) {
		float scale = Math.max(element.minScale(), Math.min(element.maxScale(), element.scale() + delta));
		// Skalowanie względem środka, żeby element nie "uciekał" spod kursora.
		element.setScaleAround(scale, element.x() + element.w() / 2.0F, element.y() + element.h() / 2.0F);
	}

	/** Stawia element z kafelka pod kursorem (włącza go, jeśli był wyłączony). */
	private void placeFromTile(HudModule module, double mx, double my) {
		if (!module.on()) {
			module.setEnabled(true);
		}

		// Element jeszcze nie był narysowany, więc rozmiar liczymy z jego zawartości.
		module.boundsW = module.width(this.client, true) * module.scale;
		module.boundsH = module.height(this.client, true) * module.scale;
		EditorElement element = this.elementOf(module);
		if (element != null && element.movable()) {
			this.moveElement(element, (float) mx - element.w() / 2.0F, (float) my - element.h() / 2.0F);
			this.selected = element;
		}
	}

	// --- wejście ---

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		double mx = click.x();
		double my = click.y();
		if (this.overHudsButton(mx, my) && click.button() == 0) {
			pickerOpen = !pickerOpen;
			this.layoutPicker();
			return true;
		}

		if (this.overPicker(mx, my)) {
			if (Ui.inside(mx, my, this.pickerX() + this.pickerW() - 16, this.pickerY() + 3, 12, 12)) {
				pickerOpen = false;
				this.layoutPicker();
				return true;
			}

			if (super.mouseClicked(click, doubled)) {
				return true;
			}

			HudModule tile = this.tileAt(mx, my);
			if (tile != null && click.button() == 0) {
				this.pressedTile = tile;
				this.pressX = mx;
				this.pressY = my;
			}

			return true;
		}

		int corner = this.cornerAt(this.selected, mx, my);
		if (corner >= 0 && click.button() == 0) {
			EditorElement element = this.selected;
			this.dragging = element;
			this.resizeCorner = corner;
			this.fixedX = cornerX(element, 3 - corner);
			this.fixedY = cornerY(element, 3 - corner);
			this.startCornerX = cornerX(element, corner);
			this.startCornerY = cornerY(element, corner);
			this.startScale = element.scale();
			return true;
		}

		EditorElement element = this.at(mx, my);
		this.selected = element;
		if (element == null) {
			return super.mouseClicked(click, doubled);
		}

		if (click.button() == 1) {
			element.reset();
		} else if (click.button() == 0) {
			this.dragging = element;
			this.resizeCorner = -1;
			this.grabX = (float) mx - element.x();
			this.grabY = (float) my - element.y();
		}

		return true;
	}

	@Override
	public boolean mouseDragged(Click click, double offsetX, double offsetY) {
		if (this.pressedTile != null) {
			if (this.placing == null && Math.hypot(click.x() - this.pressX, click.y() - this.pressY) > 4.0) {
				this.placing = this.pressedTile;
			}

			if (this.placing != null) {
				this.placeFromTile(this.placing, click.x(), click.y());
			}

			return true;
		}

		if (this.dragging != null) {
			if (this.resizeCorner >= 0) {
				this.resizeDragged(click.x(), click.y());
			} else {
				this.moveElement(this.dragging, (float) click.x() - this.grabX, (float) click.y() - this.grabY);
			}

			return true;
		}

		return super.mouseDragged(click, offsetX, offsetY);
	}

	@Override
	public boolean mouseReleased(Click click) {
		if (this.pressedTile != null && this.placing == null) {
			// Zwykły klik w kafelek: dodaj albo usuń element.
			this.pressedTile.toggle();
		}

		this.pressedTile = null;
		this.placing = null;
		this.dragging = null;
		this.resizeCorner = -1;
		this.guideX = null;
		this.guideY = null;
		return super.mouseReleased(click);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (this.overPicker(mouseX, mouseY)) {
			this.pickerScroll -= verticalAmount * (TILE_H + 6) / 2.0;
			this.clampPickerScroll();
			return true;
		}

		EditorElement element = this.at(mouseX, mouseY);
		if (element != null && verticalAmount != 0.0) {
			this.selected = element;
			this.changeScale(element, verticalAmount > 0.0 ? 0.05F : -0.05F);
			return true;
		}

		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (this.getFocused() == this.search && this.search.visible) {
			if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
				this.setFocused(null);
				return true;
			}

			return super.keyPressed(input);
		}

		if (input.key() == GLFW.GLFW_KEY_G) {
			grid = !grid;
			return true;
		}

		if (input.key() == GLFW.GLFW_KEY_S) {
			snapping = !snapping;
			return true;
		}

		EditorElement element = this.selected;
		if (element != null && element.visible() && element.movable()) {
			int step = (input.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0 ? 10 : 1;
			int dx = 0;
			int dy = 0;
			switch (input.key()) {
				case GLFW.GLFW_KEY_LEFT -> dx = -step;
				case GLFW.GLFW_KEY_RIGHT -> dx = step;
				case GLFW.GLFW_KEY_UP -> dy = -step;
				case GLFW.GLFW_KEY_DOWN -> dy = step;
				case GLFW.GLFW_KEY_R -> {
					element.reset();
					return true;
				}
				default -> {
				}
			}

			if (dx != 0 || dy != 0) {
				element.setTopLeft(
					Math.max(0.0F, Math.min(this.width - element.w(), element.x() + dx)),
					Math.max(0.0F, Math.min(this.height - element.h(), element.y() + dy))
				);
				return true;
			}
		}

		return super.keyPressed(input);
	}

	// --- rysowanie ---

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
		// Bez rozmycia i przyciemnienia: HUD pod spodem ma być widoczny tak jak w grze.
		this.client.inGameHud.renderDeferredSubtitles();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
		if (grid) {
			for (int x = 0; x < this.width; x += 10) {
				context.fill(x, 0, x + 1, this.height, 0x14FFFFFF);
			}

			for (int y = 0; y < this.height; y += 10) {
				context.fill(0, y, this.width, y + 1, 0x14FFFFFF);
			}
		}

		for (EditorElement element : this.elements) {
			element.drawSample(context, this.textRenderer);
		}

		EditorElement hovered = this.dragging != null ? this.dragging : this.at(mouseX, mouseY);
		for (EditorElement element : this.elements) {
			if (element.visible()) {
				this.drawFrame(context, element, element == this.selected, element == hovered);
			}
		}

		if (this.guideX != null) {
			int gx = Math.round(this.guideX);
			context.fill(gx, 0, gx + 1, this.height, Theme.ACCENT);
		}

		if (this.guideY != null) {
			int gy = Math.round(this.guideY);
			context.fill(0, gy, this.width, gy + 1, Theme.ACCENT);
		}

		boolean hudsHover = this.overHudsButton(mouseX, mouseY);
		this.iconButton(context, this.hudsX(), this.hudsY(), HUDS_SIZE, Draw.Icon.HUD, hudsHover, pickerOpen);
		// Podpowiedź tylko przez kilka sekund po otwarciu, potem znika, żeby nie zasłaniała elementów.
		long shown = System.currentTimeMillis() - this.openedAt;
		if (!pickerOpen && this.dragging == null && shown < 6000L) {
			float fade = shown < 5000L ? 1.0F : (6000L - shown) / 1000.0F;
			String hint = Ui.ellipsis(this.textRenderer, HINT, this.width - 28);
			int w = this.textRenderer.getWidth(hint) + 14;
			int hx = this.width / 2 - w / 2;
			Draw.round(context, hx, 22, w, 14, 4, Theme.alpha(Theme.GLASS_STRONG, Math.round(0xE6 * fade)));
			Draw.outline(context, hx, 22, w, 14, 4, Theme.alpha(Theme.BORDER, Math.round(0x24 * fade)));
			Draw.text(context, this.textRenderer, hint, hx + 7, 25, Theme.alpha(Theme.TEXT_2, Math.round(255 * fade)));
		}

		if (pickerOpen) {
			this.drawPicker(context, mouseX, mouseY);
		}

		super.render(context, mouseX, mouseY, deltaTicks);
	}

	/** Mały kwadratowy szklany przycisk z samą ikoną 9x9 (przycisk HUDs). Otwarty panel = podświetlenie akcentem. */
	private void iconButton(DrawContext context, int x, int y, int size, Draw.Icon icon, boolean hover, boolean open) {
		boolean active = hover || open;
		Draw.card(context, x, y, size, size, 4, active ? Theme.mix(Theme.GLASS_STRONG, Theme.alpha(Theme.ACCENT, 0xE6), open ? 0.35F : 0.15F) : Theme.GLASS_STRONG,
			active ? Theme.alpha(Theme.ACCENT, 0xB0) : Theme.BORDER_STRONG);
		Draw.icon(context, icon, x + (size - 9) / 2, y + (size - 9) / 2, open ? Theme.TEXT : hover ? Theme.TEXT_2 : Theme.TEXT_3);
	}

	private void drawPicker(DrawContext context, int mouseX, int mouseY) {
		int x = this.pickerX();
		int y = this.pickerY();
		int w = this.pickerW();
		int h = this.pickerH();
		Ui.panel(context, x, y, w, h);
		Draw.text(context, this.textRenderer, "HUDs", x + 9, y + 5, Theme.TEXT);
		boolean closeHover = Ui.inside(mouseX, mouseY, x + w - 16, y + 3, 12, 12);
		if (closeHover) {
			Draw.round(context, x + w - 17, y + 2, 14, 14, 3, Theme.SURFACE_HOVER);
		}

		Draw.icon(context, Draw.Icon.CLOSE, x + w - 15, y + 4, closeHover ? Theme.TEXT : Theme.TEXT_3);

		// Szklane pole wyszukiwania (sam tekst rysuje widget, bez vanillowego tła).
		boolean focused = this.getFocused() == this.search;
		Draw.round(context, x + 8, y + 16, w - 16, 16, 4, Theme.SURFACE_2);
		Draw.outline(context, x + 8, y + 16, w - 16, 16, 4, focused ? Theme.alpha(Theme.ACCENT, 0xC0) : Theme.BORDER);
		Draw.icon(context, Draw.Icon.SEARCH, x + 12, y + 20, focused ? Theme.TEXT_2 : Theme.TEXT_4);
		Draw.text(context, this.textRenderer, Ui.ellipsis(this.textRenderer, "Klik: dodaj, przeciągnij: ustaw", w - 16), x + 9, y + 37, Theme.TEXT_4);

		List<HudModule> modules = this.pickerModules();
		this.clampPickerScroll();
		context.enableScissor(x + 1, this.tilesTop(), x + w - 1, this.tilesBottom());
		for (int i = 0; i < modules.size(); i++) {
			HudModule module = modules.get(i);
			int tx = this.tileX(i);
			int ty = this.tileY(i);
			if (ty + TILE_H < this.tilesTop() || ty > this.tilesBottom()) {
				continue;
			}

			boolean hover = Ui.inside(mouseX, mouseY, tx, ty, this.tileW(), TILE_H) && mouseY >= this.tilesTop() && mouseY < this.tilesBottom();
			int accent = Theme.category(module.category);
			int border = module.on() ? Theme.alpha(accent, 0xB0) : hover ? Theme.BORDER_STRONG : Theme.BORDER;
			Draw.card(context, tx, ty, this.tileW(), TILE_H, 4, hover ? Theme.SURFACE_2 : Theme.SURFACE, border);
			int boxW = this.tileW() - 6;
			Draw.round(context, tx + 3, ty + 3, boxW, 26, 3, Theme.INSET);
			context.enableScissor(tx + 4, Math.max(ty + 4, this.tilesTop()), tx + 2 + boxW, Math.min(ty + 28, this.tilesBottom()));
			if (module.hasPreview() && this.client.player != null) {
				module.renderPreview(context, this.client, tx + 3, ty + 3, boxW, 26, 1.0F);
			} else {
				Previews.draw(context, this.client, module, tx + 3, ty + 3, boxW, 26);
			}

			context.disableScissor();
			String name = Ui.ellipsis(this.textRenderer, module.name, this.tileW() - 24);
			Draw.text(context, this.textRenderer, name, tx + (this.tileW() - this.textRenderer.getWidth(name)) / 2, ty + 31, module.on() ? Theme.TEXT : Theme.TEXT_3);
			if (module.on()) {
				Draw.round(context, tx + this.tileW() - 9, ty + 33, 4, 4, 2, accent);
			}
		}

		context.disableScissor();
	}

	private void drawFrame(DrawContext context, EditorElement element, boolean isSelected, boolean hovered) {
		int x1 = Math.round(element.x()) - 1;
		int y1 = Math.round(element.y()) - 1;
		int x2 = Math.round(element.x() + element.w()) + 1;
		int y2 = Math.round(element.y() + element.h()) + 1;
		// Bez wypełnienia na zaznaczonym: zawartość elementu ma być widać w całości, ramka tylko go obrysowuje.
		if (hovered && !isSelected) {
			context.fill(x1, y1, x2, y2, 0x12FFFFFF);
		}

		int line = isSelected ? Theme.alpha(Theme.ACCENT, 0x90) : hovered ? 0x90FFFFFF : 0x30FFFFFF;
		context.drawStrokedRectangle(x1, y1, x2 - x1, y2 - y1, line);
		if (!element.movable()) {
			return;
		}

		boolean active = element == this.dragging;
		if (isSelected) {
			// Narożniki "L" na zewnątrz ramki zamiast kwadratów: widać, gdzie łapać, a nic nie zasłaniają.
			// Pole łapania (HANDLE) się nie zmienia, więc rozciąganie działa tak samo.
			int arm = 5;
			int corners = active && this.resizeCorner >= 0 ? Theme.TEXT : Theme.ACCENT;
			for (int corner = 0; corner < 4; corner++) {
				int cx = corner % 2 == 0 ? x1 - 1 : x2;
				int cy = corner < 2 ? y1 - 1 : y2;
				int dx = corner % 2 == 0 ? 1 : -1;
				int dy = corner < 2 ? 1 : -1;
				context.fill(Math.min(cx, cx + dx * arm), cy, Math.max(cx, cx + dx * arm) + 1, cy + 1, corners);
				context.fill(cx, Math.min(cy, cy + dy * arm), cx + 1, Math.max(cy, cy + dy * arm) + 1, corners);
			}
		}

		// Podpis tylko gdy coś się dzieje: nazwa przy najechaniu, rozmiar podczas przeciągania. Zaznaczony i nieruszany = sama ramka.
		String label = active ? Math.round(element.w()) + " x " + Math.round(element.h()) : hovered && !isSelected ? element.name : null;
		if (label != null) {
			int labelW = this.textRenderer.getWidth(label) + 6;
			int lx = Math.max(0, Math.min(this.width - labelW, x1 - 1));
			int ly = y1 - 13 < 0 ? y2 + 3 : y1 - 13;
			Draw.round(context, lx, ly, labelW, 11, 2, Theme.alpha(Theme.GLASS_STRONG, 0xC0));
			Draw.text(context, this.textRenderer, label, lx + 3, ly + 2, active ? Theme.TEXT : Theme.TEXT_2);
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void close() {
		ConfigManager.save();
		this.client.setScreen(this.parent);
	}
}
