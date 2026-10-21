package com.csmod.viewmodel;

import com.csmod.CsModClient;
import com.csmod.gltf.GltfAnimation;
import com.csmod.viewmodel.Viewmodels.Weapon;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class ViewmodelAnimator {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final float BLEND_SECONDS = 0.12F;
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(CsModClient.id("weapons"));
	public static final KeyMapping INSPECT_KEY = new KeyMapping("key.csmod.inspect", InputConstants.KEY_G, CATEGORY);

	private static @Nullable Weapon weapon;
	private static @Nullable Viewmodel viewmodel;
	private static int slot = -1;
	private static @Nullable GltfAnimation clip;
	private static float clipStart;
	private static boolean followCharge;
	private static float soundTime;
	private static int attacks;
	private static @Nullable Pose current;
	private static @Nullable Pose blendFrom;
	private static float blendStart;
	private static long ticks;
	private static float partialTick;

	private ViewmodelAnimator() {
	}

	public static void reset() {
		weapon = null;
		viewmodel = null;
		slot = -1;
		clip = null;
		current = null;
		blendFrom = null;
	}

	public static @Nullable Weapon heldWeapon() {
		return weapon;
	}

	private static float now() {
		return (ticks + partialTick) / 20.0F;
	}

	public static void tick(Minecraft minecraft) {
		if (!minecraft.isPaused()) {
			ticks++;
		}

		partialTick = 0.0F;
		boolean inspect = false;

		while (INSPECT_KEY.consumeClick()) {
			inspect = true;
		}

		LocalPlayer player = minecraft.player;

		if (selectHeld(player) || weapon == null || player == null) {
			return;
		}

		if (weapon == Weapon.AWP) {
			boolean charging = !CrossbowItem.isCharged(player.getMainHandItem())
					&& player.isUsingItem()
					&& player.getUsedItemHand() == InteractionHand.MAIN_HAND;

			if (charging != followCharge) {
				play(charging ? "reload" : weapon.idle, charging);
			}
		}

		if (inspect && !followCharge) {
			play(weapon.inspect, false);
		}

		playSoundsUntil(clipTime(player, 0.0F));
	}

	private static boolean selectHeld(@Nullable LocalPlayer player) {
		ItemStack stack = player != null && !player.isSpectator() ? player.getMainHandItem() : ItemStack.EMPTY;
		Weapon held = Weapon.of(stack);
		Viewmodel heldViewmodel = Viewmodels.get(held);
		int heldSlot = player != null ? player.getInventory().getSelectedSlot() : -1;

		if (heldViewmodel == viewmodel && heldSlot == slot) {
			return false;
		}

		weapon = heldViewmodel != null ? held : null;
		viewmodel = heldViewmodel;
		slot = heldSlot;
		clip = null;
		current = null;
		blendFrom = null;
		followCharge = false;

		if (weapon != null) {
			play(weapon.draw, false);
		}

		return true;
	}

	public static void onShoot(Minecraft minecraft) {
		selectHeld(minecraft.player);

		if (weapon == Weapon.AWP) {
			play("shoot1", false);
			WeaponSounds.play("weapon_awp.single");
		}
	}

	public static void onAttack(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		selectHeld(player);

		if (weapon != Weapon.M9_BAYONET || player == null) {
			return;
		}

		boolean heavy = player.fallDistance > 0.0 && !player.onGround() && !player.onClimbable() && !player.isInWater();
		boolean alternate = (attacks++ & 1) == 1;
		HitResult hit = minecraft.hitResult;

		if (hit instanceof EntityHitResult entityHit) {
			if (isBehind(player, entityHit.getEntity())) {
				play(heavy ? "heavy_backstab" : alternate ? "light_backstab2" : "light_backstab", false);
				WeaponSounds.play(heavy ? "weapon_knife.hit.heavy.backstab.flesh" : "weapon_knife.hit.light.backstab.flesh");
			} else {
				play(heavy ? "heavy_hit1" : alternate ? "light_hit2" : "light_hit1", false);
				WeaponSounds.play(heavy ? "weapon_knife.hit.heavy.flesh" : "weapon_knife.hit.light.flesh");
			}
		} else if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
			play(heavy ? "heavy_hit1" : alternate ? "light_hit2" : "light_hit1", false);
			WeaponSounds.play("weapon_knife.hitwall");
		} else {
			play(heavy ? "heavy_miss1" : alternate ? "light_miss2" : "light_miss1", false);
		}
	}

	private static boolean isBehind(LocalPlayer attacker, Entity target) {
		float targetYaw = target instanceof LivingEntity living ? living.yBodyRot : target.getYRot();
		return Vec3.directionFromRotation(0.0F, attacker.getYRot()).dot(Vec3.directionFromRotation(0.0F, targetYaw)) > 0.5;
	}

	private static void play(String name, boolean followsCharge) {
		GltfAnimation next = viewmodel != null ? viewmodel.clip(name) : null;

		if (next == null) {
			LOGGER.warn("No clip '{}' for {}", name, weapon);
			return;
		}

		if (current != null) {
			if (blendFrom == null) {
				blendFrom = new Pose(viewmodel.rig);
			}

			blendFrom.set(current);
			blendStart = now();
		}

		clip = next;
		clipStart = now();
		followCharge = followsCharge;
		soundTime = -1.0F;
		playSoundsUntil(0.0F);
	}

	private static void playSoundsUntil(float time) {
		for (GltfAnimation.Sound sound : clip.sounds()) {
			if (sound.time() > soundTime && sound.time() <= time) {
				WeaponSounds.play(sound.id());
			}
		}

		soundTime = time;
	}

	private static float clipTime(LocalPlayer player, float partialTicks) {
		if (clip == null) {
			play(weapon.idle, false);
		}

		if (followCharge) {
			int chargeTicks = CrossbowItem.getChargeDuration(player.getMainHandItem(), player);
			return clip.duration() * Mth.clamp(player.getTicksUsingItem(partialTicks) / Math.max(chargeTicks, 1), 0.0F, 1.0F);
		}

		float time = now() - clipStart;

		if (time <= clip.duration()) {
			return time;
		} else if (!clip.name().equals(weapon.idle)) {
			play(weapon.idle, false);
			return 0.0F;
		} else if (clip.duration() > 0.0F) {
			soundTime = -1.0F;
			return time % clip.duration();
		} else {
			return 0.0F;
		}
	}

	static @Nullable Quaternionf weaponTurn(float partialTicks) {
		LocalPlayer player = Minecraft.getInstance().player;

		if (viewmodel == null || weapon == null || player == null) {
			return null;
		}

		partialTick = partialTicks;
		float time = clipTime(player, partialTicks);
		String name = clip.name();

		if (name.equals(weapon.idle) || name.contains("hit") || name.contains("miss") || name.contains("backstab")) {
			return null;
		}

		return viewmodel.weaponTurn(clip, time);
	}

	public static boolean submit(
		ItemStack visibleItem, AbstractClientPlayer renderedPlayer, float partialTicks, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords
	) {
		LocalPlayer player = Minecraft.getInstance().player;

		if (viewmodel == null || weapon == null || player == null) {
			return Viewmodels.get(Weapon.of(visibleItem)) != null;
		}

		partialTick = partialTicks;
		float time = clipTime(player, partialTicks);

		if (current == null) {
			current = new Pose(viewmodel.rig);
		}

		current.sample(viewmodel.rig, clip, time);

		if (blendFrom != null) {
			float weight = Mth.clamp((now() - blendStart) / BLEND_SECONDS, 0.0F, 1.0F);

			if (weight < 1.0F) {
				current.blendFrom(blendFrom, weight * weight * (3.0F - 2.0F * weight));
			} else {
				blendFrom = null;
			}
		}

		viewmodel.submit(current, renderedPlayer, poseStack, submitNodeCollector, lightCoords, player.getMainHandItem().hasFoil());
		return true;
	}
}
