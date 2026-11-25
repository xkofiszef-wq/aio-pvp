package pl.kerf.project.gui;

import pl.kerf.project.Lang;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import pl.kerf.project.config.ConfigManager;
import pl.kerf.project.config.Setting;
import pl.kerf.project.gui.theme.Anim;
import pl.kerf.project.gui.theme.Draw;
import pl.kerf.project.gui.theme.FlatButton;
import pl.kerf.project.gui.theme.Theme;
import pl.kerf.project.gui.theme.Tip;
import pl.kerf.project.module.FightTracker;
import pl.kerf.project.module.Module;
import pl.kerf.project.module.Modules;

/**
 * Główne menu moda: trzy unoszące się szklane panele. Po lewej kategorie, w środku kafelki funkcji z wyszukiwarką,
 * po prawej podgląd funkcji pod kursorem. Ustawienia funkcji wysuwają się nad środkowym panelem.
 */
public class SettingsScreen extends Screen {
	private static final int GAP = 6;
	private static final int ROW = 20;
	private static final int CARD_H = 46;
	private static final int TILE_H = 84;
	private static final int CARD_GAP = 6;
	private static final int SEARCH_H = 20;

	/** Zapamiętane między otwarciami: kategoria (null = wszystkie), otwarty panel i wyszukiwanie. */
	private static Module.Category category;
	private static Module openModule;
	private static String query = "";

	private final Screen parent;
	private final List<Module> visible = new ArrayList<>();
	private final Map<Module, Anim> cardHover = new HashMap<>();
	private final Map<Module, Anim> cardSwitch = new HashMap<>();
	private final Anim indicator = new Anim(-1.0F, 16.0F);
	private final Anim panel = new Anim(0.0F, 13.0F);
	private final Anim cardScroll = new Anim(0.0F, 18.0F);
	private final Anim panelScroll = new Anim(0.0F, 18.0F);
	private final Anim dropdownAnim = new Anim(0.0F, 20.0F);
	private double cardScrollTarget;
	private double panelScrollTarget;
	/** Przeciągany pasek przewijania (0 = żaden) i odstęp kursora od góry uchwytu. */
	private int draggingBar;
	private double barGrab;
	private static final int BAR_CARDS = 1;
	private static final int BAR_PANEL = 2;
	/** Funkcja pokazywana w prawym panelu, gdy kursor nie stoi nad żadnym kafelkiem. */
	private Module hoveredCard;

	// Panel ustawień.
	private Module panelModule;
	private final List<Control> controls = new ArrayList<>();
	private int panelContentH;
	private DropdownControl dropdown;
	private KeybindControl capturing;
	/** Wiersz Command Keys czekający na klawisz bindu. */
	private BindControl capturingBind;
	private SliderControl draggingSlider;
	private FlatButton defaultsButton;

	// Widgety.
	private EditBox search;
	private FlatButton hudEditor;
	private FlatButton menuKey;
	/** Czeka na klawisz otwierający to menu (przycisk nad "Resetuj"). */
	private boolean capturingMenuKey;

	/** Cały obszar trzech paneli (wy/wh służą też jako granice list rozwijanych). */
	private int wx;
	private int wy;
	private int ww;
	private int wh;
	private int sideW;
	private int contentX;
	private int contentW;
	private int rightX;
	private int rightW;
	/** Układ z GUI Theme, z którym zbudowano ekran (zmiana w ustawieniach przebudowuje go od razu). */
	private int layout;
	/** Język, w którym zbudowano przyciski (zmiana języka przebudowuje ekran). */
	private boolean english;
	/** Pływający układ: wyszukiwarka to osobna pigułka nad panelami, moduły i podgląd są w jednym panelu. */
	private boolean floating;
	private int sideX;
	/** Pasek kategorii: w pływającym układzie na całą wysokość (razem z rzędem wyszukiwarki), żeby wszystko się mieściło. */
	private int sideY;
	private int sideH;
	private int searchY;
	private int panelR;

	public SettingsScreen(Screen parent) {
		super(Component.literal(Lang.t("AIO PvP")));
		this.parent = parent;
	}

	/** Otwiera menu od razu na ustawieniach danej funkcji. */
	public static void select(Module module) {
		category = module.category;
		openModule = module;
		query = "";
	}

	private static String categoryHint(Module.Category entry) {
		if (entry == null) {
			return "Przegląd wszystkich funkcji moda";
		}

		return switch (entry) {
			case HUD -> "Elementy na ekranie i ich wygląd";
			case COMBAT -> "Walka, celowanie i trafienia";
			case VISUALS -> "Wygląd świata i efektów";
			case ITEMS -> "Przedmioty w rękach i ich użycie";
			case MISC -> "Pozostałe ustawienia";
		};
	}

	// --- układ ---

	@Override
	protected void init() {
		// Trzy panele jak w Shine na ok. 90% ekranu (bez zmiany skali); prawy znika dopiero na bardzo wąskim ekranie.
		this.ww = Math.min(this.width - 16, Math.max(360, Math.min(760, Math.round(this.width * 0.9F))));
		this.wh = Math.min(this.height - 16, Math.max(210, Math.min(420, Math.round(this.height * 0.88F))));
		this.wx = (this.width - this.ww) / 2;
		this.wy = (this.height - this.wh) / 2;
		this.sideW = this.ww >= 560 ? 140 : 110;
		this.rightW = this.ww >= 400 ? Math.max(100, Math.min(190, this.ww * 24 / 100)) : 0;
		this.layout = layoutSetting();
		this.english = Lang.english();
		this.floating = this.layout > 0;
		this.panelR = this.floating ? 12 : Ui.PANEL_R;
		this.sideY = this.wy;
		this.sideH = this.wh;
		if (this.floating) {
			// Pigułka wyszukiwarki zajmuje górny pasek; panele zaczynają się pod nią.
			this.searchY = this.wy;
			this.wy += SEARCH_H + GAP;
			this.wh -= SEARCH_H + GAP;
		}

		boolean sideRight = this.layout == 2;
		this.sideX = sideRight ? this.wx + this.ww - this.sideW : this.wx;
		this.contentX = sideRight ? this.wx : this.wx + this.sideW + GAP;
		this.contentW = this.ww - this.sideW - GAP - (this.rightW > 0 ? this.rightW + GAP : 0);
		this.rightX = this.contentX + this.contentW + GAP;

		this.search = this.floating
			? new EditBox(this.font, this.contentX + 14, this.searchY + 6, this.mainW() - 28, 12, Component.literal(Lang.t("Szukaj")))
			: new EditBox(this.font, this.contentX + 15, this.wy + 46, this.contentW - 30, 12, Component.literal(Lang.t("Szukaj")));
		this.search.setBordered(false);
		this.search.setHint(Component.literal(Lang.t("Szukaj funkcji…")).withColor(Theme.TEXT_4 & 0xFFFFFF));
		this.search.setValue(query);
		this.search.setResponder(text -> {
			query = text;
			this.cardScrollTarget = 0.0;
			this.refresh();
		});
		this.addRenderableWidget(this.search);

		int bw = this.sideW - 12;
		this.hudEditor = this.addRenderableWidget(FlatButton.builder(Component.literal(Lang.t("Edytor HUD")), button -> this.minecraft.setScreen(new HudEditorScreen(this)))
			.dimensions(this.sideX + 6, this.sideY + 38, bw, 20)
			.style(FlatButton.Style.SECONDARY)
			.build()
			.icon(Draw.Icon.LAYOUT)
			.tip(this.minecraft.player != null ? "Przesuwaj i skaluj elementy HUD-u" : "Dostępny w świecie"));
		this.hudEditor.active = this.minecraft.player != null;
		this.menuKey = this.addRenderableWidget(FlatButton.builder(this.menuKeyLabel(), button -> {
				this.capturingMenuKey = true;
				this.menuKey.setMessage(this.menuKeyLabel());
			})
			.dimensions(this.sideX + 6, this.sideY + this.sideH - 50, bw, 20)
			.style(FlatButton.Style.SECONDARY)
			.build()
			.tip("Klawisz otwierający to menu (ten sam co w Opcje -> Sterowanie). Kliknij i naciśnij klawisz albo boczny/środkowy przycisk myszy, Backspace usuwa."));
		// Ustawienia samego moda (motyw, układ, język, reset wszystkiego) otwierają się jak panel modułu.
		this.addRenderableWidget(FlatButton.builder(Component.literal(Lang.t("Ustawienia")), button -> this.buildPanel(Modules.GUI_THEME))
			.dimensions(this.sideX + 6, this.sideY + this.sideH - 26, bw, 20)
			.style(FlatButton.Style.SECONDARY)
			.build()
			.icon(Draw.Icon.GEAR)
			.tip("Motyw, układ i język menu oraz reset wszystkich ustawień."));

		this.defaultsButton = this.addRenderableWidget(FlatButton.builder(Component.literal(Lang.t("Przywróć domyślne")), button -> {
			if (this.panelModule != null) {
				this.panelModule.reset();
				Notifications.push(this.panelModule.name, "Przywrócono domyślne ustawienia", Theme.ACCENT);
				this.buildPanel(this.panelModule);
			}
		}).dimensions(0, 0, 100, 16).style(FlatButton.Style.GHOST).build());

		this.refresh();
		if (openModule != null) {
			this.buildPanel(openModule);
			this.panel.set(1.0F);
		}
	}

	private void refresh() {
		this.visible.clear();
		String q = query.trim().toLowerCase(Locale.ROOT);
		for (Module module : Modules.ALL) {
			// Settings (motyw, język, reset) ma własny przycisk w pasku bocznym, nie kartę.
			boolean match = module != Modules.GUI_THEME && (q.isEmpty() ? category == null || module.category == category : matches(module, q));
			if (match) {
				this.visible.add(module);
			}
		}
	}

