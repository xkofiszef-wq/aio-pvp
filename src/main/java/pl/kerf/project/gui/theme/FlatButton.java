package pl.kerf.project.gui.theme;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

/** Płaski przycisk w stylu interfejsu moda, z płynnym podświetleniem. Zastępuje vanillowe przyciski. */
public final class FlatButton extends ClickableWidget {
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
	/** Kolorowy pasek z lewej (jak przyciski w Shine); 0 = brak. */
	private int stripe;
	private final Anim hover = new Anim(0.0F, 18.0F);

	private FlatButton(int x, int y, int width, int height, Text message, PressAction action) {
		super(x, y, width, height, message);
		this.action = action;
	}

	public static Builder builder(Text message, PressAction action) {
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

	public FlatButton stripe(int color) {
		this.stripe = color;
		return this;
	}

	@Override
	public void onClick(Click click, boolean doubled) {
		this.action.onPress(this);
	}

	@Override
	public void playDownSound(net.minecraft.client.sound.SoundManager soundManager) {
		soundManager.play(PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK.value(), 1.2F, 0.35F));
	}

	@Override
	protected void renderWidget(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
		TextRenderer font = MinecraftClient.getInstance().textRenderer;
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
				if (this.stripe != 0) {
					// Pasek omija zaokrąglone rogi (r = 4), żeby nie wystawał poza kształt.
					context.fill(x + 1, y + 3, x + 3, y + ht - 3, this.active ? this.stripe : Theme.alpha(this.stripe, 0x50));
				}
				textColor = this.active ? Theme.mix(Theme.TEXT_2, Theme.TEXT, h) : Theme.TEXT_4;
			}
		}

		Text message = this.getMessage();
		int textW = font.getWidth(message);
		int iconW = this.icon != null ? 9 + (textW > 0 ? 5 : 0) : 0;
		int maxText = w - 10 - iconW;
		String shown = Draw.fit(font, message.getString(), maxText);
		int contentW = iconW + font.getWidth(shown);
		// Z paskiem treść od lewej, jak przyciski paska bocznego w Shine.
		int cx = this.stripe != 0 ? x + 8 : x + (w - contentW) / 2;
		int cy = y + (ht - 8) / 2;
		if (this.icon != null) {
			Draw.icon(context, this.icon, cx, cy - 1, textColor);
			cx += iconW;
		}

		boolean styled = shown.equals(message.getString());
		if (styled) {
			context.drawText(font, message, cx, cy, textColor, false);
		} else {
			Draw.text(context, font, shown, cx, cy, textColor);
		}

		if (this.isHovered() && this.tip != null) {
			Tip.offer(this.tip);
		}
	}

	@Override
	protected void appendClickableNarrations(NarrationMessageBuilder builder) {
		this.appendDefaultNarrations(builder);
	}

	public static final class Builder {
		private final Text message;
		private final PressAction action;
		private int x;
		private int y;
		private int width = 100;
		private int height = 20;
		private Tooltip tooltip;
		private Style style = Style.SECONDARY;

		Builder(Text message, PressAction action) {
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
