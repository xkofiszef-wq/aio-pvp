package pl.kerf.project.render;

/** Dodatkowe dane doklejane mixinem do LivingEntityRenderState. */
public interface LivingStateExt {
	/** 1 = świeże trafienie, 0 = brak efektu. */
	float aio$hurtProgress();

	float aio$health();

	float aio$maxHealth();

	float aio$absorption();

	void aio$set(float hurtProgress, float health, float maxHealth, float absorption);
}
