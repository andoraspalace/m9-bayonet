package com.bayonet.mixin;

import com.bayonet.viewmodel.Skin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
abstract class ItemStackMixin {
	@Inject(method = "getHoverName", at = @At("HEAD"), cancellable = true)
	private void bayonet$skinName(CallbackInfoReturnable<Component> cir) {
		Component name = Skin.displayName((ItemStack) (Object) this);

		if (name != null) {
			cir.setReturnValue(name);
		}
	}

	@WrapOperation(
		method = {"getStyledHoverName", "getDisplayName"},
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/network/chat/MutableComponent;withStyle(Lnet/minecraft/ChatFormatting;)Lnet/minecraft/network/chat/MutableComponent;"
		)
	)
	private MutableComponent bayonet$noItalics(MutableComponent component, ChatFormatting format, Operation<MutableComponent> original) {
		if (format == ChatFormatting.ITALIC && Skin.of((ItemStack) (Object) this) != null) {
			return component;
		}

		return original.call(component, format);
	}
}
