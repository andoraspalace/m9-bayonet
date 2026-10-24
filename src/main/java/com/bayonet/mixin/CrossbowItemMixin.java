package com.bayonet.mixin;

import com.bayonet.animation.ViewmodelAnimator;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CrossbowItem.class)
abstract class CrossbowItemMixin {
	@Inject(method = "use", at = @At("HEAD"))
	private void bayonet$onUse(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		if (level.isClientSide() && player instanceof LocalPlayer localPlayer) {
			ViewmodelAnimator.onCrossbowUse(localPlayer, hand);
		}
	}
}
