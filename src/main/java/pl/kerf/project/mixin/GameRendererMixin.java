package pl.kerf.project.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
//? if >=26.1.2 {
/*import net.minecraft.client.renderer.state.level.CameraRenderState;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import pl.kerf.project.module.Modules;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
	/** No Camera Shake: pomija bujanie kamery świata; osobne wywołanie dla ręki (renderHand) zostaje, więc ręka się buja. */
	@WrapWithCondition(
		method = "renderLevel",
		//? if <26.1.2 {
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;bobView(Lcom/mojang/blaze3d/vertex/PoseStack;F)V")
		//?} else {
		/*at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;bobView(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V")
		*///?}
	)
	//? if <26.1.2 {
	private boolean aio$noCameraShake(GameRenderer renderer, PoseStack matrices, float tickProgress) {
	//?} else {
	/*private boolean aio$noCameraShake(GameRenderer renderer, CameraRenderState camera, PoseStack matrices) {
	*///?}
		return !Modules.NO_CAMERA_SHAKE.on();
	}
}
