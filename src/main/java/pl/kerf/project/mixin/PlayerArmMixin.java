package pl.kerf.project.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import pl.kerf.project.module.Modules;

/** Invisible Hand: ręka w pierwszej osobie z kryciem z ustawień, gdy gracz ma niewidzialność. */
@Mixin(PlayerEntityRenderer.class)
public class PlayerArmMixin {
	@WrapOperation(
		method = "renderArm",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;submitModelPart(Lnet/minecraft/client/model/ModelPart;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IILnet/minecraft/client/texture/Sprite;)V"
		)
	)
	private void aio$translucentArm(
		OrderedRenderCommandQueue queue, ModelPart part, MatrixStack matrices, RenderLayer layer, int light, int overlay, Sprite sprite, Operation<Void> original
	) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (Modules.INVISIBLE_HAND.on() && client.player != null && client.player.isInvisible()) {
			// Warstwa ręki w vanilla jest już przezroczysta (entityTranslucent), więc wystarczy alfa w kolorze.
			int alpha = Math.round(Modules.INVISIBLE_HAND.opacity.f() / 100.0F * 255.0F);
			queue.submitModelPart(part, matrices, layer, light, overlay, sprite, alpha << 24 | 0xFFFFFF, null);
			return;
		}

		original.call(queue, part, matrices, layer, light, overlay, sprite);
	}
}
