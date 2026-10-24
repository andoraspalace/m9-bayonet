package com.bayonet.render;

import com.bayonet.gltf.GltfImage;
import com.bayonet.gltf.Material;
import com.bayonet.util.ModIds;
import com.bayonet.weapon.Skin;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.util.List;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.CommonColors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class GltfTextures {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final String TEXTURE_PREFIX = "gltf/";
	private static final Identifier MISSING = ModIds.of(TEXTURE_PREFIX + "missing");

	private final Identifier model;
	private final List<GltfImage> images;
	private final Identifier[] ids;

	public GltfTextures(Identifier model, List<GltfImage> images) {
		this.model = model;
		this.images = images;
		this.ids = new Identifier[images.size()];

		for (int i = 0; i < this.ids.length; i++) {
			this.ids[i] = ModIds.of(TEXTURE_PREFIX + images.get(i).key());
		}
	}

	public @Nullable RenderType renderType(Material material, float wear, boolean glint) {
		Identifier texture = this.texture(material, wear);

		if (texture == null) {
			return null;
		}

		return glint ? RenderTypes.entitySolidGlint(texture) : RenderTypes.entitySolid(texture);
	}

	public @Nullable RenderType glowing(Material material) {
		Identifier texture = this.imageTexture(material.image());
		return texture != null ? RenderTypes.eyes(texture) : null;
	}

	private @Nullable Identifier texture(Material material, float wear) {
		int image = material.image();
		String wearMaps = material.wearMaps();
		Identifier base = this.imageTexture(image);

		if (wearMaps == null || wear <= Skin.UNWORN || !this.has(image)) {
			return base;
		}

		Identifier worn = Wear.texture(this.ids[image], this.images.get(image).data(), wearMaps, wear);
		return worn != null ? worn : base;
	}

	private @Nullable Identifier imageTexture(int image) {
		if (!this.has(image)) {
			return TextureLoader.request(MISSING, GltfTextures::white);
		}

		return TextureLoader.request(this.ids[image], () -> this.decode(image));
	}

	private boolean has(int image) {
		return image >= 0 && image < this.images.size();
	}

	private NativeImage decode(int image) {
		try {
			return NativeImage.read(this.images.get(image).data());
		} catch (IOException | IllegalArgumentException e) {
			LOGGER.error("Failed to decode image {} of glTF model {} (only PNG is supported)", image, this.model, e);
			return white();
		}
	}

	private static NativeImage white() {
		NativeImage white = new NativeImage(1, 1, false);
		white.setPixel(0, 0, CommonColors.WHITE);
		return white;
	}
}
