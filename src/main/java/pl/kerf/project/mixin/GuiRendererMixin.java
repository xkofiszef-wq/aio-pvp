package pl.kerf.project.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import pl.kerf.project.render.HudCache;

/** HUD Caching: przy rysowaniu HUD-u do tekstury GuiRenderer dostaje bufor tekstury zamiast głównego. */
@Mixin(GuiRenderer.class)
public class GuiRendererMixin {
	@WrapOperation(method = "renderPreparedDraws", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;getFramebuffer()Lnet/minecraft/client/gl/Framebuffer;"))
	private Framebuffer aio$cacheTarget(MinecraftClient client, Operation<Framebuffer> original) {
		Framebuffer target = HudCache.target();
		return target != null ? target : original.call(client);
	}
}
