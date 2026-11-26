//? if >=26.1.2 {
/*package pl.kerf.project.mixin;

*///?}
//? if >=26.1.2 <26.3 {
/*import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
*///?}
//? if >=26.3 {
/*import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
*///?}
//? if >=26.1.2 {
/*import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/^* HUD Caching: vanilla'owe rysowanie tekstury z wybranym potokiem (publiczne blit ma na sztywno zwykłe GUI_TEXTURED). ^/
@Mixin(GuiGraphics.class)
public interface GuiGraphicsAccessor {
	@Invoker("innerBlit")
	void aio$innerBlit(RenderPipeline pipeline, GpuTextureView textureView, GpuSampler sampler, int x0, int y0, int x1, int y1, float u0, float u1, float v0,
		float v1, int color);
}
*///?}
