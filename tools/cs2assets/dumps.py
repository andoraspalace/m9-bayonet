import re

from .paths import ExportError


def read_dump(path):
    if not path.is_file():
        raise SystemExit(f"{path} is missing; dump it with Source2Viewer-CLI -b DATA")
    return path.read_text(encoding="utf-8-sig")


def search_dump(pattern, text, dump, flags=0):
    match = re.search(pattern, text, flags)
    if match is None:
        raise ExportError(f"{dump} has nothing matching {pattern}")
    return match
