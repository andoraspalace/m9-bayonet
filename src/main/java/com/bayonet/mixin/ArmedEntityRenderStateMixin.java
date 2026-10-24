package com.bayonet.mixin;

import com.bayonet.animation.Offhand;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ArmedEntityRenderState.class)
abstract class ArmedEntityRenderStateMixin {
	@Inject(method = "extractArmedEntityRenderState", at = @At("TAIL"))
	private static void bayonet$hideOffhand(
		LivingEntity entity, ArmedEntityRenderState state, ItemModelResolver itemModelResolver, float partialTicks, CallbackInfo ci
	) {
		Offhand.hide(state);
	}
}
