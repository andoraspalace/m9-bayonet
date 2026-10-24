package com.bayonet.viewmodel;

import com.bayonet.gltf.GltfAnimation;
import com.bayonet.gltf.GltfScene;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class Pose {
	private final GltfScene rig;
	private final Vector3f[] translations;
	private final Quaternionf[] rotations;
	private final Vector3f[] scales;
	private final Matrix4f[] locals;
	private final Matrix4f[] world;

	Pose(GltfScene rig) {
		int count = rig.nodes().size();
		this.rig = rig;
		this.translations = new Vector3f[count];
		this.rotations = new Quaternionf[count];
		this.scales = new Vector3f[count];
		this.locals = new Matrix4f[count];
		this.world = new Matrix4f[count];

		for (int i = 0; i < count; i++) {
			this.translations[i] = new Vector3f();
			this.rotations[i] = new Quaternionf();
			this.scales[i] = new Vector3f();
			this.locals[i] = new Matrix4f();
			this.world[i] = new Matrix4f();
		}
	}

	public void sample(GltfAnimation clip, float time) {
		for (int i = 0; i < this.translations.length; i++) {
			GltfScene.Node node = this.rig.nodes().get(i);
			this.translations[i].set(node.translation());
			this.rotations[i].set(node.rotation());
			this.scales[i].set(node.scale());
		}

		clip.sample(time, this.translations, this.rotations, this.scales);
	}

	public void set(Pose other) {
		for (int i = 0; i < this.translations.length; i++) {
			this.translations[i].set(other.translations[i]);
			this.rotations[i].set(other.rotations[i]);
			this.scales[i].set(other.scales[i]);
		}
	}

	public void blendFrom(Pose from, float weight) {
		for (int i = 0; i < this.translations.length; i++) {
			from.translations[i].lerp(this.translations[i], weight, this.translations[i]);
			from.rotations[i].slerp(this.rotations[i], weight, this.rotations[i]);
			from.scales[i].lerp(this.scales[i], weight, this.scales[i]);
		}
	}

	Matrix4f[] worldMatrices() {
		for (int i = 0; i < this.locals.length; i++) {
			this.locals[i].translationRotateScale(this.translations[i], this.rotations[i], this.scales[i]);
		}

		return this.rig.worldMatrices(this.locals, this.world);
	}
}
