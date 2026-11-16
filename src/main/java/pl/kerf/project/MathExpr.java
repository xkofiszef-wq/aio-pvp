package pl.kerf.project;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Kalkulator do komendy /calc: + - * / % ^, nawiasy, minus przed liczbą, sqrt(x), pi. Przecinek działa jak kropka
 * (2,5 = 2.5). Zwykłe parsowanie od lewej z kolejnością działań: ^ przed * / %, te przed + -.
 */
public final class MathExpr {
	private static final DecimalFormat FORMAT = new DecimalFormat("0.##########", DecimalFormatSymbols.getInstance(Locale.ROOT));
	private final String text;
	private int pos;

	private MathExpr(String text) {
		this.text = text.replace(',', '.').replace(" ", "").toLowerCase(Locale.ROOT);
	}

	/** Wynik wyrażenia; IllegalArgumentException z opisem błędu po polsku. */
	public static double eval(String expression) {
		MathExpr parser = new MathExpr(expression);
		if (parser.text.isEmpty()) {
			throw new IllegalArgumentException(Lang.t("Puste wyrażenie"));
		}

		double value = parser.sum();
		if (parser.pos < parser.text.length()) {
			throw new IllegalArgumentException(Lang.t("Nie rozumiem znaku '") + parser.text.charAt(parser.pos) + "'");
		}

		if (Double.isNaN(value) || Double.isInfinite(value)) {
			throw new IllegalArgumentException(Lang.t("Wynik nie jest liczbą (np. dzielenie przez 0)"));
		}

		return value;
	}

	public static String format(double value) {
		return FORMAT.format(value);
	}

	private boolean eat(char c) {
		if (this.pos < this.text.length() && this.text.charAt(this.pos) == c) {
			this.pos++;
			return true;
		}

		return false;
	}

	private double sum() {
		double value = this.product();
		while (true) {
			if (this.eat('+')) {
				value += this.product();
			} else if (this.eat('-')) {
				value -= this.product();
			} else {
				return value;
			}
		}
	}

	private double product() {
		double value = this.power();
		while (true) {
			if (this.eat('*') || this.eat('x')) {
				value *= this.power();
			} else if (this.eat('/') || this.eat(':')) {
				value /= this.power();
			} else if (this.eat('%')) {
				value %= this.power();
			} else {
				return value;
			}
		}
	}

	/** Potęgowanie od prawej: 2^3^2 = 2^9. */
	private double power() {
		double base = this.unary();
		return this.eat('^') ? Math.pow(base, this.power()) : base;
	}

	private double unary() {
		if (this.eat('-')) {
			return -this.unary();
		}

		if (this.eat('+')) {
			return this.unary();
		}

		return this.atom();
	}

	private double atom() {
		if (this.eat('(')) {
			double value = this.sum();
			if (!this.eat(')')) {
				throw new IllegalArgumentException(Lang.t("Brakuje nawiasu ')'"));
			}

			return value;
		}

		if (this.text.startsWith("sqrt", this.pos)) {
			this.pos += 4;
			return Math.sqrt(this.atom());
		}

		if (this.text.startsWith("pi", this.pos)) {
			this.pos += 2;
			return Math.PI;
		}

		int start = this.pos;
		while (this.pos < this.text.length() && (Character.isDigit(this.text.charAt(this.pos)) || this.text.charAt(this.pos) == '.')) {
			this.pos++;
		}

		if (start == this.pos) {
			throw new IllegalArgumentException(this.pos < this.text.length()
				? Lang.t("Nie rozumiem znaku '") + this.text.charAt(this.pos) + "'" : Lang.t("Wyrażenie urywa się na końcu"));
		}

		try {
			return Double.parseDouble(this.text.substring(start, this.pos));
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(Lang.t("Zła liczba: ") + this.text.substring(start, this.pos));
		}
	}
}
