package pl.kerf.project.module;

import pl.kerf.project.Lang;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.Items;
import pl.kerf.project.config.Setting;
import pl.kerf.project.config.PixelGrid;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import pl.kerf.project.hud.ArmorHud;
import pl.kerf.project.hud.CompassHud;
import pl.kerf.project.hud.CoordinatesHud;
import pl.kerf.project.hud.CpsTracker;
import pl.kerf.project.hud.InfoHud;
import pl.kerf.project.hud.ItemPreviewHud;
import pl.kerf.project.hud.MediaHud;
import pl.kerf.project.hud.KeyOverlayHud;
import pl.kerf.project.hud.LagDetectorHud;
import pl.kerf.project.hud.PitchHud;
import pl.kerf.project.hud.PotionStatusHud;
import pl.kerf.project.module.Module.Category;

/** Wszystkie funkcje moda. Mixiny i rendery czytają ustawienia bezpośrednio z tych pól. */
public final class Modules {
	/** Tworzony raz: zegar w HUD-zie formatuje godzinę co klatkę. */
	private static final DateTimeFormatter CLOCK_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
	/** Zegar 12-godzinny: 3:45 PM (AM/PM po angielsku, bo tak się je zapisuje). */
	private static final DateTimeFormatter CLOCK_FORMAT_12 = DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.ENGLISH);
	// HUD
	public static final ArmorHud ARMOR_HUD = new ArmorHud();
	public static final ItemPreviewHud ITEM_PREVIEW = new ItemPreviewHud();
	public static final PotionStatusHud POTION_STATUS = new PotionStatusHud();
	public static final KeyOverlayHud KEY_OVERLAY = new KeyOverlayHud();
	public static final PitchHud ELYTRA_PITCH = new PitchHud(
		"elytra_pitch", "Elytra Pitch", "Kąt lotu podczas używania elytry.", 14.0F, player -> player.isGliding()
	);
	public static final PitchHud BOW_PITCH = new PitchHud(
		"bow_pitch", "Bow Pitch", "Kąt celowania podczas naciągania łuku lub kuszy.", 26.0F,
		player -> player.isUsingItem() && (player.getActiveItem().isOf(Items.BOW) || player.getActiveItem().isOf(Items.CROSSBOW))
	);
	public static final CoordinatesHud COORDINATES = new CoordinatesHud();
	public static final InfoHud FPS = new InfoHud(
		"fps", "FPS", "Klatki na sekundę.", 0.0F, 0.0F, 0.0F, 0.0F, 4.0F, 22.0F,
		(client, editing) -> Text.literal("FPS: " + client.getCurrentFps())
	);
	public static final InfoHud PING = new InfoHud(
		"ping", "Ping", "Opóźnienie do serwera.", 0.0F, 0.0F, 0.0F, 0.0F, 4.0F, 40.0F,
		(client, editing) -> {
			PlayerListEntry entry = client.player.networkHandler.getPlayerListEntry(client.player.getUuid());
			return Text.literal("Ping: " + (entry == null ? 0 : entry.getLatency()) + " ms");
		}
	);
	public static final InfoHud CPS = new InfoHud(
		"cps", "CPS", "Kliknięcia na sekundę (lewy | prawy).", 0.0F, 0.0F, 0.0F, 0.0F, 4.0F, 58.0F,
		(client, editing) -> Text.literal("CPS: " + CpsTracker.left() + " | " + CpsTracker.right())
	);
	public static final InfoHud DAY = new InfoHud(
		"day", "Day", "Numer dnia w świecie.", 0.0F, 0.0F, 0.0F, 0.0F, 4.0F, 76.0F,
		(client, editing) -> Text.literal(Lang.t("Dzień: ") + client.world.getTimeOfDay() / 24000L)
	);
	public static final InfoHud CLOCK = new InfoHud(
		"clock", "Clock", "Aktualna godzina (zegar komputera).", 0.0F, 0.0F, 0.0F, 0.0F, 4.0F, 94.0F,
		(client, editing) -> Text.literal(LocalTime.now().format(Modules.CLOCK_12H.is(1) ? CLOCK_FORMAT_12 : CLOCK_FORMAT))
	);
	public static final Setting.Mode CLOCK_12H = CLOCK.add(new Setting.Mode("format", "Format godziny", 0, "24h", "12h (AM/PM)"));
	/** Stan sprintu/skradania jak w klientach PvP; znika, gdy nic nie jest aktywne (w edytorze pokazuje przykład). */
	public static final InfoHud SPRINT_STATUS = new InfoHud(
		"sprint_status", "Sprint/Sneak Status", "Napis [Sprint (Toggled)] / [Sneak], przydatne z Toggle Sprint.", 0.5F, 0.0F, 0.5F, 0.0F, 0.0F, 46.0F,
		(client, editing) -> {
			Text status = sprintStatus(client);
			return status == null && editing ? Text.literal(Lang.t("[Sprint (Toggled)]")) : status;
		}
	);
	public static final LagDetectorHud LAG_DETECTOR = new LagDetectorHud();
	public static final CompassHud COMPASS = new CompassHud();
	public static final MediaHud MEDIA = new MediaHud();
	public static final HudTweaker HUD_TWEAKER = new HudTweaker();
	public static final Saturation SATURATION = new Saturation();
	public static final LocatorBarSettings LOCATOR_BAR = new LocatorBarSettings();

	// Combat
	public static final Crosshair CROSSHAIR = new Crosshair();
	public static final Hitboxes HITBOXES = new Hitboxes();
	public static final HitColor HIT_COLOR = new HitColor();
	public static final Health HEALTH = new Health();
	public static final FightReport FIGHT_REPORT = new FightReport();
	public static final InstantHit INSTANT_HIT = new InstantHit();
	public static final DamageCalc DAMAGE_CALC = new DamageCalc();
	/** Serwer resetuje ładowanie ataku przy każdym machnięciu ręką, klient nie (MC-218570); to wyrównuje wskaźnik. */
	public static final Module COOLDOWN_FIX = new Module(
		"cooldown_fix", "Cooldown Fix", "Wskaźnik ataku zgodny z serwerem, np. po zniszczeniu bloku (MC-218570).", Category.COMBAT, true
	);

	// Visuals
	public static final LowFire LOW_FIRE = new LowFire();
	public static final PotionParticles NO_POTION_PARTICLES = new PotionParticles();
	public static final Module NO_EXPLOSION_PARTICLES = new Module(
		"no_explosion_particles", "No Explosion Particles", "Ukrywa cząsteczki eksplozji (m.in. crystal PvP).", Category.VISUALS, false
	);
	public static final Fullbright FULLBRIGHT = new Fullbright();
	public static final BlockOutline BLOCK_OUTLINE = new BlockOutline();
	public static final Nametags NAMETAGS = new Nametags();
	public static final Module OWN_NAME = new Module(
		"own_name", "Name in 3rd Person", "Twój nick nad głową w widoku z trzeciej osoby.", Category.VISUALS, false
	);
	public static final Module NO_CAMERA_SHAKE = new Module(
		"no_camera_shake", "No Camera Shake", "Kamera nie buja się przy chodzeniu (View Bobbing), ręka dalej tak.", Category.VISUALS, false
	);
	public static final ClearView CLEAR_VIEW = new ClearView();
	public static final Freelook FREELOOK = new Freelook();

	// Items
	public static final ViewModel VIEW_MODEL = new ViewModel();
	public static final Shield SHIELD = new Shield();
	public static final Totem TOTEM = new Totem();
	public static final BoatView BOAT_VIEW = new BoatView();
	public static final FastXp FAST_XP = new FastXp();
	public static final InvisibleHand INVISIBLE_HAND = new InvisibleHand();
	public static final OldAnimations OLD_ANIMATIONS = new OldAnimations();

	// Misc
	public static final ChatHistory CHAT_HISTORY = new ChatHistory();
	public static final Module NO_SIGN_GUI = new Module(
		"no_sign_gui", "No Sign GUI", "Postawiona tabliczka nie otwiera okna edycji. Skrót też w Sterowaniu.", Category.MISC, false
	);
	public static final Waypoints WAYPOINTS = new Waypoints();
	public static final Markers MARKERS = new Markers();
	public static final Party PARTY = new Party();
	public static final pl.kerf.project.hud.PartyVoteHud PARTY_VOTE = new pl.kerf.project.hud.PartyVoteHud();
	public static final Sounds SOUNDS = new Sounds();
	public static final Optimization OPTIMIZATION = new Optimization();
	public static final CommandKeys COMMAND_KEYS = new CommandKeys();
	public static final GuiTheme GUI_THEME = new GuiTheme();

	public static final List<Module> ALL = List.of(
		ARMOR_HUD, ITEM_PREVIEW, POTION_STATUS, KEY_OVERLAY, ELYTRA_PITCH, BOW_PITCH,
		COORDINATES, FPS, PING, CPS, DAY, CLOCK, SPRINT_STATUS, LAG_DETECTOR, COMPASS, MEDIA, PARTY_VOTE, HUD_TWEAKER, SATURATION, LOCATOR_BAR,
		CROSSHAIR, HITBOXES, HIT_COLOR, HEALTH, FIGHT_REPORT, INSTANT_HIT, DAMAGE_CALC, COOLDOWN_FIX,
		LOW_FIRE, NO_POTION_PARTICLES, NO_EXPLOSION_PARTICLES, FULLBRIGHT, BLOCK_OUTLINE, NAMETAGS, OWN_NAME, NO_CAMERA_SHAKE, CLEAR_VIEW, FREELOOK,
		VIEW_MODEL, SHIELD, TOTEM, BOAT_VIEW, FAST_XP, INVISIBLE_HAND, OLD_ANIMATIONS,
		CHAT_HISTORY, NO_SIGN_GUI, WAYPOINTS, MARKERS, PARTY, SOUNDS, OPTIMIZATION, COMMAND_KEYS, GUI_THEME
	);

	public static final List<HudModule> HUD = List.of(
		ARMOR_HUD, ITEM_PREVIEW, POTION_STATUS, ELYTRA_PITCH, BOW_PITCH, COORDINATES, FPS, PING, CPS, DAY, CLOCK, SPRINT_STATUS, LAG_DETECTOR, COMPASS, MEDIA, KEY_OVERLAY, PARTY_VOTE
	);

	private Modules() {
	}

	/**
	 * Skradanie ma pierwszeństwo (nie da się sprintować skradając się). "Toggled" = włączony Toggle Sprint/Sneak
	 * i przełączony klawisz; inaczej stan samej postaci (klawisz trzymany albo podwójne W). null = nic aktywnego.
	 */
	private static Text sprintStatus(net.minecraft.client.MinecraftClient client) {
		net.minecraft.client.option.GameOptions options = client.options;
		if (options.getSneakToggled().getValue() && options.sneakKey.isPressed()) {
			return Text.literal(Lang.t("[Sneak (Toggled)]"));
		}

		if (client.player.isSneaking()) {
			return Text.literal(Lang.t("[Sneak (Key held)]"));
		}

		if (options.getSprintToggled().getValue() && options.sprintKey.isPressed()) {
			return Text.literal(Lang.t("[Sprint (Toggled)]"));
		}

		return client.player.isSprinting() ? Text.literal(Lang.t("[Sprint (Vanilla)]")) : null;
	}

	/** Przesunięcie i skala jednego elementu vanilla HUD-u. */
	public static final class Tweak {
		public final Setting.Num x;
		public final Setting.Num y;
		public final Setting.Num scale;

		Tweak(HudTweaker owner, String key, String label) {
			this.x = owner.add(new Setting.Num(key + "_x", label + " X", 0, -4000, 4000, 1, " px").axis(1));
			this.y = owner.add(new Setting.Num(key + "_y", label + " Y", 0, -3000, 3000, 1, " px").axis(2));
			this.scale = owner.add(new Setting.Num(key + "_scale", label + " skala", 1.0, 0.25, 2.0, 0.05, "x"));
		}
	}

	public static final class HudTweaker extends Module {
		public final Tweak title = new Tweak(this, "title", "Title");
		public final Tweak actionbar = new Tweak(this, "actionbar", "Actionbar");
		public final Tweak scoreboard = new Tweak(this, "scoreboard", "Scoreboard");
		public final Tweak bossbar = new Tweak(this, "bossbar", "Bossbar");
		/** Hotbar, serca, głód i pasek XP o tyle pikseli wyżej, żeby nie były przyklejone do dołu ekranu. */
		public final Setting.Num lift = this.add(new Setting.Num("hotbar_lift", "Podniesienie hotbara", 0, 0, 10, 1, " px"));

		/** Ile pikseli GUI podnieść dolny pasek (0, gdy moduł wyłączony). */
		public int hotbarLift() {
			return this.on() ? this.lift.i() : 0;
		}

		HudTweaker() {
			// Domyślnie włączony: przesunięcia są zerowe, więc nic nie zmienia, dopóki nie ruszysz elementu w edytorze.
			super("hud_tweaker", "HUD Tweaker", "Pozycja i skala title, actionbara, scoreboardu i bossbara (też w edytorze HUD).", Category.HUD, true);
			// Trzy ustawienia na element (X, Y, skala) = jeden wiersz na element.
			this.columns = 3;
		}
	}

	/** Celownik: zwykły (bez celu) i na celu. Może być vanillowy (z kolorem) albo narysowany pikselami w edytorze. */
	public static final class Crosshair extends Module {
		public final Setting.Mode targets = this.add(new Setting.Mode("targets", "Reaguj na", 0, "Graczy", "Wszystkie żywe"));
		public final Setting.Bool custom = this.add(new Setting.Bool("custom", "Własny celownik", false));
		public final Setting.Num scale = this.add(new Setting.Num("scale", "Skala celownika", 1.0, 0.5, 3.0, 0.5, "x"));
		public final Setting.Bool recolor = this.add(new Setting.Bool("recolor", "Zmień kolor na celu", true));
		public final Setting.Color color = this.add(new Setting.Color("color", "Kolor na celu", 0xFFFF3030, false));
		public final Setting.Str pixels = this.add(new Setting.Str("pixels", "Piksele: bez celu", PixelGrid.plus(0xFFFFFFFF))).hide();
		public final Setting.Str targetPixels = this.add(new Setting.Str("target_pixels", "Piksele: na celu", PixelGrid.plus(0xFFFF3030))).hide();
		public final Setting.Color brush = this.add(new Setting.Color("brush", "Kolor pędzla", 0xFFFFFFFF, true)).hide();
		public final PixelGrid normalGrid = new PixelGrid(this.pixels);
		public final PixelGrid targetGrid = new PixelGrid(this.targetPixels);

		Crosshair() {
			super("crosshair", "Crosshair Indicator", "Celownik bez celu i na celu: kolor albo własny rysunek z pikseli.", Category.COMBAT, false);
		}

		public PixelGrid grid(boolean onTarget) {
			return onTarget ? this.targetGrid : this.normalGrid;
		}
	}

	/** Zastępuje vanillowe hitboxy z F3+B, więc widać je tylko wtedy, gdy są włączone tym skrótem. */
	public static final class Hitboxes extends Module {
		public final Setting.Bool players = this.add(new Setting.Bool("players", "Gracze", true));
		public final Setting.Color playerColor = this.add(new Setting.Color("player_color", "Kolor: gracze", 0xFFFFFFFF, true));
		public final Setting.Bool hostile = this.add(new Setting.Bool("hostile", "Wrogie moby", true));
		public final Setting.Color hostileColor = this.add(new Setting.Color("hostile_color", "Kolor: wrogie moby", 0xFFFFFFFF, true));
		public final Setting.Bool mobs = this.add(new Setting.Bool("mobs", "Pozostałe moby", true));
		public final Setting.Color mobColor = this.add(new Setting.Color("mob_color", "Kolor: pozostałe moby", 0xFFFFFFFF, true));
		public final Setting.Bool others = this.add(new Setting.Bool("others", "Inne encje (przedmioty, strzały)", true));
		public final Setting.Color otherColor = this.add(new Setting.Color("other_color", "Kolor: inne encje", 0x80FFFFFF, true));
		public final Setting.Color targetColor = this.add(new Setting.Color("target_color", "Kolor: na celowniku", 0xFFFF3030, true));
		public final Setting.Bool eyes = this.add(new Setting.Bool("eyes", "Linia oczu i kierunek patrzenia", false));
		public final Setting.Num width = this.add(new Setting.Num("width", "Grubość linii", 2.5, 1.0, 6.0, 0.5));

		Hitboxes() {
			super("hitboxes", "Combat Hitboxes", "Własne hitboxy F3+B: kolor celu, graczy i mobów osobno.", Category.COMBAT, true);
		}
	}

	/** Podsumowanie walki: liczenie w {@link FightTracker}, karta pod komendą /fightreport. */
	public static final class FightReport extends Module {
		public final Setting.Bool sound = this.add(new Setting.Bool("sound", "Dźwięk po walce", true));

		FightReport() {
			super("fight_report", "Fight Report", "Po walce z graczem wiadomość na czacie z dźwiękiem; /fightreport otwiera kartę ze statystykami.", Category.COMBAT, true);
		}
	}

	public static final class HitColor extends Module {
		public final Setting.Color color = this.add(new Setting.Color("color", "Kolor trafienia", 0xFFFF0000, false));
		public final Setting.Num opacity = this.add(new Setting.Num("opacity", "Krycie", 30, 0, 100, 5, "%"));
		public final Setting.Mode behavior = this.add(new Setting.Mode("behavior", "Zachowanie", 0, "Stały kolor", "Zanikanie"));

		HitColor() {
			super("hit_color", "Hit Color", "Kolor, krycie i zachowanie efektu trafienia.", Category.COMBAT, false);
		}
	}

	public static final class Health extends Module {
		public final Setting.Mode style = this.add(new Setting.Mode("style", "Widok", 1, "Serca", "Liczba serc"));
		public final Setting.Mode position = this.add(new Setting.Mode("position", "Pozycja", 0, "Nad nickiem", "Pod nickiem"));
		public final Setting.Num offset = this.add(new Setting.Num("offset", "Dodatkowe przesunięcie Y", 0, -40, 40, 1, " px"));
		public final Setting.Bool absorption = this.add(new Setting.Bool("absorption", "Pokaż absorpcję", true));
		/** Serca tylko u graczy bliżej niż tyle bloków: na zatłoczonym serwerze mniej rysowania w każdej klatce. */
		public final Setting.Num distance = this.add(new Setting.Num("distance", "Maksymalna odległość", 32, 8, 64, 4, " m"));

		Health() {
			super("health", "Player Health Indicators", "Zdrowie graczy przy nicku: serca albo liczba serc.", Category.COMBAT, false);
		}
	}

	public static final class LowFire extends Module {
		public final Setting.Num offsetY = this.add(new Setting.Num("offset_y", "Przesunięcie Y", -0.25, -0.7, 0.3, 0.05));
		public final Setting.Num opacity = this.add(new Setting.Num("opacity", "Krycie", 60, 0, 100, 5, "%"));

		LowFire() {
			super("low_fire", "Low Fire", "Obniża i rozjaśnia ogień zasłaniający ekran.", Category.VISUALS, false);
		}
	}

	public static final class PotionParticles extends Module {
		public final Setting.Bool ownOnly = this.add(new Setting.Bool("own_only", "Tylko własne", true));
		public final Setting.Bool splash = this.add(new Setting.Bool("splash", "Także rozbicie mikstur", false));

		PotionParticles() {
			super("no_potion_particles", "No Potion Particles", "Ukrywa cząsteczki efektów mikstur.", Category.VISUALS, false);
		}
	}

	/** Usuwa to, co zasłania widok: nakładkę dyni, lunety i spławik wędki zaczepiony o ciebie. */
	public static final class ClearView extends Module {
		public final Setting.Bool pumpkin = this.add(new Setting.Bool("pumpkin", "Nakładka dyni", true));
		public final Setting.Bool spyglass = this.add(new Setting.Bool("spyglass", "Nakładka lunety", true));
		public final Setting.Bool bobber = this.add(new Setting.Bool("bobber", "Spławik zaczepiony o ciebie", true));
		public final Setting.Bool inWall = this.add(new Setting.Bool("in_wall", "Ciemny ekran w bloku", true));
		public final Setting.Bool waterFog = this.add(new Setting.Bool("water_fog", "Mgła w wodzie", true));
		public final Setting.Bool lavaFog = this.add(new Setting.Bool("lava_fog", "Mgła w lawie", true));

		ClearView() {
			super("clear_view", "Clear View", "Usuwa nakładkę dyni, lunety, ciemny ekran w bloku, mgłę w wodzie i lawie oraz spławik zaczepiony o ciebie.", Category.VISUALS, false);
		}
	}

	public static final class Fullbright extends Module {
		public final Setting.Num brightness = this.add(new Setting.Num("brightness", "Jasność", 1000, 100, 1500, 50, "%"));

		Fullbright() {
			super("fullbright", "Fullbright", "Regulowana jasność ponad limit vanilla.", Category.VISUALS, false);
		}
	}

	public static final class BlockOutline extends Module {
		public static final int MODE_OUTLINE = 0;
		public static final int MODE_FULL = 1;
		public static final int MODE_FACE = 2;
		public final Setting.Mode mode = this.add(new Setting.Mode("mode", "Tryb", 1, "Tylko linie", "Cały blok", "Widoczna strona"));
		public final Setting.Color color = this.add(new Setting.Color("color", "Kolor linii", 0xFFFFFFFF, true));
		public final Setting.Bool fillEnabled = this.add(new Setting.Bool("fill_enabled", "Wypełnienie", true));
		public final Setting.Color fill = this.add(new Setting.Color("fill", "Kolor wypełnienia", 0x40FFFFFF, true));
		public final Setting.Num width = this.add(new Setting.Num("width", "Grubość linii", 2.5, 1.0, 6.0, 0.5));

		BlockOutline() {
			super("block_outline", "Block Outline", "Kolor i tryb podświetlenia wskazywanego bloku.", Category.VISUALS, false);
		}
	}

	public static final class Nametags extends Module {
		public final Setting.Bool whiteText = this.add(new Setting.Bool("white_text", "Biały tekst", true));
		public final Setting.Bool backgroundEnabled = this.add(new Setting.Bool("background_enabled", "Tło nicku", true));
		public final Setting.Color background = this.add(new Setting.Color("background", "Kolor tła nicku", 0x80000000, true));

		Nametags() {
			super("nametags", "Nametags", "Czytelniejsze nicki: biały tekst i własne tło.", Category.VISUALS, false);
		}
	}

	/** Przesunięcie i skala przedmiotu w jednej ręce. */
	public static final class HandTransform {
		public final Setting.Num x;
		public final Setting.Num y;
		public final Setting.Num z;
		public final Setting.Num scale;

		HandTransform(Module owner, String key, String label) {
			this.x = owner.add(new Setting.Num(key + "x", label + "X", 0, -1.5, 1.5, 0.05).axis(1));
			this.y = owner.add(new Setting.Num(key + "y", label + "Y", 0, -1.5, 1.5, 0.05).axis(2));
			this.z = owner.add(new Setting.Num(key + "z", label + "Z", 0, -1.5, 1.5, 0.05).axis(3));
			this.scale = owner.add(new Setting.Num(key + "scale", label + "skala", 1.0, 0.1, 2.0, 0.05, "x"));
		}
	}

	public static final class ViewModel extends Module {
		public final HandTransform main = new HandTransform(this, "main_", "Main hand: ");
		public final HandTransform off = new HandTransform(this, "off_", "Offhand: ");

		ViewModel() {
			super("view_model", "View Model", "Położenie i skala przedmiotów w main handzie i offhandzie.", Category.ITEMS, false);
			this.preview = false;
		}
	}

	/** Wszystko o tarczy w jednym module; przełącznik modułu wyłącza całość, opcje niżej poszczególne części. */
	public static final class Shield extends Module {
		/** MC-238293: gracz śledzony od połowy używania ma itemUseTimeLeft=0, więc poza się nie pokazuje. */
		public final Setting.Bool animFix = this.add(new Setting.Bool("anim_fix", "Fix animacji", true));
		/** Tarcza realnie blokuje dopiero po 5 tickach; animacja bloku pokazuje się dopiero wtedy, nie przy tapnięciu. */
		public final Setting.Bool delayFix = this.add(new Setting.Bool("delay_fix", "Blok po 5 tickach", false));
		public final Setting.Bool position = this.add(new Setting.Bool("position", "Pozycja i skala", false));
		public final HandTransform transform = new HandTransform(this, "", "");

		public boolean animFix() {
			return this.on() && this.animFix.on();
		}

		public boolean delayFix() {
			return this.on() && this.delayFix.on();
		}

		public boolean position() {
			return this.on() && this.position.on();
		}

		Shield() {
			// Domyślnie włączony, bo fix animacji był domyślnie włączony; pozycja zostaje vanilla, dopóki nie zaznaczysz opcji.
			super("shield", "Shield", "Fixy animacji tarczy (MC-238293, delay bloku) oraz jej pozycja X/Y/Z i skala.", Category.ITEMS, true);
			this.preview = false;
		}
	}

	public static final class Totem extends Module {
		public final Setting.Num scale = this.add(new Setting.Num("scale", "Rozmiar animacji", 0.5, 0.0, 1.5, 0.05, "x"));

		Totem() {
			super("totem", "Totem Tweaks", "Rozmiar animacji totemu na ekranie.", Category.ITEMS, false);
		}
	}

	public static final class BoatView extends Module {
		public final Setting.Bool mapsOnly = this.add(new Setting.Bool("maps_only", "Tylko mapy", false));

		BoatView() {
			super("boat_view", "Boat Item View", "Przedmioty w rękach (np. mapa) widoczne podczas wiosłowania.", Category.ITEMS, false);
		}
	}

	public static final class FastXp extends Module {
		/** Indeks opcji = przerwa w tickach - 1 (gra ma 20 ticków na sekundę). */
		public final Setting.Mode speed = this.add(new Setting.Mode(
			"speed", "Szybkość", 1, "20 butelek/s (co tick)", "10 butelek/s", "6.7 butelek/s", "5 butelek/s (vanilla)"
		));

		public int interval() {
			return this.speed.get() + 1;
		}

		FastXp() {
			super("fast_xp", "Fast XP", "Szybsze rzucanie butelek XP przy przytrzymaniu przycisku użycia.", Category.ITEMS, false);
		}
	}

	public static final class InvisibleHand extends Module {
		public final Setting.Num opacity = this.add(new Setting.Num("opacity", "Krycie ręki", 35, 5, 100, 5, "%"));

		InvisibleHand() {
			super("invisible_hand", "Invisible Hand", "Przezroczysta ręka w pierwszej osobie, gdy masz niewidzialność.", Category.ITEMS, false);
		}
	}

	/** Animacje w stylu 1.7/1.8 w pierwszej osobie. Czysto wizualne: serwer i inni gracze widzą wszystko jak zwykle. */
	public static final class OldAnimations extends Module {
		public final Setting.Bool swordBlock = this.add(new Setting.Bool("sword_block", "Blok mieczem (tarcza w drugiej ręce)", true));
		public final Setting.Bool noAttackDip = this.add(new Setting.Bool("no_attack_dip", "Bez opadania przedmiotu po ciosie", true));

		OldAnimations() {
			super("old_animations", "1.7 Animations", "Blok mieczem jak w 1.7 (gdy blokujesz tarczą) i przedmiot bez opadania po ciosie.", Category.ITEMS, false);
		}
	}

	/** Command Keys: klawisz -> komenda ("/spawn") albo zwykła wiadomość na czat. Lista bindów ma własny edytor w menu. */
	public static final class CommandKeys extends Module {
		public static final int MAX_LENGTH = 256;
		public final List<Bind> binds = new ArrayList<>();

		CommandKeys() {
			super("command_keys", "Command Keys", "Bindy: klawisz wysyła komendę (np. /spawn) albo wiadomość na czat.", Category.MISC, true);
		}

		public static final class Bind {
			/** Kod klawisza GLFW; -1 = nieprzypisany. */
			public int key;
			public String command;

			public Bind(int key, String command) {
				this.key = key;
				this.command = command;
			}
		}

		@Override
		public void reset() {
			super.reset();
			this.binds.clear();
		}

		@Override
		public JsonObject write() {
			JsonObject json = super.write();
			JsonArray array = new JsonArray();
			for (Bind bind : this.binds) {
				JsonObject entry = new JsonObject();
				entry.addProperty("key", bind.key);
				entry.addProperty("command", bind.command);
				array.add(entry);
			}

			json.add("binds", array);
			return json;
		}

		@Override
		public void read(JsonObject json) {
			super.read(json);
			this.binds.clear();
			if (json.has("binds") && json.get("binds").isJsonArray()) {
				for (JsonElement element : json.getAsJsonArray("binds")) {
					// Uszkodzony wpis pomijamy, reszta bindów zostaje.
					if (element.isJsonObject() && element.getAsJsonObject().has("command")) {
						JsonObject entry = element.getAsJsonObject();
						int key = entry.has("key") ? entry.get("key").getAsInt() : -1;
						String command = entry.get("command").getAsString();
						this.binds.add(new Bind(key, command.length() > MAX_LENGTH ? command.substring(0, MAX_LENGTH) : command));
					}
				}
			}
		}
	}

	/** Motyw menu moda: gotowe palety i opcjonalny własny kolor akcentu. Wyłączony = domyślny granat. */
	/** Złota obwódka na ikonkach głodu = saturacja (jak AppleSkin); kształt brany z ikonki aktywnego texturepacka. */
	public static final class Saturation extends Module {
		/** Trzymane jedzenie: mrugające ikonki głodu i obwódki saturacji, które to jedzenie doda. */
		public final Setting.Bool foodPreview = this.add(new Setting.Bool("food_preview", "Podgląd jedzenia w ręce", true));

		Saturation() {
			super("saturation", "Saturation", "Złota obwódka na pasku głodu pokazuje saturację; z jedzeniem w ręce mruga, ile ono doda.", Category.HUD, false);
		}
	}

	/** Pasek lokalizacji (waypointy graczy): głowy z obwódką w kolorze waypointa, nicki, kierunek świata i cień tekstu. */
	public static final class LocatorBarSettings extends Module {
		public final Setting.Bool heads = this.add(new Setting.Bool("heads", "Głowy graczy", true));
		public final Setting.Bool direction = this.add(new Setting.Bool("direction", "Pokaż kierunek", false));
		public final Setting.Bool xpBar = this.add(new Setting.Bool("xp_bar", "Na pasku XP", false));
		public final Setting.Mode names = this.add(new Setting.Mode("names", "Pokaż nick gracza", 0, "Nigdy", "Po najechaniu", "Zawsze"));
		public final Setting.Bool icons = this.add(new Setting.Bool("icons", "Pokaż ikony", true));
		public final Setting.Bool textShadow = this.add(new Setting.Bool("text_shadow", "Cień tekstu", true));

		LocatorBarSettings() {
			super("locator_bar", "Locator Bar", "Pasek lokalizacji: głowy graczy z obwódką w ich kolorze, nicki, kierunek świata. Opcja \"Na pasku XP\" rysuje ikony na pasku doświadczenia.", Category.HUD, false);
		}
	}

	public static final class GuiTheme extends Module {
		public final Setting.Mode theme = this.add(new Setting.Mode("theme", "Motyw", 0, pl.kerf.project.gui.theme.Theme.THEME_NAMES));
		public final Setting.Bool customAccent = this.add(new Setting.Bool("custom_accent", "Własny kolor akcentu", false));
		public final Setting.Color accent = this.add(new Setting.Color("accent", "Kolor akcentu", 0xFF8C8CF0, false));
		/** Układ menu: klasyczne trzy panele albo pływające (osobna pigułka wyszukiwarki, moduły i podgląd w jednym panelu). */
		public final Setting.Mode layout = this.add(new Setting.Mode("layout", "Układ menu", 0, "Klasyczny", "Pływający", "Pływający, pasek z prawej"));
		/** Wygląd modułów na liście: karty z przełącznikiem albo kafelki z dwoma równymi przyciskami (Ustawienia i Włącz/Wyłącz). */
		public final Setting.Mode cardStyle = this.add(new Setting.Mode("card_style", "Styl modułów", 0, "Karty", "Kafelki"));
		/** Przezroczystość menu: 100% = jak w motywie, mniej = bardziej widać świat za panelami. */
		public final Setting.Num menuOpacity = this.add(new Setting.Num("menu_opacity", "Przezroczystość menu", 100.0, 30.0, 100.0, 5.0, "%"));
		/** Język menu i HUD-u moda; domyślnie angielski (patrz {@link pl.kerf.project.Lang}). */
		public final Setting.Mode language = this.add(new Setting.Mode("language", "Język", 0, "English", "Polski"));
		/** Na własne ryzyko: moduły z szarej strefy (Freelook, Instant Hit) zostają włączone po restarcie gry. Włącza się tylko przez okno z ostrzeżeniem. */
		public final Setting.Bool rememberRisky = this.add(new Setting.Bool("remember_risky", "Zapamiętuj ryzykowne moduły", false));

		/**
		 * Ustawienia samego moda (przycisk "Settings" w menu): motyw, układ, język i reset wszystkiego. Id zostaje
		 * "gui_theme", żeby stare configi dalej działały. Nie ma go na liście modułów i nie da się go wyłączyć.
		 */
		GuiTheme() {
			super("gui_theme", "Settings", "Motyw, układ i język menu oraz reset wszystkich ustawień.", Category.MISC, true);
		}

		/** Wołane co tick: zmiana w ustawieniach od razu widać w otwartym menu. */
		public void sync() {
			pl.kerf.project.gui.theme.Theme.apply(this.theme.get(), this.customAccent.on() ? this.accent.argb() : null, this.menuOpacity.f() / 100.0F);
		}
	}

	public static final class ChatHistory extends Module {
		public final Setting.Num lines = this.add(new Setting.Num("lines", "Liczba wiadomości", 1000, 100, 10000, 100));

		ChatHistory() {
			super("chat_history", "More Chat History", "Dłuższa historia czatu (vanilla: 100).", Category.MISC, true);
		}
	}
}
