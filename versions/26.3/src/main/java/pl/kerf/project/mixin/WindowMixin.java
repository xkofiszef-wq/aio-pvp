package pl.kerf.project.mixin;

import com.mojang.renderpearl.backend.opengl.GlBackend;
import org.lwjgl.sdl.SDLVideo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.kerf.project.module.Optimization;

/**
 * No Error Context: prosi o kontekst OpenGL bez sprawdzania błędów tuż przed utworzeniem okna (sterownik może to zignorować).
 * W tej wersji okno tworzy SDL, więc ustawiamy atrybut SDL_GL_CONTEXT_NO_ERROR (25).
 */
@Mixin(GlBackend.class)
public class WindowMixin {
	@Inject(method = "createWindow", at = @At("HEAD"))
	private void aio$noErrorContext(String title, int width, int height, long flags, CallbackInfoReturnable<Long> cir) {
		if (Optimization.noErrorContextRequested()) {
			SDLVideo.SDL_GL_SetAttribute(25, 1);
		}
	}
}