	/** Wyszukiwanie po nazwie, opisie i nazwach ustawień (np. "kolor" znajdzie wszystko z kolorem). */
	private static boolean matches(Module module, String q) {
		if (module.name.toLowerCase(Locale.ROOT).contains(q) || Lang.t(module.description).toLowerCase(Locale.ROOT).contains(q)) {
			return true;
		}

		for (Setting<?> setting : module.settings) {
			if (!setting.hidden && Lang.t(setting.label).toLowerCase(Locale.ROOT).contains(q)) {
				return true;
			}
		}

		return false;
	}

	private Component menuKeyLabel() {
		return this.capturingMenuKey ? Component.literal(Lang.t("Naciśnij…")).withColor(Theme.ACCENT & 0xFFFFFF) : Component.literal("Menu: " + ModuleKeys.menuKeyName());
	}

	/** Reset wszystkiego (z panelu Settings) dopiero po potwierdzeniu w osobnym oknie. */
	private void resetAll() {
		this.minecraft.setScreen(new ConfirmScreen(confirmed -> {
			if (confirmed) {
				Modules.ALL.forEach(Module::reset);
				ConfigManager.save();
				Notifications.push("Ustawienia zresetowane", "Wszystkie funkcje mają wartości domyślne", Theme.WARNING);
			}

			this.minecraft.setScreen(this);
		}, Component.literal(Lang.t("Zresetować wszystkie ustawienia?")),
			Component.literal(Lang.t("Wszystkie funkcje wrócą do ustawień domyślnych, a elementy HUD na swoje miejsca.")),
			Component.literal(Lang.t("Tak, resetuj")).withStyle(ChatFormatting.RED), Component.literal(Lang.t("Anuluj"))));
	}

	// --- geometria kafelków ---

	private int columns() {
		if (Modules.GUI_THEME.cardStyle.is(1)) {
			return this.contentW - 16 - 2 * CARD_GAP >= 330 ? 3 : this.contentW - 16 - CARD_GAP >= 200 ? 2 : 1;
		}

		// Dwie kolumny jak w Shine, gdy karta ma min. 140 px (inaczej opisy ucinają się po 2 słowach).
		return this.contentW - 16 - CARD_GAP >= 280 ? 2 : 1;
	}

	/** Wysokosc modulu na liscie: karta albo kafelek z dwoma przyciskami (ustawienie Styl modulow). */
	private int cardH() {
		return Modules.GUI_THEME.cardStyle.is(1) ? TILE_H : CARD_H;
	}

	private int cardsTop() {
		return this.wy + (this.floating ? 42 : 68);
	}

	private int cardsBottom() {
		return this.wy + this.wh - 6;
	}

	private int cardW() {
		int cols = this.columns();
		return (this.contentW - 16 - (cols - 1) * CARD_GAP) / cols;
	}

	private int cardX(int index) {
		return this.contentX + 8 + index % this.columns() * (this.cardW() + CARD_GAP);
	}

	private int cardY(int index) {
		return this.cardsTop() + index / this.columns() * (this.cardH() + CARD_GAP) - Math.round(this.cardScroll.get());
	}

	private int cardsContentH() {
		int rows = (this.visible.size() + this.columns() - 1) / this.columns();
		return Math.max(0, rows * (this.cardH() + CARD_GAP) - CARD_GAP);
	}

	private Module cardAt(double mx, double my) {
		if (my < this.cardsTop() || my >= this.cardsBottom() || this.panel.get() > 0.05F) {
			return null;
		}

		for (int i = 0; i < this.visible.size(); i++) {
			if (inside(mx, my, this.cardX(i), this.cardY(i), this.cardW(), this.cardH())) {
				return this.visible.get(i);
			}
		}

		return null;
	}

	private boolean onCardSwitch(Module module, double mx, double my) {
		int i = this.visible.indexOf(module);
		int x = this.cardX(i) + this.cardW() - 28;
		int y = this.cardY(i) + 26;
		return inside(mx, my, x - 3, y - 3, 26, 16);
	}

	private static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private boolean insideCenter(double mx, double my) {
		return inside(mx, my, this.contentX, this.wy, this.contentW, this.wh);
	}

	// --- geometria paska bocznego ---

	private int sideTop() {
		return this.sideY + 66;
	}

	/** Odstęp między kategoriami: przyciski prawie stykają się jak w Shine; w niskim oknie ciaśniej. */
	private int sidePitch() {
		// 58 px na dole: przyciski "Menu: klawisz" i "Resetuj".
		return Math.max(16, Math.min(22, (this.sideH - 66 - 58) / (Module.Category.values().length + 1)));
	}

	private int sideRowY(int row) {
		return this.sideTop() + row * this.sidePitch();
	}

	/** -1 = "Wszystkie", 0.. = kategorie, -2 = nic. */
	private int sideRowAt(double mx, double my) {
		if (mx < this.sideX + 6 || mx >= this.sideX + this.sideW - 6) {
			return -2;
		}

		Module.Category[] categories = Module.Category.values();
		for (int row = 0; row <= categories.length; row++) {
			if (inside(mx, my, this.sideX + 6, this.sideRowY(row), this.sideW - 12, this.sidePitch() - 2)) {
				return row - 1;
			}
		}

		return -2;
	}

	// --- geometria panelu ustawień (wysuwa się nad środkowym panelem) ---

	private int panelW() {
		return this.contentW - 12;
	}

	private int panelX() {
		float t = Anim.ease(this.panel.get());
		return this.contentX + 6 + Math.round((1.0F - t) * (this.contentW + 4));
	}

	private int panelTop() {
		return this.wy + 6;
	}

	private int panelBottom() {
		return this.wy + this.wh - 6;
	}

	/** Bez prawego panelu podgląd jest w panelu ustawień; z prawym panelem tam, a ustawienia mają całą wysokość. */
	private int previewH() {
		boolean ownPreview = this.rightW == 0 && this.panelModule != null && this.panelModule.preview;
		return ownPreview ? Math.min(60, (this.panelBottom() - this.panelTop()) / 4) : 0;
	}

	private int rowsTop() {
		int preview = this.previewH();
		return this.panelTop() + 30 + (preview > 0 ? preview + 8 : 0);
	}

	private int rowsBottom() {
		return this.panelBottom() - 24;
	}

	private boolean panelOpen() {
		return this.panelModule != null && openModule != null;
	}

	// --- panel: budowa ---

	private void buildPanel(Module module) {
		for (Control control : this.controls) {
			if (control instanceof TextControl text) {
				this.removeWidget(text.field);
			} else if (control instanceof BindControl bind) {
				this.removeWidget(bind.field);
			}
		}

		this.controls.clear();
		this.dropdown = null;
		this.capturing = null;
		this.capturingBind = null;
		this.panelModule = module;
		openModule = module;
		this.panelScrollTarget = 0.0;
		this.panelScroll.set(0.0F);

		// Settings nie da się wyłączyć ani przypisać mu skrótu: to ustawienia samego menu.
		if (module != Modules.GUI_THEME) {
			this.controls.add(new ToggleControl(Component.literal(Lang.t("Włączona")), module::on, module::toggle));
			this.controls.add(new KeybindControl(module));
		}

		if (module == Modules.CROSSHAIR) {
			this.controls.add(new ActionControl("Rysuj celownik: bez celu i na celu", () -> this.minecraft.setScreen(new CrosshairEditorScreen(this))));
		}

		if (module == Modules.PARTY) {
			this.controls.add(new ActionControl("Otwórz menu party", () -> this.minecraft.setScreen(new PartyScreen(this))));
		}

		if (module == Modules.SOUNDS) {
			this.controls.add(new ActionControl("Otwórz listę dźwięków", () -> this.minecraft.setScreen(new SoundsScreen(this))));
		}

		if (module == Modules.WAYPOINTS) {
			this.controls.add(new ActionControl("Otwórz menu waypointów", () -> this.minecraft.setScreen(new WaypointScreen(this))));
			this.controls.add(new KeybindControl(module, pl.kerf.project.module.Waypoints.key, "Klawisz: dodaj waypoint tutaj"));
			this.controls.add(new KeybindControl(module, pl.kerf.project.module.Waypoints.menuKey, "Klawisz: menu waypointów"));
		}

		if (module == Modules.FIGHT_REPORT) {
			this.controls.add(new ActionControl("Pokaż ostatni raport", () -> {
				if (FightTracker.last() == null) {
					Notifications.push("Fight Report", "Jeszcze nie było żadnej walki", Theme.WARNING);
				} else {
					this.minecraft.setScreen(new FightReportScreen(this, FightTracker.last()));
				}
			}));
		}

		if (module != Modules.COMMAND_KEYS) {
			this.controls.add(new HeaderControl("Ustawienia"));
		}

		for (Setting<?> setting : module.settings) {
			if (setting.hidden) {
				continue;
			}

			Component label = label(setting);
			if (setting instanceof Setting.Bool bool) {
				if (bool.lockReason() != null) {
					this.controls.add(new LockedControl(label, bool.lockReason()));
				} else if (bool == Modules.GUI_THEME.rememberRisky) {
					this.controls.add(new ToggleControl(label, bool::on, this::toggleRememberRisky));
				} else {
					this.controls.add(new ToggleControl(label, bool::on, () -> bool.set(!bool.on())));
				}
			} else if (setting instanceof Setting.Mode mode) {
				this.controls.add(new DropdownControl(label, mode));
			} else if (setting instanceof Setting.Num num) {
				this.controls.add(new SliderControl(label, num));
			} else if (setting instanceof Setting.Color color) {
				this.controls.add(new ColorControl(label, color, module));
			} else if (setting instanceof Setting.Str str) {
				TextControl control = new TextControl(label, str);
				this.addRenderableWidget(control.field);
				this.controls.add(control);
			}
		}

		if (module == Modules.COMMAND_KEYS) {
			this.controls.add(new HeaderControl("Bindy"));
			this.controls.add(new NoteControl("Z / komenda, bez / czat"));
			for (Modules.CommandKeys.Bind bind : Modules.COMMAND_KEYS.binds) {
				BindControl control = new BindControl(bind);
				this.addRenderableWidget(control.field);
				this.controls.add(control);
			}

			this.controls.add(new ActionControl("+ Dodaj bind", () -> {
				Modules.COMMAND_KEYS.binds.add(new Modules.CommandKeys.Bind(-1, "/"));
				ConfigManager.save();
				this.rebuildPanel();
			}));
		} else if (module.settings.stream().allMatch(setting -> setting.hidden) && module != Modules.CROSSHAIR) {
			this.controls.add(new NoteControl("Ta funkcja nie ma dodatkowych ustawień."));
		}

		if (module == Modules.GUI_THEME) {
			this.controls.add(new ActionControl("Resetuj wszystkie ustawienia", this::resetAll));
		}

		int y = 0;
		for (Control control : this.controls) {
			control.y = y;
			y += control.height();
		}

		this.panelContentH = y;
	}

