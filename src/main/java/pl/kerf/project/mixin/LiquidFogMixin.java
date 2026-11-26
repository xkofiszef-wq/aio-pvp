package pl.kerf.project.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.LavaFogEnvironment;
import net.minecraft.client.renderer.fog.environment.WaterFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.kerf.project.module.Modules;

@Mixin({WaterFogEnvironment.class, LavaFogEnvironment.class})
public class LiquidFogMixin {
	/** Clear View: w wodzie/lawie mgła dopiero na granicy zasięgu renderowania, jak na lądzie. */
	@Inject(method = "setupFog", at = @At("TAIL"))
	private void aio$noLiquidFog(FogData data, Camera camera, ClientLevel world, float viewDistance, DeltaTracker tickCounter, CallbackInfo ci) {
		boolean lava = (Object) this instanceof LavaFogEnvironment;
		if (Modules.CLEAR_VIEW.on() && (lava ? Modules.CLEAR_VIEW.lavaFog : Modules.CLEAR_VIEW.waterFog).on()) {
			data.environmentalStart = viewDistance;
			data.environmentalEnd = viewDistance;
			data.skyEnd = viewDistance;
			data.cloudEnd = viewDistance;
		}
	}
}
