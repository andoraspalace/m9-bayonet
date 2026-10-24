import argparse
import re
import struct
import zlib
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path
from typing import NamedTuple

VIEWMODEL_CLIPS = r"viewmodel/(rifle/rifle_awp|knife/knife_(m9|butterfly))/"

DEFAULT_PATTERNS = (
    r"^weapons/models/awp/(?!materials/stickers/)",
    r"^weapons/models/knife/knife_(m9|butterfly)/",
    r"^materials/models/weapons/v_models/knife_butterfly/",
    r"^materials/models/weapons/customization/snip_awp/",
    rf"^animation/anims/{VIEWMODEL_CLIPS}",
    r"^animation/skeletons/(weapons/(awp|knife_m9|knife_butterfly)|characters/viewmodel)\.",
    r"/(cu_awp_asimov|cu_medieval_dragon_awp|am_(emerald|sapphire|ruby)_marbleized(_b)?|am_gamma_doppler_phase2)\.v(comp)?mat_c$",
    r"^weapons/models/shared/stattrak/stattrak_module\.vmdl_c$",
    r"^agents/models/shared/arms/glove_sporty/",
    r"^characters/models/shared/arms/glove_sporty/",
    r"^characters/models/shared/arms/bare_arms/materials/bare_arm_(\d+|hidden)\.vmat_c$",
    r"/sporty_blue_pink\.vmat_c$",
    r"^agents/models/tm_phoenix/tm_phoenix\.vmdl_c$",
    r"^soundevents/game_sounds_weapons\.vsndevts_c$",
    r"^scripts/items/items_game\.txt$",
)

DEFAULT_SKIP = (
    r"^particles/",
    r"^materials/default/",
    r"stickers?/",
    r"(?<!bare_arm)_normal_",
    r"_(rough|sss_mask|detailnormal|damagenormal)_",
    r"(rubber|fabric)\d+\w*_grunge_",
    rf"^animation/anims/(?!{VIEWMODEL_CLIPS})",
)

VPK_SIGNATURE = 0x55AA1234
HEADER = struct.Struct("<III")
HEADER_SIZE = {1: 12, 2: 28}
TREE_ENTRY = struct.Struct("<IHHIIH")
ENTRY_TERMINATOR = 0xFFFF
NO_NAME = " "
DIR_ARCHIVE = 0x7FFF
DIR_SUFFIX = "_dir.vpk"
RESOURCE_HEADER = struct.Struct("<IHHII")
RESOURCE_VERSION = 12
RESOURCE_BLOCKS_FIELD = 8
BLOCK_HEADER = struct.Struct("<4sII")
BLOCK_OFFSET_FIELD = 4
REFERENCE_BLOCK = b"RERL"
REFERENCE_LIST = struct.Struct("<II")
REFERENCE_SIZE = 16
REFERENCE_NAME_FIELD = 8
NAME_OFFSET = struct.Struct("<q")
COMPILED_SUFFIX = "_c"
LISTED = "--files"
SHOWN_PATHS = 20
GIB = 1024 ** 3
TOOLS = Path(__file__).resolve().parent


class Entry(NamedTuple):
    archive: int
    offset: int
    length: int
    preload: bytes
    crc: int


class Scan(NamedTuple):
    entries: dict[str, Entry]
    wanted: list[str]
    hits: dict[str, int]
    chunk_size: dict[int, int]


@dataclass
class Dependencies:
    needed: set[str]
    unread: set[str] = field(default_factory=set)
    stale: set[int] = field(default_factory=set)
    outside: set[str] = field(default_factory=set)
    skipped: set[str] = field(default_factory=set)


class NotDownloaded(Exception):
    pass


class StaleChunk(Exception):
    pass


def parse_args():
    parser = argparse.ArgumentParser(description="Find the CS2 VPK chunks that hold the files the asset build needs.")
    parser.add_argument("dir_vpk", help="path to pak01_dir.vpk")
    parser.add_argument("-p", "--pattern", action="append", help="regex to match (repeatable, replaces the defaults)")
    parser.add_argument("--files", action="append", default=[], help="text file of exact VPK paths to add, one per line")
    parser.add_argument("--deps", action="store_true", help="also add what downloaded files reference")
    parser.add_argument("--skip", action="append", help="regex of dependencies to leave out (repeatable, replaces the defaults)")
    parser.add_argument("-o", "--out", default=TOOLS / "cs2-chunks.txt", help="DepotDownloader filelist to write")
    parser.add_argument("--needed", default=TOOLS / "cs2-needed.txt", help="every file to export, one per line")
    parser.add_argument("--index", default=TOOLS / "cs2-vpk-index.tsv", help="every entry as path, archive, bytes")
    parser.add_argument("--depot-dir", default="game/csgo", help="folder of the VPK inside the depot")
    return parser.parse_args()


