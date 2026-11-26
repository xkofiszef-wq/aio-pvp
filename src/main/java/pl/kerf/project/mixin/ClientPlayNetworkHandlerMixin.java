package pl.kerf.project.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.kerf.project.module.FightTracker;

@Mixin(ClientPacketListener.class)
public class ClientPlayNetworkHandlerMixin {
	/**
	 * Fight Report: zużyty totem przeciwnika (status 35). TAIL wykonuje się tylko w wątku gry: wywołanie z wątku
	 * sieci kończy się wcześniej wyjątkiem przerzucającym pakiet do wątku gry.
	 */
	@Inject(method = "handleEntityEvent", at = @At("TAIL"))
	private void aio$totemPop(ClientboundEntityEventPacket packet, CallbackInfo ci) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return;
		}

		if (packet.getEventId() == 35) {
			FightTracker.totemPop(packet.getEntity(client.level));
		} else if (packet.getEventId() == 3) {
			// Status 3 = śmierć (serwer wysyła go zawsze, nawet gdy nie podaje prawdziwego HP innych graczy).
			FightTracker.died(packet.getEntity(client.level));
		}
	}

	/** Fight Report: serwer potwierdził obrażenia encji, czyli cios naprawdę wszedł (TAIL = już w wątku gry). */
	@Inject(method = "handleDamageEvent", at = @At("TAIL"))
	private void aio$confirmedHit(ClientboundDamageEventPacket packet, CallbackInfo ci) {
		Minecraft client = Minecraft.getInstance();
		if (client.level != null) {
			net.minecraft.world.entity.Entity target = client.level.getEntity(packet.entityId());
			FightTracker.damaged(target);
			pl.kerf.project.hud.ReachHud.damaged(target);
			// Obrażenia ze sprawcą (gracz, mob, pocisk) to uderzenie; ogień, lawa i upadek nie mają sprawcy (id -1).
			if (target != null && (packet.sourceCauseId() >= 0 || packet.sourceDirectId() >= 0) && !aio$fireIgnored(target, packet)) {
				pl.kerf.project.render.HitTracker.mark(target);
			}
		}
	}

	/** Płonący gracz z odpornością na ogień nie dostaje obrażeń, więc palenie się nie jest uderzeniem (niektóre serwery przypisują je podpalającemu). */
	private static boolean aio$fireIgnored(net.minecraft.world.entity.Entity target, ClientboundDamageEventPacket packet) {
		if (!(target instanceof net.minecraft.world.entity.LivingEntity living) || !living.hasEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE)) {
			return false;
		}

		var type = packet.sourceType();
		return type.is(net.minecraft.world.damagesource.DamageTypes.IN_FIRE) || type.is(net.minecraft.world.damagesource.DamageTypes.ON_FIRE)
			|| type.is(net.minecraft.world.damagesource.DamageTypes.LAVA) || type.is(net.minecraft.world.damagesource.DamageTypes.HOT_FLOOR);
	}
}
