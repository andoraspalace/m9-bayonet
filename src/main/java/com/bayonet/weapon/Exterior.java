package com.bayonet.weapon;

import java.util.Locale;

public enum Exterior {
	FACTORY_NEW(0.0F, 0.07F),
	MINIMAL_WEAR(0.07F, 0.15F),
	FIELD_TESTED(0.15F, 0.38F),
	WELL_WORN(0.38F, 0.45F),
	BATTLE_SCARRED(0.45F, 1.0F);

	private static final Exterior[] EXTERIORS = values();
	private static final String TRANSLATION_PREFIX = "exterior.bayonet.";

	private final float min;
	private final float max;

	Exterior(float min, float max) {
		this.min = min;
		this.max = max;
	}

	static Exterior of(float wear) {
		for (Exterior exterior : EXTERIORS) {
			if (wear < exterior.max) {
				return exterior;
			}
		}

		return BATTLE_SCARRED;
	}

	String translationKey() {
		return TRANSLATION_PREFIX + this.name().toLowerCase(Locale.ROOT);
	}

	float typicalWear(WearRange range) {
		float min = Math.max(this.min, range.min());
		float max = Math.min(this.max, range.max());
		return (min + Math.max(min, max)) / 2.0F;
	}
}
