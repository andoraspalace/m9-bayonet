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
import java.util.HashSet;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MipmapGenerator;
import net.minecraft.client.renderer.texture.MipmapStrategy;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class GltfTextures {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int MAX_ANISOTROPY = 8;
	private static @Nullable GpuSampler sampler;
	private static final Set<Identifier> REGISTERED = new HashSet<>();

	private final List<byte[]> images;
	private final Identifier[] ids;

	public GltfTextures(Identifier prefix, List<byte[]> images) {
		this.images = images;
		this.ids = new Identifier[images.size() + 1];

		for (int i = 0; i < this.ids.length; i++) {
			int index = i;
			this.ids[i] = prefix.withPath(path -> path + "/" + index);
		}
	}

	public Identifier get(int image) {
		int index = image >= 0 && image < this.images.size() ? image : this.ids.length - 1;

		if (REGISTERED.add(this.ids[index])) {
			register(this.ids[index], this.decode(index));
		}

		return this.ids[index];
	}

	public Identifier get(int image, @Nullable String wearMaps, float wear) {
		Identifier base = this.get(image);

		if (wearMaps == null || wear <= 0.0F || image < 0 || image >= this.images.size()) {
			return base;
		}

		return Wear.texture(base, this.images.get(image), wearMaps, wear);
	}

	public static RenderType renderType(Identifier texture, boolean cutout) {
		return cutout ? RenderTypes.entityCutout(texture) : RenderTypes.entitySolid(texture);
	}

	public static void reloaded() {
		REGISTERED.clear();
		Wear.reloaded();
	}

	public static void register(Identifier id, NativeImage image) {
		register(id, mipmaps(id, image));
	}

	static void register(Identifier id, NativeImage[] mips) {
		Minecraft.getInstance().getTextureManager().register(id, new GltfTexture(id, mips));
	}

	static NativeImage[] mipmaps(Identifier id, NativeImage image) {
		int levels = 0;

		while (Math.min(image.getWidth(), image.getHeight()) >> levels > 1) {
			levels++;
		}

		return MipmapGenerator.generateMipLevels(id, new NativeImage[]{image}, levels, MipmapStrategy.MEAN, 0.0F, Transparency.NONE);
	}

	private NativeImage decode(int index) {
		if (index < this.images.size()) {
			try {
				return NativeImage.read(this.images.get(index));
			} catch (Exception e) {
				LOGGER.error("Failed to decode glTF image {} (only PNG is supported)", this.ids[index], e);
			}
		}

		NativeImage white = new NativeImage(1, 1, false);
		white.setPixel(0, 0, -1);
		return white;
	}

	private static GpuSampler sampler() {
		if (sampler == null) {
			GpuDevice device = RenderSystem.getDevice();
			int anisotropy = Math.min(MAX_ANISOTROPY, device.getDeviceInfo().limits().maxAnisotropy());
			sampler = device.createSampler(AddressMode.REPEAT, AddressMode.REPEAT, FilterMode.LINEAR, FilterMode.LINEAR, Math.max(1, anisotropy), OptionalDouble.empty());
		}

		return sampler;
	}

	private static class GltfTexture extends AbstractTexture {
		GltfTexture(Identifier id, NativeImage[] mips) {
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
				for (NativeImage mip : mips) {
					mip.close();
				}
			}
		}
	}
}
