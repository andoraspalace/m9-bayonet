package com.csmod.viewmodel;

import com.csmod.CsModClient;
import com.csmod.gltf.GltfScene;
import com.csmod.render.GltfTextures;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

final class Reflections {
	private static final Identifier ENVIRONMENT = CsModClient.id("viewmodel/reflection_environment");
	private static final int ENVIRONMENT_SIZE = 256;
	private static final float RANGE = 3.0F;
	private static final int[] QUAD_CORNERS = {0, 1, 2, 2};
	private static @Nullable RenderType renderType;

	private Reflections() {
	}

	static RenderType renderType() {
		if (renderType == null) {
			GltfTextures.register(ENVIRONMENT, environment());
			renderType = RenderTypes.energySwirl(ENVIRONMENT, 0.0F, 0.0F);
		}

		return renderType;
	}

	static float brightness(int lightCoords) {
		ClientLevel level = Minecraft.getInstance().level;
		int sky = LightCoordsUtil.sky(lightCoords) - (level != null ? level.getSkyDarken() : 0);
		float light = Math.max(LightCoordsUtil.block(lightCoords), sky) / 15.0F;
		return light / (4.0F - 3.0F * light);
	}

	static void emit(
		GltfScene.Part part, float[] positions, float[] normals, PoseStack.Pose pose, VertexConsumer buffer, int light, float brightness
	) {
		float[] reflectivity = part.reflectivity();
		int vertexCount = part.vertexCount();
		float[] uvs = new float[vertexCount * 2];
		int[] colors = new int[vertexCount];
		Vector3f eye = new Vector3f();
		Vector3f normal = new Vector3f();

		for (int v = 0; v < vertexCount; v++) {
			pose.pose().transformPosition(positions[v * 3], positions[v * 3 + 1], positions[v * 3 + 2], eye).normalize();
			pose.transformNormal(normals[v * 3], normals[v * 3 + 1], normals[v * 3 + 2], normal).normalize();
			float along = eye.dot(normal);
			float rx = eye.x - 2.0F * along * normal.x;
			float ry = eye.y - 2.0F * along * normal.y;
			float rz = eye.z - 2.0F * along * normal.z;
			float m = 2.0F * (float) Math.sqrt(rx * rx + ry * ry + (rz + 1.0F) * (rz + 1.0F));
			uvs[v * 2] = m > 0.0F ? rx / m + 0.5F : 0.5F;
			uvs[v * 2 + 1] = m > 0.0F ? 0.5F - ry / m : 0.0F;

			float fresnel = (float) Math.pow(1.0F - Mth.clamp(-along, 0.0F, 1.0F), 5.0);
			float gloss = reflectivity[v * 4 + 3];
			float scale = brightness * (0.3F + 0.7F * gloss) * RANGE;
			float r = schlick(reflectivity[v * 4], gloss, fresnel) * scale;
			float g = schlick(reflectivity[v * 4 + 1], gloss, fresnel) * scale;
			float b = schlick(reflectivity[v * 4 + 2], gloss, fresnel) * scale;
			float over = Math.max(1.0F, Math.max(r, Math.max(g, b)));
			colors[v] = ARGB.colorFromFloat(1.0F, r / over, g / over, b / over);
		}

		int[] indices = part.indices();

		for (int t = 0; t < indices.length; t += 3) {
			for (int corner : QUAD_CORNERS) {
				int v = indices[t + corner];
				buffer.addVertex(pose, positions[v * 3], positions[v * 3 + 1], positions[v * 3 + 2])
						.setColor(colors[v])
						.setUv(uvs[v * 2], uvs[v * 2 + 1])
						.setOverlay(OverlayTexture.NO_OVERLAY)
						.setLight(light)
						.setNormal(pose, normals[v * 3], normals[v * 3 + 1], normals[v * 3 + 2]);
			}
		}
	}

	private static float schlick(float specular, float gloss, float fresnel) {
		return specular + (Math.max(gloss, specular) - specular) * fresnel;
	}

	private static NativeImage environment() {
		NativeImage image = new NativeImage(ENVIRONMENT_SIZE, ENVIRONMENT_SIZE, false);
		Vector3f key = new Vector3f(-0.3F, 0.75F, -0.6F).normalize();
		Vector3f rim = new Vector3f(0.95F, 0.2F, 0.25F).normalize();
		Vector3f fill = new Vector3f(-0.2F, 0.3F, 1.0F).normalize();
		Vector3f reflected = new Vector3f();
		Vector3f color = new Vector3f();

		for (int y = 0; y < ENVIRONMENT_SIZE; y++) {
			for (int x = 0; x < ENVIRONMENT_SIZE; x++) {
				float nx = (x + 0.5F) / ENVIRONMENT_SIZE * 2.0F - 1.0F;
				float ny = 1.0F - (y + 0.5F) / ENVIRONMENT_SIZE * 2.0F;
				float length = (float) Math.sqrt(nx * nx + ny * ny);

				if (length > 1.0F) {
					nx /= length;
					ny /= length;
				}

				float nz = (float) Math.sqrt(Math.max(0.0F, 1.0F - nx * nx - ny * ny));
				reflected.set(2.0F * nz * nx, 2.0F * nz * ny, 2.0F * nz * nz - 1.0F);

				float up = reflected.y;

				if (up >= 0.0F) {
					color.set(0.75F, 0.85F, 1.0F).mul(0.35F + 0.45F * up);
				} else {
					color.set(0.5F, 0.47F, 0.42F).mul(0.06F + 0.1F * (float) Math.exp(up * 6.0F));
				}

				float horizon = 0.8F * (float) Math.exp(-Mth.square((up - 0.05F) / 0.08F));
				float keyLight = 4.0F * (float) Math.exp((reflected.dot(key) - 1.0F) / 0.05F);
				float rimLight = 1.8F * (float) Math.exp((reflected.dot(rim) - 1.0F) / 0.03F);
				float fillLight = 0.6F * (float) Math.exp((reflected.dot(fill) - 1.0F) / 0.25F);
				color.add(0.95F * horizon, 0.97F * horizon, horizon)
						.add(keyLight, 0.96F * keyLight, 0.9F * keyLight)
						.add(0.85F * rimLight, 0.92F * rimLight, rimLight)
						.add(fillLight, fillLight, fillLight)
						.div(RANGE);
				image.setPixel(x, y, ARGB.colorFromFloat(1.0F, Math.min(color.x, 1.0F), Math.min(color.y, 1.0F), Math.min(color.z, 1.0F)));
			}
		}

		return image;
	}
}
