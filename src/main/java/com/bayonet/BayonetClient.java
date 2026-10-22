package com.bayonet;

import com.bayonet.render.GltfSpecialRenderer;
import com.bayonet.render.Wear;
import com.bayonet.viewmodel.Skin;
import com.bayonet.viewmodel.StatTrak;
import com.bayonet.viewmodel.ViewmodelAnimator;
import com.bayonet.viewmodel.Viewmodels;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;

public class BayonetClient implements ClientModInitializer {
	public static final String MOD_ID = "bayonet";

	@Override
	public void onInitializeClient() {
		SpecialModelRenderers.ID_MAPPER.put(id("gltf"), GltfSpecialRenderer.Unbaked.MAP_CODEC);

		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(id("viewmodels"), new Viewmodels.ReloadListener());
		KeyMappingHelper.registerKeyMapping(ViewmodelAnimator.INSPECT_KEY);
		KeyMappingHelper.registerKeyMapping(ViewmodelAnimator.RELOAD_KEY);
		ClientTickEvents.END_CLIENT_TICK.register(ViewmodelAnimator::tick);
		ClientTickEvents.END_CLIENT_TICK.register(StatTrak::tick);
		ClientTickEvents.END_CLIENT_TICK.register(client -> Wear.collect());
		ClientEntityEvents.ENTITY_LOAD.register(StatTrak::onEntityLoad);
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> Skin.addTooltip(stack, lines));
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(
			ClientCommands.literal("bayonet").then(ClientCommands.literal("glint").executes(command -> {
				boolean on = Settings.toggleGlint();
				command.getSource().sendFeedback(Component.translatable(on ? "message.bayonet.glint_on" : "message.bayonet.glint_off"));
				return 1;
			}))
		));
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
