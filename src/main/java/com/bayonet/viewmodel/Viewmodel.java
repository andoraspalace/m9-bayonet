package com.bayonet.viewmodel;

import com.bayonet.gltf.GltfAnimation;
import com.bayonet.gltf.GltfScene;
import com.bayonet.render.Reflections;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.OptionalInt;
import net.minecraft.client.Camera;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

public final class Viewmodel {
	private static final double CS_VIEWMODEL_FOV = 68.0;
	private static final double CS_REFERENCE_ASPECT = 4.0 / 3.0;
	private static final float FOV_SCALE = (float) (Math.tan(Math.toRadians(Camera.BASE_HUD_FOV / 2.0))
			/ (Math.tan(Math.toRadians(CS_VIEWMODEL_FOV / 2.0)) / CS_REFERENCE_ASPECT));

	private final BoundRig binding;
	private final RiggedMesh weaponMesh;
	private final int[] weaponToRig;
	private final @Nullable HandleSeat handleSeat;
	private final StatTrakModule.@Nullable Mount statTrakMount;

	Viewmodel(Identifier id, GltfScene model, BoundRig binding, @Nullable StatTrakModule statTrak) {
		Float handleEnd = binding.handleEnd();
		this.binding = binding;
		this.weaponMesh = new RiggedMesh(id, model);
		this.weaponToRig = this.weaponMesh.bind(binding.rig());
		this.handleSeat = handleEnd != null ? HandleSeat.of(model, handleEnd) : null;
		this.statTrakMount = statTrak != null ? statTrak.mountOn(model) : null;
	}

	public Pose newPose() {
		return new Pose(this.binding.rig());
	}

	public @Nullable GltfAnimation clip(String name) {
		return this.binding.clip(name);
	}

	public Quaternionf weaponTurn(GltfAnimation clip, float time) {
		return this.binding.weaponTurn(clip, time);
	}

	public void submit(
		Pose pose,
		AbstractClientPlayer avatar,
		PoseStack poseStack,
		SubmitNodeCollector submitNodeCollector,
		int lightCoords,
		boolean csArmsEnabled,
		float wear,
		boolean glint,
		OptionalInt statTrakKills
	) {
		Matrix4f[] rigWorld = pose.worldMatrices();
		poseStack.pushPose();
		poseStack.scale(FOV_SCALE, FOV_SCALE, 1.0F);

		boolean armsShown = !avatar.isInvisible();
		Arms.Bound csArms = csArmsEnabled ? this.binding.arms() : null;
		float reflections = Reflections.brightness(lightCoords);
		Matrix4f[] weaponWorld = this.weaponMesh.place(this.weaponToRig, rigWorld, this.binding.grip().attach(rigWorld, csArms == null));
		this.weaponMesh.pose();

		if (armsShown && csArms != null) {
			csArms.submit(rigWorld, poseStack, submitNodeCollector, lightCoords, reflections);
		} else if (armsShown) {
			MinecraftArms minecraftArms = this.binding.minecraftArms();
			Matrix4fc rightFist = minecraftArms.fist(HumanoidArm.RIGHT, rigWorld);
			Matrix4fc leftFist = minecraftArms.fist(HumanoidArm.LEFT, rigWorld);
			minecraftArms.submit(avatar, rightFist, leftFist, poseStack, submitNodeCollector, lightCoords);

			if (rightFist != null && this.handleSeat != null) {
				Vector3fc nudge = this.handleSeat.nudge(rightFist, MinecraftArms.fistBounds(avatar.getSkin().model()), this.weaponMesh);
				poseStack.translate(nudge.x(), nudge.y(), nudge.z());
			}
		}

		this.weaponMesh.submit(poseStack, submitNodeCollector, lightCoords, reflections, wear, glint);

		if (statTrakKills.isPresent() && this.statTrakMount != null) {
			this.statTrakMount.submit(weaponWorld, statTrakKills.getAsInt(), poseStack, submitNodeCollector, lightCoords, reflections);
		}

		poseStack.popPose();
	}
}