def read_entries(path):
    data = Path(path).read_bytes()
    signature, version, tree_size = HEADER.unpack_from(data, 0)
    if signature != VPK_SIGNATURE:
        raise SystemExit(f"{path} is not a VPK dir file")
    if version not in HEADER_SIZE:
        raise SystemExit(f"unsupported VPK version {version}")
    pos = HEADER_SIZE[version]
    data_start = pos + tree_size

    def cstr():
        nonlocal pos
        stop = data.index(b"\0", pos)
        s = data[pos:stop].decode("utf-8", "replace")
        pos = stop + 1
        return s

    while ext := cstr():
        while folder := cstr():
            while name := cstr():
                crc, preload, archive, offset, length, terminator = TREE_ENTRY.unpack_from(data, pos)
                if terminator != ENTRY_TERMINATOR:
                    raise SystemExit(f"corrupt VPK tree at byte {pos}")
                pos += TREE_ENTRY.size + preload
                full = name if ext == NO_NAME else f"{name}.{ext}"
                if folder != NO_NAME:
                    full = f"{folder}/{full}"
                if archive == DIR_ARCHIVE:
                    offset += data_start
                yield full, Entry(archive, offset, length, data[pos - preload:pos], crc)


def external_refs(data):
    if len(data) < RESOURCE_HEADER.size:
        return []
    _size, header_version, _version, block_offset, block_count = RESOURCE_HEADER.unpack_from(data, 0)
    if header_version != RESOURCE_VERSION:
        return []
    pos = RESOURCE_BLOCKS_FIELD + block_offset
    for _ in range(block_count):
        kind, offset, _length = BLOCK_HEADER.unpack_from(data, pos)
        if kind == REFERENCE_BLOCK:
            return reference_names(data, pos + BLOCK_OFFSET_FIELD + offset)
        pos += BLOCK_HEADER.size
    return []


def reference_names(data, start):
    relative, count = REFERENCE_LIST.unpack_from(data, start)
    names = []
    for i in range(count):
        name_field = start + relative + i * REFERENCE_SIZE + REFERENCE_NAME_FIELD
        (name_offset,) = NAME_OFFSET.unpack_from(data, name_field)
        name_start = name_field + name_offset
        names.append(data[name_start:data.index(b"\0", name_start)].decode("utf-8", "replace"))
    return names


def read_listed(files):
    return {line.strip().lower() for f in files for line in Path(f).read_text(encoding="utf-8-sig").splitlines() if line.strip()}


def scan(dir_vpk, patterns, listed):
    entries = dict(read_entries(dir_vpk))
    wanted = []
    hits = defaultdict(int)
    chunk_size = defaultdict(int)
    for path, entry in entries.items():
        if entry.archive != DIR_ARCHIVE:
            chunk_size[entry.archive] = max(chunk_size[entry.archive], entry.offset + entry.length)
        matched = next((pattern for pattern in patterns if pattern.search(path)), None)
        is_listed = path in listed
        if matched is not None:
            hits[matched.pattern] += 1
        if is_listed:
            hits[LISTED] += 1
        if matched is not None or is_listed:
            wanted.append(path)
    return Scan(entries, wanted, hits, chunk_size)


def chunk_name(dir_vpk, archive):
    prefix = dir_vpk.name.removesuffix(DIR_SUFFIX)
    return prefix + DIR_SUFFIX if archive == DIR_ARCHIVE else f"{prefix}_{archive:03d}.vpk"


def chunk_path(dir_vpk, archive):
    return dir_vpk if archive == DIR_ARCHIVE else dir_vpk.with_name(chunk_name(dir_vpk, archive))


def read_entry(dir_vpk, entry):
    if not entry.length:
        return entry.preload
    chunk = chunk_path(dir_vpk, entry.archive)
    if not chunk.exists() or chunk.stat().st_size < entry.offset + entry.length:
        raise NotDownloaded
    with open(chunk, "rb") as f:
        f.seek(entry.offset)
        data = entry.preload + f.read(entry.length)
    if zlib.crc32(data) != entry.crc:
        raise StaleChunk
    return data


