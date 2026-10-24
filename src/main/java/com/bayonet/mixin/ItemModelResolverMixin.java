package com.bayonet.mixin;

import com.bayonet.weapon.Skin;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemModelResolver.class)
abstract class ItemModelResolverMixin {
	@ModifyExpressionValue(
		method = {"appendItemLayers", "shouldPlaySwapAnimation", "swapAnimationScale"},
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"
		)
	)
	private Object bayonet$skinModel(Object modelId, @Local(argsOnly = true) ItemStack stack) {
		Skin skin = Skin.find(stack);
		return skin != null ? skin.definition() : modelId;
	}
}
