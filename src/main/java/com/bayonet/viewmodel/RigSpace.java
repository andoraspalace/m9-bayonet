package com.bayonet.viewmodel;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

final class RigSpace {
	static final Matrix4fc TO_CAMERA = new Matrix4f().rotationY((float) Math.PI);

	private RigSpace() {
	}
}
