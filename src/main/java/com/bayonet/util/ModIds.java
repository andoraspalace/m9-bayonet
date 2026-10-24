package com.bayonet.util;

import net.minecraft.resources.Identifier;

public final class ModIds {
	public static final String NAMESPACE = "bayonet";

	private ModIds() {
	}

	public static Identifier of(String path) {
		return Identifier.fromNamespaceAndPath(NAMESPACE, path);
	}
}
