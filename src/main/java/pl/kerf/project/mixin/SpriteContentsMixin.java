package pl.kerf.project.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import pl.kerf.project.render.AnimatedSprites;

/** Animate Only Visible Textures: zapamiętuje, do której tekstury należy dana animacja. */
@Mixin(SpriteContents.class)
public class SpriteContentsMixin {
	@ModifyReturnValue(method = "createAnimator", at = @At("RETURN"))
	private SpriteContents.Animator aio$registerOwner(SpriteContents.Animator animator) {
		if (animator != null) {
			AnimatedSprites.register(animator, (SpriteContents) (Object) this);
		}

		return animator;
	}
}
