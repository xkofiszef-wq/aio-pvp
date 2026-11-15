package pl.kerf.project.module;

import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import pl.kerf.project.gui.Notifications;
import pl.kerf.project.gui.theme.Theme;

/**
 * Instant Hit: czerwony błysk trafienia widać od razu po ciosie, zanim serwer go potwierdzi (jak Better HitReg).
 * Tylko wygląd: obrażenia liczy serwer jak zawsze, a Fight Report patrzy na potwierdzenie z serwera, nie na błysk.
 *
 * <p>Na części serwerów takie mody są zabronione, więc: włącza się tylko przez okno z ostrzeżeniem, po włączeniu
 * nie da się go wyłączyć (ani skrótem, ani "Resetuj") aż do ponownego uruchomienia gry, a stan nie jest zapisywany,
 * więc każda nowa sesja gry zaczyna się z wyłączonym.
 */
public final class InstantHit extends Module {
	private static final Text TITLE = Text.literal("Instant Hit może dać bana").formatted(Formatting.RED);
	private static final Text MESSAGE = Text.literal(
		"Ten moduł pokazuje trafienia, zanim potwierdzi je serwer. Na części serwerów takie mody są zabronione i możesz "
			+ "za niego dostać bana.\n\nPo włączeniu NIE da się go wyłączyć aż do ponownego uruchomienia gry. Nie wchodź "
			+ "z nim na serwery, które tego zabraniają."
	);
	/** Włączony przez okno ostrzeżenia: od tej chwili do końca sesji gry nie da się go wyłączyć. */
	private boolean locked;

	public InstantHit() {
		super("instant_hit", "Instant Hit", "Trafienie widać od razu, bez czekania na serwer. Na części serwerów zabronione: blokuje się do restartu gry.",
			Category.COMBAT, false);
	}

	public boolean locked() {
		return this.locked;
	}

	/** Przełącznik w menu i skrót klawiszowy: włączenie tylko po potwierdzeniu ostrzeżenia, wyłączenie zablokowane. */
	@Override
	public void toggle() {
		if (this.on()) {
			Notifications.push(this.name, "Zablokowany do restartu gry", Theme.DANGER);
			return;
		}

		MinecraftClient client = MinecraftClient.getInstance();
		Screen parent = client.currentScreen;
		client.setScreen(new ConfirmScreen(confirmed -> {
			if (confirmed) {
				this.enableForSession();
			}

			client.setScreen(parent);
		}, TITLE, MESSAGE, Text.literal("Włącz do końca sesji").formatted(Formatting.RED), Text.literal("Anuluj")));
	}

	private void enableForSession() {
		this.locked = true;
		super.setEnabled(true);
		Notifications.push(this.name, "Włączony do restartu gry", Theme.DANGER);
	}

	@Override
	public void setEnabled(boolean enabled) {
		if (!enabled && this.locked) {
			return;
		}

		super.setEnabled(enabled);
	}

	@Override
	public void reset() {
		super.reset();
		if (this.locked) {
			super.setEnabled(true);
		}
	}

	/** Stan włączenia nie przechodzi do następnej sesji gry (ustawienia i skrót tak). */
	@Override
	public JsonObject write() {
		JsonObject json = super.write();
		json.addProperty("enabled", false);
		return json;
	}

	@Override
	public void read(JsonObject json) {
		super.read(json);
		if (!this.locked) {
			super.setEnabled(false);
		}
	}

	/**
	 * Nasz cios: od razu czerwony błysk na celu, jeśli cel jest w zasięgu, żyje i nie ma właśnie nietykalności po
	 * poprzednim ciosie (wtedy serwer i tak by go nie zranił). Gdy serwer potwierdzi, gra pokaże błysk jeszcze raz.
	 */
	public void onAttack(MinecraftClient client, Entity entity) {
		if (!this.on() || client.player == null || !(entity instanceof LivingEntity living)) {
			return;
		}

		if (living.isAlive() && living.hurtTime == 0 && client.player.canInteractWithEntity(living, 0.0)) {
			living.hurtTime = living.maxHurtTime > 0 ? living.maxHurtTime : 10;
		}
	}
}
