package com.bayonet.mixin;

import com.bayonet.viewmodel.ThirdPerson;
import com.bayonet.viewmodel.Viewmodels;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
abstract class HumanoidModelMixin {
	@Shadow
	@Final
	public ModelPart head;
	@Shadow
	@Final
	public ModelPart rightArm;
	@Shadow
	@Final
	public ModelPart leftArm;

	@Inject(
		method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/model/HumanoidModel;setupAttackAnimation(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V"
		)
	)
	private void bayonet$weaponPose(HumanoidRenderState state, CallbackInfo ci) {
		if (!(state instanceof AvatarRenderState)) {
			return;
		}

		if (ThirdPerson.holdsKnife(state.rightArmPose, state.rightHandItemStack, state.leftArmPose)) {
			this.raise(this.rightArm, -1.0F);
		}

		if (ThirdPerson.holdsKnife(state.leftArmPose, state.leftHandItemStack, state.rightArmPose)) {
			this.raise(this.leftArm, 1.0F);
		}

		ThirdPerson.Turn turn = state.getData(ThirdPerson.WEAPON_TURN);
		ItemStack mainHand = state.getMainHandItemStack();

		if (turn != null && turn.weapon() == Viewmodels.Weapon.AWP && Viewmodels.Weapon.of(mainHand) == Viewmodels.Weapon.AWP
				&& state.getArmPose(state.mainArm) == HumanoidModel.ArmPose.CROSSBOW_HOLD) {
			Matrix3f delta = ThirdPerson.inModelSpace(turn.rotation(), this.head);
			turnArm(this.rightArm, delta);
			turnArm(this.leftArm, delta);
		}
	}

	@Unique
	private void raise(ModelPart arm, float inward) {
		float walkSwing = arm.xRot + (float) (Math.PI / 10);
		arm.xRot = (float) (-Math.PI / 2) + this.head.xRot * 0.5F + walkSwing * 0.5F;
		arm.yRot = inward * 0.35F + this.head.yRot * 0.5F;
	}

	@Unique
	private static void turnArm(ModelPart arm, Matrix3f delta) {
		Matrix3f rotation = new Matrix3f(delta).mul(new Matrix3f().rotationZYX(arm.zRot, arm.yRot, arm.xRot));
		Vector3f angles = rotation.getEulerAnglesZYX(new Vector3f());
		arm.xRot = angles.x;
		arm.yRot = angles.y;
		arm.zRot = angles.z;
	}
}
