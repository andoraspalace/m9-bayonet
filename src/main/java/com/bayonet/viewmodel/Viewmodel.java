package com.bayonet.viewmodel;

import com.bayonet.gltf.GltfAnimation;
import com.bayonet.gltf.GltfScene;
import com.bayonet.render.GltfTextures;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerModelType;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

final class Viewmodel {
	private static final float ARM_SCALE = 0.42F;
	private static final float PIXEL = ARM_SCALE / 16.0F;
	private static final float[] WIDE_FIST = {-2.0F * PIXEL, 2.0F * PIXEL, -12.0F * PIXEL, 0.0F};
	private static final float[] SLIM_FIST = {-1.0F * PIXEL, 2.0F * PIXEL, -11.5F * PIXEL, 0.5F * PIXEL};
	private static final float SEAT_LIMIT = 0.8F * PIXEL;
	private static final float FOV_SCALE = (float) (Math.tan(Math.toRadians(70.0 / 2.0))
			/ (Math.tan(Math.toRadians(68.0 / 2.0)) * 3.0 / 4.0));
	private static final Matrix4fc TO_CAMERA = new Matrix4f().rotationY((float) Math.PI);
	private static final int[] QUAD_CORNERS = {0, 1, 2, 2};
	private static float[] cameraPositions = new float[0];
	private static float[] cameraNormals = new float[0];

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
	private final @Nullable StatTrakModule statTrak;
	private final int statTrakBone;
	private final int[] handle;
	private final Pose turnPose;

	private record ArmBones(HumanoidArm side, int elbow, int hand, int knuckles) {
		static @Nullable ArmBones find(GltfScene rig, Matrix4f[] world, HumanoidArm side) {
			String suffix = side == HumanoidArm.RIGHT ? "_R" : "_L";
			int elbow = rig.findNode("arm_lower" + suffix);
			int hand = rig.findNode("hand" + suffix);
			int knuckles = rig.findNode("finger_middle_0" + suffix);
			return elbow >= 0 && hand >= 0 && knuckles >= 0 && world[elbow] != null && world[hand] != null && world[knuckles] != null
					? new ArmBones(side, elbow, hand, knuckles)
					: null;
		}
	}

	Viewmodel(
		Identifier id,
		GltfScene model,
		GltfScene rig,
		String idleClip,
		boolean oneHanded,
		Vector3fc gripShift,
		float handleEnd,
		@Nullable StatTrakModule statTrak
	) throws IOException {
		this.model = model;
		this.rig = rig;
		this.statTrak = statTrak;
		this.statTrakBone = model.findNode("weapon_offset");
		this.wpnBone = rig.findNode("wpn");
		this.weaponBone = rig.findNode("weapon");

		if (this.wpnBone < 0 || this.weaponBone < 0) {
			throw new IOException("Clips for " + id + " lack the 'wpn' or 'weapon' bone");
		}

		GltfAnimation idle = rig.animation(idleClip);

		if (idle == null) {
			throw new IOException("Clips for " + id + " lack the idle clip '" + idleClip + "'");
		}

		Pose pose = new Pose(rig);
		pose.sample(rig, idle, 0.0F);
		Matrix4f[] world = rig.worldMatrices(pose.localMatrices());

		if (world[this.wpnBone] == null || world[this.weaponBone] == null) {
			throw new IOException("Clips for " + id + " have the 'wpn' or 'weapon' bone outside the scene");
		}

		this.rightArm = ArmBones.find(rig, world, HumanoidArm.RIGHT);
		this.leftArm = ArmBones.find(rig, world, HumanoidArm.LEFT);
		this.weaponInHand = oneHanded && this.rightArm != null
				? new Matrix4f().translation(gripShift).mul(new Matrix4f(world[this.rightArm.hand()]).invert()).mul(world[this.wpnBone])
				: null;
		this.idleRotation = this.weaponRotation(world);

		this.modelToRig = new int[model.nodes().size()];

		for (int n = 0; n < this.modelToRig.length; n++) {
			this.modelToRig[n] = rig.findNode(model.nodes().get(n).name());
		}

		this.modelRestLocal = model.restLocalMatrices();
		this.textures = new GltfTextures(id.withPath(path -> "gltf/" + path), model.images());
		this.positions = model.newVertexArrays();
		this.normals = model.newVertexArrays();
		this.handle = this.weaponInHand != null && !Float.isNaN(handleEnd) ? handleVertices(model, handleEnd) : new int[0];
		this.turnPose = new Pose(rig);
	}

	private static int[] handleVertices(GltfScene model, float handleEnd) {
		IntArrayList handle = new IntArrayList();

		for (int p = 0; p < model.parts().size(); p++) {
			float[] raw = model.parts().get(p).positions();

			for (int v = 0; v < raw.length / 3; v++) {
				if (raw[v * 3 + 1] < handleEnd) {
					handle.add(p << 20 | v);
				}
			}
		}

		return handle.toIntArray();
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
		this.turnPose.sample(this.rig, clip, time);
		Quaternionf rotation = this.weaponRotation(this.rig.worldMatrices(this.turnPose.localMatrices()));
		return rotation.mul(new Quaternionf(this.idleRotation).conjugate());
	}

