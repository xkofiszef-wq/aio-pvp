package pl.kerf.project.hud;

import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
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
	public final Setting.Mode layout = this.add(new Setting.Mode("layout", "Układ", 0, "Poziomo", "Pionowo"));
	/** Pionowo: trzy wiersze X / Y / Z, liczone raz na tick jak tekst w InfoHud. */
	private final Text[] lines = new Text[3];
	private int linesW;
	private int linesAge = -1;

	public CoordinatesHud() {
		super("coordinates", "Coordinates", "Współrzędne X / Y / Z z kolorowymi osiami.", 0.0F, 0.0F, 0.0F, 0.0F, 4.0F, 4.0F, (client, editing) -> null);
	}

	private boolean vertical() {
		return this.layout.is(1);
	}

	@Override
	protected Text text(MinecraftClient client, boolean editing) {
		Text[] axes = this.axes(client);
		return Text.empty().append(axes[0]).append("  ").append(axes[1]).append("  ").append(axes[2]);
	}

	private Text[] axes(MinecraftClient client) {
		String format = FORMATS[Math.max(0, Math.min(FORMATS.length - 1, this.decimals.i()))];
		return new Text[] {
			this.axis("X", client.player.getX(), format, this.xColor.rgb()),
			this.axis("Y", client.player.getY(), format, this.yColor.rgb()),
			this.axis("Z", client.player.getZ(), format, this.zColor.rgb())
		};
	}

	private Text axis(String name, double value, String format, int color) {
		MutableText text = Text.literal(name).withColor(color);
		return text.append(Text.literal(": " + String.format(Locale.ROOT, format, value)).withColor(this.valueColor.rgb()));
	}

	@Override
	public int width(MinecraftClient client, boolean editing) {
		if (!this.vertical()) {
			return super.width(client, editing);
		}

		if (client.player.age != this.linesAge) {
			this.linesAge = client.player.age;
			Text[] axes = this.axes(client);
			this.linesW = 0;
			for (int i = 0; i < 3; i++) {
				this.lines[i] = axes[i];
				this.linesW = Math.max(this.linesW, client.textRenderer.getWidth(axes[i]));
			}
		}

		return this.linesW + 2 * PAD_X;
	}

	@Override
	public int height(MinecraftClient client, boolean editing) {
		return this.vertical() ? 3 * 10 - 2 + 2 * PAD_Y : super.height(client, editing);
	}

	@Override
	protected void draw(DrawContext context, MinecraftClient client, RenderTickCounter tickCounter, boolean editing) {
		if (!this.vertical()) {
			super.draw(context, client, tickCounter, editing);
			return;
		}

		if (this.background.on()) {
			context.fill(0, 0, this.linesW + 2 * PAD_X, 3 * 10 - 2 + 2 * PAD_Y, this.backgroundColor.argb());
		}

		for (int i = 0; i < 3; i++) {
			context.drawText(client.textRenderer, this.lines[i], PAD_X, PAD_Y + i * 10, 0xFFFFFFFF, this.shadow.on());
		}
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
