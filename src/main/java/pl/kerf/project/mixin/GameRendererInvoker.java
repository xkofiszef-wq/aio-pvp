//? if <26.1.2 {
package pl.kerf.project.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Markers: prawdziwy kąt widzenia w tej klatce (razem z zoomem z innych modów), żeby znaczniki trzymały się świata. */
@Mixin(GameRenderer.class)
public interface GameRendererInvoker {
	@Invoker("getFov")
	float aio$getFov(Camera camera, float tickProgress, boolean changingFov);
}
//?}
