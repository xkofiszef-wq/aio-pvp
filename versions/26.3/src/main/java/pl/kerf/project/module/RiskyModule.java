package pl.kerf.project.module;

import pl.kerf.project.Lang;

import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import pl.kerf.project.gui.Notifications;
import pl.kerf.project.gui.theme.Theme;

/**
 * Moduł z "szarej strefy", zabroniony na części serwerów (Instant Hit, Freelook). Włącza się tylko przez okno
 * z ostrzeżeniem o banie; po włączeniu nie da się go wyłączyć (ani przełącznikiem, ani skrótem, ani "Resetuj")
 * aż do ponownego uruchomienia gry. Stan włączenia nie jest zapisywany, więc każda sesja gry zaczyna się bez niego.
 */
public class RiskyModule extends Module {
	/** Co robi moduł (pierwsze zdanie okna ostrzeżenia); tekst okna składany przy otwarciu, w aktualnym języku. */
	private final String what;
	private static final String WARNING = " Na części serwerów takie mody są zabronione i możesz za niego dostać bana.\n\n"
		+ "Po włączeniu NIE da się go wyłączyć aż do ponownego uruchomienia gry. Nie wchodź z nim na serwery, które tego zabraniają.";
	/** Włączony przez okno ostrzeżenia: od tej chwili do końca sesji gry nie da się go wyłączyć. */
	private boolean locked;

	protected RiskyModule(String id, String name, String description, Category category, String what) {
		super(id, name, description, category, false);
		this.what = what;
	}

	public boolean locked() {
		return this.locked;
	}

	/** Przełącznik w menu i skrót klawiszowy: włączenie tylko po potwierdzeniu ostrzeżenia, wyłączenie zablokowane. */
	@Override
	public void toggle() {
		// Gracz wziął ryzyko w ustawieniach ("Zapamiętuj ryzykowne moduły"): zwykły przełącznik, stan zostaje na kolejne sesje.
		if (Modules.GUI_THEME.rememberRisky.on()) {
			super.toggle();
			return;
		}

		if (this.on()) {
			Notifications.push(this.name, "Zablokowany do restartu gry", Theme.DANGER);
			return;
		}

		Minecraft client = Minecraft.getInstance();
		Screen parent = client.gui.screen();
		client.gui.setScreen(new ConfirmScreen(confirmed -> {
			if (confirmed) {
				this.enableForSession();
			}

			client.gui.setScreen(parent);
		}, Component.literal(this.name + Lang.t(" może dać bana")).withStyle(ChatFormatting.RED), Component.literal(Lang.t(this.what) + Lang.t(WARNING)), Component.literal(Lang.t("Włącz do końca sesji")).withStyle(ChatFormatting.RED), Component.literal(Lang.t("Anuluj"))));
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

	/** Stan włączenia przechodzi do następnej sesji gry tylko, gdy gracz wziął ryzyko w ustawieniach; inaczej zawsze zapisuje się wyłączony. */
	@Override
	public JsonObject write() {
		JsonObject json = super.write();
		json.addProperty("enabled", Modules.GUI_THEME.rememberRisky.on() && this.on());
		return json;
	}

	/** Stan zapisany w pliku (jeszcze nie zastosowany: ustawienie "zapamiętuj" bywa wczytane później niż moduł). */
	private boolean stored;

	@Override
	public void read(JsonObject json) {
		super.read(json);
		this.stored = json.has("enabled") && json.get("enabled").getAsBoolean();
		if (!this.locked) {
			super.setEnabled(false);
		}
	}

	/** Po wczytaniu configu: przywraca włączenie, jeśli gracz wziął ryzyko i moduł był włączony przy wyjściu z gry. */
	public void applyRemembered() {
		if (Modules.GUI_THEME.rememberRisky.on() && this.stored && !this.locked) {
			super.setEnabled(true);
		}
	}
}
