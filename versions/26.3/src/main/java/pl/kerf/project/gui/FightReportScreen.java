package pl.kerf.project.gui;

import pl.kerf.project.Lang;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleFunction;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import com.mojang.blaze3d.platform.InputConstants;
import pl.kerf.project.gui.theme.Draw;
import pl.kerf.project.gui.theme.Theme;
import pl.kerf.project.module.FightTracker;

/**
 * Karta z podsumowaniem walki z graczem (komenda /fightreport) i historią walk z sesji po prawej. Jedna rzecz pokazana dużo:
 * wynik i pojedynek "ty kontra przeciwnik" z główkami; reszta to ciche wiersze. Animacje liczone od otwarcia albo
 * od wybrania walki z historii; klik albo klawisz od razu pokazuje stan końcowy.
 */
public final class FightReportScreen extends Screen {
	private static final int W = 240;
	private static final int PAD = 12;
	private static final int ROW = 12;
	private static final int ROWS_TOP = 96;
	private static final int HIST_W = 140;
	private static final int HIST_GAP = 8;
	private static final int HIST_ROW = 24;
	private static final int HIST_TOP = 26;
	private static final Identifier HEART = Identifier.withDefaultNamespace("hud/heart/full");

	/** Wartość wiersza zależy od postępu animacji 0..1 (liczby rosną od zera); hearts = ikona serca za wartością. */
	private record Row(String label, DoubleFunction<String> value, boolean hearts, DoubleFunction<String> note) {
		int height() {
			return this.note == null ? ROW : ROW + 10;
		}
	}

	private final Screen parent;
	private final List<FightTracker.Report> history;
	private FightTracker.Report report;
	private final List<Row> rows = new ArrayList<>();
	private long openedAt;
	private int historyScroll;

	public FightReportScreen(Screen parent, FightTracker.Report report) {
		super(Component.literal(Lang.t("Fight Report")));
		this.parent = parent;
		this.history = new ArrayList<>(FightTracker.history());
		this.show(report);
	}

	private void show(FightTracker.Report report) {
		this.report = report;
		this.openedAt = System.currentTimeMillis();
		this.rows.clear();
		int attempts = report.attempts();
		this.rows.add(new Row("Twoje hity, które weszły", p -> attempts == 0 ? "0"
			: Math.round(report.landed() * p) + Lang.t(" z ") + attempts + " (" + Math.round(100.0 * report.landed() / attempts * p) + "%)", false, null));
		this.rows.add(new Row("Zadane obrażenia", p -> hearts(report.damage() * p), true,
			p -> report.landed() == 0 ? null : Lang.t("średnio ") + hearts(report.damagePerHit() * p) + Lang.t(" na hit")));
		this.rows.add(new Row("Najdłuższe combo", p -> String.valueOf(Math.round(report.bestCombo() * p)), false, null));
		this.rows.add(new Row("Średni dystans hitu", p -> report.landed() == 0 ? Lang.t("brak") : String.format(Locale.ROOT, "%.2f m", report.averageDistance() * p), false, null));
		this.rows.add(new Row("Totemy przeciwnika", p -> String.valueOf(Math.round(report.totems() * p)), false, null));
		this.rows.add(new Row("Zużycie twojej zbroi", p -> Math.round(report.armorLost() * p) + Lang.t(" pkt"), false, null));
	}

	/** Punkty zdrowia jako serca: 17 -> "8.5". */
	private static String hearts(double points) {
		return String.format(Locale.ROOT, "%.1f", points / 2.0);
	}

	private boolean showHistory() {
		return !this.history.isEmpty() && this.width >= W + HIST_GAP + HIST_W + 16;
	}

	private int cardH() {
		int h = ROWS_TOP + 8;
		for (Row row : this.rows) {
			h += row.height();
		}

		return h;
	}

	private int cardX() {
		int total = W + (this.showHistory() ? HIST_GAP + HIST_W : 0);
		return (this.width - total) / 2;
	}

	private int cardY() {
		return (this.height - this.cardH()) / 2;
	}

	private int histX() {
		return this.cardX() + W + HIST_GAP;
	}

