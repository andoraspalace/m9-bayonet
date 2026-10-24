package com.bayonet.animation;

import com.bayonet.gltf.GltfAnimation;
import com.bayonet.sound.WeaponSoundIds;
import com.bayonet.sound.WeaponSounds;
import com.bayonet.viewmodel.Viewmodel;
import com.bayonet.viewmodel.Viewmodels;
import com.bayonet.viewmodel.WeaponRig;
import com.bayonet.weapon.Weapon;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

final class RemoteWeapons {
	private static final float END_OF_TICK = 1.0F;
	private static final Map<UUID, Tracker> TRACKERS = new HashMap<>();

	private RemoteWeapons() {
	}

	static void reset() {
		TRACKERS.clear();
	}

	static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;

		if (level == null || minecraft.player == null) {
			reset();
			return;
		}

		if (minecraft.isPaused()) {
			return;
		}

		double now = ClipPlayer.now();
		Set<UUID> present = new HashSet<>();

		for (AbstractClientPlayer player : level.players()) {
			if (player == minecraft.player) {
				continue;
			}

			present.add(player.getUUID());
			Tracker tracker = TRACKERS.get(player.getUUID());

			if (tracker == null) {
				TRACKERS.put(player.getUUID(), new Tracker(player));
			} else {
				tracker.tick(level, player, now);
			}
		}

		TRACKERS.keySet().retainAll(present);
	}

	private static boolean isBreakingBlock(ClientLevel level, AbstractClientPlayer player) {
		int id = player.getId();

		for (SortedSet<BlockDestructionProgress> progresses : level.destructionProgress().values()) {
			for (BlockDestructionProgress progress : progresses) {
				if (progress.getId() == id) {
					return true;
				}
			}
		}

		return false;
	}

	private static boolean isSameItem(ItemStack previous, ItemStack current) {
		return ItemStack.matchesIgnoringComponents(previous, current, RemoteWeapons::changesWithUse);
	}

	private static boolean changesWithUse(DataComponentType<?> type) {
		return type == DataComponents.CHARGED_PROJECTILES || type == DataComponents.DAMAGE;
	}

	private static final class Tracker {
		private final ClipPlayer clips = new ClipPlayer();
		private ItemStack held;
		private @Nullable Viewmodel viewmodel;
		private boolean charged;
		private LivingEntity.@Nullable SwingDescription swing;
		private double y;
		private int attacks;

		Tracker(AbstractClientPlayer player) {
			this.held = player.getMainHandItem();
			this.viewmodel = Viewmodels.forStack(this.held);
			this.charged = CrossbowItem.isCharged(this.held);
			this.swing = player.getCurrentSwing();
			this.y = player.getY();
		}

		void tick(ClientLevel level, AbstractClientPlayer player, double now) {
			ItemStack held = player.getMainHandItem();
			Viewmodel heldViewmodel = Viewmodels.forStack(held);
			Weapon weapon = Weapon.of(held);
			boolean charged = CrossbowItem.isCharged(held);
			LivingEntity.SwingDescription swing = player.getCurrentSwing();

			if (heldViewmodel != this.viewmodel || !isSameItem(this.held, held)) {
				this.viewmodel = heldViewmodel;
				this.start(weapon != null ? WeaponRig.of(weapon).draw() : null, ClipRole.ACTION, now);
			} else if (weapon == Weapon.AWP && this.charged && !charged) {
				WeaponSounds.playAt(WeaponSoundIds.AWP_SHOT, player);
				this.start(WeaponRig.SHOOT, ClipRole.ACTION, now);
			} else if (Awp.isCharging(player, held)) {
				if (!this.clips.followsCharge()) {
					this.start(WeaponRig.RELOAD, ClipRole.CHARGE, now);
				}
			} else if (weapon != null && weapon.isKnife() && this.isAttackSwing(level, player, swing)) {
				boolean heavy = !player.onGround() && player.getY() < this.y;
				boolean alternate = !heavy && (this.attacks++ & 1) == 1;
				this.start(WeaponRig.Strike.MISS.clip(heavy, alternate), ClipRole.STRIKE, now);
			} else if (this.clips.followsCharge()) {
				this.clips.stop();
			}

			this.held = held;
			this.charged = charged;
			this.swing = swing;
			this.y = player.getY();
			this.advanceClip(player, held, now);
		}

		private boolean isAttackSwing(ClientLevel level, AbstractClientPlayer player, LivingEntity.@Nullable SwingDescription swing) {
			return swing != null && swing != this.swing && swing.hand() == InteractionHand.MAIN_HAND && !isBreakingBlock(level, player);
		}

		private void start(@Nullable String name, ClipRole role, double now) {
			Viewmodel viewmodel = this.viewmodel;
			GltfAnimation clip = name != null && viewmodel != null ? viewmodel.clip(name) : null;

			if (clip != null) {
				this.clips.start(clip, role, now);
			} else {
				this.clips.stop();
			}
		}

		private void advanceClip(AbstractClientPlayer player, ItemStack held, double now) {
			if (this.clips.clip() == null) {
				return;
			}

			float time = (float) this.clips.time(now, () -> Awp.chargeProgress(player, held, END_OF_TICK));
			this.clips.playSoundsUntil(time, id -> WeaponSounds.playAt(id, player));

			if (this.clips.ended(time)) {
				this.clips.stop();
			}
		}
	}
}
