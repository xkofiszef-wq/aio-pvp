package pl.kerf.project.render;

import java.util.Locale;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.object.AtlasTextObjectContents;
import net.minecraft.util.Atlases;
import net.minecraft.util.Identifier;
import pl.kerf.project.module.Modules;

/**
 * Tekst wskaźnika zdrowia przy nicku. Serca to sprite'y z atlasu GUI (te same co na pasku zdrowia),
 * więc texture pack, który zmienia serca na pasku, zmienia je też nad głową.
 */
public final class HealthLabel {
	private static final Text FULL = sprite("hud/heart/full");
	private static final Text HALF = sprite("hud/heart/half");
	private static final Text EMPTY = sprite("hud/heart/container");
	private static final Text ABSORB_FULL = sprite("hud/heart/absorbing_full");
	private static final Text ABSORB_HALF = sprite("hud/heart/absorbing_half");
	private static final int RED = 0xFF5555;
	private static final int GOLD = 0xFFAA00;
	private static final int MAX_HEARTS = 10;
	/** Ustawiane na czas wysłania etykiety z sercami, żeby renderer etykiet pominął jej tło. */
	public static boolean submitting;
	/**
	 * Gotowe etykiety: ten sam stan zdrowia daje zawsze ten sam tekst, więc nie składamy go co klatkę dla każdego gracza.
	 * Klucz zawiera styl i wszystkie liczby, od których zależy wygląd.
	 */
	private static final java.util.Map<Long, Text> CACHE = new java.util.HashMap<>();

	private HealthLabel() {
	}

	private static Text sprite(String path) {
		return Text.object(new AtlasTextObjectContents(Atlases.GUI, Identifier.ofVanilla(path)));
	}

	public static Text build(float health, float maxHealth, float absorption) {
		// Tak jak pasek vanilla: zaokrąglenie w górę do pełnych punktów (pół serca).
		int points = Math.max(0, (int) Math.ceil(health));
		int absorptionPoints = Modules.HEALTH.absorption.on() ? Math.max(0, (int) Math.ceil(absorption)) : 0;
		int style = Modules.HEALTH.style.get();
		int max = Math.max(0, (int) Math.ceil(maxHealth));
		long key = (long) style << 60 | (long) Math.min(points, 0xFFFFF) << 40 | (long) Math.min(max, 0xFFFFF) << 20 | Math.min(absorptionPoints, 0xFFFFF);
		Text cached = CACHE.get(key);
		if (cached != null) {
			return cached;
		}

		if (CACHE.size() > 512) {
			// shortcut: czyścimy całość zamiast LRU; przy tylu różnych stanach zdrowia i tak budujemy od nowa.
			CACHE.clear();
		}

		Text text = compose(points, maxHealth, absorptionPoints);
		CACHE.put(key, text);
		return text;
	}

	private static Text compose(int points, float maxHealth, int absorptionPoints) {
		MutableText text = Text.empty();
		if (Modules.HEALTH.style.is(0)) {
			// Jeden rząd jak na pasku: max 10 serc, nadmiar zdrowia (np. z atrybutów) ucinamy.
			int slots = Math.max(1, Math.min(MAX_HEARTS, (int) Math.ceil(maxHealth / 2.0F)));
			int full = Math.min(slots, points / 2);
			boolean half = full < slots && points % 2 == 1;
			int empty = slots - full - (half ? 1 : 0);
			repeat(text, FULL, full);
			if (half) {
				text.append(HALF);
			}

			repeat(text, EMPTY, empty);
			if (absorptionPoints > 0) {
				int absorbFull = Math.min(MAX_HEARTS, absorptionPoints / 2);
				repeat(text, ABSORB_FULL, absorbFull);
				if (absorbFull < MAX_HEARTS && absorptionPoints % 2 == 1) {
					text.append(ABSORB_HALF);
				}
			}
		} else {
			float fraction = maxHealth > 0.0F ? points / maxHealth : 1.0F;
			int color = fraction > 0.6F ? 0x55FF55 : fraction > 0.3F ? 0xFFFF55 : RED;
			text.append(Text.literal(hearts(points) + " ").withColor(color));
			text.append(FULL);
			if (absorptionPoints > 0) {
				text.append(Text.literal(" +" + hearts(absorptionPoints) + " ").withColor(GOLD));
				text.append(ABSORB_FULL);
			}
		}

		return text;
	}

	private static void repeat(MutableText text, Text part, int count) {
		for (int i = 0; i < count; i++) {
			text.append(part);
		}
	}

	/** Punkty zdrowia jako liczba serc: 10 -> "5", 11 -> "5.5". */
	private static String hearts(int points) {
		return points % 2 == 0 ? String.valueOf(points / 2) : String.format(Locale.ROOT, "%.1f", points / 2.0F);
	}
}
