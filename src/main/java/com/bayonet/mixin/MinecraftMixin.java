package com.bayonet.mixin;

import com.bayonet.animation.ViewmodelAnimator;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
abstract class MinecraftMixin {
	@Inject(method = "startAttack", at = @At("HEAD"))
	private void bayonet$captureCritical(CallbackInfoReturnable<Boolean> cir, @Share("criticalReady") LocalBooleanRef criticalReady) {
		criticalReady.set(ViewmodelAnimator.criticalReady(((Minecraft) (Object) this).player));
	}

	@Inject(
		method = "startAttack",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;)V",
			ordinal = 1
		)
	)
	private void bayonet$onAttack(CallbackInfoReturnable<Boolean> cir, @Share("criticalReady") LocalBooleanRef criticalReady) {
		ViewmodelAnimator.onAttack((Minecraft) (Object) this, criticalReady.get());
	}

	@WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;isDown()Z"))
	private boolean bayonet$holdForReload(KeyMapping key, Operation<Boolean> original) {
		return original.call(key) || ViewmodelAnimator.holdsDown((Minecraft) (Object) this, key);
	}
}
