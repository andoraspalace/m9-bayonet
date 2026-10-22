package com.bayonet.mixin;

import com.bayonet.viewmodel.Viewmodels;
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
	private Object bayonet$weaponDefinition(Object modelId, @Local(argsOnly = true) ItemStack stack) {
		Object definition = Viewmodels.Weapon.definition(stack);
		return definition != null ? definition : modelId;
	}
}
