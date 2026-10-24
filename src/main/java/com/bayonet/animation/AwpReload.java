package com.bayonet.animation;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;

final class AwpReload {
	private static final int STALL_TICKS = 60;

	private boolean active;

	boolean active() {
		return this.active;
	}

	boolean holdsUse(LocalPlayer player) {
		return this.active && player.isUsingItem();
	}

	void cancel() {
		this.active = false;
	}

	void finish(MultiPlayerGameMode gameMode, LocalPlayer player, ItemStack awp) {
		if (this.active && (!Awp.usingMainHand(player) || CrossbowItem.isCharged(awp) || stalled(player, awp))) {
			this.active = false;

			if (Awp.usingMainHand(player)) {
				gameMode.releaseUsingItem(player);
			}
		}
	}

	boolean start(MultiPlayerGameMode gameMode, LocalPlayer player, ItemStack awp) {
		if (player.isUsingItem()
				|| player.isHandsBusy()
				|| gameMode.isDestroying()
				|| CrossbowItem.isCharged(awp)
				|| player.getProjectile(awp).isEmpty()) {
			return false;
		}

		gameMode.useItem(player, InteractionHand.MAIN_HAND);
		this.active = Awp.usingMainHand(player);
		return this.active;
	}

	private static boolean stalled(LocalPlayer player, ItemStack awp) {
		return player.getProjectile(awp).isEmpty() || player.getTicksUsingItem() > CrossbowItem.getChargeDuration(awp, player) + STALL_TICKS;
	}
}
