package com.bayonet.animation;

import com.bayonet.weapon.Weapon;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
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

	public static FirstPersonHandsAndItemsRenderState.HandRenderSelection firstPersonHands(
		LocalPlayer player, FirstPersonHandsAndItemsRenderState.HandRenderSelection selection
	) {
		if (isHiddenBy(player.getMainHandItem())
				|| selection == FirstPersonHandsAndItemsRenderState.HandRenderSelection.RENDER_OFF_HAND_ONLY && isHiddenInFirstPerson(player.getOffhandItem())) {
			return FirstPersonHandsAndItemsRenderState.HandRenderSelection.RENDER_MAIN_HAND_ONLY;
		}

		return selection;
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
