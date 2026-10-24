package com.bayonet.gltf;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

final class GltfPrimitiveReader {
	private static final int MODE_TRIANGLES = 4;
	private static final int TRIANGLE = 3;
	private static final int JOINTS_PER_SET = 4;
	private static final float CHANNEL_MAX = 255.0F;
	private static final int CHANNEL_MASK = 0xFF;
	private static final Vector3fc FLAT_FALLBACK_NORMAL = new Vector3f(0.0F, 1.0F, 0.0F);

	private final JsonObject json;
	private final GltfAccessors accessors;
	private final List<GltfScene.Skin> skins;

	GltfPrimitiveReader(JsonObject json, GltfAccessors accessors, List<GltfScene.Skin> skins) {
		this.json = json;
		this.accessors = accessors;
		this.skins = skins;
	}

	GltfScene.@Nullable Part read(JsonObject primitive, int node, int skin) throws IOException {
		JsonObject attributes = primitive.getAsJsonObject("attributes");

		if (GltfJson.intOr(primitive, "mode", MODE_TRIANGLES) != MODE_TRIANGLES || attributes == null || !attributes.has("POSITION")) {
			return null;
		}

		float[] positions = this.accessors.floats(attributes.get("POSITION").getAsInt());
		int vertexCount = positions.length / GltfAccessors.VEC3;
		float[] normals = this.optionalFloats(attributes, "NORMAL");
		float[] texcoords = this.optionalFloats(attributes, "TEXCOORD_0");
		float[] uvs = texcoords != null ? texcoords : new float[vertexCount * GltfAccessors.VEC2];
		float[] reflectivity = this.optionalFloats(attributes, "_REFLECTIVITY");

		if (reflectivity != null && reflectivity.length != vertexCount * GltfAccessors.VEC4) {
			throw new IOException("_REFLECTIVITY must be a VEC4 per vertex");
		}

		if (positions.length % GltfAccessors.VEC3 != 0
				|| normals != null && normals.length != vertexCount * GltfAccessors.VEC3
				|| uvs.length != vertexCount * GltfAccessors.VEC2) {
			throw new IOException("POSITION, NORMAL and TEXCOORD_0 must be a VEC3, VEC3 and VEC2 per vertex");
		}

		if (skin >= this.skins.size()) {
			throw new IOException("Node " + node + " has skin " + skin + " outside the " + this.skins.size() + " skins");
		}

		int[] indices = this.readIndices(primitive, vertexCount);
		GltfScene.Skinning skinning = this.readSkinning(attributes, skin, vertexCount);
		Material material = this.readMaterial(primitive);
		boolean smooth = normals != null;
		int[] sources = smooth ? referencedVertices(indices, vertexCount) : indices;
		int[] partIndices = smooth ? remap(indices, sources, vertexCount) : IntStream.range(0, indices.length).toArray();
		float[] partPositions = gather(positions, sources, GltfAccessors.VEC3);

		return new GltfScene.Part(
			node,
			material,
			partPositions,
			smooth ? gather(normals, sources, GltfAccessors.VEC3) : flatNormals(partPositions),
			gather(uvs, sources, GltfAccessors.VEC2),
			reflectivity != null ? gather(reflectivity, sources, GltfAccessors.VEC4) : null,
			skinning != null ? gather(skinning, sources) : null,
			partIndices
		);
	}

	private float @Nullable [] optionalFloats(JsonObject attributes, String name) throws IOException {
		return attributes.has(name) ? this.accessors.floats(attributes.get(name).getAsInt()) : null;
	}

	private int[] readIndices(JsonObject primitive, int vertexCount) throws IOException {
		int[] indices = primitive.has("indices") ? this.accessors.ints(primitive.get("indices").getAsInt()) : IntStream.range(0, vertexCount).toArray();
		indices = Arrays.copyOf(indices, indices.length - indices.length % TRIANGLE);

		for (int index : indices) {
			if (index < 0 || index >= vertexCount) {
				throw new IOException("Index " + index + " out of range for " + vertexCount + " vertices");
			}
		}

		return indices;
	}

