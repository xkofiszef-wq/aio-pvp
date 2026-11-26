package pl.kerf.project.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.component.DataComponents;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import org.joml.Matrix3x2fStack;
import pl.kerf.project.AioPvp;
import pl.kerf.project.mixin.SpriteContentsAccessor;
import pl.kerf.project.module.Modules;

/**
 * Saturation: złota obwódka ikonek głodu, jak w AppleSkin. Obwódka powstaje z ikonek aktywnego texturepacka:
 * <ol>
 * <li>vanilla i większość packów: obwódka to piksele pustej ikonki, których pełna nie zakrywa — te robią się złote;</li>
 * <li>pack z obwódką wrysowaną w pełną ikonkę: złoty robi się jej ciemny brzeg;</li>
 * <li>pack bez obwódki: złota obwódka dorysowana wokół ikonki (1 piksel tekstury na zewnątrz).</li>
 * </ol>
 * Jedna ikonka = 2 punkty saturacji; reszta poniżej 2 punktów to obwódka tylko przy kształcie połówki.
 */
public final class SaturationOutline {
	private static final int GOLD = 0xFFFFC21A;
	/** Ciemniejsze niż to (jasność 0-255) liczy się jako obwódka wrysowana w ikonkę. */
	private static final int DARK = 50;
	/** Zestawy: zwykły i z efektem Głodu (pack może mieć dla nich inne kształty). Kolejność: pusta, pełna, połówka. */
	private static final Identifier[][] SPRITES = {
		{Identifier.withDefaultNamespace("hud/food_empty"), Identifier.withDefaultNamespace("hud/food_full"), Identifier.withDefaultNamespace("hud/food_half")},
		{Identifier.withDefaultNamespace("hud/food_empty_hunger"), Identifier.withDefaultNamespace("hud/food_full_hunger"), Identifier.withDefaultNamespace("hud/food_half_hunger")}
	};
	/** [zestaw][0 = pełna obwódka, 1 = połówka] */
	private static final Identifier[][] OUTLINES = new Identifier[2][2];
	/** Pełna ikonka, z której zbudowano obwódki; inny obiekt = przeładowany texturepack, budujemy od nowa. */
	private static final TextureAtlasSprite[] BUILT_FROM = new TextureAtlasSprite[2];
	private static final int[] WIDTH = new int[2];
	private static final int[] HEIGHT = new int[2];

	static {
		for (int set = 0; set < 2; set++) {
			for (int part = 0; part < 2; part++) {
				OUTLINES[set][part] = Identifier.fromNamespaceAndPath(AioPvp.MOD_ID, "saturation_outline_" + set + "_" + part);
			}
		}
	}

	private SaturationOutline() {
	}

	/** Gdzie vanilla narysowała ikonki w tej klatce (przy saturacji 0 pasek losowo drga, więc pozycje nie są stałe). */
	private static final int[] ICON_X = new int[10];
	private static final int[] ICON_Y = new int[10];
	private static int recorded;

	/** Początek vanilla renderFood: nowe pozycje ikonek. */
	public static void beginFood() {
		recorded = 0;
	}

	/** Vanilla rysuje tło ikonki j (od prawej) w tym miejscu. */
	public static void iconAt(int x, int y) {
		if (recorded < ICON_X.length) {
			ICON_X[recorded] = x;
			ICON_Y[recorded] = y;
			recorded++;
		}
	}

