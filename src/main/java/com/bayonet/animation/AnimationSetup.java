package com.bayonet.animation;

import com.bayonet.viewmodel.Viewmodels;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class AnimationSetup {
	private AnimationSetup() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(ViewmodelAnimator::tick);
		ClientTickEvents.END_CLIENT_TICK.register(RemoteWeapons::tick);
		Viewmodels.onReload(ViewmodelAnimator::reset);
		Viewmodels.onReload(RemoteWeapons::reset);
	}
}
