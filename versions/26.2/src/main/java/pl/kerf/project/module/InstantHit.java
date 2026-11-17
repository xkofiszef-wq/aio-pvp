package pl.kerf.project.module;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Instant Hit: czerwony błysk trafienia widać od razu po ciosie, zanim serwer go potwierdzi (jak Better HitReg).
 * Tylko wygląd: obrażenia liczy serwer jak zawsze, a Fight Report patrzy na potwierdzenie z serwera, nie na błysk.
 * Zabroniony na części serwerów, więc działa jak każdy {@link RiskyModule} (ostrzeżenie, blokada do restartu).
 */
public final class InstantHit extends RiskyModule {
	public InstantHit() {
		super("instant_hit", "Instant Hit", "Trafienie widać od razu, bez czekania na serwer. Na części serwerów zabronione: blokuje się do restartu gry.",
			Category.COMBAT, "Ten moduł pokazuje trafienia, zanim potwierdzi je serwer.");
	}

	/**
	 * Nasz cios: od razu czerwony błysk na celu, jeśli cel jest w zasięgu, żyje i nie ma właśnie nietykalności po
	 * poprzednim ciosie (wtedy serwer i tak by go nie zranił). Gdy serwer potwierdzi, gra pokaże błysk jeszcze raz.
	 */
	public void onAttack(Minecraft client, Entity entity) {
		if (!this.on() || client.player == null || !(entity instanceof LivingEntity living)) {
			return;
		}

		if (living.isAlive() && living.hurtTime == 0 && client.player.isWithinEntityInteractionRange(living, 0.0)) {
			living.hurtTime = living.hurtDuration > 0 ? living.hurtDuration : 10;
		}
	}
}
