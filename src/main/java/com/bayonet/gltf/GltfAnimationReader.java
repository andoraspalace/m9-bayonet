package com.bayonet.gltf;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class GltfAnimationReader {
	private static final int CUBIC_SPLINE_TERMS = 3;

	private GltfAnimationReader() {
	}

	static List<GltfAnimation> read(JsonObject json, GltfAccessors accessors) throws IOException {
		List<GltfAnimation> animations = new ArrayList<>();
		JsonArray array = GltfJson.array(json, "animations");
		int nodeCount = GltfJson.array(json, "nodes").size();

		for (int a = 0; a < array.size(); a++) {
			animations.add(readAnimation(array.get(a).getAsJsonObject(), a, nodeCount, accessors));
		}

		return animations;
	}

	private static GltfAnimation readAnimation(JsonObject animation, int index, int nodeCount, GltfAccessors accessors) throws IOException {
		String owner = "Animation " + index;
		JsonArray samplers = GltfJson.array(animation, "samplers");
		List<GltfAnimation.Channel> channels = new ArrayList<>();
		float duration = 0.0F;

		for (JsonElement element : GltfJson.array(animation, "channels")) {
			JsonObject channel = element.getAsJsonObject();
			JsonObject target = channel.getAsJsonObject("target");

			if (target == null || !target.has("node")) {
				continue;
			}

			GltfAnimation.Path path = switch (GltfJson.requiredString(target, "path", owner + " channel target")) {
				case "translation" -> GltfAnimation.Path.TRANSLATION;
				case "rotation" -> GltfAnimation.Path.ROTATION;
				case "scale" -> GltfAnimation.Path.SCALE;
				default -> null;
			};

			if (path == null) {
				continue;
			}

			int samplerIndex = GltfJson.requiredInt(channel, "sampler", owner + " channel");
			JsonObject sampler = GltfJson.element(samplers, "animations[" + index + "].samplers", samplerIndex);
			String interpolation = sampler.has("interpolation") ? sampler.get("interpolation").getAsString() : "LINEAR";
			float[] times = accessors.floats(GltfJson.requiredInt(sampler, "input", owner + " sampler"));
			float[] values = accessors.floats(GltfJson.requiredInt(sampler, "output", owner + " sampler"));

			if (times.length == 0) {
				continue;
			}

			if ("CUBICSPLINE".equals(interpolation)) {
				values = cubicSplineValues(values, times.length);
			}

			int node = target.get("node").getAsInt();
			int components = path == GltfAnimation.Path.ROTATION ? GltfAccessors.VEC4 : GltfAccessors.VEC3;

			if (node < 0 || node >= nodeCount || values.length < times.length * components) {
				throw new IOException(owner + " has a channel with node " + node + " or too few values");
			}

			channels.add(new GltfAnimation.Channel(node, path, "STEP".equals(interpolation), times, values));
			duration = Math.max(duration, times[times.length - 1]);
		}

		String name = animation.has("name") ? animation.get("name").getAsString() : "animation" + index;
		return new GltfAnimation(name, duration, channels, readSounds(animation));
	}

	private static List<GltfAnimation.Sound> readSounds(JsonObject animation) {
		List<GltfAnimation.Sound> sounds = new ArrayList<>();
		JsonObject extras = animation.getAsJsonObject("extras");

		for (JsonElement sound : extras != null ? GltfJson.array(extras, "sounds") : new JsonArray()) {
			JsonArray entry = sound.getAsJsonArray();
			sounds.add(new GltfAnimation.Sound(entry.get(0).getAsFloat(), entry.get(1).getAsString()));
		}

		sounds.sort(Comparator.comparingDouble(GltfAnimation.Sound::time));
		return sounds;
	}

	private static float[] cubicSplineValues(float[] values, int keys) {
		int components = values.length / (keys * CUBIC_SPLINE_TERMS);
		float[] result = new float[keys * components];

		for (int k = 0; k < keys; k++) {
			System.arraycopy(values, (k * CUBIC_SPLINE_TERMS + 1) * components, result, k * components, components);
		}

		return result;
	}
}
