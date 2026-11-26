package pl.kerf.project.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import pl.kerf.project.render.AnimatedSprites;

/** Animate Only Visible Textures: zapamiętuje, do której tekstury należy dana animacja. */
@Mixin(SpriteContents.class)
public class SpriteContentsMixin {
	@ModifyReturnValue(method = "createAnimationState", at = @At("RETURN"))
	//? if <26.1.2 {
	private SpriteContents.AnimationState aio$registerOwner(SpriteContents.AnimationState animator) {
		if (animator != null) {
			AnimatedSprites.register(animator, (SpriteContents) (Object) this);
	//?} else {
	/*private SpriteContents.AnimationState aio$registerOwner(SpriteContents.AnimationState state) {
		if (state != null) {
			AnimatedSprites.register(state, (SpriteContents) (Object) this);
	*///?}
		}

		//? if <26.1.2 {
		return animator;
		//?} else {
		/*return state;
		*///?}
	}
}
