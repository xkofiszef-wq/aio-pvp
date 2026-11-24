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
			// Obrażenia ze sprawcą (gracz, mob, pocisk) to uderzenie; ogień, lawa i upadek nie mają sprawcy (id -1).
			if (target != null && (packet.sourceCauseId() >= 0 || packet.sourceDirectId() >= 0)) {
				pl.kerf.project.render.HitTracker.mark(target);
			}
		}
	}
}
