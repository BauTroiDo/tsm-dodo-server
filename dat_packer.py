"""
dat_packer.py — Parse & pack các file .dat trong CompreseData

Hỗ trợ:
  - Talk_C.dat: [count(4LE)] [id(2LE)] [textlen(2LE)] [utf16le_text(textlen)] ...
    -> Xuất ra JSON {id: text} để edit
    -> Đóng gói lại từ JSON

  - Eve.emg: file index nhị phân (chỉ đọc, không cần edit text)
  - Ground.mmg: file bản đồ nhị phân (chỉ đọc)

Usage:
  python dat_packer.py unpack Talk_C.dat.lz4 -> Talk_C.json
  python dat_packer.py pack Talk_C.json -> Talk_C.dat.lz4
"""
import struct
import json
import sys
from pathlib import Path

# ── Talk_C.dat format ──────────────────────────────────────────────
# [uint32 count] then count * [uint16 id, uint16 textlen, utf16le_bytes(textlen)]

def unpack_talk_data(data: bytes) -> dict:
    """Parse Talk_C.dat -> {id: text}"""
    pos = 0
    count = struct.unpack_from('<I', data, pos)[0]
    pos += 4
    entries = {}
    for i in range(count):
        if pos + 4 > len(data):
            break
        eid = struct.unpack_from('<H', data, pos)[0]
        pos += 2
        tlen = struct.unpack_from('<H', data, pos)[0]
        pos += 2
        text_bytes = data[pos:pos + tlen]
        pos += tlen
        text = text_bytes.decode('utf-16-le', errors='replace')
        entries[eid] = text
    return entries

def pack_talk_data(entries: dict) -> bytes:
    """Pack {id: text} -> Talk_C.dat bytes"""
    result = bytearray()
    result += struct.pack('<I', len(entries))
    for eid, text in entries.items():
        text_bytes = text.encode('utf-16-le', errors='replace')
        result += struct.pack('<H', eid)
        result += struct.pack('<H', len(text_bytes))
        result += text_bytes
    return bytes(result)

def unpack_talk_file(input_path: Path, output_path: Path):
    """Giải nén + parse Talk_C.dat(.lz4) -> JSON"""
    data = input_path.read_bytes()
    if input_path.suffix == '.lz4':
        import lz4_packed
        if lz4_packed.is_lz4_packed(data):
            data = lz4_packed.decompress(data)
    entries = unpack_talk_data(data)
    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(entries, f, ensure_ascii=False, indent=1)
    print(f"[OK] Unpacked {len(entries)} entries -> {output_path}")
    return entries

def pack_talk_file(input_json: Path, output_path: Path, compress: bool = True):
    """JSON -> Talk_C.dat(.lz4)"""
    with open(input_json, 'r', encoding='utf-8') as f:
        entries = json.load(f)
    # Convert keys to int
    entries = {int(k): v for k, v in entries.items()}
    data = pack_talk_data(entries)
    if compress and output_path.suffix == '.lz4':
        import lz4_packed
        data = lz4_packed.compress(data, high_compression=True)
    output_path.write_bytes(data)
    print(f"[OK] Packed {len(entries)} entries -> {output_path} ({len(data)} bytes)")

# ── Eve.emg format ─────────────────────────────────────────────────
# File index: [uint16 count] then count * [1-byte flag(0x09)] [23-byte name(null-padded)] [uint32 pos] [uint32 size]
# = 32 bytes per entry. Names like "10701.eve"
# Data section starts at offset 2 + count*32

EVE_ENTRY_SIZE = 32   # 1 flag + 23 name + 4 pos + 4 size
EVE_NAME_LEN = 23

def unpack_eve_data(data: bytes) -> list:
    """Parse Eve.emg -> list of {index, name, position, size, flag}"""
    pos = 0
    count = struct.unpack_from('<H', data, pos)[0]
    pos += 2
    entries = []
    for i in range(count):
        if pos + EVE_ENTRY_SIZE > len(data):
            break
        flag = data[pos]
        pos += 1
        name_bytes = data[pos:pos + EVE_NAME_LEN]
        name = name_bytes.split(b'\x00')[0].decode('ascii', errors='replace')
        pos += EVE_NAME_LEN
        position = struct.unpack_from('<I', data, pos)[0]
        pos += 4
        size = struct.unpack_from('<I', data, pos)[0]
        pos += 4
        entries.append({'index': i, 'flag': flag, 'name': name, 'position': position, 'size': size})
    return entries

def pack_eve_data(entries: list) -> bytes:
    """Pack list of {name, position, size, flag} -> Eve.emg bytes"""
    result = bytearray()
    result += struct.pack('<H', len(entries))
    for e in entries:
        flag = e.get('flag', 0x09)
        name = e.get('name', '')
        name_bytes = name.encode('ascii', errors='replace')[:EVE_NAME_LEN]
        name_bytes = name_bytes + b'\x00' * (EVE_NAME_LEN - len(name_bytes))
        position = e.get('position', 0)
        size = e.get('size', 0)
        result += bytes([flag])
        result += name_bytes
        result += struct.pack('<I', position)
        result += struct.pack('<I', size)
    return bytes(result)

