package pl.kerf.project.gui.theme;

import pl.kerf.project.module.Module;

/**
 * Paleta w stylu Shine: granatowe półprzezroczyste panele, przez które widać świat, szare karty
 * z kolorowym paskiem kategorii i cienkie chłodne ramki. Kolor pojawia się tylko w paskach i ikonach.
 */
public final class Theme {
	/** Tło paneli: granatowe szkło. */
	public static int BASE = 0xB40C1020;
	/** Warstwy nad panelami (panel ustawień, powiadomienia): mocniej kryjące, żeby tekst pod spodem nie przeszkadzał. */
	public static int GLASS_STRONG = 0xE80E1222;
	public static int POPUP = 0xF4121728;
	/** Karty i przyciski: szary grafit, prawie kryjący. */
	public static int CARD = 0xD8282B34;
	public static int CARD_HOVER = 0xE233363F;
	/** Powierzchnie: białe nakładki na szkło, coraz jaśniejsze. */
	public static final int SURFACE = 0x12FFFFFF;
	public static final int SURFACE_2 = 0x1CFFFFFF;
	public static final int SURFACE_3 = 0x2AFFFFFF;
	public static final int SURFACE_HOVER = 0x30FFFFFF;
	public static int BORDER = 0x40707896;
	public static int BORDER_STRONG = 0x90808AA8;
	/** Ramka aktywnego lub wskazanego elementu (jasny lawendowy jak w Shine). */
	public static int BORDER_ACTIVE = 0xE0B4B0E8;
	public static int DIVIDER = 0x30707896;
	/** Pole wyszukiwania i ramki podglądu: prawie czarne. */
	public static int INSET = 0xD0040508;

	public static final int TEXT = 0xFFF0F1F5;
	public static final int TEXT_2 = 0xFFC2C6D0;
	public static final int TEXT_3 = 0xFF8C92A0;
	public static final int TEXT_4 = 0xFF5E6472;

	/** Główny akcent (lawendowy błękit). */
	public static int ACCENT = 0xFF8C8CF0;
	public static final int SUCCESS = 0xFF6CC98E;
	public static final int WARNING = 0xFFE6B34E;
	public static final int DANGER = 0xFFE5675F;

	/**
	 * Motywy GUI (moduł GUI Theme): tło okien, karty, pola, ramki i akcent. Kolumny: BASE, GLASS_STRONG, POPUP, CARD,
	 * CARD_HOVER, INSET, BORDER, BORDER_STRONG, BORDER_ACTIVE, DIVIDER, ACCENT. Teksty i kolory kategorii zostają,
	 * więc każdy motyw ma tę samą czytelność. Pierwszy to domyślny (granat), zgodny z plikiem Figmy.
	 */
	public static final String[] THEME_NAMES = {"Granat", "Grafit", "Północ (czarny)", "Las", "Wino", "Ocean", "Zachód"};
	private static final int[][] THEMES = {
		{0xB40C1020, 0xE80E1222, 0xF4121728, 0xD8282B34, 0xE233363F, 0xD0040508, 0x40707896, 0x90808AA8, 0xE0B4B0E8, 0x30707896, 0xFF8C8CF0},
		{0xB4121316, 0xE8151619, 0xF418191D, 0xD82A2B2F, 0xE2353639, 0xD0050506, 0x40808084, 0x90A0A0A8, 0xE0D0D0D8, 0x30808084, 0xFFC8C8D0},
		{0xD0000000, 0xF0050505, 0xF80A0A0A, 0xE0161616, 0xE8202020, 0xE0000000, 0x40505050, 0x80707070, 0xE0F0F0F0, 0x30505050, 0xFF5AA9FF},
		{0xB40C1A12, 0xE80E1C14, 0xF4122218, 0xD8243028, 0xE22E3A32, 0xD0040805, 0x40709680, 0x9080A890, 0xE0B0E8C0, 0x30709680, 0xFF6CC98E},
		{0xB41A0C10, 0xE81C0E12, 0xF4221216, 0xD8302428, 0xE23A2E32, 0xD0080405, 0x40967078, 0x90A88088, 0xE0E8B0B8, 0x30967078, 0xFFE5675F},
		{0xB40C1A1C, 0xE80E1C1F, 0xF4122226, 0xD8243034, 0xE22E3A3E, 0xD0040808, 0x40709096, 0x9080A4A8, 0xE0B0E4E8, 0x30709096, 0xFF4FC3D9},
		{0xB41C140C, 0xE81F160E, 0xF4261C12, 0xD8342C24, 0xE23E362E, 0xD0080604, 0x40968670, 0x90A89A80, 0xE0E8D4B0, 0x30968670, 0xFFE6A04E}
	};

	private Theme() {
	}

	/** Ustawia motyw; accent != null nadpisuje kolor akcentu motywu (własny kolor z ustawień). */
	public static void apply(int theme, Integer accent) {
		int[] t = THEMES[Math.max(0, Math.min(THEMES.length - 1, theme))];
		BASE = t[0];
		GLASS_STRONG = t[1];
		POPUP = t[2];
		CARD = t[3];
		CARD_HOVER = t[4];
		INSET = t[5];
		BORDER = t[6];
		BORDER_STRONG = t[7];
		BORDER_ACTIVE = t[8];
		DIVIDER = t[9];
		ACCENT = accent != null ? accent | 0xFF000000 : t[10];
	}

	public static int category(Module.Category category) {
		return switch (category) {
			case HUD -> 0xFF6FA8E8;
			case COMBAT -> 0xFFE5675F;
			case VISUALS -> 0xFFE6B34E;
			case ITEMS -> 0xFF6CC98E;
			case MISC -> 0xFFA88CE8;
		};
	}

	/** Kolor z nadpisanym kryciem (0..255). */
	public static int alpha(int argb, int alpha) {
		return Math.max(0, Math.min(255, alpha)) << 24 | argb & 0xFFFFFF;
	}

	/** Liniowe przejście między dwoma kolorami ARGB (t = 0..1). */
	public static int mix(int a, int b, float t) {
		t = Math.max(0.0F, Math.min(1.0F, t));
		int aa = a >>> 24;
		int ar = a >> 16 & 0xFF;
		int ag = a >> 8 & 0xFF;
		int ab = a & 0xFF;
		int ba = b >>> 24;
		int br = b >> 16 & 0xFF;
		int bg = b >> 8 & 0xFF;
		int bb = b & 0xFF;
		return Math.round(aa + (ba - aa) * t) << 24 | Math.round(ar + (br - ar) * t) << 16 | Math.round(ag + (bg - ag) * t) << 8 | Math.round(ab + (bb - ab) * t);
	}
}
