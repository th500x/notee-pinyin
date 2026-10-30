import struct, zlib, sys
from pathlib import Path

def bmp_to_png(src: Path, dest: Path):
    data = src.read_bytes()
    if data[:2] != b"BM":
        raise SystemExit(f"not a bmp: {src}")
    offset = struct.unpack_from("<I", data, 10)[0]
    header = struct.unpack_from("<I", data, 14)[0]
    w, h = struct.unpack_from("<ii", data, 18)
    planes, bpp = struct.unpack_from("<HH", data, 26)
    if bpp != 24 or h < 0:
        raise SystemExit(f"unexpected bmp {w}x{h} bpp={bpp} hsign={h}")
    stride = (w * 3 + 3) & ~3
    rows = []
    for y in range(h - 1, -1, -1):
        start = offset + y * stride
        bgr = data[start:start + w * 3]
        rgb = bytearray(w * 3)
        for i in range(w):
            rgb[i * 3] = bgr[i * 3 + 2]
            rgb[i * 3 + 1] = bgr[i * 3 + 1]
            rgb[i * 3 + 2] = bgr[i * 3]
        rows.append(bytes(rgb))
    def chunk(tag, payload):
        return struct.pack(">I", len(payload)) + tag + payload + struct.pack(">I", zlib.crc32(tag + payload) & 0xFFFFFFFF)
    raw = b"".join(b"\x00" + row for row in rows)
    ihdr = struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    dest.write_bytes(png)
    print(f"{dest.name} {w}x{h} {dest.stat().st_size}")

root = Path(r"D:\Google Docs\KIRO\notee-pinyin\store")
for name in ["phone-1-setup", "phone-2-qwerty", "phone-3-t9", "phone-4-tools"]:
    bmp_to_png(root / f"{name}.bmp", root / f"{name}.png")
    (root / f"{name}.bmp").unlink()
bmp_to_png(root / "source" / "keys.bmp", root / "source" / "keys.png")
(root / "source" / "keys.bmp").unlink()