	void submit(
		Pose pose,
		AbstractClientPlayer player,
		PoseStack poseStack,
		SubmitNodeCollector submitNodeCollector,
		int lightCoords,
		boolean glint,
		int statTrakValue,
		float wear
	) {
		Matrix4f[] rigWorld = this.rig.worldMatrices(pose.localMatrices());
		poseStack.pushPose();
		poseStack.scale(FOV_SCALE, FOV_SCALE, 1.0F);

		boolean armsShown = !player.isInvisible();
		Matrix4f rightFist = this.rightArm != null ? fistPlacement(this.rightArm, rigWorld) : null;

		if (armsShown && rightFist != null) {
			submitArm(HumanoidArm.RIGHT, rightFist, player, poseStack, submitNodeCollector, lightCoords);
		}

		if (armsShown && this.leftArm != null) {
			submitArm(HumanoidArm.LEFT, fistPlacement(this.leftArm, rigWorld), player, poseStack, submitNodeCollector, lightCoords);
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

		if (armsShown && rightFist != null && this.handle.length > 0) {
			this.seatHandle(rightFist, player.getSkin().model() == PlayerModelType.SLIM ? SLIM_FIST : WIDE_FIST);
		}

		float reflections = Reflections.brightness(lightCoords);

		for (int p = 0; p < this.model.parts().size(); p++) {
			GltfScene.Part part = this.model.parts().get(p);
			Identifier texture = this.textures.get(part.image(), part.wearMaps(), wear);
			float[] partPositions = this.positions[p];
			float[] partNormals = this.normals[p];
			submitNodeCollector.submitCustomGeometry(
				poseStack, GltfTextures.renderType(texture, part.cutout()), (cameraPose, buffer) -> emit(part, partPositions, partNormals, cameraPose, buffer, lightCoords)
			);

			if (glint) {
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

		if (statTrakValue >= 0 && this.statTrak != null && this.statTrakBone >= 0 && modelWorld[this.statTrakBone] != null) {
			this.statTrak.submit(modelWorld[this.statTrakBone], statTrakValue, poseStack, submitNodeCollector, lightCoords);
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

	private void seatHandle(Matrix4f fist, float[] arm) {
		Matrix4f toFist = new Matrix4f(fist).invert();
		Vector3f local = new Vector3f();
		float minX = Float.POSITIVE_INFINITY;
		float maxX = Float.NEGATIVE_INFINITY;
		float minY = Float.POSITIVE_INFINITY;
		float maxY = Float.NEGATIVE_INFINITY;

		for (int code : this.handle) {
			float[] posed = this.positions[code >>> 20];
			int v = (code & 0xFFFFF) * 3;
			toFist.transformPosition(posed[v], posed[v + 1], posed[v + 2], local);
			minX = Math.min(minX, local.x);
			maxX = Math.max(maxX, local.x);
			minY = Math.min(minY, local.y);
			maxY = Math.max(maxY, local.y);
		}

		Vector3f nudge = new Vector3f(fit(minX, maxX, arm[0], arm[1]), fit(minY, maxY, arm[2], arm[3]), 0.0F);
		float length = nudge.length();

		if (length == 0.0F) {
			return;
		}

		fist.transformDirection(nudge.mul(Math.min(1.0F, SEAT_LIMIT / length)));

		for (float[] posed : this.positions) {
			for (int i = 0; i < posed.length; i += 3) {
				posed[i] += nudge.x;
				posed[i + 1] += nudge.y;
				posed[i + 2] += nudge.z;
			}
		}
	}

	private static float fit(float min, float max, float low, float high) {
		if (max - min > high - low) {
			return (low + high - min - max) / 2.0F;
		}

		return min < low ? low - min : max > high ? high - max : 0.0F;
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
		int vertexCount = part.vertexCount();

		if (cameraPositions.length < vertexCount * 3) {
			cameraPositions = new float[vertexCount * 3];
			cameraNormals = new float[vertexCount * 3];
		}

		boolean hasNormals = part.normals() != null;
		Vector3f scratch = new Vector3f();

		for (int v = 0; v < vertexCount; v++) {
			GltfScene.store(pose.pose().transformPosition(positions[v * 3], positions[v * 3 + 1], positions[v * 3 + 2], scratch), cameraPositions, v * 3);
			GltfScene.store(
				pose.transformNormal(hasNormals ? normals[v * 3] : 0.0F, hasNormals ? normals[v * 3 + 1] : 1.0F, hasNormals ? normals[v * 3 + 2] : 0.0F, scratch),
				cameraNormals,
				v * 3
			);
		}

		int[] indices = part.indices();
		float[] uvs = part.uvs();

		for (int t = 0; t < indices.length; t += 3) {
			for (int corner : QUAD_CORNERS) {
				int v = indices[t + corner];
				buffer.addVertex(
					cameraPositions[v * 3], cameraPositions[v * 3 + 1], cameraPositions[v * 3 + 2], part.color(), uvs[v * 2], uvs[v * 2 + 1],
					OverlayTexture.NO_OVERLAY, light, cameraNormals[v * 3], cameraNormals[v * 3 + 1], cameraNormals[v * 3 + 2]
				);
			}
		}
	}
}
