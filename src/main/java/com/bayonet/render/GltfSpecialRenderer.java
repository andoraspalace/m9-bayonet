package com.bayonet.render;

import com.bayonet.config.Settings;
import com.bayonet.gltf.GltfModel;
import com.bayonet.weapon.Skin;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.IOException;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class GltfSpecialRenderer implements SpecialModelRenderer<GltfSpecialRenderer.Appearance> {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Appearance PLAIN = new Appearance(Skin.UNWORN, false, 0);

	private final GltfModel model;
	private final GltfTextures textures;
	private final GltfModel.Bounds bounds;

	private GltfSpecialRenderer(Identifier modelId, GltfModel model) {
		this.model = model;
		this.textures = new GltfTextures(modelId, model.images());
		this.bounds = model.bounds();
	}

	public record Appearance(float wear, boolean glint, int textureGeneration) {
	}

	@Override
	public Appearance extractArgument(ItemStack stack) {
		return new Appearance(Skin.wearOf(Skin.of(stack)), Settings.glint(), TextureLoader.generation());
	}

	@Override
	public void submit(
		@Nullable Appearance argument, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, int overlayCoords, boolean hasFoil, int outlineColor
	) {
		Appearance appearance = argument != null ? argument : PLAIN;

		for (GltfModel.Part part : this.model.parts()) {
			RenderType renderType = this.textures.renderType(part.material(), appearance.wear(), hasFoil && appearance.glint());

			if (renderType == null) {
				continue;
			}

			submitNodeCollector.submitCustomGeometry(poseStack, renderType, (pose, buffer) -> MeshEmitter.emit(
				pose, buffer, part.positions(), part.normals(), part.uvs(), part.indices(), part.material().color(), lightCoords, overlayCoords
			));

			if (outlineColor != 0) {
				renderType.outline().ifPresent(outline -> submitNodeCollector.submitCustomGeometry(poseStack, outline, (pose, buffer) -> MeshEmitter.emit(
					pose, buffer, part.positions(), part.normals(), part.uvs(), part.indices(), outlineColor, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY
				)));
			}
		}
	}

	@Override
	public void getExtents(Consumer<Vector3fc> output) {
		this.bounds.corners(output);
	}

	record Unbaked(Identifier model, Vector3fc rotation) implements SpecialModelRenderer.Unbaked<Appearance> {
		static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(
			i -> i.group(
					Identifier.CODEC.fieldOf("model").forGetter(Unbaked::model),
					ExtraCodecs.VECTOR3F.optionalFieldOf("rotation", new Vector3f()).forGetter(Unbaked::rotation)
				)
				.apply(i, Unbaked::new)
		);

		@Override
		public MapCodec<Unbaked> type() {
			return MAP_CODEC;
		}

		@Override
		public @Nullable GltfSpecialRenderer bake(SpecialModelRenderer.BakingContext context) {
			Matrix4f rotation = new Matrix4f().rotationXYZ(
				(float) Math.toRadians(this.rotation.x()), (float) Math.toRadians(this.rotation.y()), (float) Math.toRadians(this.rotation.z())
			);

			try {
				GltfModel gltf = GltfModel.restPose(SceneCache.load(Minecraft.getInstance().getResourceManager(), this.model)).fitToUnitCube(rotation);
				LOGGER.info("Loaded glTF model {}: {} parts, {} images", this.model, gltf.parts().size(), gltf.images().size());
				return new GltfSpecialRenderer(this.model, gltf);
			} catch (IOException | RuntimeException e) {
				LOGGER.error("Failed to load glTF model {}", this.model, e);
				return null;
			}
		}
	}
}
