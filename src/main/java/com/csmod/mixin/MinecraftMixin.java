package com.csmod.mixin;

import com.csmod.viewmodel.ViewmodelAnimator;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
abstract class MinecraftMixin {
	@Inject(
		method = "startAttack",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/component/SwingAnimation;Z)Z"
		)
	)
	private void csmod$onAttack(CallbackInfoReturnable<Boolean> cir) {
		ViewmodelAnimator.onAttack((Minecraft) (Object) this);
	}
}
