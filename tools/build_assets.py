import argparse
import json
import re
import shutil
import struct
from io import BytesIO
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
EXPORT = ROOT / "cs2-export"
ASSETS = ROOT / "src/main/resources/assets/bayonet"
OUT = ASSETS / "models/gltf"
WEAR = ASSETS / "wear"
WEAR_SIZE = 1024
CLIPS = EXPORT / "animation/anims/viewmodel"
SOUND_EVENTS = EXPORT / "soundevents/game_sounds_weapons.vsndevts.data.txt"
TEXTURE_SIZE = 2048
REFLECTIVITY_SIZE = TEXTURE_SIZE // 8

LOUDNESS_RATIO = 0.45

GAME_SOUNDS = [
    "Weapon_AWP.Single",
    "Weapon_Knife.HitWall",
    "Weapon_Knife.Hit.Light.Flesh",
    "Weapon_Knife.Hit.Heavy.Flesh",
    "Weapon_Knife.Hit.Light.Backstab.Flesh",
    "Weapon_Knife.Hit.Heavy.Backstab.Flesh",
]

LEGACY_WEAR = {"wear_maps": "awp_legacy"}


def load(path, size=None):
    image = Image.open(path).convert("RGBA")
    if size and image.size != (size, size):
        image = Image.merge("RGBA", [band.resize((size, size), Image.LANCZOS) for band in image.split()])
    return np.asarray(image, dtype=np.float32) / 255.0


def find(pattern):
    matches = sorted(EXPORT.glob(pattern))
    if not matches:
        raise SystemExit(f"nothing in {EXPORT} matches {pattern}; re-run the Source 2 Viewer export")
    if len(matches) > 1:
        raise SystemExit(f"{pattern} matches {len(matches)} files in {EXPORT}: {', '.join(str(m.relative_to(EXPORT)) for m in matches)}; delete the stale exports")
    return matches[0]


def to_uint8(rgb):
    return (np.clip(rgb, 0.0, 1.0) * 255.0 + 0.5).astype(np.uint8)


def to_png(rgb):
    out = BytesIO()
    Image.fromarray(to_uint8(rgb), "RGB").save(out, "PNG", optimize=True)
    return out.getvalue()


