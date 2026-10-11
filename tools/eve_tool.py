#!/usr/bin/env python3
"""
eve_tool.py — Công cụ Đọc, Xem, Tra cứu & Chỉnh sửa File Eve.emg cho TS Mobile (tsm_dodo)
========================================================================================
Hỗ trợ:
  1. Trích xuất và nạp toàn bộ 3,823 bản đồ (scenes) trong file Eve.emg.
  2. Xem danh sách Cổng dịch chuyển (Doors) và đích đến (Target Scene, X, Y).
  3. Xem danh sách NPC và các sự kiện (NpcEvents) trên từng bản đồ.
  4. Phân tích điều kiện nhiệm vụ (Conditions) và kết quả (Add/Dec Step, Talk ID, Battle...).
  5. Tra cứu toàn cục (Global Search): Tìm nhiệm vụ hoặc NPC xuất hiện ở những map nào.
  6. Chỉnh sửa tọa độ đích của Cổng dịch chuyển và ghi đè an toàn (có sao lưu tự động).
  7. Giao diện đồ họa (GUI) trực quan bằng Tkinter.

Cách chạy:
  python tools/eve_tool.py          (Mở giao diện GUI)
  python tools/eve_tool.py --help   (Xem các lệnh dòng lệnh)
"""

import os
import sys
import struct
import shutil
import time
from pathlib import Path

# Đảm bảo UTF-8
if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

ROOT_DIR = Path(__file__).resolve().parent.parent

def find_eve_emg() -> Path | None:
    candidates = [
        ROOT_DIR / "gamedata" / "Eve.emg",
        ROOT_DIR / "cdn" / "mirror" / "tsr" / "2.4.76" / "ResourcesPacked" / "CompreseData" / "Eve.emg",
        ROOT_DIR / "cdn" / "mirror" / "tsr" / "2.7" / "ResourcesPacked" / "CompreseData" / "Eve.emg",
    ]
    for c in candidates:
        if c.is_file():
            return c
    return None

