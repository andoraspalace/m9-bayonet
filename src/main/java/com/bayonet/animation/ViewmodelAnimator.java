package com.bayonet.animation;

import com.bayonet.config.KeyBindings;
import com.bayonet.sound.LocalActivity;
import com.bayonet.sound.WeaponSoundIds;
import com.bayonet.sound.WeaponSounds;
import com.bayonet.viewmodel.Viewmodels;
import com.bayonet.viewmodel.WeaponRig;
import com.bayonet.weapon.StatTrak;
import com.bayonet.weapon.Weapon;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public final class ViewmodelAnimator {
	private static final LocalViewmodel VIEWMODEL = new LocalViewmodel();
	private static final AwpReload AWP_RELOAD = new AwpReload();
	private static @Nullable ItemStack lastShot;
	private static int attacks;

	private ViewmodelAnimator() {
	}

	static void reset() {
		VIEWMODEL.reset();
		AWP_RELOAD.cancel();
	}

	static void tick(Minecraft minecraft) {
		VIEWMODEL.startTick();
		boolean inspect = consumeClicks(KeyBindings.INSPECT);
		boolean reload = consumeClicks(KeyBindings.RELOAD);
		LocalPlayer player = minecraft.player;
		MultiPlayerGameMode gameMode = minecraft.gameMode;
		boolean switched = selectHeld(player);
		Weapon weapon = VIEWMODEL.weapon();

		if (player == null || gameMode == null) {
			return;
		}

		if (weapon != null && !switched) {
			animate(gameMode, player, weapon, inspect, reload);
		}

		if (Awp.usingCrossbow(player)) {
			LocalActivity.markCrossbowUse(player.position(), animatesAwp(player.getUsedItemHand(), player.getUseItem()));
		}
	}

	public static boolean holdsDown(Minecraft minecraft, KeyMapping key) {
		LocalPlayer player = minecraft.player;
		return key == minecraft.options.keyUse && player != null && AWP_RELOAD.holdsUse(player);
	}

	public static boolean criticalReady(@Nullable LocalPlayer player) {
		return player != null && KnifeStrike.criticalReady(player);
	}

	public static void onCrossbowUse(LocalPlayer player, InteractionHand hand) {
		ItemStack crossbow = player.getItemInHand(hand);
		boolean shot = Awp.isMainHandAwp(hand, crossbow) && CrossbowItem.isCharged(crossbow) && crossbow != lastShot;

		if (shot) {
			lastShot = crossbow;
			StatTrak.onShoot(crossbow);
			selectHeld(player);
		}

		boolean animated = animatesAwp(hand, crossbow);
		LocalActivity.markCrossbowUse(player.position(), animated);

		if (shot && animated) {
			VIEWMODEL.play(WeaponRig.SHOOT, ClipRole.ACTION);
			WeaponSounds.play(WeaponSoundIds.AWP_SHOT);
		}
	}

	public static void onAttack(Minecraft minecraft, boolean criticalReady) {
		LocalPlayer player = minecraft.player;

		if (player == null) {
			return;
		}

		selectHeld(player);
		Weapon weapon = VIEWMODEL.weapon();
		boolean knife = weapon != null && weapon.isKnife();
		LocalActivity.markAttack(player.position(), knife);

		if (!knife) {
			return;
		}

		KnifeStrike strike = KnifeStrike.of(player, minecraft.hitResult, criticalReady);
		VIEWMODEL.play(strike.clip((attacks++ & 1) == 1), ClipRole.STRIKE);
		String sound = strike.sound();

		if (sound != null) {
			WeaponSounds.play(sound);
		}
	}

	public static boolean submit(
		ItemStack visibleItem, @Nullable AvatarRenderState avatar, float partialTicks, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords
	) {
		LocalPlayer player = Minecraft.getInstance().player;

		if (player != null && VIEWMODEL.submit(player, avatar, partialTicks, poseStack, submitNodeCollector, lightCoords)) {
			return true;
		}

		return Viewmodels.forStack(visibleItem) != null;
	}

	static @Nullable WeaponTurn weaponTurn(LocalPlayer player, float partialTicks) {
		return VIEWMODEL.turn(player, partialTicks);
	}

	private static void animate(MultiPlayerGameMode gameMode, LocalPlayer player, Weapon weapon, boolean inspect, boolean reload) {
		if (weapon == Weapon.AWP) {
			tickAwp(gameMode, player, reload);
		}

		if (inspect && !VIEWMODEL.followsCharge() && !AWP_RELOAD.active()) {
			VIEWMODEL.playInspect();
		}

		VIEWMODEL.tickSounds(player);
	}

	private static void tickAwp(MultiPlayerGameMode gameMode, LocalPlayer player, boolean reloadPressed) {
		ItemStack held = player.getMainHandItem();
		AWP_RELOAD.finish(gameMode, player, held);

		if (reloadPressed && !VIEWMODEL.followsCharge() && AWP_RELOAD.start(gameMode, player, held)) {
			VIEWMODEL.play(WeaponRig.RELOAD, ClipRole.ACTION);
		}

		boolean charging = !AWP_RELOAD.active() && Awp.isCharging(player, held);

		if (charging == VIEWMODEL.followsCharge()) {
			return;
		}

		if (charging) {
			VIEWMODEL.play(WeaponRig.RELOAD, ClipRole.CHARGE);
		} else {
			VIEWMODEL.playIdle();
		}
	}

	private static boolean animatesAwp(InteractionHand hand, ItemStack crossbow) {
		return Awp.isMainHandAwp(hand, crossbow) && VIEWMODEL.weapon() == Weapon.AWP;
	}

	private static boolean selectHeld(@Nullable LocalPlayer player) {
		if (!VIEWMODEL.select(player)) {
			return false;
		}

		AWP_RELOAD.cancel();
		return true;
	}

	private static boolean consumeClicks(KeyMapping key) {
		boolean clicked = false;

		while (key.consumeClick()) {
			clicked = true;
		}

		return clicked;
	}
}
