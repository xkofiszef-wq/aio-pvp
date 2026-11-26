package pl.kerf.project.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
//? if <26.3 {
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
//?} else {
/*import net.minecraft.client.renderer.texture.UvMapping;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import pl.kerf.project.module.Modules;

/** Invisible Hand: ręka w pierwszej osobie z kryciem z ustawień, gdy gracz ma niewidzialność. */
@Mixin(AvatarRenderer.class)
public class PlayerArmMixin {
	@WrapOperation(
		method = "renderHand",
		at = @At(
			value = "INVOKE",
			//? if <26.3 {
			target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModelPart(Lnet/minecraft/client/model/geom/ModelPart;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IILnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V"
			//?} else {
			/*target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModelPart(Lnet/minecraft/client/model/geom/ModelPart;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IILnet/minecraft/client/renderer/texture/UvMapping;)V"
			*///?}
		)
	)
	private void aio$translucentArm(
		//? if <26.3 {
		SubmitNodeCollector queue, ModelPart part, PoseStack matrices, RenderType layer, int light, int overlay, TextureAtlasSprite sprite, Operation<Void> original
		//?} else {
		/*SubmitNodeCollector queue, ModelPart part, PoseStack matrices, RenderType layer, int light, int overlay, UvMapping sprite, Operation<Void> original
		*///?}
	) {
		Minecraft client = Minecraft.getInstance();
		if (Modules.INVISIBLE_HAND.on() && client.player != null && client.player.isInvisible()) {
			// Warstwa ręki w vanilla jest już przezroczysta (entityTranslucent), więc wystarczy alfa w kolorze.
			int alpha = Math.round(Modules.INVISIBLE_HAND.opacity.f() / 100.0F * 255.0F);
			//? if <26.3 {
			queue.submitModelPart(part, matrices, layer, light, overlay, sprite, alpha << 24 | 0xFFFFFF, null);
			//?} else {
			/*queue.submitModelPart(part, matrices, layer, light, overlay, sprite, alpha << 24 | 0xFFFFFF);
			*///?}
			return;
		}

		original.call(queue, part, matrices, layer, light, overlay, sprite);
	}
}
