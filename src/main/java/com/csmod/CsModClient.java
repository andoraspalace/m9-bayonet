package com.csmod;

import com.csmod.render.GltfSpecialRenderer;
import com.csmod.viewmodel.ViewmodelAnimator;
import com.csmod.viewmodel.Viewmodels;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;

public class CsModClient implements ClientModInitializer {
	public static final String MOD_ID = "csmod";

	@Override
	public void onInitializeClient() {
		SpecialModelRenderers.ID_MAPPER.put(id("gltf"), GltfSpecialRenderer.Unbaked.MAP_CODEC);

		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(id("viewmodels"), new Viewmodels.ReloadListener());
		KeyMappingHelper.registerKeyMapping(ViewmodelAnimator.INSPECT_KEY);
		ClientTickEvents.END_CLIENT_TICK.register(ViewmodelAnimator::tick);
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
