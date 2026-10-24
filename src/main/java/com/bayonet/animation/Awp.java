package com.bayonet.animation;

import com.bayonet.weapon.Weapon;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;

final class Awp {
	private static final int MIN_CHARGE_TICKS = 1;

	private Awp() {
	}

	static boolean usingMainHand(LivingEntity entity) {
		return entity.isUsingItem() && entity.getUsedItemHand() == InteractionHand.MAIN_HAND;
	}

	static boolean isMainHandAwp(InteractionHand hand, ItemStack stack) {
		return hand == InteractionHand.MAIN_HAND && Weapon.of(stack) == Weapon.AWP;
	}

	static boolean usingCrossbow(LivingEntity entity) {
		return entity.isUsingItem() && entity.getUseItem().getItem() instanceof CrossbowItem;
	}

	static boolean isCharging(LivingEntity user, ItemStack crossbow) {
		return user.isUsingItem() && isMainHandAwp(user.getUsedItemHand(), crossbow) && !CrossbowItem.isCharged(crossbow);
	}

	static float chargeProgress(LivingEntity user, ItemStack crossbow, float partialTicks) {
		float chargeTicks = Math.max(CrossbowItem.getChargeDuration(crossbow, user), MIN_CHARGE_TICKS);
		return Mth.clamp(user.getTicksUsingItem(partialTicks) / chargeTicks, 0.0F, 1.0F);
	}
}
