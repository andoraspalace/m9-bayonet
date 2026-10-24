package com.bayonet.weapon;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public final class SkinText {
	private static final int DETAIL_COLOR = 0xAAAAAA;
	private static final int STAT_TRAK_COLOR = 0xCF6A32;
	private static final int FIRST_DETAIL_LINE = 1;
	private static final String STAT_TRAK_TITLE_PREFIX = "StatTrak™ ";
	private static final String STAT_TRAK_ANVIL_PREFIX = "StatTrak ";
	private static final String ENTRY_INDENT = "  ";
	private static final String ENTRY_ARROW = "  →  ";
	private static final String PHASE_OPEN = " (";
	private static final String PHASE_CLOSE = ")";

	private SkinText() {
	}

	public static @Nullable Component displayName(ItemStack stack) {
		Skin.Look look = Skin.of(stack);
		return look != null ? csName(look.skin(), look.statTrak()).withStyle(style -> style.withItalic(false)) : null;
	}

	public static List<Component> catalog() {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("message.bayonet.skins.awp").withColor(DETAIL_COLOR));
		addEntries(lines, skin -> !skin.weapon().isKnife(), false);
		addEntries(lines, skin -> !skin.weapon().isKnife() && skin.hasStatTrak(), true);
		lines.add(Component.translatable("message.bayonet.skins.knives").withColor(DETAIL_COLOR));
		addEntries(lines, skin -> skin.weapon().isKnife(), false);
		return List.copyOf(lines);
	}

	static void addTooltip(ItemStack stack, List<Component> lines) {
		Skin.Look look = Skin.of(stack);

		if (look == null) {
			return;
		}

		Exterior exterior = look.exterior();
		Phase phase = look.skin().phase();
		int line = Math.min(FIRST_DETAIL_LINE, lines.size());

		if (exterior != null) {
			lines.add(line++, Component.translatable("tooltip.bayonet.exterior", Component.translatable(exterior.translationKey())).withColor(DETAIL_COLOR));
		}

		if (phase != null) {
			lines.add(line++, phase(phase).withColor(DETAIL_COLOR));
		}

		if (look.statTrak()) {
			lines.add(line, Component.translatable("tooltip.bayonet.stattrak", StatTrak.kills(look.skin())).withColor(STAT_TRAK_COLOR));
		}
	}

	private static void addEntries(List<Component> lines, Predicate<Skin> filter, boolean statTrak) {
		for (Skin skin : Skin.values()) {
			if (filter.test(skin)) {
				lines.add(entry(skin, statTrak));
			}
		}
	}

	private static Component entry(Skin skin, boolean statTrak) {
		String name = (statTrak ? STAT_TRAK_ANVIL_PREFIX : "") + skin.anvilName();
		Phase phase = skin.phase();
		MutableComponent display = csName(skin, statTrak);

		if (phase != null) {
			display.append(Component.literal(PHASE_OPEN).append(phase(phase)).append(PHASE_CLOSE).withColor(skin.color()));
		}

		return Component.literal(ENTRY_INDENT + name)
				.withStyle(style -> style.withColor(ChatFormatting.WHITE)
						.withClickEvent(new ClickEvent.CopyToClipboard(name))
						.withHoverEvent(new HoverEvent.ShowText(Component.translatable("message.bayonet.skins.copy"))))
				.append(Component.literal(ENTRY_ARROW).withColor(DETAIL_COLOR))
				.append(display);
	}

	private static MutableComponent csName(Skin skin, boolean statTrak) {
		MutableComponent name = Component.empty();

		if (statTrak) {
			name.append(Component.literal(STAT_TRAK_TITLE_PREFIX).withColor(STAT_TRAK_COLOR));
		}

		return name.append(Component.literal(skin.title()).withColor(skin.color()));
	}

	private static MutableComponent phase(Phase phase) {
		return Component.translatable("tooltip.bayonet.phase", Component.translatable(phase.translationKey()));
	}
}
