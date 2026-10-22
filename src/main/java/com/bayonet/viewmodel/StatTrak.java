package com.bayonet.viewmodel;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class StatTrak {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("bayonet-stattrak.json");
	private static final Path TEMP = FILE.resolveSibling("bayonet-stattrak.json.tmp");
	private static final Path BACKUP = FILE.resolveSibling("bayonet-stattrak.json.bak");
	private static final int SHOT_TICKS = 40;
	private static final int KILL_TICKS = 100;
	private static final Map<Integer, Skin> PROJECTILES = new HashMap<>();
	private static final Map<Integer, Hit> HITS = new HashMap<>();
	private static long ticks;
	private static long lastShot = Long.MIN_VALUE / 2;
	private static @Nullable Skin shotSkin;
	private static @Nullable ClientLevel level;
	private static @Nullable Map<Skin, Integer> kills;

	private record Hit(Skin skin, long tick) {
	}

	private StatTrak() {
	}

	public static int kills(Skin skin) {
		return counts().getOrDefault(skin, 0);
	}

	public static int display(Skin.@Nullable Look look) {
		return look != null && look.statTrak() ? kills(look.skin()) : -1;
	}

	static void onShoot(Skin.@Nullable Look look) {
		lastShot = ticks;
		shotSkin = look != null && look.statTrak() ? look.skin() : null;
	}

	public static void onEntityLoad(Entity entity, ClientLevel entityLevel) {
		if (shotSkin != null && entity instanceof Projectile projectile && ticks - lastShot <= SHOT_TICKS && isLocalPlayer(projectile.getOwner())) {
			PROJECTILES.put(entity.getId(), shotSkin);
		}
	}

	public static void onDamage(LivingEntity victim, DamageSource source) {
		if (!isLocalPlayer(source.getEntity()) || isLocalPlayer(victim)) {
			return;
		}

		Entity direct = source.getDirectEntity();
		Skin skin = direct != null
				? PROJECTILES.get(direct.getId())
				: source.is(DamageTypeTags.IS_PROJECTILE) && ticks - lastShot <= SHOT_TICKS ? shotSkin : null;

		if (skin != null) {
			HITS.put(victim.getId(), new Hit(skin, ticks));
		}
	}

	public static void onDeath(LivingEntity victim) {
		Hit hit = HITS.remove(victim.getId());

		if (hit != null && ticks - hit.tick() <= KILL_TICKS) {
			counts().merge(hit.skin(), 1, Integer::sum);
			save(counts());
		}
	}

	public static void tick(Minecraft minecraft) {
		ticks++;

		if (minecraft.level != level) {
			level = minecraft.level;
			PROJECTILES.clear();
			HITS.clear();
		}

		HITS.values().removeIf(hit -> ticks - hit.tick() > KILL_TICKS);

		if (level != null) {
			PROJECTILES.keySet().removeIf(id -> level.getEntity(id) == null && ticks - lastShot > SHOT_TICKS);
		}
	}

	private static boolean isLocalPlayer(@Nullable Entity entity) {
		LocalPlayer player = Minecraft.getInstance().player;
		return entity != null && entity == player;
	}

	private static Map<Skin, Integer> counts() {
		if (kills == null) {
			kills = load();
		}

		return kills;
	}

	private static Map<Skin, Integer> load() {
		Map<Skin, Integer> result = new EnumMap<>(Skin.class);

		try {
			if (Files.exists(FILE)) {
				JsonElement awp = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject().get("awp");

				if (awp != null && awp.isJsonObject()) {
					JsonObject entries = awp.getAsJsonObject();

					for (Skin skin : Skin.values()) {
						JsonElement count = skin.statTrakKey != null ? entries.get(skin.statTrakKey) : null;

						if (count != null) {
							result.put(skin, Math.max(0, count.getAsInt()));
						}
					}
				}
			}
		} catch (Exception e) {
			LOGGER.error("Failed to read {}", FILE, e);

			try {
				Files.copy(FILE, BACKUP, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException backupError) {
				LOGGER.error("Failed to back up {}", FILE, backupError);
			}
		}

		return result;
	}

	private static void save(Map<Skin, Integer> counts) {
		JsonObject awp = new JsonObject();
		counts.forEach((skin, count) -> awp.addProperty(skin.statTrakKey, count));
		JsonObject json = new JsonObject();
		json.add("awp", awp);

		try {
			Files.createDirectories(FILE.getParent());
			Files.writeString(TEMP, json + "\n", StandardCharsets.UTF_8);
			Files.move(TEMP, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			LOGGER.error("Failed to write {}", FILE, e);
		}
	}
}
