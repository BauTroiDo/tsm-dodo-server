import os
import sys
import json
import zlib
import shutil
from pathlib import Path

if sys.platform == 'win32':
    try:
        sys.stdout.reconfigure(encoding='utf-8', errors='replace')
        sys.stderr.reconfigure(encoding='utf-8', errors='replace')
    except Exception:
        pass

# Thư mục nguồn và đích
vtc_dir = Path(r"C:\Server-Game\tsm_dodo\cdn\mirror\tsr\VTC")
dst_dir = Path(r"C:\Server-Game\tsm_dodo\cdn\mirror\tsr\2.4.76\Android\Resources")
res_list_p = dst_dir / "ResourceList.dat"

print(f"[*] Bắt đầu merge từ {vtc_dir} sang {dst_dir}...")

# 1. Đọc ResourceList.dat của VTC và 2.4.76
with open(vtc_dir / "ResourceList.dat", "r", encoding="utf-8-sig") as f:
    vtc_data = json.load(f)

with open(res_list_p, "r", encoding="utf-8-sig") as f:
    p76_data = json.load(f)

vtc_bundles = vtc_data.get("bundles", {})
p76_bundles = p76_data.get("bundles", {})

merged_bundles = dict(vtc_bundles)

# Giữ lại các bundle chỉ có ở 2.4.76 (ví dụ: các map l11150 -> l11155)
kept_p76 = 0
for bname, binfo in p76_bundles.items():
    if bname not in merged_bundles:
        merged_bundles[bname] = binfo
        kept_p76 += 1
print(f"    - Đã giữ lại {kept_p76} bundles đặc thù của 2.4.76")

# 2. Copy tất cả file .unity3d (NGOẠI TRỪ font.unity3d)
copied = 0
skipped = 0
for f in vtc_dir.glob("*.unity3d"):
    if f.name.lower() == "font.unity3d":
        print("    [!] BỎ QUA font.unity3d (Giữ nguyên Be Vietnam Pro Bold)")
        skipped += 1
        continue
    target = dst_dir / f.name
    shutil.copy2(f, target)
    copied += 1

print(f"    [OK] Đã copy {copied} file .unity3d sang 2.4.76 (bỏ qua {skipped} file)")

# 3. Chuẩn hóa font.unity3d trong ResourceList.dat
if "font" in merged_bundles:
    font_file = dst_dir / "font.unity3d"
    if font_file.is_file():
        fb = font_file.read_bytes()
        merged_bundles["font"]["size"] = len(fb)
        merged_bundles["font"]["crc"] = zlib.crc32(fb) & 0xffffffff
        print(f"    [OK] Chuẩn hóa font.unity3d: size={len(fb)}, crc={merged_bundles['font']['crc']}")

# 4. Quét và cập nhật chính xác size và crc32 của tất cả bundles trên đĩa
print("[*] Đang xác thực Size và CRC32 toàn bộ bundle...")
updated_crc = 0
missing_on_disk = 0

for bname, binfo in list(merged_bundles.items()):
    fn = bname + ".unity3d"
    fp = dst_dir / fn
    if fp.is_file():
        raw = fp.read_bytes()
        sz = len(raw)
        crc = zlib.crc32(raw) & 0xffffffff
        if binfo.get("size") != sz or binfo.get("crc") != crc:
            binfo["size"] = sz
            binfo["crc"] = crc
            updated_crc += 1
    else:
        missing_on_disk += 1

vtc_data["bundles"] = merged_bundles

# Ghi lại ResourceList.dat
with open(res_list_p, "w", encoding="utf-8") as f:
    json.dump(vtc_data, f, ensure_ascii=False, indent=2)

print(f"[HOÀN TẤT] Tổng bundles: {len(merged_bundles)}")
print(f"           Đã cập nhật CRC/Size: {updated_crc}")
print(f"           File thiếu trên đĩa: {missing_on_disk}")
