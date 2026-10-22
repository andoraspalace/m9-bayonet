package com.bayonet.viewmodel;

import com.bayonet.BayonetClient;
import com.bayonet.gltf.GlbLoader;
import com.bayonet.gltf.GltfAnimation;
import com.bayonet.gltf.GltfScene;
import com.bayonet.render.GltfTextures;
import com.bayonet.render.Wear;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class Viewmodels {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static Map<String, Viewmodel> loaded = Map.of();

	private Viewmodels() {
	}

	public enum Weapon {
		AWP("awp_asimov", "awp_anims", "draw", "idle", "lookat01", false, new Vector3f(), Float.NaN),
		M9_BAYONET("m9_default", "m9_anims", "draw", "idle1", "lookat01", true, new Vector3f(-0.0018F, 0.0371F, -0.0002F), 0.025F);

		final String model;
		final String clips;
		final String draw;
		final String idle;
		final String inspect;
		final boolean oneHanded;
		final Vector3fc gripShift;
		final float handleEnd;

		Weapon(String model, String clips, String draw, String idle, String inspect, boolean oneHanded, Vector3fc gripShift, float handleEnd) {
			this.model = model;
			this.clips = clips;
			this.draw = draw;
			this.idle = idle;
			this.inspect = inspect;
			this.oneHanded = oneHanded;
			this.gripShift = gripShift;
			this.handleEnd = handleEnd;
		}

		public static @Nullable Weapon of(ItemStack stack) {
			Skin.Look look = plainLook(stack);
			return look != null ? look.skin().weapon : null;
		}

		public static @Nullable Identifier definition(ItemStack stack) {
			Skin.Look look = plainLook(stack);
			return look != null ? look.skin().definition() : null;
		}

		private static Skin.@Nullable Look plainLook(ItemStack stack) {
			Skin.Look look = Skin.of(stack);
			return look != null && isPlain(stack) ? look : null;
		}

		private static boolean isPlain(ItemStack stack) {
			return !stack.has(DataComponents.CUSTOM_MODEL_DATA)
					&& Objects.equals(stack.get(DataComponents.ITEM_MODEL), stack.getPrototype().get(DataComponents.ITEM_MODEL));
		}
	}

	static @Nullable Viewmodel get(Weapon weapon) {
		return loaded.get(weapon.model);
	}

	static @Nullable Viewmodel forStack(ItemStack stack) {
		Skin.Look look = Weapon.plainLook(stack);

		if (look == null) {
			return null;
		}

		Viewmodel viewmodel = loaded.get(look.skin().model);
		return viewmodel != null ? viewmodel : loaded.get(look.skin().weapon.model);
	}

	static ThirdPerson.@Nullable Turn turn(Weapon weapon, String clip, float progress) {
		Viewmodel viewmodel = get(weapon);
		GltfAnimation animation = viewmodel != null ? viewmodel.clip(clip) : null;
		return animation != null ? new ThirdPerson.Turn(weapon, viewmodel, animation, progress * animation.duration()) : null;
	}

	private static GltfScene load(ResourceManager resources, String name) throws IOException {
		try (InputStream stream = resources.open(BayonetClient.id("models/gltf/" + name + ".glb"))) {
			return GlbLoader.loadScene(stream.readAllBytes());
		}
	}

	private static @Nullable GltfScene loadOptional(ResourceManager resources, String name) {
		try {
			return load(resources, name);
		} catch (Exception e) {
			LOGGER.warn("No {} model, so no StatTrak module: {}", name, e.toString());
			return null;
		}
	}

	public static class ReloadListener extends SimpleReloadListener<Map<String, Viewmodel>> {
		private Map<String, Wear.Mask> wearMaps = Map.of();

		@Override
		protected Map<String, Viewmodel> prepare(PreparableReloadListener.SharedState state) {
			this.wearMaps = Wear.load(state.resourceManager());
			Map<String, Viewmodel> result = new HashMap<>();
			GltfScene statTrak = loadOptional(state.resourceManager(), "stattrak_module");

			for (Weapon weapon : Weapon.values()) {
				GltfScene clips;

				try {
					clips = load(state.resourceManager(), weapon.clips);
				} catch (Exception e) {
					LOGGER.error("Failed to load the clips of {}", weapon, e);
					continue;
				}

				for (Skin skin : Skin.values()) {
					if (skin.weapon != weapon) {
						continue;
					}

					try {
						GltfScene model = load(state.resourceManager(), skin.model);
						StatTrakModule module = skin.statTrakKey != null && statTrak != null ? new StatTrakModule(statTrak) : null;
						result.put(skin.model, new Viewmodel(BayonetClient.id(skin.model), model, clips, weapon.idle, weapon.oneHanded, weapon.gripShift, weapon.handleEnd, module));
						LOGGER.info("Loaded viewmodel {}: {} clips", skin.model, clips.animations().size());
					} catch (Exception e) {
						LOGGER.error("Failed to load viewmodel {}", skin.model, e);
					}
				}
			}

			return result;
		}

		@Override
		protected void apply(Map<String, Viewmodel> prepared, PreparableReloadListener.SharedState state) {
			loaded = prepared;
			ViewmodelAnimator.reset();
			GltfTextures.reloaded();
			Wear.setMaps(this.wearMaps);
		}
	}
}
