package com.csmod.viewmodel;

import com.csmod.viewmodel.Viewmodels.Weapon;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix3fc;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

public final class ThirdPerson {
	public static final RenderStateDataKey<Turn> WEAPON_TURN = RenderStateDataKey.create(() -> "csmod:weapon_turn");
	private static final Matrix3fc MODEL_FROM_CAMERA = new Matrix3f().scaling(-1.0F, -1.0F, 1.0F);

	private ThirdPerson() {
	}

	public record Turn(Weapon weapon, Quaternionf rotation) {
	}

	public static Matrix3f inModelSpace(Quaternionf turn, ModelPart head) {
		Matrix3f view = new Matrix3f().rotationZYX(0.0F, head.yRot, head.xRot).mul(MODEL_FROM_CAMERA);
		return new Matrix3f(view).mul(new Matrix3f().set(turn)).mul(view.transpose(new Matrix3f()));
	}

	public static boolean holdsKnife(HumanoidModel.ArmPose pose, ItemStack item, HumanoidModel.ArmPose otherPose) {
		return pose == HumanoidModel.ArmPose.ITEM && !otherPose.isTwoHanded() && Weapon.of(item) == Weapon.M9_BAYONET;
	}

	public static @Nullable Turn weaponTurn(Avatar avatar, float partialTicks) {
		if (avatar == Minecraft.getInstance().player) {
			Weapon held = ViewmodelAnimator.heldWeapon();
			Quaternionf rotation = ViewmodelAnimator.weaponTurn(partialTicks);
			return held != null && rotation != null ? new Turn(held, rotation) : null;
		}

		ItemStack used = avatar.getUseItem();

		if (avatar.isUsingItem() && avatar.getUsedItemHand() == InteractionHand.MAIN_HAND && Weapon.of(used) == Weapon.AWP
				&& !CrossbowItem.isCharged(used)) {
			float progress = avatar.getTicksUsingItem(partialTicks) / Math.max(CrossbowItem.getChargeDuration(used, avatar), 1);
			Quaternionf rotation = Viewmodels.turn(Weapon.AWP, "reload", Mth.clamp(progress, 0.0F, 1.0F));
			return rotation != null ? new Turn(Weapon.AWP, rotation) : null;
		}

		return null;
	}
}
