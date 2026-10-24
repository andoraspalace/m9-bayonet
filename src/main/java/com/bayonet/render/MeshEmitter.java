package com.bayonet.render;

import com.bayonet.gltf.GltfScene;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

public final class MeshEmitter {
	private static final int[] QUAD_CORNERS = {0, 1, 2, 2};
	private static final Vector3f SCRATCH = new Vector3f();
	private static float[] transformedPositions = new float[0];
	private static float[] transformedNormals = new float[0];

	private MeshEmitter() {
	}

	public static void emit(
		PoseStack.Pose pose, VertexConsumer buffer, float[] positions, float[] normals, float[] uvs, int[] indices, int color, int light, int overlay
	) {
		if (transformedPositions.length < positions.length) {
			transformedPositions = new float[positions.length];
			transformedNormals = new float[positions.length];
		}

		transform(pose, positions, normals, transformedPositions, transformedNormals);
		emitQuads(buffer, transformedPositions, transformedNormals, uvs, indices, null, color, light, overlay);
	}

	public static void transform(PoseStack.Pose pose, float[] positions, float[] normals) {
		transform(pose, positions, normals, positions, normals);
	}

	public static void emitTransformed(VertexConsumer buffer, float[] positions, float[] normals, float[] uvs, int[] indices, int color, int light, int overlay) {
		emitQuads(buffer, positions, normals, uvs, indices, null, color, light, overlay);
	}

	static void emitTransformedVertexColors(
		VertexConsumer buffer, float[] positions, float[] normals, float[] uvs, int[] indices, int[] colors, int light, int overlay
	) {
		emitQuads(buffer, positions, normals, uvs, indices, colors, 0, light, overlay);
	}

	private static void transform(PoseStack.Pose pose, float[] positions, float[] normals, float[] outPositions, float[] outNormals) {
		for (int i = 0; i < positions.length; i += 3) {
			GltfScene.store(pose.pose().transformPosition(positions[i], positions[i + 1], positions[i + 2], SCRATCH), outPositions, i);
			GltfScene.store(pose.transformNormal(normals[i], normals[i + 1], normals[i + 2], SCRATCH), outNormals, i);
		}
	}

	private static void emitQuads(
		VertexConsumer buffer, float[] positions, float[] normals, float[] uvs, int[] indices, int @Nullable [] colors, int color, int light, int overlay
	) {
		for (int t = 0; t < indices.length; t += 3) {
			for (int corner : QUAD_CORNERS) {
				int v = indices[t + corner];
				buffer.addVertex(
					positions[v * 3], positions[v * 3 + 1], positions[v * 3 + 2], colors != null ? colors[v] : color, uvs[v * 2], uvs[v * 2 + 1],
					overlay, light, normals[v * 3], normals[v * 3 + 1], normals[v * 3 + 2]
				);
			}
		}
	}
}
