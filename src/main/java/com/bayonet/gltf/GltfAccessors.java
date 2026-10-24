package com.bayonet.gltf;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import org.jspecify.annotations.Nullable;

final class GltfAccessors {
	static final int SCALAR = 1;
	static final int VEC2 = 2;
	static final int VEC3 = 3;
	static final int VEC4 = 4;
	static final int MAT4 = 16;
	static final int MAX_VALUES = 1 << 24;
	private static final double UNSIGNED_BYTE_MAX = 0xFF;
	private static final double UNSIGNED_SHORT_MAX = 0xFFFF;
	private static final String DATA_URI_PREFIX = "data:";
	private static final String DATA_URI_BASE64 = ";base64,";

	private final JsonObject json;
	private final @Nullable ByteBuffer glbBuffer;
	private final @Nullable ByteBuffer[] buffers;

	GltfAccessors(JsonObject json, @Nullable ByteBuffer glbBuffer) {
		this.json = json;
		this.glbBuffer = glbBuffer;
		this.buffers = new ByteBuffer[GltfJson.array(json, "buffers").size()];
	}

	float[] floats(int accessor) throws IOException {
		return this.accessor(accessor).floats();
	}

	int[] ints(int accessor) throws IOException {
		return this.accessor(accessor).ints();
	}

	byte[] bytes(int bufferView) throws IOException {
		ByteBuffer view = this.view(bufferView, GltfJson.object(this.json, "bufferViews", bufferView));
		byte[] bytes = new byte[view.limit()];
		view.get(0, bytes);
		return bytes;
	}

	static byte[] dataUri(String uri) throws IOException {
		int base64 = uri.indexOf(DATA_URI_BASE64);

		if (!uri.startsWith(DATA_URI_PREFIX) || base64 < 0) {
			throw new IOException("External file '" + uri + "' is not supported; export as .glb with embedded textures");
		}

		try {
			return Base64.getDecoder().decode(uri.substring(base64 + DATA_URI_BASE64.length()));
		} catch (IllegalArgumentException e) {
			throw new IOException("Invalid base64 data URI", e);
		}
	}

	private Accessor accessor(int index) throws IOException {
		JsonObject accessor = GltfJson.object(this.json, "accessors", index);
		String owner = "Accessor " + index;

		if (accessor.has("sparse")) {
			throw new IOException("Sparse accessors are not supported (accessor " + index + ")");
		}

		String type = GltfJson.requiredString(accessor, "type", owner);
		int components = switch (type) {
			case "SCALAR" -> SCALAR;
			case "VEC2" -> VEC2;
			case "VEC3" -> VEC3;
			case "VEC4" -> VEC4;
			case "MAT4" -> MAT4;
			default -> throw new IOException("Unsupported accessor type " + type);
		};
		ComponentType componentType = ComponentType.of(GltfJson.requiredInt(accessor, "componentType", owner));
		int count = GltfJson.requiredInt(accessor, "count", owner);
		boolean normalized = accessor.has("normalized") && accessor.get("normalized").getAsBoolean();

		if (count < 0) {
			throw new IOException(owner + " has a negative count");
		}

		if ((long) count * components > MAX_VALUES) {
			throw new IOException(owner + " has " + count + " elements, more than the " + MAX_VALUES / components + " supported");
		}

		if (!accessor.has("bufferView")) {
			return new Accessor(null, 0, 0, count, components, componentType, normalized);
		}

		int viewIndex = accessor.get("bufferView").getAsInt();
		JsonObject viewJson = GltfJson.object(this.json, "bufferViews", viewIndex);
		ByteBuffer view = this.view(viewIndex, viewJson);
		int elementSize = componentType.size * components;
		int offset = GltfJson.intOr(accessor, "byteOffset", 0);
		int stride = GltfJson.intOr(viewJson, "byteStride", elementSize);

		if (stride < elementSize) {
			throw new IOException(owner + " has a byte stride of " + stride + ", less than its " + elementSize + "-byte elements");
		}

		if (count > 0 && (offset < 0 || (long) offset + (long) (count - 1) * stride + elementSize > view.limit())) {
			throw new IOException(owner + " reads past the end of buffer view " + viewIndex);
		}

		return new Accessor(view, offset, stride, count, components, componentType, normalized);
	}

