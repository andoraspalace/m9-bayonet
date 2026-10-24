package com.bayonet.viewmodel;

import com.bayonet.gltf.GltfScene;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerModelType;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

final class MinecraftArms {
	private static final float PIXELS_PER_BLOCK = 16.0F;
	private static final float ARM_SCALE = 0.42F;
	static final float PIXEL = ARM_SCALE / PIXELS_PER_BLOCK;
	private static final FistBounds WIDE_FIST = new FistBounds(-2.0F * PIXEL, 2.0F * PIXEL, -12.0F * PIXEL, 0.0F);
	private static final FistBounds SLIM_FIST = new FistBounds(-1.0F * PIXEL, 2.0F * PIXEL, -11.5F * PIXEL, 0.5F * PIXEL);
	private static final float SHOULDER_X = 5.0F;
	private static final float SHOULDER_Y = 2.0F;
	private static final float ARM_ROLL = 0.1F;
	private static final float FIST_X = 1.0F;
	private static final float FIST_Y = -10.0F;
	private static final Matrix4fc RIGHT_ARM_OFFSET = armOffset(HumanoidArm.RIGHT);
	private static final Matrix4fc LEFT_ARM_OFFSET = armOffset(HumanoidArm.LEFT);
	private static final String ELBOW_BONE = "arm_lower";
	private static final String HAND_BONE = "hand";
	private static final String KNUCKLES_BONE = "finger_middle_0";
	private static final String RIGHT_SUFFIX = "_R";
	private static final String LEFT_SUFFIX = "_L";

	private final @Nullable Bones right;
	private final @Nullable Bones left;
	private final Matrix4f rightFist = new Matrix4f();
	private final Matrix4f leftFist = new Matrix4f();
	private final Matrix4f hand = new Matrix4f();
	private final Matrix4f bone = new Matrix4f();
	private final Matrix4f orientation = new Matrix4f();
	private final Matrix4f armPose = new Matrix4f();
	private final Matrix3f basis = new Matrix3f();
	private final Vector3f knuckles = new Vector3f();
	private final Vector3f elbow = new Vector3f();
	private final Vector3f along = new Vector3f();
	private final Vector3f thumb = new Vector3f();
	private final Vector3f back = new Vector3f();
	private final Vector3f scratch = new Vector3f();

	MinecraftArms(GltfScene rig) {
		this.right = Bones.find(rig, RIGHT_SUFFIX);
		this.left = Bones.find(rig, LEFT_SUFFIX);
	}

	record FistBounds(float minX, float maxX, float minY, float maxY) {
	}

	private record Bones(int elbow, int hand, int knuckles) {
		static @Nullable Bones find(GltfScene rig, String suffix) {
			int elbow = rig.findNode(ELBOW_BONE + suffix);
			int hand = rig.findNode(HAND_BONE + suffix);
			int knuckles = rig.findNode(KNUCKLES_BONE + suffix);
			return elbow >= 0 && hand >= 0 && knuckles >= 0 ? new Bones(elbow, hand, knuckles) : null;
		}
	}

	static FistBounds fistBounds(PlayerModelType type) {
		return type == PlayerModelType.SLIM ? SLIM_FIST : WIDE_FIST;
	}

	int rightHand() {
		return this.right != null ? this.right.hand() : -1;
	}

	@Nullable Matrix4fc fist(HumanoidArm side, Matrix4f[] rigWorld) {
		Bones bones = side == HumanoidArm.RIGHT ? this.right : this.left;
		Matrix4f fist = side == HumanoidArm.RIGHT ? this.rightFist : this.leftFist;
		return bones != null ? this.placeFist(bones, side, rigWorld, fist) : null;
	}

	void submit(
		AbstractClientPlayer avatar, @Nullable Matrix4fc rightFist, @Nullable Matrix4fc leftFist, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
		int lightCoords
	) {
		AvatarRenderer<?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getPlayerRenderer(avatar);
		Identifier skin = avatar.getSkin().body().texturePath();

		if (rightFist != null) {
			poseStack.pushPose();
			poseStack.mulPose(rightFist.mul(RIGHT_ARM_OFFSET, this.armPose));
			renderer.renderRightHand(poseStack, submitNodeCollector, lightCoords, skin, avatar.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE));
			poseStack.popPose();
		}

		if (leftFist != null) {
			poseStack.pushPose();
			poseStack.mulPose(leftFist.mul(LEFT_ARM_OFFSET, this.armPose));
			renderer.renderLeftHand(poseStack, submitNodeCollector, lightCoords, skin, avatar.isModelPartShown(PlayerModelPart.LEFT_SLEEVE));
			poseStack.popPose();
		}

		PlayerModel model = renderer.getModel();
		model.rightSleeve.visible = avatar.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE);
		model.leftSleeve.visible = avatar.isModelPartShown(PlayerModelPart.LEFT_SLEEVE);
	}

	private Matrix4f placeFist(Bones bones, HumanoidArm side, Matrix4f[] rigWorld, Matrix4f fist) {
		Matrix4f hand = RigSpace.TO_CAMERA.mul(rigWorld[bones.hand()], this.hand);
		Vector3f elbow = RigSpace.TO_CAMERA.mul(rigWorld[bones.elbow()], this.bone).getTranslation(this.elbow);
		Vector3f knuckles = RigSpace.TO_CAMERA.mul(rigWorld[bones.knuckles()], this.bone).getTranslation(this.knuckles);
		Vector3f along = hand.getTranslation(this.along).sub(elbow).normalize();
		Vector3f thumb = hand.transformDirection(this.thumb.set(0.0F, 0.0F, sign(side)));
		thumb.sub(this.scratch.set(along).mul(thumb.dot(along))).normalize();
		Vector3f back = thumb.negate(this.back);
		Matrix3f basis = this.basis.set(along.cross(back, this.scratch), along, back);
		return fist.translation(knuckles).mul(this.orientation.set(basis));
	}

	private static Matrix4f armOffset(HumanoidArm side) {
		float sign = sign(side);
		Matrix4f armPose = new Matrix4f()
				.translation(-SHOULDER_X * sign / PIXELS_PER_BLOCK, SHOULDER_Y / PIXELS_PER_BLOCK, 0.0F)
				.rotateZ(ARM_ROLL * sign);
		return new Matrix4f()
				.scaling(ARM_SCALE)
				.translate(FIST_X * sign / PIXELS_PER_BLOCK, FIST_Y / PIXELS_PER_BLOCK, 0.0F)
				.mul(armPose.invert());
	}

	private static float sign(HumanoidArm side) {
		return side == HumanoidArm.RIGHT ? 1.0F : -1.0F;
	}
}
