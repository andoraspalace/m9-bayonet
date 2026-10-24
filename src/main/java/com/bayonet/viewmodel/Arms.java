package com.bayonet.viewmodel;

import com.bayonet.gltf.GltfScene;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;

final class Arms {
	private static final Pattern FOREARM_TWIST = Pattern.compile("arm_lower_([LR])_TWIST\\d*");
	private static final String HAND_PREFIX = "hand_";
	private static final float MIN_ROLL = 1.0E-4F;

	private final List<Piece> pieces;

	Arms(List<Piece> pieces) {
		this.pieces = List.copyOf(pieces);
	}

	Bound bind(GltfScene rig) {
		int[][] toRig = new int[this.pieces.size()][];

		for (int i = 0; i < toRig.length; i++) {
			toRig[i] = this.pieces.get(i).mesh.bind(rig);
		}

		return new Bound(this.pieces, toRig);
	}

	static final class Bound {
		private final List<Piece> pieces;
		private final int[][] toRig;

		private Bound(List<Piece> pieces, int[][] toRig) {
			this.pieces = pieces;
			this.toRig = toRig;
		}

		void submit(Matrix4f[] rigWorld, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, float reflections) {
			for (int i = 0; i < this.pieces.size(); i++) {
				this.pieces.get(i).submit(this.toRig[i], rigWorld, poseStack, submitNodeCollector, lightCoords, reflections);
			}
		}
	}

	static final class Piece {
		private final RiggedMesh mesh;
		private final List<Twist> twists;
		private final Matrix4f relative = new Matrix4f();
		private final Matrix4f local = new Matrix4f();
		private final Quaternionf delta = new Quaternionf();
		private final Quaternionf roll = new Quaternionf();
		private final Quaternionf rotation = new Quaternionf();

		Piece(Identifier id, GltfScene model) {
			this.mesh = new RiggedMesh(id, model);
			this.twists = twists(model);
		}

		private static List<Twist> twists(GltfScene model) {
			List<Twist> twists = new ArrayList<>();

			for (int n = 0; n < model.nodes().size(); n++) {
				GltfScene.Node node = model.nodes().get(n);
				Matcher twist = FOREARM_TWIST.matcher(node.name());
				int forearm = node.parent();
				int hand = twist.matches() ? model.findNode(HAND_PREFIX + twist.group(1)) : -1;

				if (hand >= 0 && forearm >= 0 && model.nodes().get(hand).parent() == forearm) {
					GltfScene.Node handNode = model.nodes().get(hand);
					float share = Math.abs(node.translation().x()) / Math.abs(handNode.translation().x());
					twists.add(new Twist(n, forearm, hand, Math.min(1.0F, share), node, new Quaternionf(handNode.rotation()).invert()));
				}
			}

			return List.copyOf(twists);
		}

		private void submit(int[] toRig, Matrix4f[] rigWorld, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, float reflections) {
			Matrix4f[] world = this.mesh.place(toRig, rigWorld, RigSpace.TO_CAMERA);

			for (Twist twist : this.twists) {
				world[twist.forearm()].mul(this.twistLocal(twist, world), world[twist.node()]);
			}

			this.mesh.pose();
			this.mesh.submit(poseStack, submitNodeCollector, lightCoords, reflections, RiggedMesh.GlowingParts.NONE);
		}

		private Matrix4f twistLocal(Twist twist, Matrix4f[] world) {
			Quaternionf handRotation = world[twist.forearm()].invert(this.relative).mul(world[twist.hand()]).getNormalizedRotation(this.delta);
			Quaternionf delta = handRotation.mul(twist.handRestInverse());
			float length = (float) Math.sqrt(delta.x * delta.x + delta.w * delta.w);
			Quaternionf roll = length > MIN_ROLL ? this.roll.set(delta.x / length, 0.0F, 0.0F, delta.w / length) : this.roll.identity();
			Quaternionf rotation = this.rotation.identity().slerp(roll, twist.share()).mul(twist.rest().rotation());
			return this.local.translationRotateScale(twist.rest().translation(), rotation, twist.rest().scale());
		}
	}

	private record Twist(int node, int forearm, int hand, float share, GltfScene.Node rest, Quaternionfc handRestInverse) {
	}
}
