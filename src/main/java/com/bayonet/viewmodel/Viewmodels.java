package com.bayonet.viewmodel;

import com.bayonet.gltf.GltfScene;
import com.bayonet.render.SceneCache;
import com.bayonet.util.ModIds;
import com.bayonet.weapon.Skin;
import com.bayonet.weapon.Weapon;
import com.mojang.logging.LogUtils;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class Viewmodels {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final String STATTRAK_MODEL = "stattrak_module";
	private static final List<String> ARM_MODELS = List.of("arms", "sleeves");
	private static final List<Runnable> RELOAD_OBSERVERS = new ArrayList<>();
	private static Map<Skin, Viewmodel> loaded = Map.of();

	private Viewmodels() {
	}

	public static void onReload(Runnable observer) {
		RELOAD_OBSERVERS.add(observer);
	}

	public static @Nullable Viewmodel get(Weapon weapon) {
		return loaded.get(Skin.fallback(weapon));
	}

	public static @Nullable Viewmodel forStack(ItemStack stack) {
		Skin skin = Skin.find(stack);

		if (skin == null) {
			return null;
		}

		Viewmodel viewmodel = loaded.get(skin);
		return viewmodel != null ? viewmodel : get(skin.weapon());
	}

	private static GltfScene load(ResourceManager resources, String name) throws IOException {
		return SceneCache.load(resources, ModIds.of(name));
	}

	private static @Nullable GltfScene loadOptional(ResourceManager resources, String name) {
		try {
			return load(resources, name);
		} catch (FileNotFoundException e) {
			LOGGER.warn("No {} model", name);
		} catch (IOException | RuntimeException e) {
			LOGGER.error("Failed to load the {} model", name, e);
		}

		return null;
	}

	private static @Nullable StatTrakModule loadStatTrak(ResourceManager resources) {
		GltfScene model = loadOptional(resources, STATTRAK_MODEL);
		return model != null ? new StatTrakModule(ModIds.of(STATTRAK_MODEL), model) : null;
	}

	private static @Nullable Arms loadArms(ResourceManager resources) {
		List<Arms.Piece> pieces = new ArrayList<>();

		for (String name : ARM_MODELS) {
			GltfScene model = loadOptional(resources, name);

			if (model != null) {
				pieces.add(new Arms.Piece(ModIds.of(name), model));
			}
		}

		return pieces.isEmpty() ? null : new Arms(pieces);
	}

	private static Map<Weapon, BoundRig> bindWeapons(ResourceManager resources, @Nullable Arms arms) {
		Map<Weapon, BoundRig> bindings = new EnumMap<>(Weapon.class);

		for (Weapon weapon : Weapon.values()) {
			WeaponRig tuning = WeaponRig.of(weapon);

			try {
				bindings.put(weapon, BoundRig.bind(load(resources, tuning.clipsModel()), tuning, arms));
			} catch (IOException | RuntimeException e) {
				LOGGER.error("Failed to load the clips of {}", weapon, e);
			}
		}

		return bindings;
	}

	static final class ReloadListener extends SimpleReloadListener<Map<Skin, Viewmodel>> {
		@Override
		protected Map<Skin, Viewmodel> prepare(PreparableReloadListener.SharedState state) {
			ResourceManager resources = state.resourceManager();
			StatTrakModule statTrak = loadStatTrak(resources);
			Map<Weapon, BoundRig> bindings = bindWeapons(resources, loadArms(resources));
			Map<Skin, Viewmodel> result = new EnumMap<>(Skin.class);

			for (Skin skin : Skin.values()) {
				BoundRig binding = bindings.get(skin.weapon());

				if (binding == null) {
					continue;
				}

				try {
					GltfScene model = load(resources, skin.model());
					result.put(skin, new Viewmodel(skin.definition(), model, binding, skin.hasStatTrak() ? statTrak : null));
					LOGGER.info("Loaded viewmodel {}: {} clips", skin.model(), binding.rig().animations().size());
				} catch (IOException | RuntimeException e) {
					LOGGER.error("Failed to load viewmodel {}", skin.model(), e);
				}
			}

			return result;
		}

		@Override
		protected void apply(Map<Skin, Viewmodel> prepared, PreparableReloadListener.SharedState state) {
			loaded = prepared;
			RELOAD_OBSERVERS.forEach(Runnable::run);
		}
	}
}
