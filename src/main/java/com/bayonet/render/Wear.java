package com.bayonet.render;

import com.bayonet.BayonetClient;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.slf4j.Logger;

public final class Wear {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final String[] MAP_NAMES = {"shared", "awp_legacy"};
	private static final Map<Identifier, Job> PENDING = new HashMap<>();
	private static final Map<Identifier, Identifier> RESOLVED = new HashMap<>();
	private static Map<String, Mask> maps = Map.of();
	private static int generation;

	private Wear() {
	}

	private record Job(Identifier base, CompletableFuture<NativeImage[]> mips) {
	}

	public record Mask(int size, int[] argb) {
		void sample(float u, float v, float[] out) {
			float x = (u - (float) Math.floor(u)) * this.size - 0.5F;
			float y = (v - (float) Math.floor(v)) * this.size - 0.5F;
			int x0 = (int) Math.floor(x);
			int y0 = (int) Math.floor(y);
			float fx = x - x0;
			float fy = y - y0;

			for (int channel = 0; channel < 4; channel++) {
				out[channel] = 0.0F;
			}

			this.add(x0, y0, (1.0F - fx) * (1.0F - fy), out);
			this.add(x0 + 1, y0, fx * (1.0F - fy), out);
			this.add(x0, y0 + 1, (1.0F - fx) * fy, out);
			this.add(x0 + 1, y0 + 1, fx * fy, out);
		}

		private void add(int x, int y, float weight, float[] out) {
			int pixel = this.argb[Math.floorMod(y, this.size) * this.size + Math.floorMod(x, this.size)];
			out[0] += ARGB.redFloat(pixel) * weight;
			out[1] += ARGB.greenFloat(pixel) * weight;
			out[2] += ARGB.blueFloat(pixel) * weight;
			out[3] += ARGB.alphaFloat(pixel) * weight;
		}
	}

	public static Map<String, Mask> load(ResourceManager resources) {
		Map<String, Mask> result = new HashMap<>();

		for (String name : MAP_NAMES) {
			try (InputStream stream = resources.open(BayonetClient.id("wear/" + name + ".png")); NativeImage image = NativeImage.read(stream)) {
				if (image.getWidth() != image.getHeight()) {
					throw new IOException("Wear map " + name + " is " + image.getWidth() + "x" + image.getHeight() + ", not square");
				}

				result.put(name, new Mask(image.getWidth(), image.getPixels()));
			} catch (Exception e) {
				LOGGER.warn("No wear map {}, so weapons show no wear: {}", name, e.toString());
			}
		}

		return result;
	}

	public static void setMaps(Map<String, Mask> loaded) {
		maps = loaded;
	}

	public static boolean busy() {
		return !PENDING.isEmpty();
	}

	public static int generation() {
		return generation;
	}

	public static void collect() {
		Iterator<Map.Entry<Identifier, Job>> jobs = PENDING.entrySet().iterator();

		while (jobs.hasNext()) {
			Map.Entry<Identifier, Job> entry = jobs.next();

			if (entry.getValue().mips().isDone()) {
				jobs.remove();
				finish(entry.getKey(), entry.getValue());
			}
		}
	}

	static void reloaded() {
		PENDING.values().forEach(job -> job.mips().thenAccept(Wear::close));
		PENDING.clear();
		RESOLVED.clear();
		generation++;
	}

	static Identifier texture(Identifier base, byte[] png, String mapName, float wear) {
		Identifier id = base.withPath(path -> path + "/wear_" + Math.round(wear * 1000.0F));
		Identifier resolved = RESOLVED.get(id);

		if (resolved != null) {
			return resolved;
		}

		Job job = PENDING.get(id);

		if (job == null) {
			Mask substrate = maps.get(mapName);
			Mask shared = maps.get("shared");

			if (substrate != null && shared != null) {
				CompletableFuture<NativeImage[]> mips = CompletableFuture.supplyAsync(
					() -> GltfTextures.mipmaps(id, composite(png, substrate, shared, wear)), Util.backgroundExecutor()
				);
				PENDING.put(id, new Job(base, mips));
			}

			return base;
		}

		if (!job.mips().isDone()) {
			return base;
		}

		PENDING.remove(id);
		return finish(id, job);
	}

	private static Identifier finish(Identifier id, Job job) {
		generation++;

		try {
			GltfTextures.register(id, job.mips().join());
			RESOLVED.put(id, id);
			return id;
		} catch (Exception e) {
			LOGGER.error("Failed to paint the wear of {}", job.base(), e);
			RESOLVED.put(id, job.base());
			return job.base();
		}
	}

	private static NativeImage composite(byte[] png, Mask substrate, Mask shared, float wear) {
		try (NativeImage paint = NativeImage.read(png)) {
			int width = paint.getWidth();
			int height = paint.getHeight();
			NativeImage out = new NativeImage(width, height, false);
			float[] metal = new float[4];
			float[] masks = new float[4];
			float grime = wear * 0.6F;

			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					float u = (x + 0.5F) / width;
					float v = (y + 0.5F) / height;
					substrate.sample(u, v, metal);
					shared.sample(u, v, masks);
					float resistance = (masks[0] - 0.3F) / 0.7F * (1.0F - 0.8F * metal[3]);
					float painted = smoothstep(wear * 0.45F, wear * 0.55F, resistance);
					int pixel = paint.getPixel(x, y);
					out.setPixel(x, y, ARGB.colorFromFloat(
						1.0F,
						Mth.clamp((metal[0] + (ARGB.redFloat(pixel) - metal[0]) * painted) * (1.0F + (masks[1] - 1.0F) * grime), 0.0F, 1.0F),
						Mth.clamp((metal[1] + (ARGB.greenFloat(pixel) - metal[1]) * painted) * (1.0F + (masks[2] - 1.0F) * grime), 0.0F, 1.0F),
						Mth.clamp((metal[2] + (ARGB.blueFloat(pixel) - metal[2]) * painted) * (1.0F + (masks[3] - 1.0F) * grime), 0.0F, 1.0F)
					));
				}
			}

			return out;
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private static float smoothstep(float edge0, float edge1, float x) {
		float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}

	private static void close(NativeImage[] mips) {
		for (NativeImage mip : mips) {
			mip.close();
		}
	}
}
