package pl.kerf.project.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.kerf.project.module.FightTracker;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {
	/**
	 * Fight Report: zużyty totem przeciwnika (status 35). TAIL wykonuje się tylko w wątku gry: wywołanie z wątku
	 * sieci kończy się wcześniej wyjątkiem przerzucającym pakiet do wątku gry.
	 */
	@Inject(method = "onEntityStatus", at = @At("TAIL"))
	private void aio$totemPop(EntityStatusS2CPacket packet, CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) {
			return;
		}

		if (packet.getStatus() == 35) {
			FightTracker.totemPop(packet.getEntity(client.world));
		} else if (packet.getStatus() == 3) {
			// Status 3 = śmierć (serwer wysyła go zawsze, nawet gdy nie podaje prawdziwego HP innych graczy).
			FightTracker.died(packet.getEntity(client.world));
		}
	}

	/** Fight Report: serwer potwierdził obrażenia encji, czyli cios naprawdę wszedł (TAIL = już w wątku gry). */
	@Inject(method = "onEntityDamage", at = @At("TAIL"))
	private void aio$confirmedHit(EntityDamageS2CPacket packet, CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world != null) {
			FightTracker.damaged(client.world.getEntityById(packet.entityId()));
		}
	}
}
