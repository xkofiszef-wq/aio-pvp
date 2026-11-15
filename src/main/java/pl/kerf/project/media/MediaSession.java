package pl.kerf.project.media;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import pl.kerf.project.AioPvp;

/**
 * Aktualnie odtwarzany utwór z kontrolek multimediów Windows (Spotify, przeglądarka...).
 *
 * <p>Java nie ma dostępu do tych kontrolek, więc w tle działa mały skrypt PowerShella (assets/aio_pvp/media/media.ps1),
 * który co pół sekundy wypisuje stan jako JSON i wykonuje polecenia zapisane do pliku. Skrypt kończy się sam,
 * gdy gra się zamknie, a mod zatrzymuje go od razu po wyłączeniu funkcji.
 */
public final class MediaSession {
	public static final Identifier ART = Identifier.of(AioPvp.MOD_ID, "media_art");
	private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve(AioPvp.MOD_ID).resolve("media");
	private static Process process;
	private static volatile State state = State.EMPTY;
	/** Kiedy przyszedł ostatni stan (do płynnego przesuwania paska postępu między aktualizacjami). */
	private static volatile long stateTime;
	private static int loadedArt = -1;
	private static boolean hasArt;
	private static boolean failed;
	private static long lastStart;
	private static int quickExits;

	public record State(boolean active, String title, String artist, String app, double position, double duration, boolean playing, int art) {
		static final State EMPTY = new State(false, "", "", "", 0.0, 0.0, false, 0);
	}

	private MediaSession() {
	}

	public static boolean supported() {
		return Util.getOperatingSystem() == Util.OperatingSystem.WINDOWS;
	}

	public static State state() {
		return state;
	}

	/** Pozycja w sekundach, przesunięta o czas od ostatniej aktualizacji, gdy utwór gra. */
	public static double position() {
		State current = state;
		double position = current.position;
		if (current.playing) {
			position += (System.currentTimeMillis() - stateTime) / 1000.0;
		}

		return current.duration > 0.0 ? Math.min(position, current.duration) : position;
	}

	public static boolean hasArt() {
		return hasArt;
	}

	/** Uruchamia lub zatrzymuje skrypt zgodnie z tym, czy funkcja jest włączona. Wywoływane co tick. */
	public static void tick(MinecraftClient client, boolean enabled) {
		if (!enabled || !supported()) {
			stop();
			return;
		}

		if ((process == null || !process.isAlive()) && !failed) {
			long now = System.currentTimeMillis();
			// Skrypt, który pada od razu (antywirus, brak kontrolek multimediów), nie może startować co tick.
			if (now - lastStart < 10_000L) {
				return;
			}

			if (process != null && now - lastStart < 15_000L) {
				if (++quickExits >= 3) {
					failed = true;
					AioPvp.LOGGER.warn("Odczyt multimediów pada zaraz po starcie, wyłączam go do restartu gry");
					return;
				}
			} else {
				quickExits = 0;
			}

			lastStart = now;
			start();
		}

		loadArt(client);
	}

	private static synchronized void start() {
		try {
			Files.createDirectories(DIR);
			Path script = DIR.resolve("media.ps1");
			try (InputStream in = MediaSession.class.getResourceAsStream("/assets/aio_pvp/media/media.ps1")) {
				if (in == null) {
					throw new IOException("brak skryptu media.ps1 w jarze");
				}

				Files.copy(in, script, StandardCopyOption.REPLACE_EXISTING);
			}

			ProcessBuilder builder = new ProcessBuilder(
				"powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden",
				"-File", script.toString(),
				"-CommandFile", DIR.resolve("command.txt").toString(),
				"-ArtFile", DIR.resolve("art.png").toString(),
				"-ParentPid", String.valueOf(ProcessHandle.current().pid())
			);
			builder.redirectErrorStream(true);
			process = builder.start();
			Process started = process;
			Thread reader = new Thread(() -> read(started), "AIO PvP media");
			reader.setDaemon(true);
			reader.start();
		} catch (IOException e) {
			// Bez PowerShella (albo zablokowany) odtwarzacz po prostu nic nie pokaże; nie próbujemy w kółko.
			failed = true;
			AioPvp.LOGGER.warn("Nie udało się uruchomić odczytu multimediów: {}", e.getMessage());
		}
	}

	private static void read(Process source) {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(source.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (!line.startsWith("{")) {
					continue;
				}

				try {
					JsonObject json = JsonParser.parseString(line).getAsJsonObject();
					boolean active = json.has("active") && json.get("active").getAsBoolean();
					state = !active ? State.EMPTY : new State(
						true,
						string(json, "title"),
						string(json, "artist"),
						string(json, "app"),
						number(json, "position"),
						number(json, "duration"),
						json.has("playing") && json.get("playing").getAsBoolean(),
						json.has("art") ? json.get("art").getAsInt() : 0
					);
					stateTime = System.currentTimeMillis();
				} catch (RuntimeException ignored) {
					// Pojedyncza uszkodzona linia nie zatrzymuje odczytu.
				}
			}
		} catch (IOException ignored) {
			// Proces zakończony.
		}
	}

	private static String string(JsonObject json, String key) {
		return json.has(key) ? json.get(key).getAsString() : "";
	}

	private static double number(JsonObject json, String key) {
		return json.has(key) ? json.get(key).getAsDouble() : 0.0;
	}

	public static synchronized void stop() {
		if (process != null) {
			process.destroy();
			process = null;
		}

		// Ręczne wyłączenie to nie pad: po ponownym włączeniu skrypt startuje od razu.
		lastStart = 0L;
		quickExits = 0;
		state = State.EMPTY;
	}

	/** Polecenie dla odtwarzacza: "toggle", "next" albo "prev". */
	public static void command(String command) {
		if (process == null) {
			return;
		}

		try {
			Path file = DIR.resolve("command.txt");
			Path tmp = DIR.resolve("command.tmp");
			Files.writeString(tmp, command, StandardCharsets.UTF_8);
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			// Od razu przełączamy ikonę, żeby klik był odczuwalny przed następną aktualizacją.
			if (command.equals("toggle")) {
				State current = state;
				state = new State(current.active, current.title, current.artist, current.app, position(), current.duration, !current.playing, current.art);
				stateTime = System.currentTimeMillis();
			}
		} catch (IOException e) {
			AioPvp.LOGGER.warn("Nie udało się wysłać polecenia do odtwarzacza: {}", e.getMessage());
		}
	}

	/** Wczytuje okładkę jako teksturę, gdy skrypt zapisał nową. */
	private static void loadArt(MinecraftClient client) {
		State current = state;
		if (!current.active || current.art == loadedArt) {
			return;
		}

		loadedArt = current.art;
		Path file = DIR.resolve("art.png");
		if (!Files.exists(file)) {
			hasArt = false;
			return;
		}

		try (InputStream in = Files.newInputStream(file)) {
			NativeImage image = NativeImage.read(in);
			client.getTextureManager().registerTexture(ART, new NativeImageBackedTexture(() -> "AIO PvP album art", image));
			hasArt = true;
		} catch (IOException e) {
			hasArt = false;
		}
	}
}
