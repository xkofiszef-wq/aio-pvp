package pl.kerf.project.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import pl.kerf.project.config.Setting;
import pl.kerf.project.module.HudModule;

/** Pasek kompasu: kierunki przesuwają się pod czerwoną kreską środka w miarę obracania się. */
public final class CompassHud extends HudModule {
	private static final String[] NAMES = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
	private static final int HEIGHT = 20;

	public final Setting.Num width = this.add(new Setting.Num("width", "Szerokość", 140, 80, 240, 10, " px"));
	public final Setting.Num span = this.add(new Setting.Num("span", "Widoczny kąt", 120, 60, 240, 10, "°"));
	public final Setting.Bool backgroundEnabled = this.add(new Setting.Bool("background_enabled", "Tło", true));
	public final Setting.Color background = this.add(new Setting.Color("background", "Kolor tła", 0xC0101014, true));
	public final Setting.Num rounding = this.add(new Setting.Num("rounding", "Zaokrąglenie rogów", 0, 0, 8, 1, " px"));
	public final Setting.Color marker = this.add(new Setting.Color("marker", "Kolor środka", 0xFFFF4040, false));

	public CompassHud() {
		super("compass", "Direction", "Kompas z kierunkami świata.", false, 0.5F, 0.0F, 0.5F, 0.0F, 0.0F, 22.0F);
	}

	@Override
	public int width(Minecraft client, boolean editing) {
		return this.width.i();
	}

	@Override
	public int height(Minecraft client, boolean editing) {
		return HEIGHT;
	}

	@Override
	protected void draw(GuiGraphics context, Minecraft client, DeltaTracker tickCounter, boolean editing) {
		int w = this.width.i();
		float span = this.span.f();
		// Yaw w grze: 0 = południe, 180 = północ. Kompas: 0 = północ, rośnie zgodnie z ruchem wskazówek zegara.
		float heading = Mth.wrapDegrees(client.player.getViewYRot(tickCounter.getGameTimeDeltaPartialTick(true)) + 180.0F);
		if (this.backgroundEnabled.on()) {
			if (this.rounding.i() > 0) {
				pl.kerf.project.gui.theme.Draw.roundExact(context, 0, 0, w, HEIGHT, this.rounding.i(), this.background.argb());
			} else {
				context.fill(0, 0, w, HEIGHT, this.background.argb());
			}
		}
		context.enableScissor(0, 0, w, HEIGHT);
		for (int angle = 0; angle < 360; angle += 15) {
			float diff = Mth.wrapDegrees(angle - heading);
			if (Math.abs(diff) > span / 2.0F + 10.0F) {
				continue;
			}

			int x = Math.round(w / 2.0F + diff * w / span);
			if (angle % 45 == 0) {
				String name = NAMES[angle / 45];
				int color = angle == 0 ? 0xFFFF6060 : 0xFFFFFFFF;
				context.drawString(client.font, name, x - client.font.width(name) / 2, 3, color, true);
				context.fill(x, HEIGHT - 6, x + 1, HEIGHT - 1, 0xFFD0D0D0);
			} else {
				context.fill(x, HEIGHT - 4, x + 1, HEIGHT - 1, 0xFF808080);
			}
		}

		context.disableScissor();
		context.fill(w / 2, 1, w / 2 + 1, HEIGHT - 1, this.marker.argb());
	}
}
