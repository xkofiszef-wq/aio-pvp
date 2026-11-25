package pl.kerf.project.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** HUD Caching: vanilla'owe rysowanie tekstury z wybranym potokiem (publiczne blit ma na sztywno zwykłe GUI_TEXTURED). */
@Mixin(GuiGraphicsExtractor.class)
public interface GuiGraphicsAccessor {
	@Invoker("innerBlit")
	void aio$innerBlit(RenderPipeline pipeline, GpuTextureView textureView, GpuSampler sampler, int x0, int y0, int x1, int y1, float u0, float u1, float v0,
		float v1, int color);
}
