package pl.kerf.project.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.kerf.project.module.Modules;

@Mixin(ParticleEngine.class)
public class ParticleManagerMixin {
	@Inject(
		method = "createParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)Lnet/minecraft/client/particle/Particle;", at = @At("HEAD"), cancellable = true
	)
	private void aio$hideParticles(
		ParticleOptions parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ, CallbackInfoReturnable<Particle> cir
	) {
		ParticleType<?> type = parameters.getType();
		if (Modules.NO_EXPLOSION_PARTICLES.on() && (type == ParticleTypes.EXPLOSION || type == ParticleTypes.EXPLOSION_EMITTER)) {
			cir.setReturnValue(null);
			return;
		}

		Modules.PotionParticles potions = Modules.NO_POTION_PARTICLES;
		if (!potions.on()) {
			return;
		}

		if (type == ParticleTypes.ENTITY_EFFECT) {
			if (!potions.ownOnly.on() || aio$nearOwnPlayer(x, y, z)) {
				cir.setReturnValue(null);
			}
		} else if (potions.splash.on() && (type == ParticleTypes.EFFECT || type == ParticleTypes.INSTANT_EFFECT)) {
			cir.setReturnValue(null);
		}
	}

	/** Cząsteczki efektów powstają wewnątrz hitboxa encji, więc "własne" to te w hitboxie gracza. */
	private static boolean aio$nearOwnPlayer(double x, double y, double z) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return false;
		}

		// Bez tworzenia nowego Box przy każdej cząsteczce (to samo co getBoundingBox().expand(0.3).contains).
		net.minecraft.world.phys.AABB box = player.getBoundingBox();
		return x >= box.minX - 0.3 && x < box.maxX + 0.3 && y >= box.minY - 0.3 && y < box.maxY + 0.3 && z >= box.minZ - 0.3 && z < box.maxZ + 0.3;
	}
}
