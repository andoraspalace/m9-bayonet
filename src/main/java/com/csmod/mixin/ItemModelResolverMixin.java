package com.csmod.mixin;

import com.csmod.viewmodel.Viewmodels;
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
	private Object csmod$weaponDefinition(Object modelId, @Local(argsOnly = true) ItemStack stack) {
		Viewmodels.Weapon weapon = Viewmodels.Weapon.of(stack);
		return weapon != null ? weapon.definition : modelId;
	}
}
