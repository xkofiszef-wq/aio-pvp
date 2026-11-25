package pl.kerf.project.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import pl.kerf.project.render.HudCache;

/** HUD Caching: przy rysowaniu HUD-u do tekstury GuiRenderer dostaje bufor tekstury zamiast głównego. */
@Mixin(GuiRenderer.class)
public class GuiRendererMixin {
	@WrapOperation(method = "draw", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getMainRenderTarget()Lcom/mojang/blaze3d/pipeline/RenderTarget;"))
	private RenderTarget aio$cacheTarget(Minecraft client, Operation<RenderTarget> original) {
		RenderTarget target = HudCache.target();
		return target != null ? target : original.call(client);
	}
}
