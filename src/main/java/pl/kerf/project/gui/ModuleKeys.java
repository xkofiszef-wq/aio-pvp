package pl.kerf.project.gui;

import pl.kerf.project.Lang;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
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
	private static KeyBinding noSignGui;
	/**
	 * Klawisz otwierający menu moda. Zwykły klawisz Minecrafta (Opcje -> Sterowanie, zapis w options.txt),
	 * a menu moda zmienia ten sam obiekt, więc oba miejsca zawsze pokazują to samo.
	 */
	private static KeyBinding openMenu;
	/** Kody >= MOUSE to przyciski myszy (MOUSE + numer przycisku GLFW); niższe to klawisze klawiatury. */
	public static final int MOUSE = 1000;
	/** Wciśnięte przyciski myszy, z MouseMixin (GLFW nie ma prostego "czy wciśnięty" jak dla klawiatury). */
	private static final Set<Integer> MOUSE_DOWN = new HashSet<>();
	/** Przyciski wciśnięte od ostatniego ticku: szybkie kliknięcie (krótsze niż tick) też odpala bind. */
	private static final Set<Integer> MOUSE_CLICKED = new HashSet<>();

	private ModuleKeys() {
	}

	public static void register() {
		KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("aio_pvp", "main"));
		openMenu = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.aio_pvp.open_menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));
		pl.kerf.project.module.Freelook.key = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.aio_pvp.freelook", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, category));
		noSignGui = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.aio_pvp.no_sign_gui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));
	}

	public static void tick(MinecraftClient client) {
		while (openMenu != null && openMenu.wasPressed()) {
			if (client.currentScreen == null) {
				client.setScreen(new SettingsScreen(null));
			}
		}

		while (noSignGui != null && noSignGui.wasPressed()) {
			toggle(Modules.NO_SIGN_GUI);
		}

		commandKeys(client);
		for (Module module : Modules.ALL) {
			if (module.key < 0) {
				continue;
			}

			boolean down = client.currentScreen == null && down(client, module.key);
			boolean wasDown = HELD.getOrDefault(module, false);
			HELD.put(module, down);
			if (down && !wasDown) {
				toggle(module);
			}
		}

		MOUSE_CLICKED.clear();
	}

	/** Command Keys: wciśnięty klawisz bindu wysyła komendę ("/spawn") albo wiadomość. Tylko w grze, bez otwartego ekranu. */
	private static void commandKeys(MinecraftClient client) {
		Set<Integer> down = new HashSet<>();
		boolean active = Modules.COMMAND_KEYS.on() && client.currentScreen == null && client.player != null;
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

	public static void mouseButton(int button, boolean pressed) {
		if (pressed) {
			MOUSE_DOWN.add(button);
			MOUSE_CLICKED.add(button);
		} else {
			MOUSE_DOWN.remove(button);
		}
	}

	/** Czy klawisz albo przycisk myszy (kod >= MOUSE) jest wciśnięty. */
	private static boolean down(MinecraftClient client, int code) {
		if (code >= MOUSE) {
			return MOUSE_DOWN.contains(code - MOUSE) || MOUSE_CLICKED.contains(code - MOUSE);
		}

		return InputUtil.isKeyPressed(client.getWindow(), code);
	}

	/** Klawisz Minecrafta dla kodu moda (klawiatura albo mysz). */
	private static InputUtil.Key key(int code) {
		return code >= MOUSE ? InputUtil.Type.MOUSE.createFromCode(code - MOUSE) : InputUtil.Type.KEYSYM.createFromCode(code);
	}

	/** Wysyła tekst bindu tak, jakby gracz wpisał go na czacie. Pusty bind (albo samo "/") nic nie robi. */
	public static void run(MinecraftClient client, String text) {
		if (text.startsWith("/")) {
			if (text.length() > 1) {
				client.player.networkHandler.sendChatCommand(text.substring(1));
			}
		} else if (!text.isEmpty()) {
			client.player.networkHandler.sendChatMessage(text);
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
		return openMenu.isUnbound() ? Lang.t("Brak") : openMenu.getBoundKeyLocalizedText().getString();
	}

	/** Ustawia klawisz menu (-1 = brak, kod >= MOUSE = przycisk myszy) i zapisuje options.txt, jak zrobiłoby to Sterowanie. */
	public static void setMenuKey(int key) {
		openMenu.setBoundKey(key < 0 ? InputUtil.UNKNOWN_KEY : key(key));
		KeyBinding.updateKeysByCode();
		MinecraftClient.getInstance().options.write();
	}

	/** Nazwa klawisza do wyświetlenia ("Brak", gdy nieprzypisany). */
	public static String name(int key) {
		if (key < 0) {
			return Lang.t("Brak");
		}

		Text text = key(key).getLocalizedText();
		return text.getString();
	}
}
