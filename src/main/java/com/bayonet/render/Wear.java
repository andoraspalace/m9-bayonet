package com.bayonet.render;

import com.bayonet.util.ModIds;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

final class Wear {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final String SHARED_MAP = "shared";
	private static final List<String> MAP_NAMES = List.of(SHARED_MAP, "awp_legacy");
	private static final float WEAR_ID_PRECISION = 1000.0F;
	private static final float PIXEL_CENTER = 0.5F;
	private static final int RED = 0;
	private static final int GREEN = 1;
	private static final int BLUE = 2;
	private static final int ALPHA = 3;
	private static final int CHANNELS = 4;
	private static final int PAINT_RESISTANCE = RED;
	private static final int GRIME_TINT = GREEN;
	private static final int EDGE_EXPOSURE = ALPHA;
	private static final float RESISTANCE_FLOOR = 0.3F;
	private static final float RESISTANCE_SPAN = 0.7F;
	private static final float EXPOSURE_WEAKENING = 0.8F;
	private static final float PAINT_EDGE_START = 0.45F;
	private static final float PAINT_EDGE_END = 0.55F;
	private static final float GRIME_PER_WEAR = 0.6F;
	private static Map<String, Mask> maps = Map.of();

	private Wear() {
	}

	record Mask(int size, int[] argb) {
		void sample(float u, float v, float[] out) {
			float x = (u - (float) Math.floor(u)) * this.size - PIXEL_CENTER;
			float y = (v - (float) Math.floor(v)) * this.size - PIXEL_CENTER;
			int x0 = (int) Math.floor(x);
			int y0 = (int) Math.floor(y);
			float fx = x - x0;
			float fy = y - y0;

			for (int channel = 0; channel < CHANNELS; channel++) {
				out[channel] = 0.0F;
			}

			this.add(x0, y0, (1.0F - fx) * (1.0F - fy), out);
			this.add(x0 + 1, y0, fx * (1.0F - fy), out);
			this.add(x0, y0 + 1, (1.0F - fx) * fy, out);
			this.add(x0 + 1, y0 + 1, fx * fy, out);
		}

		private void add(int x, int y, float weight, float[] out) {
			int pixel = this.argb[Math.floorMod(y, this.size) * this.size + Math.floorMod(x, this.size)];
			out[RED] += ARGB.redFloat(pixel) * weight;
			out[GREEN] += ARGB.greenFloat(pixel) * weight;
			out[BLUE] += ARGB.blueFloat(pixel) * weight;
			out[ALPHA] += ARGB.alphaFloat(pixel) * weight;
		}
	}

	static Map<String, Mask> load(ResourceManager resources) {
		Map<String, Mask> result = new HashMap<>();

		for (String name : MAP_NAMES) {
			try (InputStream stream = resources.open(ModIds.of("wear/" + name + ".png")); NativeImage image = NativeImage.read(stream)) {
				if (image.getWidth() != image.getHeight()) {
					throw new IOException("Wear map " + name + " is " + image.getWidth() + "x" + image.getHeight() + ", not square");
				}

				result.put(name, new Mask(image.getWidth(), image.getPixels()));
			} catch (FileNotFoundException e) {
				LOGGER.warn("No wear map {}, so weapons show no wear", name);
			} catch (IOException | RuntimeException e) {
				LOGGER.error("Failed to load wear map {}, so weapons show no wear", name, e);
			}
		}

		return Map.copyOf(result);
	}

	static void setMaps(Map<String, Mask> loaded) {
		maps = loaded;
	}

	static @Nullable Identifier texture(Identifier base, byte[] png, String mapName, float wear) {
		Mask substrate = maps.get(mapName);
		Mask shared = maps.get(SHARED_MAP);

		if (substrate == null || shared == null) {
			return null;
		}

		Identifier id = base.withPath(path -> path + "/" + mapName + "_wear_" + Math.round(wear * WEAR_ID_PRECISION));
		return TextureLoader.request(id, () -> composite(png, substrate, shared, wear));
	}

	private static NativeImage composite(byte[] png, Mask substrate, Mask shared, float wear) {
		try (NativeImage paint = NativeImage.read(png)) {
			int width = paint.getWidth();
			int height = paint.getHeight();
			NativeImage out = new NativeImage(width, height, false);
			float[] metal = new float[CHANNELS];
			float[] masks = new float[CHANNELS];
			float grime = wear * GRIME_PER_WEAR;

			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					float u = (x + PIXEL_CENTER) / width;
					float v = (y + PIXEL_CENTER) / height;
					substrate.sample(u, v, metal);
					shared.sample(u, v, masks);
					float resistance = (masks[PAINT_RESISTANCE] - RESISTANCE_FLOOR) / RESISTANCE_SPAN * (1.0F - EXPOSURE_WEAKENING * metal[EDGE_EXPOSURE]);
					float painted = paintCoverage(resistance, wear);
					int pixel = paint.getPixel(x, y);
					out.setPixel(x, y, ARGB.colorFromFloat(
						1.0F,
						shade(metal[RED], ARGB.redFloat(pixel), painted, masks[GRIME_TINT + RED], grime),
						shade(metal[GREEN], ARGB.greenFloat(pixel), painted, masks[GRIME_TINT + GREEN], grime),
						shade(metal[BLUE], ARGB.blueFloat(pixel), painted, masks[GRIME_TINT + BLUE], grime)
					));
				}
			}

			return out;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static float paintCoverage(float resistance, float wear) {
		return smoothstep(wear * PAINT_EDGE_START, wear * PAINT_EDGE_END, resistance);
	}

	private static float shade(float metal, float paint, float painted, float grimeTint, float grime) {
		return Mth.clamp((metal + (paint - metal) * painted) * (1.0F + (grimeTint - 1.0F) * grime), 0.0F, 1.0F);
	}

	private static float smoothstep(float edge0, float edge1, float x) {
		float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}
}
