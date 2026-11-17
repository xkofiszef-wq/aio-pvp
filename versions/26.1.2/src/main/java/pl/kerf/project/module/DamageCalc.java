package pl.kerf.project.module;

import pl.kerf.project.config.Setting;

/**
 * Kalkulator obrażeń: ile zada jeden cios i ile ciosów zabije przeciwnika. Wzory jak w Minecraft 1.21 (cios w pełni
 * naładowany): broń + siła, krytyk x1.5 (bez ostrości), ostrość 0.5*poziom + 0.5, potem pancerz z wytrzymałością,
 * odporność (20% na poziom) i ochrona (4% na punkt, max 20 punktów).
 */
public final class DamageCalc extends Module {
	private static final String[] WEAPONS = {
		"Pięść", "Miecz drewniany", "Miecz kamienny", "Miecz żelazny", "Miecz złoty", "Miecz diamentowy", "Miecz netherytowy",
		"Siekiera diamentowa", "Siekiera netherytowa", "Maczuga (bez spadania)", "Trójząb"
	};
	/** Obrażenia broni z opisu przedmiotu (z bazowym 1 gracza) i szybkość ataku na sekundę. */
	private static final float[] WEAPON_DAMAGE = {1, 4, 5, 6, 4, 7, 8, 9, 10, 6, 9};
	private static final float[] WEAPON_SPEED = {4.0F, 1.6F, 1.6F, 1.6F, 1.6F, 1.6F, 1.6F, 1.0F, 1.0F, 0.6F, 1.1F};
	private static final String[] ARMORS = {"Brak", "Skórzana", "Kolczuga", "Złota", "Żelazna", "Diamentowa", "Netherytowa"};
	/** Punkty pancerza i wytrzymałości pełnego zestawu. */
	private static final float[] ARMOR_POINTS = {0, 7, 12, 11, 15, 20, 20};
	private static final float[] ARMOR_TOUGHNESS = {0, 0, 0, 0, 0, 8, 12};
	private static final float[] ABSORPTION = {0, 4, 16};

	public final Setting.Mode weapon = this.add(new Setting.Mode("weapon", "Twoja broń", 5, WEAPONS));
	public final Setting.Num sharpness = this.add(new Setting.Num("sharpness", "Ostrość", 5, 0, 500, 1));
	public final Setting.Mode strength = this.add(new Setting.Mode("strength", "Siła", 0, "Brak", "Siła I", "Siła II"));
	public final Setting.Bool crit = this.add(new Setting.Bool("crit", "Krytyk (cios w spadaniu)", false));
	public final Setting.Mode armor = this.add(new Setting.Mode("armor", "Zbroja przeciwnika", 5, ARMORS));
	public final Setting.Num protHead = this.add(new Setting.Num("prot_head", "Ochrona: hełm", 4, 0, 4, 1));
	public final Setting.Num protChest = this.add(new Setting.Num("prot_chest", "Ochrona: napierśnik", 4, 0, 4, 1));
	public final Setting.Num protLegs = this.add(new Setting.Num("prot_legs", "Ochrona: spodnie", 4, 0, 4, 1));
	public final Setting.Num protFeet = this.add(new Setting.Num("prot_feet", "Ochrona: buty", 4, 0, 4, 1));
	public final Setting.Mode resistance = this.add(new Setting.Mode("resistance", "Odporność przeciwnika", 0, "Brak", "Odporność I", "Odporność II"));
	public final Setting.Mode absorption = this.add(new Setting.Mode("absorption", "Absorpcja przeciwnika", 0, "Brak", "Złote jabłko (+2 serca)", "Zaklęte jabłko (+8 serc)"));
	public final Setting.Bool saturation = this.add(new Setting.Bool("saturation", "Przeciwnik z pełną saturacją (leczy się)", true));

	public DamageCalc() {
		super("damage_calc", "Damage Calculator", "Ile zadasz jednym ciosem i ile ciosów zabije: ostrość do 500, siła, krytyk, zbroja i ochrona przeciwnika.",
			Category.COMBAT, true);
	}

	/** Obrażenia jednego ciosu w punktach zdrowia (2 = jedno serce). Statyczne, żeby dało się je sprawdzić w teście. */
	public static float hit(int weapon, int sharpness, int strength, boolean crit, int armor, int protection, int resistance) {
		float attack = WEAPON_DAMAGE[weapon] + 3.0F * strength;
		if (crit) {
			attack *= 1.5F;
		}

		float damage = attack + (sharpness > 0 ? 0.5F * sharpness + 0.5F : 0.0F);
		float points = ARMOR_POINTS[armor];
		float toughness = ARMOR_TOUGHNESS[armor];
		float blocked = Math.max(points * 0.2F, Math.min(20.0F, points - damage / (2.0F + toughness / 4.0F)));
		damage *= 1.0F - blocked / 25.0F;
		damage *= 1.0F - 0.2F * resistance;
		damage *= 1.0F - Math.min(20, protection) / 25.0F;
		return damage;
	}

	/**
	 * Ile pełnych ciosów (co {@code interval} ticków) zabije przeciwnika z 20 HP, pełnym głodem i absorpcją. Leczenie
	 * jak w HungerManager 1.21, tick po ticku: z saturacją co 10 ticków min(sat, 6)/6 HP (zużywa saturację), bez niej
	 * przy głodzie >= 18 co 80 ticków 1 HP. Każde 4 punkty zmęczenia zjadają 1 saturacji, potem 1 głodu.
	 * -1 = nie da się zabić w 20 minut ciągłej walki.
	 */
	public static int hitsToKill(float hit, float absorption, float interval, boolean fullSaturation) {
		float health = 20.0F;
		int food = 20;
		float saturation = fullSaturation ? 20.0F : 0.0F;
		float exhaustion = 0.0F;
		int timer = 0;
		int hits = 0;
		double nextHit = 0.0;
		for (int tick = 0; tick < 24000; tick++) {
			if (tick >= nextHit) {
				hits++;
				nextHit += interval;
				float rest = hit - absorption;
				absorption = Math.max(0.0F, absorption - hit);
				health -= Math.max(0.0F, rest);
				if (health <= 0.0F) {
					return hits;
				}
			}

			if (health < 20.0F && saturation > 0.0F && food >= 20) {
				if (++timer >= 10) {
					float spent = Math.min(saturation, 6.0F);
					health = Math.min(20.0F, health + spent / 6.0F);
					exhaustion += spent;
					timer = 0;
				}
			} else if (health < 20.0F && food >= 18) {
				if (++timer >= 80) {
					health = Math.min(20.0F, health + 1.0F);
					exhaustion += 6.0F;
					timer = 0;
				}
			} else {
				timer = 0;
			}

			while (exhaustion >= 4.0F) {
				exhaustion -= 4.0F;
				if (saturation > 0.0F) {
					saturation = Math.max(0.0F, saturation - 1.0F);
				} else {
					food = Math.max(0, food - 1);
				}
			}
		}

		return -1;
	}

	public float hit() {
		int protection = this.protHead.i() + this.protChest.i() + this.protLegs.i() + this.protFeet.i();
		return hit(this.weapon.get(), this.sharpness.i(), this.strength.get(), this.crit.on(), this.armor.get(), protection, this.resistance.get());
	}

	/** Przerwa między pełnymi ciosami tą bronią, w tickach. */
	public float interval() {
		return 20.0F / WEAPON_SPEED[this.weapon.get()];
	}

	public int hitsToKill() {
		return hitsToKill(this.hit(), ABSORPTION[this.absorption.get()], this.interval(), this.saturation.on());
	}
}
