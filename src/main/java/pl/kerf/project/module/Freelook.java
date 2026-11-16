package pl.kerf.project.module;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.MathHelper;
import pl.kerf.project.config.Setting;

/**
 * Freelook: przy trzymanym klawiszu kamera w trzeciej osobie obraca się wokół gracza, a postać dalej patrzy tam,
 * gdzie patrzyła (ruch myszy idzie do kamery, nie do gracza). Po puszczeniu wraca poprzedni widok.
 * Zabroniony na części serwerów, więc działa jak każdy {@link RiskyModule} (ostrzeżenie, blokada do restartu).
 */
public final class Freelook extends RiskyModule {
	/** Klawisz trzymania (Opcje -> Sterowanie, domyślnie lewy Alt); ustawiany przy rejestracji klawiszy. */
	public static KeyBinding key;
	/** Jak w Perspective Mod: klawisz trzymany albo przełączany, i z jakiego widoku się rozglądasz. */
	public final Setting.Mode keyMode = this.add(new Setting.Mode("key_mode", "Klawisz", 0, "Przytrzymaj", "Przełącz"));
	public final Setting.Mode view = this.add(new Setting.Mode("view", "Widok", 0, "Trzecia osoba z tyłu", "Trzecia osoba z przodu", "Pierwsza osoba"));
	private boolean active;
	private boolean toggled;
	private float yaw;
	private float pitch;
	private Perspective previous;
	/** Odległość kamery w trzeciej osobie (bloki), zmieniana kółkiem myszy; na start jak w vanilli. */
	private float distance = DEFAULT_DISTANCE;
	private static final float DEFAULT_DISTANCE = 4.0F;

	public Freelook() {
		super("freelook", "Freelook", "Trzymaj klawisz (domyślnie lewy Alt), żeby rozglądać się kamerą bez obracania postaci. Na części serwerów zabronione.",
			Category.VISUALS, "Ten moduł pozwala rozglądać się wokół postaci bez obracania jej.");
	}

	public boolean active() {
		return this.active;
	}

	public float yaw() {
		return this.yaw;
	}

	public float pitch() {
		return this.pitch;
	}

	/** Mnożnik vanillowej odległości kamery (1 = bez zmian). */
	public float distanceScale() {
		return this.distance / DEFAULT_DISTANCE;
	}

	/** Kółko myszy przy Freelooku w trzeciej osobie: pół bloku na ząbek, od 1 do 20 bloków. */
	public boolean scroll(MinecraftClient client, double amount) {
		if (!this.active || client.options.getPerspective().isFirstPerson() || amount == 0.0) {
			return false;
		}

		this.distance = MathHelper.clamp(this.distance - (float) Math.signum(amount) * 0.5F, 1.0F, 20.0F);
		return true;
	}

	public void tick(MinecraftClient client) {
		boolean usable = this.on() && key != null && client.player != null && client.currentScreen == null;
		boolean want;
		if (this.keyMode.is(1)) {
			while (key != null && key.wasPressed()) {
				this.toggled = !this.toggled;
			}

			want = usable && this.toggled;
		} else {
			want = usable && key.isPressed();
		}

		if (!usable) {
			this.toggled = false;
		}

		if (want && !this.active) {
			this.active = true;
			this.distance = DEFAULT_DISTANCE;
			this.yaw = client.player.getYaw();
			this.pitch = client.player.getPitch();
			this.previous = client.options.getPerspective();
			client.options.setPerspective(switch (this.view.get()) {
				case 1 -> Perspective.THIRD_PERSON_FRONT;
				case 2 -> Perspective.FIRST_PERSON;
				default -> Perspective.THIRD_PERSON_BACK;
			});
		} else if (!want && this.active) {
			this.active = false;
			if (this.previous != null) {
				client.options.setPerspective(this.previous);
			}
		}
	}

	/** Ruch myszy przy Freelooku: te same przeliczniki co Entity.changeLookDirection, tylko dla kamery. */
	public void turn(double dx, double dy) {
		this.yaw += (float) dx * 0.15F;
		this.pitch = MathHelper.clamp(this.pitch + (float) dy * 0.15F, -90.0F, 90.0F);
	}
}
