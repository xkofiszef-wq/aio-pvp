package pl.kerf.project.hud;

import pl.kerf.project.Lang;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import pl.kerf.project.config.Setting;

/**
 * Ostrzeżenie, gdy serwer przestał przysyłać pakiety (lag serwera albo łącza). Zdrowy serwer wysyła coś co najmniej
 * raz na sekundę (aktualizacja czasu świata), więc dłuższa cisza oznacza, że ciosy i ruch chwilowo "nie dochodzą".
 */
public final class LagDetectorHud extends InfoHud {
	private static final int WARNING = 0xE6B34E;
	private static final int DANGER = 0xE5675F;
	/** Czas ostatniego pakietu z serwera; zapisuje wątek sieci, czyta wątek gry. */
	private static volatile long lastPacket = System.nanoTime();

	public final Setting.Num threshold = this.add(new Setting.Num("threshold", "Alarm po ciszy dłuższej niż", 2.0, 1.5, 10.0, 0.5, " s"));

	public LagDetectorHud() {
		super("lag_detector", "Lag Detector", "Ostrzega, gdy serwer przestał odpowiadać: wiesz, że to lag, a nie twoje pudła.",
			0.5F, 0.0F, 0.5F, 0.0F, 0.0F, 62.0F, (client, editing) -> null);
	}

	/** Wołane z wątku sieci przy każdym pakiecie od serwera. */
	public static void packetReceived() {
		lastPacket = System.nanoTime();
	}

	public static double gapSeconds() {
		return (System.nanoTime() - lastPacket) / 1.0e9;
	}

	/** Pauza w singleplayerze zatrzymuje serwer celowo, a poza światem nie ma kogo słuchać: to nie lag. */
	public static void tick(MinecraftClient client) {
		if (client.world == null || client.isPaused()) {
			lastPacket = System.nanoTime();
		}
	}

	/** Tekst ostrzeżenia dla danej ciszy; null = wszystko w porządku. Żółty, a od dwukrotności progu czerwony. */
	public Text message(double gap) {
		double limit = this.threshold.get();
		if (gap < limit) {
			return null;
		}

		return Text.literal(String.format(Locale.ROOT, Lang.t("Serwer nie odpowiada: %.1f s"), gap)).withColor(gap >= 2.0 * limit ? DANGER : WARNING);
	}

	@Override
	protected Text text(MinecraftClient client, boolean editing) {
		Text message = this.message(gapSeconds());
		return message == null && editing ? this.message(this.threshold.get()) : message;
	}
}
