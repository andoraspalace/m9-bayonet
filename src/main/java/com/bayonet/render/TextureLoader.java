package com.bayonet.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Transparency;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MipmapGenerator;
import net.minecraft.client.renderer.texture.MipmapStrategy;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

final class TextureLoader {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int MAX_ANISOTROPY = 8;
	private static final float NO_ALPHA_CUTOFF_BIAS = 0.0F;
	private static final Map<Identifier, CompletableFuture<NativeImage[]>> PENDING = new HashMap<>();
	private static final Set<Identifier> UPLOADED = new HashSet<>();
	private static final Set<Identifier> FAILED = new HashSet<>();
	private static @Nullable GpuSampler sampler;
	private static int generation;

	private TextureLoader() {
	}

	static int generation() {
		return generation;
	}

	static @Nullable Identifier request(Identifier id, Supplier<NativeImage> image) {
		if (UPLOADED.contains(id)) {
			return id;
		}

		if (FAILED.contains(id)) {
			return null;
		}

		CompletableFuture<NativeImage[]> job = PENDING.get(id);

		if (job == null) {
			PENDING.put(id, CompletableFuture.supplyAsync(() -> mipmaps(id, image.get()), Util.backgroundExecutor()));
			return null;
		}

		if (!job.isDone()) {
			return null;
		}

		PENDING.remove(id);
		return upload(id, job) ? id : null;
	}

	static void collect() {
		Iterator<Map.Entry<Identifier, CompletableFuture<NativeImage[]>>> jobs = PENDING.entrySet().iterator();

		while (jobs.hasNext()) {
			Map.Entry<Identifier, CompletableFuture<NativeImage[]>> job = jobs.next();

			if (job.getValue().isDone()) {
				jobs.remove();
				upload(job.getKey(), job.getValue());
			}
		}
	}

	static void reloaded() {
		releaseAll();
		generation++;
	}

	static void shutdown() {
		releaseAll();

		if (sampler != null) {
			sampler.close();
			sampler = null;
		}
	}

	private static void releaseAll() {
		PENDING.values().forEach(job -> job.thenAccept(TextureLoader::close));
		PENDING.clear();
		TextureManager textures = Minecraft.getInstance().getTextureManager();
		UPLOADED.forEach(textures::release);
		UPLOADED.clear();
		FAILED.clear();
	}

	private static boolean upload(Identifier id, CompletableFuture<NativeImage[]> job) {
		generation++;

		try {
			Minecraft.getInstance().getTextureManager().register(id, new MipmappedTexture(id, job.join()));
			UPLOADED.add(id);
			return true;
		} catch (RuntimeException e) {
			LOGGER.error("Failed to load texture {}", id, e);
			FAILED.add(id);
			return false;
		}
	}

	private static NativeImage[] mipmaps(Identifier id, NativeImage image) {
		int levels = 0;

		while (Math.min(image.getWidth(), image.getHeight()) >> levels > 1) {
			levels++;
		}

		return MipmapGenerator.generateMipLevels(id, new NativeImage[]{image}, levels, MipmapStrategy.MEAN, NO_ALPHA_CUTOFF_BIAS, Transparency.NONE);
	}

	private static void close(NativeImage[] mips) {
		for (NativeImage mip : mips) {
			mip.close();
		}
	}

	private static GpuSampler sampler() {
		if (sampler == null) {
			GpuDevice device = RenderSystem.getDevice();
			int anisotropy = Math.min(MAX_ANISOTROPY, device.getDeviceInfo().limits().maxAnisotropy());
			sampler = device.createSampler(AddressMode.REPEAT, AddressMode.REPEAT, FilterMode.LINEAR, FilterMode.LINEAR, Math.max(1, anisotropy), OptionalDouble.empty());
		}

		return sampler;
	}

	private static final class MipmappedTexture extends AbstractTexture {
		MipmappedTexture(Identifier id, NativeImage[] mips) {
			try {
				GpuDevice device = RenderSystem.getDevice();
				this.texture = device.createTexture(
					id::toString, GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, GpuFormat.RGBA8_UNORM, mips[0].getWidth(), mips[0].getHeight(), 1, mips.length
				);
				this.textureView = device.createTextureView(this.texture);
				this.sampler = sampler();

				for (int level = 0; level < mips.length; level++) {
					device.createCommandEncoder().writeToTexture(this.texture, mips[level], level, 0, 0, 0);
				}
			} finally {
				TextureLoader.close(mips);
			}
		}
	}
}