def unpack_eve_file(input_path: Path, output_path: Path):
    """Giải nén + parse Eve.emg(.lz4) -> JSON"""
    data = input_path.read_bytes()
    if input_path.suffix == '.lz4':
        import lz4_packed
        if lz4_packed.is_lz4_packed(data):
            data = lz4_packed.decompress(data)
    entries = unpack_eve_data(data)
    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(entries, f, ensure_ascii=False, indent=1)
    print(f"[OK] Unpacked {len(entries)} eve entries -> {output_path}")
    return entries

def pack_eve_file(input_json: Path, output_path: Path, compress: bool = True):
    """JSON -> Eve.emg(.lz4)"""
    with open(input_json, 'r', encoding='utf-8') as f:
        entries = json.load(f)
    data = pack_eve_data(entries)
    if compress and output_path.suffix == '.lz4':
        import lz4_packed
        data = lz4_packed.compress(data, high_compression=True)
    output_path.write_bytes(data)
    print(f"[OK] Packed {len(entries)} eve entries -> {output_path} ({len(data)} bytes)")

# ── Ground.mmg format ──────────────────────────────────────────────
# File: [uint16 width] [uint16 pad] [uint16 height] [uint16 pad] then tile/grid data,
# followed by an index section at the end:
#   count * [1-byte flag(0x09)] [9-byte name(null-padded)] [uint32 pos] [uint32 size] [extra fields]
# Names like "10001.map"

def unpack_ground_index(data: bytes, index_offset: int) -> list:
    """Parse the map index at the end of Ground.mmg."""
    entries = []
    pos = index_offset
    while pos + 1 + 9 + 4 + 4 <= len(data):
        flag = data[pos]
        if flag != 0x09:
            # Try to skip or break
            break
        pos += 1
        name_bytes = data[pos:pos + 9]
        name = name_bytes.split(b'\x00')[0].decode('ascii', errors='replace')
        pos += 9
        if pos + 8 > len(data):
            break
        position = struct.unpack_from('<I', data, pos)[0]
        pos += 4
        size = struct.unpack_from('<I', data, pos)[0]
        pos += 4
        # Some entries have extra 8 bytes (two more uint32s)
        entries.append({'name': name, 'position': position, 'size': size})
    return entries

def unpack_ground_data(data: bytes) -> dict:
    """Parse Ground.mmg -> {width, height, maps: [...]}"""
    width = struct.unpack_from('<H', data, 0)[0]
    height = struct.unpack_from('<H', data, 4)[0]
    # Find index: scan for 0x09 followed by "1000" or similar
    index_offset = None
    search_start = len(data) - 200000
    if search_start < 8:
        search_start = 8
    for i in range(search_start, len(data) - 20):
        if data[i] == 0x09:
            name_candidate = data[i + 1:i + 10]
            if b'.map' in name_candidate:
                index_offset = i
                break
    maps = []
    if index_offset is not None:
        maps = unpack_ground_index(data, index_offset)
    return {
        'width': width,
        'height': height,
        'index_offset': index_offset,
        'map_count': len(maps),
        'maps': maps,
    }

def unpack_ground_file(input_path: Path, output_path: Path):
    """Giải nén + parse Ground.mmg(.lz4) -> JSON"""
    data = input_path.read_bytes()
    if input_path.suffix == '.lz4':
        import lz4_packed
        if lz4_packed.is_lz4_packed(data):
            data = lz4_packed.decompress(data)
    info = unpack_ground_data(data)
    with open(output_path, 'w', encoding='utf-8') as f:
        # Don't dump full map data, just the index
        out = {
            'width': info['width'],
            'height': info['height'],
            'index_offset': info['index_offset'],
            'map_count': info['map_count'],
            'maps': info['maps'],
        }
        json.dump(out, f, ensure_ascii=False, indent=1)
    print(f"[OK] Unpacked {info['map_count']} maps -> {output_path}")
    return info

# ── CLI ───────────────────────────────────────────────────────────
if __name__ == '__main__':
    if len(sys.argv) < 4:
        print("Usage:")
        print("  python dat_packer.py unpack_talk <input.dat[.lz4]> <output.json>")
        print("  python dat_packer.py pack_talk <input.json> <output.dat[.lz4]>")
        print("  python dat_packer.py unpack_eve <input.emg[.lz4]> <output.json>")
        print("  python dat_packer.py pack_eve <input.json> <output.emg[.lz4]>")
        print("  python dat_packer.py unpack_ground <input.mmg[.lz4]> <output.json>")
        sys.exit(1)
    cmd = sys.argv[1]
    inp = Path(sys.argv[2])
    outp = Path(sys.argv[3])
    if cmd == 'unpack_talk':
        unpack_talk_file(inp, outp)
    elif cmd == 'pack_talk':
        pack_talk_file(inp, outp)
    elif cmd == 'unpack_eve':
        unpack_eve_file(inp, outp)
    elif cmd == 'pack_eve':
        pack_eve_file(inp, outp)
    elif cmd == 'unpack_ground':
        unpack_ground_file(inp, outp)
    else:
        print(f"Unknown command: {cmd}")
