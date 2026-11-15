package pl.kerf.project.mixin;

import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.kerf.project.module.Modules;

@Mixin(InGameOverlayRenderer.class)
public class InGameOverlayRendererMixin {
	@ModifyArg(
		method = "renderFireOverlay", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;translate(FFF)V"), index = 1
	)
	private static float aio$fireOffset(float y) {
		return Modules.LOW_FIRE.on() ? y + Modules.LOW_FIRE.offsetY.f() : y;
	}

	@ModifyArg(
		method = "renderFireOverlay",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/VertexConsumer;color(FFFF)Lnet/minecraft/client/render/VertexConsumer;"),
		index = 3
	)
	private static float aio$fireOpacity(float alpha) {
		return Modules.LOW_FIRE.on() ? alpha * Modules.LOW_FIRE.opacity.f() / 100.0F : alpha;
	}

	/** Clear View: w bloku (np. po wepchnięciu w ścianę) nie ma ciemnej nakładki z teksturą bloku na cały ekran. */
	@Inject(method = "renderInWallOverlay", at = @At("HEAD"), cancellable = true)
	private static void aio$noInWallOverlay(Sprite sprite, MatrixStack matrices, VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
		if (Modules.CLEAR_VIEW.on() && Modules.CLEAR_VIEW.inWall.on()) {
			ci.cancel();
		}
	}

	@Inject(
		method = "renderFloatingItem",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;scale(FFF)V", shift = At.Shift.AFTER)
	)
	private void aio$totemScale(MatrixStack matrices, float tickProgress, OrderedRenderCommandQueue queue, CallbackInfo ci) {
		if (Modules.TOTEM.on()) {
			float scale = Modules.TOTEM.scale.f();
			matrices.scale(scale, scale, scale);
		}
	}
}
