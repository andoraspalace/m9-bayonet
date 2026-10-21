package com.csmod.render;

import com.csmod.gltf.GlbLoader;
import com.csmod.gltf.GltfModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.InputStream;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class GltfSpecialRenderer implements NoDataSpecialModelRenderer {
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
	public void submit(
		PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, int overlayCoords, boolean hasFoil, int outlineColor
	) {
		for (GltfModel.Part part : this.model.parts()) {
			Identifier texture = this.textures.get(part.image());
			RenderType renderType = hasFoil
					? RenderTypes.entitySolidGlint(texture)
					: part.cutout() ? RenderTypes.entityCutout(texture) : RenderTypes.entitySolid(texture);
			submitNodeCollector.submitCustomGeometry(poseStack, renderType, (pose, buffer) -> emit(part, pose, buffer, lightCoords, overlayCoords));
		}
	}

	private static void emit(GltfModel.Part part, PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay) {
		int vertexCount = part.vertexCount();

		for (int v = 0; v < vertexCount; v += 3) {
			vertex(part, v, pose, buffer, light, overlay);
			vertex(part, v + 1, pose, buffer, light, overlay);
			vertex(part, v + 2, pose, buffer, light, overlay);
			vertex(part, v + 2, pose, buffer, light, overlay);
		}
	}

	private static void vertex(GltfModel.Part part, int v, PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay) {
		float[] p = part.positions();
		float[] n = part.normals();
		float[] uv = part.uvs();
		buffer.addVertex(pose, p[v * 3], p[v * 3 + 1], p[v * 3 + 2])
				.setColor(part.color())
				.setUv(uv[v * 2], uv[v * 2 + 1])
				.setOverlay(overlay)
				.setLight(light)
				.setNormal(pose, n[v * 3], n[v * 3 + 1], n[v * 3 + 2]);
	}

	@Override
	public void getExtents(Consumer<Vector3fc> output) {
		float[] b = this.bounds;

		for (int corner = 0; corner < 8; corner++) {
			output.accept(new Vector3f((corner & 1) == 0 ? b[0] : b[3], (corner & 2) == 0 ? b[1] : b[4], (corner & 4) == 0 ? b[2] : b[5]));
		}
	}

	public record Unbaked(Identifier model, Vector3fc rotation) implements NoDataSpecialModelRenderer.Unbaked {
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
