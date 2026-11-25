package pl.kerf.project.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;
import pl.kerf.project.gui.theme.Theme;
import pl.kerf.project.AioPvp;
import pl.kerf.project.gui.ColorScreen;
import pl.kerf.project.gui.HudEditorScreen;
import pl.kerf.project.gui.SettingsScreen;
import pl.kerf.project.module.Module;
import pl.kerf.project.module.Modules;

/**
 * Test dymny: włącza wszystkie moduły, wchodzi do świata i robi zrzuty ekranu każdej grupy funkcji.
 * Sam fakt dojścia do końca potwierdza, że mixiny się nałożyły i render nie wywala gry.
 */
public class AioPvpClientTest implements FabricClientGameTest {
	/**
	 * Pomiar wydajności (tylko z AIO_PROFILE w środowisku): gracz stoi w miejscu bez limitu FPS. 20 s ze wszystkimi
	 * modułami (FPS + próbki CPU z JFR do build/run/clientGameTest/aio_profile.jfr), potem 20 s z wyłączonymi modułami.
	 */
	private static void profile(ClientGameTestContext context) {
		context.runOnClient(client -> {
			client.options.framerateLimit().set(260);
			client.options.enableVsync().set(false);
			// Vanilla po 60 s bez ruchu myszą/klawiaturą tnie FPS do 30 (opcja "Inactivity FPS Limit: AFK").
			client.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
			client.gui.toastManager().clear();
		});
		context.waitTicks(40);
		jdk.jfr.Recording recording = new jdk.jfr.Recording();
		recording.enable("jdk.ExecutionSample").withPeriod(java.time.Duration.ofMillis(5));
		recording.start();
		double on = averageFps(context, 20);
		recording.stop();
		try {
			recording.dump(java.nio.file.Path.of("aio_profile.jfr"));
		} catch (java.io.IOException e) {
			throw new java.io.UncheckedIOException(e);
		} finally {
			recording.close();
		}

		// FPS w oknie testu pływa między minutami, więc porównanie na przemian: 3x10 s z modułami i 3x10 s bez.
		java.util.List<pl.kerf.project.module.Module> enabled = context.computeOnClient(client -> Modules.ALL.stream().filter(pl.kerf.project.module.Module::on).toList());
		double sumOn = 0.0;
		double sumOff = 0.0;
		for (int round = 0; round < 3; round++) {
			context.runOnClient(client -> enabled.forEach(module -> module.setEnabled(false)));
			context.waitTicks(20);
			sumOff += averageFps(context, 10);
			context.runOnClient(client -> enabled.forEach(module -> module.setEnabled(true)));
			context.waitTicks(20);
			sumOn += averageFps(context, 10);
		}

		AioPvp.LOGGER.info("[profile] FPS (pierwsze 20 s z modułami: {}) na przemian: z modułami {}, bez modułów {}",
			Math.round(on), Math.round(sumOn / 3), Math.round(sumOff / 3));
	}

	/** Dwa ciosy (z przerwą na pełne naładowanie) w encje spełniające warunek, w świecie klienta. */
	private static void attackTwice(ClientGameTestContext context, java.util.function.Predicate<net.minecraft.world.entity.Entity> target) {
		for (int hit = 0; hit < 2; hit++) {
			context.runOnClient(client -> {
				for (net.minecraft.world.entity.Entity entity : client.level.entitiesForRendering()) {
					if (target.test(entity)) {
						client.gameMode.attack(client.player, entity);
					}
				}
			});
			context.waitTicks(15);
		}
	}

