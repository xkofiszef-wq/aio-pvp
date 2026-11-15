package pl.kerf.project.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteContents;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Atlases;
import net.minecraft.util.Identifier;
import pl.kerf.project.AioPvp;

/**
 * Saturation: złota obwódka na ikonkach głodu, jak w AppleSkin. Obwódka nie jest gotowym obrazkiem: powstaje
 * z ikonki aktywnego texturepacka (jej brzegowe nieprzezroczyste piksele), więc pasuje do każdego kształtu.
 * Jedna ikonka = 2 punkty saturacji; reszta poniżej 2 punktów rysuje się w kształcie połówki.
 */
public final class SaturationOutline {
	private static final int GOLD = 0xFFFFC21A;
	/** Kolejność: pełna, połówka; potem to samo w wersji z efektem Głodu (inna tekstura w texturepacku). */
	private static final Identifier[] SPRITES = {
		Identifier.ofVanilla("hud/food_full"), Identifier.ofVanilla("hud/food_half"),
		Identifier.ofVanilla("hud/food_full_hunger"), Identifier.ofVanilla("hud/food_half_hunger")
	};
	private static final Identifier[] OUTLINES = new Identifier[SPRITES.length];
	/** Sprite, z którego zbudowano obwódkę; inny obiekt = przeładowany texturepack, budujemy od nowa. */
	private static final Sprite[] BUILT_FROM = new Sprite[SPRITES.length];
	private static final int[] WIDTH = new int[SPRITES.length];
	private static final int[] HEIGHT = new int[SPRITES.length];

	static {
		for (int i = 0; i < SPRITES.length; i++) {
			OUTLINES[i] = Identifier.of(AioPvp.MOD_ID, "saturation_outline_" + i);
		}
	}

	private SaturationOutline() {
	}

	/** Te same pozycje co vanilla renderFood: ikonka j od prawej, co 8 px. Drżenie paska jest tylko przy saturacji 0. */
	public static void render(DrawContext context, PlayerEntity player, int top, int right) {
		float saturation = player.getHungerManager().getSaturationLevel();
		int base = player.hasStatusEffect(StatusEffects.HUNGER) ? 2 : 0;
		for (int j = 0; j < 10 && saturation > j * 2; j++) {
			int variant = base + (saturation >= j * 2 + 2 ? 0 : 1);
			outline(variant);
			context.drawTexture(RenderPipelines.GUI_TEXTURED, OUTLINES[variant], right - j * 8 - 9, top, 0.0F, 0.0F, 9, 9,
				WIDTH[variant], HEIGHT[variant], WIDTH[variant], HEIGHT[variant]);
		}
	}

	private static void outline(int variant) {
		Sprite sprite = MinecraftClient.getInstance().getAtlasManager().getAtlasTexture(Atlases.GUI).getSprite(SPRITES[variant]);
		if (sprite == BUILT_FROM[variant]) {
			return;
		}

		BUILT_FROM[variant] = sprite;
		SpriteContents contents = sprite.getContents();
		int w = contents.getWidth();
		int h = contents.getHeight();
		NativeImage image = new NativeImage(w, h, true);
		for (int x = 0; x < w; x++) {
			for (int y = 0; y < h; y++) {
				image.setColorArgb(x, y, edge(contents, x, y, w, h) ? GOLD : 0);
			}
		}

		WIDTH[variant] = w;
		HEIGHT[variant] = h;
		MinecraftClient.getInstance().getTextureManager().registerTexture(OUTLINES[variant],
			new NativeImageBackedTexture(() -> "AIO PvP saturation outline", image));
	}

	/** Nieprzezroczysty piksel, który styka się z przezroczystym (albo z krawędzią obrazka): brzeg kształtu. */
	private static boolean edge(SpriteContents contents, int x, int y, int w, int h) {
		return !clear(contents, x, y, w, h)
			&& (clear(contents, x - 1, y, w, h) || clear(contents, x + 1, y, w, h) || clear(contents, x, y - 1, w, h) || clear(contents, x, y + 1, w, h));
	}

	private static boolean clear(SpriteContents contents, int x, int y, int w, int h) {
		return x < 0 || y < 0 || x >= w || y >= h || contents.isPixelTransparent(0, x, y);
	}
}
