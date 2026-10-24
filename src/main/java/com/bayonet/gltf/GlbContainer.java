package com.bayonet.gltf;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;

record GlbContainer(JsonObject json, @Nullable ByteBuffer bin) {
	private static final int MAGIC = 0x46546C67;
	private static final int VERSION = 2;
	private static final int HEADER_SIZE = 12;
	private static final int CHUNK_HEADER_SIZE = 8;
	private static final int CHUNK_JSON = 0x4E4F534A;
	private static final int CHUNK_BIN = 0x004E4942;

	static GlbContainer parse(byte[] data) throws IOException {
		ByteBuffer file = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

		if (data.length < HEADER_SIZE || file.getInt() != MAGIC) {
			throw new IOException("Not a binary glTF (.glb) file");
		}

		int version = file.getInt();

		if (version != VERSION) {
			throw new IOException("Unsupported glTF version " + version);
		}

		int length = Math.min(file.getInt(), data.length);
		JsonObject json = null;
		ByteBuffer bin = null;

		while (file.position() + CHUNK_HEADER_SIZE <= length) {
			int chunkLength = file.getInt();
			int chunkType = file.getInt();

			if (chunkLength < 0 || file.position() + chunkLength > length) {
				throw new IOException("Truncated GLB chunk");
			}

			ByteBuffer chunk = file.slice(file.position(), chunkLength).order(ByteOrder.LITTLE_ENDIAN);

			if (chunkType == CHUNK_JSON) {
				json = parseJson(chunk);
			} else if (chunkType == CHUNK_BIN && bin == null) {
				bin = chunk;
			}

			file.position(file.position() + chunkLength);
		}

		if (json == null) {
			throw new IOException("GLB file has no JSON chunk");
		}

		return new GlbContainer(json, bin);
	}

	private static JsonObject parseJson(ByteBuffer chunk) throws IOException {
		try {
			return JsonParser.parseString(StandardCharsets.UTF_8.decode(chunk).toString()).getAsJsonObject();
		} catch (JsonParseException | IllegalStateException e) {
			throw new IOException("Invalid glTF JSON chunk", e);
		}
	}
}
