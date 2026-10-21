package com.csmod.viewmodel;

import com.csmod.CsModClient;
import com.csmod.gltf.GlbLoader;
import com.csmod.gltf.GltfAnimation;
import com.csmod.gltf.GltfScene;
import com.csmod.render.GltfTextures;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class Viewmodels {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static Map<Weapon, Viewmodel> loaded = Map.of();

	private Viewmodels() {
	}

	public enum Weapon {
		AWP(Items.CROSSBOW, "awp", "awp_asimov", "awp_anims", "draw", "idle", "lookat01", false),
		M9_BAYONET(Items.IRON_SWORD, "m9_bayonet", "m9_emerald", "m9_anims", "draw", "idle1", "lookat01", true);

		final Item item;
		public final Identifier definition;
		final String model;
		final String clips;
		final String draw;
		final String idle;
		final String inspect;
		final boolean oneHanded;

		Weapon(Item item, String definition, String model, String clips, String draw, String idle, String inspect, boolean oneHanded) {
			this.item = item;
			this.definition = CsModClient.id(definition);
			this.model = model;
			this.clips = clips;
			this.draw = draw;
			this.idle = idle;
			this.inspect = inspect;
			this.oneHanded = oneHanded;
		}

		public static @Nullable Weapon of(ItemStack stack) {
			for (Weapon weapon : values()) {
				if (stack.is(weapon.item)) {
					return isPlain(stack) ? weapon : null;
				}
			}

			return null;
		}

		private static boolean isPlain(ItemStack stack) {
			return !stack.has(DataComponents.CUSTOM_MODEL_DATA)
					&& Objects.equals(stack.get(DataComponents.ITEM_MODEL), stack.getPrototype().get(DataComponents.ITEM_MODEL));
		}
	}

	static @Nullable Viewmodel get(@Nullable Weapon weapon) {
		return weapon != null ? loaded.get(weapon) : null;
	}

	static @Nullable Quaternionf turn(Weapon weapon, String clip, float progress) {
		Viewmodel viewmodel = get(weapon);
		GltfAnimation animation = viewmodel != null ? viewmodel.clip(clip) : null;
		return animation != null ? viewmodel.weaponTurn(animation, progress * animation.duration()) : null;
	}

	private static GltfScene load(ResourceManager resources, String name) throws IOException {
		try (InputStream stream = resources.open(CsModClient.id("models/gltf/" + name + ".glb"))) {
			return GlbLoader.loadScene(stream.readAllBytes());
		}
	}

	public static class ReloadListener extends SimpleReloadListener<Map<Weapon, Viewmodel>> {
		@Override
		protected Map<Weapon, Viewmodel> prepare(PreparableReloadListener.SharedState state) {
			Map<Weapon, Viewmodel> result = new EnumMap<>(Weapon.class);

			for (Weapon weapon : Weapon.values()) {
				try {
					GltfScene model = load(state.resourceManager(), weapon.model);
					GltfScene clips = load(state.resourceManager(), weapon.clips);
					result.put(weapon, new Viewmodel(CsModClient.id(weapon.model), model, clips, weapon.idle, weapon.oneHanded));
					LOGGER.info("Loaded viewmodel {}: {} clips", weapon, clips.animations().size());
				} catch (Exception e) {
					LOGGER.error("Failed to load viewmodel {}", weapon, e);
				}
			}

			return result;
		}

		@Override
		protected void apply(Map<Weapon, Viewmodel> prepared, PreparableReloadListener.SharedState state) {
			loaded = prepared;
			ViewmodelAnimator.reset();
			GltfTextures.reloaded();
		}
	}
}
