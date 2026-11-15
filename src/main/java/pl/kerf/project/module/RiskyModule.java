package pl.kerf.project.module;

import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import pl.kerf.project.gui.Notifications;
import pl.kerf.project.gui.theme.Theme;

/**
 * Moduł z "szarej strefy", zabroniony na części serwerów (Instant Hit, Freelook). Włącza się tylko przez okno
 * z ostrzeżeniem o banie; po włączeniu nie da się go wyłączyć (ani przełącznikiem, ani skrótem, ani "Resetuj")
 * aż do ponownego uruchomienia gry. Stan włączenia nie jest zapisywany, więc każda sesja gry zaczyna się bez niego.
 */
public class RiskyModule extends Module {
	private final Text warningTitle;
	private final Text warningMessage;
	/** Włączony przez okno ostrzeżenia: od tej chwili do końca sesji gry nie da się go wyłączyć. */
	private boolean locked;

	protected RiskyModule(String id, String name, String description, Category category, String what) {
		super(id, name, description, category, false);
		this.warningTitle = Text.literal(name + " może dać bana").formatted(Formatting.RED);
		this.warningMessage = Text.literal(
			what + " Na części serwerów takie mody są zabronione i możesz za niego dostać bana.\n\n"
				+ "Po włączeniu NIE da się go wyłączyć aż do ponownego uruchomienia gry. Nie wchodź z nim na serwery, "
				+ "które tego zabraniają."
		);
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
		}, this.warningTitle, this.warningMessage, Text.literal("Włącz do końca sesji").formatted(Formatting.RED), Text.literal("Anuluj")));
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
}
