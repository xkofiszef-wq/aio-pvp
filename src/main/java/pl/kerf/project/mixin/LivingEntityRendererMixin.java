package pl.kerf.project.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.kerf.project.module.Modules;
import pl.kerf.project.render.HitColorTexture;
import pl.kerf.project.render.HitTracker;
import pl.kerf.project.render.LivingStateExt;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
		at = @At("TAIL")
	)
	private void aio$copyExtraState(LivingEntity entity, LivingEntityRenderState state, float tickProgress, CallbackInfo ci) {
		float hurtProgress = 0.0F;
		if (entity.deathTime > 0) {
			hurtProgress = 1.0F;
		} else if (entity.hurtTime > 0) {
			int max = entity.hurtDuration > 0 ? entity.hurtDuration : 10;
			hurtProgress = Math.max(0.0F, Math.min(1.0F, (entity.hurtTime - tickProgress) / max));
		}

		((LivingStateExt) state).aio$set(hurtProgress, entity.getHealth(), entity.getMaxHealth(), entity.getAbsorptionAmount());
		((LivingStateExt) state).aio$setAttackHit(HitTracker.recent(entity));
	}

	/**
	 * Name in 3rd Person: vanilla nie pokazuje nicku encji, z której patrzy kamera. Z funkcją, w trzeciej osobie,
	 * własny gracz nie jest traktowany jak kamera, więc nick się pojawia (reszta warunków vanilla zostaje).
	 */
	@ModifyExpressionValue(
		method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getCameraEntity()Lnet/minecraft/world/entity/Entity;")
	)
	private Entity aio$ownName(Entity camera, LivingEntity entity, double distance) {
		Minecraft client = Minecraft.getInstance();
		if (Modules.OWN_NAME.on() && entity == client.player && !client.options.getCameraType().isFirstPerson()) {
			return null;
		}

		return camera;
	}

	/** Tryb "zanikanie": zamiast stałego wiersza tekstury wybiera wiersz o kryciu zależnym od postępu efektu. */
	@Inject(method = "getOverlayCoords", at = @At("RETURN"), cancellable = true)
	private static void aio$fadeHitColor(LivingEntityRenderState state, float whiteOverlayProgress, CallbackInfoReturnable<Integer> cir) {
		// Hit Color koloruje tylko uderzenia: przy obrażeniach bez sprawcy (ogień, lawa, upadek) encja nie świeci się na czerwono.
		if (state.hasRedOverlay && Modules.HIT_COLOR.on() && !((LivingStateExt) state).aio$attackHit()) {
			cir.setReturnValue(OverlayTexture.pack(cir.getReturnValue() & 0xFFFF, 10));
			return;
		}

		if (state.hasRedOverlay && HitColorTexture.fading()) {
			int u = cir.getReturnValue() & 0xFFFF;
			cir.setReturnValue(OverlayTexture.pack(u, HitColorTexture.fadeRow(((LivingStateExt) state).aio$hurtProgress())));
		}
	}
}
