package pl.kerf.project.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
//? if <26.1.2 {
import net.minecraft.client.renderer.LightTexture;
//?} else {
/*import net.minecraft.client.renderer.LightmapRenderStateExtractor;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;
import pl.kerf.project.module.Modules;

//? if <26.1.2 {
@Mixin(LightTexture.class)
//?} else {
/*@Mixin(LightmapRenderStateExtractor.class)
*///?}
public class LightmapTextureManagerMixin {
	/** Podmienia gammę tylko w obliczeniach lightmapy; suwak jasności w opcjach gry zostaje nietknięty. */
	@ModifyExpressionValue(
		//? if <26.1.2 {
		method = "updateLightTexture",
		//?} else {
		/*method = "extract",
		*///?}
		slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;gamma()Lnet/minecraft/client/OptionInstance;")),
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 0)
	)
	private Object aio$fullbright(Object gamma) {
		return Modules.FULLBRIGHT.on() ? (Object) Double.valueOf(Modules.FULLBRIGHT.brightness.get() / 100.0) : gamma;
	}
}
