package com.bayonet.weapon;

import com.bayonet.util.ClientTicks;
import com.bayonet.util.JsonFiles;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ChargedProjectiles;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class StatTrak {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("bayonet-stattrak.json");
	private static final String AWP_COUNTS_KEY = "awp";
	private static final int SHOT_TICKS = 40;
	private static final int KILL_TICKS = 100;
	private static final Map<Skin, Integer> KILLS = load();
	private static final Map<Integer, Skin> PROJECTILES = new HashMap<>();
	private static final Map<Integer, Hit> HITS = new HashMap<>();
	private static @Nullable Shot shot;
	private static @Nullable ClientLevel level;

	private StatTrak() {
	}

	public static OptionalInt displayedKills(Skin.@Nullable Look look) {
		return look != null && look.statTrak() ? OptionalInt.of(kills(look.skin())) : OptionalInt.empty();
	}

	static int kills(Skin skin) {
		return KILLS.getOrDefault(skin, 0);
	}

	public static void onShoot(ItemStack weapon) {
		Skin.Look look = Skin.of(weapon);
		int projectiles = weapon.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY).items().size();
		shot = look != null && look.statTrak() && projectiles > 0 ? new Shot(look.skin(), ClientTicks.ticks(), projectiles) : null;
	}

	static void onEntityLoad(Entity entity, ClientLevel entityLevel) {
		Shot recent = recentShot();

		if (recent != null && recent.canTag() && isOwnCrossbowProjectile(entity)) {
			PROJECTILES.put(entity.getId(), recent.skin());
			shot = recent.afterLoad();
		}
	}

	public static void onDamage(LivingEntity victim, DamageSource source) {
		Skin skin = isLocalPlayer(source.getEntity()) && !isLocalPlayer(victim) ? projectileSkin(source) : null;

		if (skin != null) {
			HITS.put(victim.getId(), new Hit(skin, ClientTicks.ticks()));
		} else {
			HITS.remove(victim.getId());
		}
	}

	public static void onDeath(LivingEntity victim) {
		Hit hit = HITS.remove(victim.getId());

		if (hit != null && !hit.expired(ClientTicks.ticks())) {
			KILLS.merge(hit.skin(), 1, Integer::sum);
			save();
		}
	}

	static void tick(Minecraft minecraft) {
		ClientLevel current = minecraft.level;

		if (current != level) {
			level = current;
			shot = null;
			PROJECTILES.clear();
			HITS.clear();
		}

		long now = ClientTicks.ticks();
		HITS.values().removeIf(hit -> hit.expired(now));

		if (current != null) {
			PROJECTILES.keySet().removeIf(id -> current.getEntity(id) == null);
		}
	}

	private static @Nullable Shot recentShot() {
		return shot != null && !shot.expired(ClientTicks.ticks()) ? shot : null;
	}

	private static @Nullable Skin projectileSkin(DamageSource source) {
		Entity direct = source.getDirectEntity();

		if (direct != null) {
			return PROJECTILES.get(direct.getId());
		}

		Shot recent = recentShot();
		return recent != null && source.is(DamageTypeTags.IS_PROJECTILE) ? recent.skin() : null;
	}

	private static boolean isOwnCrossbowProjectile(Entity entity) {
		return entity instanceof Projectile projectile
				&& (projectile instanceof FireworkRocketEntity || projectile instanceof AbstractArrow && !(projectile instanceof ThrownTrident))
				&& isLocalPlayer(projectile.getOwner());
	}

	private static boolean isLocalPlayer(@Nullable Entity entity) {
		return entity != null && entity == Minecraft.getInstance().player;
	}

	private static Map<Skin, Integer> load() {
		Map<Skin, Integer> kills = new EnumMap<>(Skin.class);
		JsonObject json = JsonFiles.read(FILE);
		JsonElement counts = json != null ? json.get(AWP_COUNTS_KEY) : null;

		if (counts == null) {
			return kills;
		}

		if (!counts.isJsonObject()) {
			LOGGER.warn("Ignoring the StatTrak counts in {}: \"{}\" is not an object", FILE, AWP_COUNTS_KEY);
			return kills;
		}

		JsonObject entries = counts.getAsJsonObject();

		for (Skin skin : Skin.values()) {
			String key = skin.statTrakKey();

			if (key != null && entries.has(key)) {
				kills.put(skin, Math.max(0, JsonFiles.integer(FILE, entries, key, 0)));
			}
		}

		return kills;
	}

	private static void save() {
		JsonObject counts = new JsonObject();

		for (Skin skin : Skin.values()) {
			String key = skin.statTrakKey();
			Integer kills = KILLS.get(skin);

			if (key != null && kills != null) {
				counts.addProperty(key, kills);
			}
		}

		JsonObject json = new JsonObject();
		json.add(AWP_COUNTS_KEY, counts);
		JsonFiles.write(FILE, json);
	}

	private record Shot(Skin skin, long tick, int remaining) {
		boolean expired(long now) {
			return now - this.tick > SHOT_TICKS;
		}

		boolean canTag() {
			return this.remaining > 0;
		}

		Shot afterLoad() {
			return new Shot(this.skin, this.tick, this.remaining - 1);
		}
	}

	private record Hit(Skin skin, long tick) {
		boolean expired(long now) {
			return now - this.tick > KILL_TICKS;
		}
	}
}
