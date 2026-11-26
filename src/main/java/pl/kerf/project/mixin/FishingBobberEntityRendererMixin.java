package pl.kerf.project.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.kerf.project.module.Modules;

@Mixin(FishingHookRenderer.class)
public class FishingBobberEntityRendererMixin {
	/** Clear View: spławik cudzej wędki zaczepiony o ciebie nie zasłania ekranu. */
	@Inject(
		//? if <26.3 {
		method = "shouldRender(Lnet/minecraft/world/entity/projectile/FishingHook;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z",
		//?} else {
		/*method = "shouldRender(Lnet/minecraft/world/entity/projectile/FishingHook;Lnet/minecraft/client/renderer/culling/Frustum;DDDF)Z",
		*///?}
		at = @At("HEAD"),
		cancellable = true
	)
	//? if <26.3 {
	private void aio$hideHookedOnMe(FishingHook bobber, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
	//?} else {
	/*private void aio$hideHookedOnMe(FishingHook bobber, Frustum frustum, double x, double y, double z, float tickProgress, CallbackInfoReturnable<Boolean> cir) {
	*///?}
		Minecraft client = Minecraft.getInstance();
		if (Modules.CLEAR_VIEW.on() && Modules.CLEAR_VIEW.bobber.on() && client.player != null && bobber.getHookedIn() == client.player) {
			cir.setReturnValue(false);
		}
	}
}
