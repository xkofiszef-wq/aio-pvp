package pl.kerf.project.module;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import pl.kerf.project.config.Setting;

/**
 * Markery: środkowy przycisk myszy stawia znacznik w miejscu, na które patrzysz (z ikoną bloku, dystansem i
 * współrzędnymi); na mobie marker przyczepia się do niego i jedzie razem z nim. Znaczniki widzą też osoby z party
 * ({@link Party}), jeśli moduł Party jest włączony.
 */
public final class Markers extends Module {
	/** Klawisz stawiania markera (Opcje -> Sterowanie, domyślnie środkowy przycisk myszy). */
	public static KeyBinding key;
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
		Vec3d pos;
		Entity entity;
		boolean seen;

		Marker(String owner, Vec3d pos, String dim, String kind, UUID uuid, long born) {
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
			Vec3d pos = new Vec3d(Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]));
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

			boolean near = m.kind.equals(marker.kind) && java.util.Objects.equals(m.uuid, marker.uuid) && m.pos.squaredDistanceTo(marker.pos) < 4.0;
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
	public void tick(MinecraftClient client) {
		if (client.world == null || client.player == null) {
			this.markers.clear();
			return;
		}

		while (key != null && key.wasPressed()) {
			if (this.on() && client.currentScreen == null) {
				this.ping(client);
			}
		}

		long now = System.currentTimeMillis();
		this.markers.removeIf(marker -> now - marker.born > this.duration.i() * 1000L);
	}

	private void ping(MinecraftClient client) {
		long now = System.currentTimeMillis();
		if (now - this.lastPing < 500L) {
			return;
		}

		float tickProgress = client.getRenderTickCounter().getTickProgress(false);
		HitResult hit = client.player.raycast(256.0, tickProgress, false);
		// Encja na linii wzroku (bliżej niż trafiony blok) wygrywa: marker przyczepia się do niej i jedzie razem z nią.
		Vec3d start = client.player.getCameraPosVec(tickProgress);
		Vec3d look = client.player.getRotationVec(tickProgress);
		Vec3d end = start.add(look.multiply(256.0));
		double blockDistance = hit.getType() == HitResult.Type.BLOCK ? hit.getPos().squaredDistanceTo(start) : 256.0 * 256.0;
		EntityHitResult entityHit = ProjectileUtil.raycast(client.player, start, end, client.player.getBoundingBox().stretch(look.multiply(256.0)).expand(1.0),
			entity -> !entity.isSpectator() && entity.canHit(), blockDistance);
		String dim = client.world.getRegistryKey().getValue().getPath();
		String me = client.player.getName().getString();
		if (entityHit != null) {
			Entity target = entityHit.getEntity();
			this.lastPing = now;
			String type = Registries.ENTITY_TYPE.getId(target.getType()).toString();
			Vec3d pos = target.getLerpedPos(tickProgress).add(0.0, target.getHeight() + 0.6, 0.0);
			Marker marker = new Marker(me, pos, dim, type, target.getUuid(), now);
			marker.entity = target;
			marker.seen = true;
			this.addMarker(marker);
			Modules.PARTY.broadcast(String.format(Locale.ROOT, "m %.1f %.1f %.1f %s %s %s", pos.x, pos.y, pos.z, dim, type, target.getUuid()));
			return;
		}

		if (hit.getType() != HitResult.Type.BLOCK) {
			return;
		}

		this.lastPing = now;
		BlockHitResult blockHit = (BlockHitResult) hit;
		BlockPos blockPos = blockHit.getBlockPos();
		Block block = client.world.getBlockState(blockPos).getBlock();
		String blockId = Registries.BLOCK.getId(block).toString();
		// Znacznik tuż nad trafioną ścianą, żeby nie wchodził w blok.
		Vec3d pos = blockHit.getPos().add(Vec3d.of(blockHit.getSide().getVector()).multiply(0.3));
		this.addMarker(new Marker(me, pos, dim, blockId, null, now));
		Modules.PARTY.broadcast(String.format(Locale.ROOT, "m %.1f %.1f %.1f %s %s -", pos.x, pos.y, pos.z, dim, blockId));
	}

	/** Ustawia pozycję markera z encji; false = encja zniknęła i marker ma zniknąć. */
	private static boolean follow(MinecraftClient client, Marker marker, float tickProgress) {
		if (marker.uuid == null) {
			return true;
		}

		if (marker.entity == null || marker.entity.isRemoved()) {
			Entity found = null;
			for (Entity entity : client.world.getEntities()) {
				if (entity.getUuid().equals(marker.uuid)) {
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

		marker.pos = marker.entity.getLerpedPos(tickProgress).add(0.0, marker.entity.getHeight() + 0.6, 0.0);
		return true;
	}

	// ---------- rysowanie ----------

	/** Rysuje znaczniki na HUD-zie: pozycja świata jest rzutowana na ekran, a ikona bloku i napisy rysują się zwykłym DrawContext. */
	public void render(DrawContext context, MinecraftClient client) {
		if (!this.on() || client.player == null || client.world == null || this.markers.isEmpty()) {
			return;
		}

		Camera camera = client.gameRenderer.getCamera();
		Vec3d eye = camera.getCameraPos();
		Vec3d forward = new Vec3d(camera.getHorizontalPlane());
		Vec3d right = new Vec3d(camera.getDiagonalPlane()).multiply(-1.0);
		Vec3d up = new Vec3d(camera.getVerticalPlane());
		int sw = context.getScaledWindowWidth();
		int sh = context.getScaledWindowHeight();
		// Prawdziwy kąt widzenia z tej klatki (z zoomem z innych modów i efektami sprintu), nie sam suwak FOV z opcji.
		double fov = ((pl.kerf.project.mixin.GameRendererInvoker) client.gameRenderer).aio$getFov(camera, client.getRenderTickCounter().getTickProgress(false), true);
		double tanY = Math.tan(Math.toRadians(fov) / 2.0);
		double tanX = tanY * sw / sh;
		String dim = client.world.getRegistryKey().getValue().getPath();
		float tickProgress = client.getRenderTickCounter().getTickProgress(false);
		this.markers.removeIf(marker -> !follow(client, marker, tickProgress));
		for (Marker marker : this.markers) {
			if (!marker.dim.equals(dim)) {
				continue;
			}

			Vec3d d = marker.pos.subtract(eye);
			double z = d.dotProduct(forward);
			if (z < 0.05) {
				continue;
			}

			double x = sw / 2.0 + d.dotProduct(right) / z / tanX * sw / 2.0;
			double y = sh / 2.0 - d.dotProduct(up) / z / tanY * sh / 2.0;
			if (x < -60 || x > sw + 60 || y < -60 || y > sh + 60) {
				continue;
			}

			this.draw(context, client, marker, (float) x, (float) y, d.length(), Math.hypot(x - sw / 2.0, y - sh / 2.0) < 16.0 * this.scale.f());
		}
	}

	private static boolean shown(Setting.Mode mode, boolean hover) {
		return mode.is(0) || mode.is(1) && hover;
	}

	private void draw(DrawContext context, MinecraftClient client, Marker marker, float x, float y, double distance, boolean hover) {
		TextRenderer font = client.textRenderer;
		int bob = this.animate.on() ? (int) Math.round(Math.sin(System.currentTimeMillis() / 250.0) * 2.0) : 0;
		context.getMatrices().pushMatrix();
		context.getMatrices().translate(x, y + bob);
		context.getMatrices().scale(this.scale.f(), this.scale.f());
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
			description = typeId == null ? "" : Registries.ENTITY_TYPE.get(typeId).getName().getString();
			stack = typeId == null ? ItemStack.EMPTY : new ItemStack(Registries.ITEM.get(Identifier.of(typeId.getNamespace(), typeId.getPath() + "_spawn_egg")));
		} else {
			Identifier blockId = Identifier.tryParse(marker.kind);
			Block block = blockId == null ? net.minecraft.block.Blocks.AIR : Registries.BLOCK.get(blockId);
			description = block.getName().getString();
			stack = new ItemStack(block);
		}

		int line = -14;
		if (shown(this.showCoordinates, hover)) {
			String coords = String.format(Locale.ROOT, "(%.1f, %.1f, %.1f)", marker.pos.x, marker.pos.y, marker.pos.z);
			line -= 10;
			context.drawText(font, coords, -font.getWidth(coords) / 2, line, 0xFFFFFFFF, true);
		}

		String info = (shown(this.showDescription, hover) ? description + " " : "")
			+ (shown(this.showDistance, hover) ? String.format(Locale.ROOT, "(%.1fm)", distance) : "");
		boolean icon = this.showBlock.on() && !stack.isEmpty() && !stack.isOf(Items.AIR);
		if (!info.isBlank() || icon) {
			line -= 12;
			int width = font.getWidth(info) + (icon ? 14 : 0);
			int left = -width / 2;
			if (icon) {
				context.getMatrices().pushMatrix();
				context.getMatrices().translate(left, line - 1);
				context.getMatrices().scale(0.75F, 0.75F);
				context.drawItem(stack, 0, 0);
				context.getMatrices().popMatrix();
				left += 14;
			}

			context.drawText(font, info, left, line, 0xFFFFFFFF, true);
		}

		if (shown(this.showOwner, hover)) {
			String owner = marker.owner + this.ownerSuffix.get();
			line -= 11;
			context.drawText(font, owner, -font.getWidth(owner) / 2, line, 0xFFFFFFFF, true);
		}

		context.getMatrices().popMatrix();
	}
}
