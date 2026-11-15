package pl.kerf.project.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.kerf.project.module.Modules;

@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMixin {
	/**
	 * Cooldown Fix (MC-218570): serwer po każdym pakiecie machnięcia ręką zeruje ładowanie ataku
	 * (ServerPlayerEntity.swingHand), a klient tylko przy ataku. Np. po zniszczeniu bloku wskaźnik pokazywał pełne
	 * naładowanie, a cios był słaby. Klient robi teraz to samo co serwer, więc wskaźnik pokazuje prawdę.
	 */
	@Inject(method = "swingHand", at = @At("TAIL"))
	private void aio$syncCooldown(Hand hand, CallbackInfo ci) {
		if (Modules.COOLDOWN_FIX.on()) {
			((PlayerEntity) (Object) this).resetTicksSince();
		}
	}

	/**
	 * MC-263293 / MC-304861, wbudowane na stałe (bez modułu): respawn odznaczał Toggle Sprint i Toggle Sneak.
	 * Z ekranem śmierci vanilla przywraca je po jego zamknięciu, ale przy natychmiastowym respawnie (bez ekranu) przepadały.
	 * Pozostałe klawisze przełączane (np. Toggle Attack/Use) dalej resetują się jak w vanilla.
	 */
	@WrapOperation(method = "requestRespawn", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/KeyBinding;untoggleStickyKeys()V"))
	private void aio$keepMovementToggles(Operation<Void> original) {
		GameOptions options = MinecraftClient.getInstance().options;
		boolean sprint = options.getSprintToggled().getValue() && options.sprintKey.isPressed();
		boolean sneak = options.getSneakToggled().getValue() && options.sneakKey.isPressed();
		original.call();
		// Klawisz przełączany: setPressed(true) odwraca stan, a po untoggle jest false, więc wraca na true.
		if (sprint) {
			options.sprintKey.setPressed(true);
		}

		if (sneak) {
			options.sneakKey.setPressed(true);
		}
	}

	/** No Sign GUI: okno edycji tabliczki się nie otwiera (tabliczka zostaje pusta). */
	@Inject(method = "openEditSignScreen", at = @At("HEAD"), cancellable = true)
	private void aio$noSignGui(SignBlockEntity sign, boolean front, CallbackInfo ci) {
		if (Modules.NO_SIGN_GUI.on()) {
			ci.cancel();
		}
	}
}
