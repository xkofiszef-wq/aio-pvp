package pl.kerf.project.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.KeyMapping;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import pl.kerf.project.Lang;
import pl.kerf.project.config.ConfigManager;
import pl.kerf.project.config.PixelGrid;
import pl.kerf.project.config.Setting;
import pl.kerf.project.gui.WaypointScreen;

/**
 * Waypointy: punkt z nazwą, kolorem i (opcjonalnie) własną ikoną. Dodasz go w menu waypointów (klawisz albo przycisk
 * w ustawieniach), klawiszem "dodaj tutaj" albo komendą /waypoint. W świecie: pionowa wiązka, ramka, ikona, nazwa i dystans.
 * Zapis w configu (ukryte ustawienie "data"), osobno dla każdego serwera/świata i wymiaru.
 */
public final class Waypoints extends Module {
	/** Klawisze (Opcje -> Sterowanie, domyślnie nieprzypisane); ustawiane przy rejestracji klawiszy. */
	public static KeyMapping key;
	public static KeyMapping menuKey;
	/** Napis dalej niż tyle bloków jest przesuwany na promień do punktu, żeby zawsze mieścił się w polu widzenia. */
	private static final double LABEL_MAX_DISTANCE = 24.0;
	private static final double BEAM_HEIGHT = 320.0;
	/** Szerokość ikony jako ułamek odległości od kamery: stały rozmiar na ekranie. */
	private static final double ICON_WIDTH_PER_BLOCK = 0.15;

	public final Setting.Color color = this.add(new Setting.Color("color", "Kolor punktów", 0xFF55FFFF, false));
	public final Setting.Num labelSize = this.add(new Setting.Num("label_size", "Rozmiar napisu", 1.0, 0.5, 3.0, 0.1, "x"));
	public final Setting.Num opacity = this.add(new Setting.Num("opacity", "Przezroczystość waypointów", 100.0, 10.0, 100.0, 5.0, "%"));
	public final Setting.Bool alwaysNames = this.add(new Setting.Bool("always_names", "Zawsze pokazuj nazwy", false));
	public final Setting.Bool deathWaypoint = this.add(new Setting.Bool("death_waypoint", "Waypoint po śmierci", false));
	public final Setting.Bool labelBackground = this.add(new Setting.Bool("label_background", "Tło napisu", true));
	public final Setting.Color labelBackgroundColor = this.add(new Setting.Color("label_background_color", "Kolor tła napisu", 0x80000000, true));
	private final Setting.Str data = this.add(new Setting.Str("data", "Punkty", "[]")).hide();
	/** Sparsowane {@link #data}; null = trzeba wczytać (start, reset). */
	private List<Point> points;

	/** icon = rysunek w formacie {@link PixelGrid} albo pusty tekst (bez ikony). */
	public record Point(String world, String dim, String name, int x, int y, int z, int color, String icon) {
	}

	public Waypoints() {
		super("waypoints", "Waypoints", "Zapisuje miejsca z nazwą, kolorem i ikoną: wiązka i dystans w świecie. Menu waypointów otwierasz klawiszem (Sterowanie) albo przyciskiem tutaj.", Category.MISC, false);
		this.preview = false;
	}

	@Override
	public void reset() {
		super.reset();
		this.points = null;
	}

