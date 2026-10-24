package com.bayonet.sound;

import com.bayonet.util.ModIds;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import org.slf4j.Logger;

final class SoundBehaviours {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Identifier FILE = ModIds.of("sound_events.json");
	private static Map<String, SoundBehaviour> loaded = Map.of();

	private SoundBehaviours() {
	}

	static SoundBehaviour get(String id) {
		return loaded.getOrDefault(id, SoundBehaviour.DEFAULT);
	}

	private static Map<String, SoundBehaviour> load(ResourceManager resources) {
		Optional<Resource> resource = resources.getResource(FILE);

		if (resource.isEmpty()) {
			LOGGER.warn("Missing {}, so weapon sounds play at full volume", FILE);
			return Map.of();
		}

		JsonObject root;

		try (Reader reader = resource.get().openAsReader()) {
			root = GsonHelper.parse(reader);
		} catch (IOException | JsonParseException e) {
			LOGGER.error("Failed to read {}, so weapon sounds play at full volume", FILE, e);
			return Map.of();
		}

		Map<String, SoundBehaviour> behaviours = new HashMap<>();

		for (Map.Entry<String, JsonElement> event : root.entrySet()) {
			try {
				behaviours.put(event.getKey(), SoundBehaviour.parse(GsonHelper.convertToJsonObject(event.getValue(), event.getKey())));
			} catch (JsonParseException e) {
				LOGGER.warn("Ignoring sound event {} in {}: {}", event.getKey(), FILE, e.getMessage());
			}
		}

		return Map.copyOf(behaviours);
	}

	static final class ReloadListener extends SimpleReloadListener<Map<String, SoundBehaviour>> {
		@Override
		protected Map<String, SoundBehaviour> prepare(PreparableReloadListener.SharedState state) {
			return load(state.resourceManager());
		}

		@Override
		protected void apply(Map<String, SoundBehaviour> prepared, PreparableReloadListener.SharedState state) {
			loaded = prepared;
		}
	}
}
