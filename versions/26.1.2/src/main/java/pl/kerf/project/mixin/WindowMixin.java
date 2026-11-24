package pl.kerf.project.mixin;

import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.kerf.project.module.Optimization;

/** No Error Context: prosi o kontekst OpenGL bez sprawdzania błędów tuż przed utworzeniem okna (sterownik może to zignorować). */
@Mixin(Window.class)
public class WindowMixin {
	@Inject(method = "createGlfwWindow", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J", remap = false))
	private static void aio$noErrorContext(CallbackInfoReturnable<Long> cir) {
		if (Optimization.noErrorContextRequested()) {
			GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_NO_ERROR, GLFW.GLFW_TRUE);
		}
	}
}
