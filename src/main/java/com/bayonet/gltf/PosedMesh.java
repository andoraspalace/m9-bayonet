package com.bayonet.gltf;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

public final class PosedMesh {
	private static final int MATRIX_SIZE = 16;
	private static final int[] AFFINE_ELEMENTS = {0, 1, 2, 4, 5, 6, 8, 9, 10, 12, 13, 14};

	private final GltfScene scene;
	private final float[][] positions;
	private final float[][] normals;
	private final float[][] jointMatrices;
	private final float[] blended = new float[MATRIX_SIZE];
	private final float[] nodeMatrix = new float[MATRIX_SIZE];
	private final Matrix4f inverseBind = new Matrix4f();
	private final Matrix4f jointMatrix = new Matrix4f();
	private final Matrix3f normalMatrix = new Matrix3f();
	private final Vector3f scratch = new Vector3f();

	public PosedMesh(GltfScene scene) {
		this.scene = scene;
		this.positions = new float[scene.parts().size()][];
		this.normals = new float[scene.parts().size()][];
		this.jointMatrices = new float[scene.skins().size()][];

		for (int p = 0; p < this.positions.length; p++) {
			this.positions[p] = new float[scene.parts().get(p).positions().length];
			this.normals[p] = new float[scene.parts().get(p).normals().length];
		}

		for (int s = 0; s < this.jointMatrices.length; s++) {
			this.jointMatrices[s] = new float[scene.skins().get(s).joints().length * MATRIX_SIZE];
		}
	}

	public float[] positions(int part) {
		return this.positions[part];
	}

	public float[] normals(int part) {
		return this.normals[part];
	}

	public void pose(Matrix4fc[] world) {
		for (int s = 0; s < this.jointMatrices.length; s++) {
			GltfScene.Skin skin = this.scene.skins().get(s);

			for (int j = 0; j < skin.joints().length; j++) {
				this.inverseBind.set(skin.inverseBind(), j * MATRIX_SIZE);
				world[skin.joints()[j]].mul(this.inverseBind, this.jointMatrix).get(this.jointMatrices[s], j * MATRIX_SIZE);
			}
		}

		for (int p = 0; p < this.positions.length; p++) {
			GltfScene.Part part = this.scene.parts().get(p);
			GltfScene.Skinning skinning = part.skinning();

			if (skinning != null) {
				this.poseSkinned(part, skinning, world[part.node()], p);
			} else {
				this.poseRigid(part, world[part.node()], p);
			}
		}
	}

	private void poseRigid(GltfScene.Part part, Matrix4fc nodeWorld, int p) {
		float[] in = part.positions();
		float[] inNormals = part.normals();
		float[] out = this.positions[p];
		float[] outNormals = this.normals[p];
		nodeWorld.normal(this.normalMatrix);

		for (int v = 0; v < part.vertexCount(); v++) {
			nodeWorld.transformPosition(this.scratch.set(in[v * 3], in[v * 3 + 1], in[v * 3 + 2])).get(out, v * 3);
			this.normalMatrix.transform(this.scratch.set(inNormals[v * 3], inNormals[v * 3 + 1], inNormals[v * 3 + 2])).normalize().get(outNormals, v * 3);
		}
	}

	private void poseSkinned(GltfScene.Part part, GltfScene.Skinning skinning, Matrix4fc nodeWorld, int p) {
		float[] in = part.positions();
		float[] inNormals = part.normals();
		float[] out = this.positions[p];
		float[] outNormals = this.normals[p];
		float[] joints = this.jointMatrices[skinning.skin()];
		int[] bindings = skinning.bindings();
		nodeWorld.get(this.nodeMatrix);

		for (int v = 0; v < part.vertexCount(); v++) {
			int binding = bindings[v];
			float[] matrix = this.nodeMatrix;
			int offset = 0;

			if (binding >= 0) {
				matrix = joints;
				offset = binding * MATRIX_SIZE;
			} else if (binding == GltfScene.Skinning.BLENDED) {
				this.blend(skinning, joints, v);
				matrix = this.blended;
			}

			float x = in[v * 3];
			float y = in[v * 3 + 1];
			float z = in[v * 3 + 2];
			out[v * 3] = matrix[offset] * x + matrix[offset + 4] * y + matrix[offset + 8] * z + matrix[offset + 12];
			out[v * 3 + 1] = matrix[offset + 1] * x + matrix[offset + 5] * y + matrix[offset + 9] * z + matrix[offset + 13];
			out[v * 3 + 2] = matrix[offset + 2] * x + matrix[offset + 6] * y + matrix[offset + 10] * z + matrix[offset + 14];

			float nx = inNormals[v * 3];
			float ny = inNormals[v * 3 + 1];
			float nz = inNormals[v * 3 + 2];
			this.scratch.set(
				matrix[offset] * nx + matrix[offset + 4] * ny + matrix[offset + 8] * nz,
				matrix[offset + 1] * nx + matrix[offset + 5] * ny + matrix[offset + 9] * nz,
				matrix[offset + 2] * nx + matrix[offset + 6] * ny + matrix[offset + 10] * nz
			).normalize().get(outNormals, v * 3);
		}
	}

	private void blend(GltfScene.Skinning skinning, float[] joints, int vertex) {
		int influences = skinning.influences();
		float weightSum = 0.0F;

		for (int element : AFFINE_ELEMENTS) {
			this.blended[element] = 0.0F;
		}

		for (int k = vertex * influences; k < (vertex + 1) * influences; k++) {
			float weight = skinning.weights()[k];

			if (weight == 0.0F) {
				continue;
			}

			int joint = skinning.joints()[k] * MATRIX_SIZE;

			for (int element : AFFINE_ELEMENTS) {
				this.blended[element] += weight * joints[joint + element];
			}

			weightSum += weight;
		}

		if (weightSum != 1.0F) {
			for (int element : AFFINE_ELEMENTS) {
				this.blended[element] /= weightSum;
			}
		}
	}
}
