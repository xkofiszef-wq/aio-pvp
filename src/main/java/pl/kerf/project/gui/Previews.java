package pl.kerf.project.gui;

import pl.kerf.project.Lang;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import pl.kerf.project.module.HudModule;
import pl.kerf.project.module.Module;
import pl.kerf.project.module.Modules;
import pl.kerf.project.render.HealthLabel;
import pl.kerf.project.render.CustomCrosshairRenderer;

/** Ikony funkcji i podglądy na żywo w menu (odświeżane co klatkę, więc zmiana ustawienia widać od razu). */
public final class Previews {
	private static final Map<Module, ItemStack> ICONS = new HashMap<>();
	private static final Map<Module.Category, ItemStack> CATEGORY_ICONS = new HashMap<>();
	private static final Identifier CROSSHAIR = Identifier.ofVanilla("hud/crosshair");

	static {
		icon(Modules.ARMOR_HUD, Items.DIAMOND_CHESTPLATE);
		icon(Modules.ITEM_PREVIEW, Items.ENDER_PEARL);
		icon(Modules.POTION_STATUS, Items.POTION);
		icon(Modules.KEY_OVERLAY, Items.OAK_SIGN);
		icon(Modules.ELYTRA_PITCH, Items.ELYTRA);
		icon(Modules.BOW_PITCH, Items.BOW);
		icon(Modules.HUD_TWEAKER, Items.PAINTING);
		icon(Modules.COORDINATES, Items.MAP);
		icon(Modules.FPS, Items.REDSTONE);
		icon(Modules.PING, Items.REPEATER);
		icon(Modules.CPS, Items.STONE_BUTTON);
		icon(Modules.DAY, Items.DAYLIGHT_DETECTOR);
		icon(Modules.CLOCK, Items.CLOCK);
		icon(Modules.COMPASS, Items.COMPASS);
		icon(Modules.MEDIA, Items.JUKEBOX);
		icon(Modules.CROSSHAIR, Items.SPYGLASS);
		icon(Modules.HITBOXES, Items.ARMOR_STAND);
		icon(Modules.HIT_COLOR, Items.RED_DYE);
		icon(Modules.HEALTH, Items.GOLDEN_APPLE);
		icon(Modules.LOW_FIRE, Items.FLINT_AND_STEEL);
		icon(Modules.NO_POTION_PARTICLES, Items.GLASS_BOTTLE);
		icon(Modules.NO_EXPLOSION_PARTICLES, Items.END_CRYSTAL);
		icon(Modules.FULLBRIGHT, Items.GLOWSTONE_DUST);
		icon(Modules.BLOCK_OUTLINE, Items.GRASS_BLOCK);
		icon(Modules.NAMETAGS, Items.NAME_TAG);
		icon(Modules.VIEW_MODEL, Items.DIAMOND_SWORD);
		icon(Modules.SHIELD, Items.SHIELD);
		icon(Modules.TOTEM, Items.TOTEM_OF_UNDYING);
		icon(Modules.BOAT_VIEW, Items.OAK_BOAT);
		icon(Modules.FAST_XP, Items.EXPERIENCE_BOTTLE);
		icon(Modules.CHAT_HISTORY, Items.WRITABLE_BOOK);
		icon(Modules.COOLDOWN_FIX, Items.IRON_AXE);
		icon(Modules.OWN_NAME, Items.PLAYER_HEAD);
		icon(Modules.CLEAR_VIEW, Items.CARVED_PUMPKIN);
		icon(Modules.SATURATION, Items.GOLDEN_CARROT);
		icon(Modules.NO_CAMERA_SHAKE, Items.ENDER_EYE);
		icon(Modules.INVISIBLE_HAND, Items.FERMENTED_SPIDER_EYE);
		icon(Modules.NO_SIGN_GUI, Items.OAK_HANGING_SIGN);
		icon(Modules.COMMAND_KEYS, Items.COMMAND_BLOCK);
		icon(Modules.SPRINT_STATUS, Items.LEATHER_BOOTS);
		icon(Modules.FIGHT_REPORT, Items.PAPER);
		icon(Modules.LAG_DETECTOR, Items.REDSTONE_TORCH);
		icon(Modules.OLD_ANIMATIONS, Items.GOLDEN_SWORD);
		icon(Modules.INSTANT_HIT, Items.LIGHTNING_ROD);
		icon(Modules.FREELOOK, Items.ENDER_EYE);
		icon(Modules.DAMAGE_CALC, Items.NETHERITE_SWORD);
		icon(Modules.GUI_THEME, Items.CYAN_DYE);
		CATEGORY_ICONS.put(Module.Category.HUD, new ItemStack(Items.COMPASS));
		CATEGORY_ICONS.put(Module.Category.COMBAT, new ItemStack(Items.NETHERITE_SWORD));
		CATEGORY_ICONS.put(Module.Category.VISUALS, new ItemStack(Items.ENDER_EYE));
		CATEGORY_ICONS.put(Module.Category.ITEMS, new ItemStack(Items.CHEST));
		CATEGORY_ICONS.put(Module.Category.MISC, new ItemStack(Items.COMPARATOR));
	}

