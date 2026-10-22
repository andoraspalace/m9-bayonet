package com.bayonet.mixin;

import com.bayonet.viewmodel.Viewmodels;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ItemFrame;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemFrameRenderer.class)
abstract class ItemFrameRendererMixin {
	@Unique
	private static final RenderStateDataKey<Boolean> SHOWCASE = RenderStateDataKey.create(() -> "bayonet:showcase");
	@Unique
	private static final float SHOWCASE_SCALE = 4.0F;
	@Unique
	private static final float SHOWCASE_LIFT = -0.17F;

	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/decoration/ItemFrame;Lnet/minecraft/client/renderer/entity/state/ItemFrameRenderState;F)V",
		at = @At("TAIL")
	)
	private void bayonet$showcase(ItemFrame frame, ItemFrameRenderState state, float partialTicks, CallbackInfo ci) {
		if (Viewmodels.Weapon.of(frame.getItem()) == Viewmodels.Weapon.AWP) {
			state.setData(SHOWCASE, true);
			state.isInvisible = true;
			state.frameModel.clear();
		}
	}

	@ModifyExpressionValue(
		method = "shouldShowName(Lnet/minecraft/world/entity/decoration/ItemFrame;D)Z",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getCustomName()Lnet/minecraft/network/chat/Component;")
	)
	private @Nullable Component bayonet$weaponName(@Nullable Component customName, @Local(argsOnly = true) ItemFrame frame) {
		return customName == null && Viewmodels.Weapon.of(frame.getItem()) != null ? frame.getItem().getHoverName() : customName;
	}

	@WrapOperation(
		method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemFrameRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"
		)
	)
	private void bayonet$showcaseSize(
		ItemStackRenderState item, PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, int overlayCoords, int outlineColor,
		Operation<Void> original, @Local(argsOnly = true) ItemFrameRenderState state
	) {
		if (Boolean.TRUE.equals(state.getData(SHOWCASE))) {
			poseStack.translate(0.0F, 0.0F, SHOWCASE_LIFT);
			poseStack.scale(SHOWCASE_SCALE, SHOWCASE_SCALE, SHOWCASE_SCALE);
		}

		original.call(item, poseStack, collector, lightCoords, overlayCoords, outlineColor);
	}
}
