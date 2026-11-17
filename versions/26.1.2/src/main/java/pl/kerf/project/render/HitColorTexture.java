package pl.kerf.project.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import pl.kerf.project.mixin.OverlayTextureAccessor;
import pl.kerf.project.module.Modules;

/**
 * Przepisuje "czerwoną" połowę vanilla'owej tekstury overlay (wiersze 0-7), z której silnik bierze kolor trafienia.
 *
 * <p>Vanilla zawsze próbkuje wiersz 3. W trybie zanikania wiersze poza trzecim niosą 7 poziomów krycia,
 * a {@link #fadeRow} wybiera wiersz według tego, ile czasu zostało do końca efektu.
 */
public final class HitColorTexture {
	private static final int VANILLA = 0xB2FF0000;
	private static final int VANILLA_ROW = 3;
	/** Wiersze dla poziomów zanikania 1..7 (najsłabszy -> najmocniejszy). */
	private static final int[] FADE_ROWS = {0, 1, 2, 4, 5, 6, 7};
	private static long applied = Long.MIN_VALUE;

	private HitColorTexture() {
	}

	public static boolean fading() {
		return Modules.HIT_COLOR.on() && Modules.HIT_COLOR.behavior.is(1);
	}

	/** Wiersz tekstury dla postępu efektu: 1 = świeże trafienie, 0 = koniec. */
	public static int fadeRow(float progress) {
		int level = Math.max(1, Math.min(FADE_ROWS.length, (int) Math.ceil(progress * FADE_ROWS.length)));
		return FADE_ROWS[level - 1];
	}

	/** Wywoływane co tick; teksturę wysyła do GPU tylko po zmianie ustawień. */
	public static void sync(Minecraft client) {
		Modules.HitColor module = Modules.HIT_COLOR;
		long signature = module.on() ? ((long) module.color.rgb() << 16 | (long) module.opacity.i() << 4 | module.behavior.get()) : -1L;
		if (signature == applied || client.gameRenderer == null || client.gameRenderer.overlayTexture() == null) {
			return;
		}

		applied = signature;
		DynamicTexture texture = ((OverlayTextureAccessor) client.gameRenderer.overlayTexture()).aio$getTexture();
		NativeImage image = texture.getPixels();
		if (image == null) {
			return;
		}

		for (int row = 0; row < 8; row++) {
			int pixel = VANILLA;
			if (module.on()) {
				float opacity = module.opacity.f() / 100.0F;
				if (module.behavior.is(1) && row != VANILLA_ROW) {
					int level = java.util.Arrays.binarySearch(FADE_ROWS, row) + 1;
					opacity *= level / (float) FADE_ROWS.length;
				}

				// Shader miesza odwrotnie: alfa 255 = brak koloru, alfa 0 = pełny kolor.
				int alpha = Math.round((1.0F - opacity) * 255.0F);
				pixel = alpha << 24 | module.color.rgb();
			}

			for (int column = 0; column < 16; column++) {
				image.setPixel(column, row, pixel);
			}
		}

		texture.upload();
	}
}
