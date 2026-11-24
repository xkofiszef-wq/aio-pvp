package pl.kerf.project.render;

import net.minecraft.client.Minecraft;
import net.minecraft.core.SectionPos;

/**
 * Fog Occlusion: fragmenty świata dalej niż koniec mgły są w niej całkiem niewidoczne, więc nie ma po co ich budować
 * ani rysować. Koniec mgły bierzemy z danych mgły liczonych przez Minecrafta w każdej klatce.
 */
public final class FogOcclusion {
	/** Zapas na rozmiar fragmentu (16 bloków, przekątna ok. 14) po stronie kamery i po stronie fragmentu. */
	private static final double MARGIN = 28.0;
	/** Zmiana końca mgły o tyle bloków przebudowuje listę widocznych fragmentów (wejście do wody, wyjście z niej). */
	private static final float RESCHEDULE_STEP = 8.0F;
	private static volatile float end = Float.MAX_VALUE;
	private static float scheduledFor = Float.MAX_VALUE;

	private FogOcclusion() {
	}

	/** Wołane przy zapisie danych mgły (wątek renderu). 0 albo mniej oznacza "brak tej mgły". */
	public static void capture(float environmentalEnd, float renderDistanceEnd) {
		float value = renderDistanceEnd > 0.0F ? renderDistanceEnd : Float.MAX_VALUE;
		if (environmentalEnd > 0.0F && environmentalEnd < value) {
			value = environmentalEnd;
		}

		end = value;
		// Lista fragmentów jest liczona rzadko (przy ruchu kamery); po zmianie mgły trzeba ją odświeżyć, inaczej zostają dziury.
		if (Math.abs(Math.min(value, 4096.0F) - Math.min(scheduledFor, 4096.0F)) >= RESCHEDULE_STEP) {
			scheduledFor = value;
			Minecraft client = Minecraft.getInstance();
			if (client.levelRenderer != null) {
				client.levelRenderer.needsUpdate();
			}
		}
	}

	/** Czy fragment (pozycja spakowana jak w {@link SectionPos}) leży całkowicie za końcem mgły względem fragmentu kamery. */
	public static boolean beyond(long centerSection, long otherSection) {
		float limit = end;
		if (limit >= 4096.0F) {
			return false;
		}

		double dx = (SectionPos.x(otherSection) - SectionPos.x(centerSection)) * 16.0;
		double dy = (SectionPos.y(otherSection) - SectionPos.y(centerSection)) * 16.0;
		double dz = (SectionPos.z(otherSection) - SectionPos.z(centerSection)) * 16.0;
		return Math.sqrt(dx * dx + dy * dy + dz * dz) - MARGIN > limit;
	}
}
