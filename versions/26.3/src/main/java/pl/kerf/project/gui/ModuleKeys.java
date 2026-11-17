package pl.kerf.project.gui;

import pl.kerf.project.Lang;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import pl.kerf.project.config.ConfigManager;
import pl.kerf.project.gui.theme.Theme;
import pl.kerf.project.module.Module;
import pl.kerf.project.module.Modules;
import pl.kerf.project.module.RiskyModule;

/** Opcjonalne skróty funkcji. Działają tylko w grze (bez otwartego menu) i tylko te, które gracz sam ustawił. */
public final class ModuleKeys {
	private static final Map<Module, Boolean> HELD = new HashMap<>();
	/** Klawisze bindów Command Keys wciśnięte w poprzednim ticku (bind odpala raz na wciśnięcie, nie co tick). */
	private static final Set<Integer> HELD_BINDS = new HashSet<>();
	/** No Sign GUI ma też skrót w Opcje -> Sterowanie; domyślnie nieprzypisany, jak wszystkie skróty moda. */
	private static KeyMapping noSignGui;
	/**
	 * Klawisz otwierający menu moda. Zwykły klawisz Minecrafta (Opcje -> Sterowanie, zapis w options.txt),
	 * a menu moda zmienia ten sam obiekt, więc oba miejsca zawsze pokazują to samo.
	 */
	private static KeyMapping openMenu;
	/** Kody >= MOUSE to przyciski myszy (MOUSE + numer przycisku GLFW); niższe to klawisze klawiatury. */
	public static final int MOUSE = 1000;
	/** Wciśnięte przyciski myszy, z MouseMixin (GLFW nie ma prostego "czy wciśnięty" jak dla klawiatury). */
	private static final Set<Integer> MOUSE_DOWN = new HashSet<>();
	/** Przyciski wciśnięte od ostatniego ticku: szybkie kliknięcie (krótsze niż tick) też odpala bind. */
	private static final Set<Integer> MOUSE_CLICKED = new HashSet<>();

	private ModuleKeys() {
	}

	public static void register() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("aio_pvp", "main"));
		openMenu = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.aio_pvp.open_menu", InputConstants.Type.KEYBOARD, InputConstants.KEY_RSHIFT, category));
		pl.kerf.project.module.Freelook.key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.aio_pvp.freelook", InputConstants.Type.KEYBOARD, InputConstants.KEY_LALT, category));
		noSignGui = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.aio_pvp.no_sign_gui", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), category));
	}

	public static void tick(Minecraft client) {
		while (openMenu != null && openMenu.consumeClick()) {
			if (client.gui.screen() == null) {
				client.gui.setScreen(new SettingsScreen(null));
			}
		}

		while (noSignGui != null && noSignGui.consumeClick()) {
			toggle(Modules.NO_SIGN_GUI);
		}

		commandKeys(client);
		for (Module module : Modules.ALL) {
			if (module.key < 0) {
				continue;
			}

			boolean down = client.gui.screen() == null && down(client, module.key);
			boolean wasDown = HELD.getOrDefault(module, false);
			HELD.put(module, down);
			if (down && !wasDown) {
				toggle(module);
			}
		}

		MOUSE_CLICKED.clear();
	}

	/** Command Keys: wciśnięty klawisz bindu wysyła komendę ("/spawn") albo wiadomość. Tylko w grze, bez otwartego ekranu. */
	private static void commandKeys(Minecraft client) {
		Set<Integer> down = new HashSet<>();
		boolean active = Modules.COMMAND_KEYS.on() && client.gui.screen() == null && client.player != null;
		for (Modules.CommandKeys.Bind bind : Modules.COMMAND_KEYS.binds) {
			if (!active || bind.key < 0 || !down(client, bind.key)) {
				continue;
			}

			down.add(bind.key);
			if (!HELD_BINDS.contains(bind.key)) {
				run(client, bind.command.trim());
			}
		}

		HELD_BINDS.clear();
		HELD_BINDS.addAll(down);
	}

	/** 26.3 (SDL) numeruje przyciski myszy od 1 (1 lewy, 2 środkowy, 3 prawy); mod liczy po staremu (0 lewy, 1 prawy, 2 środkowy, 3+ boczne). */
	public static int button(int sdl) {
		return switch (sdl) {
			case 1 -> 0;
			case 3 -> 1;
			case 2 -> 2;
			default -> sdl - 1;
		};
	}

	private static int sdlButton(int button) {
		return switch (button) {
			case 0 -> 1;
			case 1 -> 3;
			case 2 -> 2;
			default -> button + 1;
		};
	}

	public static void mouseButton(int button, boolean pressed) {
		if (pressed) {
			MOUSE_DOWN.add(button);
			MOUSE_CLICKED.add(button);
		} else {
			MOUSE_DOWN.remove(button);
		}
	}

	/** Czy klawisz albo przycisk myszy (kod >= MOUSE) jest wciśnięty. */
	private static boolean down(Minecraft client, int code) {
		if (code >= MOUSE) {
			return MOUSE_DOWN.contains(code - MOUSE) || MOUSE_CLICKED.contains(code - MOUSE);
		}

		return InputConstants.isKeyDown(code);
	}

	/** Klawisz Minecrafta dla kodu moda (klawiatura albo mysz). */
	private static InputConstants.Key key(int code) {
		return code >= MOUSE ? InputConstants.Type.MOUSE.getOrCreate(sdlButton(code - MOUSE)) : InputConstants.Type.KEYBOARD.getOrCreate(code);
	}

	/** Wysyła tekst bindu tak, jakby gracz wpisał go na czacie. Pusty bind (albo samo "/") nic nie robi. */
	public static void run(Minecraft client, String text) {
		if (text.startsWith("/")) {
			if (text.length() > 1) {
				client.player.connection.sendCommand(text.substring(1));
			}
		} else if (!text.isEmpty()) {
			client.player.connection.sendChat(text);
		}
	}

	private static void toggle(Module module) {
		module.toggle();
		ConfigManager.save();
		if (module instanceof RiskyModule) {
			// Moduł z szarej strefy sam pokazuje okno ostrzeżenia albo "zablokowany do restartu"; zwykłe "Włączone/Wyłączone" by kłamało.
			return;
		}

		Notifications.push(module.name, module.on() ? "Włączone" : "Wyłączone", module.on() ? Theme.SUCCESS : Theme.TEXT_3);
	}

	/** Nazwa klawisza menu, jak w Sterowaniu ("Brak", gdy nieprzypisany). */
	public static String menuKeyName() {
		return openMenu.isUnbound() ? Lang.t("Brak") : openMenu.getTranslatedKeyMessage().getString();
	}

	/** Ustawia klawisz menu (-1 = brak, kod >= MOUSE = przycisk myszy) i zapisuje options.txt, jak zrobiłoby to Sterowanie. */
	public static void setMenuKey(int key) {
		openMenu.setKey(key < 0 ? InputConstants.UNKNOWN : key(key));
		KeyMapping.resetMapping();
		Minecraft.getInstance().options.save();
	}

	/** Nazwa klawisza do wyświetlenia ("Brak", gdy nieprzypisany). */
	public static String name(int key) {
		if (key < 0) {
			return Lang.t("Brak");
		}

		Component text = key(key).getDisplayName();
		return text.getString();
	}
}
