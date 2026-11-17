package pl.kerf.project.hud;

import java.util.function.BiFunction;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import pl.kerf.project.config.Setting;
import pl.kerf.project.module.HudModule;

/** Prosty element HUD-u z jedną linią tekstu na półprzezroczystym tle (styl jak tekst w F3). */
public class InfoHud extends HudModule {
	protected static final int PAD_X = 3;
	protected static final int PAD_Y = 3;

	public final Setting.Bool background = this.add(new Setting.Bool("background", "Tło", true));
	public final Setting.Color backgroundColor = this.add(new Setting.Color("background_color", "Kolor tła", 0x90505050, true));
	public final Setting.Bool shadow = this.add(new Setting.Bool("shadow", "Cień tekstu", true));
	private final BiFunction<Minecraft, Boolean, Component> text;
	/** Tekst i jego szerokość z bieżącej klatki: width() liczy raz, height() i draw() tylko czytają. */
	private Component current;
	private int currentW;
	/** Tekst liczony raz na tick: FPS, ping, CPS czy zegar i tak nie zmieniają się częściej. */
	private int updatedAge = -1;
	private boolean updatedEditing;

	public InfoHud(String id, String name, String description, float ax, float ay, float px, float py, float ox, float oy, BiFunction<Minecraft, Boolean, Component> text) {
		super(id, name, description, false, ax, ay, px, py, ox, oy);
		this.text = text;
	}

	/** Treść; null = nic do pokazania (wtedy element znika). */
	protected Component text(Minecraft client, boolean editing) {
		return this.text.apply(client, editing);
	}

	/** Wywoływane jako pierwsze w każdej klatce (render i podgląd zaczynają od width). */
	@Override
	public int width(Minecraft client, boolean editing) {
		if (client.player.tickCount != this.updatedAge || editing != this.updatedEditing) {
			this.updatedAge = client.player.tickCount;
			this.updatedEditing = editing;
			this.current = this.text(client, editing);
			this.currentW = this.current == null ? 0 : client.font.width(this.current);
		}

		return this.current == null ? 0 : this.currentW + 2 * PAD_X;
	}

	@Override
	public int height(Minecraft client, boolean editing) {
		return this.current == null ? 0 : 8 + 2 * PAD_Y;
	}

	@Override
	protected void draw(GuiGraphicsExtractor context, Minecraft client, DeltaTracker tickCounter, boolean editing) {
		Component text = this.current;
		if (text == null) {
			return;
		}

		if (this.background.on()) {
			context.fill(0, 0, this.currentW + 2 * PAD_X, 8 + 2 * PAD_Y, this.backgroundColor.argb());
		}

		context.text(client.font, text, PAD_X, PAD_Y, 0xFFFFFFFF, this.shadow.on());
	}
}
