package com.bayonet.animation;

import com.bayonet.gltf.GltfAnimation;
import com.bayonet.util.ClientTicks;
import java.util.function.Consumer;
import net.minecraft.SharedConstants;
import org.jspecify.annotations.Nullable;

final class ClipPlayer {
	private static final float BEFORE_START = -1.0F;

	private @Nullable GltfAnimation clip;
	private ClipRole role = ClipRole.IDLE;
	private double start;
	private float soundTime = BEFORE_START;

	static double now() {
		return now(0.0F);
	}

	static double now(float partialTick) {
		return (ClientTicks.ticks() + (double) partialTick) / SharedConstants.TICKS_PER_SECOND;
	}

	static float chargeTime(GltfAnimation clip, float chargeProgress) {
		return clip.duration() * chargeProgress;
	}

	@Nullable GltfAnimation clip() {
		return this.clip;
	}

	ClipRole role() {
		return this.role;
	}

	boolean followsCharge() {
		return this.clip != null && this.role.followsCharge();
	}

	void start(GltfAnimation clip, ClipRole role, double now) {
		this.clip = clip;
		this.role = role;
		this.start = now;
		this.soundTime = BEFORE_START;
	}

	void stop() {
		this.clip = null;
	}

	double time(double now, ChargeProgress chargeProgress) {
		GltfAnimation clip = this.clip;
		return clip != null && this.role.followsCharge() ? chargeTime(clip, chargeProgress.get()) : now - this.start;
	}

	boolean ended(double time) {
		GltfAnimation clip = this.clip;
		return clip != null && !this.role.followsCharge() && time > clip.duration();
	}

	void playSoundsUntil(float time, Consumer<String> sink) {
		GltfAnimation clip = this.clip;

		if (clip == null) {
			return;
		}

		if (time < this.soundTime) {
			this.soundTime = BEFORE_START;
		}

		for (GltfAnimation.Sound sound : clip.sounds()) {
			if (sound.time() > this.soundTime && sound.time() <= time) {
				sink.accept(sound.id());
			}
		}

		this.soundTime = time;
	}

	@FunctionalInterface
	interface ChargeProgress {
		float get();
	}
}
