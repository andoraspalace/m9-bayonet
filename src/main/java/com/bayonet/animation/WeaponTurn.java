package com.bayonet.animation;

import com.bayonet.gltf.GltfAnimation;
import com.bayonet.viewmodel.Viewmodel;
import com.bayonet.weapon.Weapon;
import org.joml.Quaternionfc;
import org.jspecify.annotations.Nullable;

final class WeaponTurn {
	private final Weapon weapon;
	private final Viewmodel viewmodel;
	private final GltfAnimation clip;
	private final float time;
	private @Nullable Quaternionfc rotation;

	WeaponTurn(Weapon weapon, Viewmodel viewmodel, GltfAnimation clip, float time) {
		this.weapon = weapon;
		this.viewmodel = viewmodel;
		this.clip = clip;
		this.time = time;
	}

	Weapon weapon() {
		return this.weapon;
	}

	Quaternionfc rotation() {
		Quaternionfc rotation = this.rotation;

		if (rotation == null) {
			rotation = this.viewmodel.weaponTurn(this.clip, this.time);
			this.rotation = rotation;
		}

		return rotation;
	}
}