	private int visibleHistoryRows() {
		return Math.max(1, (this.cardH() - HIST_TOP - 6) / HIST_ROW);
	}

	/** Postęp jednej fazy animacji, 0..1 z wyhamowaniem na końcu; delay i duration w ms od otwarcia ekranu. */
	private float phase(long delay, long duration) {
		float t = (System.currentTimeMillis() - this.openedAt - delay) / (float) duration;
		float left = 1.0F - Math.max(0.0F, Math.min(1.0F, t));
		return 1.0F - left * left * left;
	}

	private static int fade(int color, float alpha) {
		return Theme.alpha(color, Math.round((color >>> 24) * alpha));
	}

	private void text(GuiGraphicsExtractor context, String text, float x, float y, int color, float alpha) {
		int faded = fade(color, alpha);
		// Prawie przezroczysty tekst gra rysuje jako pełny albo wcale, więc go pomijamy.
		if (faded >>> 24 >= 8) {
			Draw.text(context, this.font, text, Math.round(x), Math.round(y), faded);
		}
	}

	/** Duży tekst (2x) z lewym górnym rogiem w (x, y). */
	private void bigText(GuiGraphicsExtractor context, String text, float x, float y, int color, float alpha) {
		context.pose().pushMatrix();
		context.pose().translate(x, y);
		context.pose().scale(2.0F, 2.0F);
		this.text(context, text, 0, 0, color, alpha);
		context.pose().popMatrix();
	}

	private static void heart(GuiGraphicsExtractor context, int x, int y, float alpha) {
		context.blitSprite(RenderPipelines.GUI_TEXTURED, HEART, x, y, 9, 9, fade(0xFFFFFFFF, alpha));
	}

