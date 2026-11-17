package pl.kerf.project.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.kerf.project.module.Modules;

@Mixin(ScreenEffectRenderer.class)
public class InGameOverlayRendererMixin {
	@ModifyArg(
		method = "renderFire", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"), index = 1
	)
	private static float aio$fireOffset(float y) {
		return Modules.LOW_FIRE.on() ? y + Modules.LOW_FIRE.offsetY.f() : y;
	}

	@ModifyArg(
		method = "renderFire",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;setColor(FFFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;"),
		index = 3
	)
	private static float aio$fireOpacity(float alpha) {
		return Modules.LOW_FIRE.on() ? alpha * Modules.LOW_FIRE.opacity.f() / 100.0F : alpha;
	}

	/** Clear View: w bloku (np. po wepchnięciu w ścianę) nie ma ciemnej nakładki z teksturą bloku na cały ekran. */
	@Inject(method = "renderTex", at = @At("HEAD"), cancellable = true)
	private static void aio$noInWallOverlay(TextureAtlasSprite sprite, PoseStack matrices, MultiBufferSource vertexConsumers, CallbackInfo ci) {
		if (Modules.CLEAR_VIEW.on() && Modules.CLEAR_VIEW.inWall.on()) {
			ci.cancel();
		}
	}

	@Inject(
		method = "renderItemActivationAnimation",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V", shift = At.Shift.AFTER)
	)
	private void aio$totemScale(PoseStack matrices, float tickProgress, SubmitNodeCollector queue, CallbackInfo ci) {
		if (Modules.TOTEM.on()) {
			float scale = Modules.TOTEM.scale.f();
			matrices.scale(scale, scale, scale);
		}
	}
}
