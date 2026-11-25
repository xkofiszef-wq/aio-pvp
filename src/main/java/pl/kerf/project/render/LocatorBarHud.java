package pl.kerf.project.render;

import com.mojang.datafixers.util.Either;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.resource.waypoint.WaypointStyleAsset;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.tick.TickManager;
import net.minecraft.world.waypoint.EntityTickProgress;
import net.minecraft.world.waypoint.TrackedWaypoint;
import net.minecraft.world.waypoint.Waypoint;
import pl.kerf.project.module.Modules;

/**
 * Locator Bar: ikony waypointów na pasku lokalizacji. Rysunek vanilli (ikona stylu, strzałka wyżej/niżej) z opcjami:
 * głowy graczy z obwódką w kolorze waypointa, nicki, kierunek świata na środku paska i cień tekstu.
 */
public final class LocatorBarHud {
	private static final Identifier ARROW_UP = Identifier.ofVanilla("hud/locator_bar_arrow_up");
	private static final Identifier ARROW_DOWN = Identifier.ofVanilla("hud/locator_bar_arrow_down");
	private static final String[] DIRECTIONS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};

	private LocatorBarHud() {
	}

	/** Kolor waypointa tak jak w vanilli: ustawiony w konfiguracji albo wyliczony z nadawcy. */
	private static int color(TrackedWaypoint waypoint) {
		Waypoint.Config config = waypoint.getConfig();
		return config.color.orElseGet(() -> waypoint.getSource().map(
			uuid -> ColorHelper.withBrightness(ColorHelper.withAlpha(255, uuid.hashCode()), 0.9F),
			name -> ColorHelper.withBrightness(ColorHelper.withAlpha(255, name.hashCode()), 0.9F)
		)) | 0xFF000000;
	}

	public static void draw(DrawContext context, RenderTickCounter tickCounter, MinecraftClient client) {
		Entity entity = client.getCameraEntity();
		if (entity == null || client.player == null) {
			return;
		}

		Modules.LocatorBarSettings settings = Modules.LOCATOR_BAR;
		TextRenderer font = client.textRenderer;
		int barY = context.getScaledWindowHeight() - 24 - 5;
		int left = MathHelper.ceil((context.getScaledWindowWidth() - 9) / 2.0F);
		World world = entity.getEntityWorld();
		TickManager tickManager = world.getTickManager();
		EntityTickProgress progress = e -> tickCounter.getTickProgress(!tickManager.shouldSkipTick(e));
		boolean shadow = settings.textShadow.on();
		client.player.networkHandler.getWaypointHandler().forEachWaypoint(entity, waypoint -> {
			Either<UUID, String> source = waypoint.getSource();
			if (source.left().map(uuid -> uuid.equals(entity.getUuid())).orElse(false)) {
				return;
			}

			double relative = waypoint.getRelativeYaw(world, client.gameRenderer.getCamera(), progress);
			if (relative <= -60.0 || relative > 60.0) {
				return;
			}

			int x = left + MathHelper.floor(relative * 173.0 / 2.0 / 60.0);
			int color = color(waypoint);
			PlayerListEntry player = source.left().map(uuid -> client.getNetworkHandler() == null ? null : client.getNetworkHandler().getPlayerListEntry(uuid)).orElse(null);
			if (!settings.icons.on()) {
				context.fill(x + 2, barY, x + 7, barY + 5, color);
			} else if (settings.heads.on() && source.left().isPresent()) {
				// Obwódka w kolorze waypointa, w środku głowa (8x8) razem z warstwą kapelusza.
				context.fill(x - 1, barY - 3, x + 10, barY + 8, color);
				PlayerSkinDrawer.draw(context, player != null ? player.getSkinTextures() : net.minecraft.client.util.DefaultSkinHelper.getSkinTextures(source.left().get()), x, barY - 2, 9);
			} else {
				WaypointStyleAsset style = client.getWaypointStyleAssetManager().get(waypoint.getConfig().style);
				float distance = MathHelper.sqrt((float) waypoint.squaredDistanceTo(entity));
				context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, style.getSpriteForDistance(distance), x, barY - 2, 9, 9, color);
			}

			TrackedWaypoint.Pitch pitch = waypoint.getPitch(world, client.gameRenderer, progress);
			if (pitch != TrackedWaypoint.Pitch.NONE) {
				boolean down = pitch == TrackedWaypoint.Pitch.DOWN;
				context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, down ? ARROW_DOWN : ARROW_UP, x + 1, barY + (down ? 6 : -6), 7, 5);
			}

			boolean hover = Math.abs(relative) < 4.0;
			if (settings.names.is(2) || settings.names.is(1) && hover) {
				String name = source.map(uuid -> player == null ? null : player.getProfile().name(), text -> text);
				if (name != null && !name.isBlank()) {
					context.drawText(font, name, x + 5 - font.getWidth(name) / 2, barY - 13, 0xFFFFFFFF, shadow);
				}
			}
		});
		if (settings.direction.on()) {
			float yaw = MathHelper.wrapDegrees(client.gameRenderer.getCamera().getYaw());
			int index = Math.floorMod(Math.round(yaw / 45.0F), 8);
			String text = DIRECTIONS[index];
			// Pasek ma 5 px wysokości, więc litery są mniejsze i mieszczą się w nim.
			context.getMatrices().pushMatrix();
			context.getMatrices().translate(context.getScaledWindowWidth() / 2.0F, barY + 2.5F);
			context.getMatrices().scale(0.6F, 0.6F);
			context.drawText(font, text, -font.getWidth(text) / 2, -4, 0xFFFFFFFF, shadow);
			context.getMatrices().popMatrix();
		}
	}
}
