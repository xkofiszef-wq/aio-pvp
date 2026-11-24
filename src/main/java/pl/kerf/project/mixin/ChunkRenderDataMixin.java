package pl.kerf.project.mixin;

import java.util.Set;
import net.minecraft.client.render.chunk.ChunkRenderData;
import net.minecraft.client.render.chunk.NormalizedRelativePos;
import net.minecraft.client.render.chunk.SectionBuilder;
import net.minecraft.client.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.kerf.project.render.AnimatedSprites;

/** Dane renderowania fragmentu (to, co zostaje po budowie) przejmują zbiór animowanych tekstur z wyniku budowy. */
@Mixin(ChunkRenderData.class)
public class ChunkRenderDataMixin implements AnimatedSprites.SpritesHolder {
	@Unique
	private Set<SpriteContents> aio$sprites;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void aio$copySprites(NormalizedRelativePos pos, SectionBuilder.RenderData renderData, CallbackInfo ci) {
		this.aio$sprites = ((AnimatedSprites.SpritesHolder) (Object) renderData).aio$sprites();
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
