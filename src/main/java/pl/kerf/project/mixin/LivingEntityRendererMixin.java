package pl.kerf.project.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
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
		method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
		at = @At("TAIL")
	)
	private void aio$copyExtraState(LivingEntity entity, LivingEntityRenderState state, float tickProgress, CallbackInfo ci) {
		float hurtProgress = 0.0F;
		if (entity.deathTime > 0) {
			hurtProgress = 1.0F;
		} else if (entity.hurtTime > 0) {
			int max = entity.maxHurtTime > 0 ? entity.maxHurtTime : 10;
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
		method = "hasLabel(Lnet/minecraft/entity/LivingEntity;D)Z",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;getCameraEntity()Lnet/minecraft/entity/Entity;")
	)
	private Entity aio$ownName(Entity camera, LivingEntity entity, double distance) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (Modules.OWN_NAME.on() && entity == client.player && !client.options.getPerspective().isFirstPerson()) {
			return null;
		}

		return camera;
	}

	/** Tryb "zanikanie": zamiast stałego wiersza tekstury wybiera wiersz o kryciu zależnym od postępu efektu. */
	@Inject(method = "getOverlay", at = @At("RETURN"), cancellable = true)
	private static void aio$fadeHitColor(LivingEntityRenderState state, float whiteOverlayProgress, CallbackInfoReturnable<Integer> cir) {
		// Hit Color koloruje tylko uderzenia: przy obrażeniach bez sprawcy (ogień, lawa, upadek) encja nie świeci się na czerwono.
		if (state.hurt && Modules.HIT_COLOR.on() && !((LivingStateExt) state).aio$attackHit()) {
			cir.setReturnValue(OverlayTexture.packUv(cir.getReturnValue() & 0xFFFF, 10));
			return;
		}

		if (state.hurt && HitColorTexture.fading()) {
			int u = cir.getReturnValue() & 0xFFFF;
			cir.setReturnValue(OverlayTexture.packUv(u, HitColorTexture.fadeRow(((LivingStateExt) state).aio$hurtProgress())));
		}
	}
}
