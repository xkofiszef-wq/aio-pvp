package pl.kerf.project.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;
import pl.kerf.project.module.Modules;

@Mixin(LightmapTextureManager.class)
public class LightmapTextureManagerMixin {
	/** Podmienia gammę tylko w obliczeniach lightmapy; suwak jasności w opcjach gry zostaje nietknięty. */
	@ModifyExpressionValue(
		method = "update",
		slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/GameOptions;getGamma()Lnet/minecraft/client/option/SimpleOption;")),
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/SimpleOption;getValue()Ljava/lang/Object;", ordinal = 0)
	)
	private Object aio$fullbright(Object gamma) {
		return Modules.FULLBRIGHT.on() ? (Object) Double.valueOf(Modules.FULLBRIGHT.brightness.get() / 100.0) : gamma;
	}
}
