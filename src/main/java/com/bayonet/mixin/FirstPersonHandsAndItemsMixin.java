package com.bayonet.mixin;

import com.bayonet.animation.Offhand;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FirstPersonHandsAndItems.class)
abstract class FirstPersonHandsAndItemsMixin {
	@ModifyReturnValue(method = "evaluateWhichHandsToRender", at = @At("RETURN"))
	private static FirstPersonHandsAndItemsRenderState.HandRenderSelection bayonet$hideOffhand(
		FirstPersonHandsAndItemsRenderState.HandRenderSelection selection, @Local(argsOnly = true) LocalPlayer player
	) {
		return Offhand.firstPersonHands(player, selection);
	}
}
