package com.bayonet.mixin;

import com.bayonet.Settings;
import com.bayonet.render.GltfSpecialRenderer;
import com.bayonet.render.Wear;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.SpecialModelWrapper;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SpecialModelWrapper.class)
abstract class SpecialModelWrapperMixin {
	@Shadow
	@Final
	private SpecialModelRenderer<?> specialRenderer;

	@WrapWithCondition(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;setAnimated()V"))
	private boolean bayonet$animatedOnlyWhenNeeded(ItemStackRenderState output) {
		if (!(this.specialRenderer instanceof GltfSpecialRenderer)) {
			return true;
		}

		output.appendModelIdentityElement(Settings.glint());
		output.appendModelIdentityElement(Wear.generation());
		return Settings.glint() || Wear.busy();
	}
}
