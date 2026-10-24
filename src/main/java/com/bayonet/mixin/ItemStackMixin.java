package com.bayonet.mixin;

import com.bayonet.weapon.SkinText;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemStack.class)
abstract class ItemStackMixin {
	@ModifyReturnValue(method = "getHoverName", at = @At("RETURN"))
	private Component bayonet$skinName(Component original) {
		Component name = SkinText.displayName((ItemStack) (Object) this);
		return name != null ? name : original;
	}
}
