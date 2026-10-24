package com.bayonet.sound;

import com.bayonet.util.ModIds;
import com.bayonet.util.SourceUnits;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class WeaponSounds {
	private static final double SOURCE_HEIGHT_INCHES = 60.0;
	private static final double SOURCE_HEIGHT = SOURCE_HEIGHT_INCHES / SourceUnits.INCHES_PER_BLOCK;
	private static final String REMOTE_SUFFIX = ".remote";
	private static final float PITCH = 1.0F;
	private static final float SILENT = 0.001F;
	private static final int MAX_CHILD_DEPTH = 4;

	private WeaponSounds() {
	}

	public static void play(String id) {
		play(id, null, 0, SoundBehaviour.NO_DELAY);
	}

	public static void playAt(String id, Entity source) {
		play(id, source.position().add(0.0, SOURCE_HEIGHT, 0.0), 0, SoundBehaviour.NO_DELAY);
	}

	private static void play(String id, @Nullable Vec3 position, int depth, float inheritedDelay) {
		SoundBehaviour behaviour = SoundBehaviours.get(id);
		Identifier event = Identifier.tryBuild(ModIds.NAMESPACE, id);

		if (event == null || position != null && behaviour.localOnly()) {
			return;
		}

		float delay = inheritedDelay + behaviour.delaySeconds();
		start(event, behaviour.curve(), position, delay);

		if (depth < MAX_CHILD_DEPTH) {
			for (String child : behaviour.children()) {
				play(child, position, depth + 1, delay);
			}
		}
	}

	private static void start(Identifier event, DistanceCurve curve, @Nullable Vec3 position, float delay) {
		Minecraft minecraft = Minecraft.getInstance();
		double inches = position != null ? minecraft.gameRenderer.mainCamera().position().distanceTo(position) * SourceUnits.INCHES_PER_BLOCK : 0.0;
		float volume = curve.volumeAt(inches);

		if (volume <= SILENT) {
			return;
		}

		SoundManager sounds = minecraft.getSoundManager();
		Identifier sound = position != null ? remoteVariant(sounds, event) : event;
		Vec3 origin = position != null ? position : Vec3.ZERO;
		SoundInstance instance = new SimpleSoundInstance(
			sound, SoundSource.PLAYERS, volume, PITCH, SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE,
			origin.x, origin.y, origin.z, position == null
		);

		if (delay > SoundBehaviour.NO_DELAY) {
			DelayedSounds.schedule(instance, delay);
		} else {
			sounds.play(instance);
		}
	}

	private static Identifier remoteVariant(SoundManager sounds, Identifier event) {
		Identifier mono = event.withSuffix(REMOTE_SUFFIX);
		return sounds.getSoundEvent(mono) != null ? mono : event;
	}
}
