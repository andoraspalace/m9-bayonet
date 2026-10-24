package com.bayonet.render;

import com.bayonet.util.ModIds;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

public final class Reflections {
	private static final Identifier ENVIRONMENT = ModIds.of("viewmodel/reflection_environment");
	private static final int ENVIRONMENT_SIZE = 256;
	private static final float RANGE = 3.0F;
	private static final float SPHERE_MAP_CENTER = 0.5F;
	private static final float MATTE_REFLECTION = 0.3F;
	private static final float GLOSS_REFLECTION = 0.7F;
	private static final Vector3fc SKY_COLOR = new Vector3f(0.75F, 0.85F, 1.0F);
	private static final float SKY_BASE = 0.35F;
	private static final float SKY_GRADIENT = 0.45F;
	private static final Vector3fc GROUND_COLOR = new Vector3f(0.5F, 0.47F, 0.42F);
	private static final float GROUND_BASE = 0.06F;
	private static final float GROUND_BOUNCE = 0.1F;
	private static final float GROUND_FALLOFF = 6.0F;
	private static final Vector3fc HORIZON_TINT = new Vector3f(0.95F, 0.97F, 1.0F);
	private static final float HORIZON_INTENSITY = 0.8F;
	private static final float HORIZON_HEIGHT = 0.05F;
	private static final float HORIZON_WIDTH = 0.08F;
	private static final Glow KEY = new Glow(new Vector3f(-0.3F, 0.75F, -0.6F).normalize(), new Vector3f(1.0F, 0.96F, 0.9F), 4.0F, 0.05F);
	private static final Glow RIM = new Glow(new Vector3f(0.95F, 0.2F, 0.25F).normalize(), new Vector3f(0.85F, 0.92F, 1.0F), 1.8F, 0.03F);
	private static final Glow FILL = new Glow(new Vector3f(-0.2F, 0.3F, 1.0F).normalize(), new Vector3f(1.0F, 1.0F, 1.0F), 0.6F, 0.25F);
	private static final Vector3f EYE = new Vector3f();
	private static final Vector3f NORMAL = new Vector3f();
	private static @Nullable RenderType renderType;
	private static float[] reflectedUvs = new float[0];
	private static int[] reflectedColors = new int[0];

	private Reflections() {
	}

	private record Glow(Vector3fc direction, Vector3fc tint, float intensity, float spread) {
	}

	public static @Nullable RenderType renderType() {
		if (TextureLoader.request(ENVIRONMENT, Reflections::environment) == null) {
			return null;
		}

		if (renderType == null) {
			renderType = RenderTypes.energySwirl(ENVIRONMENT, 0.0F, 0.0F);
		}

		return renderType;
	}

	public static float brightness(int lightCoords) {
		ClientLevel level = Minecraft.getInstance().level;

		if (level == null) {
			return 0.0F;
		}

		int sky = LightCoordsUtil.sky(lightCoords) - level.getSkyDarken();
		return Lightmap.getBrightness(level.dimensionType(), Math.max(LightCoordsUtil.block(lightCoords), sky));
	}

	public static void emit(VertexConsumer buffer, float[] positions, float[] normals, float[] reflectivity, int[] indices, int light, float brightness) {
		int vertexCount = positions.length / 3;

		if (reflectedColors.length < vertexCount) {
			reflectedUvs = new float[vertexCount * 2];
			reflectedColors = new int[vertexCount];
		}

		for (int v = 0; v < vertexCount; v++) {
			Vector3f eye = EYE.set(positions[v * 3], positions[v * 3 + 1], positions[v * 3 + 2]).normalize();
			Vector3f normal = NORMAL.set(normals[v * 3], normals[v * 3 + 1], normals[v * 3 + 2]);
			float along = eye.dot(normal);
			float rx = eye.x - 2.0F * along * normal.x;
			float ry = eye.y - 2.0F * along * normal.y;
			float rz = eye.z - 2.0F * along * normal.z;
			float m = 2.0F * (float) Math.sqrt(rx * rx + ry * ry + (rz + 1.0F) * (rz + 1.0F));
			reflectedUvs[v * 2] = m > 0.0F ? rx / m + SPHERE_MAP_CENTER : SPHERE_MAP_CENTER;
			reflectedUvs[v * 2 + 1] = m > 0.0F ? SPHERE_MAP_CENTER - ry / m : 0.0F;

			float facing = 1.0F - Mth.clamp(-along, 0.0F, 1.0F);
			float facingSquared = facing * facing;
			float fresnel = facingSquared * facingSquared * facing;
			float gloss = reflectivity[v * 4 + 3];
			float scale = brightness * (MATTE_REFLECTION + GLOSS_REFLECTION * gloss) * RANGE;
			float r = schlick(reflectivity[v * 4], gloss, fresnel) * scale;
			float g = schlick(reflectivity[v * 4 + 1], gloss, fresnel) * scale;
			float b = schlick(reflectivity[v * 4 + 2], gloss, fresnel) * scale;
			float over = Math.max(1.0F, Math.max(r, Math.max(g, b)));
			reflectedColors[v] = ARGB.colorFromFloat(1.0F, r / over, g / over, b / over);
		}

		MeshEmitter.emitTransformedVertexColors(buffer, positions, normals, reflectedUvs, indices, reflectedColors, light, OverlayTexture.NO_OVERLAY);
	}

	private static float schlick(float specular, float gloss, float fresnel) {
		return specular + (Math.max(gloss, specular) - specular) * fresnel;
	}

	private static NativeImage environment() {
		NativeImage image = new NativeImage(ENVIRONMENT_SIZE, ENVIRONMENT_SIZE, false);
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
					color.set(SKY_COLOR).mul(SKY_BASE + SKY_GRADIENT * up);
				} else {
					color.set(GROUND_COLOR).mul(GROUND_BASE + GROUND_BOUNCE * (float) Math.exp(up * GROUND_FALLOFF));
				}

				add(color, HORIZON_TINT, HORIZON_INTENSITY * (float) Math.exp(-Mth.square((up - HORIZON_HEIGHT) / HORIZON_WIDTH)));
				add(color, KEY.tint(), glow(KEY, reflected));
				add(color, RIM.tint(), glow(RIM, reflected));
				add(color, FILL.tint(), glow(FILL, reflected));
				color.div(RANGE);
				image.setPixel(x, y, ARGB.colorFromFloat(1.0F, Math.min(color.x, 1.0F), Math.min(color.y, 1.0F), Math.min(color.z, 1.0F)));
			}
		}

		return image;
	}

	private static float glow(Glow glow, Vector3fc reflected) {
		return glow.intensity() * (float) Math.exp((reflected.dot(glow.direction()) - 1.0F) / glow.spread());
	}

	private static void add(Vector3f color, Vector3fc tint, float amount) {
		color.add(tint.x() * amount, tint.y() * amount, tint.z() * amount);
	}
}
