package pl.kerf.project.hud;

//? if <26.1.2 {
import net.minecraft.client.DeltaTracker;
//?}
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
//? if >=26.1.2 {
/*import net.minecraft.client.DeltaTracker;
*///?}
import pl.kerf.project.Lang;
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
	/** Czas wejścia (spadek z górą i jedno odbicie) i wyjścia okienka w milisekundach. */
	private static final long ENTER_MS = 700L;
	private static final long LEAVE_MS = 320L;

	/** Prostokąty przycisków na ekranie (Tak, Nie): x, y, szerokość, wysokość. */
	private final float[][] buttons = new float[2][4];
	private long enterAt;
	private long leaveAt;
	private boolean wasPending;
	private String lastTitle;
	private int lastSeconds = 59;
	private float animY;

	public PartyVoteHud() {
		super("party_vote", "Party Vote", "Okienko głosowania w party (np. o koordynatach): przesuń je i zeskaluj w edytorze HUD.", true, 0.5F, 0.0F, 0.5F, 0.0F, 0.0F, 8.0F);
	}

	/** Tło okienka: własne (zaokrąglone) albo panel z gry (Vanilla GUI = oryginalna tekstura, Resourcepack GUI = z texturepacka); panel z gry skaluje się razem z elementem. */
	private void panel(GuiGraphics context, int w) {
		int style = Modules.PARTY.voteStyle.get();
		if (style == 0) {
			Draw.roundExact(context, 0, 0, w, HEIGHT, Modules.PARTY.voteRounding.i(), Modules.PARTY.voteBackground.argb());
			Draw.outlineExact(context, 0, 0, w, HEIGHT, Modules.PARTY.voteRounding.i(), Theme.BORDER_STRONG);
			return;
		}

		VanillaGui.effectPanel(context, 0, 0, w, HEIGHT, false, style == 2);
	}

	/** Przycisk Tak/Nie: własny kolorowy albo panel z gry z półprzezroczystym kolorem na wierzchu (zielony/czerwony). */
	private void button(GuiGraphics context, int x, int y, int argb) {
		int style = Modules.PARTY.voteStyle.get();
		if (style == 0) {
			Draw.roundExact(context, x, y, BUTTON_W, BUTTON_H, 3, argb);
			return;
		}

		VanillaGui.effectPanel(context, x, y, BUTTON_W, BUTTON_H, true, style == 2);
		context.fill(x + 2, y + 2, x + BUTTON_W - 2, y + BUTTON_H - 2, Theme.alpha(argb, 0x90));
	}

	/** Czy okienko jeszcze znika po udzielonej odpowiedzi (animacja wyjścia). */
	private boolean leaving() {
		return Modules.PARTY.voteAnimation.on() && this.leaveAt != 0L && this.lastTitle != null && System.currentTimeMillis() - this.leaveAt < LEAVE_MS;
	}

	/** Przesunięcie w pionie (px, ujemne = wyżej): wejście spada z góry, odbija się raz i staje; wyjście wjeżdża w górę. */
	private float animationOffset(long now, boolean pending) {
		float drop = HEIGHT + 20.0F;
		if (pending) {
			float t = Math.min(1.0F, (now - this.enterAt) / (float) ENTER_MS);
			if (t < 0.6F) {
				float f = t / 0.6F;
				return -drop * (1.0F - f * f);
			}

			return -(float) Math.sin((t - 0.6F) / 0.4F * Math.PI) * 9.0F;
		}

		if (this.leaveAt != 0L) {
			float t = Math.min(1.0F, (now - this.leaveAt) / (float) LEAVE_MS);
			return -drop * t * t;
		}

		return 0.0F;
	}

	/** Głosowanie czekające na ciebie albo null; w edytorze zawsze pokazujemy przykład. */
	private String title(Minecraft client, boolean editing) {
		Party.Vote vote = Modules.PARTY.pendingFor(client);
		if (vote != null) {
			return vote.proposer + Lang.t(" chce włączyć koordynaty w party");
		}

		if (editing) {
			return "Leader" + Lang.t(" chce włączyć koordynaty w party");
		}

		return this.leaving() ? this.lastTitle : null;
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
	protected void draw(GuiGraphics context, Minecraft client, DeltaTracker tickCounter, boolean editing) {
		String title = this.title(client, editing);
		boolean pending = Modules.PARTY.pendingFor(client) != null;
		long now = System.currentTimeMillis();
		if (pending && !this.wasPending) {
			this.enterAt = now;
			this.leaveAt = 0L;
		}

		if (!pending && this.wasPending) {
			this.leaveAt = now;
		}

		this.wasPending = pending;
		if (pending && title != null) {
			this.lastTitle = title;
		}

		this.animY = editing || !Modules.PARTY.voteAnimation.on() ? 0.0F : this.animationOffset(now, pending);
		this.buttons[0][2] = 0;
		this.buttons[1][2] = 0;
		if (title == null) {
			return;
		}

		Font font = client.font;
		Party.Vote vote = Modules.PARTY.pendingFor(client);
		int seconds = vote == null ? this.lastSeconds : (int) Math.max(0, (vote.expires - System.currentTimeMillis()) / 1000L);
		if (vote != null) {
			this.lastSeconds = seconds;
		}

		int w = this.width(client, editing);
		context.pose().pushMatrix();
		context.pose().translate(0.0F, this.animY);
		this.panel(context, w);
		context.drawString(font, title, (w - font.width(title)) / 2, 6, 0xFFFFFFFF, true);
		int by = 19;
		int yx = w / 2 - BUTTON_W - 4;
		int nx = w / 2 + 4;
		this.button(context, yx, by, Modules.PARTY.voteYes.argb());
		this.button(context, nx, by, Modules.PARTY.voteNo.argb());
		String yes = "[" + Party.yesKey.getTranslatedKeyMessage().getString() + "] " + Lang.t("Tak");
		String no = "[" + Party.noKey.getTranslatedKeyMessage().getString() + "] " + Lang.t("Nie");
		context.drawString(font, yes, yx + (BUTTON_W - font.width(yes)) / 2, by + 4, 0xFFFFFFFF, false);
		context.drawString(font, no, nx + (BUTTON_W - font.width(no)) / 2, by + 4, 0xFFFFFFFF, false);
		String left = seconds + "s";
		context.drawString(font, left, w - 4 - font.width(left), HEIGHT - 10, 0xFFA0A4B0, false);
		context.pose().popMatrix();
		this.store(0, yx, Math.round(by + this.animY));
		this.store(1, nx, Math.round(by + this.animY));
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
