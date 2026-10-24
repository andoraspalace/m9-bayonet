package com.bayonet.viewmodel;

import com.bayonet.weapon.Weapon;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

public enum WeaponRig {
	AWP("awp_anims", List.of("draw"), "idle", List.of("lookat01"), null),
	M9_BAYONET("m9_anims", List.of("draw"), "idle1", List.of("lookat01"), new FistGrip(new Vector3f(-0.0018F, 0.0371F, -0.0002F), 0.025F)),
	BUTTERFLY(
		"butterfly_anims", List.of("draw", "draw2"), "idle1", List.of("lookat01", "lookat02", "lookat03"),
		new FistGrip(new Vector3f(0.0095F, 0.0448F, 0.0075F), null)
	);

	public static final String RELOAD = "reload";
	public static final String SHOOT = "shoot1";

	private final String clipsModel;
	private final List<String> draws;
	private final String idle;
	private final List<String> inspects;
	private final @Nullable FistGrip fistGrip;

	WeaponRig(String clipsModel, List<String> draws, String idle, List<String> inspects, @Nullable FistGrip fistGrip) {
		this.clipsModel = clipsModel;
		this.draws = draws;
		this.idle = idle;
		this.inspects = inspects;
		this.fistGrip = fistGrip;
	}

	public static WeaponRig of(Weapon weapon) {
		return switch (weapon) {
			case AWP -> WeaponRig.AWP;
			case M9_BAYONET -> WeaponRig.M9_BAYONET;
			case BUTTERFLY -> WeaponRig.BUTTERFLY;
		};
	}

	public String draw() {
		return pick(this.draws);
	}

	public String inspect() {
		return pick(this.inspects);
	}

	public String idle() {
		return this.idle;
	}

	String clipsModel() {
		return this.clipsModel;
	}

	@Nullable FistGrip fistGrip() {
		return this.fistGrip;
	}

	private static String pick(List<String> clips) {
		return clips.get(ThreadLocalRandom.current().nextInt(clips.size()));
	}

	record FistGrip(Vector3fc shift, @Nullable Float handleEnd) {
	}

	public enum Strike {
		HIT("heavy_hit1", "light_hit1", "light_hit2"),
		BACKSTAB("heavy_backstab", "light_backstab", "light_backstab2"),
		MISS("heavy_miss1", "light_miss1", "light_miss2");

		private final String heavy;
		private final String light;
		private final String lightAlternate;

		Strike(String heavy, String light, String lightAlternate) {
			this.heavy = heavy;
			this.light = light;
			this.lightAlternate = lightAlternate;
		}

		public String clip(boolean heavy, boolean alternate) {
			return heavy ? this.heavy : alternate ? this.lightAlternate : this.light;
		}
	}
}
