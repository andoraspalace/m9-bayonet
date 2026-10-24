import functools
import re
from dataclasses import dataclass

from .dumps import read_dump
from .paths import find

SOUND_EVENTS = "soundevents/game_sounds_weapons.vsndevts.data.txt"
EVENT_BLOCK = re.compile(r"^\t([\w.]+) = \s*\n\t\{\n(.*?)^\t\}", re.S | re.M)
VSND_FILE = re.compile(r'"(sounds/[^"]+\.vsnd)"')
CHILD_GROUP = re.compile(r"^\t\tsoundevent_\d+ = \s*\[(.*?)\]", re.S | re.M)
CHILD_EVENTS_DISABLED = re.compile(r"^\t\tenable_child_events = false", re.M)
QUOTED = re.compile(r'"([^"]+)"')
USES_CURVE = re.compile(r"^\t\tuse_distance_volume_mapping_curve = true", re.M)
CURVE = re.compile(r"^\t\tdistance_volume_mapping_curve = \s*\[(.*?)\n\t\t\]", re.S | re.M)
CURVE_KEY = re.compile(r"\[([^\[\]]*)\]")
CURVE_KEY_VALUES = 4
LOCAL_ONLY = "localplayeronly"


@dataclass(frozen=True)
class SoundEvent:
    name: str
    volume: float
    pitch: float
    delay: float
    files: tuple[str, ...]
    children: tuple[str, ...]
    curve: tuple[tuple[float, ...], ...]
    local: bool

    @property
    def asset_id(self):
        return sound_id(self.name)


def sound_id(event):
    return event.lower()


@functools.cache
def _event_blocks():
    return dict(EVENT_BLOCK.findall(read_dump(find(SOUND_EVENTS))))


@functools.cache
def sound_event(name):
    block = _event_blocks().get(name)
    return None if block is None else _parse_event(name, block)


def _parse_event(name, block):
    return SoundEvent(
        name=name,
        volume=_block_number(block, "volume", 1.0),
        pitch=_block_number(block, "pitch", 1.0),
        delay=_block_number(block, "delay", 0.0),
        files=tuple(VSND_FILE.findall(block)),
        children=_child_events(block),
        curve=_distance_curve(block),
        local=LOCAL_ONLY in block,
    )


def _block_number(block, key, default):
    match = re.search(rf"^\t\t{key} = ([-\d.]+)", block, re.M)
    return float(match.group(1)) if match else default


def _child_events(block):
    if CHILD_EVENTS_DISABLED.search(block):
        return ()
    return tuple(child for group in CHILD_GROUP.findall(block) for child in QUOTED.findall(group))


def _distance_curve(block):
    if not USES_CURVE.search(block):
        return ()
    points = CURVE.search(block)
    return tuple(tuple([float(v) for v in key.replace(",", " ").split()][:CURVE_KEY_VALUES])
                 for key in CURVE_KEY.findall(points.group(1) if points else ""))


def with_children(roots):
    names = set(roots)
    queue = list(names)
    while queue:
        event = sound_event(queue.pop())
        if event is None:
            continue
        for child in event.children:
            if child not in names:
                names.add(child)
                queue.append(child)
    return sorted(names)
