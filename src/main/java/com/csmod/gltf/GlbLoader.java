package com.csmod.gltf;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import net.minecraft.util.ARGB;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

public final class GlbLoader {
	private static final int MAGIC = 0x46546C67;
	private static final int CHUNK_JSON = 0x4E4F534A;
	private static final int CHUNK_BIN = 0x004E4942;
	private static final int MODE_TRIANGLES = 4;

	private static final int BYTE = 5120;
	private static final int UNSIGNED_BYTE = 5121;
	private static final int SHORT = 5122;
	private static final int UNSIGNED_SHORT = 5123;
	private static final int UNSIGNED_INT = 5125;
	private static final int FLOAT = 5126;

	private final JsonObject json;
	private final @Nullable ByteBuffer glbBuffer;
	private final ByteBuffer[] buffers;

	private GlbLoader(JsonObject json, @Nullable ByteBuffer glbBuffer) {
		this.json = json;
		this.glbBuffer = glbBuffer;
		this.buffers = new ByteBuffer[array(json, "buffers").size()];
	}

	public static GltfModel load(byte[] data) throws IOException {
		return loadScene(data).restPose();
	}

	public static GltfScene loadScene(byte[] data) throws IOException {
		ByteBuffer file = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

		if (data.length < 12 || file.getInt() != MAGIC) {
			throw new IOException("Not a binary glTF (.glb) file");
		}

		int version = file.getInt();

		if (version != 2) {
			throw new IOException("Unsupported glTF version " + version);
		}

		int length = Math.min(file.getInt(), data.length);
		JsonObject json = null;
		ByteBuffer bin = null;

		while (file.position() + 8 <= length) {
			int chunkLength = file.getInt();
			int chunkType = file.getInt();

			if (chunkLength < 0 || file.position() + chunkLength > length) {
				throw new IOException("Truncated GLB chunk");
			}

			ByteBuffer chunk = file.slice(file.position(), chunkLength).order(ByteOrder.LITTLE_ENDIAN);

			if (chunkType == CHUNK_JSON) {
				json = JsonParser.parseString(StandardCharsets.UTF_8.decode(chunk).toString()).getAsJsonObject();
			} else if (chunkType == CHUNK_BIN && bin == null) {
				bin = chunk;
			}

			file.position(file.position() + chunkLength);
		}

		if (json == null) {
			throw new IOException("GLB file has no JSON chunk");
		}

		return new GlbLoader(json, bin).read();
	}

	private GltfScene read() throws IOException {
		List<GltfScene.Node> nodes = this.readNodes();
		int[] order = this.sceneOrder(nodes);
		List<GltfScene.Skin> skins = new ArrayList<>();

		for (int i = 0; i < array(this.json, "skins").size(); i++) {
			skins.add(this.readSkin(i));
		}

		JsonArray nodeArray = array(this.json, "nodes");
		List<GltfScene.Part> parts = new ArrayList<>();

		for (int i : order) {
			JsonObject node = nodeArray.get(i).getAsJsonObject();

			if (!node.has("mesh")) {
				continue;
			}

			int skin = intOr(node, "skin", -1);
			JsonObject mesh = this.get("meshes", node.get("mesh").getAsInt());

			for (JsonElement primitive : array(mesh, "primitives")) {
				GltfScene.Part part = this.readPrimitive(primitive.getAsJsonObject(), i, skin);

				if (part != null) {
					parts.add(part);
				}
			}
		}

		return new GltfScene(nodes, order, skins, parts, this.readImages(), this.readAnimations());
	}

