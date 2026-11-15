package pl.kerf.project.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import pl.kerf.project.module.Modules;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
	/** No Camera Shake: pomija bujanie kamery świata; osobne wywołanie dla ręki (renderHand) zostaje, więc ręka się buja. */
	@WrapWithCondition(
		method = "renderWorld",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;bobView(Lnet/minecraft/client/util/math/MatrixStack;F)V")
	)
	private boolean aio$noCameraShake(GameRenderer renderer, MatrixStack matrices, float tickProgress) {
		return !Modules.NO_CAMERA_SHAKE.on();
	}
}
