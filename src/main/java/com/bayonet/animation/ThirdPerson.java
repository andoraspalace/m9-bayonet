package com.bayonet.animation;

import com.bayonet.gltf.GltfAnimation;
import com.bayonet.util.ModIds;
import com.bayonet.viewmodel.Viewmodel;
import com.bayonet.viewmodel.Viewmodels;
import com.bayonet.viewmodel.WeaponRig;
import com.bayonet.weapon.Weapon;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix3fc;
import org.joml.Matrix4f;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

public final class ThirdPerson {
	private static final RenderStateDataKey<WeaponTurn> WEAPON_TURN = RenderStateDataKey.create(ModIds.of("weapon_turn")::toString);
	private static final Matrix3fc MODEL_FROM_CAMERA = new Matrix3f().scaling(-1.0F, -1.0F, 1.0F);
	private static final float ARM_RAISED = (float) (-Math.PI / 2);
	private static final float ITEM_POSE_TILT = (float) (Math.PI / 10);
	private static final float HEAD_FOLLOW = 0.5F;
	private static final float WALK_SWING_DAMPING = 0.5F;
	private static final float KNIFE_ARM_INWARD = 0.35F;
	private static final float HAND_FRAME_PITCH = (float) (-Math.PI / 2);
	private static final float HAND_FRAME_YAW = (float) Math.PI;
	private static final float GRIP_PIVOT_Y = -2.0F / 16.0F;
	private static final float GRIP_PIVOT_Z = 2.0F / 16.0F;
	private static final Matrix3f VIEW = new Matrix3f();
	private static final Matrix3f VIEW_INVERSE = new Matrix3f();
	private static final Matrix3f TURN = new Matrix3f();
	private static final Matrix3f ARM = new Matrix3f();
	private static final Matrix3f TURNED_ARM = new Matrix3f();
	private static final Vector3f ARM_ANGLES = new Vector3f();
	private static final Matrix3f HAND = new Matrix3f();
	private static final Matrix3f IN_HAND = new Matrix3f();
	private static final Matrix4f IN_HAND_POSE = new Matrix4f();

	private ThirdPerson() {
	}

	public static void extractWeaponTurn(Avatar avatar, AvatarRenderState state, float partialTicks) {
		WeaponTurn turn = weaponTurn(avatar, partialTicks);

		if (turn != null) {
			state.setData(WEAPON_TURN, turn);
		}
	}

	public static HumanoidModel.ArmPose armPose(Avatar avatar, ItemStack itemInHand, InteractionHand hand, HumanoidModel.ArmPose vanilla) {
		if (hand == InteractionHand.OFF_HAND) {
			return vanilla != HumanoidModel.ArmPose.EMPTY && Offhand.isHiddenBy(avatar.getMainHandItem()) ? HumanoidModel.ArmPose.EMPTY : vanilla;
		}

		return Weapon.of(itemInHand) == Weapon.AWP ? HumanoidModel.ArmPose.CROSSBOW_HOLD : vanilla;
	}

	public static void poseArms(HumanoidModel<?> model, HumanoidRenderState state) {
		if (!(state instanceof AvatarRenderState)) {
			return;
		}

		if (holdsKnife(state.rightArmPose, state.rightHandItemStack, state.leftArmPose)) {
			raiseKnifeArm(model.rightArm, model.head, -KNIFE_ARM_INWARD);
		}

		if (holdsKnife(state.leftArmPose, state.leftHandItemStack, state.rightArmPose)) {
			raiseKnifeArm(model.leftArm, model.head, KNIFE_ARM_INWARD);
		}

		WeaponTurn turn = state.getData(WEAPON_TURN);

		if (turn != null
				&& turn.weapon() == Weapon.AWP
				&& state.getArmPose(state.mainArm) == HumanoidModel.ArmPose.CROSSBOW_HOLD
				&& Weapon.of(state.getMainHandItemStack()) == Weapon.AWP) {
			Matrix3fc delta = inModelSpace(turn.rotation(), model.head);
			turnArm(model.rightArm, delta);
			turnArm(model.leftArm, delta);
		}
	}

	public static void turnHeldKnife(ArmedEntityRenderState state, HumanoidModel<?> model, HumanoidArm arm, ItemStack itemStack, PoseStack poseStack) {
		WeaponTurn turn = state.getData(WEAPON_TURN);

		if (turn == null
				|| !turn.weapon().isKnife()
				|| arm != state.mainArm
				|| !holdsKnife(state.getArmPose(arm), itemStack, state.getArmPose(arm.getOpposite()))) {
			return;
		}

		ModelPart part = arm == HumanoidArm.LEFT ? model.leftArm : model.rightArm;
		Matrix3f hand = HAND.rotationZYX(part.zRot, part.yRot, part.xRot).rotateX(HAND_FRAME_PITCH).rotateY(HAND_FRAME_YAW);
		Matrix3f inHand = hand.transpose(IN_HAND).mul(inModelSpace(turn.rotation(), model.head)).mul(hand);
		poseStack.translate(0.0F, GRIP_PIVOT_Y, GRIP_PIVOT_Z);
		poseStack.mulPose(IN_HAND_POSE.set(inHand));
		poseStack.translate(0.0F, -GRIP_PIVOT_Y, -GRIP_PIVOT_Z);
	}

	private static @Nullable WeaponTurn weaponTurn(Avatar avatar, float partialTicks) {
		LocalPlayer player = Minecraft.getInstance().player;

		if (player != null && avatar == player) {
			return ViewmodelAnimator.weaponTurn(player, partialTicks);
		}

		ItemStack used = avatar.getUseItem();

		if (!Awp.isCharging(avatar, used)) {
			return null;
		}

		Viewmodel viewmodel = Viewmodels.forStack(used);
		GltfAnimation reload = viewmodel != null ? viewmodel.clip(WeaponRig.RELOAD) : null;

		if (viewmodel == null || reload == null) {
			return null;
		}

		return new WeaponTurn(Weapon.AWP, viewmodel, reload, ClipPlayer.chargeTime(reload, Awp.chargeProgress(avatar, used, partialTicks)));
	}

	private static boolean holdsKnife(HumanoidModel.ArmPose pose, ItemStack item, HumanoidModel.ArmPose otherPose) {
		return pose == HumanoidModel.ArmPose.ITEM && !otherPose.isTwoHanded() && Weapon.of(item) instanceof Weapon weapon && weapon.isKnife();
	}

	private static void raiseKnifeArm(ModelPart arm, ModelPart head, float inwardYaw) {
		float walkSwing = arm.xRot + ITEM_POSE_TILT;
		arm.xRot = ARM_RAISED + head.xRot * HEAD_FOLLOW + walkSwing * WALK_SWING_DAMPING;
		arm.yRot = inwardYaw + head.yRot * HEAD_FOLLOW;
	}

	private static void turnArm(ModelPart arm, Matrix3fc delta) {
		Vector3f angles = delta.mul(ARM.rotationZYX(arm.zRot, arm.yRot, arm.xRot), TURNED_ARM).getEulerAnglesZYX(ARM_ANGLES);
		arm.xRot = angles.x;
		arm.yRot = angles.y;
		arm.zRot = angles.z;
	}

	private static Matrix3fc inModelSpace(Quaternionfc turn, ModelPart head) {
		Matrix3f view = VIEW.rotationZYX(0.0F, head.yRot, head.xRot).mul(MODEL_FROM_CAMERA);
		Matrix3f viewInverse = view.transpose(VIEW_INVERSE);
		return view.mul(TURN.set(turn)).mul(viewInverse);
	}
}
