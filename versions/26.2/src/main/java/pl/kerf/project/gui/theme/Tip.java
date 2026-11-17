package pl.kerf.project.gui.theme;

import pl.kerf.project.Lang;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Podpowiedzi w stylu interfejsu. Element pod kursorem zgłasza tekst w czasie rysowania ({@link #offer}),
 * a ekran na końcu klatki rysuje go ({@link #render}), jeśli kursor zatrzymał się na nim na chwilę.
 */
public final class Tip {
	private static final long DELAY = 450L;
	private static String offered;
	private static String current;
	private static long since;
	private static final Anim FADE = new Anim(0.0F, 16.0F);

	private Tip() {
	}

	public static void offer(String text) {
		text = Lang.t(text);
		offered = text;
	}

	public static void render(GuiGraphicsExtractor context, int mouseX, int mouseY) {
		String text = offered;
		offered = null;
		long now = System.currentTimeMillis();
		if (text == null || !text.equals(current)) {
			current = text;
			since = now;
			FADE.set(0.0F);
		}

		if (current == null || now - since < DELAY) {
			return;
		}

		float alpha = FADE.to(1.0F);
		Font font = Minecraft.getInstance().font;
		List<FormattedCharSequence> lines = font.split(Component.literal(current), 180);
		int w = 0;
		for (FormattedCharSequence line : lines) {
			w = Math.max(w, font.width(line));
		}

		w += 12;
		int h = lines.size() * 10 + 8;
		int sw = context.guiWidth();
		int sh = context.guiHeight();
		int x = Math.min(mouseX + 10, sw - w - 4);
		int y = Math.min(mouseY + 12, sh - h - 4);
		context.nextStratum();
		Draw.shadow(context, x, y, w, h, 3, 3);
		Draw.round(context, x, y, w, h, 3, Theme.alpha(0xFF101216, Math.round(250 * alpha)));
		Draw.outline(context, x, y, w, h, 3, Theme.alpha(Theme.BORDER_STRONG, Math.round(255 * alpha)));
		int ty = y + 5;
		for (FormattedCharSequence line : lines) {
			context.text(font, line, x + 6, ty, Theme.alpha(Theme.TEXT_2, Math.round(255 * alpha)), false);
			ty += 10;
		}
	}
}
