package pl.kerf.project.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import pl.kerf.project.module.Modules;
import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.lwjgl.glfw.GLFW;
import pl.kerf.project.gui.ModuleKeys;
import pl.kerf.project.hud.CpsTracker;

/** Licznik CPS i bindy na myszce: tylko odczyt kliknięć, nic nie zmienia w obsłudze myszy. */
@Mixin(Mouse.class)
public class MouseMixin {
	@Inject(method = "onMouseButton", at = @At("HEAD"))
	private void aio$countClick(long window, MouseInput input, int action, CallbackInfo ci) {
		if (action == GLFW.GLFW_PRESS && MinecraftClient.getInstance().currentScreen == null) {
			CpsTracker.click(input.button());
		}

		// Stan przycisków dla bindów i skrótów na myszce (np. boczne przyciski).
		ModuleKeys.mouseButton(input.button(), action == GLFW.GLFW_PRESS);
	}

	/** Freelook: kółko myszy oddala i przybliża kamerę zamiast zmieniać slot w hotbarze. */
	@Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
	private void aio$freelookZoom(long window, double horizontal, double vertical, CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (window == client.getWindow().getHandle() && client.currentScreen == null && Modules.FREELOOK.scroll(client, vertical)) {
			ci.cancel();
		}
	}

	/** Freelook: przy trzymanym klawiszu ruch myszy obraca kamerę, a postać zostaje skierowana tak jak była. */
	@WrapOperation(method = "updateMouse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V"))
	private void aio$freelook(ClientPlayerEntity player, double dx, double dy, Operation<Void> original) {
		if (Modules.FREELOOK.active()) {
			Modules.FREELOOK.turn(dx, dy);
		} else {
			original.call(player, dx, dy);
		}
	}
}
