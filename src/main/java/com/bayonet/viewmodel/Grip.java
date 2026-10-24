package com.bayonet.viewmodel;

import com.bayonet.gltf.GltfAnimation;
import com.bayonet.gltf.GltfScene;
import java.io.IOException;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.jspecify.annotations.Nullable;

final class Grip {
	private static final String WPN_BONE = "wpn";
	private static final String WEAPON_BONE = "weapon";

	private final int wpnBone;
	private final int weaponBone;
	private final int handBone;
	private final @Nullable Matrix4fc inHand;
	private final Quaternionfc fromIdle;
	private final Matrix4f frame = new Matrix4f();
	private final Matrix4f weaponInverse = new Matrix4f();
	private final Matrix4f attached = new Matrix4f();
	private final Matrix4f camera = new Matrix4f();

	private Grip(int wpnBone, int weaponBone, int handBone, @Nullable Matrix4fc inHand, Matrix4f[] idleWorld) {
		this.wpnBone = wpnBone;
		this.weaponBone = weaponBone;
		this.handBone = handBone;
		this.inHand = inHand;
		this.fromIdle = this.rotation(idleWorld).conjugate();
	}

	static Grip bind(GltfScene rig, WeaponRig tuning, int rightHand) throws IOException {
		int wpnBone = rig.findNode(WPN_BONE);
		int weaponBone = rig.findNode(WEAPON_BONE);

		if (wpnBone < 0 || weaponBone < 0) {
			throw new IOException("Clips " + tuning.clipsModel() + " lack the '" + WPN_BONE + "' or '" + WEAPON_BONE + "' bone");
		}

		GltfAnimation idle = rig.animation(tuning.idle());

		if (idle == null) {
			throw new IOException("Clips " + tuning.clipsModel() + " lack the idle clip '" + tuning.idle() + "'");
		}

		Pose pose = new Pose(rig);
		pose.sample(idle, 0.0F);
		Matrix4f[] world = pose.worldMatrices();
		WeaponRig.FistGrip fist = tuning.fistGrip();
		Matrix4f inHand = fist != null && rightHand >= 0
				? new Matrix4f().translation(fist.shift()).mul(new Matrix4f(world[rightHand]).invert()).mul(world[wpnBone])
				: null;
		return new Grip(wpnBone, weaponBone, rightHand, inHand, world);
	}

	boolean canHoldInFist() {
		return this.inHand != null;
	}

	Matrix4fc attach(Matrix4f[] rigWorld, boolean inFist) {
		return RigSpace.TO_CAMERA.mul(this.frame(rigWorld, inFist), this.attached).mul(rigWorld[this.weaponBone].invert(this.weaponInverse));
	}

	Quaternionf turn(Matrix4f[] rigWorld) {
		return this.rotation(rigWorld).mul(this.fromIdle);
	}

	private Quaternionf rotation(Matrix4f[] rigWorld) {
		return RigSpace.TO_CAMERA.mul(this.frame(rigWorld, true), this.camera).getNormalizedRotation(new Quaternionf());
	}

	private Matrix4fc frame(Matrix4f[] rigWorld, boolean inFist) {
		return inFist && this.inHand != null ? rigWorld[this.handBone].mul(this.inHand, this.frame) : rigWorld[this.wpnBone];
	}
}
