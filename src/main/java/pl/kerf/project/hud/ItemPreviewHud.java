package pl.kerf.project.hud;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import pl.kerf.project.config.Setting;
import pl.kerf.project.module.HudModule;

public final class ItemPreviewHud extends HudModule {
	public final Setting.Str items = this.add(new Setting.Str(
		"items", "Śledzone przedmioty (po przecinku, w kolejności)",
		"totem_of_undying, golden_apple, enchanted_golden_apple, ender_pearl, experience_bottle, splash_potion, cobweb, end_crystal, obsidian"
	));
	public final Setting.Mode style = this.add(new Setting.Mode("style", "Wygląd", 1, "Zwykły", "Vanilla GUI", "Resourcepack GUI"));
	public final Setting.Mode layout = this.add(new Setting.Mode("layout", "Układ", 0, "Poziomo", "Pionowo"));
	public final Setting.Bool hideZero = this.add(new Setting.Bool("hide_zero", "Ukryj brakujące", true));

	private String parsedSource;
	private final List<ItemStack> tracked = new ArrayList<>();
	private final List<ItemStack> visible = new ArrayList<>();
	private final List<Integer> counts = new ArrayList<>();

	public ItemPreviewHud() {
		super("item_preview", "Item Preview", "Liczniki wybranych przedmiotów z ekwipunku obok hotbara.", true, 0.5F, 1.0F, 0.0F, 1.0F, 100.0F, -3.0F);
	}

	private void parse() {
		String source = this.items.get();
		if (source.equals(this.parsedSource)) {
			return;
		}

		this.parsedSource = source;
		this.tracked.clear();
		for (String part : source.split(",")) {
			Identifier id = Identifier.tryParse(part.trim().toLowerCase(java.util.Locale.ROOT));
			if (id == null || !Registries.ITEM.containsId(id)) {
				continue;
			}

			Item item = Registries.ITEM.get(id);
			if (item != Items.AIR) {
				this.tracked.add(new ItemStack(item));
			}
		}
	}

	private void update(MinecraftClient client, boolean editing) {
		this.parse();
		this.visible.clear();
		this.counts.clear();
		// Jedno przejście po ekwipunku na klatkę, puste sloty od razu pomijane.
		int[] totals = new int[this.tracked.size()];
		PlayerInventory inventory = client.player.getInventory();
		for (int slot = 0; slot < inventory.size(); slot++) {
			ItemStack stack = inventory.getStack(slot);
			if (stack.isEmpty()) {
				continue;
			}

			for (int i = 0; i < totals.length; i++) {
				if (stack.isOf(this.tracked.get(i).getItem())) {
					totals[i] += stack.getCount();
				}
			}
		}

		for (int i = 0; i < totals.length; i++) {
			if (totals[i] > 0 || editing || !this.hideZero.on()) {
				this.visible.add(this.tracked.get(i));
				this.counts.add(totals[i]);
			}
		}
	}

	private boolean vanilla() {
		return !this.style.is(0);
	}

	private int pitch() {
		return this.vanilla() ? VanillaGui.SLOT_PITCH : 18;
	}

	@Override
	public int margin() {
		return this.vanilla() ? VanillaGui.SLOT_MARGIN : 0;
	}

	private int length() {
		return this.visible.isEmpty() ? 0 : (this.visible.size() - 1) * this.pitch() + 16;
	}

	@Override
	public int width(MinecraftClient client, boolean editing) {
		this.update(client, editing);
		return this.layout.is(0) ? this.length() : this.visible.isEmpty() ? 0 : 16;
	}

	@Override
	public int height(MinecraftClient client, boolean editing) {
		return this.layout.is(0) ? this.visible.isEmpty() ? 0 : 16 : this.length();
	}

	@Override
	protected void draw(DrawContext context, MinecraftClient client, RenderTickCounter tickCounter, boolean editing) {
		boolean horizontal = this.layout.is(0);
		if (this.vanilla()) {
			VanillaGui.slots(context, 0, 0, this.visible.size(), horizontal, this.style.is(2));
		}

		for (int i = 0; i < this.visible.size(); i++) {
			int x = horizontal ? i * this.pitch() : 0;
			int y = horizontal ? 0 : i * this.pitch();
			ItemStack stack = this.visible.get(i);
			context.drawItem(stack, x, y);
			// Własny tekst licznika: vanilla ukrywa "1" i nie pokaże sumy większej niż stack.
			context.drawStackOverlay(client.textRenderer, stack, x, y, String.valueOf(this.counts.get(i)));
		}
	}
}
