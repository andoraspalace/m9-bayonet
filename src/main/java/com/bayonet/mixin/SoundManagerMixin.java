package com.bayonet.mixin;

import com.bayonet.sound.DelayedSounds;
import com.bayonet.sound.VanillaSoundFilter;
import net.minecraft.client.Camera;
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
	private void bayonet$interceptSound(SoundInstance instance, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
		if (VanillaSoundFilter.intercept(instance)) {
			cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
		}
	}

	@Inject(method = "playDelayed", at = @At("HEAD"), cancellable = true)
	private void bayonet$interceptDelayedSound(SoundInstance instance, int delay, CallbackInfo ci) {
		if (VanillaSoundFilter.intercept(instance)) {
			ci.cancel();
		}
	}

	@Inject(method = "updateSource", at = @At("HEAD"))
	private void bayonet$playDelayedWeaponSounds(Camera camera, CallbackInfo ci) {
		DelayedSounds.playDue((SoundManager) (Object) this);
	}

	@Inject(method = "stop()V", at = @At("HEAD"))
	private void bayonet$dropDelayedWeaponSounds(CallbackInfo ci) {
		DelayedSounds.clear();
	}
}
