package pl.kerf.project.module;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import pl.kerf.project.Lang;
import pl.kerf.project.config.Setting;
import pl.kerf.project.gui.PartyScreen;
import pl.kerf.project.gui.Ui;
import pl.kerf.project.gui.theme.Draw;
import pl.kerf.project.gui.theme.Theme;

/**
 * Party po stronie klienta: serwer nie zna tego moda, więc komunikaty idą zwykłą prywatną wiadomością (domyślnie /msg)
 * tylko do członków party i są chowane w czacie odbiorcy.
 *
 * Role: Leader (jeden; zaprasza, wyrzuca, nadaje rangę Inviter, zarządza głosowaniami), Inviter (może zapraszać) i Member.
 * Stan party jest autorytatywny u leadera: po każdej zmianie wysyła pozostałym pełny opis ("state"), a członkowie
 * przyjmują go tylko od swojego leadera. Koordynaty członków włącza głosowanie: wszyscy muszą się zgodzić.
 */
public final class Party extends Module {
	public static KeyBinding menuKey;
	public static KeyBinding yesKey;
	public static KeyBinding noKey;
	/** Znacznik rozpoznawczy w wiadomościach moda; odbiorca z modem ukrywa każdą wiadomość z tym tekstem. */
	private static final String TOKEN = "[aio]";
	private static final long INVITE_VALID_MS = 60_000L;
	private static final long VOTE_MS = 60_000L;
	/** Odstęp między wysyłanymi wiadomościami (ticki): serwery tną zbyt szybkie wiadomości prywatne. */
	private static final int SEND_GAP_TICKS = 6;
	private static final double POSITION_MIN_MOVE_SQ = 5.0 * 5.0;

	public final Setting.Str whisper = this.add(new Setting.Str("whisper_command", "Komenda wiadomości prywatnej", "msg"));
	public final Setting.Num positionInterval = this.add(new Setting.Num("position_interval", "Odświeżaj moją pozycję w party co", 10.0, 5.0, 60.0, 5.0, " s"));

	public enum Role {
		LEADER("L", "Leader", 0xFFFFD25E),
		INVITER("I", "Inviter", 0xFF5EC8FF),
		MEMBER("M", "Member", 0xFFA0A4B0);

		public final String code;
		public final String label;
		public final int color;

		Role(String code, String label, int color) {
			this.code = code;
			this.label = label;
			this.color = color;
		}

		static Role of(String code) {
			for (Role role : values()) {
				if (role.code.equals(code)) {
					return role;
				}
			}

			return MEMBER;
		}
	}

	/** Trwające głosowanie (jedno naraz). answers trzyma tylko leader. */
	public static final class Vote {
		public final int id;
		public final String proposer;
		public final long expires;
		public Boolean myAnswer;
		final Map<String, Boolean> answers = new HashMap<>();

		Vote(int id, String proposer, long expires) {
			this.id = id;
			this.proposer = proposer;
			this.expires = expires;
		}
	}

	/** Ostatnio znana pozycja członka: x, y, z, czas (ms) i wymiar. */
	public record Position(double x, double y, double z, long time, String dim) {
	}

	/** Wszyscy członkowie razem z tobą; pusta mapa = nie jesteś w party. */
	private final Map<String, Role> roles = new LinkedHashMap<>();
	private boolean coords;
	private Vote vote;
	private int voteCounter;
	private final Map<String, Position> positions = new HashMap<>();
	private final Map<String, Long> invites = new HashMap<>();
	private final Set<String> invitedByMe = new LinkedHashSet<>();
	/** Zaproszenia wysłane, na które jeszcze nikt nie odpowiedział potwierdzeniem "ack" (nick -> kiedy wysłano). */
	private final Map<String, Long> pendingAck = new HashMap<>();
	/** Wersja protokołu wiadomości. Nowsze wersje moda tylko DOPISUJĄ pola na końcu wiadomości, a nieznane polecenia i pola są ignorowane, więc różne wersje dogadują się dalej. */
	private static final int PROTOCOL = 1;
	private static final String MOD_VERSION = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("aio_pvp")
		.map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("?");
	private static final long ACK_WAIT_MS = 10_000L;
	/** Zaproszenia, które przyjęliśmy: od tych osób przyjmiemy pierwszy opis party. */
	private final Set<String> pendingJoin = new LinkedHashSet<>();
	private final Deque<String> outbox = new ArrayDeque<>();
	private int sendCooldown;
	private long lastPositionSent;
	private Position lastPositionSentAt;
	/** Zmienia się przy każdej zmianie stanu, żeby ekran party wiedział, kiedy się odświeżyć. */
	private int version;

