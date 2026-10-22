package com.bayonet.mixin;

import com.bayonet.viewmodel.Skin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({AnvilScreen.class, AnvilMenu.class})
abstract class AnvilMixin {
	@WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getHoverName()Lnet/minecraft/network/chat/Component;"))
	private Component bayonet$vanillaName(ItemStack stack, Operation<Component> original) {
		if (Skin.of(stack) == null) {
			return original.call(stack);
		}

		Component custom = stack.getCustomName();
		return custom != null ? custom : stack.getItemName();
	}
}