	/**
	 * Obwódki saturacji, a z jedzeniem w ręce (opcja "Podgląd jedzenia w ręce") mrugające ikonki głodu i obwódki,
	 * które to jedzenie doda. Wartości jak w HungerManager: głód do 20, saturacja najwyżej tyle co głód.
	 */
	public static void render(GuiGraphics context, Player player, int top, int right) {
		FoodData hunger = player.getFoodData();
		int food = hunger.getFoodLevel();
		float saturation = hunger.getSaturationLevel();
		int set = player.hasEffect(MobEffects.HUNGER) ? 1 : 0;
		build(set);
		for (int j = 0; j < 10 && saturation > j * 2; j++) {
			outline(context, set, saturation >= j * 2 + 2 ? 0 : 1, x(j, right), y(j, top), 0xFFFFFFFF);
		}

		FoodProperties eaten = Modules.SATURATION.foodPreview.on() ? heldFood(player) : null;
		if (eaten == null) {
			return;
		}

		int newFood = Math.min(20, food + eaten.nutrition());
		float newSaturation = Math.min(newFood, saturation + eaten.saturation());
		int color = blinkAlpha() << 24 | 0xFFFFFF;
		for (int j = 0; j < 10; j++) {
			int x = x(j, right);
			int y = y(j, top);
			if (food < j * 2 + 2 && newFood >= j * 2 + 1) {
				context.blitSprite(RenderPipelines.GUI_TEXTURED, SPRITES[set][newFood >= j * 2 + 2 ? 1 : 2], x, y, 9, 9, color);
			}

			// Obwódka, której jeszcze nie ma: cała ikonka albo połówka, gdy obecna saturacja jej nie pokrywa.
			boolean whole = newSaturation >= j * 2 + 2;
			if (saturation < j * 2 + 2 && newSaturation > j * 2 && (whole || saturation <= j * 2)) {
				outline(context, set, whole ? 0 : 1, x, y, color);
			}
		}
	}

	private static int x(int j, int right) {
		return j < recorded ? ICON_X[j] : right - j * 8 - 9;
	}

	private static int y(int j, int top) {
		return j < recorded ? ICON_Y[j] : top;
	}

	/** Jedzenie w głównej ręce, a bez niego w drugiej; null = nic do zjedzenia. */
	private static FoodProperties heldFood(Player player) {
		FoodProperties main = player.getMainHandItem().get(DataComponents.FOOD);
		return main != null ? main : player.getOffhandItem().get(DataComponents.FOOD);
	}

	/** Płynne mruganie raz na sekundę, od ledwo widocznego do pełnego. */
	private static int blinkAlpha() {
		double wave = 0.5 + 0.5 * Math.sin(Util.getMillis() / 1000.0 * 2.0 * Math.PI);
		return (int) Math.round(255.0 * (0.2 + 0.8 * wave));
	}

	/** Tekstura ma 1 piksel zapasu z każdej strony (na dorysowaną obwódkę) i skalę ikonki z packa (np. 16x16 w 9x9). */
	private static void outline(GuiGraphics context, int set, int part, int x, int y, int color) {
		int w = WIDTH[set];
		int h = HEIGHT[set];
		Matrix3x2fStack matrices = context.pose();
		matrices.pushMatrix();
		matrices.translate(x, y);
		matrices.scale(9.0F / w, 9.0F / h);
		context.blit(RenderPipelines.GUI_TEXTURED, OUTLINES[set][part], -1, -1, 0.0F, 0.0F, w + 2, h + 2, w + 2, h + 2, w + 2, h + 2, color);
		matrices.popMatrix();
	}

	private static void build(int set) {
		TextureAtlas atlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.GUI);
		TextureAtlasSprite full = atlas.getSprite(SPRITES[set][1]);
		if (full == BUILT_FROM[set]) {
			return;
		}

		BUILT_FROM[set] = full;
		Icon empty = new Icon(atlas.getSprite(SPRITES[set][0]));
		Icon filled = new Icon(full);
		Icon half = new Icon(atlas.getSprite(SPRITES[set][2]));
		int w = filled.w;
		int h = filled.h;
		boolean[][] outline = outline(empty, filled, w, h);
		NativeImage whole = new NativeImage(w + 2, h + 2, true);
		NativeImage partial = new NativeImage(w + 2, h + 2, true);
		for (int x = -1; x <= w; x++) {
			for (int y = -1; y <= h; y++) {
				boolean on = outline[x + 1][y + 1];
				whole.setPixel(x + 1, y + 1, on ? GOLD : 0);
				partial.setPixel(x + 1, y + 1, on && inHalf(filled, half, x, y) ? GOLD : 0);
			}
		}

