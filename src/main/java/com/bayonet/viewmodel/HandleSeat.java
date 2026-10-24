package com.bayonet.viewmodel;

import com.bayonet.gltf.GltfScene;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

final class HandleSeat {
	private static final float SEAT_LIMIT = 0.8F * MinecraftArms.PIXEL;

	private final int[][] vertices;
	private final Matrix4f toFist = new Matrix4f();
	private final Vector3f local = new Vector3f();
	private final Vector3f offset = new Vector3f();

	private HandleSeat(int[][] vertices) {
		this.vertices = vertices;
	}

	static @Nullable HandleSeat of(GltfScene model, float handleEnd) {
		int[][] vertices = new int[model.parts().size()][];
		boolean found = false;

		for (int p = 0; p < vertices.length; p++) {
			GltfScene.Part part = model.parts().get(p);
			IntArrayList handle = new IntArrayList();

			for (int v = 0; v < part.vertexCount(); v++) {
				if (part.positions()[v * 3 + 1] < handleEnd) {
					handle.add(v);
				}
			}

			vertices[p] = handle.toIntArray();
			found |= !handle.isEmpty();
		}

		return found ? new HandleSeat(vertices) : null;
	}

	Vector3fc nudge(Matrix4fc fist, MinecraftArms.FistBounds bounds, RiggedMesh weapon) {
		Matrix4f toFist = fist.invert(this.toFist);
		Vector3f local = this.local;
		float minX = Float.POSITIVE_INFINITY;
		float maxX = Float.NEGATIVE_INFINITY;
		float minY = Float.POSITIVE_INFINITY;
		float maxY = Float.NEGATIVE_INFINITY;

		for (int p = 0; p < this.vertices.length; p++) {
			float[] posed = weapon.positions(p);

			for (int v : this.vertices[p]) {
				toFist.transformPosition(posed[v * 3], posed[v * 3 + 1], posed[v * 3 + 2], local);
				minX = Math.min(minX, local.x);
				maxX = Math.max(maxX, local.x);
				minY = Math.min(minY, local.y);
				maxY = Math.max(maxY, local.y);
			}
		}

		Vector3f nudge = this.offset.set(fit(minX, maxX, bounds.minX(), bounds.maxX()), fit(minY, maxY, bounds.minY(), bounds.maxY()), 0.0F);
		float length = nudge.length();
		return length == 0.0F ? nudge : fist.transformDirection(nudge.mul(Math.min(1.0F, SEAT_LIMIT / length)));
	}

	private static float fit(float min, float max, float low, float high) {
		if (max - min > high - low) {
			return (low + high - min - max) / 2.0F;
		}

		return min < low ? low - min : max > high ? high - max : 0.0F;
	}
}
