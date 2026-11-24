package pl.kerf.project.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import pl.kerf.project.Lang;
import pl.kerf.project.gui.theme.Draw;
import pl.kerf.project.gui.theme.FlatButton;
import pl.kerf.project.gui.theme.Theme;
import pl.kerf.project.module.Modules;
import pl.kerf.project.module.Party;

/**
 * Menu party. Po lewej członkowie: głowa ze skina, nick, koordynaty (jeśli wszyscy się zgodzili) i ranga; leader klika
 * głowę, żeby nadać albo zabrać rangę Inviter, i może wyrzucić gracza przyciskiem X. Po prawej ustawienia party:
 * zapraszanie, koordynaty (włączane głosowaniem), trwające głosowanie, zaproszenia dla ciebie i wyjście z party.
 */
public class PartyScreen extends Screen {
	private static final int ROW = 40;
	private static final int HEAD = 28;
	private final Screen parent;
	private int seenVersion = -1;
	private String inviteName = "";
	private int scroll;
	private int leftX;
	private int leftW;
	private int rightX;
	private int rightW;
	private int listTop;

	public PartyScreen(Screen parent) {
		super(Text.literal(Lang.t("Party")));
		this.parent = parent;
	}

	private Party party() {
		return Modules.PARTY;
	}

	@Override
	protected void init() {
		this.seenVersion = this.party().version();
		int total = Math.min(this.width - 12, 440);
		int x0 = (this.width - total) / 2;
		this.leftW = total * 56 / 100;
		this.leftX = x0;
		this.rightX = x0 + this.leftW + 6;
		this.rightW = total - this.leftW - 6;
		this.listTop = Ui.PAD + 24;
		MinecraftClient client = MinecraftClient.getInstance();
		int inner = this.rightX + 8;
		int content = this.rightW - 16;
		int y = Ui.PAD + 24;
		if (this.party().canInvite(client)) {
			TextFieldWidget field = new TextFieldWidget(this.textRenderer, inner, y, content, 18, Text.literal(Lang.t("Nick gracza")));
			field.setMaxLength(16);
			field.setText(this.inviteName);
			field.setPlaceholder(Text.literal(Lang.t("Nick gracza")));
			field.setChangedListener(text -> this.inviteName = text);
			this.addDrawableChild(field);
			this.addDrawableChild(FlatButton.builder(Text.literal(Lang.t("Zaproś")), button -> {
				if (!this.inviteName.isBlank() && this.party().invite(this.inviteName.strip())) {
					this.inviteName = "";
					this.clearAndInit();
				}
			}).dimensions(inner, y + 22, content, 18).style(FlatButton.Style.PRIMARY).build());
		}

		y += 46;
		// Koordynaty: leader włącza (głosowanie) albo wyłącza; reszta widzi tylko stan.
		if (this.party().isLeader(client)) {
			String label = this.party().coordsEnabled() ? Lang.t("Wyłącz koordynaty") : Lang.t("Włącz koordynaty (głosowanie)");
			FlatButton coords = FlatButton.builder(Text.literal(label), button -> {
				this.party().setCoords(!this.party().coordsEnabled());
				this.clearAndInit();
			}).dimensions(inner, y + 12, content, 18).build();
			coords.active = this.party().vote() == null;
			this.addDrawableChild(coords);
		}

		y += 36;
		Party.Vote vote = this.party().vote();
		if (vote != null && !vote.proposer.equals(client.player.getName().getString()) && vote.myAnswer == null) {
			int half = (content - 4) / 2;
			this.addDrawableChild(FlatButton.builder(Text.literal(Lang.t("Tak")), button -> this.party().answer(true)).dimensions(inner, y + 12, half, 16).style(FlatButton.Style.PRIMARY).build());
			this.addDrawableChild(FlatButton.builder(Text.literal(Lang.t("Nie")), button -> this.party().answer(false)).dimensions(inner + half + 4, y + 12, half, 16).build());
		}

		if (vote != null) {
			y += 34;
		}

		int shown = 0;
		for (Map.Entry<String, Long> invite : this.party().pendingInvites().entrySet()) {
			if (shown++ >= 2) {
				break;
			}

			String from = invite.getKey();
			this.addDrawableChild(FlatButton.builder(Text.literal(Lang.t("Dołącz")), button -> {
				this.party().accept(from);
				this.clearAndInit();
			}).dimensions(inner + content - 56, y, 56, 16).style(FlatButton.Style.PRIMARY).build());
			y += 20;
		}

		int by = this.height - Ui.PAD - 26;
		int half = (content - 4) / 2;
		FlatButton leave = FlatButton.builder(Text.literal(Lang.t("Opuść party")), button -> {
			this.party().leave();
			this.clearAndInit();
		}).dimensions(inner, by, half, 20).build();
		leave.active = this.party().inParty();
		this.addDrawableChild(leave);
		this.addDrawableChild(FlatButton.builder(ScreenTexts.DONE, button -> this.close()).dimensions(inner + half + 4, by, half, 20).build());
	}

