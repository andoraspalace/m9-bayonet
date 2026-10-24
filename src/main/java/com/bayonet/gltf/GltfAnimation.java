package com.bayonet.gltf;

import java.util.Arrays;
import java.util.List;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public record GltfAnimation(String name, float duration, List<Channel> channels, List<Sound> sounds) {
	public GltfAnimation {
		channels = List.copyOf(channels);
		sounds = List.copyOf(sounds);
	}

	public record Sound(float time, String id) {
	}

	public enum Path {
		TRANSLATION, ROTATION, SCALE
	}

	public record Channel(int node, Path path, boolean step, float[] times, float[] values) {
	}

	public void sample(float time, Vector3f[] translations, Quaternionf[] rotations, Vector3f[] scales) {
		Vector3f nextVector = new Vector3f();
		Quaternionf nextRotation = new Quaternionf();

		for (Channel channel : this.channels) {
			float[] times = channel.times();
			int last = times.length - 1;
			int key;
			float blend;

			if (time <= times[0] || last == 0) {
				key = 0;
				blend = 0.0F;
			} else if (time >= times[last]) {
				key = last;
				blend = 0.0F;
			} else {
				int found = Arrays.binarySearch(times, time);
				key = found >= 0 ? found : -found - 2;
				blend = channel.step() ? 0.0F : (time - times[key]) / (times[key + 1] - times[key]);
			}

			float[] v = channel.values();
			Path path = channel.path();

			switch (path) {
				case TRANSLATION, SCALE -> {
					Vector3f target = (path == Path.TRANSLATION ? translations : scales)[channel.node()];
					target.set(v[key * 3], v[key * 3 + 1], v[key * 3 + 2]);

					if (blend > 0.0F) {
						target.lerp(nextVector.set(v[key * 3 + 3], v[key * 3 + 4], v[key * 3 + 5]), blend);
					}
				}
				case ROTATION -> {
					Quaternionf target = rotations[channel.node()];
					target.set(v[key * 4], v[key * 4 + 1], v[key * 4 + 2], v[key * 4 + 3]);

					if (blend > 0.0F) {
						target.slerp(nextRotation.set(v[key * 4 + 4], v[key * 4 + 5], v[key * 4 + 6], v[key * 4 + 7]), blend);
					}

					target.normalize();
				}
			}
		}
	}
}
