package com.bayonet.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class JsonFiles {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final OpenOption[] DURABLE_WRITE = {
		StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE, StandardOpenOption.SYNC
	};
	private static final String BACKUP_SUFFIX = ".bak";
	private static final String TEMP_SUFFIX = ".tmp";
	private static final String MISMATCH = "Ignoring \"{}\" in {}: {} is not {}, using {}";

	private JsonFiles() {
	}

	public static @Nullable JsonObject read(Path file) {
		if (!Files.exists(file)) {
			return null;
		}

		try {
			JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));

			if (json.isJsonObject()) {
				return json.getAsJsonObject();
			}

			LOGGER.error("{} does not hold a JSON object", file);
		} catch (IOException | JsonParseException e) {
			LOGGER.error("Failed to read {}", file, e);
		}

		backUp(file);
		return null;
	}

	public static synchronized void write(Path file, JsonElement json) {
		Path temp = sibling(file, TEMP_SUFFIX);

		try {
			Files.createDirectories(file.toAbsolutePath().getParent());
			Files.writeString(temp, GSON.toJson(json) + "\n", StandardCharsets.UTF_8, DURABLE_WRITE);
			replace(temp, file);
		} catch (IOException e) {
			LOGGER.error("Failed to write {}", file, e);
			discard(temp);
		}
	}

	public static boolean bool(Path file, JsonObject json, String key, boolean fallback) {
		JsonElement value = json.get(key);

		if (value == null) {
			return fallback;
		}

		if (value instanceof JsonPrimitive primitive && primitive.isBoolean()) {
			return primitive.getAsBoolean();
		}

		LOGGER.warn(MISMATCH, key, file, value, "a boolean", fallback);
		return fallback;
	}

	public static int integer(Path file, JsonObject json, String key, int fallback) {
		JsonElement value = json.get(key);

		if (value == null) {
			return fallback;
		}

		Integer exact = value instanceof JsonPrimitive primitive && primitive.isNumber() ? exactInt(primitive) : null;

		if (exact != null) {
			return exact;
		}

		LOGGER.warn(MISMATCH, key, file, value, "an integer", fallback);
		return fallback;
	}

	private static @Nullable Integer exactInt(JsonPrimitive number) {
		try {
			return number.getAsBigDecimal().intValueExact();
		} catch (ArithmeticException | NumberFormatException e) {
			return null;
		}
	}

	private static void replace(Path source, Path target) throws IOException {
		try {
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			LOGGER.warn("Atomic replace of {} is not supported, replacing it non-atomically", target);
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static void backUp(Path file) {
		Path backup = sibling(file, BACKUP_SUFFIX);

		try {
			Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
			LOGGER.warn("Backed up the unreadable {} to {}", file, backup);
		} catch (IOException e) {
			LOGGER.error("Failed to back up {}", file, e);
		}
	}

	private static void discard(Path temp) {
		try {
			Files.deleteIfExists(temp);
		} catch (IOException e) {
			LOGGER.warn("Failed to delete {}", temp, e);
		}
	}

	private static Path sibling(Path file, String suffix) {
		return file.resolveSibling(file.getFileName() + suffix);
	}
}