		WIDTH[set] = w;
		HEIGHT[set] = h;
		register(OUTLINES[set][0], whole);
		register(OUTLINES[set][1], partial);
	}

	/** Maska obwódki w układzie tekstury z zapasem: [x + 1][y + 1] dla x, y od -1 do w/h. */
	public static boolean[][] outline(Icon empty, Icon full, int w, int h) {
		boolean[][] mask = new boolean[w + 2][h + 2];
		// 1. Obwódka z pustej ikonki: to, czego pełna nie zakrywa.
		boolean found = false;
		for (int x = 0; x < w; x++) {
			for (int y = 0; y < h; y++) {
				if (empty.opaque(x, y) && !full.opaque(x, y)) {
					mask[x + 1][y + 1] = found = true;
				}
			}
		}

		if (found) {
			return mask;
		}

		// 2. Obwódka wrysowana w pełną ikonkę: większość jej brzegu jest ciemna.
		int edge = 0;
		int dark = 0;
		for (int x = 0; x < w; x++) {
			for (int y = 0; y < h; y++) {
				if (full.edge(x, y)) {
					edge++;
					if (full.dark(x, y)) {
						dark++;
						mask[x + 1][y + 1] = true;
					}
				}
			}
		}

		if (edge > 0 && dark * 2 >= edge) {
			return mask;
		}

		// 3. Bez obwódki: dorysowujemy ją na zewnątrz kształtu.
		for (int x = -1; x <= w; x++) {
			for (int y = -1; y <= h; y++) {
				boolean outside = !full.opaque(x, y) && !empty.opaque(x, y);
				mask[x + 1][y + 1] = outside && (shape(empty, full, x - 1, y) || shape(empty, full, x + 1, y)
					|| shape(empty, full, x, y - 1) || shape(empty, full, x, y + 1));
			}
		}

		return mask;
	}

	private static boolean shape(Icon empty, Icon full, int x, int y) {
		return empty.opaque(x, y) || full.opaque(x, y);
	}

	/**
	 * Piksel obwódki należy do połówki, gdy nie dotyka (także po skosie) części, której połówce brakuje względem
	 * pełnej ikonki. Pack, w którym połówka niczego nie ucina, dostaje obwódkę wokół samego kształtu połówki.
	 */
	static boolean inHalf(Icon full, Icon half, int x, int y) {
		boolean cut = false;
		boolean touchesMissing = false;
		boolean touchesHalf = false;
		for (int px = 0; px < full.w; px++) {
			for (int py = 0; py < full.h; py++) {
				if (full.opaque(px, py) && !half.opaque(px, py)) {
					cut = true;
					touchesMissing |= Math.abs(px - x) <= 1 && Math.abs(py - y) <= 1;
				}
			}
		}

		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				touchesHalf |= half.opaque(x + dx, y + dy);
			}
		}

		return cut ? !touchesMissing : touchesHalf;
	}

	private static void register(Identifier id, NativeImage image) {
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "AIO PvP saturation outline", image));
	}

	/** Piksele pierwszej klatki ikonki (ARGB, 0 poza obrazkiem). */
	public static final class Icon {
		final int w;
		final int h;
		private final int[] argb;

		Icon(TextureAtlasSprite sprite) {
			this.w = sprite.contents().width();
			this.h = sprite.contents().height();
			NativeImage image = ((SpriteContentsAccessor) sprite.contents()).aio$image();
			this.argb = new int[this.w * this.h];
			for (int x = 0; x < this.w; x++) {
				for (int y = 0; y < this.h; y++) {
					this.argb[y * this.w + x] = image.getPixel(x, y);
				}
			}
		}

		/** Do testu: ikonka z tablicy ARGB wierszami. */
		public Icon(int w, int h, int[] argb) {
			this.w = w;
			this.h = h;
			this.argb = argb;
		}

		int argb(int x, int y) {
			return x < 0 || y < 0 || x >= this.w || y >= this.h ? 0 : this.argb[y * this.w + x];
		}

		boolean opaque(int x, int y) {
			return this.argb(x, y) >>> 24 != 0;
		}

		boolean edge(int x, int y) {
			return this.opaque(x, y) && (!this.opaque(x - 1, y) || !this.opaque(x + 1, y) || !this.opaque(x, y - 1) || !this.opaque(x, y + 1));
		}

		boolean dark(int x, int y) {
			int c = this.argb(x, y);
			return ((c >> 16 & 0xFF) * 299 + (c >> 8 & 0xFF) * 587 + (c & 0xFF) * 114) / 1000 < DARK;
		}
	}
}
