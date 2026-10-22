package com.bayonet.mixin;

import com.bayonet.viewmodel.Skin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Hud.class)
abstract class HudMixin {
	@Shadow
	private ItemStack lastToolHighlight;

	@WrapOperation(
		method = "extractSelectedItemName",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/network/chat/MutableComponent;withStyle(Lnet/minecraft/ChatFormatting;)Lnet/minecraft/network/chat/MutableComponent;"
		)
	)
	private MutableComponent bayonet$noItalics(MutableComponent component, ChatFormatting format, Operation<MutableComponent> original) {
		if (format == ChatFormatting.ITALIC && Skin.of(this.lastToolHighlight) != null) {
			return component;
		}

		return original.call(component, format);
	}
}
