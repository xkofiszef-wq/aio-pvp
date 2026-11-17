package pl.kerf.project.hud;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import pl.kerf.project.config.Setting;
import pl.kerf.project.module.HudModule;

public final class PotionStatusHud extends HudModule {
	private static final int ROW = 20;
	private static final int VANILLA_ROW = 33;
	private static final List<MobEffectInstance> SAMPLE = List.of(
		new MobEffectInstance(MobEffects.STRENGTH, 1800, 1), new MobEffectInstance(MobEffects.SPEED, 150, 1)
	);

	public final Setting.Mode style = this.add(new Setting.Mode("style", "Wygląd", 0, "Zwykły", "Vanilla GUI", "Resourcepack GUI"));
	public final Setting.Bool showName = this.add(new Setting.Bool("show_name", "Nazwa efektu", true));
	public final Setting.Bool hideAmbient = this.add(new Setting.Bool("hide_ambient", "Pomiń efekty z beaconów", false));
	public final Setting.Mode layout = this.add(new Setting.Mode("layout", "Układ", 0, "Pionowo", "Poziomo"));

	private final List<MobEffectInstance> rows = new ArrayList<>();
	private final List<Component> names = new ArrayList<>();
	private final List<Component> times = new ArrayList<>();
	/** Lista i szerokość tekstu liczone raz na tick (czasy efektów zmieniają się co tick, nie co klatkę). */
	private int updatedAge = -1;
	private boolean updatedEditing;
	private int textWidth;

	public PotionStatusHud() {
		super("potion_status", "Potion Status", "Aktywne efekty mikstur z pozostałym czasem (zamiast ikon vanilla w rogu).", true, 0.0F, 0.5F, 0.0F, 0.5F, 4.0F, 0.0F);
	}

	private void update(Minecraft client, boolean editing) {
		if (client.player.tickCount == this.updatedAge && editing == this.updatedEditing) {
			return;
		}

		this.updatedAge = client.player.tickCount;
		this.updatedEditing = editing;
		this.rows.clear();
		this.names.clear();
		this.times.clear();
		List<MobEffectInstance> effects = new ArrayList<>(client.player.getActiveEffects());
		effects.removeIf(effect -> !effect.showIcon() || this.hideAmbient.on() && effect.isAmbient());
		if (effects.isEmpty() && editing) {
			effects = SAMPLE;
		} else {
			effects.sort(null);
		}

		float tickRate = client.level.tickRateManager().tickrate();
		for (MobEffectInstance effect : effects) {
			MutableComponent name = effect.getEffect().value().getDisplayName().copy();
			if (effect.getAmplifier() >= 1 && effect.getAmplifier() <= 9) {
				name.append(" ").append(Component.translatable("enchantment.level." + (effect.getAmplifier() + 1)));
			}

			this.rows.add(effect);
			this.names.add(name);
			this.times.add(MobEffectUtil.formatDuration(effect, 1.0F, tickRate));
		}

		this.textWidth = this.textWidth(client);
	}

	private boolean vanilla() {
		return !this.style.is(0);
	}

	/** Odstęp między wierszami: panel vanilla ma 32 px, zwykły wiersz to ikona 18 px + odstęp. */
	private int row() {
		return this.vanilla() ? VANILLA_ROW : ROW;
	}

	private boolean horizontal() {
		return this.layout.is(1);
	}

	/** Odstęp między efektami (pionowo między wierszami, poziomo między kolumnami). */
	private int gap() {
		return this.vanilla() ? 1 : 2;
	}

	/** Szerokość jednego efektu. Układ panelu jak w ekwipunku: ikona w (7,7), tekst od x=32, 7 px marginesu z prawej. */
	private int cellW() {
		return this.vanilla() ? 32 + this.textWidth + 7 : 18 + 4 + this.textWidth;
	}

	private int column() {
		return this.cellW() + this.gap() + (this.vanilla() ? 0 : 4);
	}

	private int cellX(int i) {
		return this.horizontal() ? i * this.column() : 0;
	}

	private int cellY(int i) {
		return this.horizontal() ? 0 : i * this.row();
	}

	private int textWidth(Minecraft client) {
		int text = 0;
		for (int i = 0; i < this.rows.size(); i++) {
			text = Math.max(text, client.font.width(this.times.get(i)));
			if (this.showName.on()) {
				text = Math.max(text, client.font.width(this.names.get(i)));
			}
		}

		return text;
	}

	@Override
	public int width(Minecraft client, boolean editing) {
		this.update(client, editing);
		if (this.rows.isEmpty()) {
			return 0;
		}

		return this.horizontal() ? (this.rows.size() - 1) * this.column() + this.cellW() : this.cellW();
	}

	@Override
	public int height(Minecraft client, boolean editing) {
		if (this.horizontal()) {
			return this.rows.isEmpty() ? 0 : this.row() - this.gap();
		}

		return Math.max(0, this.rows.size() * this.row() - this.gap());
	}

	@Override
	protected void draw(GuiGraphicsExtractor context, Minecraft client, DeltaTracker tickCounter, boolean editing) {
		boolean vanilla = this.vanilla();
		int panelW = 32 + this.textWidth + 7;
		int iconX = vanilla ? 7 : 0;
		int iconY = vanilla ? 7 : 0;
		int textX = vanilla ? 32 : 22;
		// Warstwami (panele, ikony, napisy), nie wiersz po wierszu: mniej warstw GUI = mniej pracy przy każdym elemencie.
		if (vanilla) {
			for (int i = 0; i < this.rows.size(); i++) {
				VanillaGui.effectPanel(context, this.cellX(i), this.cellY(i), panelW, 32, this.rows.get(i).isAmbient(), this.style.is(2));
			}
		}

		for (int i = 0; i < this.rows.size(); i++) {
			context.blitSprite(RenderPipelines.GUI_TEXTURED, Gui.getMobEffectSprite(this.rows.get(i).getEffect()), this.cellX(i) + iconX, this.cellY(i) + iconY, 18, 18);
		}

		for (int i = 0; i < this.rows.size(); i++) {
			MobEffectInstance effect = this.rows.get(i);
			int x = this.cellX(i) + textX;
			int y = this.cellY(i);
			// Końcówka efektu (poniżej 10 s) na czerwono.
			boolean ending = !effect.isInfiniteDuration() && effect.getDuration() <= 200;
			int timeColor = ending ? 0xFFFF5555 : 0xFFAAAAAA;
			if (this.showName.on()) {
				int textY = y + (vanilla ? 7 : 0);
				context.text(client.font, this.names.get(i), x, textY, 0xFFFFFFFF, true);
				context.text(client.font, this.times.get(i), x, textY + (vanilla ? 9 : 10), timeColor, true);
			} else {
				int textY = y + (vanilla ? 12 : 5);
				context.text(client.font, this.times.get(i), x, textY, ending ? timeColor : 0xFFFFFFFF, true);
			}
		}
	}
}
