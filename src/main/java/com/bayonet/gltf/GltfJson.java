package com.bayonet.gltf;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;

final class GltfJson {
	private GltfJson() {
	}

	static JsonObject object(JsonObject root, String arrayName, int index) throws IOException {
		return element(array(root, arrayName), arrayName, index);
	}

	static JsonObject element(JsonArray array, String name, int index) throws IOException {
		if (index < 0 || index >= array.size()) {
			throw new IOException("Missing " + name + "[" + index + "]");
		}

		return array.get(index).getAsJsonObject();
	}

	static JsonArray array(JsonObject object, String name) {
		return object.has(name) ? object.getAsJsonArray(name) : new JsonArray();
	}

	static int intOr(JsonObject object, String name, int fallback) {
		return object.has(name) ? object.get(name).getAsInt() : fallback;
	}

	static int requiredInt(JsonObject object, String name, String owner) throws IOException {
		return required(object, name, owner).get(name).getAsInt();
	}

	static String requiredString(JsonObject object, String name, String owner) throws IOException {
		return required(object, name, owner).get(name).getAsString();
	}

	static float[] floats(JsonArray array, int size) {
		float[] result = new float[size];

		for (int i = 0; i < Math.min(size, array.size()); i++) {
			result[i] = array.get(i).getAsFloat();
		}

		return result;
	}

	private static JsonObject required(JsonObject object, String name, String owner) throws IOException {
		if (!object.has(name)) {
			throw new IOException(owner + " has no '" + name + "'");
		}

		return object;
	}
}
