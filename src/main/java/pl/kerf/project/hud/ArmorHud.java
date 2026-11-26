package pl.kerf.project.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import pl.kerf.project.config.Setting;
import pl.kerf.project.module.HudModule;

public final class ArmorHud extends HudModule {
	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	//? if <26.1.2 {
	private static final ItemStack[] SAMPLE = {
		new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE), new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.DIAMOND_BOOTS)
	};
	//?} else {
	/*/^* Przykład do edytora; tworzony przy pierwszym użyciu, bo w 26.x przedmiotów nie można tworzyć przed wczytaniem ich danych. ^/
	private static ItemStack[] sample;

	private static ItemStack[] sample() {
		if (sample == null) {
			sample = new ItemStack[] {
				new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE), new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.DIAMOND_BOOTS)
			};
		}

		return sample;
	}
	*///?}
	private static final int LAYOUT_HORIZONTAL = 0;
	private static final int INFO_BAR = 0;
	private static final int INFO_PERCENT = 1;
	private static final int INFO_POINTS = 2;
	private static final int RED = 0xFFFF5555;
	/** Ostrzeżenie o pękającej zbroi: assets/aio_pvp/sounds/tool_breaking.ogg (z Durability Viewer, MIT). */
	private static final SoundEvent TOOL_BREAKING = SoundEvent.createVariableRangeEvent(net.minecraft.resources.Identifier.fromNamespaceAndPath("aio_pvp", "tool_breaking"));

	public final Setting.Mode style = this.add(new Setting.Mode("style", "Wygląd", 1, "Zwykły", "Vanilla GUI", "Resourcepack GUI"));
	public final Setting.Mode layout = this.add(new Setting.Mode("layout", "Układ", 0, "Poziomo", "Pionowo"));
	public final Setting.Mode info = this.add(new Setting.Mode("info", "Zużycie", 1, "Pasek", "Procent", "Punkty", "Brak"));
	public final Setting.Bool colored = this.add(new Setting.Bool("colored", "Kolor liczb wg zużycia", false));
	public final Setting.Bool warning = this.add(new Setting.Bool("warning", "Ostrzeżenie", true));
	public final Setting.Num threshold = this.add(new Setting.Num("threshold", "Próg ostrzeżenia", 15, 1, 50, 1, "%"));
	public final Setting.Bool sound = this.add(new Setting.Bool("sound", "Dźwięk ostrzeżenia", true));
	public final Setting.Num volume = this.add(new Setting.Num("volume", "Głośność dźwięku", 80, 0, 100, 5, "%"));

	private final boolean[] wasLow = new boolean[SLOTS.length];
	private final boolean[] hadItem = new boolean[SLOTS.length];
	private final int[] lastDamage = new int[SLOTS.length];
	private final net.minecraft.world.item.Item[] lastItem = new net.minecraft.world.item.Item[SLOTS.length];
	private final ItemStack[] stacks = new ItemStack[SLOTS.length];

	public ArmorHud() {
		// Domyślnie po lewej stronie hotbara, za slotem offhandu (który kończy się 120 px w lewo od środka).
		super("armor_hud", "Armor HUD", "Zbroja i jej zużycie obok hotbara, z ostrzeżeniem o niskim durability.", true, 0.5F, 1.0F, 1.0F, 1.0F, -130.0F, -3.0F);
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

	private boolean hasText() {
		return this.info.is(INFO_PERCENT) || this.info.is(INFO_POINTS);
	}

	/** Uzupełnia {@link #stacks}; zwraca false, gdy nie ma czego pokazać. */
	private boolean collect(Minecraft client, boolean editing) {
		boolean anyWorn = false;
		for (int i = 0; i < SLOTS.length; i++) {
			this.stacks[i] = client.player.getItemBySlot(SLOTS[i]);
			anyWorn |= !this.stacks[i].isEmpty();
		}

		if (!anyWorn && editing) {
			//? if <26.1.2 {
			System.arraycopy(SAMPLE, 0, this.stacks, 0, SAMPLE.length);
			//?} else {
			/*System.arraycopy(sample(), 0, this.stacks, 0, sample().length);
			*///?}
			return true;
		}

		return anyWorn;
	}

	@Override
	public int width(Minecraft client, boolean editing) {
		if (!this.collect(client, editing)) {
			return 0;
		}

		return this.layout.is(LAYOUT_HORIZONTAL) ? (SLOTS.length - 1) * this.pitch() + 16 : 16;
	}

	@Override
	public int height(Minecraft client, boolean editing) {
		return this.layout.is(LAYOUT_HORIZONTAL) ? 16 : (SLOTS.length - 1) * this.pitch() + 16;
	}

	private static float fraction(ItemStack stack) {
		return (stack.getMaxDamage() - stack.getDamageValue()) / (float) stack.getMaxDamage();
	}

	private boolean isLow(ItemStack stack) {
		return stack.isDamageableItem() && fraction(stack) * 100.0F <= this.threshold.f();
	}

	@Override
	protected void draw(GuiGraphics context, Minecraft client, DeltaTracker tickCounter, boolean editing) {
		boolean horizontal = this.layout.is(LAYOUT_HORIZONTAL);
		int pitch = this.pitch();
		if (this.vanilla()) {
			VanillaGui.slots(context, 0, 0, SLOTS.length, horizontal, this.style.is(2));
		}

		for (int i = 0; i < SLOTS.length; i++) {
			ItemStack stack = this.stacks[i];
			if (stack.isEmpty()) {
				continue;
			}

			int x = horizontal ? i * pitch : 0;
			int y = horizontal ? 0 : i * pitch;
			//? if <26.1.2 {
			context.renderItem(stack, x, y);
			//?} else {
			/*context.item(stack, x, y);
			*///?}
			boolean low = this.isLow(stack);
			if (this.info.is(INFO_BAR)) {
				//? if <26.1.2 {
				context.renderItemDecorations(client.font, stack, x, y);
				//?} else {
				/*context.itemDecorations(client.font, stack, x, y);
				*///?}
			} else if (this.hasText() && stack.isDamageableItem()) {
				float fraction = fraction(stack);
				int left = stack.getMaxDamage() - stack.getDamageValue();
				String text = this.info.is(INFO_PERCENT) ? String.valueOf(Math.round(fraction * 100.0F)) : compact(left);
				int color = low ? RED : this.colored.on() ? durabilityColor(fraction) : 0xFFFFFFFF;
				// Dokładnie tak jak liczba przedmiotów w slocie: ta sama czcionka, cień i miejsce (prawy dolny róg).
				context.drawString(client.font, text, x + 19 - 2 - client.font.width(text), y + 6 + 3, color, true);
			}

			if (low && this.warning.on()) {
				context.drawString(client.font, "!", x + 1, y - 1, RED, true);
			}
		}
	}

	/** Punkty zużycia w 3 znakach, żeby zmieściły się w slocie: 1561 -> "1.5k". */
	private static String compact(int value) {
		if (value < 1000) {
			return String.valueOf(value);
		}

		return value < 10000 ? String.format(java.util.Locale.ROOT, "%.1fk", value / 1000.0F) : value / 1000 + "k";
	}

	/** Zielony przy pełnym, przez żółty do pomarańczowego. Czerwony jest zarezerwowany dla progu ostrzeżenia. */
	private static int durabilityColor(float fraction) {
		if (fraction > 0.6F) {
			return 0xFF55FF55;
		}

		return fraction > 0.3F ? 0xFFFFFF55 : 0xFFFFAA00;
	}

	/**
	 * Wywoływane co tick, tylko dla zbroi własnego gracza. Gdy część jest poniżej progu, dźwięk pękania gra
	 * przy KAŻDYM uderzeniu w nią (każda utrata wytrzymałości), a niżej przy zniszczeniu.
	 */
	public void tick(Minecraft client) {
		if (client.player == null) {
			return;
		}

		for (int i = 0; i < SLOTS.length; i++) {
			ItemStack stack = client.player.getItemBySlot(SLOTS[i]);
			boolean low = !stack.isEmpty() && this.isLow(stack);
			int damage = stack.isEmpty() ? -1 : stack.getDamageValue();
			// Ta sama część zbroi i większe zużycie niż tick temu = dostała uderzenie.
			boolean hit = low && this.hadItem[i] && stack.getItem() == this.lastItem[i] && damage > this.lastDamage[i];
			boolean broke = stack.isEmpty() && this.hadItem[i] && this.wasLow[i];
			if (this.on() && this.warning.on() && this.sound.on() && (hit || broke)) {
				float volume = this.volume.f() / 100.0F;
				if (volume > 0.0F) {
					// Dźwięk z Durability Viewer (Guntram Blohm, licencja MIT); przy zniszczeniu części niżej.
					client.getSoundManager().play(SimpleSoundInstance.forUI(TOOL_BREAKING, broke ? 0.8F : 1.0F, volume));
				}
			}

			this.wasLow[i] = low;
			this.hadItem[i] = !stack.isEmpty();
			this.lastDamage[i] = damage;
			this.lastItem[i] = stack.getItem();
		}
	}
}
