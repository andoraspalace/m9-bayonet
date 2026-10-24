from pathlib import Path

NAMESPACE = "bayonet"
ROOT = Path(__file__).resolve().parents[2]
EXPORT = ROOT / "cs2-export"
ASSETS = ROOT / "src/main/resources/assets" / NAMESPACE
MODELS = ASSETS / "models/gltf"
WEAR = ASSETS / "wear"
SOUNDS = ASSETS / "sounds"
SOUNDS_JSON = ASSETS / "sounds.json"
SOUND_EVENTS_JSON = ASSETS / "sound_events.json"


class ExportError(SystemExit):

    def __init__(self, problem):
        super().__init__(f"{problem}; re-run the Source 2 Viewer export")


def find(pattern):
    matches = sorted(EXPORT.glob(pattern))
    if not matches:
        raise ExportError(f"nothing in {EXPORT} matches {pattern}")
    if len(matches) > 1:
        raise SystemExit(f"{pattern} matches {len(matches)} files in {EXPORT}: {', '.join(str(m.relative_to(EXPORT)) for m in matches)}; delete the stale exports")
    return matches[0]
