package com.bayonet.animation;

import com.bayonet.config.Settings;
import com.bayonet.gltf.GltfAnimation;
import com.bayonet.sound.WeaponSounds;
import com.bayonet.viewmodel.Pose;
import com.bayonet.viewmodel.Viewmodel;
import com.bayonet.viewmodel.Viewmodels;
import com.bayonet.viewmodel.WeaponRig;
import com.bayonet.weapon.Skin;
import com.bayonet.weapon.StatTrak;
import com.bayonet.weapon.Weapon;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

final class LocalViewmodel {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final float BLEND_SECONDS = 0.12F;
	private static final int NO_SLOT = -1;

	private final ClipPlayer clips = new ClipPlayer();
	private @Nullable Held held;
	private int slot = NO_SLOT;
	private @Nullable Poses poses;
	private boolean blending;
	private double blendStart;
	private @Nullable String missingClip;
	private float framePartialTick;

	@Nullable Weapon weapon() {
		Held held = this.held;
		return held != null ? held.weapon() : null;
	}

	boolean followsCharge() {
		return this.clips.followsCharge();
	}

	void startTick() {
		this.framePartialTick = 0.0F;
	}

	boolean select(@Nullable LocalPlayer player) {
		LocalPlayer present = player != null && player.connection.hasClientLoaded() ? player : null;
		ItemStack stack = present != null && !present.isSpectator() ? present.getMainHandItem() : ItemStack.EMPTY;
		Viewmodel viewmodel = Viewmodels.forStack(stack);
		int heldSlot = present != null ? present.getInventory().getSelectedSlot() : NO_SLOT;
		Held held = this.held;
		Viewmodel selected = held != null ? held.viewmodel() : null;

		if (viewmodel == selected && heldSlot == this.slot) {
			return false;
		}

		Weapon weapon = Weapon.of(stack);
		Held next = viewmodel != null && weapon != null ? new Held(weapon, WeaponRig.of(weapon), viewmodel) : null;
		this.switchTo(next, heldSlot);

		if (next != null) {
			this.play(next.tuning().draw(), ClipRole.ACTION);
		}

		return true;
	}

	void reset() {
		this.switchTo(null, NO_SLOT);
	}

	void play(String name, ClipRole role) {
		Held held = this.held;

		if (held == null) {
			return;
		}

		GltfAnimation next = held.viewmodel().clip(name);

		if (next == null) {
			if (!name.equals(this.missingClip)) {
				LOGGER.warn("No clip '{}' for {}", name, held.weapon());
				this.missingClip = name;
			}

			return;
		}

		double now = this.now();
		Poses poses = this.poses;

		if (poses != null) {
			poses.blendFrom().set(poses.current());
			this.blendStart = now;
			this.blending = true;
		}

		this.clips.start(next, role, now);
		this.clips.playSoundsUntil(0.0F, WeaponSounds::play);
	}

	void playIdle() {
		Held held = this.held;

		if (held != null) {
			this.play(held.tuning().idle(), ClipRole.IDLE);
		}
	}

	void playInspect() {
		Held held = this.held;

		if (held != null) {
			this.play(held.tuning().inspect(), ClipRole.ACTION);
		}
	}

	void tickSounds(LocalPlayer player) {
		Held held = this.held;

		if (held != null) {
			this.clips.playSoundsUntil(this.clipTime(player, held), WeaponSounds::play);
		}
	}

	boolean submit(
		LocalPlayer player, AbstractClientPlayer avatar, float partialTicks, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords
	) {
		Held held = this.held;
		Pose pose = held != null ? this.pose(player, held, partialTicks) : null;

		if (held == null || pose == null) {
			return false;
		}

		ItemStack stack = player.getMainHandItem();
		Skin.Look look = Skin.of(stack);
		boolean glint = stack.hasFoil() && Settings.glint();
		held.viewmodel().submit(
			pose, avatar, poseStack, submitNodeCollector, lightCoords, Settings.arms(), Skin.wearOf(look), glint, StatTrak.displayedKills(look)
		);
		return true;
	}

	@Nullable WeaponTurn turn(LocalPlayer player, float partialTicks) {
		Held held = this.held;
		GltfAnimation clip = this.clips.clip();

		if (held == null || clip == null || !this.clips.role().turnsWeapon()) {
			return null;
		}

		double time = this.clips.time(ClipPlayer.now(partialTicks), () -> Awp.chargeProgress(player, player.getMainHandItem(), partialTicks));
		return this.clips.ended(time) ? null : new WeaponTurn(held.weapon(), held.viewmodel(), clip, (float) time);
	}

	private @Nullable Pose pose(LocalPlayer player, Held held, float partialTicks) {
		this.framePartialTick = partialTicks;
		float time = this.clipTime(player, held);
		GltfAnimation clip = this.clips.clip();

		if (clip == null) {
			return null;
		}

		Poses poses = this.poses;

		if (poses == null) {
			poses = new Poses(held.viewmodel().newPose(), held.viewmodel().newPose());
			this.poses = poses;
		}

		poses.current().sample(clip, time);

		if (this.blending) {
			float weight = Mth.clamp((float) (this.now() - this.blendStart) / BLEND_SECONDS, 0.0F, 1.0F);

			if (weight < 1.0F) {
				poses.current().blendFrom(poses.blendFrom(), smoothstep(weight));
			} else {
				this.blending = false;
			}
		}

		return poses.current();
	}

	private void switchTo(@Nullable Held held, int slot) {
		this.held = held;
		this.slot = slot;
		this.clips.stop();
		this.poses = null;
		this.blending = false;
		this.missingClip = null;
	}

	private float clipTime(LocalPlayer player, Held held) {
		if (this.clips.clip() == null) {
			this.play(held.tuning().idle(), ClipRole.IDLE);
		}

		GltfAnimation clip = this.clips.clip();

		if (clip == null) {
			return 0.0F;
		}

		double time = this.clips.time(this.now(), () -> Awp.chargeProgress(player, player.getMainHandItem(), this.framePartialTick));

		if (!this.clips.ended(time)) {
			return (float) time;
		} else if (!this.clips.role().loops()) {
			this.clips.playSoundsUntil(clip.duration(), WeaponSounds::play);
			this.play(held.tuning().idle(), ClipRole.IDLE);
			return 0.0F;
		} else if (clip.duration() > 0.0F) {
			return (float) (time % clip.duration());
		} else {
			return 0.0F;
		}
	}

	private double now() {
		return ClipPlayer.now(this.framePartialTick);
	}

	private static float smoothstep(float t) {
		return t * t * (3.0F - 2.0F * t);
	}

	private record Held(Weapon weapon, WeaponRig tuning, Viewmodel viewmodel) {
	}

	private record Poses(Pose current, Pose blendFrom) {
	}
}
