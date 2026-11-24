package pl.kerf.project.render;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import pl.kerf.project.module.Modules;
import pl.kerf.project.module.Modules.BlockOutline;

/**
 * Hitboxy i podświetlenie bloku rysowane przez vanilla'owy system gizmo (ten sam, którego używa F3+B),
 * więc bez własnych pipeline'ów renderowania.
 */
public final class WorldOverlays {
	/** Odsunięcie od ściany bloku, żeby wypełnienie nie migotało (z-fighting). */
	private static final double EPSILON = 0.002;

	private WorldOverlays() {
	}

	/** Wywoływane raz na klatkę, zanim renderer świata zbierze gizmo. */
	public static void submit(Minecraft client) {
		if (client.level == null || client.player == null || !(Modules.BLOCK_OUTLINE.on() || Modules.WAYPOINTS.on() || Modules.MARKERS.on() && Modules.MARKERS.beacon.on())) {
			return;
		}

		try (Gizmos.TemporaryCollection scope = client.levelRenderer.collectPerFrameGizmos()) {
			if (Modules.BLOCK_OUTLINE.on()) {
				submitBlockOutline(client);
			}

			if (Modules.WAYPOINTS.on()) {
				Modules.WAYPOINTS.submit(client);
			}

			if (Modules.MARKERS.on() && Modules.MARKERS.beacon.on()) {
				Modules.MARKERS.submit(client);
			}
		}
	}

	/**
	 * Hitboxy w miejsce vanillowych z F3+B. Wywoływane z renderera debug, więc pokazują się tylko wtedy,
	 * gdy gracz włączył hitboxy skrótem F3+B.
	 */
	public static void drawHitboxes(Minecraft client, float tickProgress) {
		if (client.level == null) {
			return;
		}

		// Własny zakres na ten sam kolektor renderera świata: działa też w klatkach renderowanych poza główną pętlą.
		try (Gizmos.TemporaryCollection scope = client.levelRenderer.collectPerFrameGizmos()) {
			submitHitboxes(client, tickProgress);
		}
	}

	private static void submitHitboxes(Minecraft client, float tickProgress) {
		Modules.Hitboxes module = Modules.HITBOXES;
		float width = module.width.f();
		Entity target = client.crosshairPickEntity;
		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity.isInvisible()) {
				continue;
			}

			if (entity == client.getCameraEntity() && client.options.getCameraType() == CameraType.FIRST_PERSON) {
				continue;
			}

			// Grupa encji decyduje, czy hitbox w ogóle jest rysowany; cel celownika dostaje tylko inny kolor.
			boolean enabled;
			int color;
			if (entity instanceof Avatar) {
				enabled = module.players.on();
				color = module.playerColor.argb();
			} else if (entity instanceof Enemy) {
				enabled = module.hostile.on();
				color = module.hostileColor.argb();
			} else if (entity instanceof LivingEntity) {
				enabled = module.mobs.on();
				color = module.mobColor.argb();
			} else {
				enabled = module.others.on();
				color = module.otherColor.argb();
			}

			if (!enabled) {
				continue;
			}

			if (entity == target) {
				color = module.targetColor.argb();
			}

			// Hitbox w pozycji interpolowanej, żeby nie "skakał" między tickami.
			Vec3 offset = entity.getPosition(tickProgress).subtract(entity.position());
			AABB box = entity.getBoundingBox().move(offset);
			Gizmos.cuboid(box, GizmoStyle.stroke(color, width));
			if (module.eyes.on() && entity instanceof LivingEntity) {
				double eye = box.minY + entity.getEyeHeight();
				Gizmos.cuboid(new AABB(box.minX, eye - 0.01, box.minZ, box.maxX, eye + 0.01, box.maxZ), GizmoStyle.stroke(0xFFFF0000));
				Vec3 eyePos = entity.getPosition(tickProgress).add(0.0, entity.getEyeHeight(), 0.0);
				Gizmos.arrow(eyePos, eyePos.add(entity.getViewVector(tickProgress).scale(2.0)), 0xFF0000FF);
			}
		}
	}

	private static void submitBlockOutline(Minecraft client) {
		if (client.options.hideGui || !(client.hitResult instanceof BlockHitResult hit) || hit.getType() == HitResult.Type.MISS) {
			return;
		}

		BlockPos pos = hit.getBlockPos();
		BlockState state = client.level.getBlockState(pos);
		if (state.isAir() || !client.level.getWorldBorder().isWithinBounds(pos)) {
			return;
		}

		Entity camera = client.getCameraEntity();
		VoxelShape shape = state.getShape(client.level, pos, camera == null ? CollisionContext.empty() : CollisionContext.of(camera));
		if (shape.isEmpty()) {
			return;
		}

		BlockOutline module = Modules.BLOCK_OUTLINE;
		int color = module.color.argb();
		boolean fill = module.fillEnabled.on() && !module.mode.is(BlockOutline.MODE_OUTLINE);
		int fillColor = module.fill.argb();
		float width = module.width.f();
		double x = pos.getX();
		double y = pos.getY();
		double z = pos.getZ();
		if (module.mode.is(BlockOutline.MODE_FACE)) {
			AABB box = shape.bounds().move(pos).inflate(EPSILON);
			GizmoStyle style = fill ? GizmoStyle.strokeAndFill(color, width, fillColor) : GizmoStyle.stroke(color, width);
			Gizmos.rect(new Vec3(box.minX, box.minY, box.minZ), new Vec3(box.maxX, box.maxY, box.maxZ), hit.getDirection(), style);
			return;
		}

		// Krawędzie kształtu, tak jak vanilla: schody czy płotki nie dostają wewnętrznych linii.
		shape.forAllEdges((minX, minY, minZ, maxX, maxY, maxZ) -> Gizmos.line(
			new Vec3(x + minX, y + minY, z + minZ), new Vec3(x + maxX, y + maxY, z + maxZ), color, width
		));
		if (fill) {
			shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> Gizmos.cuboid(
				new AABB(x + minX, y + minY, z + minZ, x + maxX, y + maxY, z + maxZ).inflate(EPSILON), GizmoStyle.fill(fillColor)
			));
		}
	}
}
