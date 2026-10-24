package com.bayonet.config;

import com.bayonet.util.JsonFiles;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

public final class Settings {
	static final boolean DEFAULT_GLINT = false;
	static final boolean DEFAULT_ARMS = true;
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("bayonet.json");
	private static final String GLINT_KEY = "glint";
	private static final String ARMS_KEY = "arms";
	private static boolean glint = DEFAULT_GLINT;
	private static boolean arms = DEFAULT_ARMS;

	static {
		load();
	}

	private Settings() {
	}

	public static boolean glint() {
		return glint;
	}

	public static boolean arms() {
		return arms;
	}

	public static boolean toggleGlint() {
		glint = !glint;
		save();
		return glint;
	}

	public static boolean toggleArms() {
		arms = !arms;
		save();
		return arms;
	}

	static void setGlint(boolean value) {
		glint = value;
	}

	static void setArms(boolean value) {
		arms = value;
	}

	static void save() {
		JsonObject json = new JsonObject();
		json.addProperty(GLINT_KEY, glint);
		json.addProperty(ARMS_KEY, arms);
		JsonFiles.write(FILE, json);
	}

	private static void load() {
		JsonObject json = JsonFiles.read(FILE);

		if (json != null) {
			glint = JsonFiles.bool(FILE, json, GLINT_KEY, DEFAULT_GLINT);
			arms = JsonFiles.bool(FILE, json, ARMS_KEY, DEFAULT_ARMS);
		}
	}
}
