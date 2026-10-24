package com.bayonet.util;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public final class ClientTicks {
	private static final Identifier PHASE = ModIds.of("client_ticks");
	private static long ticks;

	private ClientTicks() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.addPhaseOrdering(PHASE, Event.DEFAULT_PHASE);
		ClientTickEvents.END_CLIENT_TICK.register(PHASE, ClientTicks::tick);
	}

	public static long ticks() {
		return ticks;
	}

	private static void tick(Minecraft minecraft) {
		if (!minecraft.isPaused()) {
			ticks++;
		}
	}
}
