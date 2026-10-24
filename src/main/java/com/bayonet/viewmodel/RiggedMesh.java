package com.bayonet.viewmodel;

import com.bayonet.gltf.GltfScene;
import com.bayonet.gltf.PosedMesh;
import com.bayonet.render.GltfTextures;
import com.bayonet.render.MeshEmitter;
import com.bayonet.render.Reflections;
import com.bayonet.weapon.Skin;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

final class RiggedMesh {
	private final GltfScene model;
	private final Matrix4f[] restLocal;
	private final Matrix4f[] world;
	private final GltfTextures textures;
	private final PosedMesh posed;

	RiggedMesh(Identifier id, GltfScene model) {
		this.model = model;
		this.restLocal = model.restLocalMatrices();
		this.world = new Matrix4f[this.restLocal.length];
		this.textures = new GltfTextures(id, model.images());
		this.posed = new PosedMesh(model);

		for (int n = 0; n < this.world.length; n++) {
			this.world[n] = new Matrix4f();
		}
	}

	@FunctionalInterface
	interface GlowingParts {
		GlowingParts NONE = part -> null;

		float @Nullable [] uvs(int part);
	}

	int[] bind(GltfScene rig) {
		int[] toRig = new int[this.model.nodes().size()];

		for (int n = 0; n < toRig.length; n++) {
			toRig[n] = rig.findNode(this.model.nodes().get(n).name());
		}

		return toRig;
	}

	Matrix4f[] place(int[] toRig, Matrix4f[] rigWorld, Matrix4fc attach) {
		for (int n : this.model.order()) {
			int rigNode = toRig[n];

			if (rigNode >= 0) {
				attach.mul(rigWorld[rigNode], this.world[n]);
			} else {
				this.placeAtRest(n, attach);
			}
		}

		return this.world;
	}

	void place(Matrix4fc attach) {
		for (int n : this.model.order()) {
			this.placeAtRest(n, attach);
		}
	}

	void pose() {
		this.posed.pose(this.world);
	}

	float[] positions(int part) {
		return this.posed.positions(part);
	}

	void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, float reflections, float wear, boolean glint) {
		this.submit(poseStack, submitNodeCollector, lightCoords, reflections, wear, glint, GlowingParts.NONE);
	}

	void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, float reflections, GlowingParts glowing) {
		this.submit(poseStack, submitNodeCollector, lightCoords, reflections, Skin.UNWORN, false, glowing);
	}

	private void submit(
		PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, float reflections, float wear, boolean glint, GlowingParts glowing
	) {
		for (int p = 0; p < this.model.parts().size(); p++) {
			GltfScene.Part part = this.model.parts().get(p);
			float[] glowingUvs = glowing.uvs(p);
			RenderType renderType = glowingUvs != null ? this.textures.glowing(part.material()) : this.textures.renderType(part.material(), wear);

			if (renderType == null) {
				continue;
			}

			float[] positions = this.posed.positions(p);
			float[] normals = this.posed.normals(p);
			float[] uvs = glowingUvs != null ? glowingUvs : part.uvs();
			int light = glowingUvs != null ? LightCoordsUtil.FULL_BRIGHT : lightCoords;
			MeshEmitter.transform(poseStack.last(), positions, normals);
			SubmitNodeCollector.CustomGeometryRenderer mesh = (pose, buffer) -> MeshEmitter.emitTransformed(
				buffer, positions, normals, uvs, part.indices(), part.material().color(), light, OverlayTexture.NO_OVERLAY
			);
			submitNodeCollector.submitCustomGeometry(poseStack, renderType, mesh);

			if (glint) {
				submitNodeCollector.submitCustomGeometry(poseStack, RenderTypes.entityGlint(), mesh);
			}

			float[] reflectivity = part.reflectivity();
			RenderType reflection = reflectivity != null && reflections > 0.0F ? Reflections.renderType() : null;

			if (reflection != null) {
				submitNodeCollector.submitCustomGeometry(poseStack, reflection, (pose, buffer) -> Reflections.emit(
					buffer, positions, normals, reflectivity, part.indices(), lightCoords, reflections
				));
			}
		}
	}

	private void placeAtRest(int node, Matrix4fc attach) {
		int parent = this.model.nodes().get(node).parent();
		Matrix4fc base = parent >= 0 ? this.world[parent] : attach;
		base.mul(this.restLocal[node], this.world[node]);
	}
}