	/** Przebudowa panelu po dodaniu/usunięciu wiersza, bez skakania na górę listy. */
	private void rebuildPanel() {
		double scroll = this.panelScrollTarget;
		this.buildPanel(this.panelModule);
		this.panelScrollTarget = scroll;
		this.panelScroll.set((float) scroll);
	}

	private void closePanel() {
		openModule = null;
		this.dropdown = null;
		this.capturing = null;
		this.capturingBind = null;
	}

	/** Etykieta ustawienia; dla osi X/Y/Z ostatnia litera w kolorze osi. */
	private static Component label(Setting<?> setting) {
		if (setting instanceof Setting.Num num && num.axis != 0 && !num.label.isEmpty()) {
			String prefix = Lang.t(num.label.substring(0, num.label.length() - 1));
			String letter = num.label.substring(num.label.length() - 1);
			return Component.literal(prefix).append(Component.literal(letter).withColor(Modules.COORDINATES.axisColor(num.axis) & 0xFFFFFF));
		}

		return Component.literal(Lang.t(setting.label));
	}

	// --- wejście ---

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		double mx = click.x();
		double my = click.y();
		int button = click.button();

		if (this.dropdown != null) {
			if (!this.dropdown.clickList(mx, my)) {
				this.dropdown = null;
			}

			return true;
		}

		// Paski przewijania: chwyć i przeciągnij (dla myszy bez kółka), klik obok uchwytu przeskakuje w to miejsce.
		if (button == 0) {
			boolean panelShown = this.panel.get() > 0.5F && this.panelOpen();
			if (panelShown ? this.grabBar(mx, my, BAR_PANEL) : this.grabBar(mx, my, BAR_CARDS)) {
				return true;
			}
		}

		// Podczas czekania na klawisz: środkowy i boczne przyciski myszy (od 2 w górę) się przypisują,
		// lewy i prawy anulują (przypisanie ich do skrótu blokowałoby atak i używanie).
		int mouseCode = button >= 2 ? ModuleKeys.MOUSE + button : -1;
		if (this.capturing != null) {
			if (mouseCode >= 0) {
				this.capturing.assign(mouseCode);
				ConfigManager.save();
				Notifications.push(this.capturing.module.name, Lang.t("Skrót: ") + this.capturing.current(), Theme.ACCENT);
			}

			this.capturing = null;
			return true;
		}

		if (this.capturingMenuKey) {
			this.capturingMenuKey = false;
			if (mouseCode >= 0) {
				ModuleKeys.setMenuKey(mouseCode);
				Notifications.push("Menu AIO PvP", Lang.t("Klawisz: ") + ModuleKeys.menuKeyName(), Theme.ACCENT);
			}

			this.menuKey.setMessage(this.menuKeyLabel());
			return true;
		}

		if (this.capturingBind != null) {
			if (mouseCode >= 0) {
				this.capturingBind.bind.key = mouseCode;
				ConfigManager.save();
			}

			this.capturingBind = null;
			return true;
		}

		// Zamknięcie (X w nagłówku środkowego panelu).
		if (this.panel.get() < 0.05F && inside(mx, my, this.contentX + this.contentW - 22, this.wy + 8, 16, 16)) {
			this.onClose();
			return true;
		}

		if (super.mouseClicked(click, doubled)) {
			return true;
		}

		if (this.panel.get() > 0.05F && this.panelOpen() && this.insideCenter(mx, my)) {
			if (inside(mx, my, this.panelX(), this.panelTop(), this.panelW(), this.panelBottom() - this.panelTop())) {
				this.clickPanel(mx, my, button);
			} else {
				this.closePanel();
			}

			return true;
		}

		int row = this.sideRowAt(mx, my);
		if (row != -2) {
			category = row == -1 ? null : Module.Category.values()[row];
			this.search.setValue("");
			this.cardScrollTarget = 0.0;
			this.refresh();
			return true;
		}

		Module module = this.cardAt(mx, my);
		if (module != null) {
			boolean tiles = Modules.GUI_THEME.cardStyle.is(1);
			if (button == 0 && (tiles ? this.tileButton(this.visible.indexOf(module), mx, my) == 2 : this.onCardSwitch(module, mx, my))) {
				module.toggle();
			} else {
				this.buildPanel(module);
			}

			return true;
		}

