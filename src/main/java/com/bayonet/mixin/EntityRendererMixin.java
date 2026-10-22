package com.bayonet.mixin;

import com.bayonet.viewmodel.Viewmodels;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
abstract class EntityRendererMixin {
	@Inject(method = "getBoundingBoxForCulling", at = @At("RETURN"), cancellable = true)
	private void bayonet$wideShowcase(Entity entity, CallbackInfoReturnable<AABB> cir) {
		if (entity instanceof ItemFrame frame && Viewmodels.Weapon.of(frame.getItem()) == Viewmodels.Weapon.AWP) {
			cir.setReturnValue(cir.getReturnValue().inflate(1.0));
		}
	}
}