class EveArchive:
    """Quản lý container Eve.emg chứa 3,823 file .eve"""
    def __init__(self, filepath: Path = None):
        self.filepath = Path(filepath) if filepath else find_eve_emg()
        self.scenes = {} # scene_id -> {"name": str, "pos": int, "size": int}
        if self.filepath and self.filepath.is_file():
            self._load_index()

    def _load_index(self):
        with open(self.filepath, "rb") as f:
            raw_cnt = f.read(2)
            if len(raw_cnt) < 2:
                return
            count, = struct.unpack("<H", raw_cnt)
            for _ in range(count):
                nlen = f.read(1)[0]
                name_b = f.read(23)[:nlen]
                name = name_b.decode("latin1", errors="replace")
                pos, sz = struct.unpack("<ii", f.read(8))
                sid = int(name.replace(".eve", "")) if name.endswith(".eve") and name.replace(".eve", "").isdigit() else 0
                if sid > 0:
                    self.scenes[sid] = {"name": name, "pos": pos, "size": sz}

    def get_scene_raw_block(self, scene_id: int) -> bytes | None:
        info = self.scenes.get(scene_id)
        if not info:
            return None
        with open(self.filepath, "rb") as f:
            f.seek(info["pos"])
            return f.read(info["size"])

    def parse_scene(self, scene_id: int) -> dict | None:
        raw = self.get_scene_raw_block(scene_id)
        if not raw or len(raw) < 103:
            return None
        
        # Bỏ qua 103 bytes header
        data = raw[103:]
        pos = 0

        def read_u8():
            nonlocal pos
            val = data[pos]; pos += 1
            return val

        def read_u16():
            nonlocal pos
            val, = struct.unpack("<H", data[pos:pos+2]); pos += 2
            return val

        def read_i32():
            nonlocal pos
            val, = struct.unpack("<i", data[pos:pos+4]); pos += 4
            return val

        def read_bytes(n):
            nonlocal pos
            val = data[pos:pos+n]; pos += n
            return val

        res = {
            "scene_id": scene_id,
            "npcs": [],
            "doors": [],
            "scene_infos": {},
            "events": {},
            "raw_block": raw,
        }

        try:
            # 1. NpcData
            npc_cnt = read_i32()
            for _ in range(npc_cnt):
                nid = read_u16()
                npcid = read_u16()
                ecnt = read_u16()
                evs = [read_u8() for _ in range(ecnt)]
                sk_cnt = read_u8(); read_bytes(sk_cnt)
                mn_cnt = read_u8(); read_bytes((mn_cnt + 1) * 8)
                read_bytes(4); read_bytes(3); read_bytes(16); read_bytes(8)
                px = read_i32(); py = read_i32()
                read_bytes(6); read_bytes(16); read_bytes(16)
                read_bytes(1); tr = read_u16(); close = read_u8() != 0
                if nid > 0:
                    res["npcs"].append({
                        "id": nid, "npc_id": npcid, "events": evs, "x": px, "y": py, "close": close
                    })

            # 2. GoodsData (13B each)
            goods_cnt = read_u16()
            read_bytes(goods_cnt * 13)

            # 3. DoorData
            door_cnt = read_u16()
            for _ in range(door_cnt):
                did = read_u16()
                decnt = read_u16()
                devs = [read_u8() for _ in range(decnt)]
                gx = read_i32(); gy = read_i32(); gw = read_i32(); gh = read_i32()
                read_bytes(1); read_bytes(4); read_bytes(1)
                dx = (gx + gw // 2) * 20
                dy = (gy + gh // 2) * 20
                if did > 0:
                    res["doors"].append({
                        "id": did, "events": devs, "x": dx, "y": dy
                    })

            # 4. MineData
            mine_cnt = read_u16()
            for _ in range(mine_cnt):
                read_bytes(2)
                mc = read_u16()
                read_bytes(mc + 16 + 1)

            # 5. SurfaceData
            surf_cnt = read_u16()
            for _ in range(surf_cnt):
                read_bytes(4)
                scnt = read_u8(); read_bytes(3)
                read_bytes(scnt * 5)

            # 6. SceneInfoData (39B each) - Đích đến của Door Teleport
            sc_info_cnt = read_u16()
            for _ in range(sc_info_cnt):
                offset_in_block = 103 + pos
                eve_no = read_u16()
                bg_no = read_u16()
                read_bytes(2) # eveFileName
                app_x = read_i32()
                app_y = read_i32()
                dir_ = read_u8()
                area_name = read_bytes(21).split(b"\x00")[0].decode("latin1", errors="replace")
                read_bytes(3)
                if eve_no > 0:
                    res["scene_infos"][eve_no] = {
                        "eve_no": eve_no,
                        "target_scene": bg_no,
                        "target_x": app_x * 20,
                        "target_y": app_y * 20,
                        "raw_app_x": app_x,
                        "raw_app_y": app_y,
                        "direction": dir_,
                        "name": area_name,
                        "block_offset": offset_in_block # Lưu vị trí để sửa trực tiếp nếu cần
                    }

            # 7. GroupData
            grp_cnt = read_u16()
            for _ in range(grp_cnt):
                read_bytes(4); read_bytes(2)
                mnum = read_u8(); read_bytes(mnum * 2); read_bytes(1)

            # 8. NpcEventData
            ev_cnt = read_u16()
            for _ in range(ev_cnt):
                eve_no = read_u16()
                ev_name = read_bytes(9).split(b"\x00")[0].decode("latin1", errors="replace")
                when_happen = [read_u8() != 0 for _ in range(4)]
                cond_cnt = read_u8()
                conditions = []
                for _ in range(cond_cnt):
                    c_no = read_u8()
                    c_class = read_u8()
                    c_param = read_u16()
                    c_pstyle = read_u8()
                    c_ops = read_u8()
                    c_val = read_i32()
                    c_time_value = struct.unpack("<d", read_bytes(8))[0] # DVT ReadDouble()
                    to_res = read_u8()
                    and_num = read_u8()
                    sub_item = read_u16()

                    rcnt = read_u8()
                    results = []
                    for _ in range(rcnt):
                        r_grp = read_u16()
                        r_no = read_u8()
                        r_type = read_u8()
                        r_class = read_u8()
                        r_param = read_u16()
                        r_pstyle = read_u8()
                        r_val = read_i32()
                        r_mean = read_u16()
                        results.append({
                            "type": r_type, "class": r_class, "param": r_param,
                            "pstyle": r_pstyle, "val": r_val, "mean_no": r_mean
                        })

                    conditions.append({
                        "no": c_no, "class": c_class, "param": c_param,
                        "pstyle": c_pstyle, "ops": c_ops, "val": c_val,
                        "to_result": to_res, "and_num": and_num,
                        "time_value": c_time_value, "sub_item": sub_item,
                        "results": results
                    })

                res["events"][eve_no] = {
                    "eve_no": eve_no, "name": ev_name, "when_happen": when_happen, "conditions": conditions
                }
        except Exception:
            pass

        return res

    def search_mission(self, mission_id: int) -> list[dict]:
        """Tìm kiếm xem nhiệm vụ mission_id xuất hiện ở những bản đồ và sự kiện nào."""
        results = []
        for sid in sorted(self.scenes.keys()):
            sc = self.parse_scene(sid)
            if not sc:
                continue
            for eve_no, ev in sc["events"].items():
                found_conds = []
                found_results = []
                for c in ev["conditions"]:
                    if c["class"] == 2 and c["param"] == mission_id:
                        found_conds.append(c)
                    for r in c["results"]:
                        if r["class"] == 2 and r["param"] == mission_id:
                            found_results.append(r)
                if found_conds or found_results:
                    # Tìm NPC nào sở hữu event này
                    linked_npcs = [npc for npc in sc["npcs"] if eve_no in npc["events"]]
                    results.append({
                        "scene_id": sid,
                        "eve_no": eve_no,
                        "event_name": ev["name"],
                        "npcs": linked_npcs,
                        "conditions": found_conds,
                        "results": found_results
                    })
        return results

    def update_door_scene_info(self, scene_id: int, eve_no: int, target_scene: int, target_x: int, target_y: int) -> bool:
        """Cập nhật tọa độ đích của một SceneInfo trong Eve.emg."""
        sc = self.parse_scene(scene_id)
        if not sc or eve_no not in sc["scene_infos"]:
            return False
        
        info = sc["scene_infos"][eve_no]
        block_offset = info["block_offset"]
        entry = self.scenes.get(scene_id)
        if not entry:
            return False

        # Tự động sao lưu Eve.emg trước khi sửa
        backup_path = self.filepath.with_suffix(f".bak_{time.strftime('%Y%m%d_%H%M%S')}")
        if not backup_path.exists():
            shutil.copy2(self.filepath, backup_path)

        # Tính toán giá trị raw: target_x và target_y chia 20
        raw_x = target_x // 20
        raw_y = target_y // 20

        # Ghi trực tiếp vào file tại byte offset
        # Cấu trúc: eveNo(2B) + bgNo(2B) + eveFileName(2B) + playerAppearX(4B) + playerAppearY(4B)
        abs_pos = entry["pos"] + block_offset
        with open(self.filepath, "r+b") as f:
            f.seek(abs_pos + 2) # Bỏ qua eveNo 2 bytes
            f.write(struct.pack("<H", target_scene)) # Cập nhật target scene
            f.seek(abs_pos + 6) # Đến playerAppearX
            f.write(struct.pack("<ii", raw_x, raw_y)) # Cập nhật X, Y
        
        return True


# ============================================================================
# GIAO DIỆN ĐỒ HỌA (GUI)
# ============================================================================
def launch_gui():
    import tkinter as tk
    from tkinter import ttk, messagebox

    # Nạp dữ liệu tên bản đồ từ TSM_Setup nếu có
    scenes_map = {}
    try:
        from TSM_Setup import GameDataCache
        scenes_map = GameDataCache.get_scenes()
    except Exception:
        pass

    archive = EveArchive()

    root = tk.Tk()
    root.title("TSM Eve.emg Editor & Inspector — Quản lý Sự kiện & Cổng Game")
    root.geometry("1100x750")
    root.minsize(950, 650)

    # Style
    style = ttk.Style(root)
    try:
        style.theme_use("clam")
    except Exception:
        pass

    # Top Header
    header = tk.Frame(root, bg="#107C41", height=50)
    header.pack(fill="x", side="top")
    tk.Label(
        header, text="TSM Eve.emg Editor & Inspector (Hệ Thống Sự Kiện & Cổng Teleport)",
        font=("Segoe UI", 13, "bold"), fg="white", bg="#107C41"
    ).pack(side="left", padx=16, pady=8)

    file_lbl = tk.Label(
        header, text=f"File: {archive.filepath.name} ({len(archive.scenes)} bản đồ)" if archive.filepath else "Không tìm thấy Eve.emg",
        font=("Segoe UI", 9), fg="#E0E0E0", bg="#107C41"
    )
    file_lbl.pack(side="right", padx=16, pady=8)

    # Main Notebook
    notebook = ttk.Notebook(root)
    notebook.pack(fill="both", expand=True, padx=12, pady=10)

    # ────────────────────────────────────────────────────────────────
    # TAB 1: BẢN ĐỒ & CỔNG DỊCH CHUYỂN (DOORS & TELEPORT)
    # ────────────────────────────────────────────────────────────────
    tab_scene = ttk.Frame(notebook, padding=8)
    notebook.add(tab_scene, text="[1] Bản đồ & Cổng Teleport")

    # Map Selector Bar
    map_bar = ttk.Frame(tab_scene)
    map_bar.pack(fill="x", pady=(0, 8))

    ttk.Label(map_bar, text="Chọn Bản đồ:", font=("Segoe UI", 10, "bold")).pack(side="left", padx=(0, 6))
    map_search_var = tk.StringVar(value="10851")
    map_search_ent = ttk.Entry(map_bar, textvariable=map_search_var, width=12)
    map_search_ent.pack(side="left", padx=(0, 6))

    map_title_lbl = tk.Label(
        map_bar, text="[10851] Làng Tân Thủ", font=("Segoe UI", 10, "bold"), fg="#005A9E", padx=8, relief="groove"
    )
    map_title_lbl.pack(side="left", padx=(0, 10))

    def on_load_scene():
        raw_val = map_search_var.get().strip()
        if not raw_val.isdigit():
            messagebox.showwarning("Thông báo", "Vui lòng nhập Map ID là số.")
            return
        sid = int(raw_val)
        if sid not in archive.scenes:
            messagebox.showerror("Không tìm thấy", f"Map ID {sid} không có trong file Eve.emg!")
            return
        sname = scenes_map.get(sid, f"Bản đồ {sid}")
        map_title_lbl.config(text=f"[{sid}] {sname}")
        load_scene_details(sid)

    ttk.Button(map_bar, text="🔍 Tải Bản đồ", command=on_load_scene).pack(side="left", padx=(0, 6))

    # Split: Left Doors, Right SceneInfos
    paned_door = ttk.PanedWindow(tab_scene, orient="horizontal")
    paned_door.pack(fill="both", expand=True)

    # Left: Door list
    door_box = ttk.LabelFrame(paned_door, text=" Danh sách Cổng Teleport (DoorData) ", padding=6)
    paned_door.add(door_box, weight=1)

    door_tree = ttk.Treeview(door_box, columns=("id", "coords", "events"), show="headings", selectmode="browse")
    door_tree.heading("id", text="Door ID")
    door_tree.heading("coords", text="Tọa độ Cổng (X, Y)")
    door_tree.heading("events", text="Event IDs")
    door_tree.column("id", width=60, anchor="center")
    door_tree.column("coords", width=120, anchor="center")
    door_tree.column("events", width=100, anchor="center")
    door_tree.pack(side="left", fill="both", expand=True)
    d_sc = ttk.Scrollbar(door_box, orient="vertical", command=door_tree.yview)
    door_tree.configure(yscrollcommand=d_sc.set)
    d_sc.pack(side="right", fill="y")

    # Right: SceneInfo (Teleport destination details)
    info_box = ttk.LabelFrame(paned_door, text=" Đích đến dịch chuyển (SceneInfoData) ", padding=6)
    paned_door.add(info_box, weight=2)

    info_tree = ttk.Treeview(
        info_box, columns=("eve_no", "target_scene", "target_coords", "name"), show="headings", selectmode="browse"
    )
    info_tree.heading("eve_no", text="EveNo (Key)")
    info_tree.heading("target_scene", text="Map Đích")
    info_tree.heading("target_coords", text="Tọa độ Đích (X, Y)")
    info_tree.heading("name", text="Tên Vị Trí")
    info_tree.column("eve_no", width=80, anchor="center")
    info_tree.column("target_scene", width=160, anchor="w")
    info_tree.column("target_coords", width=130, anchor="center")
    info_tree.column("name", width=140, anchor="w")
    info_tree.pack(side="left", fill="both", expand=True)
    i_sc = ttk.Scrollbar(info_box, orient="vertical", command=info_tree.yview)
    info_tree.configure(yscrollcommand=i_sc.set)
    i_sc.pack(side="right", fill="y")

    # Form chỉnh sửa cổng
    edit_box = ttk.LabelFrame(tab_scene, text=" Chỉnh sửa Đích đến của Cổng đang chọn ", padding=6)
    edit_box.pack(fill="x", pady=(8, 0))

    e_row = ttk.Frame(edit_box)
    e_row.pack(fill="x")

    ttk.Label(e_row, text="EveNo:").pack(side="left", padx=(0, 4))
    e_eve_var = tk.StringVar()
    ttk.Entry(e_row, textvariable=e_eve_var, width=6, state="readonly").pack(side="left", padx=(0, 10))

    ttk.Label(e_row, text="Map Đích ID:").pack(side="left", padx=(0, 4))
    e_scene_var = tk.StringVar()
    ttk.Entry(e_row, textvariable=e_scene_var, width=8).pack(side="left", padx=(0, 10))

    ttk.Label(e_row, text="X Đích:").pack(side="left", padx=(0, 4))
    e_x_var = tk.StringVar()
    ttk.Entry(e_row, textvariable=e_x_var, width=6).pack(side="left", padx=(0, 10))

    ttk.Label(e_row, text="Y Đích:").pack(side="left", padx=(0, 4))
    e_y_var = tk.StringVar()
    ttk.Entry(e_row, textvariable=e_y_var, width=6).pack(side="left", padx=(0, 14))

    def on_save_door():
        sid_raw = map_search_var.get().strip()
        eve_raw = e_eve_var.get().strip()
        ts_raw = e_scene_var.get().strip()
        tx_raw = e_x_var.get().strip()
        ty_raw = e_y_var.get().strip()
        if not (sid_raw.isdigit() and eve_raw.isdigit() and ts_raw.isdigit() and tx_raw.isdigit() and ty_raw.isdigit()):
            messagebox.showwarning("Thông báo", "Vui lòng chọn cổng và nhập số hợp lệ.")
            return
        
        sid = int(sid_raw)
        eve_no = int(eve_raw)
        ts = int(ts_raw)
        tx = int(tx_raw)
        ty = int(ty_raw)

        if not messagebox.askyesno("Xác nhận", f"Cập nhật đích đến của EveNo={eve_no} trong Map {sid} thành:\nMap {ts} (X:{tx}, Y:{ty})?\n(Hệ thống sẽ tự động tạo bản sao lưu .bak)"):
            return

        ok = archive.update_door_scene_info(sid, eve_no, ts, tx, ty)
        if ok:
            messagebox.showinfo("Thành công", "Đã cập nhật file Eve.emg thành công!")
            load_scene_details(sid)
        else:
            messagebox.showerror("Lỗi", "Không thể cập nhật SceneInfo.")

    ttk.Button(e_row, text="💾 Lưu vào Eve.emg", command=on_save_door).pack(side="left")

    def on_select_info(event=None):
        sel = info_tree.selection()
        if not sel: return
        vals = info_tree.item(sel[0], "values")
        if vals:
            e_eve_var.set(str(vals[0]))
            # parse map id from "[10901]..."
            ts_str = str(vals[1]).split("]")[0].replace("[", "") if "[" in str(vals[1]) else str(vals[1])
            e_scene_var.set(ts_str)
            coords = str(vals[2]).split(",")
            if len(coords) == 2:
                e_x_var.set(coords[0].strip())
                e_y_var.set(coords[1].strip())

    info_tree.bind("<<TreeviewSelect>>", on_select_info)

    def load_scene_details(sid: int):
        for i in door_tree.get_children(): door_tree.delete(i)
        for i in info_tree.get_children(): info_tree.delete(i)
        sc = archive.parse_scene(sid)
        if not sc: return
        for d in sc["doors"]:
            door_tree.insert("", "end", values=(d["id"], f"{d['x']}, {d['y']}", ",".join(map(str, d["events"]))))
        for k, inf in sc["scene_infos"].items():
            ts_id = inf["target_scene"]
            ts_name = scenes_map.get(ts_id, f"Map {ts_id}")
            info_tree.insert("", "end", values=(
                inf["eve_no"], f"[{ts_id}] {ts_name}", f"{inf['target_x']}, {inf['target_y']}", inf["name"]
            ))

    # ────────────────────────────────────────────────────────────────
    # TAB 2: TRA CỨU NHIỆM VỤ & SỰ KIỆN (QUEST INSPECTOR)
    # ────────────────────────────────────────────────────────────────
    tab_quest = ttk.Frame(notebook, padding=8)
    notebook.add(tab_quest, text="[2] Phân tích Nhiệm vụ Toàn Cục")

    q_top = ttk.Frame(tab_quest)
    q_top.pack(fill="x", pady=(0, 8))

    ttk.Label(q_top, text="Nhập ID Nhiệm vụ cần tra cứu:", font=("Segoe UI", 10, "bold")).pack(side="left", padx=(0, 6))
    q_search_var = tk.StringVar(value="10002")
    q_search_ent = ttk.Entry(q_top, textvariable=q_search_var, width=12)
    q_search_ent.pack(side="left", padx=(0, 8))

    q_status_lbl = ttk.Label(q_top, text="Sẵn sàng tìm kiếm", foreground="#666666")
    q_status_lbl.pack(side="right")

    q_tree_frame = ttk.Frame(tab_quest)
    q_tree_frame.pack(fill="both", expand=True)

    q_cols = ("scene", "eve_no", "npc", "type", "details")
    q_tree = ttk.Treeview(q_tree_frame, columns=q_cols, show="headings")
    q_tree.heading("scene", text="Bản đồ")
    q_tree.heading("eve_no", text="EveNo")
    q_tree.heading("npc", text="NPC liên quan")
    q_tree.heading("type", text="Loại tác động")
    q_tree.heading("details", text="Chi tiết điều kiện / kết quả")

    q_tree.column("scene", width=160)
    q_tree.column("eve_no", width=60, anchor="center")
    q_tree.column("npc", width=140)
    q_tree.column("type", width=120, anchor="center")
    q_tree.column("details", width=450)

    q_tree.pack(side="left", fill="both", expand=True)
    q_sc = ttk.Scrollbar(q_tree_frame, orient="vertical", command=q_tree.yview)
    q_tree.configure(yscrollcommand=q_sc.set)
    q_sc.pack(side="right", fill="y")

    def on_search_mission():
        raw = q_search_var.get().strip()
        if not raw.isdigit():
            messagebox.showwarning("Thông báo", "Vui lòng nhập Mission ID là số.")
            return
        mid = int(raw)
        q_status_lbl.config(text=f"Đang quét toàn bộ 3,823 map trong Eve.emg...")
        root.update_idletasks()

        for i in q_tree.get_children(): q_tree.delete(i)

        matches = archive.search_mission(mid)
        for m in matches:
            sid = m["scene_id"]
            sname = scenes_map.get(sid, f"Map {sid}")
            npc_text = ", ".join([f"NPC {n['npc_id']}" for n in m["npcs"]]) if m["npcs"] else "Trigger tự do"
            
            # Liệt kê điều kiện
            for c in m["conditions"]:
                q_tree.insert("", "end", values=(
                    f"[{sid}] {sname}", m["eve_no"], npc_text, "Điều kiện kiểm tra",
                    f"Kiểm tra bước {c['ops']} {c['val']} (pStyle={c['pstyle']})"
                ))

            # Liệt kê kết quả
            for r in m["results"]:
                action = "Tăng bước (+)" if r["pstyle"] == 1 else ("Giảm/hoàn thành" if r["pstyle"] == 2 else "Hoàn tất")
                q_tree.insert("", "end", values=(
                    f"[{sid}] {sname}", m["eve_no"], npc_text, f"Kết quả: {action}",
                    f"ResultType={r['type']}, Value={r['val']}, MeanNo={r['mean_no']}"
                ))

        q_status_lbl.config(text=f"Tìm thấy {len(matches)} mắt xích liên quan đến nhiệm vụ {mid}.")

    ttk.Button(q_top, text="🔍 Phân tích Lộ trình Nhiệm vụ", command=on_search_mission).pack(side="left")

    # Nạp bản đồ ban đầu
    on_load_scene()

    root.mainloop()

if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "--search":
        mid = int(sys.argv[2])
        arc = EveArchive()
        res = arc.search_mission(mid)
        print(f"Found {len(res)} matches for Mission {mid}")
        for r in res:
            print(f"  Scene {r['scene_id']} EveNo {r['eve_no']}: {r['conditions']} -> {r['results']}")
    else:
        launch_gui()
