package pl.kerf.project.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import pl.kerf.project.module.Modules;

/** Sounds: mnożnik głośności pojedynczego dźwięku, nakładany na głośność liczoną przez Minecrafta. */
@Mixin(SoundSystem.class)
public class SoundSystemMixin {
	@ModifyReturnValue(method = "getAdjustedVolume(Lnet/minecraft/client/sound/SoundInstance;)F", at = @At("RETURN"))
	private float aio$soundVolume(float volume, SoundInstance sound) {
		return volume * Modules.SOUNDS.multiplier(sound.getId());
	}
}
