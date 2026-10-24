package com.bayonet.viewmodel;

import com.bayonet.util.ModIds;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.server.packs.PackType;

public final class ViewmodelSetup {
	private ViewmodelSetup() {
	}

	public static void register() {
		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(ModIds.of("viewmodels"), new Viewmodels.ReloadListener());
	}
}
