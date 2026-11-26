package pl.kerf.project.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import pl.kerf.project.module.Modules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if <26.3 {
import org.lwjgl.glfw.GLFW;
//?} else {
/*import com.mojang.blaze3d.platform.InputConstants;
*///?}
import pl.kerf.project.gui.ModuleKeys;
import pl.kerf.project.hud.CpsTracker;

/** Licznik CPS i bindy na myszce: tylko odczyt kliknięć, nic nie zmienia w obsłudze myszy. */
@Mixin(MouseHandler.class)
public class MouseMixin {
	@Inject(method = "onButton", at = @At("HEAD"))
	private void aio$countClick(long window, MouseButtonInfo input, int action, CallbackInfo ci) {
		//? if <26.2 {
		if (action == GLFW.GLFW_PRESS && Minecraft.getInstance().screen == null) {
		//?}
		//? if =26.2 {
		/*if (action == GLFW.GLFW_PRESS && Minecraft.getInstance().gui.screen() == null) {
		*///?}
			//? if <26.3 {
			CpsTracker.click(input.button());
			//?} else {
		/*if (action == InputConstants.PRESS && Minecraft.getInstance().gui.screen() == null) {
			CpsTracker.click(ModuleKeys.button(input.button()));
			*///?}
		}

		// Stan przycisków dla bindów i skrótów na myszce (np. boczne przyciski).
		//? if <26.3 {
		ModuleKeys.mouseButton(input.button(), action == GLFW.GLFW_PRESS);
		//?} else {
		/*ModuleKeys.mouseButton(ModuleKeys.button(input.button()), action == InputConstants.PRESS);
		*///?}
	}

	/** Freelook: kółko myszy oddala i przybliża kamerę zamiast zmieniać slot w hotbarze. */
	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void aio$freelookZoom(long window, double horizontal, double vertical, CallbackInfo ci) {
		Minecraft client = Minecraft.getInstance();
		//? if <26.2 {
		if (window == client.getWindow().handle() && client.screen == null && Modules.FREELOOK.scroll(client, vertical)) {
		//?} else {
		/*if (window == client.getWindow().handle() && client.gui.screen() == null && Modules.FREELOOK.scroll(client, vertical)) {
		*///?}
			ci.cancel();
		}
	}

	/** Freelook: przy trzymanym klawiszu ruch myszy obraca kamerę, a postać zostaje skierowana tak jak była. */
	@WrapOperation(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
	private void aio$freelook(LocalPlayer player, double dx, double dy, Operation<Void> original) {
		if (Modules.FREELOOK.active()) {
			Modules.FREELOOK.turn(dx, dy);
		} else {
			original.call(player, dx, dy);
		}
	}
}
