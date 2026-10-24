package com.bayonet.command;

import com.bayonet.config.Settings;
import com.bayonet.util.ModIds;
import com.bayonet.weapon.SkinText;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

public final class BayonetCommands {
	private BayonetCommands() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> dispatcher.register(root()));
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> root() {
		return ClientCommands.literal(ModIds.NAMESPACE)
				.then(toggle("glint", Settings::toggleGlint, "message.bayonet.glint_on", "message.bayonet.glint_off"))
				.then(ClientCommands.literal("skins").executes(BayonetCommands::listSkins))
				.then(toggle("arms", Settings::toggleArms, "message.bayonet.arms_on", "message.bayonet.arms_off"));
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> toggle(String name, BooleanSupplier toggle, String onKey, String offKey) {
		return ClientCommands.literal(name).executes(context -> {
			boolean on = toggle.getAsBoolean();
			context.getSource().sendFeedback(Component.translatable(on ? onKey : offKey));
			return Command.SINGLE_SUCCESS;
		});
	}

	private static int listSkins(CommandContext<FabricClientCommandSource> context) {
		SkinText.catalog().forEach(context.getSource()::sendFeedback);
		return Command.SINGLE_SUCCESS;
	}
}
