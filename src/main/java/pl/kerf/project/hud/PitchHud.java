package pl.kerf.project.hud;

import java.util.Locale;
import java.util.function.Predicate;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import pl.kerf.project.config.Setting;
import pl.kerf.project.module.HudModule;

/** Kąt patrzenia (pitch) pokazywany tylko wtedy, gdy spełniony jest warunek, np. lot elytrą. */
public final class PitchHud extends HudModule {
	public final Setting.Color color = this.add(new Setting.Color("color", "Kolor tekstu", 0xFFFFFFFF, false));
	public final Setting.Bool label = this.add(new Setting.Bool("label", "Etykieta \"Pitch\"", true));
	private final Predicate<LocalPlayer> active;
	/** Tekst z bieżącej klatki (liczony w width(), rysowany w draw()). */
	private String current = "";

	public PitchHud(String id, String name, String description, float oy, Predicate<LocalPlayer> active) {
		super(id, name, description, false, 0.5F, 0.5F, 0.5F, 0.5F, 0.0F, oy);
		this.active = active;
	}

	private String text(Minecraft client) {
		// Vanilla liczy pitch dodatnio w dół; dla gracza czytelniej "w górę = plus".
		// Zaokrąglenie i "+ 0.0F" zamieniają ujemne zero na zwykłe, żeby nie wyświetlać "-0.0°".
		String angle = String.format(Locale.ROOT, "%.1f°", Math.round(-client.player.getXRot() * 10.0F) / 10.0F + 0.0F);
		return this.label.on() ? "Pitch: " + angle : angle;
	}

	private boolean visible(Minecraft client, boolean editing) {
		return editing || this.active.test(client.player);
	}

	@Override
	public int width(Minecraft client, boolean editing) {
		if (!this.visible(client, editing)) {
			return 0;
		}

		this.current = this.text(client);
		return client.font.width(this.current);
	}

	@Override
	public int height(Minecraft client, boolean editing) {
		return this.visible(client, editing) ? 9 : 0;
	}

	@Override
	protected void draw(GuiGraphics context, Minecraft client, DeltaTracker tickCounter, boolean editing) {
		context.drawString(client.font, this.current, 0, 0, this.color.argb(), true);
	}
}
