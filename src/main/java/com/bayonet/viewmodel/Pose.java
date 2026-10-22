package com.bayonet.viewmodel;

import com.bayonet.gltf.GltfAnimation;
import com.bayonet.gltf.GltfScene;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class Pose {
	private final Vector3f[] translations;
	private final Quaternionf[] rotations;
	private final Vector3f[] scales;
	private final Quaternionf scratch = new Quaternionf();

	Pose(GltfScene rig) {
		int count = rig.nodes().size();
		this.translations = new Vector3f[count];
		this.rotations = new Quaternionf[count];
		this.scales = new Vector3f[count];

		for (int i = 0; i < count; i++) {
			this.translations[i] = new Vector3f();
			this.rotations[i] = new Quaternionf();
			this.scales[i] = new Vector3f();
		}
	}

	void sample(GltfScene rig, GltfAnimation clip, float time) {
		for (int i = 0; i < this.translations.length; i++) {
			GltfScene.Node node = rig.nodes().get(i);
			this.translations[i].set(node.translation());
			this.rotations[i].set(node.rotation());
			this.scales[i].set(node.scale());
		}

		clip.sample(time, this.translations, this.rotations, this.scales);
	}

	void set(Pose other) {
		for (int i = 0; i < this.translations.length; i++) {
			this.translations[i].set(other.translations[i]);
			this.rotations[i].set(other.rotations[i]);
			this.scales[i].set(other.scales[i]);
		}
	}

	void blendFrom(Pose from, float weight) {
		for (int i = 0; i < this.translations.length; i++) {
			this.translations[i].set(new Vector3f(from.translations[i]).lerp(this.translations[i], weight));
			this.rotations[i].set(this.scratch.set(from.rotations[i]).slerp(this.rotations[i], weight));
			this.scales[i].set(new Vector3f(from.scales[i]).lerp(this.scales[i], weight));
		}
	}

	Matrix4f[] localMatrices() {
		Matrix4f[] local = new Matrix4f[this.translations.length];

		for (int i = 0; i < local.length; i++) {
			local[i] = new Matrix4f().translationRotateScale(this.translations[i], this.rotations[i], this.scales[i]);
		}

		return local;
	}
}
