package com.bayonet.gltf;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public record GltfModel(List<Part> parts, List<GltfImage> images) {
	private static final float UNIT_CUBE_CENTER = 0.5F;

	public GltfModel {
		parts = List.copyOf(parts);
		images = List.copyOf(images);
	}

	public record Part(Material material, float[] positions, float[] normals, float[] uvs, int[] indices) {
	}

	public record Bounds(Vector3fc min, Vector3fc max) {
		public static final Bounds EMPTY = new Bounds(new Vector3f(), new Vector3f());
		private static final int CORNERS = 8;
		private static final int MAX_X = 1;
		private static final int MAX_Y = 2;
		private static final int MAX_Z = 4;

		float largestSide() {
			return Math.max(this.max.x() - this.min.x(), Math.max(this.max.y() - this.min.y(), this.max.z() - this.min.z()));
		}

		Vector3f center() {
			return this.min.add(this.max, new Vector3f()).div(2.0F);
		}

		public void corners(Consumer<Vector3fc> output) {
			for (int corner = 0; corner < CORNERS; corner++) {
				output.accept(new Vector3f(
					(corner & MAX_X) == 0 ? this.min.x() : this.max.x(),
					(corner & MAX_Y) == 0 ? this.min.y() : this.max.y(),
					(corner & MAX_Z) == 0 ? this.min.z() : this.max.z()
				));
			}
		}
	}

	public static GltfModel restPose(GltfScene scene) {
		Matrix4f[] world = scene.worldMatrices(scene.restLocalMatrices());
		PosedMesh posed = new PosedMesh(scene);
		posed.pose(world);
		List<Part> parts = new ArrayList<>(scene.parts().size());

		for (int p = 0; p < scene.parts().size(); p++) {
			GltfScene.Part part = scene.parts().get(p);
			boolean mirrored = part.skinning() == null && world[part.node()].determinant() < 0.0F;
			int[] indices = mirrored ? reverseWinding(part.indices()) : part.indices();
			parts.add(new Part(part.material(), posed.positions(p), posed.normals(p), part.uvs(), indices));
		}

		return new GltfModel(parts, scene.images());
	}

	private static int[] reverseWinding(int[] indices) {
		int[] reversed = indices.clone();

		for (int t = 0; t < reversed.length; t += 3) {
			reversed[t + 1] = indices[t + 2];
			reversed[t + 2] = indices[t + 1];
		}

		return reversed;
	}

	public Bounds bounds() {
		Vector3f min = new Vector3f(Float.POSITIVE_INFINITY);
		Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY);
		Vector3f point = new Vector3f();
		boolean any = false;

		for (Part part : this.parts) {
			float[] positions = part.positions();

			for (int i = 0; i < positions.length; i += 3) {
				point.set(positions[i], positions[i + 1], positions[i + 2]);
				min.min(point);
				max.max(point);
				any = true;
			}
		}

		return any ? new Bounds(min, max) : Bounds.EMPTY;
	}

	public GltfModel fitToUnitCube(Matrix4fc rotation) {
		GltfModel rotated = this.transform(rotation);
		Bounds bounds = rotated.bounds();
		float size = bounds.largestSide();
		float scale = size > 0.0F ? 1.0F / size : 1.0F;
		Matrix4f fit = new Matrix4f()
				.translation(UNIT_CUBE_CENTER, UNIT_CUBE_CENTER, UNIT_CUBE_CENTER)
				.scale(scale)
				.translate(bounds.center().negate());
		return rotated.transform(fit);
	}

	private GltfModel transform(Matrix4fc matrix) {
		Matrix3f normalMatrix = matrix.normal(new Matrix3f());
		Vector3f scratch = new Vector3f();
		List<Part> result = new ArrayList<>(this.parts.size());

		for (Part part : this.parts) {
			float[] sourcePositions = part.positions();
			float[] sourceNormals = part.normals();
			float[] positions = new float[sourcePositions.length];
			float[] normals = new float[sourceNormals.length];

			for (int i = 0; i < positions.length; i += 3) {
				matrix.transformPosition(scratch.set(sourcePositions[i], sourcePositions[i + 1], sourcePositions[i + 2]));
				positions[i] = scratch.x;
				positions[i + 1] = scratch.y;
				positions[i + 2] = scratch.z;

				normalMatrix.transform(scratch.set(sourceNormals[i], sourceNormals[i + 1], sourceNormals[i + 2])).normalize();
				normals[i] = scratch.x;
				normals[i + 1] = scratch.y;
				normals[i + 2] = scratch.z;
			}

			result.add(new Part(part.material(), positions, normals, part.uvs(), part.indices()));
		}

		return new GltfModel(result, this.images);
	}
}
