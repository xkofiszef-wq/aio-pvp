package pl.kerf.project.gui;

import pl.kerf.project.Lang;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import pl.kerf.project.gui.theme.Anim;
import pl.kerf.project.gui.theme.Draw;
import pl.kerf.project.gui.theme.Theme;

/** Krótkie powiadomienia w prawym górnym rogu: wysuwają się, wiszą kilka sekund i chowają się same. */
public final class Notifications {
	private static final long LIFE = 2600L;
	private static final long SLIDE = 220L;
	private static final int WIDTH = 150;
	private static final int HEIGHT = 30;
	private static final List<Entry> ENTRIES = new ArrayList<>();

	private record Entry(String title, String message, int accent, long created) {
	}

	private Notifications() {
	}

	public static void push(String title, String message, int accent) {
		String shownTitle = Lang.t(title);
		String shownMessage = Lang.t(message);
		synchronized (ENTRIES) {
			// To samo powiadomienie (np. szybkie przełączanie) zastępuje poprzednie zamiast się mnożyć.
			ENTRIES.removeIf(entry -> entry.title.equals(shownTitle));
			ENTRIES.add(new Entry(shownTitle, shownMessage, accent, System.currentTimeMillis()));
			while (ENTRIES.size() > 4) {
				ENTRIES.remove(0);
			}
		}
	}

	public static void render(DrawContext context) {
		long now = System.currentTimeMillis();
		List<Entry> entries;
		synchronized (ENTRIES) {
			ENTRIES.removeIf(entry -> now - entry.created > LIFE);
			if (ENTRIES.isEmpty()) {
				return;
			}

			entries = new ArrayList<>(ENTRIES);
		}

		TextRenderer font = MinecraftClient.getInstance().textRenderer;
		int right = context.getScaledWindowWidth() - 6;
		int y = 6;
		context.createNewRootLayer();
		for (int i = entries.size() - 1; i >= 0; i--) {
			Entry entry = entries.get(i);
			long age = now - entry.created;
			float in = Anim.ease(age / (float) SLIDE);
			float out = Anim.ease((LIFE - age) / (float) SLIDE);
			float t = Math.min(in, out);
			int x = right - WIDTH + Math.round((1.0F - t) * (WIDTH + 10));
			Draw.shadow(context, x, y, WIDTH, HEIGHT, 4, 3);
			Draw.card(context, x, y, WIDTH, HEIGHT, 4, Theme.GLASS_STRONG, Theme.BORDER);
			Draw.round(context, x + 1, y + 5, 2, HEIGHT - 10, 1, entry.accent);
			Draw.text(context, font, Draw.fit(font, entry.title, WIDTH - 14), x + 9, y + 6, Theme.TEXT);
			Draw.text(context, font, Draw.fit(font, entry.message, WIDTH - 14), x + 9, y + 17, Theme.TEXT_3);
			// Pasek pozostałego czasu.
			int bar = Math.round((WIDTH - 8) * (1.0F - age / (float) LIFE));
			context.fill(x + 4, y + HEIGHT - 2, x + 4 + bar, y + HEIGHT - 1, Theme.alpha(entry.accent, 0x90));
			y += HEIGHT + 4;
		}
	}
}
