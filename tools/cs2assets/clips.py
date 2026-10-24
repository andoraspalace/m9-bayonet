import functools
import re
from typing import NamedTuple

from .dumps import read_dump, search_dump
from .glb import Packer, read_glb, write_glb
from .paths import EXPORT, MODELS, ExportError
from .soundevents import sound_id

CLIPS = EXPORT / "animation/anims/viewmodel"
CLIP_DUMP_SUFFIX = ".vnmclip.data.txt"
SOUND_EVENT_BLOCK = re.compile(r'_class = "CNmSoundEvent"(.*?)m_flDurationInterruptionThreshold', re.S)
TIME_DECIMALS = 4


class ClipSet(NamedTuple):
    folder: str
    suffix: str
    target: str


def _clip_models(clip_set):
    folder = CLIPS / clip_set.folder
    clips = sorted(folder.glob("*.glb"))
    if not clips:
        raise ExportError(f"no clips in {folder}")
    return clips


@functools.cache
def _clip_sounds(clip):
    data_dump = clip.with_suffix(CLIP_DUMP_SUFFIX)
    text = read_dump(data_dump)
    duration = float(search_dump(r"m_flDuration = ([\d.]+)", text, data_dump).group(1))
    events = []
    for block in SOUND_EVENT_BLOCK.findall(text):
        start = float(search_dump(r"m_flStartTime = \s*\{\s*m_flValue = ([\d.e-]+)", block, data_dump).group(1))
        events.append((round(start * duration, TIME_DECIMALS), search_dump(r'm_name = "([^"]*)"', block, data_dump).group(1)))
    return tuple(events)


def clip_sound_names(clip_sets):
    return {name for clip_set in clip_sets for clip in _clip_models(clip_set) for _, name in _clip_sounds(clip)}


def build_clips(clip_set):
    target = MODELS / f"{clip_set.target}.glb"
    clips = _clip_models(clip_set)
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
        events = _clip_sounds(clip)
        for animation in source.get("animations", []):
            for sampler in animation["samplers"]:
                sampler["input"] = packer.copy(source, binary, sampler["input"])
                sampler["output"] = packer.copy(source, binary, sampler["output"])
            animation["name"] = clip.stem.removesuffix(clip_set.suffix)
            animation["extras"] = {"sounds": [[time, sound_id(name)] for time, name in events]}
            animations.append(animation)

    clip_gltf = {"asset": gltf["asset"], "scene": 0, "scenes": gltf["scenes"], "nodes": gltf["nodes"],
                 "animations": animations}
    write_glb(target, clip_gltf, packer.finish(clip_gltf))
    print(f"{target}: {len(animations)} clips ({', '.join(a['name'] for a in animations)}), "
          f"{target.stat().st_size // 1024} KB")
