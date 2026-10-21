package com.csmod.viewmodel;

import com.csmod.gltf.GltfAnimation;
import com.csmod.gltf.GltfScene;
import com.csmod.render.GltfTextures;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

final class Viewmodel {
	private static final float ARM_SCALE = 0.42F;
	private static final float FOV_SCALE = (float) (Math.tan(Math.toRadians(70.0 / 2.0))
			/ (Math.tan(Math.toRadians(68.0 / 2.0)) * 3.0 / 4.0));
	private static final Matrix4fc TO_CAMERA = new Matrix4f().rotationY((float) Math.PI);
	private static final int[] QUAD_CORNERS = {0, 1, 2, 2};

	final GltfScene rig;
	private final GltfScene model;
	private final int[] modelToRig;
	private final int wpnBone;
	private final int weaponBone;
	private final Matrix4f[] modelRestLocal;
	private final GltfTextures textures;
	private final float[][] positions;
	private final float[][] normals;
	private final @Nullable ArmBones rightArm;
	private final @Nullable ArmBones leftArm;
	private final @Nullable Matrix4f weaponInHand;
	private final Quaternionf idleRotation;

	private record ArmBones(HumanoidArm side, int elbow, int hand, int knuckles) {
		static @Nullable ArmBones find(GltfScene rig, HumanoidArm side) {
			String suffix = side == HumanoidArm.RIGHT ? "_R" : "_L";
			int elbow = rig.findNode("arm_lower" + suffix);
			int hand = rig.findNode("hand" + suffix);
			int knuckles = rig.findNode("finger_middle_0" + suffix);
			return elbow >= 0 && hand >= 0 && knuckles >= 0 ? new ArmBones(side, elbow, hand, knuckles) : null;
		}
	}

	Viewmodel(Identifier id, GltfScene model, GltfScene rig, String idleClip, boolean oneHanded) throws IOException {
		this.model = model;
		this.rig = rig;
		this.wpnBone = rig.findNode("wpn");
		this.weaponBone = rig.findNode("weapon");

		if (this.wpnBone < 0 || this.weaponBone < 0) {
			throw new IOException("Clips for " + id + " lack the 'wpn' or 'weapon' bone");
		}

		this.rightArm = ArmBones.find(rig, HumanoidArm.RIGHT);
		this.leftArm = ArmBones.find(rig, HumanoidArm.LEFT);
		GltfAnimation idle = rig.animation(idleClip);

		if (idle == null) {
			throw new IOException("Clips for " + id + " lack the idle clip '" + idleClip + "'");
		}

		Pose pose = new Pose(rig);
		pose.sample(rig, idle, 0.0F);
		Matrix4f[] world = rig.worldMatrices(pose.localMatrices());
		this.weaponInHand = oneHanded && this.rightArm != null ? new Matrix4f(world[this.rightArm.hand()]).invert().mul(world[this.wpnBone]) : null;
		this.idleRotation = this.weaponRotation(world);

		this.modelToRig = new int[model.nodes().size()];

		for (int n = 0; n < this.modelToRig.length; n++) {
			this.modelToRig[n] = rig.findNode(model.nodes().get(n).name());
		}

		this.modelRestLocal = model.restLocalMatrices();
		this.textures = new GltfTextures(id.withPath(path -> "gltf/" + path), model.images());
		this.positions = model.newVertexArrays();
		this.normals = model.newVertexArrays();
	}

	@Nullable GltfAnimation clip(String name) {
		return this.rig.animation(name);
	}

	private Matrix4f weaponFrame(Matrix4f[] rigWorld) {
		return this.weaponInHand != null ? new Matrix4f(rigWorld[this.rightArm.hand()]).mul(this.weaponInHand) : new Matrix4f(rigWorld[this.wpnBone]);
	}

	private Quaternionf weaponRotation(Matrix4f[] rigWorld) {
		return new Matrix4f(TO_CAMERA).mul(this.weaponFrame(rigWorld)).getNormalizedRotation(new Quaternionf());
	}

	Quaternionf weaponTurn(GltfAnimation clip, float time) {
		Pose pose = new Pose(this.rig);
		pose.sample(this.rig, clip, time);
		Quaternionf rotation = this.weaponRotation(this.rig.worldMatrices(pose.localMatrices()));
		return rotation.mul(new Quaternionf(this.idleRotation).conjugate());
	}