	/** Średnie FPS z odczytów co sekundę (licznik gry). */
	private static double averageFps(ClientGameTestContext context, int seconds) {
		double sum = 0.0;
		for (int i = 0; i < seconds; i++) {
			context.waitTicks(20);
			sum += context.computeOnClient(client -> client.getFps());
		}

		return sum / seconds;
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		// Zapis i odczyt configu: wartości wracają po load(), a nie domyślne.
		context.runOnClient(client -> {
			Modules.HIT_COLOR.color.set(0xFF123456);
			Modules.FAST_XP.setEnabled(false);
			Modules.CROSSHAIR.pixels.set("7,7,FFFF0000");
			pl.kerf.project.config.ConfigManager.save();
			Modules.ALL.forEach(pl.kerf.project.module.Module::reset);
			pl.kerf.project.config.ConfigManager.load();
			if (Modules.HIT_COLOR.color.get() != 0xFF123456 || Modules.FAST_XP.on() || !"7,7,FFFF0000".equals(Modules.CROSSHAIR.pixels.get())) {
				throw new AssertionError("Config nie przetrwal zapisu i odczytu");
			}
		});

		// Migracja: stare trzy moduły tarczy ("shield", "shield_anim_fix", "shield_delay") łączą się w jeden "shield".
		context.runOnClient(client -> {
			java.nio.file.Path path = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve(AioPvp.MOD_ID + ".json");
			try {
				java.nio.file.Files.writeString(path, """
					{"shield": {"enabled": true, "y": -0.3},
					 "shield_anim_fix": {"enabled": false},
					 "shield_delay": {"enabled": true}}
					""");
			} catch (java.io.IOException e) {
				throw new java.io.UncheckedIOException(e);
			}

			Modules.ALL.forEach(pl.kerf.project.module.Module::reset);
			pl.kerf.project.config.ConfigManager.load();
			Modules.Shield shield = Modules.SHIELD;
			if (!shield.on() || !shield.position.on() || shield.animFix.on() || !shield.delayFix.on() || Math.abs(shield.transform.y.get() + 0.3) > 1e-6) {
				throw new AssertionError("Migracja modulow tarczy nie przeniosla ustawien");
			}

			pl.kerf.project.config.ConfigManager.save();
		});

		context.runOnClient(client -> {
			Modules.ALL.forEach(pl.kerf.project.module.Module::reset);
			Modules.ALL.forEach(module -> module.setEnabled(true));
			Modules.HEALTH.style.set(0);
			Modules.HITBOXES.mobColor.set(0xFF55FF55);
			Modules.CROSSHAIR.targets.set(1);
			Modules.HIT_COLOR.color.set(0xFF2060FF);
			Modules.HIT_COLOR.opacity.set(60.0);
			Modules.BLOCK_OUTLINE.mode.set(Modules.BlockOutline.MODE_FACE);
			Modules.BLOCK_OUTLINE.color.set(0xFF00FFFF);
			Modules.BLOCK_OUTLINE.fill.set(0x6000FFFF);
			Modules.KEY_OVERLAY.background.set(2);
			// Tweaker i view model zostają na wartościach neutralnych poza tymi, które mają być widać na zrzutach.
			Modules.HUD_TWEAKER.setEnabled(false);
			Modules.VIEW_MODEL.main.scale.set(0.4);
			Modules.SHIELD.position.set(true);
			Modules.SHIELD.transform.y.set(-0.1);
			Modules.SHIELD.transform.scale.set(0.8);
		});

		context.setScreen(() -> new SettingsScreen(null));
		context.takeScreenshot("01_menu_hud");
		// Styl "Kafelki" i motyw Redstone: dwa rowne przyciski na kazdym kafelku.
		context.runOnClient(client -> {
			Modules.GUI_THEME.theme.set(7);
			Modules.GUI_THEME.cardStyle.set(1);
		});
		context.setScreen(() -> new SettingsScreen(null));
		context.waitTicks(10);
		context.takeScreenshot("01c_menu_tiles_redstone");
		context.runOnClient(client -> {
			Modules.GUI_THEME.theme.set(0);
			Modules.GUI_THEME.cardStyle.set(0);
		});
		context.setScreen(() -> new SettingsScreen(null));
		// Przezroczystosc menu i motyw Szron: mniejsza wartosc musi realnie obnizyc krycie paneli.
		int[] baseAlpha = new int[2];
		context.runOnClient(client -> {
			Modules.GUI_THEME.theme.set(8);
			Modules.GUI_THEME.menuOpacity.set(100.0);
			Modules.GUI_THEME.sync();
			baseAlpha[0] = pl.kerf.project.gui.theme.Theme.BASE >>> 24;
		});
		context.setScreen(() -> new SettingsScreen(null));
		context.waitTicks(10);
		context.takeScreenshot("01d_menu_frost_100");
		context.runOnClient(client -> {
			Modules.GUI_THEME.menuOpacity.set(50.0);
			Modules.GUI_THEME.sync();
			baseAlpha[1] = pl.kerf.project.gui.theme.Theme.BASE >>> 24;
		});
		context.waitTicks(5);
		context.takeScreenshot("01e_menu_frost_50");
		if (baseAlpha[1] >= baseAlpha[0] || baseAlpha[1] != Math.round(baseAlpha[0] * 0.5F)) {
			throw new AssertionError("Przezroczystosc menu: 50% powinno obnizyc krycie panelu do polowy: " + baseAlpha[0] + " -> " + baseAlpha[1]);
		}

		context.runOnClient(client -> {
			Modules.GUI_THEME.theme.set(0);
			Modules.GUI_THEME.menuOpacity.set(100.0);
			Modules.GUI_THEME.sync();
		});
		context.setScreen(() -> new SettingsScreen(null));
		// Pasek przewijania bez kółka myszy: chwyć uchwyt u góry, przeciągnij na dół toru, lista ma się przewinąć do końca.
		double[] bar = context.computeOnClient(client -> {
			double[] b = ((SettingsScreen) client.gui.screen()).cardScrollbarForTest();
			double scale = client.getWindow().getGuiScale();
			return new double[] {(b[0] + 1) * scale, (b[1] + 4) * scale, (b[2] - 1) * scale};
		});
		context.getInput().setCursorPos(bar[0], bar[1]);
		context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTick();
		context.getInput().setCursorPos(bar[0], bar[2]);
		context.waitTicks(3);
		context.takeScreenshot("01b_menu_scrollbar_dragged");
		context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		double scrolled = context.computeOnClient(client -> ((SettingsScreen) client.gui.screen()).cardScrollbarForTest()[3]);
		if (scrolled <= 0.0) {
			throw new AssertionError("Pasek przewijania: przeciągnięcie nie przewinęło listy (" + scrolled + ")");
		}
		SettingsScreen.select(Modules.ARMOR_HUD);
		context.setScreen(() -> new SettingsScreen(null));
		context.takeScreenshot("02_module_armor");
		SettingsScreen.select(Modules.ITEM_PREVIEW);
		context.setScreen(() -> new SettingsScreen(null));
		context.takeScreenshot("03_module_items");
		SettingsScreen.select(Modules.HUD_TWEAKER);
		context.setScreen(() -> new SettingsScreen(null));
		context.takeScreenshot("04_module_tweaker");
		context.setScreen(() -> new ColorScreen(null, Modules.HITBOXES.playerColor));
		context.takeScreenshot("05_color");
		context.setScreen(() -> new ColorScreen(null, Modules.HIT_COLOR.color));
		context.takeScreenshot("05b_hit_color_picker");

		// Wejście przez Mod Menu: przycisk "Mods" na ekranie tytułowym.
		context.setScreen(() -> new net.minecraft.client.gui.screens.TitleScreen());
		context.clickScreenButton("modmenu.title");
		context.waitTicks(2);
		context.takeScreenshot("05c_modmenu");
		context.setScreen(() -> new net.minecraft.client.gui.screens.TitleScreen());

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			// Tryb "smoke" (-Daio.smoke=true): tylko start gry z innymi modami, wszystkie moduly wlaczone, krotki zrzut.
			if (Boolean.getBoolean("aio.smoke")) {
				context.runOnClient(client -> {
					for (Module module : Modules.ALL) {
						module.setEnabled(true);
					}

					Modules.OPTIMIZATION.entityCulling.set(true);
					Modules.OPTIMIZATION.fogOcclusion.set(true);
					Modules.OPTIMIZATION.animateOnlyVisible.set(true);
				});
				context.waitTicks(120);
				context.takeScreenshot("smoke");
				return;
			}

			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamemode survival @p");
			server.runCommand("time set noon");
			server.runCommand("item replace entity @p armor.head with diamond_helmet");
			server.runCommand("item replace entity @p armor.chest with diamond_chestplate[damage=500]");
			server.runCommand("item replace entity @p armor.legs with iron_leggings[damage=100]");
			server.runCommand("item replace entity @p armor.feet with netherite_boots");
			server.runCommand("item replace entity @p weapon.mainhand with diamond_sword");
			server.runCommand("item replace entity @p weapon.offhand with shield");
			server.runCommand("give @p totem_of_undying 3");
			server.runCommand("give @p ender_pearl 16");
			server.runCommand("give @p experience_bottle 64");
			server.runCommand("give @p golden_apple 20");
			server.runCommand("give @p cobweb 70");
			server.runCommand("effect give @p strength 60 1");
			server.runCommand("effect give @p speed 8 0");
			server.runCommand("effect give @p fire_resistance 600 0");
			server.runCommand("execute at @p run summon husk ^ ^ ^2.5 {NoAI:1b}");
			server.runCommand("execute at @p run summon pig ^-2 ^ ^4 {NoAI:1b}");
			server.runCommand("execute at @p run summon mannequin ^2 ^ ^4 {CustomName:\"Kerfuss_\",CustomNameVisible:1b}");
			context.waitTicks(30);
			// Toasty zasłaniają prawy górny róg, a tam mają NIE być ikony efektów vanilla (Potion Status je zastępuje).
			context.runOnClient(client -> client.gui.toastManager().clear());
			context.waitTick();
			context.takeScreenshot("06_world_hud_combat");
			if (System.getenv("AIO_PROFILE") != null) {
				// Jak na serwerze: tłum graczy z nickami i sercami w polu widzenia.
				for (int i = 0; i < 60; i++) {
					double angle = i * 0.35;
					double radius = 5.0 + i % 6;
					server.runCommand(String.format(java.util.Locale.ROOT, "execute at @p run summon mannequin ~%.2f ~ ~%.2f {CustomName:\"Gracz_%d\",CustomNameVisible:1b}",
						Math.sin(angle) * radius, -Math.abs(Math.cos(angle)) * radius - 2.0, i));
				}

				context.waitTicks(20);
				profile(context);
				server.runCommand("kill @e[type=mannequin,name=!Kerfuss_]");
				context.waitTicks(5);
			}

			// Serca "Pod nickiem" u manekina z podpisem "NPC" pod nickiem: mają zejść pod podpis, nie nachodzić na niego.
			context.runOnClient(client -> Modules.HEALTH.position.set(1));
			context.waitTick();
			context.takeScreenshot("06f_health_below_label");
			context.runOnClient(client -> Modules.HEALTH.position.set(0));
			// Waypoint: dodany komendą, potem odejście na bok - wiązka, nazwa i dystans mają być widoczne.
			context.runOnClient(client -> client.player.connection.sendCommand("waypoint add Baza"));
			context.waitTick();
			server.runCommand("execute at @p run tp @p ~9 ~ ~9");
			server.runCommand("execute at @p run tp @p ~ ~ ~ facing ~-9 ~ ~-9");
			context.waitTicks(20);
			context.takeScreenshot("06g_waypoint");
			String plus = pl.kerf.project.config.PixelGrid.plus(0xFFFFE030);
			context.runOnClient(client -> { var baza = Modules.WAYPOINTS.here(client).get(0); Modules.WAYPOINTS.create(client, "Skarbiec", baza.x() - 6, baza.y(), baza.z() + 6, 0xFFFF3030, plus); });
			context.waitTicks(5);
			context.takeScreenshot("06g2_waypoint_icon");
			// Edycja waypointa i Hit Color tylko po uderzeniu.
			context.runOnClient(client -> {
				var wp = Modules.WAYPOINTS;
				var first = wp.here(client).get(0);
				String err = wp.update(client, first, "Baza2", first.x(), first.y(), first.z(), 0xFFFF40FF, "");
				if (err != null || wp.here(client).stream().noneMatch(p -> p.name().equals("Baza2") && p.color() == 0xFFFF40FF)) {
					throw new AssertionError("Edycja waypointa nie zadzialala: " + err);
				}

				wp.update(client, wp.here(client).stream().filter(p -> p.name().equals("Baza2")).findFirst().get(), "Baza", first.x(), first.y(), first.z(), first.color(), "");
				var hurt = client.player;
				if (pl.kerf.project.render.HitTracker.recent(hurt)) {
					throw new AssertionError("Hit Color: bez ciosu nie powinno byc uderzenia");
				}

				pl.kerf.project.render.HitTracker.mark(hurt);
				if (!pl.kerf.project.render.HitTracker.recent(hurt)) {
					throw new AssertionError("Hit Color: po ciosie powinno byc uderzenie");
				}
			});
			context.setScreen(() -> new pl.kerf.project.gui.WaypointScreen(null));
			context.waitTicks(3);
			context.takeScreenshot("06g3_waypoint_menu");
			context.setScreen(() -> new pl.kerf.project.gui.WaypointIconScreen(null, plus, value -> {}));
			context.waitTicks(3);
			context.takeScreenshot("06g4_waypoint_icon_editor");
			context.setScreen(() -> null);
			// Układy HUD: koordynaty pionowo, efekty mikstur poziomo.
			context.runOnClient(client -> {
				Modules.COORDINATES.layout.set(1);
				Modules.POTION_STATUS.layout.set(1);
				Modules.CLOCK_12H.set(1);
			});
			context.waitTick();
			context.takeScreenshot("06h_hud_layouts");
			// Hotbar 2 px wyżej; Armor HUD i Item Preview (przyklejone do dołu) jadą razem z nim.
			context.runOnClient(client -> {
				Modules.HUD_TWEAKER.setEnabled(true);
				Modules.HUD_TWEAKER.lift.set(2.0);
			});
			context.waitTick();
			context.takeScreenshot("06i_hotbar_lift");
			// Styl HUD (zaokrąglone rogi, nawiasy) i klikanie przycisków odtwarzacza przy otwartym czacie.
			context.runOnClient(client -> {
				Modules.FPS.rounding.set(4.0);
				Modules.FPS.brackets.set(true);
				Modules.MEDIA.hideIdle.set(false);
				pl.kerf.project.media.MediaSession.lastCommand = null;
			});
			context.waitTick();
			context.takeScreenshot("06j_hud_style");
			context.getInput().pressKey(options -> options.keyChat);
			context.waitTicks(5);
			for (int i = 0; i < 3; i++) {
				int index = i;
				double[] at = context.computeOnClient(client -> {
					float[] b = Modules.MEDIA.buttonRect(index);
					double scale = client.getWindow().getGuiScale();
					return new double[] {(b[0] + b[2] / 2.0) * scale, (b[1] + b[3] / 2.0) * scale};
				});
				context.getInput().setCursorPos(at[0], at[1]);
				context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
				context.waitTicks(2);
				String sent = pl.kerf.project.media.MediaSession.lastCommand;
				String expected = new String[] {"prev", "toggle", "next"}[index];
				if (!expected.equals(sent)) {
					throw new AssertionError("Przycisk odtwarzacza " + expected + " nie zadzialal po kliknieciu, ostatnie polecenie: " + sent);
				}
			}

			context.takeScreenshot("06k_media_buttons");
			context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			// Ten sam odtwarzacz ma zostać widoczny i klikalny w ekwipunku.
			context.runOnClient(client -> pl.kerf.project.media.MediaSession.lastCommand = null);
			context.getInput().pressKey(options -> options.keyInventory);
			context.waitTicks(5);
			context.takeScreenshot("06l_media_in_inventory");
			double[] play = context.computeOnClient(client -> {
				float[] b = Modules.MEDIA.buttonRect(1);
				double scale = client.getWindow().getGuiScale();
				return new double[] {(b[0] + b[2] / 2.0) * scale, (b[1] + b[3] / 2.0) * scale};
			});
			context.getInput().setCursorPos(play[0], play[1]);
			context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
			context.waitTicks(2);
			if (!"toggle".equals(pl.kerf.project.media.MediaSession.lastCommand)) {
				throw new AssertionError("Odtwarzacz nie reaguje na klik w ekwipunku");
			}

			context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			// Marker: środkowy przycisk myszy w ziemię przed sobą; ma się pojawić (i wysłać się do party, której tu nie ma).
			server.runCommand("execute at @p run tp @p ~ ~ ~ ~ 25");
			context.waitTicks(5);
			context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
			context.waitTicks(10);
			context.takeScreenshot("06m_marker");
			// Sounds: głośność jednego dźwięku po nazwie i ekran z listą.
			context.runOnClient(client -> {
				Modules.SOUNDS.set("minecraft:entity.player.hurt", 50);
				if (Math.abs(Modules.SOUNDS.multiplier(net.minecraft.resources.Identifier.parse("minecraft:entity.player.hurt")) - 0.5F) > 1e-6) {
					throw new AssertionError("Sounds: mnoznik glosnosci nie dziala");
				}
			});
			context.setScreen(() -> new pl.kerf.project.gui.SoundsScreen(null));
			context.waitTicks(3);
			context.takeScreenshot("06n_sounds");
			context.runOnClient(client -> Modules.SOUNDS.set("minecraft:entity.player.hurt", 100));
			// Na własne ryzyko: Freelook zapisuje stan włączenia tylko przy ustawieniu "Zapamiętuj ryzykowne moduły".
			context.runOnClient(client -> {
				Modules.GUI_THEME.rememberRisky.set(false);
				Modules.FREELOOK.setEnabled(true);
				if (Modules.FREELOOK.write().get("enabled").getAsBoolean()) {
					throw new AssertionError("Freelook nie powinien zapisywac wlaczenia bez zgody na ryzyko");
				}

				Modules.GUI_THEME.rememberRisky.set(true);
				if (!Modules.FREELOOK.write().get("enabled").getAsBoolean()) {
					throw new AssertionError("Freelook powinien zapisac wlaczenie po zgodzie na ryzyko");
				}

				com.google.gson.JsonObject saved = Modules.FREELOOK.write();
				Modules.FREELOOK.setEnabled(false);
				Modules.FREELOOK.read(saved);
				Modules.FREELOOK.applyRemembered();
				if (!Modules.FREELOOK.on()) {
					throw new AssertionError("Freelook powinien wrocic wlaczony po restarcie");
				}

				Modules.FREELOOK.setEnabled(false);
				Modules.GUI_THEME.rememberRisky.set(false);
			});
			context.setScreen(() -> null);
			// Party client-side: zaproszenie, rola, głosowanie o koordynatach, menu i marker od członka.
			context.runOnClient(client -> {
				if (!Modules.PARTY.receive(client, "Alice whispers to you: [aio]inv", "Alice")) {
					throw new AssertionError("Wiadomosc moda powinna byc ukryta w czacie");
				}

				client.player.connection.sendCommand("mparty accept Alice");
			});
			context.waitTicks(3);
			context.runOnClient(client -> {
				// Alice (leader) przysyła opis party: ona, ja i Bob z rangą Inviter.
				Modules.PARTY.receive(client, "[aio]state 0 Alice:L Player0:M Bob:I", "Alice");
				if (!Modules.PARTY.inParty() || Modules.PARTY.myRole(client) != pl.kerf.project.module.Party.Role.MEMBER) {
					throw new AssertionError("Party: po state od leadera powinienem byc czlonkiem");
				}

				// Obcy nie zmieni stanu party.
				Modules.PARTY.receive(client, "[aio]state 0 Mallory:L Player0:L", "Mallory");
				if (Modules.PARTY.myRole(client) != pl.kerf.project.module.Party.Role.MEMBER) {
					throw new AssertionError("Party: state od obcego nie powinien dzialac");
				}

				Modules.PARTY.receive(client, "[aio]vote 1 coords", "Alice");
				if (Modules.PARTY.vote() == null) {
					throw new AssertionError("Party: powinno trwac glosowanie");
				}
			});
			context.waitTicks(3);
			context.takeScreenshot("06p_party_vote_popup");
			context.runOnClient(client -> {
				Modules.PARTY.answer(true);
				if (Modules.PARTY.vote().myAnswer == null || !Modules.PARTY.vote().myAnswer) {
					throw new AssertionError("Party: moja odpowiedz tak nie zostala zapisana");
				}

				Modules.PARTY.receive(client, "[aio]end 1 ok", "Alice");
				Modules.PARTY.receive(client, "[aio]state 1 Alice:L Player0:M Bob:I", "Alice");
				Modules.PARTY.receive(client, "[aio]pos 10.0 64.0 -5.0 overworld", "Bob");
				if (!Modules.PARTY.coordsEnabled() || Modules.PARTY.positionOf("Bob") == null || Modules.PARTY.vote() != null) {
					throw new AssertionError("Party: koordynaty powinny byc wlaczone po glosowaniu");
				}
			});
			context.setScreen(() -> new pl.kerf.project.gui.PartyScreen(null));
			context.waitTicks(3);
			context.takeScreenshot("06q_party_menu");
			// Motyw "Ocean": ekrany party i waypointów mają brać kolory z ustawień motywu.
			context.runOnClient(client -> {
				Modules.GUI_THEME.theme.set(5);
				Modules.GUI_THEME.sync();
			});
			context.setScreen(() -> new pl.kerf.project.gui.PartyScreen(null));
			context.waitTicks(3);
			context.takeScreenshot("06q2_party_menu_ocean");
			context.setScreen(() -> new pl.kerf.project.gui.WaypointScreen(null));
			context.waitTicks(3);
			context.takeScreenshot("06q3_waypoint_menu_ocean");
			context.runOnClient(client -> {
				Modules.GUI_THEME.theme.set(0);
				Modules.GUI_THEME.sync();
			});
			context.setScreen(() -> null);
			context.runOnClient(client -> {
				// Obcy gracz nie moze stawiac markerow u nas, czlonek party moze.
				int before = Modules.MARKERS.markerCount();
				Modules.PARTY.receive(client, "[aio]m 1.0 -60.0 1.0 overworld minecraft:stone -", "Mallory");
				if (Modules.MARKERS.markerCount() != before) {
					throw new AssertionError("Marker od obcego gracza nie powinien sie pojawic");
				}

				Modules.PARTY.receive(client, "[aio]m 1.0 -60.0 1.0 overworld minecraft:stone -", "Alice");
				if (Modules.MARKERS.markerCount() != before + 1) {
					throw new AssertionError("Marker od czlonka party powinien sie pojawic");
				}

				// Echo wlasnej wiadomosci (serwer odsyla ja klikajacemu, a nadawca z serwera wskazuje odbiorce) nie moze dac markera.
				int afterAlice = Modules.MARKERS.markerCount();
				Modules.PARTY.receive(client, "You whisper to Alice: [aio]m 30.0 -60.0 30.0 overworld minecraft:dirt - ~Player0", "Alice");
				Modules.PARTY.receive(client, "[aio]m 40.0 -60.0 40.0 overworld minecraft:dirt - ~Bob", "Alice");
				if (Modules.MARKERS.markerCount() != afterAlice) {
					throw new AssertionError("Party: echo wlasnej wiadomosci albo wiadomosc z cudzym nickiem nie moze dac markera");
				}

				Modules.PARTY.receive(client, "[aio]m 50.0 -60.0 50.0 overworld minecraft:dirt - ~Alice", "Alice");
				if (Modules.MARKERS.markerCount() != afterAlice + 1) {
					throw new AssertionError("Party: wiadomosc z poprawnym nickiem nadawcy powinna dac marker");
				}
			});
			double[] ahead = context.computeOnClient(client -> {
				net.minecraft.world.phys.Vec3 p = client.player.position().add(client.player.getViewVector(1.0F).multiply(1.0, 0.0, 1.0).normalize().scale(6.0));
				return new double[] {p.x, p.y, p.z};
			});
			server.runCommand(String.format(java.util.Locale.ROOT, "summon pig %.1f %.1f %.1f {NoAI:1b,Tags:[\"aio_target\"]}", ahead[0], ahead[1], ahead[2]));
			context.waitTicks(10);
			// Klient nie zna tagów komend, więc świnię (tę przy punkcie ahead) szukamy po typie i odległości.
			java.util.UUID pigId = context.computeOnClient(client -> {
				net.minecraft.world.entity.Entity best = null;
				double bestDistance = Double.MAX_VALUE;
				for (net.minecraft.world.entity.Entity entity : client.level.entitiesForRendering()) {
					double distance = entity.distanceToSqr(ahead[0], ahead[1], ahead[2]);
					if (net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath().equals("pig") && distance < bestDistance) {
						best = entity;
						bestDistance = distance;
					}
				}

				return best.getUUID();
			});
			context.runOnClient(client -> Modules.PARTY.receive(client, "[aio]m 0.0 -59.0 0.0 overworld minecraft:pig " + pigId, "Alice"));
			context.waitTicks(5);
			context.takeScreenshot("06o_entity_marker");
			int withPig = context.computeOnClient(client -> Modules.MARKERS.markerCount());
			server.runCommand("kill " + pigId);
			context.waitTicks(10);
			int afterKill = context.computeOnClient(client -> Modules.MARKERS.markerCount());
			if (afterKill != withPig - 1) {
				throw new AssertionError("Marker na mobie powinien zniknac razem z mobem: " + withPig + " -> " + afterKill);
			}

			context.runOnClient(client -> client.player.connection.sendCommand("mparty leave"));
			context.waitTicks(3);
			context.runOnClient(client -> {
				var party = Modules.PARTY;
				if (party.inParty()) {
					throw new AssertionError("Party: po leave nie powinienem byc w party");
				}

				// Teraz ja prowadze: zapraszam Zed, on akceptuje, nadaje mu range, glosowanie, kick.
				party.invite("Zed");
				party.receive(client, "[aio]acc", "Zed");
				if (!party.isLeader(client) || !party.roles().containsKey("Zed")) {
					throw new AssertionError("Party: po acc powinienem byc leaderem z Zed w party");
				}

				party.toggleInviter("Zed");
				if (party.roles().get("Zed") != pl.kerf.project.module.Party.Role.INVITER) {
					throw new AssertionError("Party: Zed powinien miec range Inviter");
				}

				party.setCoords(true);
				if (party.vote() == null) {
					throw new AssertionError("Party: leader powinien rozpoczac glosowanie");
				}

				party.receive(client, "[aio]ans " + party.vote().id + " y", "Zed");
				if (!party.coordsEnabled()) {
					throw new AssertionError("Party: komplet glosow tak powinien wlaczyc koordynaty");
				}

				party.kick("Zed");
				if (party.roles().containsKey("Zed")) {
					throw new AssertionError("Party: Zed powinien byc wyrzucony");
				}

				party.leave();
			});
			context.waitTicks(3);
			// Wykrywanie moda u zaproszonego i zgodnosc roznych wersji.
			context.runOnClient(client -> {
				var party = Modules.PARTY;
				party.receive(client, "[aio]ack 1 1.0.31 on", "Zed");
				party.invite("Qux");
				if (party.pendingAcks() != 1) {
					throw new AssertionError("Party: zaproszenie powinno czekac na ack");
				}

				// Nowsza wersja moda: inny protokol i dodatkowe pola na koncu oraz nieznane polecenie nie moga niczego psuc.
				party.receive(client, "[aio]zzz 1 2 3", "Qux");
				party.receive(client, "[aio]ack 2 9.9.9 on nowe_pole", "Qux");
				if (party.pendingAcks() != 0) {
					throw new AssertionError("Party: ack z nowszej wersji powinien zostac przyjety");
				}

				party.invite("Quux");
			});
			context.waitTicks(260);
			context.runOnClient(client -> {
				if (Modules.PARTY.pendingAcks() != 0) {
					throw new AssertionError("Party: brak odpowiedzi powinien zostac zgloszony po kilku sekundach");
				}

				Modules.PARTY.leave();
			});
			// Jak wyglada czat: widok osoby BEZ moda (surowa wiadomosc) oraz komunikaty moda u zapraszajacego i zaproszonego.
			context.runOnClient(client -> {
				var chat = client.gui.hud.getChat();
				chat.clearMessages(false);
				chat.addClientSystemMessage(net.minecraft.network.chat.Component.literal("[Gracz bez moda widzi:] Player0 whispers to you: [aio]inv 1 1.0.31 AIO PvP mod: party invite (install the AIO PvP mod to accept)"));
				Modules.PARTY.invite("Qux");
				Modules.PARTY.receive(client, "[aio]ack 1 1.0.31 on", "Qux");
				Modules.PARTY.receive(client, "Alice whispers to you: [aio]inv 1 1.0.31 AIO PvP mod: party invite (install the AIO PvP mod to accept)", "Alice");
			});
			context.waitTicks(3);
			context.takeScreenshot("06r_party_chat_messages");
			context.runOnClient(client -> {
				Modules.FPS.rounding.set(0.0);
				Modules.FPS.brackets.set(false);
			});
			// Glowy czlonkow party na ekranie: Bob 30 blokow przed graczem (z koordynat party), glowa i odleglosc pod nia.
			context.runOnClient(client -> {
				net.minecraft.world.phys.Vec3 f = client.player.getViewVector(1.0F);
				double fl = Math.sqrt(f.x * f.x + f.z * f.z);
				try {
					var names = pl.kerf.project.module.Party.class.getDeclaredField("rejoinNames");
					var at = pl.kerf.project.module.Party.class.getDeclaredField("rejoinAt");
					names.setAccessible(true);
					at.setAccessible(true);
					((java.util.Set<String>) names.get(Modules.PARTY)).add("Alice");
					at.setLong(Modules.PARTY, System.currentTimeMillis());
				} catch (ReflectiveOperationException e) {
					throw new AssertionError(e);
				}

				Modules.PARTY.receive(client, "[aio]state 1 Alice:L Player0:M Bob:I", "Alice");
				Modules.PARTY.receive(client, String.format(java.util.Locale.ROOT, "[aio]pos %.1f %.1f %.1f overworld",
					client.player.getX() + f.x / fl * 30, client.player.getY(), client.player.getZ() + f.z / fl * 30), "Bob");
				client.player.setXRot(0.0F);
			});
			context.waitTicks(10);
			context.takeScreenshot("06u_party_head");
			context.runOnClient(client -> Modules.PARTY.leave());
			// Locator Bar: waypoint przed graczem, kierunek na srodku, nick, w obu trybach (pasek lokalizacji i na pasku XP).
			context.runOnClient(client -> {
				var icon = new net.minecraft.world.waypoints.Waypoint.Icon();
				icon.color = java.util.Optional.of(0x55FF55);
				float yaw = client.player.getYRot();
				client.player.connection.getWaypointManager().trackWaypoint(
					net.minecraft.world.waypoints.TrackedWaypoint.setAzimuth(java.util.UUID.nameUUIDFromBytes("aio-test".getBytes()), icon, (float) Math.toRadians(yaw + 20.0F)));
				Modules.LOCATOR_BAR.setEnabled(true);
				Modules.LOCATOR_BAR.direction.set(true);
				Modules.LOCATOR_BAR.names.set(2);
			});
			context.waitTicks(5);
			context.takeScreenshot("06t_locator_bar");
			context.runOnClient(client -> Modules.LOCATOR_BAR.xpBar.set(true));
			context.waitTicks(5);
			context.takeScreenshot("06t2_locator_on_xp");
			context.runOnClient(client -> {
				Modules.LOCATOR_BAR.xpBar.set(false);
				Modules.LOCATOR_BAR.setEnabled(false);
			});
			// Beacon na markerze: slup swiatla o grubosci hitboxa gracza.
			context.runOnClient(client -> {
				Modules.MARKERS.beacon.set(true);
				client.player.setXRot(-10.0F);
			});
			context.waitTicks(5);
			context.takeScreenshot("06s3_marker_beacon");
			context.runOnClient(client -> Modules.MARKERS.beacon.set(false));
			// Powrot do party po rozlaczeniu: pamietany czlonek moze przyslac opis party bez zaproszenia, obcy nie.
			context.runOnClient(client -> {
				try {
					var party = Modules.PARTY;
					var names = pl.kerf.project.module.Party.class.getDeclaredField("rejoinNames");
					var at = pl.kerf.project.module.Party.class.getDeclaredField("rejoinAt");
					names.setAccessible(true);
					at.setAccessible(true);
					@SuppressWarnings("unchecked")
					java.util.Set<String> remembered = (java.util.Set<String>) names.get(party);
					remembered.add("Alice");
					at.setLong(party, System.currentTimeMillis());
					party.receive(client, "[aio]state 0 Mallory:L Player0:M", "Mallory");
					if (party.inParty()) {
						throw new AssertionError("Party: obcy nie moze dolaczyc nas do party po rozlaczeniu");
					}

					party.receive(client, "[aio]state 0 Alice:L Player0:M", "Alice");
					if (!party.inParty() || !party.isOnline("Alice")) {
						throw new AssertionError("Party: po powrocie opis party od pamietanego czlonka powinien zostac przyjety");
					}

					party.leave();
				} catch (ReflectiveOperationException e) {
					throw new AssertionError(e);
				}
			});
			// Animate Only Visible Textures: animowana tekstura (magma) jest "widoczna" tylko, gdy patrzysz na jej blok.
			context.runOnClient(client -> Modules.OPTIMIZATION.animateOnlyVisible.set(true));
			double[] magma = context.computeOnClient(client -> {
				net.minecraft.world.phys.Vec3 f = client.player.getViewVector(1.0F);
				double fl = Math.sqrt(f.x * f.x + f.z * f.z);
				return new double[] {Math.floor(client.player.getX() + f.x / fl * 70), Math.floor(client.player.getY()), Math.floor(client.player.getZ() + f.z / fl * 70)};
			});
			server.runCommand(String.format(java.util.Locale.ROOT, "setblock %d %d %d magma_block", (int) magma[0], (int) magma[1], (int) magma[2]));
			// Opcja wlaczona w tym tescie przebudowuje swiat; czekamy, az fragmenty beda gotowe.
			context.waitTicks(80);
			singleplayer.getConnection().waitForChunksRender();
			java.util.Set<String> inView = context.computeOnClient(client -> pl.kerf.project.render.AnimatedSprites.visiblePaths());
			if (inView == null || !inView.contains("block/magma")) {
				throw new 
				AssertionError("Animate Only Visible: magma przed graczem powinno byc widoczne: " + inView);
			}

			context.runOnClient(client -> client.player.setYRot(client.player.getYRot() + 180.0F));
			context.waitTicks(30);
			int skippedBefore = pl.kerf.project.render.AnimatedSprites.skipped;
			context.waitTicks(40);
			java.util.Set<String> behind = context.computeOnClient(client -> pl.kerf.project.render.AnimatedSprites.visiblePaths());
			if (behind == null || behind.contains("block/magma")) {
				throw new AssertionError("Animate Only Visible: magma za plecami nie powinno byc widoczne: " + behind);
			}

			if (pl.kerf.project.render.AnimatedSprites.skipped <= skippedBefore) {
				throw new AssertionError("Animate Only Visible: wysylka niewidocznej animacji powinna byc pomijana");
			}

			context.runOnClient(client -> {
				client.player.setYRot(client.player.getYRot() + 180.0F);
				Modules.OPTIMIZATION.animateOnlyVisible.set(false);
			});
			context.waitTicks(20);
			context.runOnClient(client -> {
				Modules.HUD_TWEAKER.lift.set(0.0);
				Modules.HUD_TWEAKER.setEnabled(false);
			});
			context.runOnClient(client -> {
				Modules.COORDINATES.layout.set(0);
				Modules.POTION_STATUS.layout.set(0);
				Modules.CLOCK_12H.set(0);
			});
			// Sprint/Sneak Status z włączonym Toggle Sprint.
			context.runOnClient(client -> client.options.toggleSprint().set(true));
			context.getInput().pressKey(options -> options.keySprint);
			context.waitTicks(2);
			context.takeScreenshot("06g_sprint_status");
			context.getInput().pressKey(options -> options.keySprint);
			context.runOnClient(client -> client.options.toggleSprint().set(false));

			// HUD moda nie może świecić nad otwartym ekranem (ekwipunek) - powinien zniknąć.
			context.runOnClient(client -> client.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(client.player)));
			context.waitTicks(2);
			context.takeScreenshot("06a_inventory_no_mod_hud");
			context.runOnClient(client -> client.gui.setScreen(null));

