package pl.kerf.project.module;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import pl.kerf.project.config.ConfigManager;
import pl.kerf.project.config.Setting;

/**
 * Głośność każdego dźwięku Minecrafta osobno (po jego nazwie, np. "entity.player.hurt"), w procentach 0-200.
 * Edytuje się w osobnym ekranie ({@link pl.kerf.project.gui.SoundsScreen}); zapis w ukrytym ustawieniu "data"
 * jako "id=procent;id=procent" tylko dla dźwięków różnych od 100%.
 */
public final class Sounds extends Module {
	public static final int MAX_PERCENT = 200;
	private final Setting.Str data = this.add(new Setting.Str("data", "Głośności", "")).hide();
	/** Sparsowane {@link #data}; null = trzeba wczytać (start, reset). */
	private Map<String, Integer> volumes;

	public Sounds() {
		super("sounds", "Sounds", "Zmień głośność dowolnego dźwięku w grze po jego nazwie. Nie wiesz, jak się nazywa? Włącz napisy (Opcje, Ułatwienia dostępu).", Category.MISC, false);
		this.preview = false;
	}

	@Override
	public void reset() {
		super.reset();
		this.volumes = null;
	}

	private Map<String, Integer> volumes() {
		if (this.volumes == null) {
			this.volumes = new HashMap<>();
			for (String part : this.data.get().split(";")) {
				String[] kv = part.split("=");
				if (kv.length == 2) {
					try {
						this.volumes.put(kv[0], Math.max(0, Math.min(MAX_PERCENT, Integer.parseInt(kv[1].strip()))));
					} catch (NumberFormatException ignored) {
						// Zepsuty wpis pomijamy, reszta zostaje.
					}
				}
			}
		}

		return this.volumes;
	}

	/** Mnożnik głośności dla dźwięku (1 = bez zmian); wołane przy każdym odtworzeniu dźwięku. */
	public float multiplier(Identifier id) {
		if (!this.on()) {
			return 1.0F;
		}

		Integer percent = this.volumes().get(id.toString());
		return percent == null ? 1.0F : percent / 100.0F;
	}

	public int percent(String id) {
		return this.volumes().getOrDefault(id, 100);
	}

	public int customized() {
		return this.volumes().size();
	}

	/** Ustawia głośność w procentach; 100 usuwa wpis. */
	public void set(String id, int percent) {
		if (percent == 100) {
			this.volumes().remove(id);
		} else {
			this.volumes().put(id, Math.max(0, Math.min(MAX_PERCENT, percent)));
		}

		StringBuilder out = new StringBuilder();
		this.volumes().forEach((key, value) -> out.append(out.isEmpty() ? "" : ";").append(key).append('=').append(value));
		this.data.set(out.toString());
		ConfigManager.save();
	}
}
