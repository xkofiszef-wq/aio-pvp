package pl.kerf.project.mixin;

import net.minecraft.client.MinecraftClient;
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
}
