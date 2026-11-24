package pl.kerf.project.render;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.BlockGetter;

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
		Minecraft client = Minecraft.getInstance();
		ClientLevel world = client.level;
		Entity camera = client.getCameraEntity();
		if (world == null || camera == null || entity == camera || entity.isCurrentlyGlowing() || entity.isVehicle() || entity.isPassenger()) {
			return false;
		}

		AABB box = entity.getBoundingBox();
		if (box.getSize() > MAX_SIZE) {
			return false;
		}

		Vec3 eye = client.gameRenderer.getMainCamera().position();
		if (box.getCenter().distanceToSqr(eye) < MIN_DISTANCE_SQ) {
			return false;
		}

		long now = world.getGameTime();
		long[] cached = CACHE.get(entity);
		if (cached != null && now - cached[0] < CACHE_TICKS) {
			return cached[1] == 1L;
		}

		Vec3 center = box.getCenter();
		boolean visible = clear(world, eye, center) || clear(world, eye, new Vec3(center.x, box.maxY, center.z)) || clear(world, eye, new Vec3(center.x, box.minY + 0.1, center.z));
		CACHE.put(entity, new long[] {now, visible ? 0L : 1L});
		return !visible;
	}

	/** Czy między kamerą a punktem nie ma pełnego, nieprzezroczystego bloku. */
	private static boolean clear(ClientLevel world, Vec3 from, Vec3 to) {
		return BlockGetter.traverseBlocks(from, to, world, (view, pos) -> view.getBlockState(pos).isSolidRender() ? Boolean.FALSE : null, view -> Boolean.TRUE);
	}
}
