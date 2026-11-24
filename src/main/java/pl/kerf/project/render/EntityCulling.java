package pl.kerf.project.render;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;

/**
 * Czy encja jest całkowicie zasłonięta pełnymi blokami (od kamery do trzech punktów encji: środek, góra, dół).
 * Wynik trzyma kilka ticków, bo promień na każdą encję w każdej klatce byłby droższy niż to, co oszczędza.
 */
public final class EntityCulling {
	private static final int CACHE_TICKS = 3;
	/** Bliżej niż tyle bloków encja jest zawsze rysowana (zasłanianie z bliska łatwo zepsuć). */
	private static final double MIN_DISTANCE_SQ = 6.0 * 6.0;
	/** Encje większe niż tyle bloków (np. smoki, wither) mają zbyt wiele punktów widzenia na trzy promienie. */
	private static final double MAX_SIZE = 3.0;
	private static final Map<Entity, long[]> CACHE = new WeakHashMap<>();

	private EntityCulling() {
	}

	public static boolean hidden(Entity entity) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientWorld world = client.world;
		Entity camera = client.getCameraEntity();
		if (world == null || camera == null || entity == camera || entity.isGlowing() || entity.hasPassengers() || entity.hasVehicle()) {
			return false;
		}

		Box box = entity.getBoundingBox();
		if (box.getAverageSideLength() > MAX_SIZE) {
			return false;
		}

		Vec3d eye = client.gameRenderer.getCamera().getCameraPos();
		if (box.getCenter().squaredDistanceTo(eye) < MIN_DISTANCE_SQ) {
			return false;
		}

		long now = world.getTime();
		long[] cached = CACHE.get(entity);
		if (cached != null && now - cached[0] < CACHE_TICKS) {
			return cached[1] == 1L;
		}

		Vec3d center = box.getCenter();
		boolean visible = clear(world, eye, center) || clear(world, eye, new Vec3d(center.x, box.maxY, center.z)) || clear(world, eye, new Vec3d(center.x, box.minY + 0.1, center.z));
		CACHE.put(entity, new long[] {now, visible ? 0L : 1L});
		return !visible;
	}

	/** Czy między kamerą a punktem nie ma pełnego, nieprzezroczystego bloku. */
	private static boolean clear(ClientWorld world, Vec3d from, Vec3d to) {
		return BlockView.raycast(from, to, world, (view, pos) -> view.getBlockState(pos).isOpaqueFullCube() ? Boolean.FALSE : null, view -> Boolean.TRUE);
	}
}