	private List<GltfScene.Node> readNodes() {
		JsonArray array = array(this.json, "nodes");
		int[] parents = new int[array.size()];
		Arrays.fill(parents, -1);

		for (int i = 0; i < array.size(); i++) {
			for (JsonElement child : array(array.get(i).getAsJsonObject(), "children")) {
				parents[child.getAsInt()] = i;
			}
		}

		List<GltfScene.Node> nodes = new ArrayList<>(array.size());

		for (int i = 0; i < array.size(); i++) {
			JsonObject node = array.get(i).getAsJsonObject();
			Vector3f translation = new Vector3f();
			Quaternionf rotation = new Quaternionf();
			Vector3f scale = new Vector3f(1.0F);

			if (node.has("matrix")) {
				Matrix4f matrix = new Matrix4f().set(floats(node.getAsJsonArray("matrix"), 16));
				matrix.getTranslation(translation);
				matrix.getUnnormalizedRotation(rotation).normalize();
				matrix.getScale(scale);
			} else {
				if (node.has("translation")) {
					float[] t = floats(node.getAsJsonArray("translation"), 3);
					translation.set(t[0], t[1], t[2]);
				}

				if (node.has("rotation")) {
					float[] r = floats(node.getAsJsonArray("rotation"), 4);
					rotation.set(r[0], r[1], r[2], r[3]).normalize();
				}

				if (node.has("scale")) {
					float[] s = floats(node.getAsJsonArray("scale"), 3);
					scale.set(s[0], s[1], s[2]);
				}
			}

			String name = node.has("name") ? node.get("name").getAsString() : "node" + i;
			nodes.add(new GltfScene.Node(name, parents[i], translation, rotation, scale));
		}

		return nodes;
	}

	private int[] sceneOrder(List<GltfScene.Node> nodes) {
		List<Integer> roots = new ArrayList<>();
		JsonArray scenes = array(this.json, "scenes");

		if (!scenes.isEmpty()) {
			for (JsonElement root : array(scenes.get(intOr(this.json, "scene", 0)).getAsJsonObject(), "nodes")) {
				roots.add(root.getAsInt());
			}
		} else {
			for (int i = 0; i < nodes.size(); i++) {
				if (nodes.get(i).parent() < 0) {
					roots.add(i);
				}
			}
		}

		List<Integer> order = new ArrayList<>();
		boolean[] visited = new boolean[nodes.size()];
		JsonArray array = array(this.json, "nodes");

		for (int root : roots) {
			this.visit(root, array, visited, order);
		}

		return order.stream().mapToInt(Integer::intValue).toArray();
	}

	private void visit(int index, JsonArray nodes, boolean[] visited, List<Integer> order) {
		if (visited[index]) {
			return;
		}

		visited[index] = true;
		order.add(index);

		for (JsonElement child : array(nodes.get(index).getAsJsonObject(), "children")) {
			this.visit(child.getAsInt(), nodes, visited, order);
		}
	}

	private GltfScene.Skin readSkin(int index) throws IOException {
		JsonObject skin = this.get("skins", index);
		JsonArray jointNodes = array(skin, "joints");
		int[] joints = new int[jointNodes.size()];
		int nodeCount = array(this.json, "nodes").size();

		for (int j = 0; j < joints.length; j++) {
			joints[j] = jointNodes.get(j).getAsInt();

			if (joints[j] < 0 || joints[j] >= nodeCount) {
				throw new IOException("Skin " + index + " has joint " + joints[j] + " outside the " + nodeCount + " nodes");
			}
		}

		float[] inverseBind;

		if (skin.has("inverseBindMatrices")) {
			inverseBind = this.readFloats(skin.get("inverseBindMatrices").getAsInt());
		} else {
			inverseBind = new float[joints.length * 16];

			for (int j = 0; j < joints.length; j++) {
				new Matrix4f().get(inverseBind, j * 16);
			}
		}

		if (inverseBind.length < joints.length * 16) {
			throw new IOException("Skin " + index + " has fewer inverse bind matrices than joints");
		}

		return new GltfScene.Skin(joints, inverseBind);
	}

