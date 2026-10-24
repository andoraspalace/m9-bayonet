package com.bayonet.weapon;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;

public final class WeaponSetup {
	private WeaponSetup() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(StatTrak::tick);
		ClientEntityEvents.ENTITY_LOAD.register(StatTrak::onEntityLoad);
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> SkinText.addTooltip(stack, lines));
	}
}
