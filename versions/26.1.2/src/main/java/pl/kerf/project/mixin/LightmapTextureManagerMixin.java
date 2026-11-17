package pl.kerf.project.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;
import pl.kerf.project.module.Modules;

@Mixin(LightmapRenderStateExtractor.class)
public class LightmapTextureManagerMixin {
	/** Podmienia gammę tylko w obliczeniach lightmapy; suwak jasności w opcjach gry zostaje nietknięty. */
	@ModifyExpressionValue(
		method = "extract",
		slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;gamma()Lnet/minecraft/client/OptionInstance;")),
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 0)
	)
	private Object aio$fullbright(Object gamma) {
		return Modules.FULLBRIGHT.on() ? (Object) Double.valueOf(Modules.FULLBRIGHT.brightness.get() / 100.0) : gamma;
	}
}
