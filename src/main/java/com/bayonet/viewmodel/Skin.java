package com.bayonet.viewmodel;

import com.bayonet.BayonetClient;
import com.bayonet.viewmodel.Viewmodels.Weapon;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jspecify.annotations.Nullable;

public enum Skin {
	AWP_DEFAULT(Weapon.AWP, "awp_default", "AWP", null, 0.0F, 0.0F, null, Skin.STOCK_COLOR),
	AWP_ASIMOV(Weapon.AWP, "awp_asimov", "AWP | Asiimov", "asimov", 0.18F, 1.0F, null, Skin.COVERT_COLOR),
	AWP_DRAGON_LORE(Weapon.AWP, "awp_dragon_lore", "AWP | Dragon Lore", "dragon_lore", 0.0F, 0.7F, null, Skin.COVERT_COLOR),
	M9_DEFAULT(Weapon.M9_BAYONET, "m9_default", "★ M9 Bayonet", null, 0.0F, 0.0F, null, Skin.KNIFE_COLOR),
	M9_EMERALD(Weapon.M9_BAYONET, "m9_emerald", "★ M9 Bayonet | Gamma Doppler", null, 0.0F, 0.08F, "emerald", Skin.KNIFE_COLOR);

	private static final int STOCK_COLOR = 0xFFFFFF;
	private static final int COVERT_COLOR = 0xEB4B4B;
	private static final int KNIFE_COLOR = 0x8650AC;
	private static final int DETAIL_COLOR = 0xAAAAAA;
	private static final int STATTRAK_COLOR = 0xCF6A32;

	public final Weapon weapon;
	public final String model;
	public final String title;
	final @Nullable String statTrakKey;
	private final float wearMin;
	private final float wearMax;
	private final @Nullable String phase;
	private final int color;

	Skin(Weapon weapon, String model, String title, @Nullable String statTrakKey, float wearMin, float wearMax, @Nullable String phase, int color) {
		this.weapon = weapon;
		this.model = model;
		this.title = title;
		this.statTrakKey = statTrakKey;
		this.wearMin = wearMin;
		this.wearMax = wearMax;
		this.phase = phase;
		this.color = color;
	}

	public Identifier definition() {
		return BayonetClient.id(this.model);
	}

	private boolean wears() {
		return this.wearMax > 0.0F;
	}

	public enum Exterior {
		FACTORY_NEW(0.0F, 0.07F),
		MINIMAL_WEAR(0.07F, 0.15F),
		FIELD_TESTED(0.15F, 0.38F),
		WELL_WORN(0.38F, 0.45F),
		BATTLE_SCARRED(0.45F, 1.0F);

		private final float min;
		private final float max;

		Exterior(float min, float max) {
			this.min = min;
			this.max = max;
		}

		static Exterior of(float wear) {
			for (Exterior exterior : values()) {
				if (wear < exterior.max) {
					return exterior;
				}
			}

			return BATTLE_SCARRED;
		}

		public String translationKey() {
			return "exterior.bayonet." + this.name().toLowerCase(Locale.ROOT);
		}
	}

	public record Look(Skin skin, boolean statTrak, @Nullable Exterior exterior) {
		public float wear() {
			if (this.exterior == null || !this.skin.wears()) {
				return 0.0F;
			}

			float min = Math.max(this.exterior.min, this.skin.wearMin);
			float max = Math.min(this.exterior.max, this.skin.wearMax);
			return Math.max(0.0F, (min + Math.max(min, max)) / 2.0F);
		}
	}

	public static @Nullable Look of(ItemStack stack) {
		Weapon weapon = stack.is(Items.CROSSBOW) ? Weapon.AWP : stack.is(Items.IRON_SWORD) ? Weapon.M9_BAYONET : null;

		if (weapon == null) {
			return null;
		}

		ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);

		if (level(enchantments, Enchantments.VANISHING_CURSE) == 0) {
			return weapon == Weapon.AWP && !enchantments.isEmpty() ? new Look(AWP_DEFAULT, false, null) : null;
		}

		boolean mending = level(enchantments, Enchantments.MENDING) > 0;
		int unbreaking = level(enchantments, Enchantments.UNBREAKING);
		boolean unbreakingOneOrTwo = unbreaking == 1 || unbreaking == 2;
		Skin skin = weapon == Weapon.AWP
				? mending ? AWP_ASIMOV : AWP_DRAGON_LORE
				: !mending && unbreakingOneOrTwo ? M9_EMERALD : M9_DEFAULT;
		boolean statTrak = skin.statTrakKey != null && unbreakingOneOrTwo;

		if (!skin.wears()) {
			return new Look(skin, statTrak, null);
		}

		float used = stack.isDamageableItem() ? Math.min(1.0F, (float) stack.getDamageValue() / stack.getMaxDamage()) : 0.0F;
		float wear = skin.wearMin + used * (skin.wearMax - skin.wearMin);
		return new Look(skin, statTrak, Exterior.of(wear));
	}

	private static int level(ItemEnchantments enchantments, ResourceKey<Enchantment> enchantment) {
		for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
			if (entry.getKey().is(enchantment)) {
				return entry.getIntValue();
			}
		}

		return 0;
	}

	public static @Nullable Component displayName(ItemStack stack) {
		Look look = of(stack);

		if (look == null) {
			return null;
		}

		MutableComponent name = Component.empty();

		if (look.statTrak()) {
			name.append(Component.literal("StatTrak™ ").withColor(STATTRAK_COLOR));
		}

		return name.append(Component.literal(look.skin().title).withColor(look.skin().color));
	}

	public static void addTooltip(ItemStack stack, List<Component> lines) {
		Look look = of(stack);

		if (look == null) {
			return;
		}

		int line = Math.min(1, lines.size());

		if (look.exterior() != null) {
			lines.add(line++, Component.translatable("tooltip.bayonet.exterior", Component.translatable(look.exterior().translationKey())).withColor(DETAIL_COLOR));
		}

		if (look.skin().phase != null) {
			lines.add(line++, Component.translatable("tooltip.bayonet.phase", Component.translatable("phase.bayonet." + look.skin().phase)).withColor(DETAIL_COLOR));
		}

		if (look.statTrak()) {
			lines.add(line, Component.translatable("tooltip.bayonet.stattrak", StatTrak.kills(look.skin())).withColor(STATTRAK_COLOR));
		}
	}
}