	private GltfScene.@Nullable Part readPrimitive(JsonObject primitive, int node, int skin) throws IOException {
		JsonObject attributes = primitive.getAsJsonObject("attributes");

		if (intOr(primitive, "mode", MODE_TRIANGLES) != MODE_TRIANGLES || attributes == null || !attributes.has("POSITION")) {
			return null;
		}

		float[] positions = this.readFloats(attributes.get("POSITION").getAsInt());
		int vertexCount = positions.length / 3;
		float[] normals = attributes.has("NORMAL") ? this.readFloats(attributes.get("NORMAL").getAsInt()) : null;
		float[] uvs = attributes.has("TEXCOORD_0") ? this.readFloats(attributes.get("TEXCOORD_0").getAsInt()) : new float[vertexCount * 2];
		float[] reflectivity = attributes.has("_REFLECTIVITY") ? this.readFloats(attributes.get("_REFLECTIVITY").getAsInt()) : null;

		if (reflectivity != null && reflectivity.length != vertexCount * 4) {
			throw new IOException("_REFLECTIVITY must be a VEC4 per vertex");
		}
		int[] indices = primitive.has("indices") ? this.readInts(primitive.get("indices").getAsInt()) : sequence(vertexCount);
		indices = Arrays.copyOf(indices, indices.length - indices.length % 3);

		for (int index : indices) {
			if (index < 0 || index >= vertexCount) {
				throw new IOException("Index " + index + " out of range for " + vertexCount + " vertices");
			}
		}

		int sets = 0;

		while (skin >= 0 && attributes.has("JOINTS_" + sets) && attributes.has("WEIGHTS_" + sets)) {
			sets++;
		}

		int[] joints = null;
		float[] weights = null;

		if (sets > 0) {
			int influences = sets * 4;
			joints = new int[vertexCount * influences];
			weights = new float[vertexCount * influences];

			for (int set = 0; set < sets; set++) {
				int[] setJoints = this.readInts(attributes.get("JOINTS_" + set).getAsInt());
				float[] setWeights = this.readFloats(attributes.get("WEIGHTS_" + set).getAsInt());

				for (int v = 0; v < vertexCount; v++) {
					System.arraycopy(setJoints, v * 4, joints, v * influences + set * 4, 4);
					System.arraycopy(setWeights, v * 4, weights, v * influences + set * 4, 4);
				}
			}
		}

		int image = -1;
		int color = -1;
		boolean cutout = false;

		if (primitive.has("material")) {
			JsonObject material = this.get("materials", primitive.get("material").getAsInt());
			cutout = material.has("doubleSided") && material.get("doubleSided").getAsBoolean()
					|| material.has("alphaMode") && "MASK".equals(material.get("alphaMode").getAsString());
			JsonObject pbr = material.getAsJsonObject("pbrMetallicRoughness");

			if (pbr != null && pbr.has("baseColorFactor")) {
				float[] factor = floats(pbr.getAsJsonArray("baseColorFactor"), 4);
				color = ARGB.colorFromFloat(factor[3], factor[0], factor[1], factor[2]);
			}

			if (pbr != null && pbr.has("baseColorTexture")) {
				JsonObject texture = this.get("textures", pbr.getAsJsonObject("baseColorTexture").get("index").getAsInt());
				image = intOr(texture, "source", -1);
			}
		}

		return new GltfScene.Part(node, sets > 0 ? skin : -1, image, color, cutout, positions, normals, uvs, reflectivity, joints, weights, sets * 4, indices);
	}

	private static int[] sequence(int count) {
		int[] result = new int[count];

		for (int i = 0; i < count; i++) {
			result[i] = i;
		}

		return result;
	}