	@Override
	public void tick() {
		if (this.party().version() != this.seenVersion) {
			this.clearAndInit();
		}
	}

	private int rows() {
		return Math.max(1, (this.height - Ui.PAD - 24 - this.listTop - 4) / ROW);
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
		super.renderBackground(context, mouseX, mouseY, deltaTicks);
		int h = this.height - 2 * Ui.PAD;
		Ui.panel(context, this.leftX, Ui.PAD, this.leftW, h);
		Ui.panel(context, this.rightX, Ui.PAD, this.rightW, h);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
		MinecraftClient client = MinecraftClient.getInstance();
		String me = client.player.getName().getString();
		List<Map.Entry<String, Party.Role>> members = new ArrayList<>(this.party().roles().entrySet());
		context.drawText(this.textRenderer, Lang.t("Party") + " (" + members.size() + ")", this.leftX + 8, Ui.PAD + 8, Theme.TEXT, false);
		if (members.isEmpty()) {
			context.drawText(this.textRenderer, Lang.t("Nie jesteś w party. Zaproś kogoś po prawej."), this.leftX + 8, this.listTop + 4, 0xFFA0A4B0, false);
		}

		this.scroll = Math.max(0, Math.min(this.scroll, Math.max(0, members.size() - this.rows())));
		boolean leader = this.party().isLeader(client);
		for (int i = 0; i < this.rows() && i + this.scroll < members.size(); i++) {
			Map.Entry<String, Party.Role> member = members.get(i + this.scroll);
			String name = member.getKey();
			Party.Role role = member.getValue();
			int rx = this.leftX + 6;
			int ry = this.listTop + i * ROW;
			int rw = this.leftW - 12;
			Draw.roundExact(context, rx, ry, rw, ROW - 4, 4, Theme.SURFACE);
			// Głowa ze skina (zielone pole z rysunku: nick, czerwone: głowa, niebieskie: koordynaty, fioletowe: ranga).
			PlayerListEntry entry = client.getNetworkHandler() == null ? null : client.getNetworkHandler().getPlayerListEntry(name);
			int hx = rx + 4;
			int hy = ry + 4;
			if (entry != null) {
				PlayerSkinDrawer.draw(context, entry.getSkinTextures(), hx, hy, HEAD);
			} else {
				context.fill(hx, hy, hx + HEAD, hy + HEAD, 0xFF3A3E4A);
				context.drawText(this.textRenderer, "?", hx + 11, hy + 10, Theme.TEXT, false);
			}

			boolean headHover = leader && role != Party.Role.LEADER && Ui.inside(mouseX, mouseY, hx, hy, HEAD, HEAD);
			if (headHover) {
				context.drawStrokedRectangle(hx - 1, hy - 1, HEAD + 2, HEAD + 2, 0xFFFFFFFF);
			}

			int tx = hx + HEAD + 8;
			boolean online = this.party().isOnline(name);
			context.drawText(this.textRenderer, Ui.ellipsis(this.textRenderer, name, rw - HEAD - 40), tx, ry + 5, online ? Theme.TEXT : 0xFF808490, false);
			context.drawText(this.textRenderer, online ? this.coordsText(client, name, me) : Lang.t("Offline (poza serwerem)"), tx, ry + 15, online ? 0xFF7FB0FF : 0xFFE06060, false);
			context.drawText(this.textRenderer, role.label, tx, ry + 25, role.color, false);
			if (leader && !name.equals(me)) {
				int kx = rx + rw - 18;
				boolean hover = Ui.inside(mouseX, mouseY, kx, ry + 4, 14, 14);
				context.fill(kx, ry + 4, kx + 14, ry + 18, hover ? Theme.DANGER : Theme.SURFACE_HOVER);
				context.drawText(this.textRenderer, "X", kx + 4, ry + 7, Theme.TEXT, false);
			}
		}

		if (leader) {
			context.drawText(this.textRenderer, Lang.t("Klik w głowę: ranga Inviter"), this.leftX + 8, this.height - Ui.PAD - 12, 0xFFA0A4B0, false);
		}

		this.renderRight(context, client);
		super.render(context, mouseX, mouseY, deltaTicks);
	}

