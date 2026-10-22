package com.bayonet.mixin;

import com.bayonet.viewmodel.ViewmodelAnimator;
import com.bayonet.viewmodel.Viewmodels;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
abstract class ItemInHandRendererMixin {
	@Shadow
	private ItemStack mainHandItem;

	@Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
	private void bayonet$submitViewmodel(
		AbstractClientPlayer player,
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
			if (Viewmodels.Weapon.of(itemStack) != null || Viewmodels.Weapon.of(this.mainHandItem) != null) {
				ci.cancel();
			}
		} else if (!player.isScoping() && ViewmodelAnimator.submit(itemStack, player, partialTicks, poseStack, submitNodeCollector, lightCoords)) {
			ci.cancel();
		}
	}
}
