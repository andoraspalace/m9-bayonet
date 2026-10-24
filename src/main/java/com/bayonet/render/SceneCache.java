package com.bayonet.render;

import com.bayonet.gltf.GlbLoader;
import com.bayonet.gltf.GltfScene;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jspecify.annotations.Nullable;

public final class SceneCache {
	private static volatile @Nullable Map<Identifier, CompletableFuture<GltfScene>> active;

	private SceneCache() {
	}

	public static GltfScene load(ResourceManager resources, Identifier model) throws IOException {
		Identifier file = model.withPath(path -> "models/gltf/" + path + ".glb");
		Map<Identifier, CompletableFuture<GltfScene>> scenes = active;

		if (scenes == null) {
			return read(resources, file);
		}

		CompletableFuture<GltfScene> created = new CompletableFuture<>();
		CompletableFuture<GltfScene> existing = scenes.putIfAbsent(file, created);

		if (existing != null) {
			return await(existing);
		}

		try {
			GltfScene scene = read(resources, file);
			created.complete(scene);
			return scene;
		} catch (Throwable e) {
			created.completeExceptionally(e);
			throw e;
		}
	}

	static void begin() {
		active = new ConcurrentHashMap<>();
	}

	static void end() {
		active = null;
	}

	private static GltfScene read(ResourceManager resources, Identifier file) throws IOException {
		try (InputStream stream = resources.open(file)) {
			return GlbLoader.loadScene(stream.readAllBytes());
		}
	}

	private static GltfScene await(CompletableFuture<GltfScene> scene) throws IOException {
		try {
			return scene.join();
		} catch (CompletionException e) {
			if (e.getCause() instanceof IOException cause) {
				throw new IOException(cause.getMessage(), cause);
			}

			throw e;
		}
	}
}
