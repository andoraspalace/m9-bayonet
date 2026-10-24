from io import BytesIO

import numpy as np
from PIL import Image

NATIVE_SIZE = None
OCCLUSION_FLOOR = 0.35
OCCLUSION_RANGE = 0.65


def load(path, size=NATIVE_SIZE, resample=Image.Resampling.LANCZOS):
    with Image.open(path) as source:
        image = source.convert("RGBA")
    if size is not NATIVE_SIZE and image.size != (size, size):
        image = Image.merge("RGBA", [band.resize((size, size), resample) for band in image.split()])
    return np.asarray(image, dtype=np.float32) / 255.0


def _to_uint8(rgb):
    return (np.clip(rgb, 0.0, 1.0) * 255.0 + 0.5).astype(np.uint8)


def to_png(rgb):
    out = BytesIO()
    Image.fromarray(_to_uint8(rgb), "RGB").save(out, "PNG", optimize=True)
    return out.getvalue()


def save_rgba(path, rgb, alpha):
    Image.fromarray(_to_uint8(np.dstack([rgb, alpha])), "RGBA").save(path, "PNG", optimize=True)
    print(f"{path}: {path.stat().st_size // 1024} KB")


def downsample(rgb, size):
    return np.asarray(Image.fromarray(_to_uint8(rgb), "RGB").resize((size, size), Image.Resampling.BOX), np.float32) / 255.0


def smoothstep(edge0, edge1, x):
    t = np.clip((x - edge0) / (edge1 - edge0), 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


def occlude(rgb, ao, floor=OCCLUSION_FLOOR, span=OCCLUSION_RANGE):
    return rgb * (floor + span * ao[..., None])


def blend(colors, weights):
    out = np.broadcast_to(colors[0], weights.shape[:2] + (3,))
    for layer in range(1, len(colors)):
        out = out + (colors[layer] - out) * weights[..., layer - 1:layer]
    return out


def tiled(texture, size, scale, turn=0.0, offset=(0.0, 0.0)):
    v, u = np.mgrid[0:size, 0:size].astype(np.float32)
    u, v = (u + 0.5) / size * scale, (v + 0.5) / size * scale
    u, v = u * np.cos(turn) - v * np.sin(turn) + offset[0], u * np.sin(turn) + v * np.cos(turn) + offset[1]
    height, width = texture.shape[:2]
    return texture[(v % 1.0 * height).astype(np.int32), (u % 1.0 * width).astype(np.int32)]


def sample_per_vertex(image, uvs):
    height, width = image.shape[:2]
    x = (uvs[:, 0] % 1.0) * width - 0.5
    y = (uvs[:, 1] % 1.0) * height - 0.5
    x0 = np.floor(x).astype(np.int64)
    y0 = np.floor(y).astype(np.int64)
    fx = (x - x0)[:, None]
    fy = (y - y0)[:, None]

    def at(row, column):
        return image[row % height, column % width]

    top = at(y0, x0) * (1 - fx) + at(y0, x0 + 1) * fx
    bottom = at(y0 + 1, x0) * (1 - fx) + at(y0 + 1, x0 + 1) * fx
    return (top * (1 - fy) + bottom * fy).astype(np.float32)
