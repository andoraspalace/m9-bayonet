import re
from collections.abc import Mapping
from dataclasses import dataclass
from pathlib import Path

import numpy as np
from PIL import Image

from .dumps import read_dump
from .images import load
from .paths import ExportError, find

FLOAT_PARAM = re.compile(r'm_name = "(\w+)"\s+m_flValue = ([-\d.e]+)')
VECTOR_PARAM = re.compile(r'm_name = "(\w+)"\s+m_value = \[([^\]]*)\]')
INT_PARAM = re.compile(r'm_name = "(\w+)"\s+m_nValue = (-?\d+)')
TEXTURE_PARAM = re.compile(r'm_name = "(\w+)"\s+m_pValue = resource:"([^"]+)\.vtex"')
TEXTURE_SUFFIX = ".png"

GLOVE_TINTS = 8
GLOVE_DETAIL_LAYERS = 4
LAYER_MASK_THRESHOLD = 0.5


@dataclass(frozen=True)
class Material:
    dump: Path
    params: Mapping[str, float | np.ndarray]
    ints: Mapping[str, int]
    textures: Mapping[str, str]

    @classmethod
    def read(cls, dump):
        return cls.parse(read_dump(dump), dump)

    @classmethod
    def parse(cls, text, dump):
        params = {name: float(value) for name, value in FLOAT_PARAM.findall(text)}
        params.update({name: np.array([float(v) for v in value.split(",")], dtype=np.float32) for name, value in VECTOR_PARAM.findall(text)})
        ints = {name: int(value) for name, value in INT_PARAM.findall(text)}
        return cls(dump, params, ints, dict(TEXTURE_PARAM.findall(text)))

    def texture(self, name):
        vtex = self.textures.get(name)
        if vtex is None:
            raise ExportError(f"{self.dump} has no texture {name}")
        return find(vtex + TEXTURE_SUFFIX)

    def pattern(self):
        return self.texture("g_tPattern")


def glove_layers(material, size):
    mask = load(material.texture("g_tLayerMask"), size, Image.Resampling.NEAREST)
    index = np.clip((mask[..., 3] * GLOVE_TINTS).astype(np.int32), 0, GLOVE_TINTS - 1)
    layer = np.where(mask[..., 0] > LAYER_MASK_THRESHOLD, 1,
                     np.where(mask[..., 1] > LAYER_MASK_THRESHOLD, 2, np.where(mask[..., 2] > LAYER_MASK_THRESHOLD, 3, 0)))
    return index, layer