	public void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(ClientCommands.literal("waypoint")
			.then(ClientCommands.literal("add").executes(c -> this.addHere(c.getSource(), null))
				.then(ClientCommands.argument("name", StringArgumentType.greedyString()).executes(c -> this.addHere(c.getSource(), StringArgumentType.getString(c, "name")))))
			.then(ClientCommands.literal("remove")
				.then(ClientCommands.argument("name", StringArgumentType.greedyString()).executes(c -> this.remove(c.getSource(), StringArgumentType.getString(c, "name")))))
			.then(ClientCommands.literal("list").executes(c -> this.list(c.getSource())))
			.then(ClientCommands.literal("menu").executes(c -> {
				// Czat po komendzie zamyka swój ekran, więc menu otwieramy dopiero w następnym kroku pętli klienta.
				Minecraft client = Minecraft.getInstance();
				client.execute(() -> client.setScreen(new WaypointScreen(null)));
				return 1;
			})));
	}

	/** Klawisze; wołane co tick. */
	public void tick(Minecraft client) {
		this.trackDeath(client);
		while (key != null && key.consumeClick()) {
			if (this.on() && client.player != null && client.screen == null) {
				this.addHere(null, null);
			}
		}

		while (menuKey != null && menuKey.consumeClick()) {
			if (this.on() && client.player != null && client.screen == null) {
				client.setScreen(new WaypointScreen(null));
			}
		}
	}

	/** Ostatnia pozycja żywego gracza i to, czy żył w poprzednim ticku: śmierć = przejście żywy -> martwy. */
	private net.minecraft.core.BlockPos lastAlivePos;
	private boolean wasAlive;

	private void trackDeath(Minecraft client) {
		if (client.player == null || client.level == null) {
			this.wasAlive = false;
			return;
		}

		if (client.player.isAlive()) {
			this.lastAlivePos = client.player.blockPosition();
			this.wasAlive = true;
		} else if (this.wasAlive) {
			this.wasAlive = false;
			if (this.on() && this.deathWaypoint.on() && this.lastAlivePos != null) {
				String name = "Death";
				for (int i = 2; this.find(client, name) != null; i++) {
					name = "Death " + i;
				}

				this.create(client, name, this.lastAlivePos.getX(), this.lastAlivePos.getY(), this.lastAlivePos.getZ(), 0xFFFF3030, "");
				this.say(null, Lang.t("Dodano waypoint: ") + name);
			}
		}
	}

	/** Waypointy bieżącego świata i wymiaru (kopia tylko do odczytu). */
	public List<Point> here(Minecraft client) {
		String world = world(client);
		String dim = dim(client);
		return this.points().stream().filter(point -> point.world().equals(world) && point.dim().equals(dim)).toList();
	}

	/** Dodaje punkt; zwraca tekst błędu albo null, gdy się udało. */
	public String create(Minecraft client, String name, int x, int y, int z, int argb, String icon) {
		String label = name == null || name.isBlank() ? "Waypoint " + (this.here(client).size() + 1) : name.strip();
		if (this.find(client, label) != null) {
			return Lang.t("Waypoint o takiej nazwie już istnieje: ") + label;
		}

		this.points().add(new Point(world(client), dim(client), label, x, y, z, argb | 0xFF000000, icon));
		this.save();
		return null;
	}

	public void delete(Point point) {
		this.points().remove(point);
		this.save();
	}

	private int addHere(FabricClientCommandSource source, String name) {
		Minecraft client = Minecraft.getInstance();
		if (!this.ready(client, source)) {
			return 0;
		}

		String error = this.create(client, name, client.player.getBlockX(), client.player.getBlockY(), client.player.getBlockZ(), this.color.argb(), "");
		this.say(source, error != null ? error : Lang.t("Dodano waypoint: ") + this.here(client).getLast().name());
		return error == null ? 1 : 0;
	}

	private int remove(FabricClientCommandSource source, String name) {
		Minecraft client = Minecraft.getInstance();
		if (!this.ready(client, source)) {
			return 0;
		}

		Point found = this.find(client, name.strip());
		if (found == null) {
			this.say(source, Lang.t("Nie ma takiego waypointa: ") + name);
			return 0;
		}

		this.delete(found);
		this.say(source, Lang.t("Usunięto waypoint: ") + found.name());
		return 1;
	}

	private int list(FabricClientCommandSource source) {
		Minecraft client = Minecraft.getInstance();
		if (!this.ready(client, source)) {
			return 0;
		}

		List<Point> here = this.here(client);
		if (here.isEmpty()) {
			this.say(source, Lang.t("Brak waypointów w tym świecie."));
			return 1;
		}

		for (Point point : here) {
			this.say(source, point.name() + ": " + point.x() + " " + point.y() + " " + point.z());
		}

		return 1;
	}

	/**
	 * Rysowane razem z podświetleniem bloku, w zakresie gizmo otwartym przez {@link pl.kerf.project.render.WorldOverlays}.
	 * Zawsze widać wiązkę (i ikonę, jeśli punkt ją ma); nazwa z dystansem pojawia się, gdy patrzysz na wiązkę.
	 */
	public void submit(Minecraft client) {
		net.minecraft.client.Camera camera = client.gameRenderer.getMainCamera();
		Vec3 eye = camera.position();
		Vec3 forward = new Vec3(camera.forwardVector());
		Vec3 right = new Vec3(camera.leftVector()).scale(-1.0);
		Vec3 up = new Vec3(camera.upVector());
		float opacity = this.opacity.f() / 100.0F;
		double lookFlat = Math.sqrt(forward.x * forward.x + forward.z * forward.z);
		for (Point point : this.here(client)) {
			int argb = fade(point.color(), opacity);
			Vec3 base = new Vec3(point.x() + 0.5, point.y(), point.z() + 0.5);
			Gizmos.line(base, base.add(0.0, BEAM_HEIGHT, 0.0), argb, 3.0F).setAlwaysOnTop();
			boolean hasIcon = !point.icon().isEmpty();
			double dx = base.x - eye.x;
			double dz = base.z - eye.z;
			double flat = Math.sqrt(dx * dx + dz * dz);
			// Patrzysz na wiązkę, gdy kierunek spojrzenia (w poziomie) mija ją o mniej niż ~2,5 stopnia albo o pół bloku z bliska.
			boolean hover = flat < 0.5 || lookFlat > 1.0E-4
				&& Math.acos(Math.max(-1.0, Math.min(1.0, (dx * forward.x + dz * forward.z) / (flat * lookFlat)))) < Math.max(Math.toRadians(2.5), Math.atan(0.5 / flat));
			if (!hasIcon && !hover && !this.alwaysNames.on()) {
				continue;
			}

			// Ikona wisi nad punktem; nazwa bez ikony pojawia się na wiązce dokładnie tam, gdzie patrzysz.
			Vec3 anchor = base.add(0.0, 1.5, 0.0);
			if (!hasIcon && lookFlat > 1.0E-4) {
				double lookY = eye.y + forward.y / lookFlat * flat;
				anchor = new Vec3(base.x, Math.max(base.y + 0.5, Math.min(base.y + BEAM_HEIGHT, lookY)), base.z);
			}

			double distance = eye.distanceTo(anchor);
			Vec3 at = distance > LABEL_MAX_DISTANCE ? eye.add(anchor.subtract(eye).scale(LABEL_MAX_DISTANCE / distance)) : anchor;
			double shown = Math.max(eye.distanceTo(at), 2.0);
			// Rozmiar rośnie z odległością od kamery, więc na ekranie zostaje taki sam.
			float scale = (float) (TextGizmo.Style.DEFAULT_SCALE * this.labelSize.f() * shown / 3.0);
			// Jednostka napisu: 1 piksel czcionki = scale / 16 bloku; tekst rysuje się od punktu w dół.
			double unit = scale / 16.0;
			double pad = 2.0 * unit;
			if (hover || this.alwaysNames.on()) {
				String text = point.name() + " " + Math.round(eye.distanceTo(base)) + "m";
				double textW = client.font.width(text) * unit;
				double textH = 9.0 * unit;
				if (this.labelBackground.on()) {
					// Tło lekko za tekstem (dalej od kamery), żeby nie przykrywało liter. Kolejność wierzchołków decyduje o stronie, z której kwadrat widać.
					Vec3 c = at.add(forward.scale(0.02 * shown));
					Vec3 tl = c.add(right.scale(-textW / 2.0 - pad)).add(up.scale(pad));
					Vec3 tr = c.add(right.scale(textW / 2.0 + pad)).add(up.scale(pad));
					Vec3 br = tr.subtract(up.scale(textH + 2.0 * pad));
					Vec3 bl = tl.subtract(up.scale(textH + 2.0 * pad));
					Gizmos.rect(tl, bl, br, tr, GizmoStyle.fill(fade(this.labelBackgroundColor.argb(), opacity))).setAlwaysOnTop();
				}

				// Alfa 254 zamiast 255: tekst trafia do tej samej warstwy co półprzezroczyste tło i rysuje się po nim.
				int textColor = Math.min(argb >>> 24, 254) << 24 | argb & 0xFFFFFF;
				Gizmos.billboardText(text, at, TextGizmo.Style.forColor(textColor).withScale(scale)).setAlwaysOnTop();
			}

			if (hasIcon) {
				double iconW = ICON_WIDTH_PER_BLOCK * shown;
				Vec3 center = at.add(up.scale(pad * 2.0 + iconW / 2.0 + 0.02 * shown));
				drawIcon(PixelGrid.decode(point.icon()), center, right, up, iconW / PixelGrid.SIZE, opacity);
			}
		}
	}

	/** Mnoży kanał alfa koloru przez przezroczystość 0..1. */
	private static int fade(int argb, float opacity) {
		return Math.round((argb >>> 24) * opacity) << 24 | argb & 0xFFFFFF;
	}

	/** Ikona jako płaskie kolorowe kwadraty zwrócone do kamery; pusty piksel nic nie rysuje. */
	private static void drawIcon(int[] grid, Vec3 center, Vec3 right, Vec3 up, double cell, float opacity) {
		int n = PixelGrid.SIZE;
		for (int i = 0; i < grid.length; i++) {
			if (grid[i] >>> 24 == 0) {
				continue;
			}

			double x0 = (i % n - n / 2.0) * cell;
			double y0 = (n / 2.0 - i / n) * cell;
			Vec3 a = center.add(right.scale(x0)).add(up.scale(y0));
			Vec3 b = a.add(right.scale(cell));
			Vec3 c = b.subtract(up.scale(cell));
			Vec3 d = a.subtract(up.scale(cell));
			Gizmos.rect(a, d, c, b, GizmoStyle.fill(fade(grid[i], opacity))).setAlwaysOnTop();
		}
	}

	private boolean ready(Minecraft client, FabricClientCommandSource source) {
		if (!this.on()) {
			this.say(source, Lang.t("Włącz moduł Waypoints w menu moda."));
			return false;
		}

		return client.player != null && client.level != null;
	}

	/** Z klawisza nie ma źródła komendy, więc odpowiedź idzie przez czat gry. */
	private void say(FabricClientCommandSource source, String message) {
		if (source != null) {
			source.sendFeedback(Component.literal(message));
		} else {
			Minecraft.getInstance().gui.getChat().addClientSystemMessage(Component.literal(message));
		}
	}

	private Point find(Minecraft client, String name) {
		return this.here(client).stream().filter(point -> point.name().equalsIgnoreCase(name)).findFirst().orElse(null);
	}

	private static String world(Minecraft client) {
		ServerData server = client.getCurrentServer();
		if (server != null) {
			return server.ip;
		}

		return client.getSingleplayerServer() != null ? "sp:" + client.getSingleplayerServer().getWorldData().getLevelName() : "?";
	}

	private static String dim(Minecraft client) {
		return client.level.dimension().identifier().toString();
	}

	private List<Point> points() {
		if (this.points == null) {
			this.points = new ArrayList<>();
			try {
				for (JsonElement element : JsonParser.parseString(this.data.get()).getAsJsonArray()) {
					JsonObject o = element.getAsJsonObject();
					// Punkty z 1.0.31 nie mają koloru ani ikony: dostają domyślny kolor, bez ikony.
					this.points.add(new Point(o.get("w").getAsString(), o.get("d").getAsString(), o.get("n").getAsString(),
						o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt(),
						o.has("c") ? o.get("c").getAsInt() : 0xFF55FFFF, o.has("i") ? o.get("i").getAsString() : ""));
				}
			} catch (RuntimeException e) {
				// Uszkodzone dane to pusta lista, nie crash; następny zapis je nadpisze.
				this.points.clear();
			}
		}

		return this.points;
	}

	private void save() {
		JsonArray array = new JsonArray();
		for (Point point : this.points) {
			JsonObject o = new JsonObject();
			o.addProperty("w", point.world());
			o.addProperty("d", point.dim());
			o.addProperty("n", point.name());
			o.addProperty("x", point.x());
			o.addProperty("y", point.y());
			o.addProperty("z", point.z());
			o.addProperty("c", point.color());
			o.addProperty("i", point.icon());
			array.add(o);
		}

		this.data.set(array.toString());
		ConfigManager.save();
	}
}
