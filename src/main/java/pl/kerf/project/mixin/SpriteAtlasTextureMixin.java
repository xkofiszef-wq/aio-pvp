//? if <26.1.2 {
package pl.kerf.project.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import pl.kerf.project.render.AnimatedSprites;

/** Animate Only Visible Textures: animacja niewidocznej tekstury nie jest wysyłana na GPU (zostaje oznaczona jako do wysłania). */
@Mixin(TextureAtlas.class)
public class SpriteAtlasTextureMixin {
	@WrapOperation(method = "uploadAnimationFrames", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/SpriteContents$AnimationState;needsToDraw()Z"))
	private boolean aio$skipHidden(SpriteContents.AnimationState animator, Operation<Boolean> original) {
		return original.call(animator) && !AnimatedSprites.hidden(animator);
	}
}
//?}
