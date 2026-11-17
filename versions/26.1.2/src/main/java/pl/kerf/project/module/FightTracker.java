package pl.kerf.project.module;

import pl.kerf.project.Lang;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * Fight Report: liczy przebieg walki z jednym przeciwnikiem wyłącznie z tego, co klient i tak widzi
 * (własne ataki, animacje trafienia, status totemu). Po walce zostawia raport i wiadomość na czacie.
 */
public final class FightTracker {
	public static final String COMMAND = "fightreport";
	/** Tyle ticków serwer ma na potwierdzenie ciosu animacją trafienia (z zapasem na ping). */
	private static final int HIT_WINDOW = 15;
	/** Walka kończy się po 10 s bez żadnego ciosu. */
	private static final int IDLE_END = 200;
	/** Atak na kogoś innego zaczyna nową walkę dopiero po 3 s ciszy w obecnej. */
	private static final int SWITCH_AFTER = 60;
	/** Obrażenia przyjęte do 3 s przed naszym pierwszym ciosem liczą się do walki (przeciwnik zaczął). */
	private static final int PRE_FIGHT = 60;

	public enum Outcome {
		WIN("Wygrana", 0x6CC98E), LOSS("Przegrana", 0xE5675F), ENDED("Nierozstrzygnięta", 0xE6B34E);

		public final String label;
		public final int color;

		Outcome(String label, int color) {
			this.label = label;
			this.color = color;
		}
	}

	/**
	 * damage: zadane obrażenia w punktach zdrowia (2 = jedno serce), ze spadków zdrowia i absorpcji przeciwnika.
	 * averageDistance w blokach, od oczu do hitboxa. opponentSkin: skin przeciwnika z gry (null, gdy niedostępny).
	 */
	public record Report(
		String opponent, Outcome outcome, int ticks, int attempts, int landed, int taken, float damage,
		int bestCombo, double averageDistance, int totems, int armorLost, PlayerSkin opponentSkin
	) {
		public float damagePerHit() {
			return this.landed == 0 ? 0.0F : this.damage / this.landed;
		}
	}

	/** Historia walk z tej sesji gry, najnowsza pierwsza. */
	private static final int HISTORY_SIZE = 20;
	private static final List<Report> HISTORY = new ArrayList<>();

	private static int ticks;
	private static LivingEntity opponent;
	private static String opponentName;
	private static int startTick;
	private static int lastEvent;
	private static int attempts;
	private static int landed;
	private static int taken;
	private static int combo;
	private static int bestCombo;
	private static int totems;
	private static double distanceSum;
	private static float damage;
	private static float opponentHp;
	private static PlayerSkin opponentSkin;
	private static int pendingTick = -1;
	private static double pendingDistance;
	private static int ownHurt;
	private static int armorStart;
	private static int armorLost;
	private static final ArrayDeque<Integer> RECENT_HURTS = new ArrayDeque<>();
	private static boolean openRequested;
	private static boolean opponentDied;

	private FightTracker() {
	}

	public static Report last() {
		return HISTORY.isEmpty() ? null : HISTORY.get(0);
	}

	public static List<Report> history() {
		return Collections.unmodifiableList(HISTORY);
	}

	public static void addToHistory(Report report) {
		HISTORY.add(0, report);
		if (HISTORY.size() > HISTORY_SIZE) {
			HISTORY.remove(HISTORY.size() - 1);
		}
	}

	/** Komenda prosi o kartę; ekran otwiera się w następnym ticku, bo czat po komendzie zamyka bieżący ekran. */
	public static void requestOpen() {
		openRequested = true;
	}

	public static boolean consumeOpenRequest() {
		boolean requested = openRequested;
		openRequested = false;
		return requested;
	}

	/** Tylko walki z graczami (i postaciami graczy, np. manekinami NPC); moby się nie liczą. */
	private static boolean eligible(LivingEntity entity) {
		return entity instanceof Avatar;
	}

	/** Nasz atak na encję (strona klienta). Czy cios "wszedł", okaże się po animacji trafienia przeciwnika. */
	public static void attack(Minecraft client, Entity entity) {
		LocalPlayer player = client.player;
		if (!Modules.FIGHT_REPORT.on() || player == null || player.isSpectator() || entity == player
			|| !(entity instanceof LivingEntity living) || !eligible(living)) {
			return;
		}

		if (opponent != null && living != opponent) {
			if (ticks - lastEvent < SWITCH_AFTER) {
				return;
			}

			finish(client, Outcome.ENDED);
		}

		if (opponent == null) {
			start(player, living);
		}

		attempts++;
		pendingTick = ticks;
		pendingDistance = Math.sqrt(living.getBoundingBox().distanceToSqr(player.getEyePosition()));
		lastEvent = ticks;
	}

	/**
	 * Serwer potwierdził obrażenia encji (pakiet EntityDamageS2C). Cios liczy się jako trafiony dopiero tu, a nie po
	 * animacji trafienia: animację potrafi pokazać sam klient (Instant Hit), pakietu już nie.
	 */
	public static void damaged(Entity entity) {
		if (entity == null || entity != opponent || pendingTick < 0 || ticks - pendingTick > HIT_WINDOW) {
			return;
		}

		landed++;
		combo++;
		bestCombo = Math.max(bestCombo, combo);
		distanceSum += pendingDistance;
		pendingTick = -1;
		lastEvent = ticks;
	}

