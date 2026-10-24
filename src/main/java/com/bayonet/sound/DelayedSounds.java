package com.bayonet.sound;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.util.Util;

public final class DelayedSounds {
	private static final double NANOS_PER_SECOND = 1.0E9;
	private static final List<Pending> PENDING = new ArrayList<>();

	private DelayedSounds() {
	}

	public static void playDue(SoundManager sounds) {
		if (PENDING.isEmpty() || Minecraft.getInstance().isPaused()) {
			return;
		}

		long now = Util.getNanos();
		Iterator<Pending> iterator = PENDING.iterator();

		while (iterator.hasNext()) {
			Pending pending = iterator.next();

			if (pending.due() <= now) {
				iterator.remove();
				sounds.play(pending.sound());
			}
		}
	}

	public static void clear() {
		PENDING.clear();
	}

	static void schedule(SoundInstance sound, float delaySeconds) {
		PENDING.add(new Pending(sound, Util.getNanos() + Math.round(delaySeconds * NANOS_PER_SECOND)));
	}

	private record Pending(SoundInstance sound, long due) {
	}
}
