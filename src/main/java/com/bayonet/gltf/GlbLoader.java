package com.bayonet.gltf;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class GlbLoader {
	private final JsonObject json;
	private final GltfAccessors accessors;

	private GlbLoader(GlbContainer container) {
		this.json = container.json();
		this.accessors = new GltfAccessors(container.json(), container.bin());
	}

	public static GltfScene loadScene(byte[] data) throws IOException {
		return new GlbLoader(GlbContainer.parse(data)).read();
	}

	private GltfScene read() throws IOException {
		List<GltfScene.Node> nodes = this.readNodes();
		List<Integer> order = new ArrayList<>(nodes.size());
		boolean[] visited = new boolean[nodes.size()];

		for (int root : this.sceneRoots(nodes)) {
			this.visit(root, visited, order);
		}

		int sceneNodes = order.size();

		for (int i = 0; i < nodes.size(); i++) {
			if (nodes.get(i).parent() < 0) {
				this.visit(i, visited, order);
			}
		}

		if (order.size() != nodes.size()) {
			throw new IOException("Node hierarchy has a cycle");
		}

		List<GltfScene.Skin> skins = new ArrayList<>();

		for (int i = 0; i < GltfJson.array(this.json, "skins").size(); i++) {
			skins.add(this.readSkin(i));
		}

		List<GltfScene.Part> parts = this.readParts(order.subList(0, sceneNodes), skins);
		int[] nodeOrder = order.stream().mapToInt(Integer::intValue).toArray();
		return new GltfScene(nodes, nodeOrder, skins, parts, this.readImages(), GltfAnimationReader.read(this.json, this.accessors));
	}

	private List<GltfScene.Node> readNodes() throws IOException {
		JsonArray array = GltfJson.array(this.json, "nodes");
		int[] parents = new int[array.size()];
		Arrays.fill(parents, -1);

		for (int i = 0; i < array.size(); i++) {
			for (JsonElement element : GltfJson.array(array.get(i).getAsJsonObject(), "children")) {
				int child = element.getAsInt();

				if (child < 0 || child >= array.size()) {
					throw new IOException("Node " + i + " has child " + child + " outside the " + array.size() + " nodes");
				}

				if (parents[child] >= 0) {
					throw new IOException("Node " + child + " has more than one parent");
				}

				parents[child] = i;
			}
		}

		List<GltfScene.Node> nodes = new ArrayList<>(array.size());

		for (int i = 0; i < array.size(); i++) {
			JsonObject node = array.get(i).getAsJsonObject();
			Vector3f translation = new Vector3f();
			Quaternionf rotation = new Quaternionf();
			Vector3f scale = new Vector3f(1.0F);

			if (node.has("matrix")) {
				Matrix4f matrix = new Matrix4f().set(GltfJson.floats(node.getAsJsonArray("matrix"), GltfAccessors.MAT4));
				matrix.getTranslation(translation);
				matrix.getScale(scale);

				if (matrix.determinant3x3() < 0.0F) {
					scale.x = -scale.x;
					matrix.scale(-1.0F, 1.0F, 1.0F);
				}

				matrix.getUnnormalizedRotation(rotation).normalize();
			} else {
				if (node.has("translation")) {
					float[] t = GltfJson.floats(node.getAsJsonArray("translation"), GltfAccessors.VEC3);
					translation.set(t[0], t[1], t[2]);
				}

				if (node.has("rotation")) {
					float[] r = GltfJson.floats(node.getAsJsonArray("rotation"), GltfAccessors.VEC4);
					rotation.set(r[0], r[1], r[2], r[3]).normalize();
				}

				if (node.has("scale")) {
					float[] s = GltfJson.floats(node.getAsJsonArray("scale"), GltfAccessors.VEC3);
					scale.set(s[0], s[1], s[2]);
				}
			}

			String name = node.has("name") ? node.get("name").getAsString() : "node" + i;
			nodes.add(new GltfScene.Node(name, parents[i], translation, rotation, scale));
		}

		return nodes;
	}

	private List<Integer> sceneRoots(List<GltfScene.Node> nodes) throws IOException {
		List<Integer> roots = new ArrayList<>();

		if (GltfJson.array(this.json, "scenes").isEmpty()) {
			for (int i = 0; i < nodes.size(); i++) {
				if (nodes.get(i).parent() < 0) {
					roots.add(i);
				}
			}

			return roots;
		}

		for (JsonElement element : GltfJson.array(GltfJson.object(this.json, "scenes", GltfJson.intOr(this.json, "scene", 0)), "nodes")) {
			int root = element.getAsInt();

			if (root < 0 || root >= nodes.size() || nodes.get(root).parent() >= 0) {
				throw new IOException("Scene root " + root + " is not a root node");
			}

			roots.add(root);
		}

		return roots;
	}

	private void visit(int index, boolean[] visited, List<Integer> order) {
		if (visited[index]) {
			return;
		}

		visited[index] = true;
		order.add(index);

		for (JsonElement child : GltfJson.array(GltfJson.array(this.json, "nodes").get(index).getAsJsonObject(), "children")) {
			this.visit(child.getAsInt(), visited, order);
		}
	}

	private GltfScene.Skin readSkin(int index) throws IOException {
		JsonObject skin = GltfJson.object(this.json, "skins", index);
		JsonArray jointNodes = GltfJson.array(skin, "joints");
		int[] joints = new int[jointNodes.size()];
		int nodeCount = GltfJson.array(this.json, "nodes").size();

		for (int j = 0; j < joints.length; j++) {
			joints[j] = jointNodes.get(j).getAsInt();

			if (joints[j] < 0 || joints[j] >= nodeCount) {
				throw new IOException("Skin " + index + " has joint " + joints[j] + " outside the " + nodeCount + " nodes");
			}
		}

		float[] inverseBind;

		if (skin.has("inverseBindMatrices")) {
			inverseBind = this.accessors.floats(skin.get("inverseBindMatrices").getAsInt());
		} else {
			inverseBind = new float[joints.length * GltfAccessors.MAT4];

			for (int j = 0; j < joints.length; j++) {
				new Matrix4f().get(inverseBind, j * GltfAccessors.MAT4);
			}
		}

		if (inverseBind.length < joints.length * GltfAccessors.MAT4) {
			throw new IOException("Skin " + index + " has fewer inverse bind matrices than joints");
		}

		return new GltfScene.Skin(joints, inverseBind);
	}

	private List<GltfScene.Part> readParts(List<Integer> sceneNodes, List<GltfScene.Skin> skins) throws IOException {
		JsonArray nodes = GltfJson.array(this.json, "nodes");
		GltfPrimitiveReader primitives = new GltfPrimitiveReader(this.json, this.accessors, skins);
		List<GltfScene.Part> parts = new ArrayList<>();

		for (int i : sceneNodes) {
			JsonObject node = nodes.get(i).getAsJsonObject();

			if (!node.has("mesh")) {
				continue;
			}

			int skin = GltfJson.intOr(node, "skin", -1);
			JsonObject mesh = GltfJson.object(this.json, "meshes", node.get("mesh").getAsInt());

			for (JsonElement primitive : GltfJson.array(mesh, "primitives")) {
				GltfScene.Part part = primitives.read(primitive.getAsJsonObject(), i, skin);

				if (part != null) {
					parts.add(part);
				}
			}
		}

		return parts;
	}

	private List<GltfImage> readImages() throws IOException {
		List<GltfImage> images = new ArrayList<>();

		for (JsonElement element : GltfJson.array(this.json, "images")) {
			JsonObject image = element.getAsJsonObject();
			byte[] data;

			if (image.has("bufferView")) {
				data = this.accessors.bytes(image.get("bufferView").getAsInt());
			} else if (image.has("uri")) {
				data = GltfAccessors.dataUri(image.get("uri").getAsString());
			} else {
				data = new byte[0];
			}

			images.add(GltfImage.of(data));
		}

		return images;
	}
}
