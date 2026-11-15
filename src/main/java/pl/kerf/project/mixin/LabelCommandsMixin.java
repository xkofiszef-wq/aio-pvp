package pl.kerf.project.mixin;

import net.minecraft.client.render.command.LabelCommandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import pl.kerf.project.module.Modules;
import pl.kerf.project.render.HealthLabel;

@Mixin(LabelCommandRenderer.Commands.class)
public class LabelCommandsMixin {
	private static final String LABEL_INIT =
		"Lnet/minecraft/client/render/command/OrderedRenderCommandQueueImpl$LabelCommand;<init>(Lorg/joml/Matrix4f;FFLnet/minecraft/text/Text;IIID)V";

	/** Vanilla rysuje nick półprzezroczystą bielą (stąd szary wygląd); tu pełna biel. */
	@ModifyArg(method = "add", at = @At(value = "INVOKE", target = LABEL_INIT), index = 5)
	private int aio$textColor(int color) {
		return Modules.NAMETAGS.on() && Modules.NAMETAGS.whiteText.on() ? 0xFFFFFFFF : color;
	}

	@ModifyArg(method = "add", at = @At(value = "INVOKE", target = LABEL_INIT), index = 6)
	private int aio$backgroundColor(int color) {
		if (HealthLabel.submitting) {
			// Serca nad głową bez ciemnego prostokąta pod spodem.
			return 0;
		}

		// 0 oznacza przebieg bez tła (jasny tekst na wierzchu) - tam tła nie dodajemy, bo rysowałoby się podwójnie.
		if (!Modules.NAMETAGS.on() || color == 0) {
			return color;
		}

		return Modules.NAMETAGS.backgroundEnabled.on() ? Modules.NAMETAGS.background.argb() : 0;
	}
}
