//? if >=26.1.2 {
/*package pl.kerf.project.mixin;

import java.util.Set;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.TranslucencyPointOfView;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.kerf.project.render.AnimatedSprites;

/^* Siatka fragmentu (to, co zostaje po budowie) przejmuje zbiór animowanych tekstur z wyniku budowy. ^/
@Mixin(CompiledSectionMesh.class)
public class CompiledSectionMeshMixin implements AnimatedSprites.SpritesHolder {
	@Unique
	private Set<SpriteContents> aio$sprites;

	@Inject(method = "<init>", at = @At("RETURN"))
*///?}
	//? if >=26.1.2 <26.3 {
	/*private void aio$copySprites(TranslucencyPointOfView pov, SectionCompiler.Results results, CallbackInfo ci) {
	*///?}
	//? if >=26.3 {
	/*private void aio$copySprites(TranslucencyPointOfView pov, SectionCompiler.Results results, long unused, CallbackInfo ci) {
	*///?}
		//? if >=26.1.2 {
		/*this.aio$sprites = ((AnimatedSprites.SpritesHolder) (Object) results).aio$sprites();
	}

	@Override
	public Set<SpriteContents> aio$sprites() {
		return this.aio$sprites;
	}

	@Override
	public void aio$setSprites(Set<SpriteContents> sprites) {
		this.aio$sprites = sprites;
	}
}
		*///?}