	public Party() {
		super("party", "Party", "Party po stronie klienta: leader, inviter i członkowie, markery wspólne dla party i opcjonalne koordynaty (za zgodą wszystkich). Menu party otwierasz klawiszem (Sterowanie) albo przyciskiem tutaj.", Category.MISC, false);
		this.preview = false;
	}

	// ---------- odczyt stanu (dla ekranu) ----------

	public int version() {
		return this.version;
	}

	public boolean inParty() {
		return !this.roles.isEmpty();
	}

	public Map<String, Role> roles() {
		return new LinkedHashMap<>(this.roles);
	}

	public Role myRole(MinecraftClient client) {
		return this.roles.get(this.me(client));
	}

	public boolean isLeader(MinecraftClient client) {
		return this.myRole(client) == Role.LEADER;
	}

	/** Zapraszać może leader i inviter; ktoś spoza party też (tworzy w ten sposób własne party jako leader). */
	public boolean canInvite(MinecraftClient client) {
		Role role = this.myRole(client);
		return role == null || role != Role.MEMBER;
	}

	public boolean coordsEnabled() {
		return this.coords;
	}

	public Vote vote() {
		return this.vote;
	}

	public Position positionOf(String name) {
		return this.positions.get(name);
	}

	public Map<String, Long> pendingInvites() {
		long now = System.currentTimeMillis();
		this.invites.values().removeIf(time -> now - time > INVITE_VALID_MS);
		return new HashMap<>(this.invites);
	}

	/** Pozostałe osoby z party (bez ciebie). */
	public Set<String> others(MinecraftClient client) {
		Set<String> names = new LinkedHashSet<>(this.roles.keySet());
		names.remove(this.me(client));
		return names;
	}

	private String me(MinecraftClient client) {
		return client.player.getName().getString();
	}

	private String leader() {
		return this.roles.entrySet().stream().filter(e -> e.getValue() == Role.LEADER).map(Map.Entry::getKey).findFirst().orElse(null);
	}

	// ---------- akcje ----------

