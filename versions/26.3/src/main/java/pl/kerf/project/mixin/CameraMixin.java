package pl.kerf.project.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import pl.kerf.project.module.Modules;

@Mixin(Camera.class)
public class CameraMixin {
	/**
	 * Freelook: kamera bierze kąt z Freelooka zamiast z postaci. Odsunięcie kamery w trzeciej osobie liczy się
	 * zaraz potem z tego kąta, więc kamera krąży wokół gracza i nie wchodzi w ściany (vanilla clipToSpace).
	 * Tylko dwa pierwsze wywołania (zwykłe i w wagoniku); trzecie to odwrócenie dla widoku z przodu, które vanilla
	 * liczy od już ustawionego kąta, więc widok z przodu też działa.
	 */
	@WrapOperation(method = "alignWithEntity", at = {
		@At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V", ordinal = 0),
		@At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V", ordinal = 1)
	})
	private void aio$freelookRotation(Camera camera, float yaw, float pitch, Operation<Void> original) {
		if (Modules.FREELOOK.active()) {
			original.call(camera, Modules.FREELOOK.yaw(), Modules.FREELOOK.pitch());
		} else {
			original.call(camera, yaw, pitch);
		}
	}

	/** Freelook: odległość kamery z kółka myszy; clipToSpace dalej skraca ją przy ścianach, jak w vanilli. */
	@ModifyArg(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"))
	private float aio$freelookDistance(float distance) {
		return Modules.FREELOOK.active() ? distance * Modules.FREELOOK.distanceScale() : distance;
	}
}
