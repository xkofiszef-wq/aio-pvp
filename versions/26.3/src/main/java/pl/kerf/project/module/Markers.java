package pl.kerf.project.module;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.world.level.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import pl.kerf.project.config.Setting;

/**
 * Markery: środkowy przycisk myszy stawia znacznik w miejscu, na które patrzysz (z ikoną bloku, dystansem i
 * współrzędnymi); na mobie marker przyczepia się do niego i jedzie razem z nim. Znaczniki widzą też osoby z party
 * ({@link Party}), jeśli moduł Party jest włączony.
 */
public final class Markers extends Module {
	/** Klawisz stawiania markera (Opcje -> Sterowanie, domyślnie środkowy przycisk myszy). */
	public static KeyMapping key;
	private static final int MAX_PER_OWNER = 3;

	public final Setting.Num scale = this.add(new Setting.Num("scale", "Skala", 1.0, 0.5, 4.0, 0.1, "x"));
	public final Setting.Num duration = this.add(new Setting.Num("duration", "Czas widoczności", 120.0, 10.0, 600.0, 10.0, " s"));
	public final Setting.Bool animate = this.add(new Setting.Bool("animate", "Animuj marker", true));
	public final Setting.Color color = this.add(new Setting.Color("color", "Kolor markera", 0xFF5EC8FF, false));
	public final Setting.Bool showBlock = this.add(new Setting.Bool("show_block", "Pokaż blok obok markera", true));
	public final Setting.Mode showOwner = this.add(new Setting.Mode("show_owner", "Pokaż autora", 0, "Zawsze", "Po najechaniu", "Nigdy"));
	public final Setting.Mode showDescription = this.add(new Setting.Mode("show_description", "Pokaż nazwę bloku", 1, "Zawsze", "Po najechaniu", "Nigdy"));
	public final Setting.Mode showDistance = this.add(new Setting.Mode("show_distance", "Pokaż dystans", 1, "Zawsze", "Po najechaniu", "Nigdy"));
	public final Setting.Mode showCoordinates = this.add(new Setting.Mode("show_coordinates", "Pokaż współrzędne", 1, "Zawsze", "Po najechaniu", "Nigdy"));
	public final Setting.Str ownerSuffix = this.add(new Setting.Str("owner_suffix", "Dopisek autora", "'s Marker"));

	/**
	 * Marker w świecie. kind = id bloku albo (gdy uuid != null) id typu encji. Marker na encji jedzie za nią:
	 * znika, gdy encja przestaje istnieć (zginęła, wybuchła, zniknęła) po tym, jak już ją widzieliśmy.
	 */
	private static final class Marker {
		final String owner;
		final String dim;
		final String kind;
		final UUID uuid;
		final long born;
		Vec3 pos;
		Entity entity;
		boolean seen;

		Marker(String owner, Vec3 pos, String dim, String kind, UUID uuid, long born) {
			this.owner = owner;
			this.pos = pos;
			this.dim = dim;
			this.kind = kind;
			this.uuid = uuid;
			this.born = born;
		}
	}

	private final List<Marker> markers = new ArrayList<>();
	private long lastPing;

	public Markers() {
		super("markers", "Markers", "Środkowy przycisk myszy stawia marker w miejscu (albo na mobie), na które patrzysz; widzą go też osoby z party (moduł Party). Wszystko po stronie klienta.", Category.MISC, false);
		this.preview = false;
	}

	public int markerCount() {
		return this.markers.size();
	}

	/** Marker od członka party (wiadomość {@code m x y z wymiar rodzaj [uuid]}); zwraca false, gdy wiadomość jest zepsuta. */
	public boolean receive(String owner, String[] p) {
		if (p.length < 6) {
			return false;
		}

		try {
			Vec3 pos = new Vec3(Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]));
			// Siódme pole (opcjonalne) to UUID encji, do której marker jest przyczepiony.
			UUID uuid = p.length > 6 && !p[6].equals("-") ? UUID.fromString(p[6]) : null;
			this.addMarker(new Marker(owner, pos, p[4], p[5], uuid, System.currentTimeMillis()));
			return true;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	private void addMarker(Marker marker) {
		// Ten sam marker tego samego autora (ten sam cel albo punkt prawie w tym samym miejscu) odświeża poprzedni zamiast dublować się,
		// np. gdy ta sama wiadomość dojdzie dwiema drogami.
		// Nick porównujemy bez względu na wielkość liter; ten sam cel w promieniu 2 bloków to ten sam marker. Marker na tym samym mobie
		// od tego samego autora zawsze zastępuje poprzedni (mob się rusza). Dubel od innej osoby w ciągu 5 s też się scala.
		this.markers.removeIf(m -> {
			boolean sameOwner = m.owner.equalsIgnoreCase(marker.owner);
			if (sameOwner && marker.uuid != null && marker.uuid.equals(m.uuid)) {
				return true;
			}

			boolean near = m.kind.equals(marker.kind) && java.util.Objects.equals(m.uuid, marker.uuid) && m.pos.distanceToSqr(marker.pos) < 4.0;
			return near && (sameOwner || marker.born - m.born < 5000L);
		});
		List<Marker> mine = this.markers.stream().filter(m -> m.owner.equalsIgnoreCase(marker.owner)).toList();
		if (mine.size() >= MAX_PER_OWNER) {
			this.markers.remove(mine.get(0));
		}

		this.markers.add(marker);
	}

