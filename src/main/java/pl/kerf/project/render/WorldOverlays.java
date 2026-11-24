package pl.kerf.project.render;

import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
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
	public static void submit(MinecraftClient client) {
		if (client.world == null || client.player == null || !(Modules.BLOCK_OUTLINE.on() || Modules.WAYPOINTS.on() || Modules.MARKERS.on() && Modules.MARKERS.beacon.on())) {
			return;
		}

		try (GizmoDrawing.CollectorScope scope = client.worldRenderer.startDrawingGizmos()) {
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
	public static void drawHitboxes(MinecraftClient client, float tickProgress) {
		if (client.world == null) {
			return;
		}

		// Własny zakres na ten sam kolektor renderera świata: działa też w klatkach renderowanych poza główną pętlą.
		try (GizmoDrawing.CollectorScope scope = client.worldRenderer.startDrawingGizmos()) {
			submitHitboxes(client, tickProgress);
		}
	}

	private static void submitHitboxes(MinecraftClient client, float tickProgress) {
		Modules.Hitboxes module = Modules.HITBOXES;
		float width = module.width.f();
		Entity target = client.targetedEntity;
		for (Entity entity : client.world.getEntities()) {
			if (entity.isInvisible()) {
				continue;
			}

			if (entity == client.getCameraEntity() && client.options.getPerspective() == Perspective.FIRST_PERSON) {
				continue;
			}

			// Grupa encji decyduje, czy hitbox w ogóle jest rysowany; cel celownika dostaje tylko inny kolor.
			boolean enabled;
			int color;
			if (entity instanceof PlayerLikeEntity) {
				enabled = module.players.on();
				color = module.playerColor.argb();
			} else if (entity instanceof Monster) {
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
			Vec3d offset = entity.getLerpedPos(tickProgress).subtract(entity.getEntityPos());
			Box box = entity.getBoundingBox().offset(offset);
			GizmoDrawing.box(box, DrawStyle.stroked(color, width));
			if (module.eyes.on() && entity instanceof LivingEntity) {
				double eye = box.minY + entity.getStandingEyeHeight();
				GizmoDrawing.box(new Box(box.minX, eye - 0.01, box.minZ, box.maxX, eye + 0.01, box.maxZ), DrawStyle.stroked(0xFFFF0000));
				Vec3d eyePos = entity.getLerpedPos(tickProgress).add(0.0, entity.getStandingEyeHeight(), 0.0);
				GizmoDrawing.arrow(eyePos, eyePos.add(entity.getRotationVec(tickProgress).multiply(2.0)), 0xFF0000FF);
			}
		}
	}

	private static void submitBlockOutline(MinecraftClient client) {
		if (client.options.hudHidden || !(client.crosshairTarget instanceof BlockHitResult hit) || hit.getType() == HitResult.Type.MISS) {
			return;
		}

		BlockPos pos = hit.getBlockPos();
		BlockState state = client.world.getBlockState(pos);
		if (state.isAir() || !client.world.getWorldBorder().contains(pos)) {
			return;
		}

		Entity camera = client.getCameraEntity();
		VoxelShape shape = state.getOutlineShape(client.world, pos, camera == null ? ShapeContext.absent() : ShapeContext.of(camera));
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
			Box box = shape.getBoundingBox().offset(pos).expand(EPSILON);
			DrawStyle style = fill ? DrawStyle.filledAndStroked(color, width, fillColor) : DrawStyle.stroked(color, width);
			GizmoDrawing.face(new Vec3d(box.minX, box.minY, box.minZ), new Vec3d(box.maxX, box.maxY, box.maxZ), hit.getSide(), style);
			return;
		}

		// Krawędzie kształtu, tak jak vanilla: schody czy płotki nie dostają wewnętrznych linii.
		shape.forEachEdge((minX, minY, minZ, maxX, maxY, maxZ) -> GizmoDrawing.line(
			new Vec3d(x + minX, y + minY, z + minZ), new Vec3d(x + maxX, y + maxY, z + maxZ), color, width
		));
		if (fill) {
			shape.forEachBox((minX, minY, minZ, maxX, maxY, maxZ) -> GizmoDrawing.box(
				new Box(x + minX, y + minY, z + minZ, x + maxX, y + maxY, z + maxZ).expand(EPSILON), DrawStyle.filled(fillColor)
			));
		}
	}
}
