package com.bayonet.sound;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.GsonHelper;

record SoundBehaviour(boolean localOnly, DistanceCurve curve, float delaySeconds, List<String> children) {
	static final float NO_DELAY = 0.0F;
	static final SoundBehaviour DEFAULT = new SoundBehaviour(false, DistanceCurve.FLAT, NO_DELAY, List.of());
	private static final String LOCAL = "local";
	private static final String CURVE = "curve";
	private static final String DELAY = "delay";
	private static final String CHILDREN = "children";

	SoundBehaviour {
		children = List.copyOf(children);
	}

	static SoundBehaviour parse(JsonObject json) {
		List<String> children = new ArrayList<>();

		for (JsonElement child : GsonHelper.getAsJsonArray(json, CHILDREN)) {
			children.add(GsonHelper.convertToString(child, CHILDREN));
		}

		return new SoundBehaviour(
			GsonHelper.getAsBoolean(json, LOCAL),
			DistanceCurve.parse(GsonHelper.getAsJsonArray(json, CURVE)),
			Math.max(NO_DELAY, GsonHelper.getAsFloat(json, DELAY, NO_DELAY)),
			children
		);
	}
}
