package pl.kerf.project.hud;

import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import pl.kerf.project.config.Setting;

/** Współrzędne gracza: litery osi w kolorach (te same kolory osi używa całe GUI moda). */
public final class CoordinatesHud extends InfoHud {
	private static final String[] FORMATS = {"%.0f", "%.1f", "%.2f", "%.3f"};
	public final Setting.Num decimals = this.add(new Setting.Num("decimals", "Miejsca po przecinku", 2, 0, 3, 1));
	public final Setting.Color xColor = this.add(new Setting.Color("x_color", "Kolor osi X", 0xFF55FF55, false));
	public final Setting.Color yColor = this.add(new Setting.Color("y_color", "Kolor osi Y", 0xFF5555FF, false));
	public final Setting.Color zColor = this.add(new Setting.Color("z_color", "Kolor osi Z", 0xFFFF5555, false));
	public final Setting.Color valueColor = this.add(new Setting.Color("value_color", "Kolor liczb", 0xFFFFFFFF, false));

	public CoordinatesHud() {
		super("coordinates", "Coordinates", "Współrzędne X / Y / Z z kolorowymi osiami.", 0.0F, 0.0F, 0.0F, 0.0F, 4.0F, 4.0F, (client, editing) -> null);
	}

	@Override
	protected Text text(MinecraftClient client, boolean editing) {
		double x = client.player.getX();
		double y = client.player.getY();
		double z = client.player.getZ();
		String format = FORMATS[Math.max(0, Math.min(FORMATS.length - 1, this.decimals.i()))];
		MutableText text = Text.empty();
		this.axis(text, "X", x, format, this.xColor.rgb());
		text.append("  ");
		this.axis(text, "Y", y, format, this.yColor.rgb());
		text.append("  ");
		this.axis(text, "Z", z, format, this.zColor.rgb());
		return text;
	}

	private void axis(MutableText text, String name, double value, String format, int color) {
		text.append(Text.literal(name).withColor(color));
		text.append(Text.literal(": " + String.format(Locale.ROOT, format, value)).withColor(this.valueColor.rgb()));
	}

	/** Kolor osi (1 = X, 2 = Y, 3 = Z) w ARGB, np. dla suwaków X/Y/Z w ustawieniach. */
	public int axisColor(int axis) {
		return switch (axis) {
			case 1 -> this.xColor.argb();
			case 2 -> this.yColor.argb();
			case 3 -> this.zColor.argb();
			default -> 0;
		};
	}
}
