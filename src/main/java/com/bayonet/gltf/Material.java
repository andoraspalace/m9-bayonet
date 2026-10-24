package com.bayonet.gltf;

import org.jspecify.annotations.Nullable;

public record Material(int image, int color, @Nullable String wearMaps) {
	static final int NO_IMAGE = -1;
	static final int WHITE = 0xFFFFFFFF;
	static final Material DEFAULT = new Material(NO_IMAGE, WHITE, null);
}