	public void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		var name = ClientCommandManager.argument("name", StringArgumentType.word())
			.suggests((context, builder) -> {
				context.getSource().getPlayerNames().forEach(builder::suggest);
				return builder.buildFuture();
			});
		dispatcher.register(ClientCommandManager.literal("mparty")
			.then(ClientCommandManager.literal("invite").then(name.executes(c -> this.invite(StringArgumentType.getString(c, "name")) ? 1 : 0)))
			.then(ClientCommandManager.literal("accept").executes(c -> this.accept(null) ? 1 : 0)
				.then(ClientCommandManager.argument("from", StringArgumentType.word()).executes(c -> this.accept(StringArgumentType.getString(c, "from")) ? 1 : 0)))
			.then(ClientCommandManager.literal("kick").then(ClientCommandManager.argument("who", StringArgumentType.word()).executes(c -> this.kick(StringArgumentType.getString(c, "who")) ? 1 : 0)))
			.then(ClientCommandManager.literal("leave").executes(c -> this.leave() ? 1 : 0))
			.then(ClientCommandManager.literal("list").executes(c -> this.list()))
			.then(ClientCommandManager.literal("menu").executes(c -> {
				// Czat po komendzie zamyka swój ekran, więc menu otwieramy dopiero w następnym kroku pętli klienta.
				MinecraftClient client = MinecraftClient.getInstance();
				client.send(() -> client.setScreen(new PartyScreen(null)));
				return 1;
			})));
	}

	public boolean invite(String target) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!this.ready(client) || target.equalsIgnoreCase(this.me(client)) || !this.canInvite(client)) {
			if (this.on() && client.player != null && !this.canInvite(client)) {
				this.say(Lang.t("Zapraszać może tylko leader i inviter."));
			}

			return false;
		}

		this.invitedByMe.add(target);
		this.pendingAck.put(target, System.currentTimeMillis());
		// Tekst po numerze wersji czytają tylko ludzie bez moda (mod go ignoruje): zaproszenie to jedyna wiadomość, która może trafić do kogoś bez moda.
		this.to(target, "inv " + PROTOCOL + " " + MOD_VERSION + " AIO PvP mod: party invite (install the AIO PvP mod to accept)");
		this.say(Lang.t("Wysłano zaproszenie do party: ") + target);
		return true;
	}

	public boolean accept(String from) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!this.ready(client)) {
			return false;
		}

		Map<String, Long> open = this.pendingInvites();
		String inviter = from != null ? from : open.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
		if (inviter == null || !open.containsKey(inviter)) {
			this.say(Lang.t("Nie masz oczekującego zaproszenia do party."));
			return false;
		}

		if (this.inParty()) {
			this.leave();
		}

		this.invites.remove(inviter);
		this.pendingJoin.add(inviter);
		this.to(inviter, "acc");
		this.version++;
		return true;
	}

	public boolean leave() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!this.ready(client) || !this.inParty()) {
			return false;
		}

		String me = this.me(client);
		if (this.isLeader(client)) {
			// Leader odchodzi: pierwszy inviter (albo pierwszy członek) przejmuje party.
			List<String> rest = new ArrayList<>(this.roles.keySet());
			rest.remove(me);
			String successor = rest.stream().filter(n -> this.roles.get(n) == Role.INVITER).findFirst().orElse(rest.isEmpty() ? null : rest.get(0));
			if (successor != null) {
				this.roles.remove(me);
				this.roles.put(successor, Role.LEADER);
				this.broadcastState(me);
			}
		} else {
			this.to(this.leader(), "lea");
		}

		this.clear();
		this.say(Lang.t("Opuszczono party."));
		return true;
	}

	public boolean kick(String who) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!this.ready(client) || !this.isLeader(client) || !this.roles.containsKey(who) || who.equals(this.me(client))) {
			return false;
		}

		this.to(who, "kick");
		this.roles.remove(who);
		this.positions.remove(who);
		this.broadcastState(null);
		this.say(who + Lang.t(" został wyrzucony z party."));
		return true;
	}

	/** Leader nadaje albo zabiera rangę Inviter (klik w głowę gracza w menu party). */
	public boolean toggleInviter(String who) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!this.ready(client) || !this.isLeader(client) || !this.roles.containsKey(who) || this.roles.get(who) == Role.LEADER) {
			return false;
		}

		this.roles.put(who, this.roles.get(who) == Role.INVITER ? Role.MEMBER : Role.INVITER);
		this.broadcastState(null);
		return true;
	}

	/** Leader: włączenie koordynat wymaga głosowania wszystkich, wyłączenie działa od razu. */
	public boolean setCoords(boolean enable) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!this.ready(client) || !this.isLeader(client)) {
			return false;
		}

		if (!enable) {
			this.coords = false;
			this.positions.clear();
			this.broadcastState(null);
			return true;
		}

		if (this.vote != null) {
			return false;
		}

		Set<String> others = this.others(client);
		if (others.isEmpty()) {
			this.coords = true;
			this.version++;
			return true;
		}

		this.vote = new Vote(++this.voteCounter, this.me(client), System.currentTimeMillis() + VOTE_MS);
		this.vote.myAnswer = true;
		for (String name : others) {
			this.to(name, "vote " + this.vote.id + " coords");
		}

		this.say(Lang.t("Rozpoczęto głosowanie: koordynaty w party."));
		this.version++;
		return true;
	}

	/** Twoja odpowiedź w trwającym głosowaniu. */
	public boolean answer(boolean yes) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || this.vote == null || this.vote.myAnswer != null || this.vote.proposer.equals(this.me(client))) {
			return false;
		}

		this.vote.myAnswer = yes;
		this.to(this.vote.proposer, "ans " + this.vote.id + " " + (yes ? "y" : "n"));
		this.version++;
		return true;
	}

	private int list() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!this.inParty()) {
			this.say(Lang.t("Nie jesteś w party."));
			return 1;
		}

		StringBuilder out = new StringBuilder(Lang.t("Party: "));
		this.roles.forEach((name, role) -> out.append(name).append(" (").append(role.label).append(") "));
		this.say(out.toString().strip());
		return 1;
	}

	private void clear() {
		this.roles.clear();
		this.positions.clear();
		this.coords = false;
		this.vote = null;
		this.invitedByMe.clear();
		this.pendingJoin.clear();
		this.version++;
	}

	private void resetAll() {
		this.clear();
		this.invites.clear();
		this.outbox.clear();
	}

	// ---------- wysyłanie ----------

	private void to(String name, String payload) {
		if (name != null) {
			this.outbox.add(name + " " + TOKEN + payload);
		}
	}

	/** Wiadomość do wszystkich innych członków party (np. marker). */
	public void broadcast(String payload) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!this.on() || client.player == null) {
			return;
		}

		for (String name : this.others(client)) {
			this.to(name, payload);
		}
	}

	/** Leader wysyła pełny opis party wszystkim członkom (i ewentualnie osobie, która właśnie odeszła, żeby też się zaktualizowała). */
	private void broadcastState(String extraRecipient) {
		MinecraftClient client = MinecraftClient.getInstance();
		StringBuilder state = new StringBuilder("state ").append(this.coords ? "1" : "0");
		this.roles.forEach((name, role) -> state.append(' ').append(name).append(':').append(role.code));
		for (String name : this.others(client)) {
			this.to(name, state.toString());
		}

		this.version++;
	}

	private void say(String message) {
		MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(Text.literal("[AIO PvP] ").formatted(net.minecraft.util.Formatting.AQUA).append(Text.literal(message)));
	}

	private boolean ready(MinecraftClient client) {
		if (!this.on()) {
			this.say(Lang.t("Włącz moduł Party w menu moda."));
			return false;
		}

		return client.player != null && client.world != null;
	}

	// ---------- odbiór ----------

	/**
	 * Obsługa wiadomości z czatu. Zwraca true, gdy wiadomość należy do moda i ma zniknąć z czatu
	 * (dotyczy też naszego własnego echa "You whisper to ...").
	 */
	public boolean receive(MinecraftClient client, String text, String sender) {
		int at = text.indexOf(TOKEN);
		if (at < 0) {
			return false;
		}

		if (client.player == null) {
			return true;
		}

		String from = sender != null ? sender : this.guessSender(client, text.substring(0, at));
		String me = this.me(client);
		if (from == null || from.equalsIgnoreCase(me)) {
			return true;
		}

		String[] p = text.substring(at + TOKEN.length()).strip().split(" ");
		if (!this.on()) {
			// Mod jest, ale moduł Party wyłączony: wiadomość znika z czatu, a zapraszającemu odpowiadamy od razu, żeby wiedział dlaczego.
			if (p[0].equals("inv")) {
				String command = this.whisper.get().isBlank() ? "msg" : this.whisper.get().strip();
				client.execute(() -> client.player.networkHandler.sendChatCommand(command + " " + from + " " + TOKEN + "ack " + PROTOCOL + " " + MOD_VERSION + " off"));
			}

			return true;
		}

		Role fromRole = this.roles.get(from);
		switch (p[0]) {
			case "ack" -> {
				// Potwierdzenie, że zaproszony ma moda: "ack <protokół> <wersja> [on|off]". Pola dopisane w przyszłości są ignorowane.
				if (this.pendingAck.remove(from) != null) {
					String version = p.length > 2 ? p[2] : "?";
					if (p.length > 3 && p[3].equals("off")) {
						this.say(from + Lang.t(" ma moda, ale wyłączony moduł Party."));
					} else {
						this.say(from + Lang.t(" ma moda (wersja ") + version + Lang.t("). Czeka na jego akceptację."));
						if (this.protocolOf(p, 1) != PROTOCOL) {
							this.say(from + Lang.t(" ma inną wersję moda: część funkcji party może nie działać."));
						}
					}
				}
			}
			case "inv" -> {
				// Odpowiedź "mam moda" od razu, żeby zapraszający nie zgadywał; wersje starsze nie wysyłają pól za "inv".
				this.to(from, "ack " + PROTOCOL + " " + MOD_VERSION + " on");
				this.invites.put(from, System.currentTimeMillis());
				this.say(from + Lang.t(" zaprasza cię do party. Wpisz /mparty accept ") + from);
				this.version++;
			}
			case "acc" -> {
				if (this.invitedByMe.remove(from)) {
					if (!this.inParty()) {
						// Pierwsza osoba, która przyjęła zaproszenie, zakłada party: zapraszający zostaje leaderem.
						this.roles.put(me, Role.LEADER);
					}

					if (this.isLeader(client)) {
						this.addMember(from);
					} else if (this.leader() != null) {
						this.to(this.leader(), "req " + from);
					}
				}
			}
			case "req" -> {
				if (this.isLeader(client) && (fromRole == Role.INVITER || fromRole == Role.LEADER) && p.length > 1) {
					this.addMember(p[1]);
				}
			}
			case "state" -> this.applyState(client, from, p);
			case "kick" -> {
				if (fromRole == Role.LEADER) {
					this.clear();
					this.say(Lang.t("Wyrzucono cię z party."));
				}
			}
			case "lea" -> {
				if (this.isLeader(client) && fromRole != null) {
					this.roles.remove(from);
					this.positions.remove(from);
					this.broadcastState(null);
					this.say(from + Lang.t(" opuścił party."));
				}
			}
			case "vote" -> {
				if (fromRole == Role.LEADER && p.length > 2 && p[2].equals("coords")) {
					try {
						this.vote = new Vote(Integer.parseInt(p[1]), from, System.currentTimeMillis() + VOTE_MS);
						this.say(Lang.t("Leader chce włączyć koordynaty w party. Y = tak, N = nie (albo menu party)."));
						this.version++;
					} catch (NumberFormatException ignored) {
						// Zepsuty numer głosowania: pomijamy.
					}
				}
			}
			case "ans" -> {
				if (this.vote != null && this.isLeader(client) && fromRole != null && p.length > 2 && p[1].equals(String.valueOf(this.vote.id))) {
					this.vote.answers.put(from, p[2].equals("y"));
					this.tally(client);
				}
			}
			case "end" -> {
				if (fromRole == Role.LEADER && this.vote != null) {
					this.vote = null;
					this.say(Lang.t(p.length > 2 && p[2].equals("ok") ? "Głosowanie zakończone: koordynaty włączone." : "Głosowanie zakończone: koordynaty nie zostały włączone."));
					this.version++;
				}
			}
			case "pos" -> {
				if (fromRole != null && this.coords && p.length >= 5) {
					try {
						this.positions.put(from, new Position(Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]), System.currentTimeMillis(), p[4]));
						this.version++;
					} catch (NumberFormatException ignored) {
						// Zepsuta pozycja: pomijamy.
					}
				}
			}
			case "m" -> {
				if (fromRole != null) {
					Modules.MARKERS.receive(from, p);
				}
			}
			default -> { }
		}

		return true;
	}

	/** Numer protokołu z pola wiadomości (puste albo zepsute = 1). */
	private int protocolOf(String[] p, int index) {
		try {
			return p.length > index ? Integer.parseInt(p[index]) : 1;
		} catch (NumberFormatException e) {
			return 1;
		}
	}

	/** Ile zaproszeń czeka jeszcze na potwierdzenie, że zaproszony ma moda. */
	public int pendingAcks() {
		return this.pendingAck.size();
	}

	private void addMember(String name) {
		if (this.validName(name) && !this.roles.containsKey(name)) {
			this.roles.put(name, Role.MEMBER);
			this.say(name + Lang.t(" dołączył do party."));
			this.broadcastState(null);
		}
	}

	private boolean validName(String name) {
		return name.matches("[A-Za-z0-9_]{1,16}");
	}

	/** Opis party od leadera (albo pierwszy opis od osoby, której zaproszenie przyjęliśmy). */
	private void applyState(MinecraftClient client, String from, String[] p) {
		boolean fromLeader = from.equals(this.leader());
		boolean firstState = this.roles.isEmpty() && this.pendingJoin.contains(from);
		if ((!fromLeader && !firstState) || p.length < 3) {
			return;
		}

		Map<String, Role> parsed = new LinkedHashMap<>();
		for (int i = 2; i < p.length; i++) {
			String[] entry = p[i].split(":");
			if (entry.length == 2 && this.validName(entry[0])) {
				parsed.put(entry[0], Role.of(entry[1]));
			}
		}

		if (!parsed.containsKey(this.me(client))) {
			this.clear();
			return;
		}

		boolean joining = this.roles.isEmpty();
		this.roles.clear();
		this.roles.putAll(parsed);
		this.coords = p[1].equals("1");
		this.positions.keySet().retainAll(parsed.keySet());
		this.pendingJoin.clear();
		if (joining) {
			this.say(Lang.t("Dołączono do party: ") + from);
		}

		this.version++;
	}

	/** Leader liczy głosy: jedno "nie" kończy głosowanie porażką, komplet "tak" sukcesem. */
	private void tally(MinecraftClient client) {
		Set<String> others = this.others(client);
		boolean anyNo = this.vote.answers.containsValue(false);
		boolean allYes = this.vote.answers.keySet().containsAll(others) && !anyNo;
		if (anyNo || allYes) {
			this.finishVote(allYes);
		}
	}

	private void finishVote(boolean ok) {
		MinecraftClient client = MinecraftClient.getInstance();
		int id = this.vote.id;
		this.vote = null;
		if (ok) {
			this.coords = true;
		}

		for (String name : this.others(client)) {
			this.to(name, "end " + id + " " + (ok ? "ok" : "fail"));
		}

		this.say(Lang.t(ok ? "Głosowanie zakończone: koordynaty włączone." : "Głosowanie zakończone: koordynaty nie zostały włączone."));
		this.broadcastState(null);
	}

	/** Nadawca z tekstu wiadomości prywatnej, gdy serwer nie podał go osobno: pierwszy gracz z listy, którego nick tam stoi. */
	private String guessSender(MinecraftClient client, String prefix) {
		if (client.getNetworkHandler() == null) {
			return null;
		}

		String best = null;
		int bestAt = Integer.MAX_VALUE;
		for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
			String name = entry.getProfile().name();
			int index = prefix.indexOf(name);
			if (index >= 0 && index < bestAt) {
				best = name;
				bestAt = index;
			}
		}

		return best;
	}

	// ---------- co tick ----------

	/** Kolejka wiadomości, wysyłanie pozycji, wygasanie głosowania i klawisze; wołane co tick. */
	public void tick(MinecraftClient client) {
		if (client.world == null || client.player == null) {
			this.resetAll();
			return;
		}

		while (menuKey != null && menuKey.wasPressed()) {
			if (this.on() && client.currentScreen == null) {
				client.setScreen(new PartyScreen(null));
			}
		}

		// Klawisze głosowania działają tylko, gdy czeka na ciebie głosowanie (poza tym Y i N nic nie robią).
		boolean pending = this.on() && this.vote != null && this.vote.myAnswer == null && !this.vote.proposer.equals(this.me(client));
		while (yesKey != null && yesKey.wasPressed()) {
			if (pending) {
				this.answer(true);
			}
		}

		while (noKey != null && noKey.wasPressed()) {
			if (pending) {
				this.answer(false);
			}
		}

		if (!this.on()) {
			return;
		}

		long now = System.currentTimeMillis();
		if (this.vote != null && now > this.vote.expires) {
			if (this.isLeader(client)) {
				this.finishVote(false);
			} else {
				this.vote = null;
				this.version++;
			}
		}

		// Zaproszony nie odpowiedział w kilka sekund: najpewniej nie ma moda (albo serwer blokuje wiadomości prywatne).
		this.pendingAck.entrySet().removeIf(entry -> {
			if (now - entry.getValue() > ACK_WAIT_MS) {
				this.say(entry.getKey() + Lang.t(" nie odpowiada: prawdopodobnie nie ma tego moda (albo serwer blokuje wiadomości prywatne)."));
				return true;
			}

			return false;
		});
		this.sharePosition(client, now);
		if (this.sendCooldown > 0) {
			this.sendCooldown--;
		} else if (!this.outbox.isEmpty()) {
			String command = this.whisper.get().isBlank() ? "msg" : this.whisper.get().strip();
			client.player.networkHandler.sendChatCommand(command + " " + this.outbox.poll());
			this.sendCooldown = SEND_GAP_TICKS;
		}
	}

	/** Gdy koordynaty są włączone: co kilka sekund (i tylko po zmianie miejsca o kilka bloków) wysyła twoją pozycję reszcie party. */
	private void sharePosition(MinecraftClient client, long now) {
		if (!this.coords || !this.inParty() || this.others(client).isEmpty() || now - this.lastPositionSent < this.positionInterval.i() * 1000L) {
			return;
		}

		Position here = new Position(client.player.getX(), client.player.getY(), client.player.getZ(), now, client.world.getRegistryKey().getValue().getPath());
		Position last = this.lastPositionSentAt;
		boolean moved = last == null || !last.dim().equals(here.dim())
			|| (here.x() - last.x()) * (here.x() - last.x()) + (here.y() - last.y()) * (here.y() - last.y()) + (here.z() - last.z()) * (here.z() - last.z()) >= POSITION_MIN_MOVE_SQ;
		if (!moved) {
			return;
		}

		this.lastPositionSent = now;
		this.lastPositionSentAt = here;
		this.broadcast(String.format(Locale.ROOT, "pos %.1f %.1f %.1f %s", here.x(), here.y(), here.z(), here.dim()));
	}

	// ---------- okienko głosowania ----------

	/** Głosowanie, które czeka na twoją odpowiedź (null = brak). */
	public Vote pendingFor(MinecraftClient client) {
		return this.on() && client.player != null && this.vote != null && this.vote.myAnswer == null && !this.vote.proposer.equals(this.me(client)) ? this.vote : null;
	}

	/** Okienko "leader chce włączyć koordynaty" (element HUD "Party Vote", który przesuwa się w edytorze HUD); rysuje się na wierzchu. */
	public void renderPopup(DrawContext context, MinecraftClient client) {
		if (Modules.PARTY_VOTE.on() && this.pendingFor(client) != null) {
			Modules.PARTY_VOTE.render(context, client, client.getRenderTickCounter(), false);
		}
	}

	/** Klik w przycisk okienka głosowania; true, gdy kliknięcie zostało obsłużone. */
	public boolean clickPopup(double mx, double my) {
		return Modules.PARTY_VOTE.on() && Modules.PARTY_VOTE.click(mx, my);
	}
}