	private List<GltfAnimation> readAnimations() throws IOException {
		List<GltfAnimation> animations = new ArrayList<>();
		JsonArray array = array(this.json, "animations");
		int nodeCount = array(this.json, "nodes").size();

		for (int a = 0; a < array.size(); a++) {
			JsonObject animation = array.get(a).getAsJsonObject();
			JsonArray samplers = array(animation, "samplers");
			List<GltfAnimation.Channel> channels = new ArrayList<>();
			float duration = 0.0F;

			for (JsonElement element : array(animation, "channels")) {
				JsonObject channel = element.getAsJsonObject();
				JsonObject target = channel.getAsJsonObject("target");

				if (target == null || !target.has("node")) {
					continue;
				}

				GltfAnimation.Path path = switch (target.get("path").getAsString()) {
					case "translation" -> GltfAnimation.Path.TRANSLATION;
					case "rotation" -> GltfAnimation.Path.ROTATION;
					case "scale" -> GltfAnimation.Path.SCALE;
					default -> null;
				};

				if (path == null) {
					continue;
				}

				JsonObject sampler = samplers.get(channel.get("sampler").getAsInt()).getAsJsonObject();
				String interpolation = sampler.has("interpolation") ? sampler.get("interpolation").getAsString() : "LINEAR";
				float[] times = this.readFloats(sampler.get("input").getAsInt());
				float[] values = this.readFloats(sampler.get("output").getAsInt());

				if (times.length == 0) {
					continue;
				}

				if ("CUBICSPLINE".equals(interpolation)) {
					values = cubicSplineValues(values, times.length);
				}

				int node = target.get("node").getAsInt();
				int components = path == GltfAnimation.Path.ROTATION ? 4 : 3;

				if (node < 0 || node >= nodeCount || values.length < times.length * components) {
					throw new IOException("Animation " + a + " has a channel with node " + node + " or too few values");
				}

				channels.add(new GltfAnimation.Channel(node, path, "STEP".equals(interpolation), times, values));
				duration = Math.max(duration, times[times.length - 1]);
			}

			List<GltfAnimation.Sound> sounds = new ArrayList<>();
			JsonObject extras = animation.getAsJsonObject("extras");

			for (JsonElement sound : extras != null ? array(extras, "sounds") : new JsonArray()) {
				JsonArray entry = sound.getAsJsonArray();
				sounds.add(new GltfAnimation.Sound(entry.get(0).getAsFloat(), entry.get(1).getAsString()));
			}

			sounds.sort(Comparator.comparingDouble(GltfAnimation.Sound::time));
			String name = animation.has("name") ? animation.get("name").getAsString() : "animation" + a;
			animations.add(new GltfAnimation(name, duration, List.copyOf(channels), List.copyOf(sounds)));
		}

		return animations;
	}

	private static float[] cubicSplineValues(float[] values, int keys) {
		int components = values.length / (keys * 3);
		float[] result = new float[keys * components];

		for (int k = 0; k < keys; k++) {
			System.arraycopy(values, (k * 3 + 1) * components, result, k * components, components);
		}

		return result;
	}

	private List<byte[]> readImages() throws IOException {
		List<byte[]> images = new ArrayList<>();

		for (JsonElement element : array(this.json, "images")) {
			JsonObject image = element.getAsJsonObject();

			if (image.has("bufferView")) {
				JsonObject view = this.get("bufferViews", image.get("bufferView").getAsInt());
				byte[] bytes = new byte[view.get("byteLength").getAsInt()];
				this.buffer(view.get("buffer").getAsInt()).get(intOr(view, "byteOffset", 0), bytes);
				images.add(bytes);
			} else if (image.has("uri")) {
				images.add(dataUri(image.get("uri").getAsString()));
			} else {
				images.add(new byte[0]);
			}
		}

		return images;
	}

	private record Accessor(@Nullable ByteBuffer data, int offset, int stride, int count, int components, int componentType, boolean normalized) {
		double component(int element, int component) {
			int position = this.offset + element * this.stride + component * componentSize(this.componentType);
			double value = switch (this.componentType) {
				case FLOAT -> this.data.getFloat(position);
				case UNSIGNED_BYTE -> this.data.get(position) & 0xFF;
				case BYTE -> this.data.get(position);
				case UNSIGNED_SHORT -> this.data.getShort(position) & 0xFFFF;
				case SHORT -> this.data.getShort(position);
				case UNSIGNED_INT -> this.data.getInt(position) & 0xFFFFFFFFL;
				default -> throw new IllegalStateException("Unknown component type " + this.componentType);
			};

			if (!this.normalized) {
				return value;
			}

			return switch (this.componentType) {
				case UNSIGNED_BYTE -> value / 255.0;
				case BYTE -> Math.max(value / 127.0, -1.0);
				case UNSIGNED_SHORT -> value / 65535.0;
				case SHORT -> Math.max(value / 32767.0, -1.0);
				default -> value;
			};
		}
	}

