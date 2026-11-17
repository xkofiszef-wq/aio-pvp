package pl.kerf.project.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** Pojedyncze ustawienie modułu. Każdy typ umie się zapisać i wczytać z JSON-a. */
public abstract class Setting<T> {
	public final String key;
	public final String label;
	protected final T def;
	protected T value;
	/** Ukryte ustawienia są zapisywane, ale nie pokazują się na liście (mają własny edytor). */
	public boolean hidden;

	@SuppressWarnings("unchecked")
	public <S extends Setting<T>> S hide() {
		this.hidden = true;
		return (S) this;
	}

	protected Setting(String key, String label, T def) {
		this.key = key;
		this.label = label;
		this.def = def;
		this.value = def;
	}

	public T get() {
		return this.value;
	}

	public void set(T value) {
		this.value = value;
	}

	public void reset() {
		this.value = this.def;
	}

	public abstract JsonElement write();

	public abstract void read(JsonElement json);

	public static final class Bool extends Setting<Boolean> {
		public Bool(String key, String label, boolean def) {
			super(key, label, def);
		}

		public boolean on() {
			return this.value;
		}

		@Override
		public JsonElement write() {
			return new JsonPrimitive(this.value);
		}

		@Override
		public void read(JsonElement json) {
			this.value = json.getAsBoolean();
		}
	}

	public static final class Num extends Setting<Double> {
		public final double min;
		public final double max;
		public final double step;
		public final String suffix;
		/** Oś przesunięcia: 0 = brak, 1 = X (czerwony), 2 = Y (zielony), 3 = Z (niebieski). */
		public int axis;

		public Num axis(int axis) {
			this.axis = axis;
			return this;
		}

		public Num(String key, String label, double def, double min, double max, double step, String suffix) {
			super(key, label, def);
			this.min = min;
			this.max = max;
			this.step = step;
			this.suffix = suffix;
		}

		public Num(String key, String label, double def, double min, double max, double step) {
			this(key, label, def, min, max, step, "");
		}

		public float f() {
			return this.value.floatValue();
		}

		public int i() {
			return (int) Math.round(this.value);
		}

		@Override
		public void set(Double value) {
			double snapped = Math.round(value / this.step) * this.step;
			this.value = Math.max(this.min, Math.min(this.max, snapped));
		}

		public String format() {
			String text;
			if (this.step >= 1.0) {
				text = String.valueOf(this.i());
			} else if (this.step >= 0.1) {
				text = String.format(java.util.Locale.ROOT, "%.1f", this.value);
			} else {
				text = String.format(java.util.Locale.ROOT, "%.2f", this.value);
			}

			return text + this.suffix;
		}

		@Override
		public JsonElement write() {
			return new JsonPrimitive(this.value);
		}

		@Override
		public void read(JsonElement json) {
			this.set(json.getAsDouble());
		}
	}

	/** Kolor ARGB. */
	public static final class Color extends Setting<Integer> {
		public final boolean hasAlpha;

		public Color(String key, String label, int def, boolean hasAlpha) {
			super(key, label, def);
			this.hasAlpha = hasAlpha;
		}

		public int argb() {
			return this.hasAlpha ? this.value : this.value | 0xFF000000;
		}

		public int rgb() {
			return this.value & 0xFFFFFF;
		}

		@Override
		public JsonElement write() {
			return new JsonPrimitive(String.format("#%08X", this.value));
		}

		@Override
		public void read(JsonElement json) {
			this.value = (int) Long.parseLong(json.getAsString().replace("#", ""), 16);
		}
	}

	public static final class Mode extends Setting<Integer> {
		public final String[] options;

		public Mode(String key, String label, int def, String... options) {
			super(key, label, def);
			this.options = options;
		}

		public boolean is(int index) {
			return this.value == index;
		}

		public String current() {
			return this.options[this.value];
		}

		public void cycle() {
			this.value = (this.value + 1) % this.options.length;
		}

		@Override
		public JsonElement write() {
			return new JsonPrimitive(this.value);
		}

		@Override
		public void read(JsonElement json) {
			int index = json.getAsInt();
			this.value = index >= 0 && index < this.options.length ? index : this.def;
		}
	}

	public static final class Str extends Setting<String> {
		public Str(String key, String label, String def) {
			super(key, label, def);
		}

		@Override
		public JsonElement write() {
			return new JsonPrimitive(this.value);
		}

		@Override
		public void read(JsonElement json) {
			this.value = json.getAsString();
		}
	}
}
