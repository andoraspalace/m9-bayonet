package com.bayonet.render;

import com.bayonet.util.ModIds;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.server.packs.PackType;

public final class RenderSetup {
	private RenderSetup() {
	}

	public static void register() {
		SpecialModelRenderers.ID_MAPPER.put(ModIds.of("gltf"), GltfSpecialRenderer.Unbaked.MAP_CODEC);
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> TextureLoader.collect());
		ClientLifecycleEvents.CLIENT_STOPPING.register(minecraft -> TextureLoader.shutdown());
		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(ModIds.of("render"), new RenderReloadListener());
	}
}
