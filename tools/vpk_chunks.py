import argparse
import re
import struct
import sys
import zlib
from collections import defaultdict
from pathlib import Path

DEFAULT_PATTERNS = [
    r"^weapons/models/awp/(?!materials/stickers/)",
    r"^weapons/models/knife/knife_m9/",
    r"^materials/models/weapons/customization/snip_awp/",
    r"^animation/anims/viewmodel/(rifle/rifle_awp|knife/knife_m9)/",
    r"^animation/skeletons/(weapons/(awp|knife_m9)|characters/viewmodel)\.",
    r"/(cu_awp_asimov|cu_medieval_dragon_awp|am_emerald_marbleized)\.v(comp)?mat_c$",
    r"^weapons/models/shared/stattrak/stattrak_module\.vmdl_c$",
    r"^scripts/items/items_game\.txt$",
]

DEFAULT_SKIP = [
    r"^particles/",
    r"^materials/default/",
    r"stickers?/",
    r"_(normal|rough|sss_mask)_",
]

VPK_SIGNATURE = 0x55AA1234
HEADER_SIZE = {1: 12, 2: 28}
DIR_ARCHIVE = 0x7FFF
TOOLS = Path(__file__).resolve().parent


def read_entries(path):
    data = Path(path).read_bytes()
    signature, version, tree_size = struct.unpack_from("<III", data, 0)
    if signature != VPK_SIGNATURE:
        sys.exit(f"{path} is not a VPK dir file")
    if version not in HEADER_SIZE:
        sys.exit(f"unsupported VPK version {version}")
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
                crc, preload, archive, offset, length, terminator = struct.unpack_from("<IHHIIH", data, pos)
                if terminator != 0xFFFF:
                    sys.exit(f"corrupt VPK tree at byte {pos}")
                pos += 18 + preload
                full = name if ext == " " else f"{name}.{ext}"
                if folder != " ":
                    full = f"{folder}/{full}"
                if archive == DIR_ARCHIVE:
                    offset += data_start
                yield full, archive, offset, length, data[pos - preload:pos], crc


