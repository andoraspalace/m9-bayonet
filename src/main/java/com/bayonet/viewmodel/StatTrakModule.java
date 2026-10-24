package com.bayonet.viewmodel;

import com.bayonet.gltf.GltfScene;
import com.bayonet.util.SourceUnits;
import com.google.common.math.IntMath;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

final class StatTrakModule {
	private static final String ATTACH_BONE = "weapon_offset";
	private static final Matrix4fc GLTF_TO_SOURCE = new Matrix4f()
			.set(new Matrix3f(new Vector3f(0.0F, 1.0F, 0.0F), new Vector3f(0.0F, 0.0F, 1.0F), new Vector3f(1.0F, 0.0F, 0.0F)));
	private static final Matrix4fc ATTACHMENT = new Matrix4f()
			.translation(new Vector3f(-4.276766F, 0.790415F, -1.474666F).mul((float) SourceUnits.METERS_PER_INCH))
			.mul(GLTF_TO_SOURCE);
	private static final int RADIX = 10;
	private static final int DIGITS = 6;
	private static final int MAX_VALUE = IntMath.pow(RADIX, DIGITS) - 1;
	private static final float DIGIT_STRIP_MIN_U = 0.999F;
	private static final float ATLAS_COLUMN = 1.0F / 16.0F;

	private final RiggedMesh mesh;
	private final @Nullable DigitStrip[] strips;
	private final int[] digits = new int[DIGITS + 1];
	private final Matrix4f attach = new Matrix4f();
	private final RiggedMesh.GlowingParts glowingDigits = this::digitUvs;

	StatTrakModule(Identifier id, GltfScene model) {
		this.mesh = new RiggedMesh(id, model);
		this.strips = new DigitStrip[model.parts().size()];

		for (int p = 0; p < this.strips.length; p++) {
			this.strips[p] = DigitStrip.of(model.parts().get(p).uvs());
		}
	}

	@Nullable Mount mountOn(GltfScene weapon) {
		int bone = weapon.findNode(ATTACH_BONE);
		return bone >= 0 ? new Mount(this, bone) : null;
	}

	record Mount(StatTrakModule module, int bone) {
		void submit(Matrix4f[] weaponWorld, int value, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, float reflections) {
			this.module.submit(weaponWorld[this.bone], value, poseStack, submitNodeCollector, lightCoords, reflections);
		}
	}

	private void submit(Matrix4fc attachBone, int value, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, float reflections) {
		this.mesh.place(attachBone.mul(ATTACHMENT, this.attach));
		this.mesh.pose();
		this.showDigits(value);
		this.mesh.submit(poseStack, submitNodeCollector, lightCoords, reflections, this.glowingDigits);
	}

	private float @Nullable [] digitUvs(int part) {
		DigitStrip strip = this.strips[part];
		return strip != null ? strip.uvs(this.digits) : null;
	}

	private void showDigits(int value) {
		for (int slot = DIGITS, remaining = Math.min(value, MAX_VALUE); slot >= 1; slot--, remaining /= RADIX) {
			this.digits[slot] = remaining % RADIX;
		}
	}

	private static final class DigitStrip {
		private final int[] slots;
		private final float[] offsets;
		private final float[] uvs;

		private DigitStrip(int[] slots, float[] offsets, float[] uvs) {
			this.slots = slots;
			this.offsets = offsets;
			this.uvs = uvs;
		}

		static @Nullable DigitStrip of(float[] uvs) {
			boolean strip = uvs.length > 0;

			for (int i = 0; i < uvs.length && strip; i += 2) {
				strip = uvs[i] >= DIGIT_STRIP_MIN_U;
			}

			if (!strip) {
				return null;
			}

			int vertexCount = uvs.length / 2;
			int[] slots = new int[vertexCount];
			float[] offsets = new float[vertexCount];
			float[] shown = new float[uvs.length];

			for (int vertex = 0; vertex < vertexCount; vertex++) {
				float u = uvs[vertex * 2];
				float v = uvs[vertex * 2 + 1];
				int slot = Math.clamp((int) Math.floor(u), 1, DIGITS);
				slots[vertex] = slot;
				offsets[vertex] = u - slot - (slot - 1) * ATLAS_COLUMN;
				shown[vertex * 2 + 1] = v - (float) Math.floor(v);
			}

			return new DigitStrip(slots, offsets, shown);
		}

		float[] uvs(int[] digits) {
			for (int vertex = 0; vertex < this.slots.length; vertex++) {
				this.uvs[vertex * 2] = (digits[this.slots[vertex]] + 1) * ATLAS_COLUMN + this.offsets[vertex];
			}

			return this.uvs;
		}
	}
}
