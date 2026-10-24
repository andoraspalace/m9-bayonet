package com.bayonet.weapon;

import java.util.Locale;

enum Phase {
	EMERALD, RUBY, SAPPHIRE, PHASE2;

	private static final String TRANSLATION_PREFIX = "phase.bayonet.";

	String translationKey() {
		return TRANSLATION_PREFIX + this.name().toLowerCase(Locale.ROOT);
	}
}
