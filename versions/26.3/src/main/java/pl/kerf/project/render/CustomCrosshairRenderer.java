package pl.kerf.project.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import pl.kerf.project.config.PixelGrid;
import pl.kerf.project.module.Modules;

/** Rysuje celownik z pikseli. Ten sam kod służy grze, podglądowi w menu i edytorowi. */
public final class CustomCrosshairRenderer {
	private CustomCrosshairRenderer() {
	}

	/** Rysuje siatkę pikseli z lewym górnym rogiem w (x, y), każdy piksel jako kwadrat {@code pixel} x {@code pixel}. */
	public static void draw(GuiGraphicsExtractor context, int[] grid, float x, float y, float pixel) {
		int size = PixelGrid.SIZE;
		context.pose().pushMatrix();
		context.pose().translate(x, y);
		context.pose().scale(pixel, pixel);
		for (int i = 0; i < grid.length; i++) {
			int argb = grid[i];
			if (argb >>> 24 != 0) {
				int px = i % size;
				int py = i / size;
				context.fill(px, py, px + 1, py + 1, argb);
			}
		}

		context.pose().popMatrix();
	}

	/** Celownik w grze (bez celu albo na celu), wyśrodkowany tak jak vanillowy (15x15 przy skali 1). */
	public static void drawInGame(GuiGraphicsExtractor context, boolean onTarget) {
		float scale = Modules.CROSSHAIR.scale.f();
		float size = PixelGrid.SIZE * scale;
		// Zaokrąglenie w dół jak w vanilla, żeby piksele trafiały w siatkę ekranu.
		float x = (float) Math.floor((context.guiWidth() - size) / 2.0F);
		float y = (float) Math.floor((context.guiHeight() - size) / 2.0F);
		draw(context, Modules.CROSSHAIR.grid(onTarget).get(), x, y, scale);
	}
}