	/** Główka: twarz ze skina gracza (ten sam skin co w grze); bez skina szare pole z pierwszą literą nicku. */
	private void head(GuiGraphicsExtractor context, PlayerSkin skin, String name, int x, int y, int size, float alpha) {
		if (skin != null) {
			PlayerFaceExtractor.extractRenderState(context, skin, x, y, size, fade(0xFFFFFFFF, alpha));
			return;
		}

		Draw.round(context, x, y, size, size, 3, fade(Theme.SURFACE_3, alpha));
		String letter = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
		this.text(context, letter, x + (size - this.font.width(letter)) / 2.0F, y + (size - 8) / 2.0F, Theme.TEXT_2, alpha);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
		Font font = this.font;
		int x = this.cardX();
		int y = this.cardY();
		int h = this.cardH();
		int accent = 0xFF000000 | this.report.outcome().color;

		// Wejście karty: lekko rośnie i podjeżdża do góry.
		float appear = this.phase(0, 220);
		float cx = x + W / 2.0F;
		float cy = y + h / 2.0F;
		float scale = 0.94F + 0.06F * appear;
		context.pose().pushMatrix();
		context.pose().translate(cx, cy + (1.0F - appear) * 10.0F);
		context.pose().scale(scale, scale);
		context.pose().translate(-cx, -cy);
		if (appear >= 1.0F) {
			Draw.shadow(context, x, y, W, h, Ui.PANEL_R, 4);
		}

		Draw.card(context, x, y, W, h, Ui.PANEL_R, fade(Theme.GLASS_STRONG, appear), fade(Theme.BORDER_STRONG, appear));
		this.text(context, "Fight Report", x + PAD, y + 10, Theme.TEXT_3, appear);
		boolean closeHover = Ui.inside(mouseX, mouseY, x + W - 20, y + 6, 14, 14);
		if (closeHover) {
			Draw.round(context, x + W - 20, y + 6, 14, 14, 3, Theme.SURFACE_HOVER);
		}

		Draw.icon(context, Draw.Icon.CLOSE, x + W - 18, y + 8, fade(closeHover ? Theme.TEXT : Theme.TEXT_3, appear));

		// Wynik dużą czcionką, czas walki po prawej.
		float head = this.phase(100, 260);
		int barX = x + PAD;
		int barW = W - 2 * PAD;
		this.bigText(context, this.report.outcome().label, barX - (1.0F - head) * 6.0F, y + 24, accent, head);
		int seconds = this.report.ticks() / 20;
		String time = seconds / 60 + (seconds % 60 < 10 ? ":0" : ":") + seconds % 60;
		this.text(context, time, barX + barW - font.width(time), y + 30, Theme.TEXT_3, head);

		// Ty (z lewej, twoja główka) kontra przeciwnik (z prawej, jego główka).
		String self = this.minecraft.player != null ? this.minecraft.player.getName().getString() : this.minecraft.getUser().getName();
		PlayerSkin selfSkin = this.minecraft.player != null ? this.minecraft.player.getSkin() : null;
		this.head(context, selfSkin, self, barX, y + 44, 12, head);
		int nameW = barW / 2 - 18;
		this.text(context, Draw.fit(font, self, nameW), barX + 16, y + 46, Theme.TEXT_2, head);
		String opponent = Draw.fit(font, this.report.opponent(), nameW);
		this.head(context, this.report.opponentSkin(), this.report.opponent(), barX + barW - 12, y + 44, 12, head);
		this.text(context, opponent, barX + barW - 16 - font.width(opponent), y + 46, Theme.TEXT_2, head);

		// Pojedynek: twoje trafienia kontra przyjęte. Liczby nabijają się od zera.
		float duel = this.phase(220, 240);
		float count = this.phase(260, 700);
		String dealt = String.valueOf(Math.round(this.report.landed() * count));
		String taken = String.valueOf(Math.round(this.report.taken() * count));
		int takenW = font.width(taken) * 2;
		this.bigText(context, dealt, barX, y + 60, Theme.SUCCESS, duel);
		this.text(context, "zadane", barX + font.width(dealt) * 2 + 4, y + 68, Theme.TEXT_3, duel);
		this.bigText(context, taken, barX + barW - takenW, y + 60, Theme.DANGER, duel);
		this.text(context, "przyjęte", barX + barW - takenW - 4 - font.width(Lang.t("przyjęte")), y + 68, Theme.TEXT_3, duel);

		// Pasek udziału: zielony rośnie od lewej, czerwony od prawej, spotykają się w proporcji ciosów.
		int total = this.report.landed() + this.report.taken();
		context.fill(barX, y + 81, barX + barW, y + 86, fade(Theme.SURFACE_3, duel));
		if (total > 0) {
			int ours = Math.round(barW * (float) this.report.landed() / total);
			int gap = ours > 0 && ours < barW ? 1 : 0;
			context.fill(barX, y + 81, barX + Math.round((ours - gap) * count), y + 86, Theme.SUCCESS);
			context.fill(barX + barW - Math.round((barW - ours) * count), y + 81, barX + barW, y + 86, Theme.DANGER);
		}

		// Wiersze wjeżdżają po kolei.
		int ry = y + ROWS_TOP;
		for (int i = 0; i < this.rows.size(); i++) {
			Row row = this.rows.get(i);
			float in = this.phase(420 + i * 45L, 240);
			float p = this.phase(420 + i * 45L, 600);
			this.text(context, row.label, barX - (1.0F - in) * 4.0F, ry, Theme.TEXT_3, in);
			String value = row.value.apply(p);
			int right = barX + barW;
			if (row.hearts) {
				heart(context, right - 9, ry - 1, in);
				right -= 11;
			}

			this.text(context, value, right - font.width(value), ry, Theme.TEXT, in);
			String note = row.note == null ? null : row.note.apply(p);
			if (note != null) {
				// Podtekst pod wartością, mniejszy i cichszy.
				int noteW = Math.round(font.width(note) * 0.75F);
				context.pose().pushMatrix();
				context.pose().translate(barX + barW - 11 - noteW, ry + 10);
				context.pose().scale(0.75F, 0.75F);
				this.text(context, note, 0, 0, Theme.TEXT_4, in);
				context.pose().popMatrix();
			}

			ry += row.height();
		}

		context.pose().popMatrix();
		if (this.showHistory()) {
			this.renderHistory(context, mouseX, mouseY, appear);
		}

		super.extractRenderState(context, mouseX, mouseY, deltaTicks);
	}

