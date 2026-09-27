"""
lz4_packed.py — Module giải nén và nén file LZ4 định dạng game TSM_DODO
=============================================================================
Định dạng file .lz4:
  - Legacy LZ4Stream: flags + varint(raw size) + optional varint(packed size) + payload.
  - Bit 0 means compressed; bit 1 means HC. Incompressible blocks are stored raw.
  - Never emit a compressed block larger than its original size.
  - Lặp lại cho từng block (mỗi block 1MB = 1048576 bytes)
  - File .emg, .mmg, .dat là file đã giải nén (nhị phân game)

Hỗ trợ:
  - Giải nén file .lz4 -> file gốc
  - Nén file gốc -> file .lz4
  - Tự động phát hiện định dạng
=============================================================================
"""

import lz4.block
import struct
from pathlib import Path

BLOCK_SIZE = 1048576  # 1MB
FLAGS_BYTE = 0x03


def _read_varint(data: bytes, offset: int) -> tuple:
    value = 0
    for shift in range(0, 35, 7):
        if offset >= len(data):
            raise ValueError("Truncated LZ4 header")
        byte = data[offset]; offset += 1
        value |= (byte & 127) << shift
        if not byte & 128:
            return value, offset
    raise ValueError("LZ4 varint overflow")


def _write_varint(value: int) -> bytes:
    if value < 0: raise ValueError("Negative length")
    result = bytearray()
    while value >= 128:
        result.append((value & 127) | 128); value >>= 7
    result.append(value)
    return bytes(result)


def _blocks(data: bytes, recovery=False):
    offset = 0
    while offset < len(data):
        flags, offset = _read_varint(data, offset)
        if flags & ~3: raise ValueError("Unsupported LZ4 flags")
        size, offset = _read_varint(data, offset)
        packed, offset = _read_varint(data, offset) if flags & 1 else (size, offset)
        if size > 64 * 1024 * 1024 or packed > size + size // 255 + 32:
            raise ValueError("Invalid LZ4 block length")
        if packed > size and not recovery:
            raise ValueError("Legacy LZ4 rejects compressed length greater than original")
        if offset + packed > len(data): raise ValueError("Truncated LZ4 payload")
        yield flags, size, packed, offset
        offset += packed


def is_lz4_packed(data: bytes) -> bool:
    if not data: return False
    try:
        list(_blocks(data))
        return True
    except ValueError:
        return False


def decompress(data: bytes) -> bytes:
    output = bytearray()
    for flags, size, packed, offset in _blocks(data):
        payload = data[offset:offset + packed]
        decoded = lz4.block.decompress(payload, uncompressed_size=size) if flags & 1 else payload
        if len(decoded) != size: raise ValueError("Decoded LZ4 size mismatch")
        output.extend(decoded)
    return bytes(output)


def compress(data: bytes, high_compression: bool = True) -> bytes:
    output = bytearray()
    for offset in range(0, len(data), BLOCK_SIZE):
        chunk = data[offset:offset + BLOCK_SIZE]
        packed = lz4.block.compress(chunk, mode="high_compression" if high_compression else "default", store_size=False)
        compressed = len(packed) < len(chunk)
        flags = (2 if high_compression else 0) | int(compressed)
        output.extend(_write_varint(flags)); output.extend(_write_varint(len(chunk)))
        if compressed: output.extend(_write_varint(len(packed)))
        output.extend(packed if compressed else chunk)
    return bytes(output)


def decompress_file(input_path: str, output_path: str = None) -> str:
    """
    Giải nén file .lz4.
    Nếu output_path không chỉ định, tự bỏ .lz4 khỏi tên file.
    Trả về đường dẫn file output.
    """
    input_path = Path(input_path)
    data = input_path.read_bytes()

    if not is_lz4_packed(data):
        raise ValueError(f"File {input_path.name} không phải định dạng LZ4 packed!")

    dec = decompress(data)

    if output_path is None:
        if input_path.suffix == ".lz4":
            output_path = input_path.with_suffix("")
        else:
            output_path = input_path.with_suffix(input_path.suffix + ".dec")
    else:
        output_path = Path(output_path)

    output_path.write_bytes(dec)
    return str(output_path)


def compress_file(input_path: str, output_path: str = None, high_compression: bool = True) -> str:
    """
    Nén file thành .lz4.
    Nếu output_path không chỉ định, tự thêm .lz4 vào tên file.
    Trả về đường dẫn file output.
    """
    input_path = Path(input_path)
    data = input_path.read_bytes()

    comp = compress(data, high_compression=high_compression)

    if output_path is None:
        output_path = input_path.with_suffix(input_path.suffix + ".lz4")
    else:
        output_path = Path(output_path)

    output_path.write_bytes(comp)
    return str(output_path)


def get_block_info(data: bytes) -> list:
    return [dict(block=i+1, uncomp_size=size, comp_size=packed, flags=flags,
                 ratio=(1-packed/size)*100 if size else 0, offset=offset)
            for i,(flags,size,packed,offset) in enumerate(_blocks(data))]


if __name__ == "__main__":
    import sys
    if len(sys.argv) < 3:
        print("Cách dùng:")
        print("  Giải nén:  python lz4_packed.py d <file.lz4> [output]")
        print("  Nén:       python lz4_packed.py c <file> [output.lz4]")
        sys.exit(1)

    mode = sys.argv[1]
    inp = sys.argv[2]
    out = sys.argv[3] if len(sys.argv) > 3 else None

    if mode == "d":
        result = decompress_file(inp, out)
        print(f"Đã giải nén: {inp} -> {result}")
    elif mode == "c":
        result = compress_file(inp, out)
        print(f"Đã nén: {inp} -> {result}")
    else:
        print("Mode phải là 'd' (giải nén) hoặc 'c' (nén)")
