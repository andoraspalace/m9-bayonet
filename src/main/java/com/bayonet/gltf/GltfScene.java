package com.bayonet.gltf;

import java.util.List;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionfc;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

public record GltfScene(List<Node> nodes, int[] order, List<Skin> skins, List<Part> parts, List<GltfImage> images, List<GltfAnimation> animations) {
	public GltfScene {
		nodes = List.copyOf(nodes);
		skins = List.copyOf(skins);
		parts = List.copyOf(parts);
		images = List.copyOf(images);
		animations = List.copyOf(animations);
	}

	public record Node(String name, int parent, Vector3fc translation, Quaternionfc rotation, Vector3fc scale) {
	}

	public record Skin(int[] joints, float[] inverseBind) {
	}

	public record Skinning(int skin, int influences, int[] joints, float[] weights, int[] bindings) {
		static final int BLENDED = -1;
		static final int UNWEIGHTED = -2;

		public Skinning {
			if (skin < 0 || influences <= 0 || joints.length != weights.length || joints.length != bindings.length * influences) {
				throw new IllegalArgumentException("Skinning of skin " + skin + " has inconsistent joints, weights and bindings");
			}
		}

		static Skinning of(int skin, int influences, int[] joints, float[] weights) {
			int[] bindings = new int[joints.length / influences];

			for (int v = 0; v < bindings.length; v++) {
				bindings[v] = binding(joints, weights, v * influences, influences);
			}

			return new Skinning(skin, influences, joints, weights, bindings);
		}

		private static int binding(int[] joints, float[] weights, int offset, int influences) {
			int contributors = 0;
			int single = BLENDED;
			float weightSum = 0.0F;

			for (int k = offset; k < offset + influences; k++) {
				if (weights[k] != 0.0F) {
					contributors++;
					single = weights[k] == 1.0F ? joints[k] : BLENDED;
					weightSum += weights[k];
				}
			}

			if (contributors == 1 && single >= 0) {
				return single;
			}

			return weightSum <= 0.0F ? UNWEIGHTED : BLENDED;
		}
	}

	public record Part(
		int node, Material material, float[] positions, float[] normals, float[] uvs, float @Nullable [] reflectivity, @Nullable Skinning skinning, int[] indices
	) {
		public int vertexCount() {
			return this.positions.length / 3;
		}
	}

	public int findNode(String name) {
		for (int i = 0; i < this.nodes.size(); i++) {
			if (name.equals(this.nodes.get(i).name())) {
				return i;
			}
		}

		return -1;
	}

	public @Nullable GltfAnimation animation(String name) {
		for (GltfAnimation animation : this.animations) {
			if (animation.name().equals(name)) {
				return animation;
			}
		}

		return null;
	}

	public Matrix4f[] restLocalMatrices() {
		Matrix4f[] local = new Matrix4f[this.nodes.size()];

		for (int i = 0; i < local.length; i++) {
			Node node = this.nodes.get(i);
			local[i] = new Matrix4f().translationRotateScale(node.translation(), node.rotation(), node.scale());
		}

		return local;
	}

	public Matrix4f[] worldMatrices(Matrix4fc[] local) {
		Matrix4f[] world = new Matrix4f[this.nodes.size()];

		for (int i = 0; i < world.length; i++) {
			world[i] = new Matrix4f();
		}

		return this.worldMatrices(local, world);
	}

	public Matrix4f[] worldMatrices(Matrix4fc[] local, Matrix4f[] world) {
		for (int i : this.order) {
			int parent = this.nodes.get(i).parent();

			if (parent >= 0) {
				world[parent].mul(local[i], world[i]);
			} else {
				world[i].set(local[i]);
			}
		}

		return world;
	}

	public static void store(Vector3fc vector, float[] out, int offset) {
		out[offset] = vector.x();
		out[offset + 1] = vector.y();
		out[offset + 2] = vector.z();
	}
}
