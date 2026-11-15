package pl.kerf.project.hud;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import pl.kerf.project.config.Setting;
import pl.kerf.project.module.HudModule;

public final class PotionStatusHud extends HudModule {
	private static final int ROW = 20;
	private static final int VANILLA_ROW = 33;
	private static final List<StatusEffectInstance> SAMPLE = List.of(
		new StatusEffectInstance(StatusEffects.STRENGTH, 1800, 1), new StatusEffectInstance(StatusEffects.SPEED, 150, 1)
	);

	public final Setting.Mode style = this.add(new Setting.Mode("style", "Wygląd", 0, "Zwykły", "Vanilla GUI (ekwipunek)"));
	public final Setting.Bool showName = this.add(new Setting.Bool("show_name", "Nazwa efektu", true));
	public final Setting.Bool hideAmbient = this.add(new Setting.Bool("hide_ambient", "Pomiń efekty z beaconów", false));

	private final List<StatusEffectInstance> rows = new ArrayList<>();
	private final List<Text> names = new ArrayList<>();
	private final List<Text> times = new ArrayList<>();
	/** Lista i szerokość tekstu liczone raz na tick (czasy efektów zmieniają się co tick, nie co klatkę). */
	private int updatedAge = -1;
	private boolean updatedEditing;
	private int textWidth;

	public PotionStatusHud() {
		super("potion_status", "Potion Status", "Aktywne efekty mikstur z pozostałym czasem (zamiast ikon vanilla w rogu).", true, 0.0F, 0.5F, 0.0F, 0.5F, 4.0F, 0.0F);
	}

	private void update(MinecraftClient client, boolean editing) {
		if (client.player.age == this.updatedAge && editing == this.updatedEditing) {
			return;
		}

		this.updatedAge = client.player.age;
		this.updatedEditing = editing;
		this.rows.clear();
		this.names.clear();
		this.times.clear();
		List<StatusEffectInstance> effects = new ArrayList<>(client.player.getStatusEffects());
		effects.removeIf(effect -> !effect.shouldShowIcon() || this.hideAmbient.on() && effect.isAmbient());
		if (effects.isEmpty() && editing) {
			effects = SAMPLE;
		} else {
			effects.sort(null);
		}

		float tickRate = client.world.getTickManager().getTickRate();
		for (StatusEffectInstance effect : effects) {
			MutableText name = effect.getEffectType().value().getName().copy();
			if (effect.getAmplifier() >= 1 && effect.getAmplifier() <= 9) {
				name.append(" ").append(Text.translatable("enchantment.level." + (effect.getAmplifier() + 1)));
			}

			this.rows.add(effect);
			this.names.add(name);
			this.times.add(StatusEffectUtil.getDurationText(effect, 1.0F, tickRate));
		}

		this.textWidth = this.textWidth(client);
	}

	private boolean vanilla() {
		return this.style.is(1);
	}

	/** Odstęp między wierszami: panel vanilla ma 32 px, zwykły wiersz to ikona 18 px + odstęp. */
	private int row() {
		return this.vanilla() ? VANILLA_ROW : ROW;
	}

	private int textWidth(MinecraftClient client) {
		int text = 0;
		for (int i = 0; i < this.rows.size(); i++) {
			text = Math.max(text, client.textRenderer.getWidth(this.times.get(i)));
			if (this.showName.on()) {
				text = Math.max(text, client.textRenderer.getWidth(this.names.get(i)));
			}
		}

		return text;
	}

	@Override
	public int width(MinecraftClient client, boolean editing) {
		this.update(client, editing);
		if (this.rows.isEmpty()) {
			return 0;
		}

		// Układ panelu jak w ekwipunku: ikona w (7,7), tekst od x=32, 7 px marginesu z prawej.
		return this.vanilla() ? 32 + this.textWidth + 7 : 18 + 4 + this.textWidth;
	}

	@Override
	public int height(MinecraftClient client, boolean editing) {
		int gap = this.vanilla() ? 1 : 2;
		return Math.max(0, this.rows.size() * this.row() - gap);
	}

	@Override
	protected void draw(DrawContext context, MinecraftClient client, RenderTickCounter tickCounter, boolean editing) {
		boolean vanilla = this.vanilla();
		int panelW = 32 + this.textWidth + 7;
		int iconX = vanilla ? 7 : 0;
		int iconY = vanilla ? 7 : 0;
		int textX = vanilla ? 32 : 22;
		// Warstwami (panele, ikony, napisy), nie wiersz po wierszu: mniej warstw GUI = mniej pracy przy każdym elemencie.
		if (vanilla) {
			for (int i = 0; i < this.rows.size(); i++) {
				VanillaGui.effectPanel(context, 0, i * this.row(), panelW, 32, this.rows.get(i).isAmbient());
			}
		}

		for (int i = 0; i < this.rows.size(); i++) {
			context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, InGameHud.getEffectTexture(this.rows.get(i).getEffectType()), iconX, i * this.row() + iconY, 18, 18);
		}

		for (int i = 0; i < this.rows.size(); i++) {
			StatusEffectInstance effect = this.rows.get(i);
			int y = i * this.row();
			// Końcówka efektu (poniżej 10 s) na czerwono.
			boolean ending = !effect.isInfinite() && effect.getDuration() <= 200;
			int timeColor = ending ? 0xFFFF5555 : 0xFFAAAAAA;
			if (this.showName.on()) {
				int textY = y + (vanilla ? 7 : 0);
				context.drawText(client.textRenderer, this.names.get(i), textX, textY, 0xFFFFFFFF, true);
				context.drawText(client.textRenderer, this.times.get(i), textX, textY + (vanilla ? 9 : 10), timeColor, true);
			} else {
				int textY = y + (vanilla ? 12 : 5);
				context.drawText(client.textRenderer, this.times.get(i), textX, textY, ending ? timeColor : 0xFFFFFFFF, true);
			}
		}
	}
}