	private Previews() {
	}

	private static void icon(Module module, net.minecraft.item.Item item) {
		ICONS.put(module, new ItemStack(item));
	}

	public static ItemStack icon(Module module) {
		return ICONS.getOrDefault(module, ItemStack.EMPTY);
	}

	public static ItemStack icon(Module.Category category) {
		return CATEGORY_ICONS.getOrDefault(category, ItemStack.EMPTY);
	}

	private static boolean inWorld(MinecraftClient client) {
		return client.player != null && client.world != null;
	}

	/** Rysuje podgląd funkcji w prostokącie. */
	public static void draw(DrawContext context, MinecraftClient client, Module module, int x, int y, int w, int h) {
		int cx = x + w / 2;
		int cy = y + h / 2;
		// Key Overlay nie potrzebuje świata (rysuje hotbar i klawisze), reszta HUD-u czyta dane gracza.
		if (module instanceof HudModule hud && hud.hasPreview() && (inWorld(client) || module == Modules.KEY_OVERLAY)) {
			hud.renderPreview(context, client, x, y, w, h);
			return;
		}

		if (module == Modules.HIT_COLOR && inWorld(client)) {
			int size = Math.min(h / 2 - 4, 40);
			player(context, client.player, cx - 30, y + 4, cx + 30, y + h - 4, size, true);
		} else if (module == Modules.HITBOXES && inWorld(client)) {
			hitboxes(context, client, x, y, w, h);
		} else if (module == Modules.CROSSHAIR) {
			crosshair(context, client, cx, cy, w);
		} else if (module == Modules.BLOCK_OUTLINE) {
			IsoBlockPreview.draw(context, cx, cy + h / 12, Math.min(w, h) / 3);
		} else if (module == Modules.HEALTH) {
			// Twoje prawdziwe zdrowie (tak samo wyglądają serca nad innymi graczami); bez świata pełne 10 serc.
			boolean world = client.player != null;
			float health = world ? client.player.getHealth() : 20.0F;
			float max = world ? client.player.getMaxHealth() : 20.0F;
			float absorption = world && Modules.HEALTH.absorption.on() ? client.player.getAbsorptionAmount() : 0.0F;
			Text label = HealthLabel.build(health, max, absorption);
			boolean above = Modules.HEALTH.position.is(0);
			context.getMatrices().pushMatrix();
			context.getMatrices().translate(cx, cy);
			context.getMatrices().scale(1.5F, 1.5F);
			// Serca bez tła, tak jak w grze; nick z vanillowym tłem.
			nametag(context, client, label, 0, above ? -10 : 2, 0);
			nametag(context, client, Text.literal(Lang.t("Kerfuss_")), 0, above ? 2 : -10, 0x40000000);
			context.getMatrices().popMatrix();
		} else if (module == Modules.DAMAGE_CALC) {
			damageCalc(context, client, cx, cy);
		} else if (module == Modules.NAMETAGS) {
			boolean white = Modules.NAMETAGS.on() && Modules.NAMETAGS.whiteText.on();
			int bg = !Modules.NAMETAGS.on() ? 0x40000000 : Modules.NAMETAGS.backgroundEnabled.on() ? Modules.NAMETAGS.background.argb() : 0;
			context.getMatrices().pushMatrix();
			context.getMatrices().translate(cx, cy);
			context.getMatrices().scale(2.0F, 2.0F);
			nametag(context, client, Text.literal(Lang.t("Kerfuss_")).withColor(white ? 0xFFFFFF : 0xBBBBBB), 0, -4, bg);
			context.getMatrices().popMatrix();
		} else {
			bigIcon(context, icon(module), cx, cy);
			if (module instanceof HudModule || module == Modules.HIT_COLOR || module == Modules.HITBOXES) {
				context.drawCenteredTextWithShadow(client.textRenderer, "Podgląd w świecie", cx, y + h - 12, 0xFF8A8A96);
			}
		}
	}

	/**
	 * Model gracza w GUI. Z {@code hurt} chwilowo udaje trafienie, żeby renderer nałożył kolor trafienia
	 * (tryb zanikania widać jako pętlę animacji).
	 */
	private static void player(DrawContext context, ClientPlayerEntity player, int x1, int y1, int x2, int y2, int size, boolean hurt) {
		int hurtTime = player.hurtTime;
		int maxHurtTime = player.maxHurtTime;
		if (hurt) {
			player.maxHurtTime = 10;
			player.hurtTime = Modules.HIT_COLOR.behavior.is(1) ? 10 - (int) (System.currentTimeMillis() / 60 % 14) : 8;
			player.hurtTime = Math.max(1, player.hurtTime);
		}

		try {
			InventoryScreen.drawEntity(context, x1, y1, x2, y2, size, 0.0625F, (x1 + x2) / 2.0F, (y1 + y2) / 2.0F, player);
		} finally {
			player.hurtTime = hurtTime;
			player.maxHurtTime = maxHurtTime;
		}
	}

