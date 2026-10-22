package com.bayonet.gltf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

public record GltfScene(List<Node> nodes, int[] order, List<Skin> skins, List<Part> parts, List<byte[]> images, List<GltfAnimation> animations) {
	public record Node(String name, int parent, Vector3f translation, Quaternionf rotation, Vector3f scale) {
		public Matrix4f localMatrix(Matrix4f dest) {
			return dest.translationRotateScale(this.translation, this.rotation, this.scale);
		}
	}

	public record Skin(int[] joints, float[] inverseBind) {
	}

	public record Part(
		int node, int skin, int image, int color, boolean cutout, @Nullable String wearMaps,
		float[] positions, float @Nullable [] normals, float[] uvs, float @Nullable [] reflectivity, int @Nullable [] joints, float @Nullable [] weights, int influences,
		int[] indices
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

	public Matrix4f[] worldMatrices(Matrix4fc[] local) {
		Matrix4f[] world = new Matrix4f[this.nodes.size()];

		for (int i : this.order) {
			int parent = this.nodes.get(i).parent();
			world[i] = parent >= 0 && world[parent] != null ? new Matrix4f(world[parent]).mul(local[i]) : new Matrix4f(local[i]);
		}

		return world;
	}

	public Matrix4f[] restLocalMatrices() {
		Matrix4f[] local = new Matrix4f[this.nodes.size()];

		for (int i = 0; i < local.length; i++) {
			local[i] = this.nodes.get(i).localMatrix(new Matrix4f());
		}

		return local;
	}

	public void pose(Matrix4fc[] world, float[][] positions, float[][] normals) {
		float[][] skinMatrices = new float[this.skins.size()][];

		for (int s = 0; s < skinMatrices.length; s++) {
			Skin skin = this.skins.get(s);
			float[] matrices = new float[skin.joints().length * 16];
			Matrix4f scratch = new Matrix4f();

			for (int j = 0; j < skin.joints().length; j++) {
				Matrix4fc joint = world[skin.joints()[j]];
				scratch.set(skin.inverseBind(), j * 16);
				(joint != null ? new Matrix4f(joint) : new Matrix4f()).mul(scratch).get(matrices, j * 16);
			}

			skinMatrices[s] = matrices;
		}

		float[] blended = new float[16];
		Matrix3f normalMatrix = new Matrix3f();
		Vector3f scratch = new Vector3f();

		for (int p = 0; p < this.parts.size(); p++) {
			Part part = this.parts.get(p);
			float[] in = part.positions();
			float[] inNormals = part.normals();
			float[] out = positions[p];
			float[] outNormals = normals[p];
			Matrix4fc nodeWorld = world[part.node()] != null ? world[part.node()] : new Matrix4f();

			if (part.skin() < 0 || part.joints() == null) {
				nodeWorld.normal(normalMatrix);

				for (int v = 0; v < part.vertexCount(); v++) {
					store(nodeWorld.transformPosition(scratch.set(in[v * 3], in[v * 3 + 1], in[v * 3 + 2])), out, v * 3);

					if (inNormals != null) {
						store(normalMatrix.transform(scratch.set(inNormals[v * 3], inNormals[v * 3 + 1], inNormals[v * 3 + 2])).normalize(), outNormals, v * 3);
					}
				}

				continue;
			}

			float[] matrices = skinMatrices[part.skin()];
			int jointCount = matrices.length / 16;
			int influences = part.influences();

			for (int v = 0; v < part.vertexCount(); v++) {
				int rigid = -1;
				int contributors = 0;

				for (int k = 0; k < influences; k++) {
					float weight = part.weights()[v * influences + k];
					int joint = part.joints()[v * influences + k];

					if (weight == 0.0F || joint < 0 || joint >= jointCount) {
						continue;
					}

					contributors++;
					rigid = weight == 1.0F ? joint : -1;
				}

				float[] matrix = blended;
				int offset = 0;

				if (contributors == 1 && rigid >= 0) {
					matrix = matrices;
					offset = rigid * 16;
				} else {
					Arrays.fill(blended, 0.0F);
					float weightSum = 0.0F;

					for (int k = 0; k < influences; k++) {
						float weight = part.weights()[v * influences + k];
						int joint = part.joints()[v * influences + k];

						if (weight == 0.0F || joint < 0 || joint >= jointCount) {
							continue;
						}

						for (int e = 0; e < 16; e++) {
							blended[e] += weight * matrices[joint * 16 + e];
						}

						weightSum += weight;
					}

					if (weightSum <= 0.0F) {
						nodeWorld.get(blended);
					} else if (weightSum != 1.0F) {
						for (int e = 0; e < 16; e++) {
							blended[e] /= weightSum;
						}
					}
				}

				float x = in[v * 3];
				float y = in[v * 3 + 1];
				float z = in[v * 3 + 2];
				out[v * 3] = matrix[offset] * x + matrix[offset + 4] * y + matrix[offset + 8] * z + matrix[offset + 12];
				out[v * 3 + 1] = matrix[offset + 1] * x + matrix[offset + 5] * y + matrix[offset + 9] * z + matrix[offset + 13];
				out[v * 3 + 2] = matrix[offset + 2] * x + matrix[offset + 6] * y + matrix[offset + 10] * z + matrix[offset + 14];

				if (inNormals != null) {
					float nx = inNormals[v * 3];
					float ny = inNormals[v * 3 + 1];
					float nz = inNormals[v * 3 + 2];
					store(scratch.set(
						matrix[offset] * nx + matrix[offset + 4] * ny + matrix[offset + 8] * nz,
						matrix[offset + 1] * nx + matrix[offset + 5] * ny + matrix[offset + 9] * nz,
						matrix[offset + 2] * nx + matrix[offset + 6] * ny + matrix[offset + 10] * nz
					).normalize(), outNormals, v * 3);
				}
			}
		}
	}

	public float[][] newVertexArrays() {
		float[][] arrays = new float[this.parts.size()][];

		for (int p = 0; p < arrays.length; p++) {
			arrays[p] = new float[this.parts.get(p).positions().length];
		}

		return arrays;
	}

	public GltfModel restPose() {
		Matrix4f[] world = this.worldMatrices(this.restLocalMatrices());
		float[][] positions = this.newVertexArrays();
		float[][] normals = this.newVertexArrays();
		this.pose(world, positions, normals);
		List<GltfModel.Part> flat = new ArrayList<>(this.parts.size());
		Vector3f edgeA = new Vector3f();
		Vector3f edgeB = new Vector3f();

		for (int p = 0; p < this.parts.size(); p++) {
			Part part = this.parts.get(p);
			int[] indices = part.indices();
			boolean flip = part.skin() < 0 && world[part.node()] != null && world[part.node()].determinant() < 0.0F;
			float[] outPositions = new float[indices.length * 3];
			float[] outNormals = new float[indices.length * 3];
			float[] outUvs = new float[indices.length * 2];

			for (int t = 0; t < indices.length; t += 3) {
				int[] corners = flip ? new int[]{indices[t], indices[t + 2], indices[t + 1]} : new int[]{indices[t], indices[t + 1], indices[t + 2]};

				for (int c = 0; c < 3; c++) {
					int v = corners[c];
					System.arraycopy(positions[p], v * 3, outPositions, (t + c) * 3, 3);
					System.arraycopy(normals[p], v * 3, outNormals, (t + c) * 3, 3);
					System.arraycopy(part.uvs(), v * 2, outUvs, (t + c) * 2, 2);
				}

				if (part.normals() == null) {
					int base = t * 3;
					edgeA.set(outPositions[base + 3] - outPositions[base], outPositions[base + 4] - outPositions[base + 1], outPositions[base + 5] - outPositions[base + 2]);
					edgeB.set(outPositions[base + 6] - outPositions[base], outPositions[base + 7] - outPositions[base + 1], outPositions[base + 8] - outPositions[base + 2]);
					edgeA.cross(edgeB);

					if (edgeA.lengthSquared() > 0.0F) {
						edgeA.normalize();
					} else {
						edgeA.set(0.0F, 1.0F, 0.0F);
					}

					for (int c = 0; c < 3; c++) {
						store(edgeA, outNormals, base + c * 3);
					}
				}
			}

			flat.add(new GltfModel.Part(part.image(), part.color(), part.cutout(), part.wearMaps(), outPositions, outNormals, outUvs));
		}

		return new GltfModel(flat, this.images);
	}

	public static void store(Vector3fc vector, float[] out, int offset) {
		out[offset] = vector.x();
		out[offset + 1] = vector.y();
		out[offset + 2] = vector.z();
	}
}
