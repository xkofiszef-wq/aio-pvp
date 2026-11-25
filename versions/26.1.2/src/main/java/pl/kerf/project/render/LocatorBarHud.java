package pl.kerf.project.render;

import com.mojang.datafixers.util.Either;
import java.util.UUID;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.WaypointStyle;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.waypoints.PartialTickSupplier;
import net.minecraft.world.waypoints.TrackedWaypoint;
import net.minecraft.world.waypoints.Waypoint;
import pl.kerf.project.module.Modules;

/**
 * Locator Bar: ikony waypointów na pasku lokalizacji. Rysunek vanilli (ikona stylu, strzałka wyżej/niżej) z opcjami:
 * głowy graczy z obwódką w kolorze waypointa, nicki, kierunek świata na środku paska i cień tekstu.
 */
public final class LocatorBarHud {
	private static final Identifier ARROW_UP = Identifier.withDefaultNamespace("hud/locator_bar_arrow_up");
	private static final Identifier ARROW_DOWN = Identifier.withDefaultNamespace("hud/locator_bar_arrow_down");
	private static final String[] DIRECTIONS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};

	private LocatorBarHud() {
	}

	/** Kolor waypointa tak jak w vanilli: ustawiony w konfiguracji albo wyliczony z nadawcy. */
	private static int color(TrackedWaypoint waypoint) {
		Waypoint.Icon icon = waypoint.icon();
		return icon.color.orElseGet(() -> waypoint.id().map(
			uuid -> ARGB.setBrightness(ARGB.color(255, uuid.hashCode()), 0.9F),
			name -> ARGB.setBrightness(ARGB.color(255, name.hashCode()), 0.9F)
		)) | 0xFF000000;
	}

	public static void draw(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, Minecraft client) {
		Entity entity = client.getCameraEntity();
		if (entity == null || client.player == null) {
			return;
		}

		Modules.LocatorBarSettings settings = Modules.LOCATOR_BAR;
		Font font = client.font;
		int barY = graphics.guiHeight() - 24 - 5;
		int left = Mth.ceil((graphics.guiWidth() - 9) / 2.0F);
		Level level = entity.level();
		TickRateManager tickRateManager = level.tickRateManager();
		PartialTickSupplier progress = e -> deltaTracker.getGameTimeDeltaPartialTick(!tickRateManager.isEntityFrozen(e));
		boolean shadow = settings.textShadow.on();
		client.player.connection.getWaypointManager().forEachWaypoint(entity, waypoint -> {
			Either<UUID, String> source = waypoint.id();
			if (source.left().map(uuid -> uuid.equals(entity.getUUID())).orElse(false)) {
				return;
			}

			double relative = waypoint.yawAngleToCamera(level, client.gameRenderer.getMainCamera(), progress);
			if (relative <= -60.0 || relative > 60.0) {
				return;
			}

			int x = left + Mth.floor(relative * 173.0 / 2.0 / 60.0);
			int color = color(waypoint);
			PlayerInfo player = source.left().map(uuid -> client.getConnection() == null ? null : client.getConnection().getPlayerInfo(uuid)).orElse(null);
			if (!settings.icons.on()) {
				graphics.fill(x + 2, barY, x + 7, barY + 5, color);
			} else if (settings.heads.on() && player != null) {
				// Obwódka w kolorze waypointa, w środku głowa razem z warstwą kapelusza.
				graphics.fill(x - 1, barY - 3, x + 10, barY + 8, color);
				PlayerFaceExtractor.extractRenderState(graphics, player.getSkin(), x, barY - 2, 9);
			} else {
				WaypointStyle style = client.getWaypointStyles().get(waypoint.icon().style);
				float distance = Mth.sqrt((float) waypoint.distanceSquared(entity));
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, style.sprite(distance), x, barY - 2, 9, 9, color);
			}

			TrackedWaypoint.PitchDirection pitch = waypoint.pitchDirectionToCamera(level, client.gameRenderer, progress);
			if (pitch != TrackedWaypoint.PitchDirection.NONE) {
				boolean down = pitch == TrackedWaypoint.PitchDirection.DOWN;
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, down ? ARROW_DOWN : ARROW_UP, x + 1, barY + (down ? 6 : -6), 7, 5);
			}

			boolean hover = Math.abs(relative) < 4.0;
			if (settings.names.is(2) || settings.names.is(1) && hover) {
				String name = source.map(uuid -> player == null ? null : player.getProfile().name(), text -> text);
				if (name != null && !name.isBlank()) {
					graphics.text(font, name, x + 5 - font.width(name) / 2, barY - 13, 0xFFFFFFFF, shadow);
				}
			}
		});
		if (settings.direction.on()) {
			float yaw = Mth.wrapDegrees(client.gameRenderer.getMainCamera().yRot());
			int index = Math.floorMod(Math.round(yaw / 45.0F), 8);
			String text = DIRECTIONS[index];
			graphics.text(font, text, (graphics.guiWidth() - font.width(text)) / 2, barY - 2, 0xFFFFFFFF, shadow);
		}
	}
}
