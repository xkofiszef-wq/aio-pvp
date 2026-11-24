package pl.kerf.project.config;

import java.util.Arrays;
import java.util.Locale;

/**
 * Rysunek pikselowy SIZE x SIZE zapisany w ustawieniu tekstowym jako "x,y,AARRGGBB;..." (tylko niepuste piksele).
 * Siatka jest odczytywana z tekstu tylko wtedy, gdy tekst się zmienił.
 */
public final class PixelGrid {
	public static final int SIZE = 15;
	private final Setting.Str setting;
	private final int[] grid = new int[SIZE * SIZE];
	private String parsedFrom;

	public PixelGrid(Setting.Str setting) {
		this.setting = setting;
	}

	/** Siatka w ARGB (0 = pusty piksel). Nie modyfikuj zwróconej tablicy. */
	public int[] get() {
		String source = this.setting.get();
		if (!source.equals(this.parsedFrom)) {
			this.parsedFrom = source;
			Arrays.fill(this.grid, 0);
			for (String part : source.split(";")) {
				String[] fields = part.trim().split(",");
				if (fields.length != 3) {
					continue;
				}

				try {
					int x = Integer.parseInt(fields[0]);
					int y = Integer.parseInt(fields[1]);
					if (x >= 0 && y >= 0 && x < SIZE && y < SIZE) {
						this.grid[y * SIZE + x] = (int) Long.parseLong(fields[2], 16);
					}
				} catch (NumberFormatException ignored) {
					// Uszkodzony wpis pomijamy, reszta rysunku zostaje.
				}
			}
		}

		return this.grid;
	}

	public void set(int[] grid) {
		this.setting.set(encode(grid));
	}

	/** Odczyt rysunku z tekstu "x,y,AARRGGBB;..." (używany też przez ikony waypointów, które nie mają ustawienia). */
	public static int[] decode(String source) {
		int[] grid = new int[SIZE * SIZE];
		for (String part : source.split(";")) {
			String[] fields = part.trim().split(",");
			if (fields.length != 3) {
				continue;
			}

			try {
				int x = Integer.parseInt(fields[0]);
				int y = Integer.parseInt(fields[1]);
				if (x >= 0 && y >= 0 && x < SIZE && y < SIZE) {
					grid[y * SIZE + x] = (int) Long.parseLong(fields[2], 16);
				}
			} catch (NumberFormatException ignored) {
				// Uszkodzony wpis pomijamy, reszta rysunku zostaje.
			}
		}

		return grid;
	}

	public static String encode(int[] grid) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < grid.length; i++) {
			if (grid[i] >>> 24 != 0) {
				if (!out.isEmpty()) {
					out.append(';');
				}

				out.append(i % SIZE).append(',').append(i / SIZE).append(',').append(String.format(Locale.ROOT, "%08X", grid[i]));
			}
		}

		return out.toString();
	}

	/** Vanillowy plus (ten sam kształt co hud/crosshair.png) w podanym kolorze. */
	public static String plus(int argb) {
		int[] grid = new int[SIZE * SIZE];
		for (int i = 3; i <= 11; i++) {
			grid[i * SIZE + 7] = argb;
			grid[7 * SIZE + i] = argb;
		}

		return encode(grid);
	}
}
