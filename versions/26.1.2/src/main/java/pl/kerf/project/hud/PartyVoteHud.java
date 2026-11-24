package pl.kerf.project.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import pl.kerf.project.Lang;
import pl.kerf.project.config.Setting;
import pl.kerf.project.gui.Ui;
import pl.kerf.project.gui.theme.Draw;
import pl.kerf.project.gui.theme.Theme;
import pl.kerf.project.module.HudModule;
import pl.kerf.project.module.Modules;
import pl.kerf.project.module.Party;

/**
 * Okienko głosowania w party (jak głosowanie w CS2): "Leader chce włączyć koordynaty", przyciski Tak i Nie oraz czas.
 * Pokazuje się tylko, gdy czeka na ciebie głosowanie; w edytorze HUD widać przykład, który można przesunąć i skalować.
 * Przyciski klika się, gdy kursor jest wolny (czat, ekwipunek, menu party), albo klawiszami z Opcje -> Sterowanie.
 */
public final class PartyVoteHud extends HudModule {
	private static final int HEIGHT = 40;
	private static final int BUTTON_W = 70;
	private static final int BUTTON_H = 15;

	public final Setting.Color background = this.add(new Setting.Color("background", "Kolor tła", 0xE0101420, true));
	public final Setting.Color yesColor = this.add(new Setting.Color("yes_color", "Kolor przycisku Tak", 0xFF2E8B57, false));
	public final Setting.Color noColor = this.add(new Setting.Color("no_color", "Kolor przycisku Nie", 0xFFB04040, false));
	public final Setting.Num rounding = this.add(new Setting.Num("rounding", "Zaokrąglenie rogów", 4, 0, 8, 1, " px"));

	/** Prostokąty przycisków na ekranie (Tak, Nie): x, y, szerokość, wysokość. */
	private final float[][] buttons = new float[2][4];

	public PartyVoteHud() {
		super("party_vote", "Party Vote", "Okienko głosowania w party (np. o koordynatach): przesuń je i zeskaluj w edytorze HUD.", true, 0.5F, 0.0F, 0.5F, 0.0F, 0.0F, 8.0F);
	}

	/** Głosowanie czekające na ciebie albo null; w edytorze zawsze pokazujemy przykład. */
	private String title(Minecraft client, boolean editing) {
		Party.Vote vote = Modules.PARTY.pendingFor(client);
		if (vote != null) {
			return vote.proposer + Lang.t(" chce włączyć koordynaty w party");
		}

		return editing ? "Leader" + Lang.t(" chce włączyć koordynaty w party") : null;
	}

	@Override
	public int width(Minecraft client, boolean editing) {
		String title = this.title(client, editing);
		return title == null ? 0 : Math.max(190, client.font.width(title) + 20);
	}

	@Override
	public int height(Minecraft client, boolean editing) {
		return this.title(client, editing) == null ? 0 : HEIGHT;
	}

	@Override
	protected void draw(GuiGraphicsExtractor context, Minecraft client, DeltaTracker tickCounter, boolean editing) {
		String title = this.title(client, editing);
		this.buttons[0][2] = 0;
		this.buttons[1][2] = 0;
		if (title == null) {
			return;
		}

		Font font = client.font;
		Party.Vote vote = Modules.PARTY.pendingFor(client);
		int seconds = vote == null ? 59 : (int) Math.max(0, (vote.expires - System.currentTimeMillis()) / 1000L);
		int w = this.width(client, editing);
		Draw.roundExact(context, 0, 0, w, HEIGHT, this.rounding.i(), this.background.argb());
		Draw.outlineExact(context, 0, 0, w, HEIGHT, this.rounding.i(), Theme.BORDER_STRONG);
		context.text(font, title, (w - font.width(title)) / 2, 6, 0xFFFFFFFF, true);
		int by = 19;
		int yx = w / 2 - BUTTON_W - 4;
		int nx = w / 2 + 4;
		Draw.roundExact(context, yx, by, BUTTON_W, BUTTON_H, 3, this.yesColor.argb());
		Draw.roundExact(context, nx, by, BUTTON_W, BUTTON_H, 3, this.noColor.argb());
		String yes = "[" + Party.yesKey.getTranslatedKeyMessage().getString() + "] " + Lang.t("Tak");
		String no = "[" + Party.noKey.getTranslatedKeyMessage().getString() + "] " + Lang.t("Nie");
		context.text(font, yes, yx + (BUTTON_W - font.width(yes)) / 2, by + 4, 0xFFFFFFFF, false);
		context.text(font, no, nx + (BUTTON_W - font.width(no)) / 2, by + 4, 0xFFFFFFFF, false);
		String left = seconds + "s";
		context.text(font, left, w - 4 - font.width(left), HEIGHT - 10, 0xFFA0A4B0, false);
		this.store(0, yx, by);
		this.store(1, nx, by);
	}

	private void store(int index, int x, int y) {
		this.buttons[index] = new float[] {this.boundsX + x * this.scale, this.boundsY + y * this.scale, BUTTON_W * this.scale, BUTTON_H * this.scale};
	}

	/** Klik w Tak albo Nie (współrzędne GUI); true, gdy kliknięcie zostało obsłużone. */
	public boolean click(double mx, double my) {
		for (int i = 0; i < 2; i++) {
			float[] b = this.buttons[i];
			if (b[2] > 0 && Ui.inside(mx, my, (int) b[0], (int) b[1], (int) b[2], (int) b[3])) {
				return Modules.PARTY.answer(i == 0);
			}
		}

		return false;
	}
}
