package pl.kerf.project.hud;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import pl.kerf.project.AioPvp;

/**
 * Tła w stylu vanilla (sloty hotbara, panel efektu z ekwipunku), w dwóch wariantach stylu:
 * <ul>
 * <li>"Vanilla GUI": zawsze oryginalne tekstury gry, czytane z wbudowanych zasobów Minecrafta, więc texture pack ich nie zmienia;</li>
 * <li>"Resourcepack GUI": te same sprite'y z atlasu GUI, których używa hotbar, więc wyglądają jak w twoim texture packu.</li>
 * </ul>
 * Mod nie kopiuje żadnych tekstur Minecrafta do swojego jara, tylko czyta je z gry.
 */
public final class VanillaGui {
	/** Odstęp między slotami w hotbarze. */
	public static final int SLOT_PITCH = 20;
	/** O ile ramka slotu wystaje poza ikonę przedmiotu 16x16. */
	public static final int SLOT_MARGIN = 3;
	public static final Identifier EFFECT_PANEL = Identifier.withDefaultNamespace("container/inventory/effect_background");
	public static final Identifier EFFECT_PANEL_AMBIENT = Identifier.withDefaultNamespace("container/inventory/effect_background_ambient");
	private static final Identifier HOTBAR = Identifier.withDefaultNamespace("hud/hotbar");
	private static final int HOTBAR_W = 182;
	private static final int HOTBAR_H = 22;
	/** Panel efektu: 32x32, ramka 4 px (jak "nine_slice" w pliku .mcmeta gry). */
	private static final int PANEL = 32;
	private static final int PANEL_BORDER = 4;
	/** Tekstury z wbudowanych zasobów gry, załadowane raz: sprite -> nasza kopia w pamięci (null = nie udało się). */
	private static final Map<Identifier, Identifier> PURE = new HashMap<>();

	private VanillaGui() {
	}

	/**
	 * Rząd slotów jak w hotbarze. (itemX, itemY) to pozycja ikony pierwszego przedmiotu, kolejne co {@link #SLOT_PITCH}.
	 * {@code pack} = tekstura z texture packa, inaczej zawsze oryginalna.
	 *
	 * <p>Hotbar ma czarną krawędź 1 px, potem powtarzalne komórki po 20 px. W poziomie składamy: lewa krawędź,
	 * N komórek, prawa krawędź. W pionie tekstura nie ma odpowiednika, więc każdy slot to osobne pudełko
	 * 22x22, a kolejne nachodzą na siebie o 2 px, przez co między slotami zostaje jedna czarna linia.
	 */
	public static void slots(GuiGraphics context, int itemX, int itemY, int count, boolean horizontal, boolean pack) {
		int x = itemX - SLOT_MARGIN;
		int y = itemY - SLOT_MARGIN;
		if (count <= 0) {
			return;
		}

		if (horizontal) {
			region(context, pack, 0, x, y, 1);
			for (int i = 0; i < count; i++) {
				region(context, pack, 1 + i % 9 * SLOT_PITCH, x + 1 + i * SLOT_PITCH, y, SLOT_PITCH);
			}

			region(context, pack, HOTBAR_W - 1, x + 1 + count * SLOT_PITCH, y, 1);
			return;
		}

		for (int i = 0; i < count; i++) {
			int boxY = y + i * SLOT_PITCH;
			region(context, pack, 0, x, boxY, 1);
			region(context, pack, 1, x + 1, boxY, SLOT_PITCH);
			region(context, pack, HOTBAR_W - 1, x + 1 + SLOT_PITCH, boxY, 1);
		}
	}

	private static void region(GuiGraphics context, boolean pack, int u, int x, int y, int width) {
		Identifier pure = pack ? null : pure(HOTBAR);
		if (pure == null) {
			context.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR, HOTBAR_W, HOTBAR_H, u, 0, x, y, width, HOTBAR_H);
		} else {
			context.blit(RenderPipelines.GUI_TEXTURED, pure, x, y, u, 0.0F, width, HOTBAR_H, HOTBAR_W, HOTBAR_H);
		}
	}

	/** Panel efektu jak w ekwipunku, dowolnej szerokości (wysokość 32, jak w grze). */
	public static void effectPanel(GuiGraphics context, int x, int y, int width, int height, boolean ambient, boolean pack) {
		Identifier sprite = ambient ? EFFECT_PANEL_AMBIENT : EFFECT_PANEL;
		Identifier pure = pack ? null : pure(sprite);
		if (pure == null) {
			context.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, width, height);
			return;
		}

		// Ramka 4 px z lewej i prawej, środek rozciągnięty (w oryginale jest jednolity).
		int b = PANEL_BORDER;
		context.blit(RenderPipelines.GUI_TEXTURED, pure, x, y, 0.0F, 0.0F, b, height, b, PANEL, PANEL, PANEL);
		context.blit(RenderPipelines.GUI_TEXTURED, pure, x + b, y, b, 0.0F, width - 2 * b, height, PANEL - 2 * b, PANEL, PANEL, PANEL);
		context.blit(RenderPipelines.GUI_TEXTURED, pure, x + width - b, y, PANEL - b, 0.0F, b, height, b, PANEL, PANEL, PANEL);
	}

	/** Oryginalna tekstura sprite'a prosto z wbudowanych zasobów gry (pomija texture packi); null, gdy jej nie ma. */
	private static Identifier pure(Identifier sprite) {
		return PURE.computeIfAbsent(sprite, key -> {
			Minecraft client = Minecraft.getInstance();
			Identifier file = Identifier.withDefaultNamespace("textures/gui/sprites/" + key.getPath() + ".png");
			//? if <26.3 {
			IoSupplier<InputStream> supplier = client.getVanillaPackResources().getResource(PackType.CLIENT_RESOURCES, file);
			//?} else {
			/*IoSupplier<InputStream> supplier = client.getVanillaPackResources().fullResources().getResource(PackType.CLIENT_RESOURCES, file);
			*///?}
			if (supplier == null) {
				return null;
			}

			try (InputStream in = supplier.get()) {
				Identifier id = Identifier.fromNamespaceAndPath(AioPvp.MOD_ID, "vanilla_gui/" + key.getPath());
				client.getTextureManager().register(id, new DynamicTexture(() -> "AIO PvP " + id, NativeImage.read(in)));
				return id;
			} catch (Exception e) {
				AioPvp.LOGGER.warn("Nie udało się wczytać oryginalnej tekstury {}", file, e);
				return null;
			}
		});
	}
}
