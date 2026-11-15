package pl.kerf.project;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import pl.kerf.project.gui.FightReportScreen;
import pl.kerf.project.hud.LagDetectorHud;
import pl.kerf.project.module.FightTracker;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.screen.ChatScreen;
import pl.kerf.project.media.MediaSession;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.kerf.project.config.ConfigManager;
import pl.kerf.project.gui.HudEditorScreen;
import pl.kerf.project.gui.ModuleKeys;
import pl.kerf.project.gui.Notifications;
import pl.kerf.project.module.HudModule;
import pl.kerf.project.module.Modules;
import pl.kerf.project.render.HitColorTexture;
import pl.kerf.project.render.WorldOverlays;

/** Menu moda: klawisz z Opcje -> Sterowanie (domyślnie prawy Shift, zmienialny też w menu moda) albo Mod Menu. */
public class AioPvp implements ClientModInitializer {
	public static final String MOD_ID = "aio_pvp";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		ConfigManager.load();
		ModuleKeys.register();

		HudElementRegistry.addLast(Identifier.of(MOD_ID, "hud"), AioPvp::renderHud);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			Modules.ARMOR_HUD.tick(client);
			HitColorTexture.sync(client);
			MediaSession.tick(client, Modules.MEDIA.on());
			ModuleKeys.tick(client);
			FightTracker.tick(client);
			LagDetectorHud.tick(client);
			if (FightTracker.consumeOpenRequest() && FightTracker.last() != null) {
				client.setScreen(new FightReportScreen(null, FightTracker.last()));
			}
		});
		// Fight Report: własne ataki (zdarzenie leci też po stronie serwera w singleplayerze, stąd isClient).
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (world.isClient()) {
				FightTracker.attack(MinecraftClient.getInstance(), entity);
				Modules.INSTANT_HIT.onAttack(MinecraftClient.getInstance(), entity);
			}

			return ActionResult.PASS;
		});
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
			ClientCommandManager.literal(FightTracker.COMMAND).executes(context -> {
				if (FightTracker.last() == null) {
					context.getSource().sendFeedback(Text.literal("Fight Report: jeszcze nie było żadnej walki."));
					return 0;
				}

				FightTracker.requestOpen();
				return 1;
			})
		));
		// Powiadomienia: w grze nad HUD-em, a w menu nad każdym ekranem.
		HudElementRegistry.addLast(Identifier.of(MOD_ID, "notifications"), (context, tickCounter) -> {
			// Przy otwartym ekranie rysuje je afterRender niżej; tu tylko w grze, żeby nie były podwójne.
			if (MinecraftClient.getInstance().currentScreen == null) {
				Notifications.render(context);
			}
		});
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) ->
			ScreenEvents.afterRender(screen).register((current, context, mouseX, mouseY, delta) -> Notifications.render(context)));
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> MediaSession.stop());

		// Przyciski odtwarzacza są klikalne, gdy otwarty jest czat (kursor jest wtedy wolny).
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof ChatScreen) {
				ScreenMouseEvents.allowMouseClick(screen).register((current, click) ->
					!(click.button() == 0 && Modules.MEDIA.click(click.x(), click.y())));
			}
		});

		WorldRenderEvents.END_EXTRACTION.register(context -> WorldOverlays.submit(MinecraftClient.getInstance()));
		// Własne podświetlenie bloku zastępuje vanilla'owe, więc domyślne trzeba wyłączyć.
		WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, outline) -> !Modules.BLOCK_OUTLINE.on());

		LOGGER.info("AIO PvP: załadowano {} modułów", Modules.ALL.size());
	}

	private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.world == null || client.options.hudHidden) {
			return;
		}

		boolean editing = client.currentScreen instanceof HudEditorScreen;
		// HUD gry rysuje się też pod otwartym ekranem, ale bez przyciemnienia GUI, więc elementy moda biłyby
		// jasno nad ekwipunkiem/menu. Chowamy je, gdy ekran jest otwarty; wyjątek: czat (klikalny odtwarzacz)
		// i edytor HUD (tam elementy muszą być widoczne).
		if (client.currentScreen != null && !editing && !(client.currentScreen instanceof ChatScreen)) {
			return;
		}

		for (HudModule module : Modules.HUD) {
			if (module.on()) {
				module.render(context, client, tickCounter, editing);
			} else {
				// Wyłączony element nie może zostawić starej ramki w edytorze.
				module.boundsW = 0.0F;
				module.boundsH = 0.0F;
			}
		}
	}
}
