package pl.kerf.project.mixin;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import pl.kerf.project.render.LivingStateExt;

/** Dokleja do stanu renderowania dane, których vanilla tam nie kopiuje (zdrowie, postęp efektu trafienia). */
@Mixin(LivingEntityRenderState.class)
public class LivingEntityRenderStateMixin implements LivingStateExt {
	@Unique
	private float aio$hurtProgress;
	@Unique
	private float aio$health;
	@Unique
	private float aio$maxHealth;
	@Unique
	private float aio$absorption;

	@Override
	public float aio$hurtProgress() {
		return this.aio$hurtProgress;
	}

	@Override
	public float aio$health() {
		return this.aio$health;
	}

	@Override
	public float aio$maxHealth() {
		return this.aio$maxHealth;
	}

	@Override
	public float aio$absorption() {
		return this.aio$absorption;
	}

	@Override
	public void aio$set(float hurtProgress, float health, float maxHealth, float absorption) {
		this.aio$hurtProgress = hurtProgress;
		this.aio$health = health;
		this.aio$maxHealth = maxHealth;
		this.aio$absorption = absorption;
	}
}