	// ---------- stawianie markera ----------

	/** Klawisz i wygasanie; wołane co tick. */
	public void tick(Minecraft client) {
		if (client.level == null || client.player == null) {
			this.markers.clear();
			return;
		}

		while (key != null && key.consumeClick()) {
			if (this.on() && client.gui.screen() == null) {
				this.ping(client);
			}
		}

		long now = System.currentTimeMillis();
		this.markers.removeIf(marker -> now - marker.born > this.duration.i() * 1000L);
	}

	private void ping(Minecraft client) {
		long now = System.currentTimeMillis();
		if (now - this.lastPing < 500L) {
			return;
		}

		float tickProgress = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		HitResult hit = client.player.pick(256.0, tickProgress, false);
		// Encja na linii wzroku (bliżej niż trafiony blok) wygrywa: marker przyczepia się do niej i jedzie razem z nią.
		Vec3 start = client.player.getEyePosition(tickProgress);
		Vec3 look = client.player.getViewVector(tickProgress);
		Vec3 end = start.add(look.scale(256.0));
		double blockDistance = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().distanceToSqr(start) : 256.0 * 256.0;
		EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(client.player, start, end, client.player.getBoundingBox().expandTowards(look.scale(256.0)).inflate(1.0),
			entity -> !entity.isSpectator() && entity.isPickable(), blockDistance);
		String dim = client.level.dimension().identifier().getPath();
		String me = client.player.getName().getString();
		if (entityHit != null) {
			Entity target = entityHit.getEntity();
			this.lastPing = now;
			String type = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString();
			Vec3 pos = target.getPosition(tickProgress).add(0.0, target.getBbHeight() + 0.6, 0.0);
			Marker marker = new Marker(me, pos, dim, type, target.getUUID(), now);
			marker.entity = target;
			marker.seen = true;
			this.addMarker(marker);
			Modules.PARTY.broadcast(String.format(Locale.ROOT, "m %.1f %.1f %.1f %s %s %s", pos.x, pos.y, pos.z, dim, type, target.getUUID()));
			return;
		}

		if (hit.getType() != HitResult.Type.BLOCK) {
			return;
		}

		this.lastPing = now;
		BlockHitResult blockHit = (BlockHitResult) hit;
		BlockPos blockPos = blockHit.getBlockPos();
		Block block = client.level.getBlockState(blockPos).getBlock();
		String blockId = BuiltInRegistries.BLOCK.getKey(block).toString();
		// Znacznik tuż nad trafioną ścianą, żeby nie wchodził w blok.
		Vec3 pos = blockHit.getLocation().add(Vec3.atLowerCornerOf(blockHit.getDirection().getUnitVec3i()).scale(0.3));
		this.addMarker(new Marker(me, pos, dim, blockId, null, now));
		Modules.PARTY.broadcast(String.format(Locale.ROOT, "m %.1f %.1f %.1f %s %s -", pos.x, pos.y, pos.z, dim, blockId));
	}

	/** Ustawia pozycję markera z encji; false = encja zniknęła i marker ma zniknąć. */
	private static boolean follow(Minecraft client, Marker marker, float tickProgress) {
		if (marker.uuid == null) {
			return true;
		}

		if (marker.entity == null || marker.entity.isRemoved()) {
			Entity found = null;
			for (Entity entity : client.level.entitiesForRendering()) {
				if (entity.getUUID().equals(marker.uuid)) {
					found = entity;
					break;
				}
			}

			if (found == null) {
				// Jeszcze jej nie widzieliśmy (poza zasięgiem): marker stoi na miejscu z wiadomości; po zniknięciu widzianej encji znika.
				return !marker.seen;
			}

			marker.entity = found;
			marker.seen = true;
		}

		if (!marker.entity.isAlive()) {
			return false;
		}

		marker.pos = marker.entity.getPosition(tickProgress).add(0.0, marker.entity.getBbHeight() + 0.6, 0.0);
		return true;
	}

	// ---------- rysowanie ----------

