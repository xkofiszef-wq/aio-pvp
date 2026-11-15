package pl.kerf.project.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.kerf.project.module.Modules;

@Mixin(ParticleManager.class)
public class ParticleManagerMixin {
	@Inject(
		method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;", at = @At("HEAD"), cancellable = true
	)
	private void aio$hideParticles(
		ParticleEffect parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ, CallbackInfoReturnable<Particle> cir
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
		ClientPlayerEntity player = MinecraftClient.getInstance().player;
		if (player == null) {
			return false;
		}

		// Bez tworzenia nowego Box przy każdej cząsteczce (to samo co getBoundingBox().expand(0.3).contains).
		net.minecraft.util.math.Box box = player.getBoundingBox();
		return x >= box.minX - 0.3 && x < box.maxX + 0.3 && y >= box.minY - 0.3 && y < box.maxY + 0.3 && z >= box.minZ - 0.3 && z < box.maxZ + 0.3;
	}
}
