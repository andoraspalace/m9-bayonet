package com.bayonet.sound;

import com.bayonet.util.ClientTicks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class LocalActivity {
	private static final Actions ATTACKS = new Actions();
	private static final Actions CROSSBOW_USES = new Actions();

	private LocalActivity() {
	}

	public static void markAttack(Vec3 position, boolean withKnife) {
		ATTACKS.mark(position, withKnife);
	}

	public static void markCrossbowUse(Vec3 position, boolean withAwp) {
		CROSSBOW_USES.mark(position, withAwp);
	}

	static Actions attacks() {
		return ATTACKS;
	}

	static Actions crossbowUses() {
		return CROSSBOW_USES;
	}

	static final class Actions {
		private static final long NEVER = Long.MIN_VALUE / 2;
		private static final int REMEMBERED_MARKS = 64;

		private final @Nullable Mark[] marks = new Mark[REMEMBERED_MARKS];
		private int next;
		private long last = NEVER;
		private long lastWithWeapon = NEVER;

		private void mark(Vec3 position, boolean withWeapon) {
			this.last = ClientTicks.ticks();

			if (withWeapon) {
				this.lastWithWeapon = this.last;
			}

			this.marks[this.next] = new Mark(this.last, position, withWeapon);
			this.next = (this.next + 1) % REMEMBERED_MARKS;
		}

		long ticksSinceAny() {
			return ClientTicks.ticks() - this.last;
		}

		long ticksSinceWeapon() {
			return ClientTicks.ticks() - this.lastWithWeapon;
		}

		double distanceTo(Vec3 point, double anyTicks, double weaponTicks) {
			long now = ClientTicks.ticks();
			double nearest = Double.POSITIVE_INFINITY;

			for (Mark mark : this.marks) {
				if (mark != null && now - mark.tick() <= (mark.withWeapon() ? weaponTicks : anyTicks)) {
					nearest = Math.min(nearest, mark.position().distanceTo(point));
				}
			}

			return nearest;
		}

		private record Mark(long tick, Vec3 position, boolean withWeapon) {
		}
	}
}
