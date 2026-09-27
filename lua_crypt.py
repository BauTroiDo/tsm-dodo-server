#!/usr/bin/env python3
"""
lua_crypt.py — Tool giai ma va ma hoa file .lua cho TS Mobile Online (tsm_dodo)
==============================================================================

Thuat toan: AES-128-CBC voi PKCS7 padding
Key: 1234567870541704 (16 bytes ASCII/UTF-8)
IV:  7054170412345678 (16 bytes ASCII/UTF-8)
Nguon: ProjectSetting (libil2cpp.so)

Tuong thich:
- Tu dong su dung thu vien 'cryptography' neu co.
- Neu khong co 'cryptography', tu dong su dung bo ma hoa AES-128-CBC Pure Python tich hop san (khong can pip install).
"""

import argparse
import hashlib
import os
import re
import sys
from pathlib import Path

# Dam bao terminal Windows in tieng Viet / ky tu dac biet khong bi loi charmap cp1252
if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

# ================================================================
# Hang so Crypto chuan tu libil2cpp (ProjectSetting)
# ================================================================
AES_KEY = b"1234567870541704"
AES_IV  = b"7054170412345678"

# Thu muc mac dinh trong project
SCRIPT_DIR = Path(__file__).resolve().parent

CDN_MIRROR_DIRS = [
    SCRIPT_DIR / "cdn" / "mirror" / "tsr" / "2.4.76" / "LuaScripts" / "Lua",
    SCRIPT_DIR / "cdn" / "mirror" / "tsr" / "2.4.69" / "LuaScripts" / "Lua",
]

LUA_DIR = SCRIPT_DIR / "lua"
CDN_DATA_DIR = SCRIPT_DIR / "cdn" / "data"
TSMLUA_ZIP = Path("D:/Games/Ts-Offline/TSMobileOnLine/TSMlua.zip")


# ================================================================
# Pure Python AES-128-CBC Engine (Zero external dependencies)
# ================================================================
_SBOX = [
    0x63, 0x7c, 0x77, 0x7b, 0xf2, 0x6b, 0x6f, 0xc5, 0x30, 0x01, 0x67, 0x2b, 0xfe, 0xd7, 0xab, 0x76,
    0xca, 0x82, 0xc9, 0x7d, 0xfa, 0x59, 0x47, 0xf0, 0xad, 0xd4, 0xa2, 0xaf, 0x9c, 0xa4, 0x72, 0xc0,
    0xb7, 0xfd, 0x93, 0x26, 0x36, 0x3f, 0xf7, 0xcc, 0x34, 0xa5, 0xe5, 0xf1, 0x71, 0xd8, 0x31, 0x15,
    0x04, 0xc7, 0x23, 0xc3, 0x18, 0x96, 0x05, 0x9a, 0x07, 0x12, 0x80, 0xe2, 0xeb, 0x27, 0xb2, 0x75,
    0x09, 0x83, 0x2c, 0x1a, 0x1b, 0x6e, 0x5a, 0xa0, 0x52, 0x3b, 0xd6, 0xb3, 0x29, 0xe3, 0x2f, 0x84,
    0x53, 0xd1, 0x00, 0xed, 0x20, 0xfc, 0xb1, 0x5b, 0x6a, 0xcb, 0xbe, 0x39, 0x4a, 0x4c, 0x58, 0xcf,
    0xd0, 0xef, 0xaa, 0xfb, 0x43, 0x4d, 0x33, 0x85, 0x45, 0xf9, 0x02, 0x7f, 0x50, 0x3c, 0x9f, 0xa8,
    0x51, 0xa3, 0x40, 0x8f, 0x92, 0x9d, 0x38, 0xf5, 0xbc, 0xb6, 0xda, 0x21, 0x10, 0xff, 0xf3, 0xd2,
    0xcd, 0x0c, 0x13, 0xec, 0x5f, 0x97, 0x44, 0x17, 0xc4, 0xa7, 0x7e, 0x3d, 0x64, 0x5d, 0x19, 0x73,
    0x60, 0x81, 0x4f, 0xdc, 0x22, 0x2a, 0x90, 0x88, 0x46, 0xee, 0xb8, 0x14, 0xde, 0x5e, 0x0b, 0xdb,
    0xe0, 0x32, 0x3a, 0x0a, 0x49, 0x06, 0x24, 0x5c, 0xc2, 0xd3, 0xac, 0x62, 0x91, 0x95, 0xe4, 0x79,
    0xe7, 0xc8, 0x37, 0x6d, 0x8d, 0xd5, 0x4e, 0xa9, 0x6c, 0x56, 0xf4, 0xea, 0x65, 0x7a, 0xae, 0x08,
    0xba, 0x78, 0x25, 0x2e, 0x1c, 0xa6, 0xb4, 0xc6, 0xe8, 0xdd, 0x74, 0x1f, 0x4b, 0xbd, 0x8b, 0x8a,
    0x70, 0x3e, 0xb5, 0x66, 0x48, 0x03, 0xf6, 0x0e, 0x61, 0x35, 0x57, 0xb9, 0x86, 0xc1, 0x1d, 0x9e,
    0xe1, 0xf8, 0x98, 0x11, 0x69, 0xd9, 0x8e, 0x94, 0x9b, 0x1e, 0x87, 0xe9, 0xce, 0x55, 0x28, 0xdf,
    0x8c, 0xa1, 0x89, 0x0d, 0xbf, 0xe6, 0x42, 0x68, 0x41, 0x99, 0x2d, 0x0f, 0xb0, 0x54, 0xbb, 0x16
]
_INV_SBOX = [0] * 256
for _i, _v in enumerate(_SBOX):
    _INV_SBOX[_v] = _i

