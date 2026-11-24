package pl.kerf.project.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import pl.kerf.project.render.AnimatedSprites;

/** Animate Only Visible Textures: animacja niewidocznej tekstury nie jest wysyłana na GPU (zostaje oznaczona jako do wysłania). */
@Mixin(SpriteAtlasTexture.class)
public class SpriteAtlasTextureMixin {
	@WrapOperation(method = "uploadAnimations", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/texture/SpriteContents$Animator;isDirty()Z"))
	private boolean aio$skipHidden(SpriteContents.Animator animator, Operation<Boolean> original) {
		return original.call(animator) && !AnimatedSprites.hidden(animator);
	}
}
