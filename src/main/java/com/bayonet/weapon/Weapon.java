package com.bayonet.weapon;

import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public enum Weapon {
	AWP, M9_BAYONET, BUTTERFLY;

	public boolean isKnife() {
		return this != AWP;
	}

	public static @Nullable Weapon of(ItemStack stack) {
		Skin skin = Skin.find(stack);
		return skin != null ? skin.weapon() : null;
	}
}