	private String coordsText(MinecraftClient client, String name, String me) {
		if (!this.party().coordsEnabled()) {
			return Lang.t("Koordynaty wyłączone");
		}

		if (name.equals(me)) {
			return String.format("%d, %d, %d", client.player.getBlockX(), client.player.getBlockY(), client.player.getBlockZ());
		}

		Party.Position position = this.party().positionOf(name);
		return position == null ? Lang.t("Brak danych") : String.format("%d, %d, %d", Math.round(position.x()), Math.round(position.y()), Math.round(position.z()));
	}

	private void renderRight(DrawContext context, MinecraftClient client) {
		int inner = this.rightX + 8;
		context.drawText(this.textRenderer, Lang.t("Ustawienia party"), inner, Ui.PAD + 8, Theme.TEXT, false);
		int y = Ui.PAD + 24;
		if (!this.party().canInvite(client)) {
			context.drawText(this.textRenderer, Ui.ellipsis(this.textRenderer, Lang.t("Zapraszać może leader i inviter."), this.rightW - 16), inner, y + 4, 0xFFA0A4B0, false);
		}

		y += 46;
		String state = Lang.t("Koordynaty członków: ") + Lang.t(this.party().coordsEnabled() ? "Wł." : "Wył.");
		context.drawText(this.textRenderer, Ui.ellipsis(this.textRenderer, state, this.rightW - 16), inner, y + 2, Theme.TEXT, false);
		y += 36;
		Party.Vote vote = this.party().vote();
		if (vote != null) {
			long seconds = Math.max(0, (vote.expires - System.currentTimeMillis()) / 1000L);
			String text = vote.proposer.equals(client.player.getName().getString())
				? Lang.t("Czekanie na głosy... ") + seconds + "s"
				: vote.proposer + Lang.t(" chce włączyć koordynaty ") + seconds + "s";
			context.drawText(this.textRenderer, Ui.ellipsis(this.textRenderer, text, this.rightW - 16), inner, y + 1, Theme.WARNING, false);
			y += 34;
		}

		int shown = 0;
		for (Map.Entry<String, Long> invite : this.party().pendingInvites().entrySet()) {
			if (shown++ >= 2) {
				break;
			}

			context.drawText(this.textRenderer, Ui.ellipsis(this.textRenderer, invite.getKey() + Lang.t(" zaprasza"), this.rightW - 90), inner, y + 4, Theme.TEXT, false);
			y += 20;
		}
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (this.party().isLeader(client)) {
			List<Map.Entry<String, Party.Role>> members = new ArrayList<>(this.party().roles().entrySet());
			String me = client.player.getName().getString();
			for (int i = 0; i < this.rows() && i + this.scroll < members.size(); i++) {
				String name = members.get(i + this.scroll).getKey();
				int rx = this.leftX + 6;
				int ry = this.listTop + i * ROW;
				int rw = this.leftW - 12;
				if (name.equals(me)) {
					continue;
				}

				if (Ui.inside(click.x(), click.y(), rx + rw - 18, ry + 4, 14, 14)) {
					this.party().kick(name);
					return true;
				}

				if (Ui.inside(click.x(), click.y(), rx + 4, ry + 4, HEAD, HEAD)) {
					this.party().toggleInviter(name);
					return true;
				}
			}
		}

		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		this.scroll -= (int) Math.signum(verticalAmount);
		return true;
	}

	@Override
	public void close() {
		this.client.setScreen(this.parent);
	}
}
