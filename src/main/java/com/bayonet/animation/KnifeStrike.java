package com.bayonet.animation;

import com.bayonet.sound.WeaponSoundIds;
import com.bayonet.viewmodel.WeaponRig;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

record KnifeStrike(WeaponRig.Strike strike, boolean heavy, @Nullable String sound) {
	private static final float ATTACK_STRENGTH_PARTIAL_TICK = 0.5F;
	private static final float FULL_STRENGTH = 0.9F;
	private static final double BACKSTAB_ALIGNMENT = 0.5;

	static boolean criticalReady(LocalPlayer attacker) {
		return isFalling(attacker)
				&& attacker.getAttackStrengthScale(ATTACK_STRENGTH_PARTIAL_TICK) > FULL_STRENGTH
				&& !attacker.isMobilityRestricted()
				&& !attacker.isPassenger()
				&& !attacker.isSprinting();
	}

	static KnifeStrike of(LocalPlayer attacker, @Nullable HitResult hit, boolean criticalReady) {
		if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity target && !(target instanceof ArmorStand)) {
			if (isBehind(attacker, target)) {
				return new KnifeStrike(
					WeaponRig.Strike.BACKSTAB, criticalReady, criticalReady ? WeaponSoundIds.KNIFE_BACKSTAB_HEAVY : WeaponSoundIds.KNIFE_BACKSTAB_LIGHT
				);
			}

			return new KnifeStrike(WeaponRig.Strike.HIT, criticalReady, criticalReady ? WeaponSoundIds.KNIFE_HIT_HEAVY : WeaponSoundIds.KNIFE_HIT_LIGHT);
		}

		boolean falling = isFalling(attacker);

		if (hit != null && hit.getType() != HitResult.Type.MISS) {
			return new KnifeStrike(WeaponRig.Strike.HIT, falling, WeaponSoundIds.KNIFE_HIT_WALL);
		}

		return new KnifeStrike(WeaponRig.Strike.MISS, falling, null);
	}

	String clip(boolean alternate) {
		return this.strike.clip(this.heavy, alternate);
	}

	private static boolean isFalling(LocalPlayer attacker) {
		return attacker.fallDistance > 0.0 && !attacker.onGround() && !attacker.onClimbable() && !attacker.isInWater();
	}

	private static boolean isBehind(LocalPlayer attacker, LivingEntity target) {
		Vec3 attackerFacing = Vec3.directionFromRotation(0.0F, attacker.getYRot());
		Vec3 targetFacing = Vec3.directionFromRotation(0.0F, target.yBodyRot);
		return attackerFacing.dot(targetFacing) > BACKSTAB_ALIGNMENT;
	}
}