			// Drugi styl: zbroja i liczniki zwykłe, mikstury w panelach z ekwipunku.
			context.runOnClient(client -> {
				Modules.ARMOR_HUD.style.set(0);
				Modules.ITEM_PREVIEW.style.set(0);
				Modules.POTION_STATUS.style.set(1);
			});
			context.waitTick();
			context.takeScreenshot("06b_style_swap");
			// Trzeci styl: Resourcepack GUI (tekstury z atlasu, czyli z texture packa); bez packa wygląda jak oryginał.
			context.runOnClient(client -> {
				Modules.ARMOR_HUD.style.set(2);
				Modules.ITEM_PREVIEW.style.set(2);
				Modules.POTION_STATUS.style.set(2);
			});
			context.waitTick();
			context.takeScreenshot("06c_style_resourcepack");
			context.runOnClient(client -> {
				Modules.ARMOR_HUD.style.set(1);
				Modules.ITEM_PREVIEW.style.set(1);
				Modules.POTION_STATUS.style.set(0);
			});

			// HUD moda skaluje się razem z GUI Scale gry.
			for (int scale : new int[] {1, 3}) {
				context.runOnClient(client -> {
					client.options.guiScale().set(scale);
					client.resizeGui();
				});
				context.waitTick();
				context.takeScreenshot("06c_gui_scale_" + scale);
				if (scale == 1) {
					// Duża rozdzielczość GUI: menu z prawym panelem podglądu (najpierw kafelki, potem otwarte ustawienia).
					context.runOnClient(client -> SettingsScreen.select(Modules.ARMOR_HUD));
					context.setScreen(() -> new SettingsScreen(null));
					context.waitTick();
					context.takeScreenshot("06d_menu_large_settings");
					// Po zamknięciu panelu (Esc) i bez najechania prawy panel ma pokazać podpowiedź, nie ostatni moduł.
					context.getInput().setCursorPos(0, 0);
					context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE);
					context.waitTicks(2);
					context.takeScreenshot("06e_menu_large_no_hover");
					context.runOnClient(client -> client.gui.setScreen(null));
				}
			}

			context.runOnClient(client -> {
				client.options.guiScale().set(0);
				client.resizeGui();
			});

			server.runCommand("damage @e[type=husk,limit=1] 1");
			context.waitTicks(3);
			context.takeScreenshot("07_hit_color");

			// Fight Report liczy tylko graczy: walka z mobem (golem za plecami, husk z przodu zostaje do zrzutów hitboxów)
			// nie może dać raportu.
			server.runCommand("execute at @p run summon iron_golem ^ ^ ^-2 {NoAI:1b,DeathLootTable:\"minecraft:empty\"}");
			context.waitTicks(10);
			attackTwice(context, entity -> entity instanceof net.minecraft.world.entity.animal.golem.IronGolem);
			server.runCommand("kill @e[type=iron_golem]");
			context.waitTicks(10);
			if (context.computeOnClient(client -> pl.kerf.project.module.FightTracker.last()) != null) {
				throw new AssertionError("Fight Report: walka z mobem dala raport");
			}
			// Fight Report: zabojstwo bez statusu smierci i bez zdrowia (serwery praktyk) rozpoznane z komunikatu na czacie.
			int fightsBefore = context.computeOnClient(client -> pl.kerf.project.module.FightTracker.history().size());
			context.runOnClient(client -> {
				for (net.minecraft.world.entity.Entity entity : client.level.entitiesForRendering()) {
					if (entity.getName().getString().equals("Kerfuss_") && entity instanceof net.minecraft.world.entity.LivingEntity) {
						pl.kerf.project.module.FightTracker.attack(client, entity);
						break;
					}
				}

				pl.kerf.project.module.FightTracker.chat("Kerfuss_ was slain by Player0");
			});
			context.waitTicks(3);
			context.runOnClient(client -> {
				var history = pl.kerf.project.module.FightTracker.history();
				if (history.size() != fightsBefore + 1 || history.get(0).outcome() != pl.kerf.project.module.FightTracker.Outcome.WIN) {
					throw new AssertionError("Fight Report: komunikat o zabojstwie powinien dac wygrana, a jest: " + (history.isEmpty() ? "brak" : history.get(0).outcome()));
				}
			});

			// Walka z postacią gracza (manekin): dwa ciosy, manekin ginie = wygrana, raport 2 z 2.
			server.runCommand("execute at @p run summon mannequin ^ ^ ^-2 {CustomName:\"Rywal_\"}");
			context.waitTicks(10);
			attackTwice(context, entity -> "Rywal_".equals(entity.getName().getString()));
			server.runCommand("kill @e[type=mannequin,name=Rywal_]");
			context.waitTicks(10);
			pl.kerf.project.module.FightTracker.Report report = context.computeOnClient(client -> pl.kerf.project.module.FightTracker.last());
			if (report == null || report.outcome() != pl.kerf.project.module.FightTracker.Outcome.WIN
				|| report.attempts() != 2 || report.landed() != 2 || report.damage() <= 0.0F || report.bestCombo() != 2) {
				throw new AssertionError("Fight Report: zly raport po walce: " + report);
			}

			// Serwer bez prawdziwego HP przeciwnika: sam sygnał śmierci (status 3) też ma dać wygraną, nie "Undecided".
			server.runCommand("execute at @p run summon mannequin ^ ^ ^-2 {CustomName:\"Rywal2_\"}");
			context.waitTicks(10);
			attackTwice(context, entity -> "Rywal2_".equals(entity.getName().getString()));
			context.runOnClient(client -> {
				for (net.minecraft.world.entity.Entity entity : client.level.entitiesForRendering()) {
					if ("Rywal2_".equals(entity.getName().getString())) {
						pl.kerf.project.module.FightTracker.died(entity);
					}
				}
			});
			context.waitTicks(2);
			pl.kerf.project.module.FightTracker.Report statusWin = context.computeOnClient(client -> pl.kerf.project.module.FightTracker.last());
			server.runCommand("kill @e[type=mannequin,name=Rywal2_]");
			context.waitTicks(5);
			if (statusWin == null || !"Rywal2_".equals(statusWin.opponent()) || statusWin.outcome() != pl.kerf.project.module.FightTracker.Outcome.WIN) {
				throw new AssertionError("Fight Report: smierc ze statusu 3 nie dala wygranej: " + statusWin);
			}

			context.takeScreenshot("07h_fight_report_chat");
			context.runOnClient(client -> client.player.connection.sendCommand(pl.kerf.project.module.FightTracker.COMMAND));
			context.waitTicks(3);
			if (!context.computeOnClient(client -> client.gui.screen() instanceof pl.kerf.project.gui.FightReportScreen)) {
				throw new AssertionError("Fight Report: komenda /fightreport nie otworzyla karty");
			}

			context.takeScreenshot("07i_fight_report_card_opening");
			context.waitTicks(40);
			context.takeScreenshot("07j_fight_report_card");
			// Karta z pełniejszymi danymi (przegrana, totemy, zużycie zbroi): w trakcie animacji i po niej.
			// Dwie przykładowe walki w historii (gracz bez wczytanego skina i gracz ze skinem), karta pokazuje ostatnią.
			context.runOnClient(client -> {
				pl.kerf.project.module.FightTracker.addToHistory(new pl.kerf.project.module.FightTracker.Report(
					"Steve_PvP", pl.kerf.project.module.FightTracker.Outcome.ENDED, 20 * 12, 6, 4, 2, 9.0F, 3, 2.1, 0, 4, null
				));
				pl.kerf.project.module.FightTracker.addToHistory(new pl.kerf.project.module.FightTracker.Report(
					"Kerfuss_", pl.kerf.project.module.FightTracker.Outcome.LOSS, 20 * 47, 15, 12, 9, 31.0F, 5, 2.74, 2, 37,
					client.player.getSkin()
				));
			});
			context.setScreen(() -> new pl.kerf.project.gui.FightReportScreen(null, pl.kerf.project.module.FightTracker.last()));
			context.waitTicks(8);
			context.takeScreenshot("07k_fight_report_loss_animating");
			context.waitTicks(40);
			context.takeScreenshot("07l_fight_report_loss");
			context.setScreen(() -> null);

			// Lag Detector: pakiety z serwera docierają do licznika (cisza krótka), a tekst pojawia się dopiero po progu.
			String lag = context.computeOnClient(client -> pl.kerf.project.hud.LagDetectorHud.gapSeconds() < 1.5
				? Modules.LAG_DETECTOR.message(0.4) + " | " + (Modules.LAG_DETECTOR.message(3.2) == null ? null : Modules.LAG_DETECTOR.message(3.2).getString())
				: "cisza " + pl.kerf.project.hud.LagDetectorHud.gapSeconds());
			if (!"null | Server not responding: 3.2 s".equals(lag)) {
				throw new AssertionError("Lag Detector: " + lag);
			}

			// Nowe menu w świecie: podgląd elementu HUD i podgląd koloru trafienia.
			context.runOnClient(client -> {
				client.gui.toastManager().clear();
				SettingsScreen.select(Modules.ARMOR_HUD);
			});
			context.setScreen(() -> new SettingsScreen(null));
			context.waitTick();
			context.takeScreenshot("07b_menu_preview_armor");
			context.runOnClient(client -> SettingsScreen.select(Modules.HIT_COLOR));
			context.setScreen(() -> new SettingsScreen(null));
			context.waitTick();
			context.takeScreenshot("07c_menu_preview_hit_color");
			context.runOnClient(client -> SettingsScreen.select(Modules.POTION_STATUS));
			context.setScreen(() -> new SettingsScreen(null));
			context.waitTick();
			context.takeScreenshot("07d_menu_preview_potions");

			// Ekrany ustawień z podglądem na żywo.
			context.setScreen(() -> new pl.kerf.project.gui.CrosshairEditorScreen(null));
			context.waitTick();
			context.takeScreenshot("07g_crosshair_editor");

			for (pl.kerf.project.module.Module module : new pl.kerf.project.module.Module[] {
				Modules.HIT_COLOR, Modules.CROSSHAIR, Modules.HITBOXES, Modules.HEALTH, Modules.ARMOR_HUD,
				Modules.BLOCK_OUTLINE, Modules.KEY_OVERLAY, Modules.VIEW_MODEL, Modules.SHIELD, Modules.COORDINATES, Modules.MEDIA
			}) {
				SettingsScreen.select(module);
				context.setScreen(() -> new SettingsScreen(null));
				context.waitTick();
				context.takeScreenshot("07e_module_" + module.id);
			}

			context.setScreen(() -> new ColorScreen(null, Modules.HIT_COLOR.color, Modules.HIT_COLOR));
			context.waitTick();
			context.takeScreenshot("07f_color_picker_live");

			context.setScreen(() -> new HudEditorScreen(null));
			context.waitTicks(2);
			context.takeScreenshot("08_hud_editor");
			// Zaznaczony element (Title na środku): cienkie narożniki zamiast kwadratów, bez podpisu, gdy nic się nie dzieje.
			int[] window = context.computeOnClient(client -> new int[] {client.getWindow().getScreenWidth(), client.getWindow().getScreenHeight()});
			context.getInput().setCursorPos(window[0] / 2.0, window[1] / 2.0);
			context.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
			context.getInput().setCursorPos(window[0] * 0.8, window[1] * 0.62);
			context.waitTicks(2);
			context.takeScreenshot("08d_hud_selected");
			HudEditorScreen.setPickerOpen(true);
			context.setScreen(() -> new HudEditorScreen(null));
			context.waitTicks(2);
			context.takeScreenshot("08a_hud_picker");
			HudEditorScreen.setPickerOpen(false);
			context.setScreen(() -> null);

			// Hitboxy F3+B: zastąpione własnymi, husk na celowniku ma kolor celu.
			context.runOnClient(client -> client.debugEntries.toggleStatus(net.minecraft.client.gui.components.debug.DebugScreenEntries.ENTITY_HITBOXES));
			context.waitTicks(2);
			context.takeScreenshot("08b_hitboxes_f3b");
			context.runOnClient(client -> Modules.HITBOXES.setEnabled(false));
			context.waitTicks(2);
			context.takeScreenshot("08c_hitboxes_vanilla");
			context.runOnClient(client -> Modules.HITBOXES.setEnabled(true));
			context.runOnClient(client -> client.debugEntries.toggleStatus(net.minecraft.client.gui.components.debug.DebugScreenEntries.ENTITY_HITBOXES));

			context.runOnClient(client -> client.gameRenderer.displayItemActivation(new ItemStack(Items.TOTEM_OF_UNDYING)));
			context.waitTicks(12);
			context.takeScreenshot("09_totem");
			context.waitTicks(40);

			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 60");
			context.waitTicks(5);
			context.takeScreenshot("10_block_outline_face");
			context.runOnClient(client -> Modules.BLOCK_OUTLINE.mode.set(Modules.BlockOutline.MODE_FULL));
			context.waitTicks(2);
			context.takeScreenshot("11_block_outline_full");

			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 0");
			server.runCommand("execute at @p run setblock ~ ~ ~ fire");
			context.waitTicks(20);
			context.takeScreenshot("12_low_fire");
			server.runCommand("execute at @p run fill ~-1 ~ ~-1 ~1 ~1 ~1 air replace fire");

			// Fast XP: 20 ticków trzymania użycia. Vanilla rzuca co 4 ticki (5 butelek), z rzutem co 2 ticki powinno wyjść ok. 10.
			server.runCommand("item replace entity @p weapon.mainhand with experience_bottle 64");
			server.runCommand("clear @p shield");
			context.waitTicks(5);
			int before = context.computeOnClient(client -> client.player.getMainHandItem().getCount());
			context.getInput().holdKeyFor(options -> options.keyUse, 20);
			context.waitTicks(5);
			int after = context.computeOnClient(client -> client.player.getMainHandItem().getCount());
			AioPvp.LOGGER.info("[test] Fast XP: rzucono {} butelek w 20 tickow", before - after);
			if (before - after <= 6) {
				throw new AssertionError("Fast XP nie przyspieszylo rzucania: " + (before - after) + " butelek w 20 tickow");
			}

			context.takeScreenshot("13_after_xp");

			// Cooldown Fix: machnięcie ręką zeruje ładowanie ataku po stronie klienta, tak jak robi to serwer.
			server.runCommand("item replace entity @p weapon.mainhand with diamond_sword");
			context.waitTicks(30);
			float cooldown = context.computeOnClient(client -> {
				client.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
				return client.player.getAttackStrengthScale(0.0F);
			});
			if (cooldown >= 1.0F) {
				throw new AssertionError("Cooldown Fix nie wyzerowal ladowania ataku po machnieciu: " + cooldown);
			}

			// No Sign GUI: okno edycji tabliczki się nie otwiera.
			server.runCommand("execute at @p run setblock ~2 ~ ~ oak_sign");
			context.waitTicks(5);
			boolean signScreen = context.computeOnClient(client -> {
				net.minecraft.core.BlockPos pos = client.player.blockPosition().offset(2, 0, 0);
				if (!(client.level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign)) {
					throw new AssertionError("Brak tabliczki do testu No Sign GUI");
				}

				client.player.openTextEdit(sign, true);
				return client.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
			});
			if (signScreen) {
				throw new AssertionError("No Sign GUI nie zablokowalo okna tabliczki");
			}

			// 1.7 Animations: miecz + tarcza, PPM = blok tarczą. Z modułem miecz w pozie bloku i bez tarczy, bez modułu vanilla.
			server.runCommand("item replace entity @p weapon.mainhand with diamond_sword");
			server.runCommand("item replace entity @p weapon.offhand with shield");
			context.runOnClient(client -> {
				Modules.VIEW_MODEL.setEnabled(false);
				Modules.OLD_ANIMATIONS.setEnabled(true);
			});
			context.waitTicks(10);
			context.getInput().holdKey(options -> options.keyUse);
			context.waitTicks(10);
			context.takeScreenshot("13b_old_animations_sword_block");
			context.runOnClient(client -> Modules.OLD_ANIMATIONS.setEnabled(false));
			context.waitTicks(2);
			context.takeScreenshot("13c_vanilla_shield_block");
			context.getInput().releaseKey(options -> options.keyUse);
			// Bez opadania po ciosie: zrzut tuż po machnięciu (vanilla: miecz opada, z modułem zostaje w miejscu).
			context.runOnClient(client -> Modules.OLD_ANIMATIONS.setEnabled(true));
			context.waitTicks(10);
			context.runOnClient(client -> client.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND));
			context.runOnClient(client -> client.player.resetAttackStrengthTicker());
			context.waitTicks(3);
			context.takeScreenshot("13d_old_animations_no_dip");
			context.runOnClient(client -> {
				Modules.OLD_ANIMATIONS.setEnabled(false);
				Modules.VIEW_MODEL.setEnabled(true);
			});
			context.waitTicks(15);
			context.runOnClient(client -> {
				client.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
				client.player.resetAttackStrengthTicker();
			});
			context.waitTicks(3);
			context.takeScreenshot("13e_vanilla_attack_dip");
			server.runCommand("item replace entity @p weapon.offhand with air");

			// Invisible Hand: przezroczysta ręka przy niewidzialności.
			server.runCommand("item replace entity @p weapon.mainhand with air");
			server.runCommand("effect give @p invisibility 60 0 true");
			context.waitTicks(10);
			context.takeScreenshot("14_invisible_hand");

			// Name in 3rd Person: własny nick nad głową.
			context.runOnClient(client -> client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK));
			server.runCommand("effect clear @p invisibility");
			context.waitTicks(10);
			context.takeScreenshot("15_own_name_third_person");
			context.runOnClient(client -> client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));

			// Clear View: nakładka dyni na głowie znika (z modułem), a wraca po wyłączeniu.
			server.runCommand("item replace entity @p armor.head with carved_pumpkin");
			context.waitTicks(3);
			context.takeScreenshot("16_pumpkin_cleared");
			context.runOnClient(client -> Modules.CLEAR_VIEW.setEnabled(false));
			context.waitTicks(3);
			context.takeScreenshot("16b_pumpkin_overlay");
			context.runOnClient(client -> Modules.CLEAR_VIEW.setEnabled(true));

			// Clear View: głowa w kamieniu, bez ciemnej nakładki bloku (z modułem) i z nią (bez modułu).
			server.runCommand("item replace entity @p armor.head with air");
			// Cały gracz w kamieniu (pojedynczy blok przy głowie gra wypycha na bok).
			server.runCommand("execute at @p run fill ~-1 ~ ~-1 ~1 ~1 ~1 stone");
			context.waitTicks(3);
			context.takeScreenshot("16c_in_wall_cleared");
			context.runOnClient(client -> Modules.CLEAR_VIEW.setEnabled(false));
			context.waitTicks(2);
			context.takeScreenshot("16d_in_wall_overlay");
			context.runOnClient(client -> Modules.CLEAR_VIEW.setEnabled(true));
			server.runCommand("execute at @p run fill ~-1 ~ ~-1 ~1 ~1 ~1 air");

			// Clear View: bez mgły w wodzie i lawie (z modułem), potem vanilla dla porównania.
			server.runCommand("effect give @p fire_resistance 60 0 true");
			for (String liquid : new String[] {"water", "lava"}) {
				server.runCommand("execute at @p run fill ~-8 ~ ~-8 ~8 ~3 ~8 " + liquid + " replace air");
				context.waitTicks(10);
				context.takeScreenshot("16e_" + liquid + "_no_fog");
				context.runOnClient(client -> Modules.CLEAR_VIEW.setEnabled(false));
				context.waitTicks(3);
				context.takeScreenshot("16f_" + liquid + "_fog");
				context.runOnClient(client -> Modules.CLEAR_VIEW.setEnabled(true));
				server.runCommand("execute at @p run fill ~-8 ~ ~-8 ~8 ~3 ~8 air replace " + liquid);
			}
			server.runCommand("effect clear @p fire_resistance");

			// Gładkie rogi GUI: koło r=4 px. Róg poza kołem pusty, środek pełny, piksel na łuku częściowy, pierścień pusty w środku.
			int corner = pl.kerf.project.gui.theme.Draw.coverage(4, 0, 0, 0);
			int center = pl.kerf.project.gui.theme.Draw.coverage(4, 0, 3, 3);
			int arc = pl.kerf.project.gui.theme.Draw.coverage(4, 0, 0, 2);
			int ringHole = pl.kerf.project.gui.theme.Draw.coverage(4, 1, 3, 3);
			int ringEdge = pl.kerf.project.gui.theme.Draw.coverage(4, 1, 0, 3);
			if (corner != 0 || center != 255 || arc <= 0 || arc >= 255 || ringHole != 0 || ringEdge == 0) {
				throw new AssertionError("Gładkie rogi: zły piksel koła " + corner + " " + center + " " + arc + " " + ringHole + " " + ringEdge);
			}

			// Saturation: rozpoznanie obwódki na ikonkach 3x3 (B = czarny, R = czerwony, 0 = przezroczysty).
			int b = 0xFF000000;
			int r = 0xFFD42A2A;
			var none = new pl.kerf.project.render.SaturationOutline.Icon(3, 3, new int[9]);
			var ring = pl.kerf.project.render.SaturationOutline.outline(
				new pl.kerf.project.render.SaturationOutline.Icon(3, 3, new int[] {b, b, b, b, b, b, b, b, b}),
				new pl.kerf.project.render.SaturationOutline.Icon(3, 3, new int[] {0, 0, 0, 0, r, 0, 0, 0, 0}), 3, 3);
			var drawnIn = pl.kerf.project.render.SaturationOutline.outline(none,
				new pl.kerf.project.render.SaturationOutline.Icon(3, 3, new int[] {b, b, b, b, r, b, b, b, b}), 3, 3);
			var added = pl.kerf.project.render.SaturationOutline.outline(none,
				new pl.kerf.project.render.SaturationOutline.Icon(3, 3, new int[] {0, 0, 0, 0, r, 0, 0, 0, 0}), 3, 3);
			// Maska ma zapas 1 piksela: [x + 1][y + 1].
			if (!ring[1][1] || ring[2][2] || ring[0][0] || !drawnIn[3][3] || drawnIn[2][2]
				|| !added[1][2] || !added[2][1] || added[2][2] || added[1][1] || added[0][2]) {
				throw new AssertionError("Saturation: zła obwódka (pusta ikonka / wrysowana / dorysowana)");
			}

			// Saturation: 7 punktów = 3 pełne złote obwódki + połówka na pasku głodu (od prawej).
			server.runOnServer(s -> {
				net.minecraft.world.food.FoodData hunger = s.getPlayerList().getPlayers().get(0).getFoodData();
				hunger.setFoodLevel(20);
				hunger.setSaturation(7.0F);
			});
			context.runOnClient(client -> Modules.SATURATION.setEnabled(true));
			context.waitTicks(5);
			context.takeScreenshot("16g_saturation");
			// Podgląd jedzenia: złota marchewka (6 głodu, 14,4 saturacji) przy głodzie 10 i saturacji 2 mruga do 16 i 16.
			server.runOnServer(s -> {
				net.minecraft.world.food.FoodData hunger = s.getPlayerList().getPlayers().get(0).getFoodData();
				hunger.setFoodLevel(10);
				hunger.setSaturation(2.0F);
			});
			server.runCommand("item replace entity @p weapon.mainhand with golden_carrot");
			context.waitTicks(5);
			context.takeScreenshot("16h_saturation_food_preview");
			server.runCommand("item replace entity @p weapon.mainhand with air");
			context.runOnClient(client -> Modules.SATURATION.setEnabled(false));
			server.runCommand("execute at @p run kill @e[type=item,distance=..16]");

			// Instant Hit: włączenie tylko przez okno ostrzeżenia, potem blokada do restartu gry, stan nie zapisuje się.
			context.runOnClient(client -> {
				Modules.INSTANT_HIT.setEnabled(false);
				Modules.INSTANT_HIT.toggle();
			});
			context.waitTicks(2);
			if (Modules.INSTANT_HIT.on() || !context.computeOnClient(client -> client.gui.screen() instanceof net.minecraft.client.gui.screens.ConfirmScreen)) {
				throw new AssertionError("Instant Hit: wlaczyl sie bez okna ostrzezenia");
			}

			context.takeScreenshot("19_instant_hit_warning");
			double[] confirm = context.computeOnClient(client -> {
				double scale = client.getWindow().getGuiScale();
				for (net.minecraft.client.gui.components.events.GuiEventListener element : client.gui.screen().children()) {
					if (element instanceof net.minecraft.client.gui.components.AbstractWidget widget && widget.getMessage().getString().startsWith("Enable for this session")) {
						return new double[] {(widget.getX() + widget.getWidth() / 2.0) * scale, (widget.getY() + widget.getHeight() / 2.0) * scale};
					}
				}

				throw new AssertionError("Instant Hit: brak przycisku potwierdzenia");
			});
			context.getInput().setCursorPos(confirm[0], confirm[1]);
			context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
			context.waitTicks(2);
			String locked = context.computeOnClient(client -> {
				boolean afterConfirm = Modules.INSTANT_HIT.on() && Modules.INSTANT_HIT.locked();
				Modules.INSTANT_HIT.toggle();
				Modules.INSTANT_HIT.setEnabled(false);
				Modules.INSTANT_HIT.reset();
				return afterConfirm + "/" + Modules.INSTANT_HIT.on() + "/" + Modules.INSTANT_HIT.write().get("enabled").getAsBoolean();
			});
			if (!"true/true/false".equals(locked)) {
				throw new AssertionError("Instant Hit: blokada nie dziala (po potwierdzeniu/po probach wylaczenia/zapis): " + locked);
			}

			// Damage Calculator: wzory policzone ręcznie. Miecz diamentowy (7) + Ostrość V (0.5*5+0.5 = 3) bez zbroi = 10;
			// bez ostrości w pełnym netherycie (20 pkt, 12 wytrz.): 7 * (1 - (20 - 7/5) / 25) = 1.792; 10 HP na cios = 2 ciosy.
			float plain = pl.kerf.project.module.DamageCalc.hit(5, 5, 0, false, 0, 0, 0);
			float netherite = pl.kerf.project.module.DamageCalc.hit(5, 0, 0, false, 6, 0, 0);
			// Ciosy po 10 HP co 12.5 ticku: bez saturacji 2 ciosy; z pełną saturacją przeciwnik w 10 ticków leczy 1 HP,
			// więc po drugim ciosie ma 1 HP i trzeba trzeciego.
			int twoHits = pl.kerf.project.module.DamageCalc.hitsToKill(10.0F, 0.0F, 12.5F, false);
			int threeHits = pl.kerf.project.module.DamageCalc.hitsToKill(10.0F, 0.0F, 12.5F, true);
			if (Math.abs(plain - 10.0F) > 1e-4 || Math.abs(netherite - 1.792F) > 1e-3 || twoHits != 2 || threeHits != 3) {
				throw new AssertionError("Damage Calculator: zle wyniki " + plain + " / " + netherite + " / " + twoHits + " / " + threeHits);
			}

			context.runOnClient(client -> SettingsScreen.select(Modules.DAMAGE_CALC));
			context.setScreen(() -> new SettingsScreen(null));
			context.waitTicks(2);
			context.takeScreenshot("20_damage_calc");
			context.setScreen(() -> null);

			// Freelook (jak Perspective Mod): trzymany klawisz + ruch myszy obraca kamerę, a postać nie; po puszczeniu widok wraca.
			context.runOnClient(client -> Modules.FREELOOK.setEnabled(true));
			float yawBefore = context.computeOnClient(client -> client.player.getYRot());
			context.getInput().holdKey(options -> pl.kerf.project.module.Freelook.key);
			context.waitTicks(2);
			// Prawdziwy ruch myszy: okno testu nie ma fokusu, więc dajemy mu go i łapiemy kursor jak w grze.
			context.runOnClient(client -> {
				// 26.x: fokus okna ustawia tylko wywołanie zwrotne GLFW, więc wołamy je ręcznie (okno testu nie ma fokusu).
				try {
					java.lang.reflect.Method focus = com.mojang.blaze3d.platform.Window.class.getDeclaredMethod("onFocus", long.class, boolean.class);
					focus.setAccessible(true);
					focus.invoke(client.getWindow(), client.getWindow().handle(), true);
				} catch (ReflectiveOperationException e) {
					throw new IllegalStateException(e);
				}
				client.mouseHandler.grabMouse();
			});
			context.waitTick();
			double[] cursor = context.computeOnClient(client -> new double[] {client.mouseHandler.xpos(), client.mouseHandler.ypos()});
			for (int step = 1; step <= 10; step++) {
				context.getInput().setCursorPos(cursor[0] + step * 40.0, cursor[1]);
				context.waitTick();
			}

			context.waitTicks(3);
			context.takeScreenshot("21_freelook");
			// Kółko myszy oddala kamerę (w dół = dalej), a slot w hotbarze się nie zmienia.
			double[] zoom = context.computeOnClient(client -> new double[] {
				client.gameRenderer.mainCamera().position().distanceTo(client.player.getEyePosition()), client.player.getInventory().getSelectedSlot()
			});
			for (int notch = 0; notch < 6; notch++) {
				context.getInput().scroll(-1.0);
			}

			context.waitTicks(3);
			context.takeScreenshot("21b_freelook_zoom_out");
			double[] zoomed = context.computeOnClient(client -> new double[] {
				client.gameRenderer.mainCamera().position().distanceTo(client.player.getEyePosition()), client.player.getInventory().getSelectedSlot()
			});
			if (zoomed[0] < zoom[0] + 2.0 || zoomed[1] != zoom[1]) {
				throw new AssertionError("Freelook: kolko nie oddala kamery (odleglosc " + zoom[0] + " -> " + zoomed[0] + ", slot " + zoom[1] + " -> " + zoomed[1] + ")");
			}
			String look = context.computeOnClient(client -> Modules.FREELOOK.active() + "/"
				+ (Math.abs(client.player.getYRot() - yawBefore) < 0.01F) + "/" + (Math.abs(client.gameRenderer.mainCamera().yRot() - yawBefore) > 5.0F));
			context.getInput().releaseKey(options -> pl.kerf.project.module.Freelook.key);
			context.waitTicks(2);
			boolean restored = context.computeOnClient(client -> !Modules.FREELOOK.active() && client.options.getCameraType().isFirstPerson());
			context.runOnClient(client -> Modules.FREELOOK.setEnabled(false));
			if (!"true/true/true".equals(look) || !restored) {
				throw new AssertionError("Freelook: (aktywny/postac stoi/kamera obrocona) = " + look + ", widok wrocil = " + restored);
			}

			// /calc: kolejność działań, nawiasy, przecinek, potęga; złe wyrażenie daje błąd zamiast wyniku.
			String calc = pl.kerf.project.MathExpr.format(pl.kerf.project.MathExpr.eval("2+2*3")) + " "
				+ pl.kerf.project.MathExpr.format(pl.kerf.project.MathExpr.eval("(1+2)^2")) + " "
				+ pl.kerf.project.MathExpr.format(pl.kerf.project.MathExpr.eval("2,5*2")) + " "
				+ pl.kerf.project.MathExpr.format(pl.kerf.project.MathExpr.eval("sqrt(16)-10/4"));
			boolean rejected;
			try {
				pl.kerf.project.MathExpr.eval("2+");
				rejected = false;
			} catch (IllegalArgumentException e) {
				rejected = true;
			}

			if (!"8 9 5 1.5".equals(calc) || !rejected) {
				throw new AssertionError("/calc: " + calc + ", odrzuca bledne = " + rejected);
			}

			context.runOnClient(client -> client.player.connection.sendCommand("calc 2+2*3"));
			context.waitTicks(3);
			context.takeScreenshot("22_calc_chat");

			// GUI Theme: menu w motywie "Las", potem z własnym (pomarańczowym) akcentem; na koniec domyślny.
			context.runOnClient(client -> SettingsScreen.select(Modules.GUI_THEME));
			context.setScreen(() -> new SettingsScreen(null));
			for (int i = 0; i < Theme.THEME_NAMES.length; i++) {
				int theme = i;
				context.runOnClient(client -> Modules.GUI_THEME.theme.set(theme));
				context.waitTicks(3);
				context.takeScreenshot("23_theme_" + theme);
			}

			// Układy menu: pływający (wyszukiwarka nad panelami) i jego lustro; ekran przebudowuje się sam po zmianie.
			context.runOnClient(client -> Modules.GUI_THEME.theme.set(0));
			// Escape zamyka panel ustawień GUI Theme, żeby było widać listę modułów i wyszukiwarkę.
			context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			context.waitTicks(10);
			for (int layout = 1; layout <= 2; layout++) {
				int value = layout;
				context.runOnClient(client -> Modules.GUI_THEME.layout.set(value));
				context.waitTicks(3);
				context.takeScreenshot("23c_layout_" + layout);
			}

			context.runOnClient(client -> SettingsScreen.select(Modules.ARMOR_HUD));
			context.setScreen(() -> new SettingsScreen(null));
			context.waitTicks(10);
			context.takeScreenshot("23d_layout_2_module");
			context.runOnClient(client -> Modules.GUI_THEME.layout.set(0));

			// Język: każdy opis, etykieta i opcja ustawień ma tłumaczenie (oprócz nazw, które już są po angielsku).
			java.util.Set<String> sameInEnglish = java.util.Set.of("English", "Polski", "Ocean");
			java.util.List<String> missing = new java.util.ArrayList<>();
			for (pl.kerf.project.module.Module module : Modules.ALL) {
				java.util.List<String> texts = new java.util.ArrayList<>(java.util.List.of(module.description));
				for (pl.kerf.project.config.Setting<?> setting : module.settings) {
					if (setting.hidden) {
						continue;
					}

					boolean axis = setting instanceof pl.kerf.project.config.Setting.Num num && num.axis != 0;
					if (!axis) {
						texts.add(setting.label);
					}

					if (setting instanceof pl.kerf.project.config.Setting.Mode mode) {
						texts.addAll(java.util.List.of(mode.options));
					}
				}

				for (String text : texts) {
					if (!pl.kerf.project.Lang.has(text) && !sameInEnglish.contains(text)) {
						missing.add(module.id + ": " + text);
					}
				}
			}

			if (!missing.isEmpty()) {
				throw new AssertionError("Brak tłumaczeń: " + missing);
			}

			// Panel Settings po angielsku (domyślnie) i całe menu po polsku po zmianie języka.
			context.runOnClient(client -> SettingsScreen.select(Modules.GUI_THEME));
			context.setScreen(() -> new SettingsScreen(null));
			context.waitTicks(10);
			context.takeScreenshot("23e_settings_panel_en");
			context.runOnClient(client -> Modules.GUI_THEME.language.set(1));
			context.waitTicks(3);
			context.takeScreenshot("23f_settings_panel_pl");
			context.runOnClient(client -> Modules.GUI_THEME.language.set(0));
			context.runOnClient(client -> {
				Modules.GUI_THEME.theme.set(2);
				Modules.GUI_THEME.customAccent.set(true);
				Modules.GUI_THEME.accent.set(0xFFFF8A00);
			});
			context.waitTicks(2);
			context.takeScreenshot("23b_theme_polnoc_custom_accent");
			context.runOnClient(client -> {
				Modules.GUI_THEME.reset();
				Modules.GUI_THEME.sync();
			});
			context.setScreen(() -> null);

			// MC-263293 (wbudowane): Toggle Sprint i Toggle Sneak zostają włączone po śmierci i respawnie.
			context.runOnClient(client -> {
				client.options.toggleSprint().set(true);
				client.options.toggleCrouch().set(true);
			});
			context.getInput().pressKey(options -> options.keySprint);
			context.getInput().pressKey(options -> options.keyShift);
			context.waitTick();
			server.runCommand("kill @p");
			context.waitTicks(30);
			context.clickScreenButton("deathScreen.respawn");
			context.waitTicks(20);
			String toggles = context.computeOnClient(client -> client.options.keySprint.isDown() + "/" + client.options.keyShift.isDown());
			// To samo bez ekranu śmierci (natychmiastowy respawn, częsty na serwerach PvP): tu vanilla zawsze gubiła toggle.
			server.runCommand("gamerule immediate_respawn true");
			server.runCommand("kill @p");
			context.waitTicks(30);
			toggles += " | " + context.computeOnClient(client -> client.options.keySprint.isDown() + "/" + client.options.keyShift.isDown());
			server.runCommand("gamerule immediate_respawn false");
			context.runOnClient(client -> {
				client.options.toggleSprint().set(false);
				client.options.toggleCrouch().set(false);
			});
			if (!"true/true | true/true".equals(toggles)) {
				throw new AssertionError("Toggle sprint/sneak po respawnie (sprint/sneak): " + toggles);
			}

			// Klawisz menu: domyślnie prawy Shift otwiera menu moda.
			context.waitTicks(5);
			context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
			context.waitTicks(2);
			if (!context.computeOnClient(client -> client.gui.screen() instanceof SettingsScreen)) {
				throw new AssertionError("Prawy Shift nie otworzyl menu moda");
			}

			// Zmiana klawisza w samym menu: klik w "Menu: ...", potem K. Ten sam klawisz ma być w Sterowaniu.
			double[] button = context.computeOnClient(client -> {
				double scale = client.getWindow().getGuiScale();
				for (net.minecraft.client.gui.components.events.GuiEventListener element : client.gui.screen().children()) {
					if (element instanceof net.minecraft.client.gui.components.AbstractWidget widget && widget.getMessage().getString().startsWith("Menu:")) {
						return new double[] {(widget.getX() + widget.getWidth() / 2.0) * scale, (widget.getY() + widget.getHeight() / 2.0) * scale};
					}
				}

				throw new AssertionError("Brak przycisku klawisza menu w menu moda");
			});
			context.getInput().setCursorPos(button[0], button[1]);
			context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
			context.waitTick();
			context.takeScreenshot("17_menu_key_capture");
			context.getInput().pressKey(GLFW.GLFW_KEY_K);
			context.waitTick();
			context.takeScreenshot("17b_menu_key_set");
			String bound = context.computeOnClient(client -> {
				for (net.minecraft.client.KeyMapping key : client.options.keyMappings) {
					if (key.getName().equals("key.aio_pvp.open_menu")) {
						return key.saveString();
					}
				}

				return "brak";
			});
			context.setScreen(() -> null);
			context.getInput().pressKey(GLFW.GLFW_KEY_K);
			context.waitTicks(2);
			boolean opened = context.computeOnClient(client -> client.gui.screen() instanceof SettingsScreen);
			context.setScreen(() -> null);
			context.runOnClient(client -> pl.kerf.project.gui.ModuleKeys.setMenuKey(GLFW.GLFW_KEY_RIGHT_SHIFT));
			if (!"key.keyboard.k".equals(bound) || !opened) {
				throw new AssertionError("Zmiana klawisza menu nie zadzialala: " + bound + ", otwarte=" + opened);
			}

			// Command Keys: bind J -> /trigger (komenda bez uprawnień, świat testowy nie ma cheatów). Panel z bindem na zrzucie, potem J w grze.
			server.runCommand("scoreboard objectives add aio_bind trigger");
			server.runCommand("scoreboard players enable @a aio_bind");
			context.runOnClient(client -> {
				Modules.COMMAND_KEYS.setEnabled(true);
				Modules.COMMAND_KEYS.binds.clear();
				Modules.COMMAND_KEYS.binds.add(new Modules.CommandKeys.Bind(GLFW.GLFW_KEY_J, "/trigger aio_bind set 7"));
				Modules.COMMAND_KEYS.binds.add(new Modules.CommandKeys.Bind(-1, "gg"));
				SettingsScreen.select(Modules.COMMAND_KEYS);
			});
			context.setScreen(() -> new SettingsScreen(null));
			context.waitTicks(2);
			// Przewinięcie panelu w dół, żeby na zrzucie były wiersze bindów (nad nimi jest duży podgląd).
			int[] win = context.computeOnClient(client -> new int[] {client.getWindow().getScreenWidth(), client.getWindow().getScreenHeight()});
			context.getInput().setCursorPos(win[0] * 0.6, win[1] * 0.6);
			context.getInput().scroll(-10);
			context.waitTicks(10);
			context.takeScreenshot("18_command_keys");
			context.setScreen(() -> null);
			context.waitTicks(5);
			context.getInput().holdKeyFor(GLFW.GLFW_KEY_J, 3);
			context.waitTicks(10);
			int score = server.computeOnServer(s -> {
				net.minecraft.world.scores.Scoreboard scoreboard = s.getScoreboard();
				net.minecraft.world.scores.ReadOnlyScoreInfo value = scoreboard.getPlayerScoreInfo(
					s.getPlayerList().getPlayers().get(0), scoreboard.getObjective("aio_bind")
				);
				return value == null ? 0 : value.value();
			});
			if (score != 7) {
				throw new AssertionError("Command Keys: J nie wyslal /trigger (wynik " + score + ")");
			}

			// Bind na bocznym przycisku myszy (Mouse 4), krótkie kliknięcie (wciśnięcie i puszczenie w jednym ticku).
			server.runCommand("scoreboard players enable @a aio_bind");
			context.runOnClient(client -> Modules.COMMAND_KEYS.binds.add(
				new Modules.CommandKeys.Bind(pl.kerf.project.gui.ModuleKeys.MOUSE + GLFW.GLFW_MOUSE_BUTTON_4, "/trigger aio_bind set 9")
			));
			context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_4);
			context.waitTicks(10);
			int mouseScore = server.computeOnServer(s -> {
				net.minecraft.world.scores.Scoreboard scoreboard = s.getScoreboard();
				net.minecraft.world.scores.ReadOnlyScoreInfo value = scoreboard.getPlayerScoreInfo(
					s.getPlayerList().getPlayers().get(0), scoreboard.getObjective("aio_bind")
				);
				return value == null ? 0 : value.value();
			});
			context.runOnClient(client -> Modules.COMMAND_KEYS.binds.clear());
			if (mouseScore != 9) {
				throw new AssertionError("Command Keys: Mouse 4 nie wyslal /trigger (wynik " + mouseScore + ")");
			}
		}
	}
}
