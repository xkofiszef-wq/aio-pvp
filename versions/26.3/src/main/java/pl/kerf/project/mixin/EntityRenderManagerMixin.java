package pl.kerf.project.mixin;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.kerf.project.module.Modules;
import pl.kerf.project.render.EntityCulling;

/** Entity Culling: encje schowane za pełnymi blokami nie są w ogóle rysowane. */
@Mixin(EntityRenderDispatcher.class)
public class EntityRenderManagerMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private <E extends Entity> void aio$cull(E entity, Frustum frustum, double x, double y, double z, float partialTicks, CallbackInfoReturnable<Boolean> cir) {
		if (Modules.OPTIMIZATION.active(Modules.OPTIMIZATION.entityCulling) && EntityCulling.hidden(entity)) {
			cir.setReturnValue(false);
		}
	}
}
