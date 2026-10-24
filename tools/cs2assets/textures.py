from typing import NamedTuple

import numpy as np

from .images import blend, load, occlude, save_rgba, smoothstep, tiled
from .materials import GLOVE_DETAIL_LAYERS, GLOVE_TINTS, glove_layers
from .paths import WEAR

TEXTURE_SIZE = 2048
WEAR_SIZE = 1024

LUMINANCE = np.array([0.2126, 0.7152, 0.0722], dtype=np.float32)
ANODIZED_COLORS = 4
ANODIZED_BASE = 0.45
ANODIZED_METAL = 1.1
ANODIZED_OCCLUSION_FLOOR = 0.4
ANODIZED_OCCLUSION_RANGE = 0.6
BLADE_MASK_EDGES = (0.85, 0.95)
GLOVE_DETAIL_CONTRAST = 0.8
NO_PATTERN_OFFSET = np.zeros(2, np.float32)


class _PatternKeys(NamedTuple):
    scale: str
    rotation: str
    offset: str


ANODIZED_PATTERN = _PatternKeys("g_flPatternTexCoordScale", "g_flPatternTexCoordRotation", "g_vPatternTexCoordOffset")
GLOVE_PATTERN = _PatternKeys("g_fPatternTexCoordScale", "g_fPatternTexCoordRotation", "g_fPatternTexCoordOffset")


def _tiled_pattern(material, keys):
    params = material.params
    return tiled(load(material.pattern()), TEXTURE_SIZE, params[keys.scale], np.radians(params.get(keys.rotation, 0.0)),
                 params.get(keys.offset, NO_PATTERN_OFFSET)[:2])


def bake_custom_paint(paint_texture, occlusion_texture):
    return occlude(load(paint_texture, TEXTURE_SIZE)[..., :3], load(occlusion_texture, TEXTURE_SIZE)[..., 0])


def bake_anodized(paint_kit, color_texture, masks_texture, occlusion_texture):
    base = load(color_texture, TEXTURE_SIZE)[..., :3]
    masks = load(masks_texture, TEXTURE_SIZE)
    occlusion = load(occlusion_texture, TEXTURE_SIZE)[..., 0]
    pattern = _tiled_pattern(paint_kit, ANODIZED_PATTERN)
    paint_rgb = blend([paint_kit.params[f"g_vColor{layer}"][:3] for layer in range(ANODIZED_COLORS)], pattern)
    metal = base @ LUMINANCE
    anodized = occlude(paint_rgb * (ANODIZED_BASE + ANODIZED_METAL * metal[..., None]), occlusion, ANODIZED_OCCLUSION_FLOOR, ANODIZED_OCCLUSION_RANGE)
    blade = smoothstep(*BLADE_MASK_EDGES, masks[..., 0])[..., None]
    return base + (anodized - base) * blade


def bake_with_ao(color_texture, occlusion_texture, size=TEXTURE_SIZE):
    rgb = load(color_texture, size)[..., :3]
    return occlude(rgb, load(occlusion_texture, rgb.shape[0])[..., 0])


def bake_color(color_texture):
    return load(color_texture, TEXTURE_SIZE)[..., :3]


def bake_glove(material):
    params = material.params
    index, layer = glove_layers(material, TEXTURE_SIZE)
    tints = np.array([params[f"g_vColorTint{tint}"][:3] for tint in range(1, GLOVE_TINTS + 1)], np.float32)
    color = tints[index]

    if material.ints.get("F_PATTERN"):
        palette = tints[params["g_vPatternPaletteIndices"].astype(np.int32) - 1]
        replaced = (index == material.ints["g_nPatternReplaceIndex"] - 1)[..., None]
        color = np.where(replaced, blend(palette, _tiled_pattern(material, GLOVE_PATTERN)), color)

    detail = np.ones((TEXTURE_SIZE, TEXTURE_SIZE), np.float32)
    for n in range(GLOVE_DETAIL_LAYERS):
        values = tiled(load(material.texture(f"g_tDetail{n + 1}")), TEXTURE_SIZE, params[f"g_fDetailScale{n + 1}"])[..., 0]
        detail = np.where(layer == n, 1.0 + GLOVE_DETAIL_CONTRAST * (values - values.mean()), detail)

    cavity = load(material.texture("g_tSurface"), TEXTURE_SIZE)[..., 1]
    return occlude(np.clip(color * detail[..., None], 0.0, 1.0), cavity)


def build_legacy_wear_map(name, color_texture, occlusion_texture):
    legacy = load(color_texture, WEAR_SIZE)
    _save_wear_map(name, occlude(legacy[..., :3], load(occlusion_texture, WEAR_SIZE)[..., 0]), legacy[..., 3])


def build_shared_wear_map(name, wear_texture, grunge_texture):
    wear = load(wear_texture, WEAR_SIZE)[..., 0]
    grunge = load(grunge_texture, WEAR_SIZE)[..., :3]
    _save_wear_map(name, np.dstack([wear, grunge[..., 0], grunge[..., 1]]), grunge[..., 2])


def _save_wear_map(name, rgb, alpha):
    WEAR.mkdir(parents=True, exist_ok=True)
    save_rgba(WEAR / f"{name}.png", rgb, alpha)
