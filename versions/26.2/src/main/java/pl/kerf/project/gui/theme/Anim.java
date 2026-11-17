package pl.kerf.project.gui.theme;

/**
 * Płynna wartość: co klatkę zbliża się do celu wykładniczo (szybko na początku, miękko na końcu).
 * Niezależna od liczby FPS, bo krok liczy się z czasu, jaki upłynął od poprzedniej klatki.
 */
public final class Anim {
	private static long lastFrame = System.nanoTime();
	private static float frameSeconds;

	private float value;
	private final float speed;

	public Anim(float initial, float speed) {
		this.value = initial;
		this.speed = speed;
	}

	public Anim(float initial) {
		this(initial, 14.0F);
	}

	/** Wywoływane raz na klatkę przez ekran, przed rysowaniem. */
	public static void frame() {
		long now = System.nanoTime();
		frameSeconds = Math.min(0.1F, (now - lastFrame) / 1.0E9F);
		lastFrame = now;
	}

	public float to(float target) {
		float k = 1.0F - (float) Math.exp(-this.speed * frameSeconds);
		this.value += (target - this.value) * k;
		if (Math.abs(target - this.value) < 0.001F) {
			this.value = target;
		}

		return this.value;
	}

	public float to(boolean on) {
		return this.to(on ? 1.0F : 0.0F);
	}

	public float get() {
		return this.value;
	}

	public void set(float value) {
		this.value = value;
	}

	/** Wygładzenie krzywej 0..1 (wolny start i koniec). */
	public static float ease(float t) {
		t = Math.max(0.0F, Math.min(1.0F, t));
		return t * t * (3.0F - 2.0F * t);
	}
}
