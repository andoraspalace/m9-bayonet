package com.csmod.mixin;

import com.csmod.viewmodel.ThirdPerson;
import com.csmod.viewmodel.Viewmodels;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandLayer.class)
abstract class ItemInHandLayerMixin {
	@Inject(
		method = "submitArmWithItem",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"
		)
	)
	private void csmod$turnKnifeInHand(
		ArmedEntityRenderState state,
		ItemStackRenderState item,
		ItemStack itemStack,
		HumanoidArm arm,
		PoseStack poseStack,
		SubmitNodeCollector submitNodeCollector,
		int lightCoords,
		CallbackInfo ci
	) {
		ThirdPerson.Turn turn = state.getData(ThirdPerson.WEAPON_TURN);

		if (turn == null
				|| turn.weapon() != Viewmodels.Weapon.M9_BAYONET
				|| arm != state.mainArm
				|| !(state instanceof AvatarRenderState)
				|| !ThirdPerson.holdsKnife(ThirdPerson.armPose(state, arm), itemStack, ThirdPerson.armPose(state, arm.getOpposite()))
				|| !(((RenderLayer<?, ?>) (Object) this).getParentModel() instanceof HumanoidModel<?> model)) {
			return;
		}

		var part = arm == HumanoidArm.LEFT ? model.leftArm : model.rightArm;
		Matrix3f hand = new Matrix3f().rotationZYX(part.zRot, part.yRot, part.xRot).rotateX((float) (-Math.PI / 2)).rotateY((float) Math.PI);
		Matrix3f inHand = hand.transpose(new Matrix3f()).mul(ThirdPerson.inModelSpace(turn.rotation(), model.head)).mul(hand);
		poseStack.translate(0.0F, -2.0F / 16.0F, 2.0F / 16.0F);
		poseStack.mulPose(new Matrix4f().set(inHand));
		poseStack.translate(0.0F, 2.0F / 16.0F, -2.0F / 16.0F);
	}
}
