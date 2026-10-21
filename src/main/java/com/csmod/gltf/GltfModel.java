package com.csmod.gltf;

import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

public record GltfModel(List<Part> parts, List<byte[]> images) {
	public record Part(int image, int color, boolean cutout, float[] positions, float[] normals, float[] uvs) {
		public int vertexCount() {
			return this.positions.length / 3;
		}
	}

	public GltfModel transform(Matrix4fc matrix) {
		Matrix3f normalMatrix = matrix.normal(new Matrix3f());
		Vector3f scratch = new Vector3f();
		List<Part> result = new ArrayList<>(this.parts.size());

		for (Part part : this.parts) {
			float[] positions = new float[part.positions.length];
			float[] normals = new float[part.normals.length];

			for (int i = 0; i < positions.length; i += 3) {
				matrix.transformPosition(scratch.set(part.positions[i], part.positions[i + 1], part.positions[i + 2]));
				positions[i] = scratch.x;
				positions[i + 1] = scratch.y;
				positions[i + 2] = scratch.z;

				normalMatrix.transform(scratch.set(part.normals[i], part.normals[i + 1], part.normals[i + 2])).normalize();
				normals[i] = scratch.x;
				normals[i + 1] = scratch.y;
				normals[i + 2] = scratch.z;
			}

			result.add(new Part(part.image, part.color, part.cutout, positions, normals, part.uvs));
		}

		return new GltfModel(result, this.images);
	}

	public float[] bounds() {
		float[] bounds = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
				Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
		boolean any = false;

		for (Part part : this.parts) {
			float[] positions = part.positions;

			for (int i = 0; i < positions.length; i += 3) {
				for (int axis = 0; axis < 3; axis++) {
					bounds[axis] = Math.min(bounds[axis], positions[i + axis]);
					bounds[axis + 3] = Math.max(bounds[axis + 3], positions[i + axis]);
				}

				any = true;
			}
		}

		return any ? bounds : null;
	}

	public GltfModel fitToUnitCube(Matrix4fc rotation) {
		GltfModel rotated = this.transform(rotation);
		float[] b = rotated.bounds();

		if (b == null) {
			return rotated;
		}

		float size = Math.max(b[3] - b[0], Math.max(b[4] - b[1], b[5] - b[2]));
		float scale = size > 0.0F ? 1.0F / size : 1.0F;
		Matrix4f fit = new Matrix4f()
				.translation(0.5F, 0.5F, 0.5F)
				.scale(scale)
				.translate(-(b[0] + b[3]) / 2.0F, -(b[1] + b[4]) / 2.0F, -(b[2] + b[5]) / 2.0F);
		return rotated.transform(fit);
	}
}
