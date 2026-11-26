package pl.kerf.project.hud;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import pl.kerf.project.Lang;
import pl.kerf.project.config.Setting;

/**
 * Odległość do celu przy ostatnim trafieniu, jak Reach Display w klientach PvP. Mierzona w chwili ataku od oczu do
 * hitboxa (tak liczy serwer), a pokazywana dopiero, gdy serwer potwierdzi obrażenia, więc pudła się nie liczą.
 */
public final class ReachHud extends InfoHud {
	/** Ile czekamy na potwierdzenie z serwera po ataku (z zapasem na wysoki ping). */
	private static final long CONFIRM_NANOS = 1_000_000_000L;
	private static final double EXAMPLE = 2.87;

	private static Entity pendingTarget;
	private static double pendingDistance;
	private static long pendingAt;
	private static double shownDistance;
	private static long shownAt;

	public final Setting.Num duration = this.add(new Setting.Num("duration", "Pokazuj przez", 3.0, 1.0, 10.0, 0.5, " s"));

	public ReachHud() {
		super("reach_display", "Reach Display", "Odległość do celu przy ostatnim trafieniu, od oczu do hitboxa (tak liczy serwer).",
			0.0F, 0.0F, 0.0F, 0.0F, 4.0F, 112.0F, (client, editing) -> null);
	}

	/** Nasz atak (strona klienta): zapamiętuje odległość, zanim cel się ruszy. */
	public static void attack(Minecraft client, Entity target) {
		if (client.player == null || target == client.player) {
			return;
		}

		pendingTarget = target;
		pendingDistance = Math.sqrt(target.getBoundingBox().distanceToSqr(client.player.getEyePosition()));
		pendingAt = System.nanoTime();
	}

	/** Serwer potwierdził obrażenia encji: jeśli to nasz świeży atak, cios wszedł z zapamiętanej odległości. */
	public static void damaged(Entity entity) {
		if (entity == null || entity != pendingTarget || System.nanoTime() - pendingAt > CONFIRM_NANOS) {
			return;
		}

		shownDistance = pendingDistance;
		shownAt = System.nanoTime();
		pendingTarget = null;
	}

	/** Odległość ostatniego potwierdzonego trafienia (NaN, gdy jeszcze żadnego nie było). */
	public static double lastDistance() {
		return shownAt == 0L ? Double.NaN : shownDistance;
	}

	@Override
	protected Component text(Minecraft client, boolean editing) {
		boolean fresh = shownAt != 0L && System.nanoTime() - shownAt <= (long) (this.duration.get() * 1.0e9);
		if (!fresh && !editing) {
			return null;
		}

		return Component.literal(String.format(Locale.ROOT, Lang.t("Zasięg: %.2f"), fresh ? shownDistance : EXAMPLE));
	}
}
