package com.bayonet.weapon;

import net.minecraft.util.Mth;

record WearRange(float min, float max) {
	static final WearRange NONE = new WearRange(0.0F, 0.0F);
	static final WearRange DOPPLER = new WearRange(0.0F, 0.08F);

	boolean isEmpty() {
		return this.max <= this.min;
	}

	float at(float used) {
		return Mth.lerp(used, this.min, this.max);
	}
}