	/** Historia walk z sesji: główka przeciwnika, nick i wynik; klik pokazuje tę walkę na karcie. */
	private void renderHistory(GuiGraphicsExtractor context, int mouseX, int mouseY, float appear) {
		Font font = this.font;
		int x = this.histX();
		int y = this.cardY();
		int h = this.cardH();
		if (appear >= 1.0F) {
			Draw.shadow(context, x, y, HIST_W, h, Ui.PANEL_R, 4);
		}

		Draw.card(context, x, y, HIST_W, h, Ui.PANEL_R, fade(Theme.GLASS_STRONG, appear), fade(Theme.BORDER_STRONG, appear));
		this.text(context, "Historia", x + 10, y + 10, Theme.TEXT_3, appear);
		String count = String.valueOf(this.history.size());
		this.text(context, count, x + HIST_W - 10 - font.width(count), y + 10, Theme.TEXT_4, appear);

		int visible = this.visibleHistoryRows();
		this.historyScroll = Math.max(0, Math.min(this.historyScroll, this.history.size() - visible));
		for (int i = 0; i < visible && this.historyScroll + i < this.history.size(); i++) {
			FightTracker.Report entry = this.history.get(this.historyScroll + i);
			int ry = y + HIST_TOP + i * HIST_ROW;
			boolean selected = entry == this.report;
			boolean hover = Ui.inside(mouseX, mouseY, x + 4, ry, HIST_W - 8, HIST_ROW - 2);
			if (selected || hover) {
				Draw.round(context, x + 4, ry, HIST_W - 8, HIST_ROW - 2, 4, selected ? Theme.SURFACE_3 : Theme.SURFACE_HOVER);
			}

			float in = this.phase(200 + i * 40L, 240);
			this.head(context, entry.opponentSkin(), entry.opponent(), x + 9, ry + 3, 16, in);
			this.text(context, Draw.fit(font, entry.opponent(), HIST_W - 40), x + 30, ry + 2, Theme.TEXT, in);
			this.text(context, Draw.fit(font, entry.outcome().label, HIST_W - 40), x + 30, ry + 12, 0xFF000000 | entry.outcome().color, in);
		}

		if (this.history.size() > visible) {
			// Pasek przewijania (kółko myszy nad historią).
			int trackH = visible * HIST_ROW;
			int barH = Math.max(8, trackH * visible / this.history.size());
			int barY = y + HIST_TOP + (trackH - barH) * this.historyScroll / Math.max(1, this.history.size() - visible);
			Draw.round(context, x + HIST_W - 4, barY, 2, barH, 1, Theme.alpha(Theme.TEXT_3, 0x90));
		}
	}

	/** Przeskakuje animację do stanu końcowego. */
	private void skipAnimation() {
		this.openedAt = 0L;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		double mx = click.x();
		double my = click.y();
		if (Ui.inside(mx, my, this.cardX() + W - 20, this.cardY() + 6, 14, 14)) {
			this.onClose();
			return true;
		}

		if (this.showHistory()) {
			int x = this.histX();
			int y = this.cardY();
			for (int i = 0; i < this.visibleHistoryRows() && this.historyScroll + i < this.history.size(); i++) {
				if (Ui.inside(mx, my, x + 4, y + HIST_TOP + i * HIST_ROW, HIST_W - 8, HIST_ROW - 2)) {
					FightTracker.Report entry = this.history.get(this.historyScroll + i);
					if (entry != this.report) {
						// Wybrana walka: karta pokazuje się od nowa, z animacją.
						this.show(entry);
					}

					return true;
				}
			}
		}

		this.skipAnimation();
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (this.showHistory() && Ui.inside(mouseX, mouseY, this.histX(), this.cardY(), HIST_W, this.cardH())) {
			this.historyScroll -= (int) Math.signum(verticalAmount);
			return true;
		}

		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		if (input.key() != InputConstants.KEY_ESCAPE) {
			this.skipAnimation();
		}

		return super.keyPressed(input);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}
}
