package pl.kerf.project.render;

import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.render.chunk.ChunkRenderData;
import net.minecraft.client.texture.SpriteContents;
import pl.kerf.project.module.Modules;

/**
 * Animate Only Visible Textures: atlas bloków dostaje wysyłkę każdej klatki animowanych tekstur na GPU, także tych,
 * których żaden widoczny fragment świata nie używa. Tu liczymy, które animowane tekstury są w widocznych fragmentach
 * (zbiór zapamiętany przy budowie fragmentu), i pomijamy wysyłkę pozostałych; po wejściu w pole widzenia dostają najnowszą klatkę.
 */
public final class AnimatedSprites {
	private static final WeakHashMap<SpriteContents.Animator, SpriteContents> OWNERS = new WeakHashMap<>();
	/** Widoczny zbiór liczymy raz na kilka milisekund, a nie przy każdej animowanej teksturze. */
	private static final long CACHE_NANOS = 20_000_000L;
	private static long computedAt;
	private static Set<SpriteContents> visible;
	/** Do testów: ile wysyłek pominięto od startu. */
	public static volatile int skipped;

	private AnimatedSprites() {
	}

	public static void register(SpriteContents.Animator animator, SpriteContents owner) {
		synchronized (OWNERS) {
			OWNERS.put(animator, owner);
		}
	}

	/**
	 * Tekstury, które zawsze animujemy, bo rysują się poza fragmentami świata: ciecze (nie zbieramy ich przy budowie),
	 * nakładki (woda, ogień) i portale.
	 */
	private static boolean always(SpriteContents contents) {
		String path = contents.getId().getPath();
		return path.contains("water") || path.contains("lava") || path.contains("fire") || path.contains("overlay") || path.contains("portal");
	}

	/** Czy dla tej animacji pomijamy wysyłkę klatki na GPU (nikt jej teraz nie widzi). */
	public static boolean hidden(SpriteContents.Animator animator) {
		if (!Modules.OPTIMIZATION.active(Modules.OPTIMIZATION.animateOnlyVisible)) {
			return false;
		}

		SpriteContents owner;
		synchronized (OWNERS) {
			owner = OWNERS.get(animator);
		}

		if (owner == null || always(owner)) {
			return false;
		}

		Set<SpriteContents> set = visible();
		boolean hide = set != null && !set.contains(owner);
		if (hide) {
			skipped++;
		}

		return hide;
	}

	/** Zbiór animowanych tekstur widocznych fragmentów; null = nie wiadomo (np. fragmenty zbudowane przed włączeniem opcji). */
	public static Set<SpriteContents> visible() {
		long now = System.nanoTime();
		if (now - computedAt < CACHE_NANOS) {
			return visible;
		}

		computedAt = now;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.worldRenderer == null || client.world == null) {
			return visible = null;
		}

		Set<SpriteContents> set = new HashSet<>();
		int sections = 0;
		for (ChunkBuilder.BuiltChunk chunk : client.worldRenderer.getBuiltChunks()) {
			if (chunk.getCurrentRenderData() instanceof ChunkRenderData data) {
				Set<SpriteContents> sprites = ((SpritesHolder) data).aio$sprites();
				if (sprites == null) {
					return visible = null;
				}

				set.addAll(sprites);
				sections++;
			}
		}

		return visible = sections == 0 ? null : set;
	}

	/** Do testów: ścieżki widocznych animowanych tekstur (null = nie wiadomo). */
	public static Set<String> visiblePaths() {
		computedAt = 0L;
		Set<SpriteContents> set = visible();
		if (set == null) {
			return null;
		}

		Set<String> paths = new HashSet<>();
		set.forEach(contents -> paths.add(contents.getId().getPath()));
		return paths;
	}

	/** Dane doklejane mixinami do wyniku budowy fragmentu i do jego danych renderowania. */
	public interface SpritesHolder {
		Set<SpriteContents> aio$sprites();

		void aio$setSprites(Set<SpriteContents> sprites);
	}
}
