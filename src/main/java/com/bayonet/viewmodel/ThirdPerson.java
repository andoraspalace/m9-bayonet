package com.bayonet.viewmodel;

import com.bayonet.gltf.GltfAnimation;
import com.bayonet.viewmodel.Viewmodels.Weapon;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix3fc;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

public final class ThirdPerson {
	public static final RenderStateDataKey<Turn> WEAPON_TURN = RenderStateDataKey.create(() -> "bayonet:weapon_turn");
	private static final Matrix3fc MODEL_FROM_CAMERA = new Matrix3f().scaling(-1.0F, -1.0F, 1.0F);

	private ThirdPerson() {
	}

	public static final class Turn {
		private final Weapon weapon;
		private final Viewmodel viewmodel;
		private final GltfAnimation clip;
		private final float time;
		private @Nullable Quaternionf rotation;

		Turn(Weapon weapon, Viewmodel viewmodel, GltfAnimation clip, float time) {
			this.weapon = weapon;
			this.viewmodel = viewmodel;
			this.clip = clip;
			this.time = time;
		}

		public Weapon weapon() {
			return this.weapon;
		}

		public Quaternionf rotation() {
			if (this.rotation == null) {
				this.rotation = this.viewmodel.weaponTurn(this.clip, this.time);
			}

			return this.rotation;
		}
	}

	public static Matrix3f inModelSpace(Quaternionf turn, ModelPart head) {
		Matrix3f view = new Matrix3f().rotationZYX(0.0F, head.yRot, head.xRot).mul(MODEL_FROM_CAMERA);
		return new Matrix3f(view).mul(new Matrix3f().set(turn)).mul(view.transpose(new Matrix3f()));
	}

	public static HumanoidModel.ArmPose armPose(ArmedEntityRenderState state, HumanoidArm arm) {
		return arm == HumanoidArm.RIGHT ? state.rightArmPose : state.leftArmPose;
	}

	public static boolean holdsKnife(HumanoidModel.ArmPose pose, ItemStack item, HumanoidModel.ArmPose otherPose) {
		return pose == HumanoidModel.ArmPose.ITEM && !otherPose.isTwoHanded() && Weapon.of(item) == Weapon.M9_BAYONET;
	}

	public static @Nullable Turn weaponTurn(Avatar avatar, float partialTicks) {
		if (avatar == Minecraft.getInstance().player) {
			return ViewmodelAnimator.weaponTurn(partialTicks);
		}

		ItemStack used = avatar.getUseItem();

		if (avatar.isUsingItem() && avatar.getUsedItemHand() == InteractionHand.MAIN_HAND && Weapon.of(used) == Weapon.AWP
				&& !CrossbowItem.isCharged(used)) {
			return Viewmodels.turn(Weapon.AWP, "reload", ViewmodelAnimator.chargeProgress(avatar, used, partialTicks));
		}

		return null;
	}
}
