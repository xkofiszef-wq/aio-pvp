package pl.kerf.project.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.fog.FogData;
import net.minecraft.client.render.fog.LavaFogModifier;
import net.minecraft.client.render.fog.WaterFogModifier;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.kerf.project.module.Modules;

@Mixin({WaterFogModifier.class, LavaFogModifier.class})
public class LiquidFogMixin {
	/** Clear View: w wodzie/lawie mgła dopiero na granicy zasięgu renderowania, jak na lądzie. */
	@Inject(method = "applyStartEndModifier", at = @At("TAIL"))
	private void aio$noLiquidFog(FogData data, Camera camera, ClientWorld world, float viewDistance, RenderTickCounter tickCounter, CallbackInfo ci) {
		boolean lava = (Object) this instanceof LavaFogModifier;
		if (Modules.CLEAR_VIEW.on() && (lava ? Modules.CLEAR_VIEW.lavaFog : Modules.CLEAR_VIEW.waterFog).on()) {
			data.environmentalStart = viewDistance;
			data.environmentalEnd = viewDistance;
			data.skyEnd = viewDistance;
			data.cloudEnd = viewDistance;
		}
	}
}
