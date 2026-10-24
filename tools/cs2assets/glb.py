import json
import struct

import numpy as np

from .paths import ExportError

GLB_MAGIC = 0x46546C67
GLB_VERSION = 2
CHUNK_JSON = 0x4E4F534A
CHUNK_BIN = 0x004E4942
HEADER = struct.Struct("<III")
CHUNK_HEADER = struct.Struct("<II")
ALIGNMENT = 4
BYTE = 5120
UNSIGNED_BYTE = 5121
SHORT = 5122
UNSIGNED_SHORT = 5123
UNSIGNED_INT = 5125
FLOAT = 5126
COMPONENT_TYPES = {BYTE: np.int8, UNSIGNED_BYTE: np.uint8, SHORT: np.int16, UNSIGNED_SHORT: np.uint16, UNSIGNED_INT: np.uint32, FLOAT: np.float32}
COMPONENTS = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}
COMPONENT_NAMES = {count: name for name, count in COMPONENTS.items()}


def read_glb(path):
    data = path.read_bytes()
    magic, version, _ = HEADER.unpack_from(data, 0)
    if magic != GLB_MAGIC or version != GLB_VERSION:
        raise ExportError(f"{path} is not a glTF {GLB_VERSION} binary")
    json_start = HEADER.size + CHUNK_HEADER.size
    json_length, json_type = CHUNK_HEADER.unpack_from(data, HEADER.size)
    if json_type != CHUNK_JSON:
        raise ExportError(f"{path} does not start with a JSON chunk")
    gltf = json.loads(data[json_start:json_start + json_length])
    bin_header = json_start + json_length
    if bin_header >= len(data):
        return gltf, b""
    bin_length, bin_type = CHUNK_HEADER.unpack_from(data, bin_header)
    if bin_type != CHUNK_BIN:
        raise ExportError(f"{path} has no BIN chunk after its JSON chunk")
    bin_start = bin_header + CHUNK_HEADER.size
    return gltf, data[bin_start:bin_start + bin_length]


def write_glb(path, gltf, binary):
    text = json.dumps(gltf, separators=(",", ":")).encode("utf-8")
    text += b" " * (-len(text) % ALIGNMENT)
    binary += b"\0" * (-len(binary) % ALIGNMENT)
    total = HEADER.size + CHUNK_HEADER.size + len(text) + CHUNK_HEADER.size + len(binary)
    path.write_bytes(HEADER.pack(GLB_MAGIC, GLB_VERSION, total)
                     + CHUNK_HEADER.pack(len(text), CHUNK_JSON) + text
                     + CHUNK_HEADER.pack(len(binary), CHUNK_BIN) + binary)


def read_accessor(gltf, binary, index):
    accessor = gltf["accessors"][index]
    dtype = np.dtype(COMPONENT_TYPES[accessor["componentType"]])
    components = COMPONENTS[accessor["type"]]
    view = gltf["bufferViews"][accessor["bufferView"]]
    stride = view.get("byteStride", dtype.itemsize * components)
    start = view.get("byteOffset", 0) + accessor.get("byteOffset", 0)
    rows = np.ndarray((accessor["count"], components), dtype, binary, start, (stride, dtype.itemsize))
    values = rows.astype(np.float32)
    if accessor.get("normalized") and dtype.kind == "u":
        values /= np.iinfo(dtype).max
    return values


class Packer:

    def __init__(self):
        self.views = []
        self.accessors = []
        self.chunks = []
        self.offset = 0

    def add_view(self, data, **extra):
        padding = -self.offset % ALIGNMENT
        self.chunks.append(b"\0" * padding + data)
        self.offset += padding
        self.views.append({"buffer": 0, "byteOffset": self.offset, "byteLength": len(data), **extra})
        self.offset += len(data)
        return len(self.views) - 1

    def add_accessor(self, values):
        values = np.ascontiguousarray(values, np.float32)
        self.accessors.append({
            "bufferView": self.add_view(values.tobytes()), "componentType": FLOAT, "count": len(values),
            "type": COMPONENT_NAMES[values.shape[1]],
        })
        return len(self.accessors) - 1

    def copy(self, gltf, binary, index):
        accessor = dict(gltf["accessors"][index])
        if "bufferView" in accessor:
            view = gltf["bufferViews"][accessor["bufferView"]]
            start = view.get("byteOffset", 0)
            extra = {k: view[k] for k in ("byteStride", "target") if k in view}
            accessor["bufferView"] = self.add_view(binary[start:start + view["byteLength"]], **extra)
        self.accessors.append(accessor)
        return len(self.accessors) - 1

    def finish(self, gltf):
        gltf["accessors"] = self.accessors
        gltf["bufferViews"] = self.views
        gltf["buffers"] = [{"byteLength": self.offset}]
        return b"".join(self.chunks)