def smoothstep(edge0, edge1, x):
    t = np.clip((x - edge0) / (edge1 - edge0), 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


def material_params(path):
    text = path.read_text(encoding="utf-8")
    params = {}
    for name, value in re.findall(r'm_name = "(\w+)"\s+m_flValue = ([-\d.e]+)', text):
        params[name] = float(value)
    for name, value in re.findall(r'm_name = "(\w+)"\s+m_value = \[([^\]]*)\]', text):
        params[name] = np.array([float(v) for v in value.split(",")], dtype=np.float32)
    return params


def bake_custom_paint(pattern):
    paint = load(pattern, TEXTURE_SIZE)
    ao = load(find("materials/models/weapons/v_models/snip_awp/awp_ao_psd_*.png"), TEXTURE_SIZE)[..., 0]
    return paint[..., :3] * (0.35 + 0.65 * ao[..., None])


def paint_reflectivity(alpha, low, span):
    gloss = 0.55 + 0.45 * np.clip((alpha - low) / span, 0.0, 1.0)
    return np.dstack([np.full(alpha.shape + (3,), 0.16, np.float32), gloss])


def metal_reflectivity(color, rough, metal):
    tint = color / np.maximum(color.max(axis=2, keepdims=True), 1e-3) * 0.8
    return np.dstack([0.06 + (tint - 0.06) * metal[..., None], 1.0 - rough])


def reflectivity_custom_paint(pattern):
    alpha = load(pattern, REFLECTIVITY_SIZE)[..., 3]
    low, high = np.percentile(alpha, 5), np.percentile(alpha, 95)
    return paint_reflectivity(alpha, low, max(high - low, 1e-3))


def bake_emerald():
    params = material_params(find("materials/**/paints/vmats/am_emerald_marbleized.vmat.data.txt"))
    size = TEXTURE_SIZE
    base = load(find("materials/models/weapons/v_models/knife_m9_bay/knife_m9_bay_color_psd_*.png"), size)
    masks = load(find("weapons/models/knife/knife_m9/materials/composite_inputs/knife_m9_bay_masks_psd_*.png"), size)
    ao = load(find("weapons/models/knife/knife_m9/materials/composite_inputs/knife_m9_bay_ao_psd_*.png"), size)[..., 0]
    pattern = load(find("materials/**/paints/anodized_multi/smoke2_psd_*.png"))

    scale = params["g_flPatternTexCoordScale"]
    v, u = np.mgrid[0:size, 0:size].astype(np.float32)
    ph, pw = pattern.shape[:2]
    px = (((u + 0.5) / size * scale) % 1.0 * pw).astype(np.int32)
    py = (((v + 0.5) / size * scale) % 1.0 * ph).astype(np.int32)
    p = pattern[py, px]

    paint = np.broadcast_to(params["g_vColor0"][:3], base.shape[:2] + (3,))
    for channel in range(3):
        paint = paint + (params[f"g_vColor{channel + 1}"][:3] - paint) * p[..., channel:channel + 1]

    metal = base[..., :3] @ np.array([0.2126, 0.7152, 0.0722], dtype=np.float32)
    anodized = paint * (0.45 + 1.1 * metal[..., None]) * (0.4 + 0.6 * ao[..., None])
    blade = smoothstep(0.85, 0.95, masks[..., 0])[..., None]
    return base[..., :3] + (anodized - base[..., :3]) * blade


def awp_default_maps(size):
    color = load(find("weapons/models/awp/materials/awp_default_color_psd_*.png"), size)[..., :3]
    orm = load(find("weapons/models/awp/awp_default_ao_png_*_orm_*.png"), size)
    return color, orm[..., 0], orm[..., 1], orm[..., 2]


def bake_awp_default():
    color, ao, _, _ = awp_default_maps(TEXTURE_SIZE)
    return color * (0.35 + 0.65 * ao[..., None])


def reflectivity_awp_default():
    color, _, rough, metal = awp_default_maps(REFLECTIVITY_SIZE)
    return metal_reflectivity(color, rough, metal)


def save_rgba(path, rgb, alpha):
    Image.fromarray(to_uint8(np.dstack([rgb, alpha])), "RGBA").save(path, "PNG", optimize=True)
    print(f"{path}: {path.stat().st_size // 1024} KB")


def build_wear_maps():
    WEAR.mkdir(parents=True, exist_ok=True)
    legacy = load(find("materials/models/weapons/v_models/snip_awp/awp_substrate_color_psd_*.png"), WEAR_SIZE)
    legacy_ao = load(find("materials/models/weapons/v_models/snip_awp/awp_ao_psd_*.png"), WEAR_SIZE)[..., 0]
    save_rgba(WEAR / "awp_legacy.png", legacy[..., :3] * (0.35 + 0.65 * legacy_ao[..., None]), legacy[..., 3])
    wear = load(find("materials/**/shared/paint_wear_psd_*.png"), WEAR_SIZE)[..., 0]
    grunge = load(find("materials/**/shared/gun_grunge_psd_*.png"), WEAR_SIZE)[..., :3]
    save_rgba(WEAR / "shared.png", np.dstack([wear, grunge[..., 0], grunge[..., 1]]), grunge[..., 2])


def bake_stattrak_module():
    color = load(find("weapons/models/shared/stattrak/materials/stattrak_module_color_tga_*.png"))
    ao = load(find("weapons/models/shared/stattrak/materials/stattrak_module_ao_tga_*.png"), color.shape[0])[..., 0]
    return color[..., :3] * (0.35 + 0.65 * ao[..., None])


def reflectivity_asimov(pattern):
    alpha = load(pattern, REFLECTIVITY_SIZE)[..., 3]
    return paint_reflectivity(alpha, 16 / 255, 58 / 255)


def reflectivity_scope_lens():
    return np.array([[[0.08, 0.1, 0.16, 1.0]]], np.float32)


def bake_m9_default():
    return load(find("materials/models/weapons/v_models/knife_m9_bay/knife_m9_bay_color_psd_*.png"), TEXTURE_SIZE)[..., :3]


def reflectivity_knife(baked):
    knife = EXPORT / "weapons/models/knife/knife_m9/materials/composite_inputs"
    rough = load(knife / "knife_m9_bay_rough.png", REFLECTIVITY_SIZE)
    color = np.asarray(Image.fromarray(to_uint8(baked)).resize((REFLECTIVITY_SIZE, REFLECTIVITY_SIZE), Image.BOX), np.float32) / 255.0
    return metal_reflectivity(color, rough[..., 0], rough[..., 1])


def sample_per_vertex(image, uvs):
    h, w = image.shape[:2]
    x = (uvs[:, 0] % 1.0) * w - 0.5
    y = (uvs[:, 1] % 1.0) * h - 0.5
    x0 = np.floor(x).astype(np.int64)
    y0 = np.floor(y).astype(np.int64)
    fx = (x - x0)[:, None]
    fy = (y - y0)[:, None]

    def at(yy, xx):
        return image[yy % h, xx % w]

    top = at(y0, x0) * (1 - fx) + at(y0, x0 + 1) * fx
    bottom = at(y0 + 1, x0) * (1 - fx) + at(y0 + 1, x0 + 1) * fx
    return (top * (1 - fy) + bottom * fy).astype(np.float32)


COMPONENT_TYPES = {5120: np.int8, 5121: np.uint8, 5122: np.int16, 5123: np.uint16, 5125: np.uint32, 5126: np.float32}
COMPONENTS = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}


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


