package com.bayonet;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

public final class Settings {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("bayonet.json");
	private static boolean glint = load();

	private Settings() {
	}

	public static boolean glint() {
		return glint;
	}

	public static boolean toggleGlint() {
		glint = !glint;
		save();
		return glint;
	}

	private static boolean load() {
		try {
			if (Files.exists(FILE)) {
				JsonElement value = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject().get("glint");
				return value != null && value.getAsBoolean();
			}
		} catch (Exception e) {
			LOGGER.error("Failed to read {}", FILE, e);
		}

		return false;
	}

	private static void save() {
		JsonObject json = new JsonObject();
		json.addProperty("glint", glint);

		try {
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE, json + "\n", StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOGGER.error("Failed to write {}", FILE, e);
		}
	}
}