	private Accessor accessor(int index) throws IOException {
		JsonObject accessor = this.get("accessors", index);

		if (accessor.has("sparse")) {
			throw new IOException("Sparse accessors are not supported (accessor " + index + ")");
		}

		int components = switch (accessor.get("type").getAsString()) {
			case "SCALAR" -> 1;
			case "VEC2" -> 2;
			case "VEC3" -> 3;
			case "VEC4" -> 4;
			case "MAT4" -> 16;
			default -> throw new IOException("Unsupported accessor type " + accessor.get("type").getAsString());
		};
		int componentType = accessor.get("componentType").getAsInt();
		int count = accessor.get("count").getAsInt();
		boolean normalized = accessor.has("normalized") && accessor.get("normalized").getAsBoolean();

		if (!accessor.has("bufferView")) {
			return new Accessor(null, 0, 0, count, components, componentType, normalized);
		}

		JsonObject view = this.get("bufferViews", accessor.get("bufferView").getAsInt());
		int offset = intOr(view, "byteOffset", 0) + intOr(accessor, "byteOffset", 0);
		int stride = intOr(view, "byteStride", componentSize(componentType) * components);
		return new Accessor(this.buffer(view.get("buffer").getAsInt()), offset, stride, count, components, componentType, normalized);
	}

	private float[] readFloats(int accessorIndex) throws IOException {
		Accessor accessor = this.accessor(accessorIndex);
		float[] result = new float[accessor.count * accessor.components];

		if (accessor.data != null) {
			for (int i = 0; i < accessor.count; i++) {
				for (int c = 0; c < accessor.components; c++) {
					result[i * accessor.components + c] = (float) accessor.component(i, c);
				}
			}
		}

		return result;
	}

	private int[] readInts(int accessorIndex) throws IOException {
		Accessor accessor = this.accessor(accessorIndex);
		int[] result = new int[accessor.count * accessor.components];

		if (accessor.data != null) {
			for (int i = 0; i < accessor.count; i++) {
				for (int c = 0; c < accessor.components; c++) {
					result[i * accessor.components + c] = (int) accessor.component(i, c);
				}
			}
		}

		return result;
	}

	private static int componentSize(int componentType) {
		return switch (componentType) {
			case BYTE, UNSIGNED_BYTE -> 1;
			case SHORT, UNSIGNED_SHORT -> 2;
			default -> 4;
		};
	}

	private ByteBuffer buffer(int index) throws IOException {
		if (this.buffers[index] == null) {
			JsonObject buffer = this.get("buffers", index);

			if (buffer.has("uri")) {
				this.buffers[index] = ByteBuffer.wrap(dataUri(buffer.get("uri").getAsString()));
			} else if (this.glbBuffer != null) {
				this.buffers[index] = this.glbBuffer;
			} else {
				throw new IOException("Buffer " + index + " has no data");
			}
		}

		return this.buffers[index].duplicate().order(ByteOrder.LITTLE_ENDIAN);
	}

	private static byte[] dataUri(String uri) throws IOException {
		int base64 = uri.indexOf(";base64,");

		if (!uri.startsWith("data:") || base64 < 0) {
			throw new IOException("External file '" + uri + "' is not supported; export as .glb with embedded textures");
		}

		return Base64.getDecoder().decode(uri.substring(base64 + ";base64,".length()));
	}

	private JsonObject get(String arrayName, int index) throws IOException {
		JsonArray array = array(this.json, arrayName);

		if (index < 0 || index >= array.size()) {
			throw new IOException("Missing " + arrayName + "[" + index + "]");
		}

		return array.get(index).getAsJsonObject();
	}

	private static JsonArray array(JsonObject object, String name) {
		return object.has(name) ? object.getAsJsonArray(name) : new JsonArray();
	}

	private static int intOr(JsonObject object, String name, int fallback) {
		return object.has(name) ? object.get(name).getAsInt() : fallback;
	}

	private static float[] floats(JsonArray array, int size) {
		float[] result = new float[size];

		for (int i = 0; i < Math.min(size, array.size()); i++) {
			result[i] = array.get(i).getAsFloat();
		}

		return result;
	}
}