def read_glb(path):
    data = path.read_bytes()
    (json_length,) = struct.unpack_from("<I", data, 12)
    gltf = json.loads(data[20:20 + json_length])
    (bin_length,) = struct.unpack_from("<I", data, 20 + json_length)
    return gltf, data[28 + json_length:28 + json_length + bin_length]


def write_glb(path, gltf, binary):
    text = json.dumps(gltf, separators=(",", ":")).encode("utf-8")
    text += b" " * (-len(text) % 4)
    binary += b"\0" * (-len(binary) % 4)
    total = 12 + 8 + len(text) + 8 + len(binary)
    path.write_bytes(struct.pack("<III", 0x46546C67, 2, total)
                     + struct.pack("<II", len(text), 0x4E4F534A) + text
                     + struct.pack("<II", len(binary), 0x004E4942) + binary)


def build(source, target, drop_nodes=(), textures=None, reflectivity=None, extras=None):
    gltf, binary = read_glb(source)
    textures = textures or {}
    reflectivity = reflectivity or {}
    extras = extras or {}

    for node in gltf["nodes"]:
        if "mesh" in node and any(d in node.get("name", "") for d in drop_nodes):
            del node["mesh"]
            node.pop("skin", None)

    mesh_map = {}
    meshes = []
    for node in gltf["nodes"]:
        if "mesh" in node:
            if node["mesh"] not in mesh_map:
                mesh_map[node["mesh"]] = len(meshes)
                mesh = gltf["meshes"][node["mesh"]]
                for primitive in mesh["primitives"]:
                    for attribute in ("TANGENT", "TEXCOORD_1"):
                        primitive["attributes"].pop(attribute, None)
                meshes.append(mesh)
            node["mesh"] = mesh_map[node["mesh"]]
    gltf["meshes"] = meshes

    images = []
    for material in gltf.get("materials", []):
        name = material["name"]
        pbr = {"metallicFactor": 0.0, "roughnessFactor": 1.0}
        texture = textures.get(name)
        if texture is not None:
            png = texture.read_bytes() if isinstance(texture, Path) else to_png(texture)
            pbr["baseColorTexture"] = {"index": len(images)}
            images.append(png)
        material.clear()
        material.update({"name": name, "pbrMetallicRoughness": pbr})
        if name in extras:
            material["extras"] = extras[name]

    packer = Packer()
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
                primitive["attributes"]["_REFLECTIVITY"] = packer.add_accessor(sample_per_vertex(reflective, uvs))
            if "indices" in primitive:
                primitive["indices"] = use(primitive["indices"])
    for skin in gltf.get("skins", []):
        if "inverseBindMatrices" in skin:
            skin["inverseBindMatrices"] = use(skin["inverseBindMatrices"])
    for animation in gltf.get("animations", []):
        for sampler in animation["samplers"]:
            sampler["input"] = use(sampler["input"])
            sampler["output"] = use(sampler["output"])

    gltf["images"] = [{"bufferView": packer.add_view(png), "mimeType": "image/png"} for png in images]
    gltf["textures"] = [{"source": i} for i in range(len(images))]
    gltf.pop("samplers", None)
    for key in ("extensionsUsed", "extensionsRequired"):
        gltf.pop(key, None)

    write_glb(target, gltf, packer.finish(gltf))
    triangles = sum(gltf["accessors"][p["indices"]]["count"] // 3 for m in gltf["meshes"] for p in m["primitives"])
    print(f"{target}: {triangles} triangles, {len(images)} textures, {target.stat().st_size // 1024} KB")


def build_clips(folder, suffix, target):
    clips = sorted(folder.glob("*.glb"))
    if not clips:
        raise SystemExit(f"no clips in {folder}; re-run the Source 2 Viewer export")
    gltf, _ = read_glb(clips[0])
    names = [n.get("name") for n in gltf["nodes"]]
    for node in gltf["nodes"]:
        node.pop("mesh", None)
        node.pop("skin", None)

    packer = Packer()
    animations = []
    for clip in clips:
        source, binary = read_glb(clip)
        if [n.get("name") for n in source["nodes"]] != names:
            raise SystemExit(f"{clip} has a different skeleton from {clips[0]}")
        events = clip_sounds(clip.with_suffix(".vnmclip.data.txt"))
        for animation in source.get("animations", []):
            for sampler in animation["samplers"]:
                sampler["input"] = packer.copy(source, binary, sampler["input"])
                sampler["output"] = packer.copy(source, binary, sampler["output"])
            animation["name"] = clip.stem.removesuffix(suffix)
            animation["extras"] = {"sounds": [[time, sound_id(name)] for time, name in events]}
            animations.append(animation)

    clip_gltf = {"asset": gltf["asset"], "scene": 0, "scenes": gltf["scenes"], "nodes": gltf["nodes"],
                 "animations": animations}
    write_glb(target, clip_gltf, packer.finish(clip_gltf))
    print(f"{target}: {len(animations)} clips ({', '.join(a['name'] for a in animations)}), "
          f"{target.stat().st_size // 1024} KB")


def clip_sounds(data_dump):
    if not data_dump.exists():
        raise SystemExit(f"{data_dump} is missing; dump the clip with Source2Viewer-CLI -b DATA")
    text = data_dump.read_text(encoding="utf-8-sig")
    duration = float(re.search(r"m_flDuration = ([\d.]+)", text).group(1))
    events = []
    for block in re.findall(r'_class = "CNmSoundEvent"(.*?)m_flDurationInterruptionThreshold', text, re.S):
        start = float(re.search(r"m_flStartTime = \s*\{\s*m_flValue = ([\d.e-]+)", block).group(1))
        events.append((round(start * duration, 4), re.search(r'm_name = "([^"]*)"', block).group(1)))
    return events


def sound_id(event):
    return event.lower()


def needed_sounds():
    names = set(GAME_SOUNDS)
    for dump in CLIPS.rglob("*.vnmclip.data.txt"):
        names.update(name for _, name in clip_sounds(dump))
    return sorted(names)


def sound_definitions(names):
    text = SOUND_EVENTS.read_text(encoding="utf-8-sig")
    blocks = dict(re.findall(r"^\t([\w.]+) = \s*\n\t\{\n(.*?)^\t\}", text, re.S | re.M))
    definitions = {}
    for name in names:
        block = blocks.get(name)
        if block is None:
            print(f"warning: no sound event {name}")
            continue
        number = lambda key, default: float(m.group(1)) if (m := re.search(rf"^\t\t{key} = ([-\d.]+)", block, re.M)) else default
        files = re.findall(r'"(sounds/[^"]+\.vsnd)"', block)
        definitions[name] = {"volume": number("volume", 1.0), "pitch": number("pitch", 1.0), "files": files}
    return definitions


def loudness_db(samples):
    mono = samples.mean(axis=1)
    window = 882
    if len(mono) < window:
        return 20 * np.log10(np.sqrt(np.mean(mono ** 2)) + 1e-9)
    rms = np.sqrt(np.mean(mono[:len(mono) // window * window].reshape(-1, window) ** 2, axis=1))
    return 20 * np.log10(np.mean(np.sort(rms)[-max(1, len(rms) // 10):]) + 1e-9)


def build_sounds():
    try:
        import soundfile
    except ImportError:
        raise SystemExit("converting sounds to .ogg needs soundfile: python -m pip install soundfile")

    events = {}
    for name, definition in sound_definitions(needed_sounds()).items():
        files = []
        for vsnd in definition["files"]:
            exported = [p for p in EXPORT.glob(vsnd.removesuffix(".vsnd") + ".*") if p.suffix in (".wav", ".mp3")]
            if not exported:
                print(f"warning: {vsnd} was not exported")
                continue
            data, rate = soundfile.read(exported[0], always_2d=True)
            files.append((Path(vsnd).stem, data, rate))
        if files:
            level = float(np.mean([loudness_db(data) for _, data, _ in files]))
            events[name] = (definition, files, level, 20 * np.log10(definition["volume"]) + level)

    if not events:
        raise SystemExit("no sounds were exported; export them first (see --list-sounds)")

    out = ASSETS / "sounds"
    shutil.rmtree(out, ignore_errors=True)
    loudest = max(effective for _, _, _, effective in events.values())
    sounds_json = {}
    for name, (definition, files, level, effective) in events.items():
        gain_db = loudest + (effective - loudest) * LOUDNESS_RATIO - level
        entries = []
        for stem, data, rate in files:
            gain = 10 ** (gain_db / 20)
            baked = min(gain, 0.98 / max(np.abs(data).max(), 1e-9)) if gain > 1.0 else 1.0
            target = out / sound_id(name) / f"{stem}.ogg"
            target.parent.mkdir(parents=True, exist_ok=True)
            soundfile.write(target, data * baked, rate, format="OGG", subtype="VORBIS")
            entry = {"name": f"bayonet:{sound_id(name)}/{stem}"}
            if gain / baked < 0.999:
                entry["volume"] = round(gain / baked, 3)
            if definition["pitch"] != 1.0:
                entry["pitch"] = definition["pitch"]
            entries.append(entry)
        sounds_json[sound_id(name)] = {"sounds": entries}
    (ASSETS / "sounds.json").write_text(json.dumps(sounds_json, indent=2) + "\n", encoding="utf-8")
    count = sum(len(files) for _, files, _, _ in events.values())
    print(f"{ASSETS / 'sounds.json'}: {len(sounds_json)} sound events, {count} sounds")


class Packer:

    def __init__(self):
        self.views = []
        self.accessors = []
        self.chunks = []
        self.offset = 0

    def add_view(self, data, **extra):
        padding = -self.offset % 4
        self.chunks.append(b"\0" * padding + data)
        self.offset += padding
        self.views.append({"buffer": 0, "byteOffset": self.offset, "byteLength": len(data), **extra})
        self.offset += len(data)
        return len(self.views) - 1

    def add_accessor(self, values):
        values = np.ascontiguousarray(values, np.float32)
        self.accessors.append({
            "bufferView": self.add_view(values.tobytes()), "componentType": 5126, "count": len(values),
            "type": {1: "SCALAR", 2: "VEC2", 3: "VEC3", 4: "VEC4"}[values.shape[1]],
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


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--list-sounds", action="store_true", help="print the sound files to export, then stop")
    args = parser.parse_args()
    if args.list_sounds:
        for definition in sound_definitions(needed_sounds()).values():
            for vsnd in definition["files"]:
                print(vsnd + "_c")
        return

    OUT.mkdir(parents=True, exist_ok=True)
    asimov = find("materials/**/paints/custom/workshop/zone9_awp_tga_*.png")
    build(
        EXPORT / "weapons/models/awp/weapon_snip_awp.glb", OUT / "awp_asimov.glb",
        drop_nodes=["body_hd"],
        textures={"awp": bake_custom_paint(asimov), "shared_scope": find("weapons/models/awp/shared_scope_vmat_g_tcolor_*.png")},
        reflectivity={"awp": reflectivity_asimov(asimov), "shared_scope": reflectivity_scope_lens()},
        extras={"awp": LEGACY_WEAR},
    )
    build(
        EXPORT / "weapons/models/awp/weapon_snip_awp.glb", OUT / "awp_default.glb",
        drop_nodes=["body_legacy"],
        textures={"weapon_snip_awp": bake_awp_default()},
        reflectivity={"weapon_snip_awp": reflectivity_awp_default()},
    )
    dragon = find("materials/models/weapons/customization/paints/custom/dragon_awp_psd_*.png")
    build(
        EXPORT / "weapons/models/awp/weapon_snip_awp.glb", OUT / "awp_dragon_lore.glb",
        drop_nodes=["body_hd"],
        textures={"awp": bake_custom_paint(dragon), "shared_scope": find("weapons/models/awp/shared_scope_vmat_g_tcolor_*.png")},
        reflectivity={"awp": reflectivity_custom_paint(dragon), "shared_scope": reflectivity_scope_lens()},
        extras={"awp": LEGACY_WEAR},
    )
    emerald = bake_emerald()
    build(
        EXPORT / "weapons/models/knife/knife_m9/weapon_knife_m9.glb", OUT / "m9_emerald.glb",
        textures={"weapon_knife_m9": emerald},
        reflectivity={"weapon_knife_m9": reflectivity_knife(emerald)},
    )
    m9 = bake_m9_default()
    build(
        EXPORT / "weapons/models/knife/knife_m9/weapon_knife_m9.glb", OUT / "m9_default.glb",
        textures={"weapon_knife_m9": m9},
        reflectivity={"weapon_knife_m9": reflectivity_knife(m9)},
    )
    stattrak = EXPORT / "weapons/models/shared/stattrak"
    build(
        stattrak / "stattrak_module.glb", OUT / "stattrak_module.glb",
        textures={
            "stattrak_module": bake_stattrak_module(),
            "stattrak_module_display": find("weapons/models/shared/stattrak/materials/stattrak_digit_atlas_psd_*.png"),
        },
    )
    build_wear_maps()
    build_clips(CLIPS / "rifle/rifle_awp", "_awp", OUT / "awp_anims.glb")
    build_clips(CLIPS / "knife/knife_m9", "_m9", OUT / "m9_anims.glb")
    build_sounds()


if __name__ == "__main__":
    main()
