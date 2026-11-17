package pl.kerf.project.hud;

import java.util.ArrayDeque;
import java.util.Deque;

/** Liczy kliknięcia lewym i prawym przyciskiem z ostatniej sekundy (tylko w grze, nie w menu). */
public final class CpsTracker {
	private static final Deque<Long> LEFT = new ArrayDeque<>();
	private static final Deque<Long> RIGHT = new ArrayDeque<>();

	private CpsTracker() {
	}

	public static void click(int button) {
		long now = System.currentTimeMillis();
		if (button == 0) {
			LEFT.addLast(now);
		} else if (button == 1) {
			RIGHT.addLast(now);
		}
	}

	private static int count(Deque<Long> clicks) {
		long cutoff = System.currentTimeMillis() - 1000L;
		while (!clicks.isEmpty() && clicks.peekFirst() < cutoff) {
			clicks.pollFirst();
		}

		return clicks.size();
	}

	public static int left() {
		return count(LEFT);
	}

	public static int right() {
		return count(RIGHT);
	}
}
