package com.csmod.mixin;

import com.csmod.viewmodel.WeaponSounds;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundManager.class)
abstract class SoundManagerMixin {
	@Inject(method = "play", at = @At("HEAD"), cancellable = true)
	private void csmod$dropReplacedSound(SoundInstance instance, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
		if (WeaponSounds.isReplaced(instance)) {
			cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
		}
	}

	@Inject(method = "playDelayed", at = @At("HEAD"), cancellable = true)
	private void csmod$dropReplacedDelayedSound(SoundInstance instance, int delay, CallbackInfo ci) {
		if (WeaponSounds.isReplaced(instance)) {
			ci.cancel();
		}
	}
}
