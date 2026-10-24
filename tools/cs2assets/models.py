from pathlib import Path

from .glb import Packer, read_accessor, read_glb, write_glb
from .images import sample_per_vertex, to_png
from .paths import ExportError

UNUSED_ATTRIBUTES = ("TANGENT", "TEXCOORD_1")
REFLECTIVITY_ATTRIBUTE = "_REFLECTIVITY"


def build(source, target, *, drop_nodes=(), textures=None, reflectivity=None, extras=None):
    build_model(*read_glb(source), source, target, drop_nodes=drop_nodes, textures=textures, reflectivity=reflectivity, extras=extras)


def build_model(gltf, binary, source, target, *, drop_nodes=(), textures=None, reflectivity=None, extras=None):
    textures = textures or {}
    reflectivity = reflectivity or {}
    extras = extras or {}
    _check_inputs(gltf, source, drop_nodes, textures.keys() | reflectivity.keys() | extras.keys())

    _drop_meshes(gltf, drop_nodes)
    _keep_used_meshes(gltf)
    _strip_unused_data(gltf)
    images = _assign_materials(gltf, textures, extras)
    packer = Packer()
    _pack_buffers(gltf, binary, packer, reflectivity)
    gltf["images"] = [{"bufferView": packer.add_view(png), "mimeType": "image/png"} for png in images]
    gltf["textures"] = [{"source": i} for i in range(len(images))]
    for key in ("samplers", "extensionsUsed", "extensionsRequired"):
        gltf.pop(key, None)

    write_glb(target, gltf, packer.finish(gltf))
    triangles = sum(gltf["accessors"][p["indices"]]["count"] // 3 for m in gltf["meshes"] for p in m["primitives"])
    print(f"{target}: {triangles} triangles, {len(images)} textures, {target.stat().st_size // 1024} KB")


def _check_inputs(gltf, source, drop_nodes, material_names):
    unknown = material_names - {m.get("name") for m in gltf.get("materials", [])}
    if unknown:
        raise ExportError(f"{source} has no material {', '.join(sorted(unknown))}")
    mesh_nodes = [n.get("name", "") for n in gltf["nodes"] if "mesh" in n]
    unmatched = [d for d in drop_nodes if not any(d in name for name in mesh_nodes)]
    if unmatched:
        raise ExportError(f"{source} has no mesh node matching {', '.join(unmatched)}")


def _drop_meshes(gltf, drop_nodes):
    for node in gltf["nodes"]:
        if "mesh" in node and any(d in node.get("name", "") for d in drop_nodes):
            del node["mesh"]
            node.pop("skin", None)


def _keep_used_meshes(gltf):
    remap = {}
    meshes = []
    for node in gltf["nodes"]:
        if "mesh" not in node:
            continue
        if node["mesh"] not in remap:
            remap[node["mesh"]] = len(meshes)
            meshes.append(gltf["meshes"][node["mesh"]])
        node["mesh"] = remap[node["mesh"]]
    gltf["meshes"] = meshes


def _strip_unused_data(gltf):
    for mesh in gltf["meshes"]:
        mesh.pop("weights", None)
        for primitive in mesh["primitives"]:
            primitive.pop("targets", None)
            for attribute in UNUSED_ATTRIBUTES:
                primitive["attributes"].pop(attribute, None)


def _assign_materials(gltf, textures, extras):
    images = []
    image_of = {}
    for material in gltf.get("materials", []):
        name = material["name"]
        pbr = {"metallicFactor": 0.0, "roughnessFactor": 1.0}
        texture = textures.get(name)
        if texture is not None:
            if id(texture) not in image_of:
                image_of[id(texture)] = len(images)
                images.append(texture.read_bytes() if isinstance(texture, Path) else to_png(texture))
            pbr["baseColorTexture"] = {"index": image_of[id(texture)]}
        material.clear()
        material.update({"name": name, "pbrMetallicRoughness": pbr})
        if name in extras:
            material["extras"] = extras[name]
    return images


def _pack_buffers(gltf, binary, packer, reflectivity):
    copied = {}

    def use(index):
        if index not in copied:
            copied[index] = packer.copy(gltf, binary, index)
        return copied[index]

    for mesh in gltf["meshes"]:
        for primitive in mesh["primitives"]:
            material = gltf["materials"][primitive["material"]]["name"] if "material" in primitive else None
            reflective = reflectivity.get(material)
            uvs = read_accessor(gltf, binary, primitive["attributes"]["TEXCOORD_0"]) if reflective is not None else None
            primitive["attributes"] = {k: use(v) for k, v in primitive["attributes"].items()}
            if reflective is not None:
                primitive["attributes"][REFLECTIVITY_ATTRIBUTE] = packer.add_accessor(sample_per_vertex(reflective, uvs))
            if "indices" in primitive:
                primitive["indices"] = use(primitive["indices"])
    for skin in gltf.get("skins", []):
        if "inverseBindMatrices" in skin:
            skin["inverseBindMatrices"] = use(skin["inverseBindMatrices"])
    for animation in gltf.get("animations", []):
        for sampler in animation["samplers"]:
            sampler["input"] = use(sampler["input"])
            sampler["output"] = use(sampler["output"])
