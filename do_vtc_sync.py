import os
import sys
import json
import zlib
import shutil
import time
from pathlib import Path

if sys.platform == 'win32':
    try:
        sys.stdout.reconfigure(encoding='utf-8', errors='replace')
        sys.stderr.reconfigure(encoding='utf-8', errors='replace')
    except Exception:
        pass

vtc_res_dir = Path(r"C:\Server-Game\tsm_dodo\cdn\mirror\tsr\VTC\Resources")
dst_dir = Path(r"C:\Server-Game\tsm_dodo\cdn\mirror\tsr\2.4.76\Android\Resources")
res_list_p = dst_dir / "ResourceList.dat"
backup_res_list = dst_dir / "ResourceList.dat.bak_before_vtc_sync"

print("==========================================================")
print("[*] BẮT ĐẦU ĐỒNG BỘ TOÀN BỘ TÀI NGUYÊN VTC SANG 2.4.76")
print("==========================================================")
print(f"Nguồn VTC: {vtc_res_dir}")
print(f"Đích:      {dst_dir}")

# 1. Sao lưu ResourceList.dat hiện tại nếu chưa sao lưu
if not backup_res_list.is_file() and res_list_p.is_file():
    shutil.copy2(res_list_p, backup_res_list)
    print(f"[OK] Đã sao lưu ResourceList.dat -> {backup_res_list.name}")

# 2. Đọc ResourceList.dat của VTC làm nền tảng
vtc_list_file = vtc_res_dir / "ResourceList.dat"
with open(vtc_list_file, "r", encoding="utf-8-sig") as f:
    vtc_data = json.load(f)

# Đọc ResourceList.dat của 2.4.76 hiện tại để giữ lại các bundle đặc thù (nếu có)
with open(res_list_p, "r", encoding="utf-8-sig") as f:
    p76_data = json.load(f)

vtc_bundles = vtc_data.get("bundles", {})
p76_bundles = p76_data.get("bundles", {})

merged_bundles = dict(vtc_bundles)

# Giữ lại các bundle chỉ có ở 2.4.76 (ví dụ map l11150 -> l11155)
kept_p76 = 0
for bname, binfo in p76_bundles.items():
    if bname not in merged_bundles:
        merged_bundles[bname] = binfo
        kept_p76 += 1
print(f"[OK] Giữ lại {kept_p76} bundles đặc thù của 2.4.76 (như các map mới)")

# 3. Copy TOÀN BỘ file .unity3d từ VTC/Resources sang 2.4.76/Android/Resources (BAO GỒM CẢ font.unity3d = UTM Cafeta)
print("[*] Đang copy toàn bộ file .unity3d từ VTC sang 2.4.76...")
t0 = time.time()
copied = 0
for f in vtc_res_dir.glob("*.unity3d"):
    target = dst_dir / f.name
    shutil.copy2(f, target)
    copied += 1

print(f"[OK] Đã copy thành công {copied} file .unity3d (bao gồm font.unity3d UTM Cafeta) trong {time.time()-t0:.2f}s")

# 4. Quét toàn bộ bundle, tính toán chính xác size và CRC32 thực tế trên đĩa
print("[*] Đang tính toán kích thước và CRC32 (zlib) cho toàn bộ bundles...")
t1 = time.time()
updated_count = 0
missing_count = 0

for bname, binfo in list(merged_bundles.items()):
    fn = bname + ".unity3d"
    fp = dst_dir / fn
    if fp.is_file():
        raw = fp.read_bytes()
        actual_size = len(raw)
        actual_crc = zlib.crc32(raw) & 0xffffffff
        if binfo.get("size") != actual_size or binfo.get("crc") != actual_crc:
            binfo["size"] = actual_size
            binfo["crc"] = actual_crc
            updated_count += 1
    else:
        missing_count += 1

vtc_data["bundles"] = merged_bundles

# Ghi lại ResourceList.dat chuẩn
with open(res_list_p, "w", encoding="utf-8") as f:
    json.dump(vtc_data, f, ensure_ascii=False, indent=2)

print(f"[OK] Hoàn thành tính toán CRC32 và ghi ResourceList.dat trong {time.time()-t1:.2f}s")
print(f"     - Tổng số bundles: {len(merged_bundles)}")
print(f"     - Số bundles cập nhật size/CRC: {updated_count}")
print(f"     - Số file thiếu: {missing_count}")
print(f"     - Font info: {merged_bundles.get('font')}")
print("==========================================================")
print("[THÀNH CÔNG] ĐỒNG BỘ 100% TÀI NGUYÊN VTC HOÀN TẤT!")
print("==========================================================")
