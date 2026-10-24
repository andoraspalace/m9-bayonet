import re

import numpy as np

from .dumps import read_dump, search_dump
from .glb import Packer, read_glb
from .paths import ExportError

SOURCE_TO_GLTF = np.array([[0, 1, 0], [0, 0, 1], [1, 0, 0]], np.float32)
INCHES_TO_METERS = 0.0254


def _source_skeleton(data_dump):
    text = read_dump(data_dump)

    def block(name):
        return search_dump(name + r" = \s*\[(.*?)\n\t\t\]", text, data_dump, re.S).group(1)

    def vectors(name):
        return [np.array([float(v) for v in row.split(",")], np.float64) for row in re.findall(r"\[\s*([^\[\]]+?)\s*\]", block(name))]

    names = re.findall(r'"([^"]+)"', block("m_boneName"))
    parents = [int(p) for p in re.findall(r"-?\d+", block("m_nParent"))]
    positions = vectors("m_bonePosParent")
    rotations = vectors("m_boneRotParent")
    if not len(names) == len(parents) == len(positions) == len(rotations):
        raise SystemExit(f"{data_dump} has {len(names)} bone names, {len(parents)} parents, {len(positions)} positions and {len(rotations)} rotations")
    world = []
    for bone, (position, rotation) in enumerate(zip(positions, rotations)):
        local = np.eye(4)
        local[:3, :3] = _quaternion_matrix(rotation)
        local[:3, 3] = position
        world.append(local if parents[bone] < 0 else world[parents[bone]] @ local)
    return names, parents, [_to_gltf(matrix) for matrix in world]


def _quaternion_matrix(rotation):
    x, y, z, w = rotation
    return [[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
            [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
            [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]]


def _to_gltf(matrix):
    out = np.eye(4)
    out[:3, :3] = SOURCE_TO_GLTF @ matrix[:3, :3]
    out[:3, 3] = SOURCE_TO_GLTF @ matrix[:3, 3] * INCHES_TO_METERS
    return out


def _matrix_trs(matrix):
    translation = matrix[:3, 3]
    m = matrix[:3, :3]
    w = np.sqrt(max(0.0, 1.0 + m[0, 0] + m[1, 1] + m[2, 2])) / 2.0
    x = np.sqrt(max(0.0, 1.0 + m[0, 0] - m[1, 1] - m[2, 2])) / 2.0
    y = np.sqrt(max(0.0, 1.0 - m[0, 0] + m[1, 1] - m[2, 2])) / 2.0
    z = np.sqrt(max(0.0, 1.0 - m[0, 0] - m[1, 1] + m[2, 2])) / 2.0
    x, y, z = np.copysign(x, m[2, 1] - m[1, 2]), np.copysign(y, m[0, 2] - m[2, 0]), np.copysign(z, m[1, 0] - m[0, 1])
    return [float(v) for v in translation], [float(v) for v in (x, y, z, w)]


def skinned_mesh(source, data_dump, mesh, materials):
    gltf, binary = read_glb(source)
    names, parents, world = _source_skeleton(data_dump)
    nodes = []
    for bone, name in enumerate(names):
        local = world[bone] if parents[bone] < 0 else np.linalg.inv(world[parents[bone]]) @ world[bone]
        translation, rotation = _matrix_trs(local)
        nodes.append({"name": name, "translation": translation, "rotation": rotation})
    for bone, parent in enumerate(parents):
        if parent >= 0:
            nodes[parent].setdefault("children", []).append(bone)

    source_mesh = next((gltf["meshes"][n["mesh"]] for n in gltf["nodes"] if "mesh" in n and n.get("name", "").endswith(mesh)), None)
    if source_mesh is None:
        raise ExportError(f"{source} has no {mesh} mesh")
    if len(source_mesh["primitives"]) != len(materials):
        raise SystemExit(f"{mesh} in {source} has {len(source_mesh['primitives'])} primitives but {len(materials)} materials were given ({', '.join(materials)})")
    for index, primitive in enumerate(source_mesh["primitives"]):
        primitive["material"] = index
        primitive.pop("targets", None)

    packer = Packer()
    inverse_binds = np.stack([np.linalg.inv(m).T.reshape(16) for m in world]).astype(np.float32)
    skinned = {"asset": gltf["asset"], "scene": 0, "nodes": nodes, "meshes": [source_mesh],
               "materials": [{"name": m} for m in materials],
               "skins": [{"joints": list(range(len(names))), "inverseBindMatrices": packer.add_accessor(inverse_binds)}]}
    for primitive in source_mesh["primitives"]:
        primitive["attributes"] = {k: packer.copy(gltf, binary, v) for k, v in primitive["attributes"].items()}
        primitive["indices"] = packer.copy(gltf, binary, primitive["indices"])
    skinned["accessors"] = packer.accessors
    nodes.append({"name": mesh, "mesh": 0, "skin": 0})
    roots = [bone for bone, parent in enumerate(parents) if parent < 0]
    skinned["scenes"] = [{"nodes": roots + [len(nodes) - 1]}]
    return skinned, packer.finish(skinned)
