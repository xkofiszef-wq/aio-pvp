package pl.kerf.project.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import net.minecraft.util.debug.DebugValueAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.kerf.project.module.Modules;
import pl.kerf.project.render.WorldOverlays;

/** Hitboxy F3+B: gdy moduł jest włączony, rysuje własne (kolory, cel) zamiast vanillowych. */
@Mixin(EntityHitboxDebugRenderer.class)
public class EntityHitboxDebugRendererMixin {
	@Inject(method = "emitGizmos", at = @At("HEAD"), cancellable = true)
	private void aio$customHitboxes(double cameraX, double cameraY, double cameraZ, DebugValueAccess store, Frustum frustum, float tickProgress, CallbackInfo ci) {
		if (Modules.HITBOXES.on()) {
			WorldOverlays.drawHitboxes(Minecraft.getInstance(), tickProgress);
			ci.cancel();
		}
	}
}
