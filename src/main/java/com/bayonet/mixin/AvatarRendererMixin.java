package com.bayonet.mixin;

import com.bayonet.viewmodel.ThirdPerson;
import com.bayonet.viewmodel.Viewmodels;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AvatarRenderer.class)
abstract class AvatarRendererMixin {
	@Inject(
		method = "getArmPose(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/client/model/HumanoidModel$ArmPose;",
		at = @At("HEAD"),
		cancellable = true
	)
	private static void bayonet$aimAwp(Avatar avatar, ItemStack itemInHand, InteractionHand hand, CallbackInfoReturnable<HumanoidModel.ArmPose> cir) {
		if (Viewmodels.Weapon.of(itemInHand) == Viewmodels.Weapon.AWP && !(avatar.isUsingItem() && avatar.getUsedItemHand() != hand)) {
			cir.setReturnValue(HumanoidModel.ArmPose.CROSSBOW_HOLD);
		}
	}

	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
		at = @At("TAIL")
	)
	private void bayonet$extractWeaponTurn(Avatar avatar, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		ThirdPerson.Turn turn = ThirdPerson.weaponTurn(avatar, partialTicks);

		if (turn != null) {
			state.setData(ThirdPerson.WEAPON_TURN, turn);
		}
	}
}
