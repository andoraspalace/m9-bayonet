package com.bayonet.mixin;

import com.bayonet.render.ItemFrameShowcase;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderer.class)
abstract class EntityRendererMixin {
	@ModifyReturnValue(method = "getBoundingBoxForCulling", at = @At("RETURN"))
	private AABB bayonet$wideShowcase(AABB box, @Local(argsOnly = true) Entity entity) {
		return entity instanceof ItemFrame frame && ItemFrameShowcase.applies(frame.getItem()) ? box.inflate(ItemFrameShowcase.CULL_MARGIN) : box;
	}
}
