package pl.kerf.project.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.FishingBobberEntityRenderer;
import net.minecraft.entity.projectile.FishingBobberEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.kerf.project.module.Modules;

@Mixin(FishingBobberEntityRenderer.class)
public class FishingBobberEntityRendererMixin {
	/** Clear View: spławik cudzej wędki zaczepiony o ciebie nie zasłania ekranu. */
	@Inject(
		method = "shouldRender(Lnet/minecraft/entity/projectile/FishingBobberEntity;Lnet/minecraft/client/render/Frustum;DDD)Z",
		at = @At("HEAD"),
		cancellable = true
	)
	private void aio$hideHookedOnMe(FishingBobberEntity bobber, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (Modules.CLEAR_VIEW.on() && Modules.CLEAR_VIEW.bobber.on() && client.player != null && bobber.getHookedEntity() == client.player) {
			cir.setReturnValue(false);
		}
	}
}
