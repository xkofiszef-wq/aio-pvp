package pl.kerf.project.render;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.entity.Entity;

/**
 * Które encje dostały niedawno cios od innej encji (gracz, mob, pocisk). Hit Color koloruje tylko takie trafienia:
 * ogień, lawa, upadek i inne obrażenia bez sprawcy też zapalają vanillowy efekt "boli", ale to nie jest uderzenie.
 */
public final class HitTracker {
	/** Tyle ticków po ciosie efekt nadal liczy się jako uderzenie (maksymalny czas efektu "boli" to 10). */
	private static final int WINDOW_TICKS = 12;
	private static final Map<Entity, Integer> HITS = new WeakHashMap<>();

	private HitTracker() {
	}

	public static void mark(Entity entity) {
		HITS.put(entity, entity.age);
	}

	public static boolean recent(Entity entity) {
		Integer at = HITS.get(entity);
		return at != null && entity.age >= at && entity.age - at <= WINDOW_TICKS;
	}
}