	private static void hitboxes(DrawContext context, MinecraftClient client, int x, int y, int w, int h) {
		Modules.Hitboxes module = Modules.HITBOXES;
		int size = Math.min(h / 2 - 8, 32);
		int[] centers = {x + w / 4 + 4, x + w * 3 / 4 - 4};
		int[] colors = {module.playerColor.argb(), module.targetColor.argb()};
		String[] labels = {"Gracz", "Na celu"};
		for (int i = 0; i < 2; i++) {
			int c = centers[i];
			player(context, client.player, c - 20, y + 4, c + 20, y + h - 14, size, false);
			int boxW = Math.round(size * 0.6F);
			int boxH = Math.round(size * 1.8F);
			int bottom = y + (h - 14) / 2 + 4 + Math.round(size * 0.9F) + 2;
			// Wyłączone hitboxy graczy: bez ramki, także na celu.
			if (module.players.on()) {
				context.drawStrokedRectangle(c - boxW / 2, bottom - boxH, boxW, boxH, colors[i]);
			}

			String label = module.players.on() ? labels[i] : "wyłączone";
			context.drawCenteredTextWithShadow(client.textRenderer, label, c, y + h - 11, module.players.on() ? 0xFFD0D0D8 : 0xFF8A8A96);
		}
	}

	private static void crosshair(DrawContext context, MinecraftClient client, int cx, int cy, int w) {
		Modules.Crosshair module = Modules.CROSSHAIR;
		int offset = Math.min(w / 4, 34);
		for (int i = 0; i < 2; i++) {
			boolean target = i == 1;
			int px = cx + (target ? offset : -offset);
			context.getMatrices().pushMatrix();
			context.getMatrices().translate(px, cy - 4);
			context.getMatrices().scale(2.0F, 2.0F);
			int color = target && module.recolor.on() ? module.color.argb() : 0xFFFFFFFF;
			if (module.custom.on()) {
				// Gdy włączony jest własny celownik, podgląd pokazuje jego, nie vanillowy plus.
				CustomCrosshairRenderer.draw(context, module.grid(target).get(), -7.5F, -7.5F, 1.0F);
			} else {
				context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, CROSSHAIR, -8, -8, 15, 15, color);
			}

			context.getMatrices().popMatrix();
			context.drawCenteredTextWithShadow(client.textRenderer, target ? "Na celu" : "Bez celu", px, cy + 30, 0xFFD0D0D8);
		}
	}

	/** Wynik kalkulatora na żywo: obrażenia ciosu w sercach i liczba ciosów do zabicia. */
	private static void damageCalc(DrawContext context, MinecraftClient client, int cx, int cy) {
		var calc = Modules.DAMAGE_CALC;
		float hit = calc.hit();
		int hits = calc.hitsToKill();
		String damage = String.format(java.util.Locale.ROOT, "%.2f", hit / 2.0F);
		int dw = client.textRenderer.getWidth(damage) * 2;
		context.getMatrices().pushMatrix();
		context.getMatrices().translate(cx - (dw + 13) / 2.0F, cy - 20);
		context.getMatrices().scale(2.0F, 2.0F);
		context.drawText(client.textRenderer, damage, 0, 0, 0xFFF0F1F5, true);
		context.getMatrices().popMatrix();
		context.drawGuiTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, net.minecraft.util.Identifier.ofVanilla("hud/heart/full"),
			cx - (dw + 13) / 2 + dw + 4, cy - 17, 9, 9);
		String kill = hits < 0 ? Lang.t("Nie zabijesz: leczy się szybciej") : hits + Lang.t(hits == 1 ? " cios" : hits < 5 ? " ciosy" : " ciosów") + Lang.t(" do zabicia");
		int killColor = hits < 0 ? 0xFFE5675F : 0xFF6CC98E;
		context.drawCenteredTextWithShadow(client.textRenderer, kill, cx, cy + 2, killColor);
		if (hits > 0) {
			float seconds = (hits - 1) * calc.interval() / 20.0F;
			context.drawCenteredTextWithShadow(client.textRenderer, String.format(java.util.Locale.ROOT, Lang.t("około %.1f s pełnych ciosów"), seconds), cx, cy + 14, 0xFF8C92A0);
		}
	}

	private static void bigIcon(DrawContext context, ItemStack stack, int cx, int cy) {
		context.getMatrices().pushMatrix();
		context.getMatrices().translate(cx - 24, cy - 24);
		context.getMatrices().scale(3.0F, 3.0F);
		context.drawItem(stack, 0, 0);
		context.getMatrices().popMatrix();
	}

	private static void nametag(DrawContext context, MinecraftClient client, Text text, int cx, int y, int background) {
		int w = client.textRenderer.getWidth(text);
		context.fill(cx - w / 2 - 1, y - 1, cx + w / 2 + 1, y + 9, background);
		context.drawText(client.textRenderer, text, cx - w / 2, y, 0xFFFFFFFF, false);
	}
}
