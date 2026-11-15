package pl.kerf.project.mixin;

import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Saturation: kolory pikseli ikonki z texturepacka (vanilla daje tylko "czy przezroczysty"). */
@Mixin(SpriteContents.class)
public interface SpriteContentsAccessor {
	@Accessor("image")
	NativeImage aio$image();
}
