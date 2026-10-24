package com.bayonet.mixin;

import com.bayonet.animation.ThirdPerson;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
abstract class AvatarRendererMixin {
	@ModifyReturnValue(
		method = "getArmPose(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/client/model/HumanoidModel$ArmPose;",
		at = @At("RETURN")
	)
	private static HumanoidModel.ArmPose bayonet$weaponArmPose(
		HumanoidModel.ArmPose pose,
		@Local(argsOnly = true) Avatar avatar,
		@Local(argsOnly = true) ItemStack itemInHand,
		@Local(argsOnly = true) InteractionHand hand
	) {
		return ThirdPerson.armPose(avatar, itemInHand, hand, pose);
	}

	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
		at = @At("TAIL")
	)
	private void bayonet$extractWeaponTurn(Avatar avatar, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		ThirdPerson.extractWeaponTurn(avatar, state, partialTicks);
	}
}