	private ByteBuffer view(int index, JsonObject view) throws IOException {
		String owner = "Buffer view " + index;
		int bufferIndex = GltfJson.requiredInt(view, "buffer", owner);
		int offset = GltfJson.intOr(view, "byteOffset", 0);
		int length = GltfJson.requiredInt(view, "byteLength", owner);
		ByteBuffer buffer = this.buffer(bufferIndex);

		if (offset < 0 || length < 0 || (long) offset + length > buffer.limit()) {
			throw new IOException(owner + " lies outside buffer " + bufferIndex);
		}

		return buffer.slice(offset, length).order(ByteOrder.LITTLE_ENDIAN);
	}

	private ByteBuffer buffer(int index) throws IOException {
		JsonObject buffer = GltfJson.object(this.json, "buffers", index);
		ByteBuffer data = this.buffers[index];

		if (data == null) {
			if (buffer.has("uri")) {
				data = ByteBuffer.wrap(dataUri(buffer.get("uri").getAsString()));
			} else if (this.glbBuffer != null) {
				data = this.glbBuffer;
			} else {
				throw new IOException("Buffer " + index + " has no data");
			}

			this.buffers[index] = data;
		}

		return data;
	}

	private enum ComponentType {
		BYTE(5120, Byte.BYTES),
		UNSIGNED_BYTE(5121, Byte.BYTES),
		SHORT(5122, Short.BYTES),
		UNSIGNED_SHORT(5123, Short.BYTES),
		UNSIGNED_INT(5125, Integer.BYTES),
		FLOAT(5126, Float.BYTES);

		private final int code;
		private final int size;

		ComponentType(int code, int size) {
			this.code = code;
			this.size = size;
		}

		static ComponentType of(int code) throws IOException {
			for (ComponentType type : values()) {
				if (type.code == code) {
					return type;
				}
			}

			throw new IOException("Unsupported accessor component type " + code);
		}
	}

	private record Accessor(
		@Nullable ByteBuffer data, int offset, int stride, int count, int components, ComponentType componentType, boolean normalized
	) {
		float[] floats() {
			float[] result = new float[this.count * this.components];

			if (this.data != null) {
				for (int i = 0; i < this.count; i++) {
					for (int c = 0; c < this.components; c++) {
						result[i * this.components + c] = (float) this.component(this.data, i, c);
					}
				}
			}

			return result;
		}

		int[] ints() {
			int[] result = new int[this.count * this.components];

			if (this.data != null) {
				for (int i = 0; i < this.count; i++) {
					for (int c = 0; c < this.components; c++) {
						result[i * this.components + c] = (int) this.component(this.data, i, c);
					}
				}
			}

			return result;
		}

		private double component(ByteBuffer data, int element, int component) {
			int position = this.offset + element * this.stride + component * this.componentType.size;
			double value = switch (this.componentType) {
				case FLOAT -> (double) data.getFloat(position);
				case UNSIGNED_BYTE -> Byte.toUnsignedInt(data.get(position));
				case BYTE -> data.get(position);
				case UNSIGNED_SHORT -> Short.toUnsignedInt(data.getShort(position));
				case SHORT -> data.getShort(position);
				case UNSIGNED_INT -> Integer.toUnsignedLong(data.getInt(position));
			};

			if (!this.normalized) {
				return value;
			}

			return switch (this.componentType) {
				case UNSIGNED_BYTE -> value / UNSIGNED_BYTE_MAX;
				case BYTE -> Math.max(value / Byte.MAX_VALUE, -1.0);
				case UNSIGNED_SHORT -> value / UNSIGNED_SHORT_MAX;
				case SHORT -> Math.max(value / Short.MAX_VALUE, -1.0);
				case FLOAT, UNSIGNED_INT -> value;
			};
		}
	}
}
