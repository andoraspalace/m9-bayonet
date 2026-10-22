package com.bayonet.mixin;

import com.bayonet.viewmodel.Viewmodels;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
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
		if (Viewmodels.Weapon.of(state.getMainHandItemStack()) == null) {
			return;
		}

		if (state.mainArm == HumanoidArm.RIGHT) {
			state.leftHandItemState.clear();
			state.leftHandItemStack = ItemStack.EMPTY;
		} else {
			state.rightHandItemState.clear();
			state.rightHandItemStack = ItemStack.EMPTY;
		}
	}
}
