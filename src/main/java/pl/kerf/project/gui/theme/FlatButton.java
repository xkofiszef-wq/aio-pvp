package pl.kerf.project.gui.theme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/** Płaski przycisk w stylu interfejsu moda, z płynnym podświetleniem. Zastępuje vanillowe przyciski. */
public final class FlatButton extends AbstractWidget {
	public enum Style {
		/** Wypełniony akcentem: główna akcja ekranu. */
		PRIMARY,
		/** Ciemna powierzchnia z obramowaniem: zwykła akcja. */
		SECONDARY,
		/** Bez tła, tylko podświetlenie przy najechaniu. */
		GHOST
	}

	@FunctionalInterface
	public interface PressAction {
		void onPress(FlatButton button);
	}

	private final PressAction action;
	private Style style = Style.SECONDARY;
	private Draw.Icon icon;
	private String tip;
	private final Anim hover = new Anim(0.0F, 18.0F);

	private FlatButton(int x, int y, int width, int height, Component message, PressAction action) {
		super(x, y, width, height, message);
		this.action = action;
	}

	public static Builder builder(Component message, PressAction action) {
		return new Builder(message, action);
	}

	public FlatButton style(Style style) {
		this.style = style;
		return this;
	}

	public FlatButton icon(Draw.Icon icon) {
		this.icon = icon;
		return this;
	}

	/** Podpowiedź pokazywana po chwili najechania (rysowana przez {@link Tip}). */
	public FlatButton tip(String tip) {
		this.tip = tip;
		return this;
	}

	@Override
	public void onClick(MouseButtonEvent click, boolean doubled) {
		this.action.onPress(this);
	}

	@Override
	public void playDownSound(net.minecraft.client.sounds.SoundManager soundManager) {
		soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.2F, 0.35F));
	}

	@Override
	//? if <26.1.2 {
	protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
	//?} else {
	/*protected void extractWidgetRenderState(GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
	*///?}
		Font font = Minecraft.getInstance().font;
		float h = this.hover.to(this.isHovered() && this.active);
		int x = this.getX();
		int y = this.getY();
		int w = this.width;
		int ht = this.height;
		// Styl z projektu w Figmie (komponent "Przycisk"): rogi 4 px, ciemny napis na głównym, jaśniejsza ramka po najechaniu.
		int r = 4;
		int textColor;
		switch (this.style) {
			case PRIMARY -> {
				int fill = this.active ? Theme.mix(Theme.ACCENT, 0xFFA8A8F6, h) : Theme.SURFACE_3;
				Draw.roundExact(context, x, y, w, ht, r, fill);
				textColor = this.active ? 0xFF0C1020 : Theme.TEXT_4;
			}
			case GHOST -> {
				if (h > 0.01F) {
					Draw.roundExact(context, x, y, w, ht, r, Theme.alpha(Theme.SURFACE_HOVER, Math.round((Theme.SURFACE_HOVER >>> 24) * h)));
				}

				textColor = this.active ? Theme.mix(Theme.TEXT_2, Theme.TEXT, h) : Theme.TEXT_4;
			}
			default -> {
				Draw.roundExact(context, x, y, w, ht, r, Theme.mix(Theme.CARD, Theme.CARD_HOVER, h));
				Draw.outlineExact(context, x, y, w, ht, r, Theme.mix(Theme.BORDER, Theme.BORDER_STRONG, h));
				textColor = this.active ? Theme.mix(Theme.TEXT_2, Theme.TEXT, h) : Theme.TEXT_4;
			}
		}

		Component message = this.getMessage();
		int textW = font.width(message);
		int iconW = this.icon != null ? 9 + (textW > 0 ? 5 : 0) : 0;
		int maxText = w - 10 - iconW;
		String shown = Draw.fit(font, message.getString(), maxText);
		int contentW = iconW + font.width(shown);
		int cx = x + (w - contentW) / 2;
		int cy = y + (ht - 8) / 2;
		if (this.icon != null) {
			Draw.icon(context, this.icon, cx, cy - 1, textColor);
			cx += iconW;
		}

		boolean styled = shown.equals(message.getString());
		if (styled) {
			context.drawString(font, message, cx, cy, textColor, false);
		} else {
			Draw.text(context, font, shown, cx, cy, textColor);
		}

		if (this.isHovered() && this.tip != null) {
			Tip.offer(this.tip);
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput builder) {
		this.defaultButtonNarrationText(builder);
	}

	public static final class Builder {
		private final Component message;
		private final PressAction action;
		private int x;
		private int y;
		private int width = 100;
		private int height = 20;
		private Tooltip tooltip;
		private Style style = Style.SECONDARY;

		Builder(Component message, PressAction action) {
			this.message = message;
			this.action = action;
		}

		public Builder dimensions(int x, int y, int width, int height) {
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
			return this;
		}

		public Builder tooltip(Tooltip tooltip) {
			this.tooltip = tooltip;
			return this;
		}

		public Builder style(Style style) {
			this.style = style;
			return this;
		}

		public FlatButton build() {
			FlatButton button = new FlatButton(this.x, this.y, this.width, this.height, this.message, this.action);
			button.style = this.style;
			if (this.tooltip != null) {
				button.setTooltip(this.tooltip);
			}

			return button;
		}
	}
}
