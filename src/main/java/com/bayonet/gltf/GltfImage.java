package com.bayonet.gltf;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class GltfImage {
	private static final String KEY_DIGEST = "SHA-256";

	private final byte[] data;
	private final String key;

	private GltfImage(byte[] data, String key) {
		this.data = data;
		this.key = key;
	}

	static GltfImage of(byte[] data) {
		try {
			return new GltfImage(data, HexFormat.of().formatHex(MessageDigest.getInstance(KEY_DIGEST).digest(data)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(KEY_DIGEST + " is not available", e);
		}
	}

	public byte[] data() {
		return this.data;
	}

	public String key() {
		return this.key;
	}
}
