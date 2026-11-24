package pl.kerf.project;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import pl.kerf.project.gui.FightReportScreen;
import pl.kerf.project.hud.LagDetectorHud;
import pl.kerf.project.module.FightTracker;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import pl.kerf.project.media.MediaSession;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
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
		Modules.GUI_THEME.sync();
		ModuleKeys.register();

		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "hud"), AioPvp::renderHud);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			Modules.ARMOR_HUD.tick(client);
			HitColorTexture.sync(client);
			MediaSession.tick(client, Modules.MEDIA.on());
			ModuleKeys.tick(client);
			FightTracker.tick(client);
			LagDetectorHud.tick(client);
			Modules.FREELOOK.tick(client);
			Modules.WAYPOINTS.tick(client);
			Modules.MARKERS.tick(client);
			Modules.PARTY.tick(client);
			Modules.GUI_THEME.sync();
			if (FightTracker.consumeOpenRequest() && FightTracker.last() != null) {
				client.gui.setScreen(new FightReportScreen(null, FightTracker.last()));
			}
		});
		// Fight Report: własne ataki (zdarzenie leci też po stronie serwera w singleplayerze, stąd isClient).
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (world.isClientSide()) {
				FightTracker.attack(Minecraft.getInstance(), entity);
				Modules.INSTANT_HIT.onAttack(Minecraft.getInstance(), entity);
			}

			return InteractionResult.PASS;
		});
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
			ClientCommands.literal(FightTracker.COMMAND).executes(context -> {
				if (FightTracker.last() == null) {
					context.getSource().sendFeedback(Component.literal(Lang.t("Fight Report: jeszcze nie było żadnej walki.")));
					return 0;
				}

				FightTracker.requestOpen();
				return 1;
			})
		));
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> Modules.WAYPOINTS.register(dispatcher));
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> Modules.PARTY.register(dispatcher));
		// Wiadomości moda (zaproszenia do party, markery) idą zwykłą prywatną wiadomością i znikają z czatu odbiorcy.
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> !Modules.PARTY.receive(Minecraft.getInstance(), message.getString(), null));
		ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signed, sender, params, time) ->
			!Modules.PARTY.receive(Minecraft.getInstance(), message.getString(), sender == null ? null : sender.name()));
		// /calc 2+2*3: zwykły kalkulator na czacie (tylko u ciebie, nic nie idzie na serwer).
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
			ClientCommands.literal("calc").then(ClientCommands.argument("wyrazenie", StringArgumentType.greedyString()).executes(context -> {
				String expression = StringArgumentType.getString(context, "wyrazenie");
				try {
					String result = MathExpr.format(MathExpr.eval(expression));
					context.getSource().sendFeedback(Component.literal(expression + " = ").withColor(0xC2C6D0).append(Component.literal(result).withColor(0x8C8CF0)));
					return 1;
				} catch (IllegalArgumentException e) {
					context.getSource().sendError(Component.literal(Lang.t("Kalkulator: ") + e.getMessage()));
					return 0;
				}
			}))
		));
		// Powiadomienia: w grze nad HUD-em, a w menu nad każdym ekranem.
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "notifications"), (context, tickCounter) -> {
			// Przy otwartym ekranie rysuje je afterRender niżej; tu tylko w grze, żeby nie były podwójne.
			if (Minecraft.getInstance().gui.screen() == null) {
				Notifications.render(context);
			}
		});
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) ->
			ScreenEvents.afterExtract(screen).register((current, context, mouseX, mouseY, delta) -> Notifications.render(context)));
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> MediaSession.stop());

		// Odtwarzacz zostaje na ekranie przy czacie, ekwipunku i innych oknach w grze: rysuje się na wierzchu okna
		// (okno przyciemnia zwykły HUD) i ma klikalne przyciski, bo kursor jest wtedy wolny. Własne okna moda go nie rysują.
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (client.level == null || screen.getClass().getName().startsWith("pl.kerf.project.gui")) {
				return;
			}

			ScreenEvents.afterExtract(screen).register((current, context, mouseX, mouseY, delta) -> {
				if (client.player != null) {
					if (Modules.MEDIA.on()) {
						Modules.MEDIA.render(context, client, client.getDeltaTracker(), false);
					}

					Modules.PARTY.renderPopup(context, client);
				}
			});
			ScreenMouseEvents.allowMouseClick(screen).register((current, click) ->
				!(click.button() == 0 && (Modules.PARTY.clickPopup(click.x(), click.y()) || Modules.MEDIA.on() && Modules.MEDIA.click(click.x(), click.y()))));
		});

		LevelRenderEvents.END_EXTRACTION.register(context -> WorldOverlays.submit(Minecraft.getInstance()));
		// Własne podświetlenie bloku zastępuje vanilla'owe, więc domyślne trzeba wyłączyć.
		LevelRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, outline) -> !Modules.BLOCK_OUTLINE.on());

		LOGGER.info("AIO PvP: załadowano {} modułów", Modules.ALL.size());
	}

	private static void renderHud(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null || client.gui.hud.isHidden()) {
			return;
		}

		boolean editing = client.gui.screen() instanceof HudEditorScreen;
		// HUD gry rysuje się też pod otwartym ekranem, ale bez przyciemnienia GUI, więc elementy moda biłyby
		// jasno nad ekwipunkiem/menu. Chowamy je, gdy ekran jest otwarty; wyjątek: czat (klikalny odtwarzacz)
		// i edytor HUD (tam elementy muszą być widoczne).
		if (client.gui.screen() != null && !editing && !(client.gui.screen() instanceof ChatScreen)) {
			return;
		}

		Modules.MARKERS.render(context, client);
		for (HudModule module : Modules.HUD) {
			if (module == Modules.PARTY_VOTE && !editing) {
				// Okienko głosowania rysuje się na końcu, na wierzchu wszystkich elementów (patrz niżej i hook ekranu).
				continue;
			}

			if (module.on() && module == Modules.MEDIA && client.gui.screen() != null && !editing) {
				// Przy otwartym oknie odtwarzacz rysuje się na wierzchu okna (patrz AFTER_INIT wyżej).
				continue;
			}

			if (module.on()) {
				module.render(context, client, tickCounter, editing);
			} else {
				// Wyłączony element nie może zostawić starej ramki w edytorze.
				module.boundsW = 0.0F;
				module.boundsH = 0.0F;
			}
		}

		// Okienko głosowania na wierzchu wszystkich elementów HUD (przy otwartym oknie rysuje je hook ekranu).
		if (client.gui.screen() == null) {
			Modules.PARTY.renderPopup(context, client);
		}
	}
}
