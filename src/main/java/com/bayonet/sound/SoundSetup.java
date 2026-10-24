package com.bayonet.sound;

import com.bayonet.util.ModIds;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.server.packs.PackType;

public final class SoundSetup {
	private SoundSetup() {
	}

	public static void register() {
		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(ModIds.of("weapon_sounds"), new SoundBehaviours.ReloadListener());
	}
}
