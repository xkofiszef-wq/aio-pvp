package pl.kerf.project.gui;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2f;
import pl.kerf.project.module.Modules;
import pl.kerf.project.module.Modules.BlockOutline;

/**
 * Podgląd Block Outline: blok trawy z prawdziwych tekstur gry w rzucie izometrycznym, a na nim linie
 * i wypełnienie dokładnie według ustawień (tryb, kolory, grubość, wypełnienie wł./wył.).
 */
final class IsoBlockPreview {
	private static final SpriteIdentifier TOP = block("block/grass_block_top");
	private static final SpriteIdentifier SIDE = block("block/grass_block_side");
	/** Domyślny kolor trawy (biom równin), bo tekstura góry jest w odcieniach szarości. */
	private static final int GRASS_TINT = 0xFF91BD59;

	private IsoBlockPreview() {
	}

	private static SpriteIdentifier block(String path) {
		return new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, Identifier.ofVanilla(path));
	}

	/**
	 * Sześcian o połowie szerokości {@code s}, z górnym przednim wierzchołkiem w (cx, cy).
	 * Wierzchołki: T góra-tył, R prawy, F przód, L lewy, a pod nimi Lb, Fb, Rb.
	 */
	static void draw(DrawContext context, int cx, int cy, int s) {
		BlockOutline module = Modules.BLOCK_OUTLINE;
		float half = s / 2.0F;
		float lx = cx - s;
		float ly = cy - half;
		Sprite top = context.getSprite(TOP);
		Sprite side = context.getSprite(SIDE);

		// Ściany: góra (z odcieniem trawy), lewa jaśniejsza, prawa ciemniejsza, jak cieniowanie w grze.
		face(context, lx, ly, s, -half, s, half, top, GRASS_TINT, 0);
		face(context, lx, ly, s, half, 0, s, side, 0xFFD0D0D0, 0);
		face(context, cx, cy, s, -half, 0, s, side, 0xFF9A9A9A, 0);

		boolean faceMode = module.mode.is(BlockOutline.MODE_FACE);
		boolean fill = module.fillEnabled.on() && !module.mode.is(BlockOutline.MODE_OUTLINE);
		if (fill) {
			int color = module.fill.argb();
			face(context, lx, ly, s, -half, s, half, null, 0, color);
			if (!faceMode) {
				face(context, lx, ly, s, half, 0, s, null, 0, color);
				face(context, cx, cy, s, -half, 0, s, null, 0, color);
			}
		}

		int color = module.color.argb();
		int t = Math.max(1, Math.round(module.width.f() / 2.0F));
		float tx = cx;
		float tyTop = cy - s;
		float rx = cx + s;
		// Górna ściana: zawsze obrysowana (to ją widać "od góry").
		line(context, lx, ly, tx, tyTop, t, color);
		line(context, tx, tyTop, rx, ly, t, color);
		line(context, rx, ly, cx, cy, t, color);
		line(context, cx, cy, lx, ly, t, color);
		if (!faceMode) {
			line(context, lx, ly, lx, ly + s, t, color);
			line(context, lx, ly + s, cx, cy + s, t, color);
			line(context, cx, cy + s, rx, ly + s, t, color);
			line(context, rx, ly + s, rx, ly, t, color);
			line(context, cx, cy, cx, cy + s, t, color);
		}
	}

	/**
	 * Równoległobok o początku (ox, oy) i osiach u = (ux, uy), v = (vx, vy): tekstura (gdy sprite != null)
	 * albo wypełnienie kolorem. Przekształcenie afiniczne zamienia kwadrat 16x16 w ścianę sześcianu.
	 */
	private static void face(DrawContext context, float ox, float oy, float ux, float uy, float vx, float vy, Sprite sprite, int tint, int fill) {
		context.getMatrices().pushMatrix();
		context.getMatrices().mul(new Matrix3x2f(ux / 16.0F, uy / 16.0F, vx / 16.0F, vy / 16.0F, ox, oy));
		if (sprite != null) {
			context.drawSpriteStretched(RenderPipelines.GUI_TEXTURED, sprite, 0, 0, 16, 16, tint);
		} else {
			context.fill(0, 0, 16, 16, fill);
		}

		context.getMatrices().popMatrix();
	}

	/** Linia z kwadracików t x t (GUI nie ma linii pod kątem). */
	private static void line(DrawContext context, float x0, float y0, float x1, float y1, int t, int color) {
		float dx = x1 - x0;
		float dy = y1 - y0;
		int steps = (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy)));
		for (int i = 0; i <= steps; i++) {
			float f = steps == 0 ? 0.0F : i / (float) steps;
			int px = Math.round(x0 + dx * f - t / 2.0F);
			int py = Math.round(y0 + dy * f - t / 2.0F);
			context.fill(px, py, px + t, py + t, color);
		}
	}
}
