package com.bayonet.mixin;

import com.bayonet.viewmodel.StatTrak;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
	@Inject(method = "handleDamageEvent", at = @At("HEAD"))
	private void bayonet$onDamage(DamageSource source, CallbackInfo ci) {
		if (((LivingEntity) (Object) this).level().isClientSide()) {
			StatTrak.onDamage((LivingEntity) (Object) this, source);
		}
	}

	@Inject(method = "handleEntityEvent", at = @At("HEAD"))
	private void bayonet$onDeath(byte id, CallbackInfo ci) {
		if (id == EntityEvent.DEATH && ((LivingEntity) (Object) this).level().isClientSide()) {
			StatTrak.onDeath((LivingEntity) (Object) this);
		}
	}
}
