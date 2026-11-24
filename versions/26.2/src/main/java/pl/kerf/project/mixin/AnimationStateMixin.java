package pl.kerf.project.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import pl.kerf.project.render.AnimatedSprites;

/** Animate Only Visible Textures: animacja niewidocznej tekstury nie jest wysyłana na GPU. */
@Mixin(SpriteContents.AnimationState.class)
public class AnimationStateMixin {
	@ModifyReturnValue(method = "needsToDraw", at = @At("RETURN"))
	private boolean aio$skipHidden(boolean original) {
		return original && !AnimatedSprites.hidden((SpriteContents.AnimationState) (Object) this);
	}
}
