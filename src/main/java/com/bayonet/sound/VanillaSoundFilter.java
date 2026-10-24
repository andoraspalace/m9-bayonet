package com.bayonet.sound;

import com.bayonet.weapon.Weapon;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class VanillaSoundFilter {
	private static final double OWN_REACH = 2.0;
	private static final double OTHERS_REACH = 3.0;
	private static final double RESPONSE_SLACK_TICKS = 2.0;
	private static final double ACTIVITY_GRACE_TICKS = 2.0;
	private static final double SERVER_LAG_GRACE_TICKS = SharedConstants.TICKS_PER_SECOND;
	private static final Map<Identifier, Cue> CUES = Map.ofEntries(
		cue(SoundEvents.CROSSBOW_LOADING_START.value(), Origin.AWP, null),
		cue(SoundEvents.CROSSBOW_LOADING_MIDDLE.value(), Origin.AWP, null),
		cue(SoundEvents.CROSSBOW_LOADING_END.value(), Origin.AWP, null),
		cue(SoundEvents.CROSSBOW_QUICK_CHARGE_1.value(), Origin.AWP, null),
		cue(SoundEvents.CROSSBOW_QUICK_CHARGE_2.value(), Origin.AWP, null),
		cue(SoundEvents.CROSSBOW_QUICK_CHARGE_3.value(), Origin.AWP, null),
		cue(SoundEvents.CROSSBOW_SHOOT, Origin.AWP, null),
		cue(SoundEvents.PLAYER_ATTACK_SWEEP, Origin.KNIFE, WeaponSoundIds.KNIFE_HIT_LIGHT),
		cue(SoundEvents.PLAYER_ATTACK_STRONG, Origin.KNIFE, WeaponSoundIds.KNIFE_HIT_LIGHT),
		cue(SoundEvents.PLAYER_ATTACK_WEAK, Origin.KNIFE, WeaponSoundIds.KNIFE_HIT_LIGHT),
		cue(SoundEvents.PLAYER_ATTACK_NODAMAGE, Origin.KNIFE, WeaponSoundIds.KNIFE_HIT_LIGHT),
		cue(SoundEvents.PLAYER_ATTACK_CRIT, Origin.KNIFE, WeaponSoundIds.KNIFE_HIT_HEAVY),
		cue(SoundEvents.PLAYER_ATTACK_KNOCKBACK, Origin.KNIFE, null)
	);

	private VanillaSoundFilter() {
	}

	public static boolean intercept(SoundInstance sound) {
		Cue cue = CUES.get(sound.getIdentifier());

		if (cue == null || sound.isRelative()) {
			return false;
		}

		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer self = minecraft.player;
		ClientLevel level = minecraft.level;

		if (self == null || level == null) {
			return false;
		}

		Origin origin = cue.origin();
		LocalActivity.Actions actions = origin.localActions();
		Vec3 position = new Vec3(sound.getX(), sound.getY(), sound.getZ());
		double responseTicks = responseTicks(self);
		double activityTicks = responseTicks + ACTIVITY_GRACE_TICKS;
		double weaponTicks = origin.heldBy(self) ? activityTicks + SERVER_LAG_GRACE_TICKS : activityTicks;
		boolean usedWeapon = actions.ticksSinceWeapon() <= weaponTicks;
		boolean acted = usedWeapon || actions.ticksSinceAny() <= activityTicks;
		double ownSlack = self.getKnownSpeed().length() * responseTicks;
		AbstractClientPlayer remote = nearestRemote(level, self, position);

		if (acted && isOwnSound(self, actions.distanceTo(position, activityTicks, weaponTicks), ownSlack, remote, position)) {
			return usedWeapon;
		}

		if (remote == null || !origin.heldBy(remote)) {
			return false;
		}

		String replacement = cue.remoteReplacement();

		if (replacement != null) {
			WeaponSounds.playAt(replacement, remote);
		}

		return true;
	}

	private static double responseTicks(LocalPlayer self) {
		PlayerInfo info = self.connection.getPlayerInfo(self.getUUID());
		int latency = info != null ? info.getLatency() : 0;
		return (double) latency / SharedConstants.MILLIS_PER_TICK + RESPONSE_SLACK_TICKS;
	}

	private static @Nullable AbstractClientPlayer nearestRemote(ClientLevel level, LocalPlayer self, Vec3 position) {
		AbstractClientPlayer nearest = null;
		double nearestDistance = OTHERS_REACH;

		for (AbstractClientPlayer player : level.players()) {
			if (player == self || player.isSpectator()) {
				continue;
			}

			double distance = player.position().distanceTo(position);

			if (distance < nearestDistance) {
				nearest = player;
				nearestDistance = distance;
			}
		}

		return nearest;
	}

	private static boolean isOwnSound(LocalPlayer self, double actionDistance, double ownSlack, @Nullable AbstractClientPlayer remote, Vec3 position) {
		if (self.isSpectator()) {
			return false;
		}

		double distance = Math.min(self.position().distanceTo(position), actionDistance);
		return distance < OWN_REACH + ownSlack && (remote == null || distance < remote.position().distanceTo(position));
	}

	private static Map.Entry<Identifier, Cue> cue(SoundEvent event, Origin origin, @Nullable String remoteReplacement) {
		return Map.entry(event.location(), new Cue(origin, remoteReplacement));
	}

	private record Cue(Origin origin, @Nullable String remoteReplacement) {
	}

	private enum Origin {
		AWP,
		KNIFE;

		boolean heldBy(Player player) {
			Weapon weapon = Weapon.of(player.getMainHandItem());
			return weapon != null && weapon.isKnife() == (this == KNIFE);
		}

		LocalActivity.Actions localActions() {
			return switch (this) {
				case AWP -> LocalActivity.crossbowUses();
				case KNIFE -> LocalActivity.attacks();
			};
		}
	}
}
