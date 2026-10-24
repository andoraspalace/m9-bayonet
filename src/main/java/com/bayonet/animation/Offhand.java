package com.bayonet.animation;

import com.bayonet.weapon.Weapon;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

public final class Offhand {
	private Offhand() {
	}

	static boolean isHiddenBy(ItemStack mainHand) {
		return Weapon.of(mainHand) != null;
	}

	public static boolean isHiddenInFirstPerson(ItemStack offhand) {
		return Weapon.of(offhand) != null;
	}

	public static boolean firstPersonMainHandOnly(LocalPlayer player, boolean offHandOnly) {
		return isHiddenBy(player.getMainHandItem()) || offHandOnly && isHiddenInFirstPerson(player.getOffhandItem());
	}

	public static void hide(ArmedEntityRenderState state) {
		if (!isHiddenBy(state.getMainHandItemStack())) {
			return;
		}

		if (state.mainArm == HumanoidArm.RIGHT) {
			state.leftHandItemState.clear();
			state.leftHandItemStack = ItemStack.EMPTY;
		} else {
			state.rightHandItemState.clear();
			state.rightHandItemStack = ItemStack.EMPTY;
		}
	}
}
