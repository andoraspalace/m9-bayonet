package com.bayonet.viewmodel;

import com.bayonet.BayonetClient;
import com.bayonet.gltf.GltfScene;
import com.bayonet.render.GltfTextures;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

final class StatTrakModule {
	private static final float INCH = 0.0254F;
	private static final Matrix4fc GLTF_TO_SOURCE = new Matrix4f()
			.set(new Matrix3f(new Vector3f(0.0F, 1.0F, 0.0F), new Vector3f(0.0F, 0.0F, 1.0F), new Vector3f(1.0F, 0.0F, 0.0F)));
	private static final Matrix4fc ATTACHMENT = new Matrix4f().translation(-4.276766F * INCH, 0.790415F * INCH, -1.474666F * INCH).mul(GLTF_TO_SOURCE);
	private static final int DIGITS = 6;
	private static final int MAX_VALUE = 999999;
	private static final float ATLAS_COLUMN = 1.0F / 16.0F;
	private static final int[] QUAD_CORNERS = {0, 1, 2, 2};

	private final GltfScene model;
	private final Matrix4f[] rest;
	private final GltfTextures textures;
	private final boolean[] display;

	StatTrakModule(GltfScene model) {
		this.model = model;
		this.rest = model.worldMatrices(model.restLocalMatrices());
		this.textures = new GltfTextures(BayonetClient.id("gltf/stattrak_module"), model.images());
		this.display = new boolean[model.parts().size()];

		for (int p = 0; p < this.display.length; p++) {
			float[] uvs = model.parts().get(p).uvs();
			boolean digits = uvs.length > 0;

			for (int i = 0; i < uvs.length && digits; i += 2) {
				digits = uvs[i] >= 0.999F;
			}

			this.display[p] = digits;
		}
	}

	void submit(Matrix4fc attachBone, int value, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords) {
		Matrix4f module = new Matrix4f(attachBone).mul(ATTACHMENT);
		int[] digits = new int[DIGITS + 1];

		for (int slot = DIGITS, rest = Math.min(value, MAX_VALUE); slot >= 1; slot--, rest /= 10) {
			digits[slot] = rest % 10;
		}

		for (int p = 0; p < this.model.parts().size(); p++) {
			GltfScene.Part part = this.model.parts().get(p);
			Matrix4f partRest = this.rest[part.node()];
			Matrix4f world = new Matrix4f(module);

			if (partRest != null) {
				world.mul(partRest);
			}

			Identifier texture = this.textures.get(part.image());
			boolean display = this.display[p];
			RenderType renderType = display ? RenderTypes.eyes(texture) : RenderTypes.entitySolid(texture);
			int light = display ? LightCoordsUtil.FULL_BRIGHT : lightCoords;
			submitNodeCollector.submitCustomGeometry(
				poseStack, renderType, (pose, buffer) -> emit(part, world, display ? digits : null, pose, buffer, light)
			);
		}
	}

	private static void emit(GltfScene.Part part, Matrix4f world, int @Nullable [] digits, PoseStack.Pose pose, VertexConsumer buffer, int light) {
		Matrix3f normalMatrix = world.normal(new Matrix3f());
		Vector3f position = new Vector3f();
		Vector3f normal = new Vector3f();
		float[] positions = part.positions();
		float[] normals = part.normals();
		float[] uvs = part.uvs();
		int[] indices = part.indices();

		for (int t = 0; t < indices.length; t += 3) {
			for (int corner : QUAD_CORNERS) {
				int v = indices[t + corner];
				world.transformPosition(positions[v * 3], positions[v * 3 + 1], positions[v * 3 + 2], position);

				if (normals != null) {
					normalMatrix.transform(normals[v * 3], normals[v * 3 + 1], normals[v * 3 + 2], normal).normalize();
				} else {
					normal.set(0.0F, 1.0F, 0.0F);
				}

				float u = uvs[v * 2];
				float uv = uvs[v * 2 + 1];

				if (digits != null) {
					int slot = Math.clamp((int) Math.floor(u), 1, DIGITS);
					float within = u - slot - (slot - 1) * ATLAS_COLUMN;
					u = (digits[slot] + 1) * ATLAS_COLUMN + within;
					uv = uv - (float) Math.floor(uv);
				}

				buffer.addVertex(pose, position.x, position.y, position.z)
						.setColor(-1)
						.setUv(u, uv)
						.setOverlay(OverlayTexture.NO_OVERLAY)
						.setLight(light)
						.setNormal(pose, normal.x, normal.y, normal.z);
			}
		}
	}
}