	private GltfScene.@Nullable Skinning readSkinning(JsonObject attributes, int skin, int vertexCount) throws IOException {
		int sets = 0;

		while (skin >= 0 && attributes.has("JOINTS_" + sets) && attributes.has("WEIGHTS_" + sets)) {
			sets++;
		}

		if (sets == 0) {
			return null;
		}

		int influences = sets * JOINTS_PER_SET;

		if ((long) vertexCount * influences > GltfAccessors.MAX_VALUES) {
			throw new IOException(sets + " joint sets on " + vertexCount + " vertices exceed the supported " + GltfAccessors.MAX_VALUES + " skinning values");
		}

		int jointCount = this.skins.get(skin).joints().length;
		int[] joints = new int[vertexCount * influences];
		float[] weights = new float[vertexCount * influences];

		for (int set = 0; set < sets; set++) {
			int[] setJoints = this.accessors.ints(attributes.get("JOINTS_" + set).getAsInt());
			float[] setWeights = this.accessors.floats(attributes.get("WEIGHTS_" + set).getAsInt());

			if (setJoints.length != vertexCount * JOINTS_PER_SET || setWeights.length != vertexCount * JOINTS_PER_SET) {
				throw new IOException("JOINTS_" + set + " and WEIGHTS_" + set + " must be a VEC4 per vertex");
			}

			for (int joint : setJoints) {
				if (joint < 0 || joint >= jointCount) {
					throw new IOException("JOINTS_" + set + " has joint " + joint + " outside the " + jointCount + " joints of skin " + skin);
				}
			}

			for (int v = 0; v < vertexCount; v++) {
				System.arraycopy(setJoints, v * JOINTS_PER_SET, joints, v * influences + set * JOINTS_PER_SET, JOINTS_PER_SET);
				System.arraycopy(setWeights, v * JOINTS_PER_SET, weights, v * influences + set * JOINTS_PER_SET, JOINTS_PER_SET);
			}
		}

		return GltfScene.Skinning.of(skin, influences, joints, weights);
	}

	private Material readMaterial(JsonObject primitive) throws IOException {
		if (!primitive.has("material")) {
			return Material.DEFAULT;
		}

		int index = primitive.get("material").getAsInt();
		JsonObject material = GltfJson.object(this.json, "materials", index);
		JsonObject pbr = material.getAsJsonObject("pbrMetallicRoughness");
		JsonObject extras = material.getAsJsonObject("extras");
		String wearMaps = extras != null && extras.has("wear_maps") ? extras.get("wear_maps").getAsString() : null;
		int color = Material.WHITE;
		int image = Material.NO_IMAGE;

		if (pbr != null && pbr.has("baseColorFactor")) {
			color = argb(GltfJson.floats(pbr.getAsJsonArray("baseColorFactor"), GltfAccessors.VEC4));
		}

		if (pbr != null && pbr.has("baseColorTexture")) {
			int texture = GltfJson.requiredInt(pbr.getAsJsonObject("baseColorTexture"), "index", "Material " + index + " baseColorTexture");
			image = GltfJson.intOr(GltfJson.object(this.json, "textures", texture), "source", Material.NO_IMAGE);
		}

		return new Material(image, color, wearMaps);
	}

	private static int argb(float[] rgba) {
		return channel(rgba[3]) << 24 | channel(rgba[0]) << 16 | channel(rgba[1]) << 8 | channel(rgba[2]);
	}

	private static int channel(float value) {
		return (int) Math.floor(value * CHANNEL_MAX) & CHANNEL_MASK;
	}

	private static int[] referencedVertices(int[] indices, int vertexCount) {
		boolean[] referenced = new boolean[vertexCount];

		for (int index : indices) {
			referenced[index] = true;
		}

		return IntStream.range(0, vertexCount).filter(v -> referenced[v]).toArray();
	}

	private static int[] remap(int[] indices, int[] sources, int vertexCount) {
		int[] target = new int[vertexCount];

		for (int i = 0; i < sources.length; i++) {
			target[sources[i]] = i;
		}

		int[] remapped = new int[indices.length];

		for (int i = 0; i < indices.length; i++) {
			remapped[i] = target[indices[i]];
		}

		return remapped;
	}

	private static float[] gather(float[] values, int[] sources, int width) {
		float[] result = new float[sources.length * width];

		for (int i = 0; i < sources.length; i++) {
			System.arraycopy(values, sources[i] * width, result, i * width, width);
		}

		return result;
	}

	private static int[] gather(int[] values, int[] sources, int width) {
		int[] result = new int[sources.length * width];

		for (int i = 0; i < sources.length; i++) {
			System.arraycopy(values, sources[i] * width, result, i * width, width);
		}

		return result;
	}

	private static GltfScene.Skinning gather(GltfScene.Skinning skinning, int[] sources) {
		int influences = skinning.influences();
		return new GltfScene.Skinning(
			skinning.skin(),
			influences,
			gather(skinning.joints(), sources, influences),
			gather(skinning.weights(), sources, influences),
			gather(skinning.bindings(), sources, GltfAccessors.SCALAR)
		);
	}

	private static float[] flatNormals(float[] positions) {
		float[] normals = new float[positions.length];
		Vector3f normal = new Vector3f();
		Vector3f edge = new Vector3f();

		for (int base = 0; base < positions.length; base += TRIANGLE * GltfAccessors.VEC3) {
			normal.set(positions[base + 3] - positions[base], positions[base + 4] - positions[base + 1], positions[base + 5] - positions[base + 2]);
			edge.set(positions[base + 6] - positions[base], positions[base + 7] - positions[base + 1], positions[base + 8] - positions[base + 2]);
			normal.cross(edge);

			if (normal.lengthSquared() > 0.0F) {
				normal.normalize();
			} else {
				normal.set(FLAT_FALLBACK_NORMAL);
			}

			for (int corner = 0; corner < TRIANGLE; corner++) {
				GltfScene.store(normal, normals, base + corner * GltfAccessors.VEC3);
			}
		}

		return normals;
	}
}
