package pl.kerf.project.mixin;

import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.kerf.project.hud.LagDetectorHud;

@Mixin(Connection.class)
public class ClientConnectionMixin {
	/**
	 * Lag Detector: znacznik czasu każdego pakietu od serwera. Tylko odczyt, pakiet idzie dalej bez zmian.
	 * W singleplayerze w tej samej grze żyje też połączenie po stronie serwera (odbiera pakiety OD klienta), stąd filtr.
	 */
	@Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"))
	private void aio$packetTime(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
		if (((Connection) (Object) this).getReceiving() == PacketFlow.CLIENTBOUND) {
			LagDetectorHud.packetReceived();
		}
	}
}
