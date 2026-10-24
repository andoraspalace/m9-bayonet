package com.bayonet.mixin;

import com.bayonet.animation.Offhand;
import com.bayonet.animation.ViewmodelAnimator;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
abstract class ItemInHandRendererMixin {
	@ModifyExpressionValue(
		method = "submitHandsWithItems",
		at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer$HandRenderSelection;renderMainHand:Z")
	)
	private boolean bayonet$showMainHand(boolean render, @Local(argsOnly = true) LocalPlayer player, @Share("offHandOnly") LocalBooleanRef offHandOnly) {
		offHandOnly.set(!render);
		return render || Offhand.firstPersonMainHandOnly(player, offHandOnly.get());
	}

	@ModifyExpressionValue(
		method = "submitHandsWithItems",
		at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer$HandRenderSelection;renderOffHand:Z")
	)
	private boolean bayonet$hideOffhand(boolean render, @Local(argsOnly = true) LocalPlayer player, @Share("offHandOnly") LocalBooleanRef offHandOnly) {
		return render && !Offhand.firstPersonMainHandOnly(player, offHandOnly.get());
	}

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
			if (Offhand.isHiddenInFirstPerson(itemStack)) {
				ci.cancel();
			}
		} else if (!player.isScoping() && ViewmodelAnimator.submit(itemStack, player, partialTicks, poseStack, submitNodeCollector, lightCoords)) {
			ci.cancel();
		}
	}
}
