package com.bayonet.render;

import com.bayonet.Settings;
import com.bayonet.gltf.GlbLoader;
import com.bayonet.gltf.GltfModel;
import com.bayonet.viewmodel.Skin;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.InputStream;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class GltfSpecialRenderer implements SpecialModelRenderer<Float> {
	private static final Logger LOGGER = LogUtils.getLogger();

	private final GltfModel model;
	private final GltfTextures textures;
	private final float[] bounds;

	public GltfSpecialRenderer(Identifier modelId, GltfModel model) {
		this.model = model;
		this.textures = new GltfTextures(modelId.withPath(path -> "gltf/" + path), model.images());
		float[] bounds = model.bounds();
		this.bounds = bounds != null ? bounds : new float[6];
	}

	@Override
	public @Nullable Float extractArgument(ItemStack stack) {
		Skin.Look look = Skin.of(stack);
		return look != null ? look.wear() : null;
	}

	@Override
	public void submit(
		@Nullable Float wear, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, int overlayCoords, boolean hasFoil, int outlineColor
	) {
		boolean glint = hasFoil && Settings.glint();

		for (GltfModel.Part part : this.model.parts()) {
			Identifier texture = this.textures.get(part.image(), part.wearMaps(), wear != null ? wear : 0.0F);
			submitNodeCollector.submitCustomGeometry(
				poseStack, GltfTextures.renderType(texture, part.cutout()), (pose, buffer) -> emit(part, pose, buffer, lightCoords, overlayCoords)
			);

			if (glint) {
				submitNodeCollector.submitCustomGeometry(poseStack, RenderTypes.entityGlint(), (pose, buffer) -> emit(part, pose, buffer, lightCoords, overlayCoords));
			}
		}
	}

	private static void emit(GltfModel.Part part, PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay) {
		float[] p = part.positions();
		float[] n = part.normals();
		float[] uv = part.uvs();
		int color = part.color();
		Matrix4f matrix = pose.pose();
		Vector3f position = new Vector3f();
		Vector3f normal = new Vector3f();
		int vertexCount = part.vertexCount();

		for (int v = 0; v < vertexCount; v++) {
			matrix.transformPosition(p[v * 3], p[v * 3 + 1], p[v * 3 + 2], position);
			pose.transformNormal(n[v * 3], n[v * 3 + 1], n[v * 3 + 2], normal);
			int copies = v % 3 == 2 ? 2 : 1;

			for (int copy = 0; copy < copies; copy++) {
				buffer.addVertex(position.x, position.y, position.z, color, uv[v * 2], uv[v * 2 + 1], overlay, light, normal.x, normal.y, normal.z);
			}
		}
	}

	@Override
	public void getExtents(Consumer<Vector3fc> output) {
		float[] b = this.bounds;

		for (int corner = 0; corner < 8; corner++) {
			output.accept(new Vector3f((corner & 1) == 0 ? b[0] : b[3], (corner & 2) == 0 ? b[1] : b[4], (corner & 4) == 0 ? b[2] : b[5]));
		}
	}

	public record Unbaked(Identifier model, Vector3fc rotation) implements SpecialModelRenderer.Unbaked<Float> {
		public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(
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
			Identifier file = this.model.withPath(path -> "models/gltf/" + path + ".glb");

			try (InputStream stream = Minecraft.getInstance().getResourceManager().open(file)) {
				Matrix4f rotation = new Matrix4f().rotationXYZ(
					(float) Math.toRadians(this.rotation.x()), (float) Math.toRadians(this.rotation.y()), (float) Math.toRadians(this.rotation.z())
				);
				GltfModel gltf = GlbLoader.load(stream.readAllBytes()).fitToUnitCube(rotation);
				LOGGER.info("Loaded glTF model {}: {} parts, {} images", file, gltf.parts().size(), gltf.images().size());
				return new GltfSpecialRenderer(this.model, gltf);
			} catch (Exception e) {
				LOGGER.error("Failed to load glTF model {}", file, e);
				return null;
			}
		}
	}
}
