package pl.kerf.project.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.kerf.project.module.Modules;

@Mixin(LocalPlayer.class)
public class ClientPlayerEntityMixin {
	//? if <26.3 {
	/**
	 * Cooldown Fix (MC-218570): serwer po każdym pakiecie machnięcia ręką zeruje ładowanie ataku
	 * (ServerPlayerEntity.swingHand), a klient tylko przy ataku. Np. po zniszczeniu bloku wskaźnik pokazywał pełne
	 * naładowanie, a cios był słaby. Klient robi teraz to samo co serwer, więc wskaźnik pokazuje prawdę.
	 */
	@Inject(method = "swing", at = @At("TAIL"))
	private void aio$syncCooldown(InteractionHand hand, CallbackInfo ci) {
		if (Modules.COOLDOWN_FIX.on()) {
	//?}
			// Tylko ładowanie ataku: pełne resetTicksSince zerowałoby też licznik wyciągania ręki, więc ręka chowała się przy każdym machnięciu (np. kopaniu).
			//? if <26.3 {
			((Player) (Object) this).resetOnlyAttackStrengthTicker();
		}
	}

			//?}
	/**
	 * MC-263293 / MC-304861, wbudowane na stałe (bez modułu): respawn odznaczał Toggle Sprint i Toggle Sneak.
	 * Z ekranem śmierci vanilla przywraca je po jego zamknięciu, ale przy natychmiastowym respawnie (bez ekranu) przepadały.
	 * Pozostałe klawisze przełączane (np. Toggle Attack/Use) dalej resetują się jak w vanilla.
	 */
	@WrapOperation(method = "respawn", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;resetToggleKeys()V"))
	private void aio$keepMovementToggles(Operation<Void> original) {
		Options options = Minecraft.getInstance().options;
		boolean sprint = options.toggleSprint().get() && options.keySprint.isDown();
		boolean sneak = options.toggleCrouch().get() && options.keyShift.isDown();
		original.call();
		// Klawisz przełączany: setPressed(true) odwraca stan, a po untoggle jest false, więc wraca na true.
		if (sprint) {
			options.keySprint.setDown(true);
		}

		if (sneak) {
			options.keyShift.setDown(true);
		}
	}

	/** No Sign GUI: okno edycji tabliczki się nie otwiera (tabliczka zostaje pusta). */
	@Inject(method = "openTextEdit", at = @At("HEAD"), cancellable = true)
	//? if <26.3 {
	private void aio$noSignGui(SignBlockEntity sign, boolean front, CallbackInfo ci) {
	//?} else {
	/*private void aio$noSignGui(SignBlockEntity sign, net.minecraft.world.level.block.entity.SignTextSlot slot, CallbackInfo ci) {
	*///?}
		if (Modules.NO_SIGN_GUI.on()) {
			ci.cancel();
		}
	}
}
