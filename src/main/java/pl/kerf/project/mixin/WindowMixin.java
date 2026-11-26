package pl.kerf.project.mixin;

//? if <26.3 {
import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
//?} else {
/*import com.mojang.renderpearl.backend.opengl.GlBackend;
import org.lwjgl.sdl.SDLVideo;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
//? if <26.1.2 {
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?} else {
/*import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?}
import pl.kerf.project.module.Optimization;

//? if <26.3 {
/** No Error Context: prosi o kontekst OpenGL bez sprawdzania błędów tuż przed utworzeniem okna (sterownik może to zignorować). */
@Mixin(Window.class)
//?} else {
/*/^*
 * No Error Context: prosi o kontekst OpenGL bez sprawdzania błędów tuż przed utworzeniem okna (sterownik może to zignorować).
 * W tej wersji okno tworzy SDL, więc ustawiamy atrybut SDL_GL_CONTEXT_NO_ERROR (25).
 ^/
@Mixin(GlBackend.class)
*///?}
public class WindowMixin {
	//? if <26.1.2 {
	@Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J", remap = false))
	private void aio$noErrorContext(CallbackInfo ci) {
	//?}
	//? if >=26.1.2 <26.3 {
	/*@Inject(method = "createGlfwWindow", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J", remap = false))
	private static void aio$noErrorContext(CallbackInfoReturnable<Long> cir) {
	*///?}
	//? if >=26.3 {
	/*@Inject(method = "createWindow", at = @At("HEAD"))
	private void aio$noErrorContext(String title, int width, int height, long flags, CallbackInfoReturnable<Long> cir) {
	*///?}
		if (Optimization.noErrorContextRequested()) {
			//? if <26.3 {
			GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_NO_ERROR, GLFW.GLFW_TRUE);
			//?} else {
			/*SDLVideo.SDL_GL_SetAttribute(25, 1);
			*///?}
		}
	}
}
