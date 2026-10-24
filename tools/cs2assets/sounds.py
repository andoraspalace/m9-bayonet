import functools
import json
import shutil
import sys
from pathlib import PurePosixPath
from typing import NamedTuple

import numpy as np

from .paths import EXPORT, NAMESPACE, SOUND_EVENTS_JSON, SOUNDS, SOUNDS_JSON
from .soundevents import SoundEvent, sound_event, sound_id, with_children

LOUDNESS_RATIO = 0.45
LOUDNESS_WINDOW_SECONDS = 0.02
LOUDEST_WINDOWS_DIVISOR = 10
MIN_AMPLITUDE = 1e-9
PEAK_LIMIT = 0.98
UNITY_VOLUME_TOLERANCE = 0.999
VOLUME_DECIMALS = 3
EXPORTED_SUFFIXES = (".wav", ".mp3")
SOURCE_SUFFIX = ".vsnd"
COMPILED_SUFFIX = "_c"
REMOTE_SUFFIX = ".remote"


class _Sound(NamedTuple):
    stem: str
    data: np.ndarray
    rate: int


class _DecodedEvent(NamedTuple):
    event: SoundEvent
    sounds: tuple[_Sound, ...]
    level: float
    effective: float

    def needs_remote_variant(self):
        return not self.event.local and any(sound.data.shape[1] > 1 for sound in self.sounds)


@functools.cache
def _audio():
    try:
        import soundfile
    except ImportError:
        raise SystemExit("converting sounds to .ogg needs soundfile: python -m pip install soundfile") from None
    return soundfile


def _resolve(names):
    events = {}
    missing = []
    for name in names:
        event = sound_event(name)
        if event is None:
            missing.append(f"no sound event {name}")
        else:
            events[name] = event
    return events, missing


def _warn(messages):
    for message in messages:
        print(f"warning: {message}", file=sys.stderr)


def list_sounds(roots):
    events, missing = _resolve(with_children(roots))
    _warn(missing)
    for path in sorted({vsnd + COMPILED_SUFFIX for event in events.values() for vsnd in event.files}):
        print(path)


@functools.cache
def _exported(vsnd):
    matches = sorted(p for p in EXPORT.glob(vsnd.removesuffix(SOURCE_SUFFIX) + ".*") if p.suffix in EXPORTED_SUFFIXES)
    if len(matches) > 1:
        raise SystemExit(f"{vsnd} was exported {len(matches)} times ({', '.join(m.name for m in matches)}); delete the stale exports")
    return matches[0] if matches else None


def collect_events(roots, allow_missing):
    _audio()
    events, missing = _resolve(with_children(roots))
    missing += [f"{vsnd} was not exported (needed by {name})" for name, event in events.items() for vsnd in event.files if _exported(vsnd) is None]
    if missing and not allow_missing:
        raise SystemExit("missing sound inputs:\n  " + "\n  ".join(missing) + "\nexport them (see --list-sounds) or pass --allow-missing")
    _warn(missing)
    return events


def _to_db(amplitude):
    return 20 * np.log10(amplitude)


def _from_db(db):
    return 10 ** (db / 20)


def _loudness_db(sound):
    mono = sound.data.mean(axis=1)
    window = round(sound.rate * LOUDNESS_WINDOW_SECONDS)
    if len(mono) < window:
        return _to_db(np.sqrt(np.mean(mono ** 2)) + MIN_AMPLITUDE)
    rms = np.sqrt(np.mean(mono[:len(mono) // window * window].reshape(-1, window) ** 2, axis=1))
    return _to_db(np.mean(np.sort(rms)[-max(1, len(rms) // LOUDEST_WINDOWS_DIVISOR):]) + MIN_AMPLITUDE)


def _decode(events, anchor):
    decoded = {}
    for name, event in events.items():
        sounds = []
        for vsnd in event.files:
            path = _exported(vsnd)
            if path is not None:
                data, rate = _audio().read(path, always_2d=True)
                sounds.append(_Sound(PurePosixPath(vsnd).stem, data, rate))
        if sounds:
            level = float(np.mean([_loudness_db(sound) for sound in sounds]))
            decoded[name] = _DecodedEvent(event, tuple(sounds), level, _to_db(event.volume) + level)
    if not decoded:
        raise SystemExit("no sounds were exported; export them first (see --list-sounds)")
    if anchor not in decoded:
        raise SystemExit(f"{anchor} was not exported; it is the loudness anchor for every sound (see --list-sounds)")
    return decoded


def _clear_sounds():
    if not SOUNDS.exists():
        return
    try:
        shutil.rmtree(SOUNDS)
    except OSError as error:
        raise SystemExit(f"cannot clear {SOUNDS}: {error}; close programs using these sounds and run this again") from error


def build_sounds(events, anchor):
    decoded = _decode(events, anchor)
    _clear_sounds()
    anchor_level = decoded[anchor].effective
    sounds_json = {}
    remote_events = 0
    remote_bytes = 0
    for entry in decoded.values():
        event_id = entry.event.asset_id
        remote_id = event_id + REMOTE_SUFFIX
        remote = entry.needs_remote_variant()
        gain = _from_db(anchor_level + (entry.effective - anchor_level) * LOUDNESS_RATIO - entry.level)
        sounds = []
        remote_sounds = []
        for sound in entry.sounds:
            baked = min(gain, PEAK_LIMIT / max(np.abs(sound.data).max(), MIN_AMPLITUDE)) if gain > 1.0 else 1.0
            _write_ogg(event_id, sound.stem, sound.data * baked, sound.rate)
            sounds.append(_sound_entry(event_id, sound.stem, gain / baked, entry.event.pitch))
            if remote:
                remote_bytes += _write_ogg(remote_id, sound.stem, sound.data.mean(axis=1, keepdims=True) * baked, sound.rate)
                remote_sounds.append(_sound_entry(remote_id, sound.stem, gain / baked, entry.event.pitch))
        sounds_json[event_id] = {"sounds": sounds}
        if remote:
            sounds_json[remote_id] = {"sounds": remote_sounds}
            remote_events += 1
    _write_json(SOUNDS_JSON, sounds_json)
    _write_json(SOUND_EVENTS_JSON, {entry.event.asset_id: _behaviour(entry.event, decoded) for entry in decoded.values()})
    count = sum(len(entry.sounds) for entry in decoded.values())
    print(f"{SOUNDS_JSON}: {len(decoded)} sound events, {count} sounds, {remote_events} mono {REMOTE_SUFFIX} variants ({remote_bytes // 1024} KB)")


def _write_ogg(event_id, stem, data, rate):
    target = SOUNDS / event_id / f"{stem}.ogg"
    target.parent.mkdir(parents=True, exist_ok=True)
    _audio().write(target, data, rate, format="OGG", subtype="VORBIS")
    return target.stat().st_size


def _sound_entry(event_id, stem, volume, pitch):
    entry = {"name": f"{NAMESPACE}:{event_id}/{stem}"}
    if volume < UNITY_VOLUME_TOLERANCE:
        entry["volume"] = round(volume, VOLUME_DECIMALS)
    if pitch != 1.0:
        entry["pitch"] = pitch
    return entry


def _behaviour(event, decoded):
    entry = {"local": event.local, "curve": event.curve}
    if event.delay > 0.0:
        entry["delay"] = event.delay
    entry["children"] = [sound_id(child) for child in event.children if child in decoded]
    return entry


def _write_json(path, data):
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8", newline="\n")