	void submit(
		Pose pose, AbstractClientPlayer player, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, boolean hasFoil
	) {
		Matrix4f[] rigWorld = this.rig.worldMatrices(pose.localMatrices());
		poseStack.pushPose();
		poseStack.scale(FOV_SCALE, FOV_SCALE, 1.0F);

		if (!player.isInvisible()) {
			for (ArmBones arm : new ArmBones[]{this.rightArm, this.leftArm}) {
				if (arm != null) {
					submitArm(arm.side(), fistPlacement(arm, rigWorld), player, poseStack, submitNodeCollector, lightCoords);
				}
			}
		}

		Matrix4f attach = new Matrix4f(TO_CAMERA).mul(this.weaponFrame(rigWorld)).mul(new Matrix4f(rigWorld[this.weaponBone]).invert());
		Matrix4f[] modelWorld = new Matrix4f[this.model.nodes().size()];

		for (int n : this.model.order()) {
			int rigNode = this.modelToRig[n];
			int parent = this.model.nodes().get(n).parent();

			if (rigNode >= 0 && rigWorld[rigNode] != null) {
				modelWorld[n] = new Matrix4f(attach).mul(rigWorld[rigNode]);
			} else if (parent >= 0 && modelWorld[parent] != null) {
				modelWorld[n] = new Matrix4f(modelWorld[parent]).mul(this.modelRestLocal[n]);
			} else {
				modelWorld[n] = new Matrix4f(attach);
			}
		}

		this.model.pose(modelWorld, this.positions, this.normals);
		float reflections = Reflections.brightness(lightCoords);

		for (int p = 0; p < this.model.parts().size(); p++) {
			GltfScene.Part part = this.model.parts().get(p);
			Identifier texture = this.textures.get(part.image());
			RenderType renderType = part.cutout() ? RenderTypes.entityCutout(texture) : RenderTypes.entitySolid(texture);
			float[] partPositions = this.positions[p];
			float[] partNormals = this.normals[p];
			submitNodeCollector.submitCustomGeometry(
				poseStack, renderType, (cameraPose, buffer) -> emit(part, partPositions, partNormals, cameraPose, buffer, lightCoords)
			);

			if (hasFoil) {
				submitNodeCollector.submitCustomGeometry(
					poseStack, RenderTypes.entityGlint(), (cameraPose, buffer) -> emit(part, partPositions, partNormals, cameraPose, buffer, lightCoords)
				);
			}

			if (part.reflectivity() != null && part.normals() != null && reflections > 0.0F) {
				submitNodeCollector.submitCustomGeometry(
					poseStack,
					Reflections.renderType(),
					(cameraPose, buffer) -> Reflections.emit(part, partPositions, partNormals, cameraPose, buffer, lightCoords, reflections)
				);
			}
		}

		poseStack.popPose();
	}

	private static Matrix4f fistPlacement(ArmBones arm, Matrix4f[] rigWorld) {
		Matrix4f hand = new Matrix4f(TO_CAMERA).mul(rigWorld[arm.hand()]);
		Vector3f wrist = hand.getTranslation(new Vector3f());
		Vector3f elbow = new Matrix4f(TO_CAMERA).mul(rigWorld[arm.elbow()]).getTranslation(new Vector3f());
		Vector3f knuckles = new Matrix4f(TO_CAMERA).mul(rigWorld[arm.knuckles()]).getTranslation(new Vector3f());
		Vector3f along = wrist.sub(elbow, new Vector3f()).normalize();
		Vector3f thumb = hand.transformDirection(new Vector3f(0.0F, 0.0F, arm.side() == HumanoidArm.RIGHT ? 1.0F : -1.0F));
		thumb.sub(new Vector3f(along).mul(thumb.dot(along))).normalize();
		Vector3f back = thumb.negate(new Vector3f());
		Matrix3f basis = new Matrix3f(along.cross(back, new Vector3f()), along, back);
		return new Matrix4f().translation(knuckles).mul(new Matrix4f().set(basis));
	}

	private static void submitArm(
		HumanoidArm arm, Matrix4f fist, AbstractClientPlayer player, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords
	) {
		boolean right = arm == HumanoidArm.RIGHT;
		float side = right ? 1.0F : -1.0F;
		Matrix4f armPart = new Matrix4f().translation(-5.0F * side / 16.0F, 2.0F / 16.0F, 0.0F).rotateZ(0.1F * side);
		Matrix4f place = new Matrix4f(fist)
				.scale(ARM_SCALE)
				.translate(side / 16.0F, -10.0F / 16.0F, 0.0F)
				.mul(armPart.invert());

		poseStack.pushPose();
		poseStack.mulPose(place);
		AvatarRenderer<AbstractClientPlayer> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getPlayerRenderer(player);
		Identifier skin = player.getSkin().body().texturePath();

		if (right) {
			renderer.renderRightHand(poseStack, submitNodeCollector, lightCoords, skin, player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE));
		} else {
			renderer.renderLeftHand(poseStack, submitNodeCollector, lightCoords, skin, player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE));
		}

		poseStack.popPose();
	}

	private static void emit(GltfScene.Part part, float[] positions, float[] normals, PoseStack.Pose pose, VertexConsumer buffer, int light) {
		int[] indices = part.indices();
		boolean hasNormals = part.normals() != null;

		for (int t = 0; t < indices.length; t += 3) {
			for (int corner : QUAD_CORNERS) {
				int v = indices[t + corner];
				buffer.addVertex(pose, positions[v * 3], positions[v * 3 + 1], positions[v * 3 + 2])
						.setColor(part.color())
						.setUv(part.uvs()[v * 2], part.uvs()[v * 2 + 1])
						.setOverlay(OverlayTexture.NO_OVERLAY)
						.setLight(light)
						.setNormal(pose, hasNormals ? normals[v * 3] : 0.0F, hasNormals ? normals[v * 3 + 1] : 1.0F, hasNormals ? normals[v * 3 + 2] : 0.0F);
			}
		}
	}
}
