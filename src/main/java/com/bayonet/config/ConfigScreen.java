package com.bayonet.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public final class ConfigScreen {
	private static final String TITLE_KEY = "config.bayonet.title";

	private ConfigScreen() {
	}

	public static Screen create(@Nullable Screen parent) {
		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Component.translatable(TITLE_KEY))
				.transparentBackground()
				.setSavingRunnable(Settings::save);
		ConfigEntryBuilder entries = builder.entryBuilder();
		ConfigCategory category = builder.getOrCreateCategory(Component.translatable(TITLE_KEY));
		category.addEntry(keybinds(entries).build());
		category.addEntry(visuals(entries).build());
		return builder.build();
	}

	private static SubCategoryBuilder keybinds(ConfigEntryBuilder entries) {
		SubCategoryBuilder keybinds = entries.startSubCategory(Component.translatable("config.bayonet.keybinds")).setExpanded(true);

		for (KeyMapping key : KeyBindings.ALL) {
			keybinds.add(entries.fillKeybindingField(Component.translatable(key.getName()), key).build());
		}

		return keybinds;
	}

	private static SubCategoryBuilder visuals(ConfigEntryBuilder entries) {
		SubCategoryBuilder visuals = entries.startSubCategory(Component.translatable("config.bayonet.visuals")).setExpanded(true);
		visuals.add(entries.startBooleanToggle(Component.translatable("config.bayonet.arms"), Settings.arms())
				.setDefaultValue(Settings.DEFAULT_ARMS)
				.setTooltip(Component.translatable("config.bayonet.arms.tooltip"))
				.setSaveConsumer(Settings::setArms)
				.build());
		visuals.add(entries.startBooleanToggle(Component.translatable("config.bayonet.hide_glint"), !Settings.glint())
				.setDefaultValue(!Settings.DEFAULT_GLINT)
				.setTooltip(Component.translatable("config.bayonet.hide_glint.tooltip"))
				.setSaveConsumer(hide -> Settings.setGlint(!hide))
				.build());
		return visuals;
	}
}
