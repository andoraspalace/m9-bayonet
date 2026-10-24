package com.bayonet.render;

import java.util.Map;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.minecraft.server.packs.resources.PreparableReloadListener;

final class RenderReloadListener extends SimpleReloadListener<Map<String, Wear.Mask>> {
	@Override
	public void prepareSharedState(PreparableReloadListener.SharedState state) {
		SceneCache.begin();
	}

	@Override
	protected Map<String, Wear.Mask> prepare(PreparableReloadListener.SharedState state) {
		return Wear.load(state.resourceManager());
	}

	@Override
	protected void apply(Map<String, Wear.Mask> masks, PreparableReloadListener.SharedState state) {
		SceneCache.end();
		TextureLoader.reloaded();
		Wear.setMaps(masks);
	}
}
