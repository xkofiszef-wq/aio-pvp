package pl.kerf.project.mixin;

import java.nio.ByteBuffer;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.kerf.project.render.FogOcclusion;

/** Fog Occlusion: podgląda dane mgły zapisywane do bufora, nic w nich nie zmienia. */
@Mixin(FogRenderer.class)
public class FogRendererMixin {
	@Inject(method = "updateBuffer(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V", at = @At("HEAD"))
	private void aio$captureFog(ByteBuffer buffer, int bufPos, Vector4f fogColor, float environmentalStart, float environmentalEnd,
		float renderDistanceStart, float renderDistanceEnd, float skyEnd, float cloudEnd, CallbackInfo ci) {
		FogOcclusion.capture(environmentalEnd, renderDistanceEnd);
	}
}