def external_refs(data):
    if len(data) < 16:
        return []
    _size, header_version, _version, block_offset, block_count = struct.unpack_from("<IHHII", data, 0)
    if header_version != 12:
        return []
    pos = 8 + block_offset
    for _ in range(block_count):
        kind = data[pos:pos + 4]
        (offset,) = struct.unpack_from("<I", data, pos + 4)
        if kind == b"RERL":
            start = pos + 4 + offset
            rel, count = struct.unpack_from("<II", data, start)
            names = []
            for i in range(count):
                field = start + rel + i * 16 + 8
                (name_offset,) = struct.unpack_from("<q", data, field)
                s = field + name_offset
                names.append(data[s:data.index(b"\0", s)].decode("utf-8", "replace"))
            return names
        pos += 12
    return []


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("dir_vpk", help="path to pak01_dir.vpk")
    ap.add_argument("-p", "--pattern", action="append", help="regex to match (repeatable, replaces the defaults)")
    ap.add_argument("--files", action="append", default=[], help="text file of exact VPK paths to add, one per line")
    ap.add_argument("--deps", action="store_true", help="also add what downloaded files reference")
    ap.add_argument("--skip", action="append", help="regex of dependencies to leave out (repeatable, replaces the defaults)")
    ap.add_argument("-o", "--out", default=TOOLS / "cs2-chunks.txt", help="DepotDownloader filelist to write")
    ap.add_argument("--needed", default=TOOLS / "cs2-needed.txt", help="every file to export, one per line")
    ap.add_argument("--index", default=TOOLS / "cs2-vpk-index.tsv", help="every entry as path, archive, bytes")
    ap.add_argument("--depot-dir", default="game/csgo", help="folder of the VPK inside the depot")
    args = ap.parse_args()

    dir_vpk = Path(args.dir_vpk)
    prefix = dir_vpk.name.removesuffix("_dir.vpk")
    patterns = [re.compile(p, re.I) for p in (args.pattern or DEFAULT_PATTERNS)]
    skip = [re.compile(p, re.I) for p in (args.skip or DEFAULT_SKIP)]
    listed = {line.strip().lower() for f in args.files for line in Path(f).read_text(encoding="utf-8-sig").splitlines() if line.strip()}
    hits = defaultdict(int)
    entries = {}
    chunk_size = defaultdict(int)
    wanted = []

    with open(args.index, "w", encoding="utf-8") as index:
        for path, archive, offset, length, preload, crc in read_entries(dir_vpk):
            index.write(f"{path}\t{archive}\t{length + len(preload)}\n")
            entries[path] = (archive, offset, length, preload, crc)
            if archive != DIR_ARCHIVE:
                chunk_size[archive] = max(chunk_size[archive], offset + length)
            if path in listed:
                hits["--files"] += 1
                wanted.append(path)
                continue
            for p in patterns:
                if p.search(path):
                    hits[p.pattern] += 1
                    wanted.append(path)
                    break

    def chunk_path(archive):
        return dir_vpk if archive == DIR_ARCHIVE else dir_vpk.with_name(f"{prefix}_{archive:03d}.vpk")

    def read(path):
        archive, offset, length, preload, crc = entries[path]
        if not length:
            return preload
        chunk = chunk_path(archive)
        if not chunk.exists() or chunk.stat().st_size < offset + length:
            return None
        with open(chunk, "rb") as f:
            f.seek(offset)
            data = preload + f.read(length)
        return data if zlib.crc32(data) == crc else None

    needed = set(wanted)
    unread = set()
    outside = set()
    skipped = set()
    if args.deps:
        queue = list(wanted)
        while queue:
            path = queue.pop()
            data = read(path)
            if data is None:
                unread.add(path)
                continue
            for ref in external_refs(data):
                ref = ref.replace("\\", "/").lower()
                if not ref.endswith("_c"):
                    ref += "_c"
                if ref not in entries:
                    outside.add(ref)
                elif any(s.search(ref) for s in skip):
                    skipped.add(ref)
                elif ref not in needed:
                    needed.add(ref)
                    queue.append(ref)

    archives = sorted({entries[p][0] for p in needed} - {DIR_ARCHIVE})
    lines = [f"{args.depot_dir}/{prefix}_dir.vpk"]
    lines += [f"{args.depot_dir}/{prefix}_{i:03d}.vpk" for i in archives]
    Path(args.out).write_text("\n".join(lines) + "\n", encoding="utf-8")
    Path(args.needed).write_text("\n".join(sorted(needed)) + "\n", encoding="utf-8")

    for p in patterns:
        print(f"{hits[p.pattern]:6d} files  {p.pattern}")
    if listed:
        print(f"{hits['--files']:6d} files  of {len(listed)} listed in {', '.join(args.files)}")
    gb = 1024**3
    missing = [i for i in archives if not chunk_path(i).exists()]
    print(f"\n{len(needed)} files ({len(needed) - len(wanted)} found as dependencies, {len(skipped)} skipped) "
          f"in {len(archives)} of {len(chunk_size)} chunks")
    print(f"{len(missing)} chunks still to download, ~{sum(chunk_size[i] for i in missing) / gb:.1f} GB")
    if args.deps and unread:
        print(f"{len(unread)} files aren't downloaded yet, so their dependencies are unknown: "
              "download, then run this again")
    if outside:
        print(f"{len(outside)} referenced files aren't in this VPK (usually fine):")
        for ref in sorted(outside)[:20]:
            print(f"    {ref}")
    print(f"wrote {args.out}, {args.needed} and {args.index}")
    if not all(hits[p.pattern] for p in patterns):
        print("some patterns matched nothing; grep the index for the right paths", file=sys.stderr)


if __name__ == "__main__":
    main()
