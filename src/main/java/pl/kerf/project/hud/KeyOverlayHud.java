package pl.kerf.project.hud;

import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import pl.kerf.project.config.Setting;
import pl.kerf.project.module.HudModule;

/** Etykiety klawiszy przy slotach hotbara. Przyklejone do hotbara, więc bez przesuwania. */
public final class KeyOverlayHud extends HudModule {
	private static final int POS_SLOT_TOP = 0;
	private static final int POS_SLOT_BOTTOM = 1;
	private static final int BG_NONE = 0;
	private static final int BG_LIGHT = 1;

	public final Setting.Mode position = this.add(new Setting.Mode("position", "Pozycja", 0, "Góra slotu", "Dół slotu", "Nad hotbarem"));
	public final Setting.Mode background = this.add(new Setting.Mode("background", "Tło", 0, "Brak", "Jasne", "Ciemne"));
	public final Setting.Color color = this.add(new Setting.Color("color", "Kolor tekstu", 0xFFFFFFFF, false));
	private static final net.minecraft.util.Identifier HOTBAR = net.minecraft.util.Identifier.ofVanilla("hud/hotbar");
	/** Skrócone nazwy klawiszy (klucz: język + klawisz), żeby nie tłumaczyć i nie dzielić nazw co klatkę. */
	private static final java.util.Map<String, String> LABELS = new java.util.HashMap<>();

	public KeyOverlayHud() {
		super("key_overlay", "Key Overlay", "Klawisze przypisane do slotów hotbara.", false, 0.5F, 1.0F, 0.5F, 1.0F, 0.0F, 0.0F);
	}

	@Override
	public boolean movable() {
		return false;
	}

	@Override
	public int width(MinecraftClient client, boolean editing) {
		return 182;
	}

	@Override
	public int height(MinecraftClient client, boolean editing) {
		return 22;
	}

	@Override
	public void render(DrawContext context, MinecraftClient client, RenderTickCounter tickCounter, boolean editing) {
		// Zawsze przyklejone do vanillowego hotbara.
		this.boundsX = context.getScaledWindowWidth() / 2.0F - 91.0F;
		this.boundsY = context.getScaledWindowHeight() - 22.0F;
		this.boundsW = 182;
		this.boundsH = 22;
		context.getMatrices().pushMatrix();
		context.getMatrices().translate(this.boundsX, this.boundsY);
		ownLayer(context);
		this.draw(context, client, tickCounter, editing);
		context.getMatrices().popMatrix();
	}

	@Override
	public void renderPreview(DrawContext context, MinecraftClient client, int x, int y, int width, int height, float maxScale) {
		this.renderPreview(context, client, x, y, width, height);
	}

	@Override
	public void renderPreview(DrawContext context, MinecraftClient client, int x, int y, int width, int height) {
		// Pierwsze 5 slotów prawdziwego hotbara w dużej skali: w wąskim panelu cały hotbar byłby nieczytelny.
		int slots = 5;
		int contentW = slots * 20 + 2;
		int above = 8;
		float fit = Math.min(2.5F, Math.min((width - 8.0F) / contentW, (height - 8.0F) / (22 + above)));
		context.getMatrices().pushMatrix();
		context.getMatrices().translate(x + (width - contentW * fit) / 2.0F, y + (height - (22 + above) * fit) / 2.0F + above * fit);
		context.getMatrices().scale(fit, fit);
		context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, HOTBAR, 182, 22, 0, 0, 0, 0, 1 + slots * 20, 22);
		context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, HOTBAR, 182, 22, 181, 0, 1 + slots * 20, 0, 1, 22);
		this.drawLabels(context, client, slots);
		context.getMatrices().popMatrix();
	}

	/** Etykiety w lokalnych współrzędnych hotbara: (0,0) to jego lewy górny róg. */
	@Override
	protected void draw(DrawContext context, MinecraftClient client, RenderTickCounter tickCounter, boolean editing) {
		this.drawLabels(context, client, 9);
	}

	private void drawLabels(DrawContext context, MinecraftClient client, int count) {
		KeyBinding[] keys = client.options.hotbarKeys;
		for (int i = 0; i < count && i < keys.length; i++) {
			if (keys[i].isUnbound()) {
				continue;
			}

			KeyBinding key = keys[i];
			String label = LABELS.computeIfAbsent(client.options.language + "|" + key.getBoundKeyTranslationKey(),
				k -> shorten(key.getBoundKeyLocalizedText().getString()));
			int labelW = client.textRenderer.getWidth(label);
			float textW = labelW * 0.5F;
			float slotX = 3 + i * 20;
			float lx;
			float ly;
			if (this.position.is(POS_SLOT_TOP)) {
				lx = slotX;
				ly = 3;
			} else if (this.position.is(POS_SLOT_BOTTOM)) {
				lx = slotX;
				ly = 14.5F;
			} else {
				lx = slotX + 8.0F - textW / 2.0F;
				ly = -6.5F;
			}

			context.getMatrices().pushMatrix();
			context.getMatrices().translate(lx, ly);
			context.getMatrices().scale(0.5F, 0.5F);
			int textColor = this.color.argb();
			if (!this.background.is(BG_NONE)) {
				boolean light = this.background.is(BG_LIGHT);
				context.fill(-2, -2, labelW + 1, 9, light ? 0xD0FFFFFF : 0xA0000000);
				if (light) {
					// Na białym tle biały tekst byłby nieczytelny.
					textColor = 0xFF000000;
				}
			}

			// Napis zaraz po swoim tle (mieści się w nim), więc GUI nie przeszukuje dla niego warstw.
			context.drawText(client.textRenderer, label, 0, 0, textColor, this.background.is(BG_NONE));
			context.getMatrices().popMatrix();
		}
	}

	/** Skraca nazwę klawisza do maks. 3 znaków, np. "Button 4" -> "M4", "Left Shift" -> "LS". */
	private static String shorten(String name) {
		String upper = name.toUpperCase(Locale.ROOT);
		if (upper.length() <= 3) {
			return upper;
		}

		String[] words = upper.trim().split("\\s+");
		String last = words[words.length - 1];
		if (words.length >= 2 && last.chars().allMatch(Character::isDigit)) {
			// "Button 4", "Keypad 7", "Przycisk 4"
			String prefix = words[0].startsWith("B") || words[0].startsWith("P") ? "M" : words[0].substring(0, 1);
			return prefix + last;
		}

		if (words.length >= 2) {
			StringBuilder initials = new StringBuilder();
			for (String word : words) {
				initials.append(word.charAt(0));
			}

			return initials.substring(0, Math.min(3, initials.length()));
		}

		return upper.substring(0, 3);
	}
}
