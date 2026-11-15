package pl.kerf.project.gui.theme;

import pl.kerf.project.module.Module;

/**
 * Paleta w stylu Shine: granatowe półprzezroczyste panele, przez które widać świat, szare karty
 * z kolorowym paskiem kategorii i cienkie chłodne ramki. Kolor pojawia się tylko w paskach i ikonach.
 */
public final class Theme {
	/** Tło paneli: granatowe szkło. */
	public static final int BASE = 0xB40C1020;
	/** Warstwy nad panelami (panel ustawień, powiadomienia): mocniej kryjące, żeby tekst pod spodem nie przeszkadzał. */
	public static final int GLASS_STRONG = 0xE80E1222;
	public static final int POPUP = 0xF4121728;
	/** Karty i przyciski: szary grafit, prawie kryjący. */
	public static final int CARD = 0xD8282B34;
	public static final int CARD_HOVER = 0xE233363F;
	/** Powierzchnie: białe nakładki na szkło, coraz jaśniejsze. */
	public static final int SURFACE = 0x12FFFFFF;
	public static final int SURFACE_2 = 0x1CFFFFFF;
	public static final int SURFACE_3 = 0x2AFFFFFF;
	public static final int SURFACE_HOVER = 0x30FFFFFF;
	public static final int BORDER = 0x40707896;
	public static final int BORDER_STRONG = 0x90808AA8;
	/** Ramka aktywnego lub wskazanego elementu (jasny lawendowy jak w Shine). */
	public static final int BORDER_ACTIVE = 0xE0B4B0E8;
	public static final int DIVIDER = 0x30707896;
	/** Pole wyszukiwania i ramki podglądu: prawie czarne. */
	public static final int INSET = 0xD0040508;

	public static final int TEXT = 0xFFF0F1F5;
	public static final int TEXT_2 = 0xFFC2C6D0;
	public static final int TEXT_3 = 0xFF8C92A0;
	public static final int TEXT_4 = 0xFF5E6472;

	/** Główny akcent (lawendowy błękit). */
	public static final int ACCENT = 0xFF8C8CF0;
	public static final int SUCCESS = 0xFF6CC98E;
	public static final int WARNING = 0xFFE6B34E;
	public static final int DANGER = 0xFFE5675F;

	private Theme() {
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
