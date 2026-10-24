package com.bayonet.render;

import com.bayonet.util.ModIds;
import com.bayonet.weapon.Weapon;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;

public final class ItemFrameShowcase {
	private static final RenderStateDataKey<Boolean> SHOWCASE = RenderStateDataKey.create(ModIds.of("showcase")::toString);
	private static final float FRAMED_ITEM_SCALE = 0.5F;
	private static final float MODEL_HALF_EXTENT = 0.5F;
	private static final float SCALE = 4.0F;
	private static final float LIFT = -0.17F;
	public static final double CULL_MARGIN = FRAMED_ITEM_SCALE * MODEL_HALF_EXTENT * SCALE;

	private ItemFrameShowcase() {
	}

	public static boolean applies(ItemStack framed) {
		return Weapon.of(framed) == Weapon.AWP;
	}

	public static void extract(ItemFrame frame, ItemFrameRenderState state) {
		if (applies(frame.getItem())) {
			state.setData(SHOWCASE, true);
			state.isInvisible = true;
		}
	}

	public static void enlarge(ItemFrameRenderState state, PoseStack poseStack) {
		if (Boolean.TRUE.equals(state.getData(SHOWCASE))) {
			poseStack.translate(0.0F, 0.0F, LIFT);
			poseStack.scale(SCALE, SCALE, SCALE);
		}
	}
}