		return false;
	}

	private void clickPanel(double mx, double my, int button) {
		int px = this.panelX();
		int w = this.panelW();
		// Przycisk "wstecz" w nagłówku panelu.
		if (inside(mx, my, px + 6, this.panelTop() + 7, 14, 14)) {
			this.closePanel();
			return;
		}

		if (my < this.rowsTop() || my >= this.rowsBottom()) {
			return;
		}

		int base = this.rowsTop() - Math.round(this.panelScroll.get());
		for (Control control : this.controls) {
			int cy = base + control.y;
			if (inside(mx, my, px + 6, cy, w - 12, control.height())) {
				control.click(mx, my, button, px + 6, cy, w - 12);
				return;
			}
		}
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
		if (this.draggingSlider != null) {
			this.draggingSlider.drag(click.x());
			return true;
		}

		if (this.draggingBar != 0) {
			this.dragBar(click.y());
			return true;
		}

		return super.mouseDragged(click, offsetX, offsetY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent click) {
		this.draggingSlider = null;
		this.draggingBar = 0;
		return super.mouseReleased(click);
	}

	// --- paski przewijania ---

	/** Geometria paska: x, góra, dół, wysokość treści, przewinięcie (dla listy modułów albo panelu ustawień). */
	private int[] bar(int which) {
		return which == BAR_PANEL
			? new int[] {this.panelX() + this.panelW() - 5, this.rowsTop(), this.rowsBottom(), this.panelContentH}
			: new int[] {this.contentX + this.contentW - 5, this.cardsTop(), this.cardsBottom(), this.cardsContentH()};
	}

	private boolean grabBar(double mx, double my, int which) {
		int[] bar = this.bar(which);
		int view = bar[2] - bar[1];
		if (bar[3] <= view || mx < bar[0] - 3 || mx >= bar[0] + 6 || my < bar[1] || my >= bar[2]) {
			return false;
		}

		int barH = barHeight(view, bar[3]);
		double scroll = which == BAR_PANEL ? this.panelScrollTarget : this.cardScrollTarget;
		double barY = bar[1] + (view - barH) * scroll / (bar[3] - view);
		// Chwycony uchwyt jedzie za kursorem z tym samym odstępem; klik w tor centruje uchwyt pod kursorem.
		this.barGrab = my >= barY && my < barY + barH ? my - barY : barH / 2.0;
		this.draggingBar = which;
		this.dragBar(my);
		return true;
	}

	private void dragBar(double my) {
		int[] bar = this.bar(this.draggingBar);
		int view = bar[2] - bar[1];
		int barH = barHeight(view, bar[3]);
		double fraction = Math.max(0.0, Math.min(1.0, (my - this.barGrab - bar[1]) / Math.max(1, view - barH)));
		double target = fraction * Math.max(0, bar[3] - view);
		// Bez animacji: uchwyt ma być dokładnie pod kursorem.
		if (this.draggingBar == BAR_PANEL) {
			this.panelScrollTarget = target;
			this.panelScroll.set((float) target);
		} else {
			this.cardScrollTarget = target;
			this.cardScroll.set((float) target);
		}
	}

	private static int barHeight(int view, int content) {
		return Math.max(14, view * view / content);
	}

	/** Do testu: pasek listy modułów (x, góra, dół) i obecne przewinięcie w pikselach GUI. */
	public double[] cardScrollbarForTest() {
		int[] bar = this.bar(BAR_CARDS);
		return new double[] {bar[0], bar[1], bar[2], this.cardScrollTarget};
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (this.dropdown != null) {
			return true;
		}

		if (this.panel.get() > 0.5F && this.panelOpen() && this.insideCenter(mouseX, mouseY)) {
			int max = Math.max(0, this.panelContentH - (this.rowsBottom() - this.rowsTop()));
			this.panelScrollTarget = Math.max(0.0, Math.min(max, this.panelScrollTarget - verticalAmount * ROW));
			return true;
		}

		if (this.insideCenter(mouseX, mouseY)) {
			int max = Math.max(0, this.cardsContentH() - (this.cardsBottom() - this.cardsTop()));
			this.cardScrollTarget = Math.max(0.0, Math.min(max, this.cardScrollTarget - verticalAmount * (this.cardH() + CARD_GAP) / 2.0));
			return true;
		}

		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		if (this.capturing != null) {
			Module module = this.capturing.module;
			if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
				this.capturing = null;
			} else {
				boolean clear = input.key() == GLFW.GLFW_KEY_BACKSPACE || input.key() == GLFW.GLFW_KEY_DELETE;
				KeybindControl captured = this.capturing;
				captured.assign(clear ? -1 : input.key());
				this.capturing = null;
				ConfigManager.save();
				Notifications.push(module.name, clear ? "Skrót usunięty" : Lang.t("Skrót: ") + captured.current(), Theme.ACCENT);
			}

			return true;
		}

		if (this.capturingBind != null) {
			Modules.CommandKeys.Bind bind = this.capturingBind.bind;
			this.capturingBind = null;
			if (input.key() != GLFW.GLFW_KEY_ESCAPE) {
				boolean clear = input.key() == GLFW.GLFW_KEY_BACKSPACE || input.key() == GLFW.GLFW_KEY_DELETE;
				bind.key = clear ? -1 : input.key();
				ConfigManager.save();
			}

			return true;
		}

		if (this.capturingMenuKey) {
			this.capturingMenuKey = false;
			if (input.key() != GLFW.GLFW_KEY_ESCAPE) {
				boolean clear = input.key() == GLFW.GLFW_KEY_BACKSPACE || input.key() == GLFW.GLFW_KEY_DELETE;
				ModuleKeys.setMenuKey(clear ? -1 : input.key());
				Notifications.push("Menu AIO PvP", clear ? "Klawisz usunięty (zostaje Mod Menu)" : Lang.t("Klawisz: ") + ModuleKeys.menuKeyName(), Theme.ACCENT);
			}

			this.menuKey.setMessage(this.menuKeyLabel());
			return true;
		}

		if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
			if (this.dropdown != null) {
				this.dropdown = null;
				return true;
			}

			if (this.panelOpen() && this.getFocused() != this.search) {
				this.closePanel();
				return true;
			}
		}

		return super.keyPressed(input);
	}

	// --- rysowanie ---

	@Override
	public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
		super.extractBackground(context, mouseX, mouseY, deltaTicks);
		context.fill(0, 0, this.width, this.height, 0x38000000);
	}

	/** Panel jak w Shine: granatowe półprzezroczyste szkło, kwadratowe rogi, cienka chłodna ramka. */
	private static void glassPanel(GuiGraphicsExtractor context, int x, int y, int w, int h, int r) {
		Draw.shadow(context, x, y, w, h, r, 4);
		Draw.card(context, x, y, w, h, r, Theme.BASE, Theme.BORDER_STRONG);
	}

	/** Układ menu z GUI Theme (wyłączony moduł = klasyczny). */
	private static int layoutSetting() {
		return Modules.GUI_THEME.layout.get();
	}

	/** Szerokość głównego panelu w pływającym układzie: moduły plus podgląd. */
	private int mainW() {
		return this.contentW + (this.rightW > 0 ? GAP + this.rightW : 0);
	}

	/** Szary przycisk/karta z ramką: wskazany jaśniejszy, aktywny z lawendową ramką. */
	private static void frameButton(GuiGraphicsExtractor context, int x, int y, int w, int h, boolean active, float hover) {
		// Ten sam kształt co FlatButton (rogi 4 px, projekt z Figmy), żeby wszystkie przyciski paska wyglądały jednakowo.
		Draw.roundExact(context, x, y, w, h, 4, Theme.mix(Theme.CARD, Theme.CARD_HOVER, active ? 1.0F : hover));
		Draw.outlineExact(context, x, y, w, h, 4, active ? Theme.BORDER_ACTIVE : Theme.mix(Theme.BORDER, Theme.BORDER_STRONG, hover));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
		Anim.frame();
		this.cardScroll.to((float) this.cardScrollTarget);
		this.panelScroll.to((float) this.panelScrollTarget);
		this.panel.to(this.panelOpen());

		if (layoutSetting() != this.layout || Lang.english() != this.english) {
			this.rebuildWidgets();
		}

		this.hoveredCard = this.dropdown == null ? this.cardAt(mouseX, mouseY) : null;
		this.tileMx = mouseX;
		this.tileMy = mouseY;
		glassPanel(context, this.sideX, this.sideY, this.sideW, this.sideH, this.panelR);
		if (this.floating) {
			// Moduły i podgląd w jednym panelu, rozdzielone cienką linią; nad nim pigułka wyszukiwarki.
			glassPanel(context, this.contentX, this.wy, this.mainW(), this.wh, this.panelR);
			if (this.rightW > 0) {
				context.fill(this.rightX - GAP / 2, this.wy + 8, this.rightX - GAP / 2 + 1, this.wy + this.wh - 8, Theme.DIVIDER);
			}

			if (this.search.visible) {
				boolean focused = this.getFocused() == this.search;
				Draw.shadow(context, this.contentX, this.searchY, this.mainW(), SEARCH_H, SEARCH_H / 2, 3);
				Draw.round(context, this.contentX, this.searchY, this.mainW(), SEARCH_H, SEARCH_H / 2, Theme.INSET);
				Draw.outline(context, this.contentX, this.searchY, this.mainW(), SEARCH_H, SEARCH_H / 2, focused ? Theme.BORDER_ACTIVE : Theme.BORDER_STRONG);
			}
		} else {
			glassPanel(context, this.contentX, this.wy, this.contentW, this.wh, this.panelR);
			if (this.rightW > 0) {
				glassPanel(context, this.rightX, this.wy, this.rightW, this.wh, this.panelR);
			}
		}

		this.renderSidebar(context, mouseX, mouseY);
		this.renderCenter(context, mouseX, mouseY);
		if (this.rightW > 0) {
			this.renderRight(context);
		}

		this.renderPanel(context, mouseX, mouseY);
		this.layoutPanelWidgets();

		super.extractRenderState(context, mouseX, mouseY, deltaTicks);
		if (this.dropdown != null) {
			this.dropdown.renderList(context, mouseX, mouseY);
		}

		Tip.render(context, mouseX, mouseY);
	}

	private void renderSidebar(GuiGraphicsExtractor context, int mouseX, int mouseY) {
		Font font = this.font;
		int x = this.sideX + 6;
		int w = this.sideW - 12;
		// Nagłówek jak "Active preset" w Shine: co to jest i ile jest włączone.
		long enabled = Modules.ALL.stream().filter(Module::on).count();
		Draw.text(context, font, Draw.fit(font, "Aktywne funkcje", w - 2), x + 2, this.sideY + 9, Theme.TEXT_2);
		Draw.text(context, font, enabled + Lang.t(" z ") + Modules.ALL.size(), x + 2, this.sideY + 21, Theme.TEXT);

		Module.Category[] categories = Module.Category.values();
		boolean searching = !query.isBlank();
		int activeRow = searching ? -1 : category == null ? 0 : category.ordinal() + 1;
		int pitch = this.sidePitch();
		int hovered = this.sideRowAt(mouseX, mouseY);
		for (int row = 0; row <= categories.length; row++) {
			Module.Category entry = row == 0 ? null : categories[row - 1];
			int y = this.sideRowY(row);
			int h = pitch - 2;
			boolean active = row == activeRow;
			int color = entry == null ? Theme.ACCENT : Theme.category(entry);
			frameButton(context, x, y, w, h, active, hovered == row - 1 ? 1.0F : 0.0F);
			Draw.Icon icon = entry == null ? Draw.Icon.ALL : Draw.Icon.of(entry);
			int textY = y + (h - 8) / 2;
			Draw.icon(context, icon, x + 9, textY - 1, active ? color : Theme.mix(color, Theme.TEXT_3, 0.35F));
			String name = entry == null ? Lang.t("Wszystkie") : entry.label;
			// "› " tylko gdy się mieści; przy wąskim pasku aktywność widać po tle, ramce i pasku.
			String label = active && font.width("› " + name) <= w - 28 ? "› " + name : name;
			Draw.text(context, font, Draw.fit(font, label, w - 28), x + 23, textY, active ? Theme.TEXT : Theme.TEXT_2);
		}
	}

	private void renderCenter(GuiGraphicsExtractor context, int mouseX, int mouseY) {
		Font font = this.font;
		int hx = this.contentX + 9;
		String heading = !query.isBlank() ? "Wyniki wyszukiwania" : category == null ? "Wszystkie funkcje" : category.label;
		Draw.text(context, font, heading, hx, this.wy + 9, Theme.TEXT);
		String hint = !query.isBlank() ? this.visible.size() + Lang.t(" pasujących funkcji") : categoryHint(category);
		Draw.text(context, font, Draw.fit(font, hint, this.contentW - 40), hx, this.wy + 21, Theme.TEXT_3);

		// Zamknięcie.
		int cx = this.contentX + this.contentW - 22;
		boolean closeHover = inside(mouseX, mouseY, cx, this.wy + 8, 16, 16);
		if (closeHover) {
			context.fill(cx, this.wy + 8, cx + 16, this.wy + 24, Theme.SURFACE_HOVER);
		}

		Draw.icon(context, Draw.Icon.CLOSE, cx + 4, this.wy + 12, closeHover ? Theme.TEXT : Theme.TEXT_3);
		context.fill(this.contentX + 1, this.wy + 34, this.contentX + this.contentW - 1, this.wy + 35, Theme.DIVIDER);

		// Pole wyszukiwania: czarne, z cienką jasną ramką, na całą szerokość (w pływającym układzie jest nad panelem).
		if (!this.floating) {
			int sx = this.contentX + 8;
			int sw = this.contentW - 16;
			boolean focused = this.getFocused() == this.search;
			Draw.round(context, sx, this.wy + 40, sw, 20, 4, Theme.INSET);
			Draw.outline(context, sx, this.wy + 40, sw, 20, 4, focused ? Theme.BORDER_ACTIVE : Theme.BORDER_STRONG);
		}

		if (this.visible.isEmpty()) {
			String empty = Lang.t("Nic nie pasuje do \"") + query.trim() + "\"";
			context.centeredText(font, Draw.fit(font, empty, this.contentW - 20), this.contentX + this.contentW / 2, this.cardsTop() + 24, Theme.TEXT_3);
			return;
		}

		int w = this.cardW();
		context.enableScissor(this.contentX + 1, this.cardsTop() - 2, this.contentX + this.contentW - 1, this.cardsBottom());
		for (int i = 0; i < this.visible.size(); i++) {
			Module module = this.visible.get(i);
			int x = this.cardX(i);
			int y = this.cardY(i);
			if (y + this.cardH() < this.cardsTop() - 2 || y > this.cardsBottom()) {
				continue;
			}

			this.renderCard(context, module, x, y, w, module == this.hoveredCard);
		}

		context.disableScissor();
		this.renderScrollbar(context, this.contentX + this.contentW - 5, this.cardsTop(), this.cardsBottom(), this.cardsContentH(), this.cardScroll.get());
	}

	/** Karta modułu z Figmy: ikona, nazwa, opis; przełącznik w prawym dolnym rogu. */
	private void renderCard(GuiGraphicsExtractor context, Module module, int x, int y, int w, boolean hovered) {
		if (Modules.GUI_THEME.cardStyle.is(1)) {
			this.renderTile(context, module, x, y, w, hovered);
			return;
		}

		Font font = this.font;
		float hover = this.cardHover.computeIfAbsent(module, m -> new Anim(0.0F, 16.0F)).to(hovered);
		float on = this.cardSwitch.computeIfAbsent(module, m -> new Anim(m.on() ? 1.0F : 0.0F, 16.0F)).to(module.on());
		// Rogi 4 px jak przyciski (projekt w Figmie, komponent "Karta modułu").
		Draw.round(context, x, y, w, CARD_H, 4, Theme.mix(Theme.CARD, Theme.CARD_HOVER, hover));
		Draw.outline(context, x, y, w, CARD_H, 4, Theme.mix(Theme.BORDER, Theme.BORDER_ACTIVE, hover));
		context.item(Previews.icon(module), x + 10, y + (CARD_H - 16) / 2);

		int textX = x + 34;
		Draw.text(context, font, Draw.fit(font, module.name, w - 34 - 8), textX, y + 12, Theme.mix(Theme.TEXT_2, Theme.TEXT, Math.max(on, hover)));
		String description = Draw.fit(font, module.description, w - 34 - 34);
		Draw.text(context, font, description, textX, y + 27, Theme.TEXT_3);
		if (hovered && !description.equals(module.description) && this.rightW == 0) {
			Tip.offer(module.description);
		}

		Draw.toggle(context, x + w - 28, y + 26, on);
	}

	private int tileMx;
	private int tileMy;
	/** Szerokość pola z małym przełącznikiem po prawej stronie paska kafelka (klik tu włącza/wyłącza, reszta kafelka otwiera ustawienia). */
	private static final int TILE_SWITCH_W = 30;
	private static final int TILE_BAR_H = 22;

	/** Część kafelka pod kursorem: 2 = przełącznik na pasku, 0 = reszta (otwiera ustawienia). */
	private int tileButton(int index, double mx, double my) {
		int x = this.cardX(index);
		int y = this.cardY(index);
		int w = this.cardW();
		int by = y + TILE_H - TILE_BAR_H;
		return my >= by && my < y + TILE_H && mx >= x + w - TILE_SWITCH_W && mx < x + w ? 2 : 0;
	}

	/**
	 * Kafelek w stylu OneConfig: szary prostokąt z dużą ikoną, a na dole pasek w kolorze akcentu z nazwą i małym przełącznikiem.
	 * Wyłączony moduł ma szary pasek. Opis pokazuje się po najechaniu.
	 */
	private void renderTile(GuiGraphicsExtractor context, Module module, int x, int y, int w, boolean hovered) {
		var font = this.font;
		float hover = this.cardHover.computeIfAbsent(module, m -> new Anim(0.0F, 16.0F)).to(hovered);
		float on = this.cardSwitch.computeIfAbsent(module, m -> new Anim(m.on() ? 1.0F : 0.0F, 16.0F)).to(module.on());
		int bar = TILE_BAR_H;
		Draw.round(context, x, y, w, TILE_H, 4, Theme.mix(Theme.CARD, Theme.CARD_HOVER, hover));
		// Pasek na dole: zaokrąglone tylko dolne rogi (prostokąt przykrywa górne).
		int barColor = Theme.mix(Theme.mix(Theme.SURFACE_3, Theme.CARD_HOVER, 0.5F), Theme.ACCENT, on);
		Draw.round(context, x, y + TILE_H - bar, w, bar, 4, barColor);
		context.fill(x, y + TILE_H - bar, x + w, y + TILE_H - bar + 6, barColor);
		Draw.outline(context, x, y, w, TILE_H, 4, Theme.mix(Theme.BORDER, Theme.BORDER_ACTIVE, hover));
		// Duża ikona (2x) w prawej części górnego pola.
		context.pose().pushMatrix();
		context.pose().translate(x + w - 44, y + 8);
		context.pose().scale(2.0F, 2.0F);
		context.item(Previews.icon(module), 0, 0);
		context.pose().popMatrix();
		Draw.text(context, font, Draw.fit(font, module.name, w - 20 - TILE_SWITCH_W), x + 10, y + TILE_H - bar + 7, 0xFFFFFFFF);
		// Mały przełącznik na pasku: ciemny tor, żeby było go widać na kolorowym tle; kółko po prawej = włączony.
		int sx = x + w - 28;
		int sy = y + TILE_H - bar + 6;
		Draw.round(context, sx, sy, 20, 10, 5, Theme.alpha(0xFF000000, 0x70));
		Draw.round(context, sx + 1 + Math.round(10 * on), sy + 1, 8, 8, 4, Theme.mix(0xFF9AA0AC, 0xFFFFFFFF, on));
		String description = module.description;
		if (hovered && this.rightW == 0) {
			Tip.offer(description);
		}
	}

	/** Prawy panel: duży podgląd z podpisem na dole, pod nim krótkie informacje. */
	private void renderRight(GuiGraphicsExtractor context) {
		Font font = this.font;
		Module module = this.hoveredCard != null ? this.hoveredCard : this.panelOpen() ? this.panelModule : null;
		int x = this.rightX + 6;
		int w = this.rightW - 12;
		int infoH = 4 * 12 + 6;
		int boxH = this.wh - 12 - infoH;
		int by = this.wy + 6;
		context.fill(x, by, x + w, by + boxH, Theme.INSET);
		if (module == null) {
			// Bez najechania nie pokazujemy ostatniego modułu, tylko podpowiedź.
			String hint = Draw.fit(font, "Najedź na moduł", w - 10);
			Draw.text(context, font, hint, x + (w - font.width(hint)) / 2, by + boxH / 2 - 4, Theme.TEXT_4);
			Draw.outline(context, x, by, w, boxH, 0, Theme.BORDER_STRONG);
			return;
		}

		context.enableScissor(x + 1, by + 1, x + w - 1, by + boxH - 1);
		Previews.draw(context, this.minecraft, module, x, by, w, boxH - 34);
		context.disableScissor();
		// Podpis na dole podglądu, na ciemnym przejściu.
		int capY = by + boxH - 34;
		context.fillGradient(x + 1, capY - 12, x + w - 1, by + boxH - 1, 0x00000000, 0xE0000000);
		int accent = Theme.category(module.category);
		Draw.text(context, font, Draw.fit(font, module.name, w - 10), x + 5, capY, Theme.TEXT);
		Draw.text(context, font, module.category.label, x + 5, capY + 10, accent);
		Draw.text(context, font, Draw.fit(font, module.description, w - 10), x + 5, capY + 20, Theme.TEXT_3);
		Draw.outline(context, x, by, w, boxH, 0, Theme.BORDER_STRONG);

		// Informacje u dołu, jak "Workspace / Enabled" w Shine.
		int infoTop = by + boxH + 8;
		long settings = module.settings.stream().filter(setting -> !setting.hidden).count();
		this.infoRow(context, x + 1, infoTop, w - 2, "Kategoria", module.category.label, Theme.TEXT_2);
		this.infoRow(context, x + 1, infoTop + 12, w - 2, "Stan", module.on() ? "Włączona" : "Wyłączona", module.on() ? Theme.SUCCESS : Theme.TEXT_4);
		this.infoRow(context, x + 1, infoTop + 24, w - 2, "Skrót", ModuleKeys.name(module.key), module.key < 0 ? Theme.TEXT_4 : Theme.TEXT_2);
		this.infoRow(context, x + 1, infoTop + 36, w - 2, "Ustawienia", String.valueOf(settings), Theme.TEXT_2);
	}

	private void infoRow(GuiGraphicsExtractor context, int x, int y, int w, String label, String value, int color) {
		Draw.text(context, this.font, label, x, y, Theme.TEXT_3);
		String shown = Draw.fit(this.font, value, w / 2);
		Draw.text(context, this.font, shown, x + w - this.font.width(shown), y, color);
	}

	private void renderScrollbar(GuiGraphicsExtractor context, int x, int top, int bottom, int content, float scroll) {
		int view = bottom - top;
		if (content <= view) {
			return;
		}

		int barH = barHeight(view, content);
		int barY = top + Math.round((view - barH) * scroll / (content - view));
		// Słaby tor pokazuje, że pasek da się chwycić; przeciągany uchwyt jaśniejszy.
		Draw.roundExact(context, x, top, 3, view, 1, Theme.alpha(Theme.TEXT_3, 0x22));
		Draw.roundExact(context, x, barY, 3, barH, 1, this.draggingBar != 0 ? Theme.alpha(Theme.TEXT_2, 0xD0) : Theme.alpha(Theme.TEXT_3, 0x90));
	}

	private void renderPanel(GuiGraphicsExtractor context, int mouseX, int mouseY) {
		float t = this.panel.get();
		if (t <= 0.01F || this.panelModule == null) {
			return;
		}

		Font font = this.font;
		Module module = this.panelModule;
		int clipX1 = this.contentX + 1;
		int clipX2 = this.contentX + this.contentW - 1;
		// Przyciemnienie kafelków pod panelem.
		Draw.round(context, this.contentX, this.wy, this.contentW, this.wh, this.panelR, Theme.alpha(0xFF08090B, Math.round(0xB0 * t)));
		int px = this.panelX();
		int pw = this.panelW();
		int top = this.panelTop();
		int ph = this.panelBottom() - top;
		context.enableScissor(clipX1, this.wy + 1, clipX2, this.wy + this.wh - 1);
		Draw.card(context, px, top, pw, ph, 4, Theme.GLASS_STRONG, Theme.BORDER_STRONG);

		// Nagłówek: wstecz, ikona, nazwa.
		boolean backHover = inside(mouseX, mouseY, px + 6, top + 7, 14, 14);
		if (backHover) {
			Draw.round(context, px + 6, top + 7, 14, 14, 3, Theme.SURFACE_HOVER);
		}

		Draw.icon(context, Draw.Icon.BACK, px + 8, top + 10, backHover ? Theme.TEXT : Theme.TEXT_3);
		context.item(Previews.icon(module), px + 24, top + 6);
		Draw.text(context, font, Draw.fit(font, module.name, pw - 60), px + 44, top + 7, Theme.TEXT);
		Draw.text(context, font, module.category.label, px + 44, top + 17, Theme.alpha(Theme.category(module.category), 0xC8));
		context.fill(px + 1, top + 29, px + pw - 1, top + 30, Theme.DIVIDER);

		// Podgląd w panelu tylko wtedy, gdy nie ma prawego panelu.
		int preview = this.previewH();
		if (preview > 0) {
			int bx = px + 8;
			int by = top + 36;
			int bw = pw - 16;
			Draw.round(context, bx, by, bw, preview, 4, 0x70000000);
			Draw.outline(context, bx, by, bw, preview, 4, Theme.BORDER);
			context.enableScissor(bx + 1, by + 1, bx + bw - 1, by + preview - 1);
			Previews.draw(context, this.minecraft, module, bx, by, bw, preview);
			context.disableScissor();
		}

		// Wiersze ustawień.
		int rowsTop = this.rowsTop();
		int rowsBottom = this.rowsBottom();
		int base = rowsTop - Math.round(this.panelScroll.get());
		context.enableScissor(px + 1, rowsTop, px + pw - 1, rowsBottom);
		for (Control control : this.controls) {
			int cy = base + control.y;
			if (cy + control.height() < rowsTop || cy > rowsBottom) {
				continue;
			}

			boolean hover = this.dropdown == null && this.capturing == null && inside(mouseX, mouseY, px + 6, cy, pw - 12, control.height())
				&& mouseY >= rowsTop && mouseY < rowsBottom;
			control.render(context, px + 6, cy, pw - 12, hover, mouseX, mouseY);
		}

		context.disableScissor();
		this.renderScrollbar(context, px + pw - 5, rowsTop, rowsBottom, this.panelContentH, this.panelScroll.get());
		context.fill(px + 1, rowsBottom + 2, px + pw - 1, rowsBottom + 3, Theme.DIVIDER);
		context.disableScissor();
	}

	/** Widgety panelu (pola tekstowe, przycisk domyślnych) jadą razem z panelem i chowają się poza listą. */
	private void layoutPanelWidgets() {
		boolean shown = this.panelModule != null && this.panel.get() > 0.02F;
		int px = this.panelX();
		int pw = this.panelW();
		this.defaultsButton.visible = shown && this.panel.get() > 0.9F;
		this.defaultsButton.setX(px + 8);
		this.defaultsButton.setY(this.rowsBottom() + 5);
		this.defaultsButton.setWidth(pw - 16);
		int base = this.rowsTop() - Math.round(this.panelScroll.get());
		for (Control control : this.controls) {
			if (control instanceof TextControl text) {
				int fy = base + control.y + 15;
				text.field.setX(px + 14);
				text.field.setY(fy + 3);
				text.field.setWidth(pw - 28);
				text.field.visible = shown && this.panel.get() > 0.9F && fy >= this.rowsTop() && fy + 16 <= this.rowsBottom();
			} else if (control instanceof BindControl bind) {
				int fy = base + control.y + 4;
				bind.field.setX(px + 6 + BindControl.KEY_W + 10);
				bind.field.setY(fy + 5);
				bind.field.setWidth(pw - 12 - BindControl.KEY_W - 10 - 26);
				bind.field.visible = shown && this.panel.get() > 0.9F && fy >= this.rowsTop() && fy + 18 <= this.rowsBottom();
			}
		}

		// Wyszukiwarka chowa się pod panelem ustawień, żeby nie dało się w nią kliknąć przez panel.
		this.search.visible = this.panel.get() < 0.05F;
	}

	@Override
	public void onClose() {
		ConfigManager.save();
		this.minecraft.setScreen(this.parent);
	}

	// --- kontrolki panelu ---

	/** Wiersz panelu ustawień. Współrzędne x, w to obszar wiersza (bez marginesów panelu). */
	private abstract class Control {
		int y;

		int height() {
			return ROW;
		}

		abstract void render(GuiGraphicsExtractor context, int x, int y, int w, boolean hover, int mouseX, int mouseY);

		void click(double mx, double my, int button, int x, int y, int w) {
		}

		/** Szerokość kontrolki po prawej stronie wiersza. */
		int controlW(int w) {
			return Math.max(84, Math.min(130, w * 48 / 100));
		}

		void row(GuiGraphicsExtractor context, int x, int y, int w, boolean hover, Component label) {
			if (hover) {
				Draw.round(context, x, y + 1, w, ROW - 2, 4, Theme.SURFACE_2);
			}

			int maxLabel = w - this.controlW(w) - 14;
			String plain = label.getString();
			if (SettingsScreen.this.font.width(plain) <= maxLabel) {
				context.text(SettingsScreen.this.font, label, x + 6, y + 6, Theme.TEXT_2, false);
			} else {
				Draw.text(context, SettingsScreen.this.font, Draw.fit(SettingsScreen.this.font, plain, maxLabel), x + 6, y + 6, Theme.TEXT_2);
				if (hover) {
					Tip.offer(plain);
				}
			}
		}
	}

	private final class HeaderControl extends Control {
		private final String title;

		HeaderControl(String title) {
			this.title = title;
		}

		@Override
		int height() {
			return 18;
		}

		@Override
		void render(GuiGraphicsExtractor context, int x, int y, int w, boolean hover, int mouseX, int mouseY) {
			Draw.text(context, SettingsScreen.this.font, Lang.t(this.title).toUpperCase(Locale.ROOT), x + 6, y + 6, Theme.TEXT_4);
			context.fill(x + 12 + SettingsScreen.this.font.width(Lang.t(this.title).toUpperCase(Locale.ROOT)), y + 12, x + w - 6, y + 13, Theme.DIVIDER);
		}
	}

	private final class NoteControl extends Control {
		private final String text;

		NoteControl(String text) {
			this.text = text;
		}

		@Override
		void render(GuiGraphicsExtractor context, int x, int y, int w, boolean hover, int mouseX, int mouseY) {
			Draw.text(context, SettingsScreen.this.font, Draw.fit(SettingsScreen.this.font, this.text, w - 12), x + 6, y + 6, Theme.TEXT_4);
		}
	}

	/** Wyłączenie od razu; włączenie ("na własne ryzyko") dopiero po oknie z ostrzeżeniem o banie. */
	private void toggleRememberRisky() {
		Setting.Bool option = Modules.GUI_THEME.rememberRisky;
		if (option.on()) {
			option.set(false);
			ConfigManager.save();
			return;
		}

		this.minecraft.setScreen(new ConfirmScreen(confirmed -> {
			if (confirmed) {
				option.set(true);
				ConfigManager.save();
			}

			this.minecraft.setScreen(this);
		}, Component.literal(Lang.t("Ryzykowne moduły zostaną zapamiętane")).withStyle(net.minecraft.ChatFormatting.RED),
			Component.literal(Lang.t("Freelook i Instant Hit zostaną włączone po każdym uruchomieniu gry, jeśli je włączysz. Na części serwerów możesz za to dostać bana. Robisz to na własne ryzyko.")),
			Component.literal(Lang.t("Biorę ryzyko")).withStyle(net.minecraft.ChatFormatting.RED), Component.literal(Lang.t("Anuluj"))));
	}

	/** Zablokowana opcja (robi to już inny mod): przyciemniony wiersz bez przełącznika i powód w drugiej linii. */
	private final class LockedControl extends Control {
		private final Component label;
		private final String reason;

		LockedControl(Component label, String reason) {
			this.label = label;
			this.reason = reason;
		}

		@Override
		int height() {
			return ROW + 11;
		}

		@Override
		void render(GuiGraphicsExtractor context, int x, int y, int w, boolean hover, int mouseX, int mouseY) {
			context.text(SettingsScreen.this.font, this.label, x + 6, y + 6, Theme.TEXT_4, false);
			String status = Lang.t("Zablokowane");
			Draw.text(context, SettingsScreen.this.font, status, x + w - 6 - SettingsScreen.this.font.width(status), y + 6, Theme.TEXT_4);
			context.text(SettingsScreen.this.font, Draw.fit(SettingsScreen.this.font, this.reason, w - 12), x + 6, y + ROW - 2, Theme.WARNING, false);
		}
	}

	private final class ToggleControl extends Control {
		private final Component label;
		private final BooleanSupplier state;
		private final Runnable toggle;
		private final Anim anim;

		ToggleControl(Component label, BooleanSupplier state, Runnable toggle) {
			this.label = label;
			this.state = state;
			this.toggle = toggle;
			this.anim = new Anim(state.getAsBoolean() ? 1.0F : 0.0F, 16.0F);
		}

		@Override
		void render(GuiGraphicsExtractor context, int x, int y, int w, boolean hover, int mouseX, int mouseY) {
			this.row(context, x, y, w, hover, this.label);
			float t = this.anim.to(this.state.getAsBoolean());
			String status = this.state.getAsBoolean() ? "Wł." : "Wył.";
			int sx = x + w - 28;
			Draw.text(context, SettingsScreen.this.font, status, sx - 4 - SettingsScreen.this.font.width(status), y + 6,
				this.state.getAsBoolean() ? Theme.TEXT_2 : Theme.TEXT_4);
			Draw.toggle(context, sx, y + 5, t);
		}

		@Override
		void click(double mx, double my, int button, int x, int y, int w) {
			if (button == 0) {
				this.toggle.run();
			}
		}
	}

	private final class KeybindControl extends Control {
		private final Module module;
		/** Klawisz ze Sterowania Minecrafta (ten sam obiekt co tam) albo null dla skrótu włączającego moduł. */
		private final net.minecraft.client.KeyMapping vanilla;
		private final String label;

		KeybindControl(Module module) {
			this(module, null, "Skrót klawiszowy");
		}

		KeybindControl(Module module, net.minecraft.client.KeyMapping vanilla, String label) {
			this.vanilla = vanilla;
			this.label = label;
			this.module = module;
		}

		@Override
		void render(GuiGraphicsExtractor context, int x, int y, int w, boolean hover, int mouseX, int mouseY) {
			this.row(context, x, y, w, hover, Component.literal(Lang.t(this.label)));
			int cw = this.controlW(w);
			int bx = x + w - cw - 4;
			boolean capturing = SettingsScreen.this.capturing == this;
			int border = capturing ? Theme.ACCENT : hover ? Theme.BORDER_STRONG : Theme.BORDER;
			Draw.round(context, bx, y + 2, cw, 16, 4, capturing ? Theme.alpha(Theme.ACCENT, 0x26) : Theme.SURFACE_2);
			Draw.outline(context, bx, y + 2, cw, 16, 4, border);
			String text;
			int color;
			if (capturing) {
				// Pulsujący napis, dopóki gracz nie wciśnie klawisza.
				float pulse = 0.55F + 0.45F * (float) Math.sin(System.currentTimeMillis() / 160.0);
				text = "Naciśnij klawisz…";
				color = Theme.mix(Theme.TEXT_3, Theme.TEXT, pulse);
			} else {
				text = this.current();
				color = this.unbound() ? Theme.TEXT_4 : Theme.TEXT;
			}

			text = Draw.fit(SettingsScreen.this.font, text, cw - 10);
			Draw.text(context, SettingsScreen.this.font, text, bx + (cw - SettingsScreen.this.font.width(text)) / 2, y + 6, color);
			if (hover && !capturing) {
				Tip.offer("Kliknij i naciśnij klawisz albo boczny/środkowy przycisk myszy. Backspace usuwa skrót, PPM też.");
			}
		}

		void assign(int code) {
			if (this.vanilla != null) {
				ModuleKeys.setBinding(this.vanilla, code);
			} else {
				this.module.key = code;
			}
		}

		boolean unbound() {
			return this.vanilla != null ? this.vanilla.isUnbound() : this.module.key < 0;
		}

		String current() {
			return this.vanilla != null ? (this.vanilla.isUnbound() ? Lang.t("Brak") : this.vanilla.getTranslatedKeyMessage().getString()) : ModuleKeys.name(this.module.key);
		}

		@Override
		void click(double mx, double my, int button, int x, int y, int w) {
			if (button == 1) {
				this.assign(-1);
				ConfigManager.save();
				return;
			}

			SettingsScreen.this.capturing = this;
		}
	}

	private final class SliderControl extends Control {
		private final Component label;
		private final Setting.Num setting;
		private final Anim anim;
		private int trackX;
		private int trackW;

		SliderControl(Component label, Setting.Num setting) {
			this.label = label;
			this.setting = setting;
			this.anim = new Anim(this.fraction(), 22.0F);
		}

		private float fraction() {
			return (float) ((this.setting.get() - this.setting.min) / (this.setting.max - this.setting.min));
		}

		@Override
		void render(GuiGraphicsExtractor context, int x, int y, int w, boolean hover, int mouseX, int mouseY) {
			this.row(context, x, y, w, hover, this.label);
			Font font = SettingsScreen.this.font;
			int cw = this.controlW(w);
			String value = this.setting.format();
			int valueW = Math.max(30, font.width(value));
			this.trackX = x + w - cw - 4;
			this.trackW = cw - valueW - 8;
			float t = this.anim.to(this.fraction());
			int axis = this.setting.axis != 0 ? Modules.COORDINATES.axisColor(this.setting.axis) : Theme.ACCENT;
			// Tor 6 px, gałka 8 px wyśrodkowana na torze (komponent "Suwak" w Figmie).
			int track = 6;
			int ty = y + 7;
			Draw.round(context, this.trackX, ty, this.trackW, track, 1, Theme.SURFACE_3);
			int filled = Math.round(this.trackW * t);
			Draw.round(context, this.trackX, ty, Math.max(track, filled), track, 1, axis);
			boolean active = SettingsScreen.this.draggingSlider == this;
			int knob = active || hover ? 9 : 8;
			int kx = this.trackX + filled - knob / 2;
			int ky = ty + track / 2 - knob / 2;
			Draw.round(context, kx, ky, knob, knob, knob / 2, 0xFFF2F4F8);
			Draw.outline(context, kx, ky, knob, knob, knob / 2, Theme.alpha(axis, 0xFF));
			Draw.text(context, font, value, x + w - 4 - font.width(value), y + 6, Theme.TEXT);
		}

		@Override
		void click(double mx, double my, int button, int x, int y, int w) {
			if (button == 0 && mx >= this.trackX - 4 && mx <= this.trackX + this.trackW + 4) {
				SettingsScreen.this.draggingSlider = this;
				this.drag(mx);
			}
		}

		void drag(double mx) {
			double f = Math.max(0.0, Math.min(1.0, (mx - this.trackX) / Math.max(1, this.trackW)));
			this.setting.set(this.setting.min + f * (this.setting.max - this.setting.min));
		}
	}

	private final class DropdownControl extends Control {
		private final Component label;
		private final Setting.Mode setting;
		private int boxX;
		private int boxY;
		private int boxW;

		DropdownControl(Component label, Setting.Mode setting) {
			this.label = label;
			this.setting = setting;
		}

		@Override
		void render(GuiGraphicsExtractor context, int x, int y, int w, boolean hover, int mouseX, int mouseY) {
			this.row(context, x, y, w, hover, this.label);
			Font font = SettingsScreen.this.font;
			int cw = this.controlW(w);
			this.boxX = x + w - cw - 4;
			this.boxY = y + 2;
			this.boxW = cw;
			boolean open = SettingsScreen.this.dropdown == this;
			Draw.round(context, this.boxX, this.boxY, cw, 16, 4, open ? Theme.SURFACE_3 : Theme.SURFACE_2);
			Draw.outline(context, this.boxX, this.boxY, cw, 16, 4, open ? Theme.alpha(Theme.ACCENT, 0xC0) : hover ? Theme.BORDER_STRONG : Theme.BORDER);
			String value = Draw.fit(font, this.setting.current(), cw - 20);
			Draw.text(context, font, value, this.boxX + 6, y + 6, Theme.TEXT);
			// Strzałka w dół.
			int ax = this.boxX + cw - 11;
			int ay = y + 8;
			context.fill(ax, ay, ax + 5, ay + 1, Theme.TEXT_3);
			context.fill(ax + 1, ay + 1, ax + 4, ay + 2, Theme.TEXT_3);
			context.fill(ax + 2, ay + 2, ax + 3, ay + 3, Theme.TEXT_3);
		}

		@Override
		void click(double mx, double my, int button, int x, int y, int w) {
			if (inside(mx, my, this.boxX, this.boxY, this.boxW, 16)) {
				SettingsScreen.this.dropdown = this;
				SettingsScreen.this.dropdownAnim.set(0.0F);
			}
		}

		private int listY() {
			int h = this.setting.options.length * 16 + 6;
			int below = this.boxY + 18;
			return below + h <= SettingsScreen.this.wy + SettingsScreen.this.wh - 4 ? below : this.boxY - h - 2;
		}

		void renderList(GuiGraphicsExtractor context, int mouseX, int mouseY) {
			Font font = SettingsScreen.this.font;
			float t = Anim.ease(SettingsScreen.this.dropdownAnim.to(1.0F));
			int h = this.setting.options.length * 16 + 6;
			int ly = this.listY();
			int shown = Math.max(1, Math.round(h * t));
			context.nextStratum();
			Draw.shadow(context, this.boxX, ly, this.boxW, shown, 4, 4);
			Draw.round(context, this.boxX, ly, this.boxW, shown, 4, Theme.POPUP);
			Draw.outline(context, this.boxX, ly, this.boxW, shown, 4, Theme.BORDER_STRONG);
			context.enableScissor(this.boxX, ly, this.boxX + this.boxW, ly + shown);
			for (int i = 0; i < this.setting.options.length; i++) {
				int oy = ly + 3 + i * 16;
				boolean hover = inside(mouseX, mouseY, this.boxX + 3, oy, this.boxW - 6, 16);
				boolean selected = this.setting.is(i);
				if (hover) {
					Draw.round(context, this.boxX + 3, oy, this.boxW - 6, 16, 3, Theme.SURFACE_HOVER);
				}

				Draw.text(context, font, Draw.fit(font, this.setting.options[i], this.boxW - 22), this.boxX + 8, oy + 4, selected ? Theme.TEXT : Theme.TEXT_2);
				if (selected) {
					Draw.round(context, this.boxX + this.boxW - 11, oy + 6, 4, 4, 2, Theme.ACCENT);
				}
			}

			context.disableScissor();
		}

		/** Klik w liście: wybiera opcję. Zwraca false, gdy klik był poza listą. */
		boolean clickList(double mx, double my) {
			int ly = this.listY();
			for (int i = 0; i < this.setting.options.length; i++) {
				if (inside(mx, my, this.boxX + 3, ly + 3 + i * 16, this.boxW - 6, 16)) {
					this.setting.set(i);
					SettingsScreen.this.dropdown = null;
					return true;
				}
			}

			return false;
		}
	}

	private final class ColorControl extends Control {
		private final Component label;
		private final Setting.Color setting;
		private final Module module;

		ColorControl(Component label, Setting.Color setting, Module module) {
			this.label = label;
			this.setting = setting;
			this.module = module;
		}

		@Override
		void render(GuiGraphicsExtractor context, int x, int y, int w, boolean hover, int mouseX, int mouseY) {
			this.row(context, x, y, w, hover, this.label);
			int cw = this.controlW(w);
			int bx = x + w - cw - 4;
			Draw.round(context, bx, y + 2, cw, 16, 4, Theme.SURFACE_2);
			Draw.outline(context, bx, y + 2, cw, 16, 4, hover ? Theme.BORDER_STRONG : Theme.BORDER);
			Ui.checker(context, bx + 4, y + 5, 10, 10);
			context.fill(bx + 4, y + 5, bx + 14, y + 15, this.setting.argb());
			Draw.outline(context, bx + 3, y + 4, 12, 12, 2, Theme.BORDER_STRONG);
			String hex = this.setting.hasAlpha ? String.format(Locale.ROOT, "#%08X", this.setting.get()) : String.format(Locale.ROOT, "#%06X", this.setting.get() & 0xFFFFFF);
			Draw.text(context, SettingsScreen.this.font, hex, bx + 22, y + 6, Theme.TEXT_2);
		}

		@Override
		void click(double mx, double my, int button, int x, int y, int w) {
			SettingsScreen.this.minecraft.setScreen(new ColorScreen(SettingsScreen.this, this.setting, this.module));
		}
	}

	private final class TextControl extends Control {
		private final Component label;
		final EditBox field;

		TextControl(Component label, Setting.Str setting) {
			this.label = label;
			this.field = new EditBox(SettingsScreen.this.font, 0, 0, 100, 12, label);
			this.field.setBordered(false);
			this.field.setMaxLength(2000);
			this.field.setValue(setting.get());
			this.field.moveCursorToStart(false);
			this.field.setResponder(setting::set);
		}

		@Override
		int height() {
			return 40;
		}

		@Override
		void render(GuiGraphicsExtractor context, int x, int y, int w, boolean hover, int mouseX, int mouseY) {
			context.text(SettingsScreen.this.font, this.label, x + 6, y + 4, Theme.TEXT_2, false);
			boolean focused = SettingsScreen.this.getFocused() == this.field;
			Draw.round(context, x + 2, y + 15, w - 4, 18, 4, Theme.SURFACE_2);
			Draw.outline(context, x + 2, y + 15, w - 4, 18, 4, focused ? Theme.alpha(Theme.ACCENT, 0xC0) : Theme.BORDER);
		}

		@Override
		void click(double mx, double my, int button, int x, int y, int w) {
			SettingsScreen.this.setFocused(this.field);
		}
	}

	/** Jeden bind Command Keys: [klawisz] [komenda...] [x]. */
	private final class BindControl extends Control {
		static final int KEY_W = 64;
		final Modules.CommandKeys.Bind bind;
		final EditBox field;

		BindControl(Modules.CommandKeys.Bind bind) {
			this.bind = bind;
			this.field = new EditBox(SettingsScreen.this.font, 0, 0, 100, 12, Component.literal(Lang.t("Komenda")));
			this.field.setBordered(false);
			this.field.setMaxLength(Modules.CommandKeys.MAX_LENGTH);
			this.field.setValue(bind.command);
			this.field.moveCursorToStart(false);
			this.field.setHint(Component.literal(Lang.t("/spawn")).withColor(Theme.TEXT_4 & 0xFFFFFF));
			this.field.setResponder(text -> bind.command = text);
		}

		@Override
		int height() {
			return 26;
		}

		@Override
		void render(GuiGraphicsExtractor context, int x, int y, int w, boolean hover, int mouseX, int mouseY) {
			Font font = SettingsScreen.this.font;
			boolean capturing = SettingsScreen.this.capturingBind == this;
			boolean keyHover = inside(mouseX, mouseY, x + 2, y + 4, KEY_W, 18);
			Draw.round(context, x + 2, y + 4, KEY_W, 18, 4, capturing ? Theme.alpha(Theme.ACCENT, 0x26) : Theme.SURFACE_2);
			Draw.outline(context, x + 2, y + 4, KEY_W, 18, 4, capturing ? Theme.ACCENT : keyHover ? Theme.BORDER_STRONG : Theme.BORDER);
			String key = capturing ? "Naciśnij…" : this.bind.key < 0 ? "Klawisz" : ModuleKeys.name(this.bind.key);
			key = Draw.fit(font, key, KEY_W - 8);
			int keyColor = capturing ? Theme.ACCENT : this.bind.key < 0 ? Theme.TEXT_4 : Theme.TEXT;
			Draw.text(context, font, key, x + 2 + (KEY_W - font.width(key)) / 2, y + 9, keyColor);
			if (keyHover && !capturing) {
				Tip.offer("Kliknij i naciśnij klawisz albo boczny/środkowy przycisk myszy. Backspace usuwa.");
			}

			int fx = x + KEY_W + 6;
			int fw = w - KEY_W - 6 - 22;
			boolean focused = SettingsScreen.this.getFocused() == this.field;
			Draw.round(context, fx, y + 4, fw, 18, 4, Theme.SURFACE_2);
			Draw.outline(context, fx, y + 4, fw, 18, 4, focused ? Theme.alpha(Theme.ACCENT, 0xC0) : Theme.BORDER);

			int dx = x + w - 18;
			boolean deleteHover = inside(mouseX, mouseY, dx, y + 5, 16, 16);
			if (deleteHover) {
				Draw.round(context, dx, y + 5, 16, 16, 3, Theme.alpha(Theme.DANGER, 0x40));
				Tip.offer("Usuń bind");
			}

			Draw.icon(context, Draw.Icon.CLOSE, dx + 4, y + 9, deleteHover ? Theme.DANGER : Theme.TEXT_3);
		}

		@Override
		void click(double mx, double my, int button, int x, int y, int w) {
			if (inside(mx, my, x + 2, y + 4, KEY_W, 18)) {
				SettingsScreen.this.capturingBind = this;
				SettingsScreen.this.setFocused(null);
			} else if (inside(mx, my, x + w - 18, y + 5, 16, 16)) {
				Modules.COMMAND_KEYS.binds.remove(this.bind);
				ConfigManager.save();
				SettingsScreen.this.rebuildPanel();
			} else {
				SettingsScreen.this.setFocused(this.field);
			}
		}
	}

	/** Przycisk na całą szerokość panelu (np. otwarcie edytora celownika). */
	private final class ActionControl extends Control {
		private final String label;
		private final Runnable action;
		private final Anim hover = new Anim(0.0F, 18.0F);

		ActionControl(String label, Runnable action) {
			this.label = label;
			this.action = action;
		}

		@Override
		int height() {
			return 28;
		}

		@Override
		void render(GuiGraphicsExtractor context, int x, int y, int w, boolean hover, int mouseX, int mouseY) {
			float h = this.hover.to(hover);
			Draw.round(context, x + 2, y + 4, w - 4, 20, 4, Theme.mix(Theme.alpha(Theme.ACCENT, 0x30), Theme.alpha(Theme.ACCENT, 0x55), h));
			Draw.outline(context, x + 2, y + 4, w - 4, 20, 4, Theme.alpha(Theme.ACCENT, 0xA0));
			String text = Draw.fit(SettingsScreen.this.font, this.label, w - 16);
			Draw.text(context, SettingsScreen.this.font, text, x + (w - SettingsScreen.this.font.width(text)) / 2, y + 10, Theme.TEXT);
		}

		@Override
		void click(double mx, double my, int button, int x, int y, int w) {
			this.action.run();
		}
	}

}
