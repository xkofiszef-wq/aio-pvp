package pl.kerf.project.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import pl.kerf.project.module.Modules;

@Mixin(ChatComponent.class)
public class ChatHudMixin {
	/** Vanilla ucina historię czatu i wpisanych wiadomości do 100 pozycji. */
	@ModifyExpressionValue(
		method = {"addMessageToDisplayQueue", "addMessage", "addRecentChat"}, at = @At(value = "CONSTANT", args = "intValue=100")
	)
	private int aio$historySize(int limit) {
		return Modules.CHAT_HISTORY.on() ? Math.max(limit, Modules.CHAT_HISTORY.lines.i()) : limit;
	}
}
