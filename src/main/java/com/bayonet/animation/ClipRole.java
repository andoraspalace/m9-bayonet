package com.bayonet.animation;

enum ClipRole {
	IDLE,
	ACTION,
	STRIKE,
	CHARGE;

	boolean loops() {
		return switch (this) {
			case IDLE -> true;
			case ACTION, STRIKE, CHARGE -> false;
		};
	}

	boolean turnsWeapon() {
		return switch (this) {
			case ACTION, CHARGE -> true;
			case IDLE, STRIKE -> false;
		};
	}

	boolean followsCharge() {
		return switch (this) {
			case CHARGE -> true;
			case IDLE, ACTION, STRIKE -> false;
		};
	}
}