	/** Rysuje znaczniki na HUD-zie: pozycja świata jest rzutowana na ekran, a ikona bloku i napisy rysują się zwykłym GuiGraphicsExtractor. */
	public void render(GuiGraphicsExtractor context, Minecraft client) {
		if (!this.on() || client.player == null || client.level == null || this.markers.isEmpty()) {
			return;
		}

		Camera camera = client.gameRenderer.mainCamera();
		Vec3 eye = camera.position();
		Vec3 forward = new Vec3(camera.forwardVector());
		Vec3 right = new Vec3(camera.leftVector()).scale(-1.0);
		Vec3 up = new Vec3(camera.upVector());
		int sw = context.guiWidth();
		int sh = context.guiHeight();
		// Prawdziwy kąt widzenia z tej klatki (z zoomem z innych modów i efektami sprintu), nie sam suwak FOV z opcji.
		double fov = camera.getFov();
		double tanY = Math.tan(Math.toRadians(fov) / 2.0);
		double tanX = tanY * sw / sh;
		String dim = client.level.dimension().identifier().getPath();
		float tickProgress = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		this.markers.removeIf(marker -> !follow(client, marker, tickProgress));
		for (Marker marker : this.markers) {
			if (!marker.dim.equals(dim)) {
				continue;
			}

			Vec3 d = marker.pos.subtract(eye);
			double z = d.dot(forward);
			if (z < 0.05) {
				continue;
			}

			double x = sw / 2.0 + d.dot(right) / z / tanX * sw / 2.0;
			double y = sh / 2.0 - d.dot(up) / z / tanY * sh / 2.0;
			if (x < -60 || x > sw + 60 || y < -60 || y > sh + 60) {
				continue;
			}

			this.draw(context, client, marker, (float) x, (float) y, d.length(), Math.hypot(x - sw / 2.0, y - sh / 2.0) < 16.0 * this.scale.f());
		}
	}

	private static boolean shown(Setting.Mode mode, boolean hover) {
		return mode.is(0) || mode.is(1) && hover;
	}

	private void draw(GuiGraphicsExtractor context, Minecraft client, Marker marker, float x, float y, double distance, boolean hover) {
		Font font = client.font;
		int bob = this.animate.on() ? (int) Math.round(Math.sin(System.currentTimeMillis() / 250.0) * 2.0) : 0;
		context.pose().pushMatrix();
		context.pose().translate(x, y + bob);
		context.pose().scale(this.scale.f(), this.scale.f());
		int argb = this.color.argb();
		// Romb jak w Lunarze: rząd po rzędzie.
		for (int i = -5; i <= 5; i++) {
			int half = 5 - Math.abs(i);
			context.fill(-half, i, half + 1, i + 1, argb);
		}

		// Opis i ikona: dla bloku jego nazwa i item, dla encji nazwa typu i jajko spawnu (jeśli takie istnieje).
		String description;
		ItemStack stack;
		if (marker.uuid != null) {
			Identifier typeId = Identifier.tryParse(marker.kind);
			description = typeId == null ? "" : BuiltInRegistries.ENTITY_TYPE.getValue(typeId).getDescription().getString();
			stack = typeId == null ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(typeId.getNamespace(), typeId.getPath() + "_spawn_egg")));
		} else {
			Identifier blockId = Identifier.tryParse(marker.kind);
			Block block = blockId == null ? net.minecraft.world.level.block.Blocks.AIR : BuiltInRegistries.BLOCK.getValue(blockId);
			description = block.getName().getString();
			stack = new ItemStack(block);
		}

		int line = -14;
		if (shown(this.showCoordinates, hover)) {
			String coords = String.format(Locale.ROOT, "(%.1f, %.1f, %.1f)", marker.pos.x, marker.pos.y, marker.pos.z);
			line -= 10;
			context.text(font, coords, -font.width(coords) / 2, line, 0xFFFFFFFF, true);
		}

		String info = (shown(this.showDescription, hover) ? description + " " : "")
			+ (shown(this.showDistance, hover) ? String.format(Locale.ROOT, "(%.1fm)", distance) : "");
		boolean icon = this.showBlock.on() && !stack.isEmpty();
		if (!info.isBlank() || icon) {
			line -= 12;
			int width = font.width(info) + (icon ? 14 : 0);
			int left = -width / 2;
			if (icon) {
				context.pose().pushMatrix();
				context.pose().translate(left, line - 1);
				context.pose().scale(0.75F, 0.75F);
				context.item(stack, 0, 0);
				context.pose().popMatrix();
				left += 14;
			}

			context.text(font, info, left, line, 0xFFFFFFFF, true);
		}

		if (shown(this.showOwner, hover)) {
			String owner = marker.owner + this.ownerSuffix.get();
			line -= 11;
			context.text(font, owner, -font.width(owner) / 2, line, 0xFFFFFFFF, true);
		}

		context.pose().popMatrix();
	}
}
