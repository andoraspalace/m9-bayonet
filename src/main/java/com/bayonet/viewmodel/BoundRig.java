package com.bayonet.viewmodel;

import com.bayonet.gltf.GltfAnimation;
import com.bayonet.gltf.GltfScene;
import java.io.IOException;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

final class BoundRig {
	private final GltfScene rig;
	private final MinecraftArms minecraftArms;
	private final Grip grip;
	private final Arms.@Nullable Bound arms;
	private final @Nullable Float handleEnd;
	private final Pose turnPose;

	private BoundRig(GltfScene rig, MinecraftArms minecraftArms, Grip grip, Arms.@Nullable Bound arms, @Nullable Float handleEnd) {
		this.rig = rig;
		this.minecraftArms = minecraftArms;
		this.grip = grip;
		this.arms = arms;
		this.handleEnd = handleEnd;
		this.turnPose = new Pose(rig);
	}

	static BoundRig bind(GltfScene rig, WeaponRig tuning, @Nullable Arms arms) throws IOException {
		MinecraftArms minecraftArms = new MinecraftArms(rig);
		Grip grip = Grip.bind(rig, tuning, minecraftArms.rightHand());
		WeaponRig.FistGrip fist = tuning.fistGrip();
		Float handleEnd = grip.canHoldInFist() && fist != null ? fist.handleEnd() : null;
		return new BoundRig(rig, minecraftArms, grip, arms != null ? arms.bind(rig) : null, handleEnd);
	}

	GltfScene rig() {
		return this.rig;
	}

	MinecraftArms minecraftArms() {
		return this.minecraftArms;
	}

	Grip grip() {
		return this.grip;
	}

	Arms.@Nullable Bound arms() {
		return this.arms;
	}

	@Nullable Float handleEnd() {
		return this.handleEnd;
	}

	@Nullable GltfAnimation clip(String name) {
		return this.rig.animation(name);
	}

	Quaternionf weaponTurn(GltfAnimation clip, float time) {
		this.turnPose.sample(clip, time);
		return this.grip.turn(this.turnPose.worldMatrices());
	}
}
