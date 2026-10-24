package com.bayonet.weapon;

import com.bayonet.util.ModIds;
import java.util.Arrays;
import java.util.Objects;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

public enum Skin {
	AWP_DRAGON_LORE(
		Weapon.AWP, "awp_dragon_lore", "AWP | Dragon Lore", "Dragon Lore", "dragon_lore", new WearRange(0.0F, 0.7F), null, Skin.COVERT_COLOR, "dragonlore"
	),
	AWP_ASIMOV(Weapon.AWP, "awp_asimov", "AWP | Asiimov", "Asiimov", "asimov", new WearRange(0.0F, 1.0F), null, Skin.COVERT_COLOR, "asiimov|asimov"),
	M9_DEFAULT(Weapon.M9_BAYONET, "m9_default", "★ M9 Bayonet", "M9 Bayonet", null, WearRange.NONE, null, Skin.KNIFE_COLOR, "m9|bayonet"),
	M9_EMERALD(
		Weapon.M9_BAYONET, "m9_emerald", "★ M9 Bayonet | Gamma Doppler", "M9 Bayonet Emerald", null, WearRange.DOPPLER, Phase.EMERALD, Skin.KNIFE_COLOR,
		"m9|bayonet", "gamma", "doppler", "emerald"
	),
	M9_RUBY(
		Weapon.M9_BAYONET, "m9_ruby", "★ M9 Bayonet | Doppler", "M9 Bayonet Ruby", null, WearRange.DOPPLER, Phase.RUBY, Skin.KNIFE_COLOR,
		"m9|bayonet", "doppler", "ruby"
	),
	BUTTERFLY_DEFAULT(Weapon.BUTTERFLY, "butterfly_default", "★ Butterfly Knife", "Butterfly", null, WearRange.NONE, null, Skin.KNIFE_COLOR, "butterfly"),
	BUTTERFLY_SAPPHIRE(
		Weapon.BUTTERFLY, "butterfly_sapphire", "★ Butterfly Knife | Doppler", "Butterfly Sapphire", null, WearRange.DOPPLER, Phase.SAPPHIRE,
		Skin.KNIFE_COLOR, "butterfly", "doppler", "sapphire"
	),
	BUTTERFLY_RUBY(
		Weapon.BUTTERFLY, "butterfly_ruby", "★ Butterfly Knife | Doppler", "Butterfly Ruby", null, WearRange.DOPPLER, Phase.RUBY, Skin.KNIFE_COLOR,
		"butterfly", "doppler", "ruby"
	),
	BUTTERFLY_GAMMA_DOPPLER(
		Weapon.BUTTERFLY, "butterfly_gamma_doppler", "★ Butterfly Knife | Gamma Doppler", "Butterfly Gamma Doppler", null, WearRange.DOPPLER, Phase.PHASE2,
		Skin.KNIFE_COLOR, "butterfly", "gamma", "doppler", "phase2"
	);

	public static final float UNWORN = 0.0F;
	private static final int COVERT_COLOR = 0xEB4B4B;
	private static final int KNIFE_COLOR = 0x8650AC;
	private static final String SPELLING_SEPARATOR = "\\|";
	private static final String STAT_TRAK_KEYWORD = "stattrak";
	private static final Skin[] SKINS = values();

	private final Weapon weapon;
	private final String model;
	private final Identifier definition;
	private final String title;
	private final String anvilName;
	private final @Nullable String statTrakKey;
	private final WearRange wearRange;
	private final @Nullable Phase phase;
	private final int color;
	private final String[][] keywords;

	Skin(
		Weapon weapon, String model, String title, String anvilName, @Nullable String statTrakKey, WearRange wearRange, @Nullable Phase phase, int color,
		String... keywords
	) {
		this.weapon = weapon;
		this.model = model;
		this.definition = ModIds.of(model);
		this.title = title;
		this.anvilName = anvilName;
		this.statTrakKey = statTrakKey;
		this.wearRange = wearRange;
		this.phase = phase;
		this.color = color;
		this.keywords = Arrays.stream(keywords).map(keyword -> keyword.split(SPELLING_SEPARATOR)).toArray(String[][]::new);
	}

