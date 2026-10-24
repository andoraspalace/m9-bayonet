package com.bayonet.sound;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;

record DistanceCurve(List<Key> keys) {
	static final DistanceCurve FLAT = new DistanceCurve(List.of());
	private static final float FULL_VOLUME = 1.0F;

	DistanceCurve {
		keys = List.copyOf(keys);
	}

	static DistanceCurve parse(JsonArray json) {
		List<Key> keys = new ArrayList<>(json.size());

		for (JsonElement element : json) {
			Key key = Key.parse(GsonHelper.convertToJsonArray(element, "curve key"));

			if (!keys.isEmpty() && key.distance() < keys.getLast().distance()) {
				throw new JsonSyntaxException("Curve key distances must not decrease, got " + key.distance() + " after " + keys.getLast().distance());
			}

			keys.add(key);
		}

		return new DistanceCurve(keys);
	}

	float volumeAt(double inches) {
		if (this.keys.isEmpty()) {
			return FULL_VOLUME;
		}

		Key first = this.keys.getFirst();

		if (inches <= first.distance()) {
			return first.volume();
		}

		for (int k = 1; k < this.keys.size(); k++) {
			Key to = this.keys.get(k);

			if (inches <= to.distance()) {
				return Mth.clamp(hermite(this.keys.get(k - 1), to, inches), 0.0F, FULL_VOLUME);
			}
		}

		return this.keys.getLast().volume();
	}

	private static float hermite(Key from, Key to, double inches) {
		float span = to.distance() - from.distance();
		float t = (float) ((inches - from.distance()) / span);
		float t2 = t * t;
		float t3 = t2 * t;
		return (2.0F * t3 - 3.0F * t2 + 1.0F) * from.volume()
				+ (t3 - 2.0F * t2 + t) * span * from.outSlope()
				+ (3.0F * t2 - 2.0F * t3) * to.volume()
				+ (t3 - t2) * span * to.inSlope();
	}

	record Key(float distance, float volume, float inSlope, float outSlope) {
		private static final int VALUES = 4;

		static Key parse(JsonArray json) {
			if (json.size() != VALUES) {
				throw new JsonSyntaxException("Expected " + VALUES + " values per curve key, got " + json.size());
			}

			return new Key(
				GsonHelper.convertToFloat(json.get(0), "distance"),
				GsonHelper.convertToFloat(json.get(1), "volume"),
				GsonHelper.convertToFloat(json.get(2), "inSlope"),
				GsonHelper.convertToFloat(json.get(3), "outSlope")
			);
		}
	}
}
