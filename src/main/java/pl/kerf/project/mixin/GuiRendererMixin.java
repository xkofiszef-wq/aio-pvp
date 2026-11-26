package pl.kerf.project.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
//? if =26.1.2 {
/*import net.minecraft.client.gui.render.GuiRenderer;
*///?}
//? if <26.2 {
import net.minecraft.client.Minecraft;
//?}
//? if =1.21.11 || =26.2 || =26.3 {
import net.minecraft.client.gui.render.GuiRenderer;
//?}
//? if >=26.2 {
/*import net.minecraft.client.renderer.GameRenderer;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import pl.kerf.project.render.HudCache;

/** HUD Caching: przy rysowaniu HUD-u do tekstury GuiRenderer dostaje bufor tekstury zamiast głównego. */
@Mixin(GuiRenderer.class)
public class GuiRendererMixin {
	//? if <26.2 {
	@WrapOperation(method = "draw", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getMainRenderTarget()Lcom/mojang/blaze3d/pipeline/RenderTarget;"))
	private RenderTarget aio$cacheTarget(Minecraft client, Operation<RenderTarget> original) {
	//?} else {
	/*@WrapOperation(method = "draw", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;mainRenderTarget()Lcom/mojang/blaze3d/pipeline/RenderTarget;"))
	private RenderTarget aio$cacheTarget(GameRenderer renderer, Operation<RenderTarget> original) {
	*///?}
		RenderTarget target = HudCache.target();
		//? if <26.2 {
		return target != null ? target : original.call(client);
		//?} else {
		/*return target != null ? target : original.call(renderer);
		*///?}
	}
}