	public Weapon weapon() {
		return this.weapon;
	}

	public String model() {
		return this.model;
	}

	public Identifier definition() {
		return this.definition;
	}

	public String anvilName() {
		return this.anvilName;
	}

	public boolean hasStatTrak() {
		return this.statTrakKey != null;
	}

	@Nullable String statTrakKey() {
		return this.statTrakKey;
	}

	String title() {
		return this.title;
	}

	@Nullable Phase phase() {
		return this.phase;
	}

	int color() {
		return this.color;
	}

	public static Skin fallback(Weapon weapon) {
		return switch (weapon) {
			case AWP -> AWP_ASIMOV;
			case M9_BAYONET -> M9_DEFAULT;
			case BUTTERFLY -> BUTTERFLY_DEFAULT;
		};
	}

	public static @Nullable Skin find(ItemStack stack) {
		String name = plainName(stack);
		return name != null ? pick(stack, name) : null;
	}

	public static @Nullable Look of(ItemStack stack) {
		String name = plainName(stack);

		if (name == null) {
			return null;
		}

		Skin skin = pick(stack, name);

		if (skin == null) {
			return null;
		}

		boolean statTrak = skin.hasStatTrak() && name.contains(STAT_TRAK_KEYWORD);
		return new Look(skin, statTrak, skin.wears() ? Exterior.of(skin.wearAt(stack)) : null);
	}

	public static float wearOf(@Nullable Look look) {
		return look != null ? look.wear() : UNWORN;
	}

	private static @Nullable String plainName(ItemStack stack) {
		if (!stack.is(Items.IRON_SWORD) && !stack.is(Items.CROSSBOW)) {
			return null;
		}

		Component customName = stack.getCustomName();
		return customName != null && isPlain(stack) ? normalize(customName) : null;
	}

	private static boolean isPlain(ItemStack stack) {
		return !stack.has(DataComponents.CUSTOM_MODEL_DATA)
				&& Objects.equals(stack.get(DataComponents.ITEM_MODEL), stack.getPrototype().get(DataComponents.ITEM_MODEL));
	}

	private static String normalize(Component name) {
		String raw = name.getString();
		StringBuilder letters = new StringBuilder(raw.length());

		for (int i = 0; i < raw.length(); ) {
			int codePoint = raw.codePointAt(i);
			int lower = Character.toLowerCase(codePoint);

			if (Character.isLetterOrDigit(lower)) {
				letters.appendCodePoint(lower);
			}

			i += Character.charCount(codePoint);
		}

		return letters.toString();
	}

	private static @Nullable Skin pick(ItemStack stack, String name) {
		boolean knife = stack.is(Items.IRON_SWORD);
		Skin picked = null;
		int best = 0;

		for (Skin skin : SKINS) {
			if (skin.weapon.isKnife() != knife) {
				continue;
			}

			int matched = skin.matches(name);

			if (matched > best || matched > 0 && matched == best && skin.keywords.length < picked.keywords.length) {
				picked = skin;
				best = matched;
			}
		}

		return picked;
	}

	private int matches(String name) {
		if (!containsAny(name, this.keywords[0])) {
			return 0;
		}

		int matched = 1;

		for (int i = 1; i < this.keywords.length; i++) {
			if (containsAny(name, this.keywords[i])) {
				matched++;
			}
		}

		return matched;
	}

	private static boolean containsAny(String name, String[] spellings) {
		for (String spelling : spellings) {
			if (name.contains(spelling)) {
				return true;
			}
		}

		return false;
	}

	private boolean wears() {
		return !this.wearRange.isEmpty();
	}

	private float wearAt(ItemStack stack) {
		float used = stack.isDamageableItem() ? (float) stack.getDamageValue() / stack.getMaxDamage() : 0.0F;
		return this.wearRange.at(used);
	}

	public record Look(Skin skin, boolean statTrak, @Nullable Exterior exterior) {
		public float wear() {
			return this.exterior != null && this.skin.wears() ? this.exterior.typicalWear(this.skin.wearRange) : UNWORN;
		}
	}
}
