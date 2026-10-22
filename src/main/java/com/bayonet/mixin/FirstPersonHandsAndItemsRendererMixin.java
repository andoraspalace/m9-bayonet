package com.bayonet.mixin;

import com.bayonet.viewmodel.ViewmodelAnimator;
import com.bayonet.viewmodel.Viewmodels;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
abstract class FirstPersonHandsAndItemsRendererMixin {
	@Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
	private void bayonet$submitViewmodel(
		PlayerRenderState playerState,
		FirstPersonHandsAndItemsRenderState state,
		float partialTicks,
		float xRot,
		InteractionHand hand,
		float attack,
		ItemStack itemStack,
		float inverseArmHeight,
		PoseStack poseStack,
		SubmitNodeCollector submitNodeCollector,
		int lightCoords,
		CallbackInfo ci
	) {
		if (hand == InteractionHand.OFF_HAND) {
			if (Viewmodels.Weapon.of(itemStack) != null || Viewmodels.Weapon.of(state.mainHandItem) != null) {
				ci.cancel();
			}
		} else if (!state.isScoping
				&& ViewmodelAnimator.submit(itemStack, playerState.avatarRenderState, partialTicks, poseStack, submitNodeCollector, lightCoords)) {
			ci.cancel();
		}
	}
}
