package com.bayonet.mixin;

import com.bayonet.weapon.StatTrak;
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
		LivingEntity self = (LivingEntity) (Object) this;

		if (self.level().isClientSide()) {
			StatTrak.onDamage(self, source);
		}
	}

	@Inject(method = "handleEntityEvent", at = @At("HEAD"))
	private void bayonet$onDeath(byte id, CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;

		if (id == EntityEvent.DEATH && self.level().isClientSide()) {
			StatTrak.onDeath(self);
		}
	}
}
