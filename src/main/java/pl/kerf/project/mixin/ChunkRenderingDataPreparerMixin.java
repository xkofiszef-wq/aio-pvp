package pl.kerf.project.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.render.ChunkRenderingDataPreparer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import pl.kerf.project.module.Modules;
import pl.kerf.project.render.FogOcclusion;

/** Fog Occlusion: fragment za końcem mgły nie jest brany pod uwagę przy budowie listy widocznych fragmentów. */
@Mixin(ChunkRenderingDataPreparer.class)
public class ChunkRenderingDataPreparerMixin {
	@ModifyReturnValue(method = "isWithinViewDistance(JJ)Z", at = @At("RETURN"))
	private boolean aio$fogOcclusion(boolean within, long centerSection, long otherSection) {
		return within && !(Modules.OPTIMIZATION.active(Modules.OPTIMIZATION.fogOcclusion) && FogOcclusion.beyond(centerSection, otherSection));
	}
}