	/**
	 * Przeciwnik zginął (status 3 z serwera). Na serwerach, które nie wysyłają HP innych graczy i od razu usuwają
	 * zabitego, samo zdrowie nie pokazałoby śmierci i walka kończyłaby się jako nierozstrzygnięta.
	 */
	public static void died(Entity entity) {
		if (entity != null && entity == opponent) {
			opponentDied = true;
		}
	}

	/** Zużyty totem (status 35 z serwera). */
	public static void totemPop(Entity entity) {
		if (entity != null && entity == opponent) {
			totems++;
			lastEvent = ticks;
		}
	}

	private static void start(LocalPlayer player, LivingEntity living) {
		opponent = living;
		opponentDied = false;
		opponentName = living.getName().getString();
		startTick = ticks;
		attempts = 0;
		landed = 0;
		taken = 0;
		combo = 0;
		bestCombo = 0;
		totems = 0;
		distanceSum = 0.0;
		damage = 0.0F;
		opponentHp = hp(living);
		// Skin bierzemy z gry (ten sam, który widać na graczu), więc nie trzeba niczego pobierać z internetu.
		opponentSkin = living instanceof ClientAvatarEntity avatar ? avatar.getSkin() : null;
		pendingTick = -1;
		armorStart = armorDamage(player);
		armorLost = 0;
		for (int hurt : RECENT_HURTS) {
			if (ticks - hurt <= PRE_FIGHT) {
				taken++;
			}
		}
	}

	private static float hp(LivingEntity entity) {
		return Math.max(0.0F, entity.getHealth()) + entity.getAbsorptionAmount();
	}

	private static int armorDamage(LocalPlayer player) {
		int damage = 0;
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			damage += player.getItemBySlot(slot).getDamageValue();
		}

		return damage;
	}

	public static void tick(Minecraft client) {
		ticks++;
		LocalPlayer player = client.player;
		if (player == null || client.level == null) {
			// Wyjście ze świata: niedokończona walka przepada.
			opponent = null;
			RECENT_HURTS.clear();
			ownHurt = 0;
			return;
		}

		// hurtTime skacze w górę przy każdych przyjętych obrażeniach (potem maleje co tick).
		boolean gotHit = player.hurtTime > ownHurt;
		ownHurt = player.hurtTime;
		if (gotHit) {
			RECENT_HURTS.add(ticks);
			if (RECENT_HURTS.size() > 8) {
				RECENT_HURTS.removeFirst();
			}
		}

		if (opponent == null) {
			return;
		}

		if (gotHit) {
			taken++;
			combo = 0;
			lastEvent = ticks;
		}

		// Zadane obrażenia = każdy spadek zdrowia + absorpcji przeciwnika w trakcie walki (leczenie pomijamy).
		float hp = hp(opponent);
		if (hp < opponentHp) {
			damage += opponentHp - hp;
		}

		opponentHp = hp;
		if (!player.isDeadOrDying()) {
			// Po śmierci ekwipunek znika, więc zużycie zbroi liczymy na bieżąco, póki żyjemy.
			armorLost = Math.max(armorLost, armorDamage(player) - armorStart);
		}

		if (player.isDeadOrDying()) {
			finish(client, Outcome.LOSS);
		} else if (opponent.isDeadOrDying() || opponentDied) {
			finish(client, Outcome.WIN);
		} else if (opponent.isRemoved() || ticks - lastEvent > IDLE_END) {
			finish(client, Outcome.ENDED);
		}
	}

	private static void finish(Minecraft client, Outcome outcome) {
		opponent = null;
		// Pojedyncze przypadkowe kliknięcie w kogoś to jeszcze nie walka.
		if (outcome == Outcome.ENDED && landed + taken < 2) {
			return;
		}

		addToHistory(new Report(
			opponentName, outcome, ticks - startTick, attempts, landed, taken, damage, bestCombo,
			landed == 0 ? 0.0 : distanceSum / landed, totems, armorLost, opponentSkin
		));
		String command = "/" + COMMAND;
		client.gui.getChat().addClientSystemMessage(
			Component.literal(Lang.t("[AIO PvP] ")).withColor(0x8C8CF0)
				.append(Component.literal(Lang.t("Walka z ") + opponentName + ": ").withColor(0xC2C6D0))
				.append(Component.literal(Lang.t(outcome.label)).withColor(outcome.color))
				.append(Component.literal(Lang.t(". Raport: ")).withColor(0xC2C6D0))
				.append(Component.literal(command).withStyle(style -> style.withColor(0xFFFFFF).withUnderlined(true)
					.withClickEvent(new ClickEvent.RunCommand(command))
					.withHoverEvent(new HoverEvent.ShowText(Component.literal(Lang.t("Kliknij albo wpisz, żeby otworzyć kartę walki"))))))
		);
		if (Modules.FIGHT_REPORT.sound.on()) {
			client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F));
		}
	}
}
