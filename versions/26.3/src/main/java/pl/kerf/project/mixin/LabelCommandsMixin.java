package pl.kerf.project.mixin;

import net.minecraft.client.renderer.SubmitNodeCollection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import pl.kerf.project.module.Modules;
import pl.kerf.project.render.HealthLabel;

@Mixin(SubmitNodeCollection.class)
public class LabelCommandsMixin {
	private static final String SUBMIT_INIT =
		"Lnet/minecraft/client/renderer/SubmitNodeCollection;nameTag(Lorg/joml/Matrix4f;FFLnet/minecraft/util/FormattedCharSequence;IIILnet/minecraft/client/gui/Font$DisplayMode;)Lnet/minecraft/client/renderer/feature/TextFeatureRenderer$Submit;";

	/** Vanilla rysuje nick półprzezroczystą bielą (stąd szary wygląd); tu pełna biel. */
	@ModifyArg(method = "submitNameTag", at = @At(value = "INVOKE", target = SUBMIT_INIT), index = 5)
	private int aio$textColor(int color) {
		return Modules.NAMETAGS.on() && Modules.NAMETAGS.whiteText.on() ? 0xFFFFFFFF : color;
	}

	@ModifyArg(method = "submitNameTag", at = @At(value = "INVOKE", target = SUBMIT_INIT), index = 6)
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
