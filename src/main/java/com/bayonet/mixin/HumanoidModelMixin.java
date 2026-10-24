package com.bayonet.mixin;

import com.bayonet.animation.ThirdPerson;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
abstract class HumanoidModelMixin {
	@Inject(
		method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/model/HumanoidModel;setupAttackAnimation(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V"
		)
	)
	private void bayonet$weaponPose(HumanoidRenderState state, CallbackInfo ci) {
		ThirdPerson.poseArms((HumanoidModel<?>) (Object) this, state);
	}
}