_RCON = [0x00, 0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40, 0x80, 0x1b, 0x36]

def _sub_word(w):
    return ((_SBOX[(w >> 24) & 0xff] << 24) |
            (_SBOX[(w >> 16) & 0xff] << 16) |
            (_SBOX[(w >> 8) & 0xff] << 8) |
            _SBOX[w & 0xff])

def _rot_word(w):
    return ((w << 8) & 0xffffffff) | ((w >> 24) & 0xff)

def _key_expansion(key: bytes):
    w = [0] * 44
    for i in range(4):
        w[i] = (key[4*i] << 24) | (key[4*i + 1] << 16) | (key[4*i + 2] << 8) | key[4*i + 3]
    for i in range(4, 44):
        temp = w[i - 1]
        if i % 4 == 0:
            temp = _sub_word(_rot_word(temp)) ^ (_RCON[i // 4] << 24)
        w[i] = w[i - 4] ^ temp
    return w

def _xtime(a):
    return ((a << 1) ^ 0x1b) & 0xff if (a & 0x80) else (a << 1)

def _mul(a, b):
    res = 0
    while b:
        if b & 1: res ^= a
        a = _xtime(a)
        b >>= 1
    return res

def _encrypt_block(block: bytes, w: list) -> bytes:
    state = list(block)
    for i in range(4):
        k = w[i]
        state[4*i]     ^= (k >> 24) & 0xff
        state[4*i + 1] ^= (k >> 16) & 0xff
        state[4*i + 2] ^= (k >> 8)  & 0xff
        state[4*i + 3] ^= k & 0xff

    for round in range(1, 10):
        state = [_SBOX[x] for x in state]
        state = [
            state[0], state[5], state[10], state[15],
            state[4], state[9], state[14], state[3],
            state[8], state[13], state[2], state[7],
            state[12], state[1], state[6], state[11]
        ]
        new_state = [0] * 16
        for c in range(4):
            i = c * 4
            s0, s1, s2, s3 = state[i], state[i+1], state[i+2], state[i+3]
            new_state[i]   = _xtime(s0) ^ _xtime(s1) ^ s1 ^ s2 ^ s3
            new_state[i+1] = s0 ^ _xtime(s1) ^ _xtime(s2) ^ s2 ^ s3
            new_state[i+2] = s0 ^ s1 ^ _xtime(s2) ^ _xtime(s3) ^ s3
            new_state[i+3] = _xtime(s0) ^ s0 ^ s1 ^ s2 ^ _xtime(s3)
        state = new_state
        for c in range(4):
            k = w[round * 4 + c]
            state[4*c]     ^= (k >> 24) & 0xff
            state[4*c + 1] ^= (k >> 16) & 0xff
            state[4*c + 2] ^= (k >> 8)  & 0xff
            state[4*c + 3] ^= k & 0xff

    state = [_SBOX[x] for x in state]
    state = [
        state[0], state[5], state[10], state[15],
        state[4], state[9], state[14], state[3],
        state[8], state[13], state[2], state[7],
        state[12], state[1], state[6], state[11]
    ]
    for c in range(4):
        k = w[40 + c]
        state[4*c]     ^= (k >> 24) & 0xff
        state[4*c + 1] ^= (k >> 16) & 0xff
        state[4*c + 2] ^= (k >> 8)  & 0xff
        state[4*c + 3] ^= k & 0xff

    return bytes(state)

def _decrypt_block(block: bytes, w: list) -> bytes:
    state = list(block)
    for c in range(4):
        k = w[40 + c]
        state[4*c]     ^= (k >> 24) & 0xff
        state[4*c + 1] ^= (k >> 16) & 0xff
        state[4*c + 2] ^= (k >> 8)  & 0xff
        state[4*c + 3] ^= k & 0xff

    for round in range(9, 0, -1):
        state = [
            state[0], state[13], state[10], state[7],
            state[4], state[1], state[14], state[11],
            state[8], state[5], state[2], state[15],
            state[12], state[9], state[6], state[3]
        ]
        state = [_INV_SBOX[x] for x in state]
        for c in range(4):
            k = w[round * 4 + c]
            state[4*c]     ^= (k >> 24) & 0xff
            state[4*c + 1] ^= (k >> 16) & 0xff
            state[4*c + 2] ^= (k >> 8)  & 0xff
            state[4*c + 3] ^= k & 0xff
        new_state = [0] * 16
        for c in range(4):
            i = c * 4
            s0, s1, s2, s3 = state[i], state[i+1], state[i+2], state[i+3]
            new_state[i]   = _mul(0x0e, s0) ^ _mul(0x0b, s1) ^ _mul(0x0d, s2) ^ _mul(0x09, s3)
            new_state[i+1] = _mul(0x09, s0) ^ _mul(0x0e, s1) ^ _mul(0x0b, s2) ^ _mul(0x0d, s3)
            new_state[i+2] = _mul(0x0d, s0) ^ _mul(0x09, s1) ^ _mul(0x0e, s2) ^ _mul(0x0b, s3)
            new_state[i+3] = _mul(0x0b, s0) ^ _mul(0x0d, s1) ^ _mul(0x09, s2) ^ _mul(0x0e, s3)
        state = new_state

    state = [
        state[0], state[13], state[10], state[7],
        state[4], state[1], state[14], state[11],
        state[8], state[5], state[2], state[15],
        state[12], state[9], state[6], state[3]
    ]
    state = [_INV_SBOX[x] for x in state]
    for c in range(4):
        k = w[c]
        state[4*c]     ^= (k >> 24) & 0xff
        state[4*c + 1] ^= (k >> 16) & 0xff
        state[4*c + 2] ^= (k >> 8)  & 0xff
        state[4*c + 3] ^= k & 0xff

    return bytes(state)

def _pure_aes_cbc_encrypt(data: bytes, key: bytes, iv: bytes) -> bytes:
    pad_len = 16 - (len(data) % 16)
    padded = data + bytes([pad_len] * pad_len)
    w = _key_expansion(key)
    out = bytearray()
    prev = iv
    for i in range(0, len(padded), 16):
        block = bytes(p ^ b for p, b in zip(padded[i:i+16], prev))
        enc = _encrypt_block(block, w)
        out.extend(enc)
        prev = enc
    return bytes(out)

def _pure_aes_cbc_decrypt(data: bytes, key: bytes, iv: bytes) -> bytes:
    if len(data) % 16 != 0:
        raise ValueError(f"Dữ liệu có độ dài {len(data)}, không phải bội số của 16!")
    w = _key_expansion(key)
    out = bytearray()
    prev = iv
    for i in range(0, len(data), 16):
        block = data[i:i+16]
        dec = _decrypt_block(block, w)
        plain_block = bytes(d ^ p for d, p in zip(dec, prev))
        out.extend(plain_block)
        prev = block
    pad_len = out[-1]
    if pad_len < 1 or pad_len > 16:
        raise ValueError("Invalid padding bytes")
    if out[-pad_len:] != bytes([pad_len] * pad_len):
        raise ValueError("Invalid padding bytes")
    return bytes(out[:-pad_len])


# ================================================================
# Core AES-128-CBC Encrypt / Decrypt API
# ================================================================
try:
    from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
    from cryptography.hazmat.primitives import padding as _crypto_padding
    _HAS_CRYPTOGRAPHY = True
except ImportError:
    _HAS_CRYPTOGRAPHY = False


def aes_decrypt(enc_data: bytes, key: bytes = AES_KEY, iv: bytes = AES_IV) -> bytes:
    """Giai ma AES-128-CBC voi PKCS7 unpadding."""
    if _HAS_CRYPTOGRAPHY:
        if len(enc_data) % 16 != 0:
            raise ValueError(f"Du lieu ma hoa co do dai {len(enc_data)}, khong chia het cho 16!")
        cipher = Cipher(algorithms.AES(key), modes.CBC(iv))
        decryptor = cipher.decryptor()
        padded_plain = decryptor.update(enc_data) + decryptor.finalize()
        unpadder = _crypto_padding.PKCS7(128).unpadder()
        return unpadder.update(padded_plain) + unpadder.finalize()
    else:
        return _pure_aes_cbc_decrypt(enc_data, key, iv)


def aes_encrypt(plain_data: bytes, key: bytes = AES_KEY, iv: bytes = AES_IV) -> bytes:
    """Ma hoa AES-128-CBC voi PKCS7 padding."""
    if _HAS_CRYPTOGRAPHY:
        padder = _crypto_padding.PKCS7(128).padder()
        padded_data = padder.update(plain_data) + padder.finalize()
        cipher = Cipher(algorithms.AES(key), modes.CBC(iv))
        encryptor = cipher.encryptor()
        return encryptor.update(padded_data) + encryptor.finalize()
    else:
        return _pure_aes_cbc_encrypt(plain_data, key, iv)


# Helper compatibility functions for existing scripts
def compute_keystream(plain_bytes: bytes, enc_bytes: bytes) -> bytes:
    return AES_KEY


def apply_keystream(data_bytes: bytes, keystream: bytes) -> bytes:
    return aes_decrypt(data_bytes)


def clean_filename(name: str) -> str:
    """Loai bo cac hau to tam thoi nhu .decrypted, .plain, .reencrypted, .tmp..."""
    return re.sub(r'\.(decrypted|plain|reencrypted|tmp|new|mod)', '', name, flags=re.I)


def resolve_relative_path(file_path: Path, explicit_rel: str | None = None, alt_path: Path | None = None) -> str:
    if explicit_rel:
        return explicit_rel.replace("\\", "/").lstrip("/")
    return clean_filename(file_path.name)


def find_reference_plain(rel_path: str, filename: str) -> tuple[bytes | None, str | None]:
    p = LUA_DIR / rel_path.replace("/", os.sep)
    if p.is_file():
        return p.read_bytes(), str(p)
    return None, None


def find_reference_encrypted(rel_path: str, filename: str) -> tuple[bytes | None, str | None]:
    p = CDN_DATA_DIR / rel_path.replace("/", os.sep)
    if p.is_file():
        return p.read_bytes(), str(p)
    return None, None


# ================================================================
# Giai ma file (Decrypt)
# ================================================================
def decrypt_file(input_path: Path, output_path: Path, rel_path: str | None = None) -> bytes:
    """Giai ma file .lua encrypted tu CDN sang plain text."""
    if not input_path.exists():
        raise FileNotFoundError(f"Khong tim thay file input: {input_path}")

    enc_data = input_path.read_bytes()
    decrypted = aes_decrypt(enc_data)

    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_bytes(decrypted)

    print(f"[OK] Da giai ma AES-128-CBC -> {output_path} ({len(decrypted)} bytes)")
    return decrypted


# ================================================================
# Ma hoa file (Encrypt)
# ================================================================
def encrypt_file(input_path: Path, output_path: Path, rel_path: str | None = None, ref_enc_file: Path | None = None) -> bytes:
    """Ma hoa file .lua plain text sang dinh dang CDN encrypted bang AES-128-CBC."""
    if not input_path.exists():
        raise FileNotFoundError(f"Khong tim thay file input: {input_path}")

    plain_data = input_path.read_bytes()
    encrypted = aes_encrypt(plain_data)

    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_bytes(encrypted)

    md5_hash = hashlib.md5(encrypted).hexdigest().upper()
    print(f"[OK] Da ma hoa AES-128-CBC -> {output_path} ({len(encrypted)} bytes, MD5: {md5_hash})")
    return encrypted


# ================================================================
# Doi IP / Port truc tiep (Helper)
# ================================================================
def set_server_ip(file_path: Path, new_host: str, new_port: int | None = None, output_path: Path | None = None):
    """
    Doc file Network.lua (encrypted hoac plain), thay the IP/Port, roi ma hoa lai bang AES.
    """
    if output_path is None:
        output_path = file_path

    raw_data = file_path.read_bytes()

    # Thu giai ma bang AES truoc, neu loi thi xem nhu da la plain text
    try:
        plain_bytes = aes_decrypt(raw_data)
    except Exception:
        plain_bytes = raw_data

    content = plain_bytes.decode("utf-8", errors="replace")

    # Tim dong servers
    pattern = r'(this\.servers\s*=\s*\{\{id=1,\s*host=")([^"]+)(".*\}\};?)'
    match = re.search(pattern, content)
    if not match:
        raise ValueError(f"Khong tim thay dong 'this.servers' trong {file_path}!")

    old_host = match.group(2)
    print(f"[*] Host hien tai: {old_host}")
    print(f"[*] Host moi:      {new_host}")

    def replace_server(m):
        header = m.group(1)
        tail_part = m.group(3)
        if new_port is not None:
            tail_part = re.sub(r'(port=)\d+', f'\\g<1>{new_port}', tail_part)
        return f'{header}{new_host}{tail_part}'

    new_content = re.sub(pattern, replace_server, content)
    new_plain_bytes = new_content.encode("utf-8")

    # Ma hoa lai bang AES-CBC PKCS7
    encrypted = aes_encrypt(new_plain_bytes)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_bytes(encrypted)

    md5_hash = hashlib.md5(encrypted).hexdigest().upper()
    print(f"[OK] Da cap nhat IP/Port -> {output_path} ({len(encrypted)} bytes, MD5: {md5_hash})")
    return encrypted


# ================================================================
# Thong tin file (Info)
# ================================================================
def show_info(file_path: Path):
    if not file_path.exists():
        print(f"[LOI] Khong tim thay: {file_path}")
        return

    data = file_path.read_bytes()
    md5 = hashlib.md5(data).hexdigest().upper()

    print("=" * 60)
    print(f"File:       {file_path.resolve()}")
    print(f"Size:       {len(data)} bytes ({len(data) / 1024:.2f} KB)")
    print(f"Size % 16:  {len(data) % 16}")
    print(f"MD5:        {md5}")

    # Thu decrypt bang AES
    try:
        dec = aes_decrypt(data)
        print(f"Trang thai: ENCRYPTED hop le (AES-128-CBC) -> Decrypted size: {len(dec)} bytes")
        print(f"Xem truoc:  {repr(dec[:60].decode('utf-8', errors='replace'))}")
    except Exception as e:
        is_text = False
        try:
            prev = data[:80].decode("utf-8")
            if prev.isprintable() or "\n" in prev:
                is_text = True
        except Exception:
            pass
        if is_text:
            print(f"Trang thai: PLAIN TEXT (.lua)")
            print(f"Xem truoc:  {repr(data[:60].decode('utf-8', errors='replace'))}")
        else:
            print(f"Trang thai: KHONG THE GIAI MA ({e})")
    print("=" * 60)


# ================================================================
# CLI Interface
# ================================================================
def main():
    parser = argparse.ArgumentParser(
        description="Tool giai ma va ma hoa file .lua cua game TS Mobile Online (tsm_dodo)",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    # Subcommand: decrypt
    p_dec = subparsers.add_parser("decrypt", help="Giai ma file .lua encrypted sang plain text")
    p_dec.add_argument("input", help="File .lua can giai ma")
    p_dec.add_argument("output", help="File plain text luu ket qua")
    p_dec.add_argument("--rel-path", help="Duong dan tuong doi")

    # Subcommand: encrypt
    p_enc = subparsers.add_parser("encrypt", help="Ma hoa file .lua plain text sang file CDN encrypted")
    p_enc.add_argument("input", help="File plain text can ma hoa")
    p_enc.add_argument("output", help="File ma hoa luu ket qua")
    p_enc.add_argument("--rel-path", help="Duong dan tuong doi")
    p_enc.add_argument("--ref-enc", help="Khong can thiet (giu cho tuong thich)")

    # Subcommand: set-server
    p_srv = subparsers.add_parser("set-server", help="Doi IP/Port truc tiep trong file Network.lua")
    p_srv.add_argument("file", help="File Network.lua can sua")
    p_srv.add_argument("--host", required=True, help="Dia chi IP hoac domain server moi")
    p_srv.add_argument("--port", type=int, default=None, help="Cong server (mac dinh 6613)")
    p_srv.add_argument("--output", help="File dau ra")

    # Subcommand: info
    p_info = subparsers.add_parser("info", help="Xem thong tin va checksum file .lua")
    p_info.add_argument("file", help="File can xem")

    args = parser.parse_args()

    try:
        if args.command == "decrypt":
            decrypt_file(Path(args.input), Path(args.output), args.rel_path)
        elif args.command == "encrypt":
            encrypt_file(Path(args.input), Path(args.output), args.rel_path)
        elif args.command == "set-server":
            out = Path(args.output) if args.output else None
            set_server_ip(Path(args.file), args.host, args.port, out)
        elif args.command == "info":
            show_info(Path(args.file))
    except Exception as e:
        print(f"\n[LOI] {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()
