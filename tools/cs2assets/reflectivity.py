from typing import NamedTuple

import numpy as np

from .images import downsample, load
from .materials import GLOVE_DETAIL_LAYERS, glove_layers

REFLECTIVITY_SIZE = 256
DIELECTRIC = 0.04
SKIN = 0.028
PAINT = 0.16
PAINT_GLOSS_MIN = 0.55
PAINT_GLOSS_RANGE = 0.45
PAINT_ALPHA_PERCENTILES = (5, 95)
MIN_ALPHA_SPAN = 1e-3
METAL_BASE = 0.06
METAL_TINT = 0.8
MIN_BRIGHTNESS = 1e-3


class AlphaRange(NamedTuple):
    low: float
    span: float


def uniform(f0, roughness):
    return np.array([[[*f0, 1.0 - roughness]]], np.float32)


def dielectric(roughness):
    return uniform((DIELECTRIC,) * 3, roughness)


def custom_paint(paint_texture, alpha_range=None):
    alpha = load(paint_texture, REFLECTIVITY_SIZE)[..., 3]
    low, span = _percentile_range(alpha) if alpha_range is None else alpha_range
    gloss = PAINT_GLOSS_MIN + PAINT_GLOSS_RANGE * np.clip((alpha - low) / span, 0.0, 1.0)
    return np.dstack([np.full(alpha.shape + (3,), PAINT, np.float32), gloss])


def _percentile_range(alpha):
    low, high = (np.percentile(alpha, percentile) for percentile in PAINT_ALPHA_PERCENTILES)
    return AlphaRange(low, max(high - low, MIN_ALPHA_SPAN))


def knife(baked, roughness_texture):
    maps = load(roughness_texture, REFLECTIVITY_SIZE)
    roughness, metalness = maps[..., 0], maps[..., 1]
    color = downsample(baked, REFLECTIVITY_SIZE)
    tint = color / np.maximum(color.max(axis=2, keepdims=True), MIN_BRIGHTNESS) * METAL_TINT
    return np.dstack([METAL_BASE + (tint - METAL_BASE) * metalness[..., None], 1.0 - roughness])


def glove(material):
    params = material.params
    index, layer = glove_layers(material, REFLECTIVITY_SIZE)
    roughness = np.choose(layer, [params[f"g_fDetailRoughnessBrightness{n + 1}"] for n in range(GLOVE_DETAIL_LAYERS)]).astype(np.float32)
    if material.ints.get("F_PATTERN"):
        roughness = np.where(index == material.ints["g_nPatternReplaceIndex"] - 1, params["g_fPatternRoughnessBrightness"], roughness)
    return np.dstack([np.full(index.shape + (3,), DIELECTRIC, np.float32), 1.0 - np.clip(roughness, 0.0, 1.0)])


def skin(material):
    roughness = load(material.texture("g_tNormal"), REFLECTIVITY_SIZE)[..., 3]
    return np.dstack([np.full(roughness.shape + (3,), SKIN, np.float32), 1.0 - roughness])
