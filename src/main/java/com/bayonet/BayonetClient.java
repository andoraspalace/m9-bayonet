package com.bayonet;

import com.bayonet.animation.AnimationSetup;
import com.bayonet.command.BayonetCommands;
import com.bayonet.config.KeyBindings;
import com.bayonet.render.RenderSetup;
import com.bayonet.sound.SoundSetup;
import com.bayonet.util.ClientTicks;
import com.bayonet.viewmodel.ViewmodelSetup;
import com.bayonet.weapon.WeaponSetup;
import net.fabricmc.api.ClientModInitializer;

public final class BayonetClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientTicks.register();
		KeyBindings.register();
		RenderSetup.register();
		ViewmodelSetup.register();
		SoundSetup.register();
		WeaponSetup.register();
		AnimationSetup.register();
		BayonetCommands.register();
	}
}
