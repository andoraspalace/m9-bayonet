package com.bayonet.config;

import com.bayonet.util.ModIds;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

public final class KeyBindings {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(ModIds.of("weapons"));
	public static final KeyMapping INSPECT = new KeyMapping("key.bayonet.inspect", InputConstants.KEY_G, CATEGORY);
	public static final KeyMapping RELOAD = new KeyMapping("key.bayonet.reload", InputConstants.KEY_R, CATEGORY);
	public static final List<KeyMapping> ALL = List.of(INSPECT, RELOAD);

	private KeyBindings() {
	}

	public static void register() {
		ALL.forEach(KeyMappingHelper::registerKeyMapping);
	}
}
