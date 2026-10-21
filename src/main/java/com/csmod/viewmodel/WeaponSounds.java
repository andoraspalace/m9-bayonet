package com.csmod.viewmodel;

import com.csmod.CsModClient;
import com.csmod.viewmodel.Viewmodels.Weapon;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;

public final class WeaponSounds {
	private static final Set<String> CROSSBOW_SOUNDS = Set.of(
		"item.crossbow.loading_start", "item.crossbow.loading_middle", "item.crossbow.loading_end",
		"item.crossbow.quick_charge_1", "item.crossbow.quick_charge_2", "item.crossbow.quick_charge_3",
		"item.crossbow.shoot"
	);
	private static final Set<String> ATTACK_SOUNDS = Set.of(
		"entity.player.attack.sweep", "entity.player.attack.strong", "entity.player.attack.weak",
		"entity.player.attack.knockback", "entity.player.attack.crit", "entity.player.attack.nodamage"
	);

	private WeaponSounds() {
	}

	static void play(String id) {
		Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(
			CsModClient.id(id), SoundSource.PLAYERS, 1.0F, 1.0F, SoundInstance.createUnseededRandom(),
			false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true
		));
	}

	public static boolean isReplaced(SoundInstance sound) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		Identifier id = sound.getIdentifier();
		Weapon weapon = ViewmodelAnimator.heldWeapon();

		if (player == null || weapon == null || !id.getNamespace().equals(Identifier.DEFAULT_NAMESPACE)) {
			return false;
		}

		Set<String> replaced = weapon == Weapon.AWP ? CROSSBOW_SOUNDS : ATTACK_SOUNDS;

		if (!replaced.contains(id.getPath())) {
			return false;
		}

		PlayerInfo info = player.connection.getPlayerInfo(player.getUUID());
		double pingTicks = (info != null ? info.getLatency() : 0) / 50.0 + 2.0;
		double reach = 2.0 + player.getDeltaMovement().length() * pingTicks;
		return sound.isRelative() || player.distanceToSqr(sound.getX(), sound.getY(), sound.getZ()) < reach * reach;
	}
}
