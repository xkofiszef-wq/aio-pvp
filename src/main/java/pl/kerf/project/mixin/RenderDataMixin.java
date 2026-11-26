//? if <26.1.2 {
package pl.kerf.project.mixin;

import java.util.Set;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import pl.kerf.project.render.AnimatedSprites;

/** Wynik budowy fragmentu niesie zbiór jego animowanych tekstur (null = zbudowany przy wyłączonej opcji). */
@Mixin(SectionCompiler.Results.class)
public class RenderDataMixin implements AnimatedSprites.SpritesHolder {
	@Unique
	private Set<SpriteContents> aio$sprites;

	@Override
	public Set<SpriteContents> aio$sprites() {
		return this.aio$sprites;
	}

	@Override
	public void aio$setSprites(Set<SpriteContents> sprites) {
		this.aio$sprites = sprites;
	}
}
//?}