def resolve_dependencies(dir_vpk, entries, wanted, skip):
    dependencies = Dependencies(set(wanted))
    queue = list(wanted)
    while queue:
        path = queue.pop()
        try:
            data = read_entry(dir_vpk, entries[path])
        except NotDownloaded:
            dependencies.unread.add(path)
            continue
        except StaleChunk:
            dependencies.stale.add(entries[path].archive)
            continue
        for ref in external_refs(data):
            ref = ref.replace("\\", "/").lower()
            if not ref.endswith(COMPILED_SUFFIX):
                ref += COMPILED_SUFFIX
            if ref not in entries:
                dependencies.outside.add(ref)
            elif ref in dependencies.needed:
                continue
            elif any(rule.search(ref) for rule in skip):
                dependencies.skipped.add(ref)
            else:
                dependencies.needed.add(ref)
                queue.append(ref)
    return dependencies


def write_outputs(args, dir_vpk, entries, needed):
    archives = sorted({entries[path].archive for path in needed} - {DIR_ARCHIVE})
    lines = [f"{args.depot_dir}/{chunk_name(dir_vpk, archive)}" for archive in (DIR_ARCHIVE, *archives)]
    Path(args.out).write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
    Path(args.needed).write_text("\n".join(sorted(needed)) + "\n", encoding="utf-8", newline="\n")
    Path(args.index).write_text("".join(f"{path}\t{entry.archive}\t{entry.length + len(entry.preload)}\n" for path, entry in entries.items()),
                                encoding="utf-8", newline="\n")
    return archives


def report(args, dir_vpk, patterns, listed, found, dependencies, archives):
    for pattern in patterns:
        print(f"{found.hits[pattern.pattern]:6d} files  {pattern.pattern}")
    unmatched = [pattern.pattern for pattern in patterns if not found.hits[pattern.pattern]]
    if unmatched:
        print(f"these patterns matched nothing, grep the index for the right paths: {', '.join(unmatched)}")
    if listed:
        print(f"{found.hits[LISTED]:6d} files  of {len(listed)} listed in {', '.join(args.files)}")
    unknown = sorted(listed - found.entries.keys())
    if unknown:
        print(f"{len(unknown)} listed files aren't in this VPK:")
        for path in unknown[:SHOWN_PATHS]:
            print(f"    {path}")
    missing = [archive for archive in archives if not chunk_path(dir_vpk, archive).exists()]
    print(f"\n{len(dependencies.needed)} files ({len(dependencies.needed) - len(found.wanted)} found as dependencies, "
          f"{len(dependencies.skipped)} skipped) in {len(archives)} of {len(found.chunk_size)} chunks")
    print(f"{len(missing)} chunks still to download, ~{sum(found.chunk_size[archive] for archive in missing) / GIB:.1f} GB")
    if dependencies.unread:
        print(f"{len(dependencies.unread)} files aren't downloaded yet, so their dependencies are unknown: "
              "download, then run this again")
    if dependencies.stale:
        print(f"CRC mismatch in {', '.join(chunk_name(dir_vpk, archive) for archive in sorted(dependencies.stale))}: "
              "re-download with -validate, then run this again")
    if dependencies.outside:
        print(f"{len(dependencies.outside)} referenced files aren't in this VPK (usually fine):")
        for ref in sorted(dependencies.outside)[:SHOWN_PATHS]:
            print(f"    {ref}")
    print(f"wrote {args.out}, {args.needed} and {args.index}")


def main():
    args = parse_args()
    dir_vpk = Path(args.dir_vpk)
    if not dir_vpk.exists():
        raise SystemExit(f"{dir_vpk} does not exist")
    if not dir_vpk.is_file():
        raise SystemExit(f"{dir_vpk} is not a file, pass the path to pak01_dir.vpk")
    patterns = [re.compile(pattern, re.I) for pattern in (args.pattern or DEFAULT_PATTERNS)]
    skip = [re.compile(rule, re.I) for rule in (args.skip or DEFAULT_SKIP)]
    listed = read_listed(args.files)
    found = scan(dir_vpk, patterns, listed)
    if args.deps:
        dependencies = resolve_dependencies(dir_vpk, found.entries, found.wanted, skip)
    else:
        dependencies = Dependencies(set(found.wanted))
    archives = write_outputs(args, dir_vpk, found.entries, dependencies.needed)
    report(args, dir_vpk, patterns, listed, found, dependencies, archives)


if __name__ == "__main__":
    main()
