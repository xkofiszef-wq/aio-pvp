package pl.kerf.project.mixin;

//? if <26.2 {
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
//?} else {
/*import net.minecraft.client.renderer.SubmitNodeCollection;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import pl.kerf.project.module.Modules;
import pl.kerf.project.render.HealthLabel;

//? if <26.2 {
@Mixin(NameTagFeatureRenderer.Storage.class)
//?} else {
/*@Mixin(SubmitNodeCollection.class)
*///?}
public class LabelCommandsMixin {
	//? if <26.1.2 {
	private static final String LABEL_INIT =
		"Lnet/minecraft/client/renderer/SubmitNodeStorage$NameTagSubmit;<init>(Lorg/joml/Matrix4f;FFLnet/minecraft/network/chat/Component;IIID)V";
	//?}
	//? if >=26.2 {
	/*private static final String SUBMIT_INIT =
	*///?}
		//? if =26.2 {
		/*"Lnet/minecraft/client/renderer/feature/NameTagFeatureRenderer$Submit;<init>(Lorg/joml/Matrix4fc;FFLnet/minecraft/network/chat/Component;IIILnet/minecraft/client/gui/Font$DisplayMode;)V";
		*///?}
		//? if >=26.3 {
		/*"Lnet/minecraft/client/renderer/SubmitNodeCollection;nameTag(Lorg/joml/Matrix4f;FFLnet/minecraft/util/FormattedCharSequence;IIILnet/minecraft/client/gui/Font$DisplayMode;)Lnet/minecraft/client/renderer/feature/TextFeatureRenderer$Submit;";
		*///?}

	/** Vanilla rysuje nick półprzezroczystą bielą (stąd szary wygląd); tu pełna biel. */
	//? if <26.1.2 {
	@ModifyArg(method = "add", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeStorage$NameTagSubmit;<init>(Lorg/joml/Matrix4f;FFLnet/minecraft/network/chat/Component;IIID)V"), index = 5)
	//?}
	//? if >=26.2 {
	/*@ModifyArg(method = "submitNameTag", at = @At(value = "INVOKE", target = SUBMIT_INIT), index = 5)
	*///?}
	//? if =26.1.2 {
	/*@ModifyArg(method = "add", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeStorage$NameTagSubmit;<init>(Lorg/joml/Matrix4fc;FFLnet/minecraft/network/chat/Component;IIID)V"), index = 5)
	*///?}
	private int aio$textColor(int color) {
		return Modules.NAMETAGS.on() && Modules.NAMETAGS.whiteText.on() ? 0xFFFFFFFF : color;
	}

	//? if <26.1.2 {
	@ModifyArg(method = "add", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeStorage$NameTagSubmit;<init>(Lorg/joml/Matrix4f;FFLnet/minecraft/network/chat/Component;IIID)V"), index = 6)
	//?}
	//? if >=26.2 {
	/*@ModifyArg(method = "submitNameTag", at = @At(value = "INVOKE", target = SUBMIT_INIT), index = 6)
	*///?}
	//? if =26.1.2 {
	/*@ModifyArg(method = "add", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeStorage$NameTagSubmit;<init>(Lorg/joml/Matrix4fc;FFLnet/minecraft/network/chat/Component;IIID)V"), index = 6)
	*///?}
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
