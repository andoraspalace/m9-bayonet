package com.bayonet.mixin;

import com.bayonet.viewmodel.ViewmodelAnimator;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.KeyMapping;
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
	private void bayonet$onAttack(CallbackInfoReturnable<Boolean> cir) {
		ViewmodelAnimator.onAttack((Minecraft) (Object) this);
	}

	@WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;isDown()Z"))
	private boolean bayonet$holdForReload(KeyMapping key, Operation<Boolean> original) {
		Minecraft minecraft = (Minecraft) (Object) this;
		return original.call(key) || key == minecraft.options.keyUse && minecraft.player != null && ViewmodelAnimator.holdsUse(minecraft.player);
	}
}
