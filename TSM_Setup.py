#!/usr/bin/env python3
"""
TSM_Setup.py — All-in-One Tool cho TS Mobile Online (tsm_dodo)
=============================================================================
GỘP TẤT CẢ VÀO 1 FILE DUY NHẤT:
  1. Auto-Setup: Tự check & cài JDK 21 + Python + MySQL (mỗi bước hỏi đồng ý)
  2. Control Panel: Quản lý CDN / Game Server / ADB Reverse
  3. Cấu hình môi trường (tự quét JDK, ADB trên mọi ổ đĩa)
  4. Công cụ Lua (đổi IP Network.lua)
  5. MySQL Database (tạo DB + import SQL)
  6. APK IP/Port Editor (đổi IP + Port trong file APK)

Cách chạy:  python TSM_Setup.py
            (hoặc double-click TSM_Setup.bat)
=============================================================================
"""

import argparse
import ctypes
import hashlib
import json
import os
import queue
import re
import shutil
import socket
import string
import struct
import subprocess
import sys
import tempfile
import threading
import time
import tkinter as tk
import zipfile
from tkinter import ttk, filedialog, messagebox, simpledialog
from pathlib import Path
from urllib.request import urlretrieve, Request, urlopen

# ============================================================================
# Bật UTF-8 console trên Windows
# ============================================================================
if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

# ============================================================================
# Hằng số
# ============================================================================
ROOT_DIR = Path(__file__).resolve().parent
CONFIG_FILE = ROOT_DIR / "launcher_config.json"

# ============================================================================
# Hàm tiện ích
# ============================================================================
def is_admin() -> bool:
    try:
        return ctypes.windll.shell32.IsUserAnAdmin()
    except Exception:
        return False


def run_as_admin(script_path: str, extra_args: list = None):
    """Khởi động lại script hiện tại với quyền Admin."""
    params = f'"{script_path}"'
    if extra_args:
        params += " " + " ".join(extra_args)
    ctypes.windll.shell32.ShellExecuteW(None, "runas", sys.executable, params, None, 1)


def get_local_lan_ip() -> str:
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            s.connect(("8.8.8.8", 80))
            return s.getsockname()[0]
    except Exception:
        return "127.0.0.1"


def check_port_listening(port: int, host: str = "127.0.0.1", timeout: float = 0.5) -> bool:
    """Kiểm tra một cổng TCP có đang mở/lắng nghe hay không (hỗ trợ cả IPv4 và IPv6)."""
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.settimeout(timeout)
            if s.connect_ex((host, port)) == 0:
                return True
    except Exception:
        pass
    if host in ("127.0.0.1", "localhost"):
        try:
            with socket.socket(socket.AF_INET6, socket.SOCK_STREAM) as s6:
                s6.settimeout(timeout)
                if s6.connect_ex(("::1", port)) == 0:
                    return True
        except Exception:
            pass
    return False


def kill_process_tree(pid: int) -> bool:
    """Diệt tiến trình và toàn bộ cây tiến trình con triệt để bằng taskkill."""
    if pid <= 4:
        return False
    try:
        creationflags = getattr(subprocess, "CREATE_NO_WINDOW", 0)
        res = subprocess.run(
            ["taskkill", "/F", "/T", "/PID", str(pid)],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            creationflags=creationflags,
            check=False,
        )
        return res.returncode == 0
    except Exception:
        return False


def stop_listeners_on_ports(ports: set[int]) -> list[int]:
    """Dừng cây tiến trình đang LISTEN trên các cổng được chỉ định."""
    if sys.platform != "win32" or not ports:
        return []
    pids: set[int] = set()
    creationflags = getattr(subprocess, "CREATE_NO_WINDOW", 0)
    str_ports = {str(p) for p in ports}

    # 1. Quét qua netstat -ano -p tcp
    try:
        output = subprocess.run(
            ["netstat", "-ano", "-p", "tcp"],
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            text=True,
            encoding="utf-8",
            errors="replace",
            creationflags=creationflags,
            check=False,
        ).stdout
        for line in output.splitlines():
            columns = line.split()
            if len(columns) < 4:
                continue
            local_address = columns[1]
            port_part = local_address.rsplit(":", 1)[-1]
            if port_part in str_ports:
                try:
                    pid = int(columns[-1])
                    if pid > 4:
                        pids.add(pid)
                except ValueError:
                    pass
    except Exception:
        pass

    # 2. Dự phòng bằng PowerShell Get-NetTCPConnection nếu netstat bị sót
    if not pids:
        port_filter = ",".join(str_ports)
        ps_cmd = f"Get-NetTCPConnection -LocalPort {port_filter} -State Listen -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess"
        try:
            res = subprocess.run(
                ["powershell", "-NoProfile", "-Command", ps_cmd],
                stdout=subprocess.PIPE,
                stderr=subprocess.DEVNULL,
                text=True,
                creationflags=creationflags,
                timeout=4,
                check=False,
            )
            for line in res.stdout.splitlines():
                line = line.strip()
                if line.isdigit() and int(line) > 4:
                    pids.add(int(line))
        except Exception:
            pass

    for pid in sorted(pids):
        kill_process_tree(pid)

    return sorted(pids)


def kill_processes_by_commandline(patterns: list[str]) -> list[int]:
    """Tìm và diệt tất cả các tiến trình có dòng lệnh (CommandLine) chứa các từ khóa."""
    if sys.platform != "win32" or not patterns:
        return []
    creationflags = getattr(subprocess, "CREATE_NO_WINDOW", 0)
    killed_pids: set[int] = set()
    conditions = " -or ".join([f"$_.CommandLine -like '*{p}*'" for p in patterns])
    ps_cmd = (
        f"$cur = [System.Diagnostics.Process]::GetCurrentProcess().Id; "
        f"Get-CimInstance Win32_Process | "
        f"Where-Object {{ ($_.ProcessId -ne $cur) -and ($_.ProcessId -gt 4) -and ({conditions}) }} | "
        f"Select-Object -ExpandProperty ProcessId"
    )
    try:
        res = subprocess.run(
            ["powershell", "-NoProfile", "-Command", ps_cmd],
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            text=True,
            creationflags=creationflags,
            timeout=6,
            check=False,
        )
        for line in res.stdout.splitlines():
            line = line.strip()
            if line.isdigit() and int(line) > 4:
                pid = int(line)
                if kill_process_tree(pid):
                    killed_pids.add(pid)
    except Exception:
        pass
    return sorted(killed_pids)


class MySqlExecutor:
    """Thực thi câu lệnh SQL với hỗ trợ kép (ưu tiên pymysql, tự động fallback mysql.exe)."""

    @classmethod
    def get_cfg(cls) -> dict:
        cfg = ConfigManager.load()
        host = cfg.get("mysql_host", "127.0.0.1")
        try:
            port = int(cfg.get("mysql_port", 3307))
        except (ValueError, TypeError):
            port = 3307
        user = cfg.get("mysql_user", "root")
        pwd = cfg.get("mysql_password", "")
        db = cfg.get("mysql_database", "tsm_dodo")
        return {
            "host": host,
            "port": port,
            "user": user,
            "password": pwd,
            "database": db,
        }

    @classmethod
    def query(cls, sql: str, params: tuple = None) -> list[tuple]:
        info = cls.get_cfg()
        # 1. Thử qua pymysql
        try:
            import pymysql
            conn = pymysql.connect(
                host=info["host"],
                port=info["port"],
                user=info["user"],
                password=info["password"],
                database=info["database"],
                charset="utf8mb4",
                connect_timeout=3
            )
            with conn.cursor() as cur:
                cur.execute(sql, params)
                rows = cur.fetchall()
            conn.close()
            return rows
        except Exception:
            pass

        # 2. Dự phòng bằng mysql.exe client
        mysql_exe = find_mysql_client()
        if not mysql_exe:
            for extra in [
                r"C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe",
                r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe",
                r"C:\xampp\mysql\bin\mysql.exe"
            ]:
                if os.path.isfile(extra):
                    mysql_exe = extra
                    break

        if not mysql_exe:
            raise RuntimeError("Chưa cài đặt thư viện pymysql và không tìm thấy file mysql.exe client.")

        exec_sql = sql
        if params:
            for p in params:
                if isinstance(p, str):
                    escaped = p.replace("'", "''")
                    exec_sql = exec_sql.replace("%s", f"'{escaped}'", 1)
                else:
                    exec_sql = exec_sql.replace("%s", str(p), 1)

        cmd = [
            mysql_exe,
            "-h", info["host"],
            "-P", str(info["port"]),
            "-u", info["user"],
            "-D", info["database"],
            "--batch",
            "--skip-column-names",
            "-e", exec_sql
        ]
        env = os.environ.copy()
        if info["password"]:
            env["MYSQL_PWD"] = str(info["password"])

        creationflags = getattr(subprocess, "CREATE_NO_WINDOW", 0)
        res = subprocess.run(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            encoding="utf-8",
            errors="replace",
            env=env,
            creationflags=creationflags,
            timeout=10,
            check=False
        )
        if res.returncode != 0:
            raise RuntimeError(res.stderr.strip() or f"mysql exit code {res.returncode}")

        rows = []
        for line in res.stdout.splitlines():
            line = line.strip()
            if line:
                rows.append(tuple(line.split("\t")))
        return rows

    @classmethod
    def execute(cls, sql: str, params: tuple = None) -> int:
        info = cls.get_cfg()
        try:
            import pymysql
            conn = pymysql.connect(
                host=info["host"],
                port=info["port"],
                user=info["user"],
                password=info["password"],
                database=info["database"],
                charset="utf8mb4",
                connect_timeout=3
            )
            with conn.cursor() as cur:
                affected = cur.execute(sql, params)
            conn.commit()
            conn.close()
            return affected
        except Exception:
            pass

        # Fallback bằng query mysql.exe
        cls.query(sql, params)
        return 1


class GameDataCache:
    """Nạp và lưu bộ đệm tài nguyên dữ liệu trò chơi (Mark_C.dat, Warp_C.dat, SceneSet_C.dat, TextData_C.dat)."""
    _marks = None
    _scenes = None
    _warps = None
    _text_data = None
    _npc_names = None

    @classmethod
    def get_data_dir(cls) -> Path | None:
        for ver in ["2.4.76", "2.7", "2.4.69"]:
            p = ROOT_DIR / "cdn" / "mirror" / "tsr" / ver / "ResourcesPacked" / "Data"
            if (p / "Mark_C.dat").is_file():
                return p
        for p in ROOT_DIR.glob("**/ResourcesPacked/Data"):
            if (p / "Mark_C.dat").is_file():
                return p
        return None

    @classmethod
    def get_marks(cls) -> dict:
        if cls._marks is not None:
            return cls._marks
        d = cls.get_data_dir()
        if not d or not (d / "Mark_C.dat").is_file():
            cls._marks = {}
            return cls._marks
        marks = {}
        try:
            with open(d / "Mark_C.dat", "rb") as f:
                cb = f.read(4)
                if len(cb) == 4:
                    count, = struct.unpack("<i", cb)
                    for _ in range(count):
                        lb = f.read(2)
                        if len(lb) < 2:
                            break
                        l_name, = struct.unpack("<H", lb)
                        name = f.read(l_name).decode("utf-16le", errors="replace") if l_name else ""
                        meta_b = f.read(6)
                        if len(meta_b) < 6:
                            break
                        kind, qid, bit_id, gain_way = struct.unpack("<BHHB", meta_b)
                        ldb = f.read(2)
                        if len(ldb) < 2:
                            break
                        l_desc, = struct.unpack("<H", ldb)
                        desc = f.read(l_desc).decode("utf-16le", errors="replace") if l_desc else ""
                        if qid != 0:
                            marks[qid] = {
                                "id": qid,
                                "name": name,
                                "desc": desc,
                                "kind": kind,
                                "bit_id": bit_id,
                            }
        except Exception:
            pass
        cls._marks = marks
        return cls._marks

    @classmethod
    def get_text_data(cls) -> dict:
        if cls._text_data is not None:
            return cls._text_data
        d = cls.get_data_dir()
        if not d or not (d / "TextData_C.dat").is_file():
            cls._text_data = {}
            return cls._text_data
        texts = {}
        try:
            with open(d / "TextData_C.dat", "rb") as f:
                cb = f.read(4)
                if len(cb) == 4:
                    count, = struct.unpack("<i", cb)
                    for _ in range(count):
                        hdr = f.read(6)
                        if len(hdr) < 6:
                            break
                        k, length = struct.unpack("<IH", hdr)
                        texts[k] = f.read(length).decode("utf-16le", errors="replace")
        except Exception:
            pass
        cls._text_data = texts
        return cls._text_data

    @classmethod
    def get_scenes(cls) -> dict:
        if cls._scenes is not None:
            return cls._scenes
        d = cls.get_data_dir()
        if not d or not (d / "SceneSet_C.dat").is_file():
            cls._scenes = {}
            return cls._scenes
        text_data = cls.get_text_data()
        scenes = {}
        try:
            with open(d / "SceneSet_C.dat", "rb") as f:
                cb = f.read(4)
                if len(cb) == 4:
                    count, = struct.unpack("<i", cb)
                    for _ in range(count):
                        raw = f.read(17)
                        if len(raw) < 17:
                            break
                        name_id, scene_id, kind, max_player, l1, l2, eff, min_lv, max_lv, view, sub, l3 = struct.unpack("<IHBHBBBBBBBB", raw)
                        if scene_id != 0:
                            scenes[scene_id] = text_data.get(name_id, f"Bản đồ {scene_id}")
        except Exception:
            pass
        cls._scenes = scenes
        return cls._scenes

    @classmethod
    def get_warps(cls) -> list:
        if cls._warps is not None:
            return cls._warps
        d = cls.get_data_dir()
        if not d or not (d / "Warp_C.dat").is_file():
            cls._warps = []
            return cls._warps
        text_data = cls.get_text_data()
        scenes = cls.get_scenes()
        marks = cls.get_marks()
        warps = []
        try:
            with open(d / "Warp_C.dat", "rb") as f:
                cb = f.read(4)
                if len(cb) == 4:
                    count, = struct.unpack("<i", cb)
                    for i in range(count):
                        raw = f.read(16)
                        if len(raw) < 16:
                            break
                        name_id, scene, mark, x, y = struct.unpack("<IHHii", raw)
                        wname = text_data.get(name_id, f"Cổng {i + 1}")
                        sname = scenes.get(scene, f"Bản đồ {scene}")
                        bit_id = marks.get(mark, {}).get("bit_id", 0)
                        warps.append({
                            "index": i,
                            "name": wname,
                            "scene_id": scene,
                            "scene_name": sname,
                            "mark": mark,
                            "bit_id": bit_id,
                            "x": x,
                            "y": y,
                        })
        except Exception:
            pass
        cls._warps = warps
        return cls._warps

    @classmethod
    def get_npc_names(cls) -> dict:
        if cls._npc_names is not None:
            return cls._npc_names
        d = cls.get_data_dir()
        if not d or not (d / "Npc_C.dat").is_file():
            cls._npc_names = {}
            return cls._npc_names
        names = {}
        try:
            with open(d / "Npc_C.dat", "rb") as f:
                cnt, = struct.unpack("<i", f.read(4))
                for _ in range(cnt):
                    l_b = f.read(2)
                    if len(l_b) < 2:
                        break
                    l, = struct.unpack("<H", l_b)
                    name = f.read(l).decode("utf-16le", errors="replace") if l else ""
                    kind, nid = struct.unpack("<BH", f.read(3))
                    if nid != 0:
                        names[nid] = name
                    f.read(78)
        except Exception:
            pass
        cls._npc_names = names
        return cls._npc_names


class EveArchive:
    """Quản lý và đọc/ghi container Eve.emg chứa 3,823 file .eve (Sự kiện, Cổng & NPC)."""
    @classmethod
    def find_file(cls) -> Path | None:
        candidates = [
            ROOT_DIR / "gamedata" / "Eve.emg",
            ROOT_DIR / "cdn" / "mirror" / "tsr" / "2.4.76" / "ResourcesPacked" / "CompreseData" / "Eve.emg",
            ROOT_DIR / "cdn" / "mirror" / "tsr" / "2.7" / "ResourcesPacked" / "CompreseData" / "Eve.emg",
        ]
        for c in candidates:
            if c.is_file():
                return c
        return None

    def __init__(self, filepath: Path = None):
        self.filepath = Path(filepath) if filepath else self.find_file()
        self.scenes = {}  # scene_id -> {"name": str, "pos": int, "size": int}
        if self.filepath and self.filepath.is_file():
            self._load_index()

    def _load_index(self):
        try:
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
        except Exception:
            pass

    def get_scene_raw_block(self, scene_id: int) -> bytes | None:
        info = self.scenes.get(scene_id)
        if not info:
            return None
        try:
            with open(self.filepath, "rb") as f:
                f.seek(info["pos"])
                return f.read(info["size"])
        except Exception:
            return None

    def parse_scene(self, scene_id: int) -> dict | None:
        raw = self.get_scene_raw_block(scene_id)
        if not raw or len(raw) < 103:
            return None
        
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

            # 2. GoodsData
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

            # 6. SceneInfoData (Đích đến Door Teleport)
            sc_info_cnt = read_u16()
            for _ in range(sc_info_cnt):
                offset_in_block = 103 + pos
                eve_no = read_u16()
                bg_no = read_u16()
                read_bytes(2)
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
                        "direction": dir_,
                        "name": area_name,
                        "block_offset": offset_in_block
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
                    cond_offset = 103 + pos
                    c_no = read_u8()
                    c_class = read_u8()
                    c_param = read_u16()
                    c_pstyle = read_u8()
                    c_ops = read_u8()
                    c_val = read_i32()
                    read_bytes(8)
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
                        "block_offset": cond_offset,
                        "results": results
                    })

                res["events"][eve_no] = {
                    "eve_no": eve_no, "name": ev_name, "when_happen": when_happen, "conditions": conditions
                }
        except Exception:
            pass

        return res

    def search_mission(self, mission_id: int) -> list[dict]:
        """Tìm kiếm nhiệm vụ qua toàn bộ 3,823 bản đồ trong Eve.emg."""
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

        backup_path = self.filepath.with_suffix(f".bak_{time.strftime('%Y%m%d_%H%M%S')}")
        if not backup_path.exists():
            shutil.copy2(self.filepath, backup_path)

        raw_x = target_x // 20
        raw_y = target_y // 20

        with open(self.filepath, "r+b") as f:
            abs_pos = entry["pos"] + block_offset
            f.seek(abs_pos + 2)
            f.write(struct.pack("<H", target_scene))
            f.seek(abs_pos + 6)
            f.write(struct.pack("<ii", raw_x, raw_y))
        
        return True

    def unlock_door_event(self, scene_id: int, eve_no: int) -> bool:
        """Mở khóa một sự kiện cổng: đặt điều kiện đầu tiên thành class=0 (không điều kiện)."""
        sc = self.parse_scene(scene_id)
        if not sc or eve_no not in sc["events"]:
            return False

        ev = sc["events"][eve_no]
        if not ev["conditions"]:
            return False

        cond = ev["conditions"][0]
        block_offset = cond.get("block_offset")
        if not block_offset:
            return False

        entry = self.scenes.get(scene_id)
        if not entry:
            return False

        backup_path = self.filepath.with_suffix(f".bak_{time.strftime('%Y%m%d_%H%M%S')}")
        if not backup_path.exists():
            shutil.copy2(self.filepath, backup_path)

        with open(self.filepath, "r+b") as f:
            abs_pos = entry["pos"] + block_offset
            # cond header: c_no (1B), then class(1B), param(2B), pstyle(1B), ops(1B), val(4B)
            f.seek(abs_pos + 1)
            # pack class=0, param=0, pstyle=0, ops=0, val=0
            f.write(struct.pack("<BHBBi", 0, 0, 0, 0, 0))

        # Đồng bộ sang các file CDN nếu có tại local
        cdn_paths = [
            ROOT_DIR / "cdn" / "mirror" / "tsr" / "2.4.76" / "ResourcesPacked" / "CompreseData" / "Eve.emg",
            ROOT_DIR / "cdn" / "mirror" / "tsr" / "files" / "CompreseData" / "Eve.emg",
        ]
        for cp in cdn_paths:
            if cp.is_file() and cp.resolve() != self.filepath.resolve():
                try:
                    c_arc = EveArchive(cp)
                    c_entry = c_arc.scenes.get(scene_id)
                    if c_entry:
                        with open(cp, "r+b") as cf:
                            cf.seek(c_entry["pos"] + block_offset + 1)
                            cf.write(struct.pack("<BHBBi", 0, 0, 0, 0, 0))
                except Exception:
                    pass

        return True


def get_available_drives() -> list:
    if sys.platform == "win32":
        return [f"{d}:\\" for d in string.ascii_uppercase if os.path.exists(f"{d}:\\")]
    return ["/"]


def download_file(url: str, dest: str, progress_label: str = None):
    """Tải file với hiển thị tiến độ."""
    def report(block_num, block_size, total_size):
        if total_size > 0 and progress_label:
            pct = min(100, int(block_num * block_size * 100 / total_size))
            print(f"\r  {progress_label}: {pct}% ({block_num * block_size // 1024}KB / {total_size // 1024}KB)", end="")
    
    print(f"  Đang tải: {url}")
    urlretrieve(url, dest, reporthook=report)
    if progress_label:
        print()  # xuống dòng sau tiến độ
    if not os.path.isfile(dest):
        raise FileNotFoundError(f"Tải thất bại: {url}")


# ============================================================================
# Quét môi trường
# ============================================================================
def scan_for_jdk21() -> str:
    """Quét tìm JDK 21 trên tất cả ổ đĩa."""
    # 1. Kiểm tra JAVA_HOME
    jh = os.environ.get("JAVA_HOME")
    if jh and os.path.isfile(os.path.join(jh, "bin", "java.exe")):
        return jh

    # 2. Kiểm tra PATH
    java_which = shutil.which("java")
    if java_which:
        real_java = os.path.realpath(java_which)
        bin_dir = os.path.dirname(real_java)
        if os.path.basename(bin_dir).lower() == "bin":
            candidate = os.path.dirname(bin_dir)
            if os.path.isfile(os.path.join(candidate, "bin", "javac.exe")):
                return candidate

    # 3. Quét tất cả ổ đĩa
    drives = get_available_drives()
    search_subdirs = [
        "Program Files/Java",
        "Program Files/Eclipse Adoptium",
        "Program Files/Amazon Corretto",
        "Program Files/Zulu",
        "Program Files/BellSoft",
        "Program Files/Microsoft",
        "Program Files (x86)/Java",
        "Java",
        "jdk",
        "Soft/AndroidStudio/jbr",
        "AndroidStudio/jbr",
    ]

    candidates = []
    for drive in drives:
        for sub in search_subdirs:
            target_base = os.path.join(drive, sub)
            if os.path.isdir(target_base):
                try:
                    for entry in os.listdir(target_base):
                        full = os.path.join(target_base, entry)
                        if os.path.isdir(full):
                            java_bin = os.path.join(full, "bin", "java.exe")
                            if os.path.isfile(java_bin):
                                candidates.append(full)
                except Exception:
                    pass

    # Ưu tiên JDK 21
    for c in candidates:
        if "21" in os.path.basename(c):
            return c

    return candidates[0] if candidates else None


def scan_for_python() -> str:
    """Quét tìm Python 3.x."""
    # 1. Kiểm tra PATH
    for cmd_name in ["python", "python3"]:
        w = shutil.which(cmd_name)
        if w:
            try:
                result = subprocess.run([w, "--version"], capture_output=True, text=True, timeout=5)
                if result.stdout and "Python 3." in result.stdout:
                    return w
            except Exception:
                pass

    # 2. Kiểm tra các đường dẫn phổ biến
    py_paths = []
    for ver in ["314", "313", "312", "311", "310"]:
        py_paths.append(os.path.join(os.environ.get("LOCALAPPDATA", ""), "Programs", "Python", f"Python{ver}", "python.exe"))
        py_paths.append(os.path.join(os.environ.get("ProgramFiles", ""), "Python" + ver, "python.exe"))
    
    for p in py_paths:
        if os.path.isfile(p):
            return p

    return None


def scan_for_mysql() -> str:
    """Quét tìm MySQL/XAMPP."""
    # 1. Kiểm tra port từ config hoặc port 3307, 3306
    port = 3307
    try:
        if CONFIG_FILE.is_file():
            c = json.loads(CONFIG_FILE.read_text(encoding="utf-8"))
            port = int(c.get("mysql_port", 3307))
    except Exception:
        pass

    for p in [port, 3307, 3306]:
        if check_port_listening(p):
            return "running"

    # 2. Kiểm tra XAMPP
    for x in ["C:\\xampp", "D:\\xampp", "E:\\xampp"]:
        if os.path.isfile(os.path.join(x, "mysql", "bin", "mysqld.exe")):
            return f"xampp:{x}"

    # 3. Kiểm tra MySQL độc lập
    mysql_dirs = [
        "C:\\Program Files\\MySQL\\MySQL Server 8.0",
        "C:\\Program Files\\MySQL\\MySQL Server 8.4",
        "D:\\Program Files\\MySQL\\MySQL Server 8.0",
        "D:\\Program Files\\MySQL\\MySQL Server 8.4",
        "C:\\Program Files (x86)\\MySQL\\MySQL Server 8.0",
    ]
    for m in mysql_dirs:
        if os.path.isfile(os.path.join(m, "bin", "mysqld.exe")):
            return f"mysql:{m}"

    return None


def find_mysql_client(mysql_info: str = None) -> str:
    """Tìm mysql.exe client từ chuỗi mysql_info hoặc các đường dẫn phổ biến."""
    if mysql_info:
        if mysql_info.startswith("xampp:"):
            p = os.path.join(mysql_info.split(":", 1)[1], "mysql", "bin", "mysql.exe")
            if os.path.isfile(p):
                return p
        elif mysql_info.startswith("mysql:"):
            p = os.path.join(mysql_info.split(":", 1)[1], "bin", "mysql.exe")
            if os.path.isfile(p):
                return p

    w = shutil.which("mysql")
    if w:
        return w

    search_dirs = [
        "C:\\xampp\\mysql\\bin", "D:\\xampp\\mysql\\bin", "E:\\xampp\\mysql\\bin",
        "C:\\Program Files\\MySQL\\MySQL Server 8.0\\bin",
        "C:\\Program Files\\MySQL\\MySQL Server 8.4\\bin",
        "D:\\Program Files\\MySQL\\MySQL Server 8.0\\bin",
        "D:\\Program Files\\MySQL\\MySQL Server 8.4\\bin",
        "C:\\Program Files (x86)\\MySQL\\MySQL Server 8.0\\bin",
        "C:\\laragon\\bin\\mysql\\current\\bin",
        "D:\\laragon\\bin\\mysql\\current\\bin",
    ]
    for d in search_dirs:
        p = os.path.join(d, "mysql.exe")
        if os.path.isfile(p):
            return p
    return None


def scan_for_adb() -> str:
    """Quét tìm ADB trên tất cả ổ đĩa."""
    for cmd_name in ["nox_adb", "adb"]:
        w = shutil.which(cmd_name)
        if w:
            return w

    drives = get_available_drives()
    patterns = [
        "Program Files/Nox/bin/nox_adb.exe",
        "Program Files (x86)/Nox/bin/nox_adb.exe",
        "Nox/bin/nox_adb.exe",
        "LDPlayer/LDPlayer9/adb.exe",
        "LDPlayer/LDPlayer4/adb.exe",
        "LDPlayer/adb.exe",
        "LDPlayer9/adb.exe",
        "Program Files/LDPlayer/LDPlayer9/adb.exe",
        "leidian/LDPlayer9/adb.exe",
        "Program Files/Microvirt/MEmu/adb.exe",
        "Android/platform-tools/adb.exe",
    ]

    for drive in drives:
        for pat in patterns:
            cand = os.path.join(drive, pat)
            if os.path.isfile(cand):
                return cand

    return None


# ============================================================================
# Quản lý cấu hình
# ============================================================================
class ConfigManager:
    @staticmethod
    def load() -> dict:
        default_cfg = {
            "java_home": "",
            "python_path": "",
            "mysql_info": "",
            "adb_path": "",
            "cdn_port": 443,
            "game_port": 6613,
            "mysql_host": "127.0.0.1",
            "mysql_port": 3307,
            "mysql_user": "root",
            "mysql_password": "",
            "mysql_database": "tsm_dodo",
            "auto_sync_mirror": True,
        }

        if CONFIG_FILE.is_file():
            try:
                with open(CONFIG_FILE, "r", encoding="utf-8") as f:
                    saved = json.load(f)
                    default_cfg.update(saved)
            except Exception:
                pass

        # Tự quét nếu đường dẫn bị hỏng
        saved_java = default_cfg.get("java_home")
        if not saved_java or not os.path.isfile(os.path.join(saved_java, "bin", "java.exe")):
            detected = scan_for_jdk21()
            if detected:
                default_cfg["java_home"] = detected

        saved_adb = default_cfg.get("adb_path")
        if not saved_adb or not os.path.isfile(saved_adb):
            detected = scan_for_adb()
            if detected:
                default_cfg["adb_path"] = detected

        saved_py = default_cfg.get("python_path")
        if not saved_py or not os.path.isfile(saved_py):
            detected = scan_for_python()
            if detected:
                default_cfg["python_path"] = detected

        saved_mysql = default_cfg.get("mysql_info")
        if not saved_mysql:
            detected = scan_for_mysql()
            if detected:
                default_cfg["mysql_info"] = detected

        return default_cfg

    @staticmethod
    def save(cfg: dict):
        try:
            with open(CONFIG_FILE, "w", encoding="utf-8") as f:
                json.dump(cfg, f, indent=2, ensure_ascii=False)
        except Exception:
            pass


# ============================================================================
# Các lớp cài đặt — mỗi lớp có hộp thoại xác nhận
# ============================================================================
class InstallerBase:
    """Lớp cơ sở cho các trình cài đặt có tải xuống + ghi log tiến độ."""
    
    def __init__(self, log_callback=None):
        self.log = log_callback or (lambda msg: print(msg))

    def confirm(self, title: str, message: str) -> bool:
        """Hỏi người dùng xác nhận trước khi cài."""
        return messagebox.askyesno(title, message)

    def run_silent(self, cmd: list, cwd: str = None, timeout: int = 300) -> bool:
        """Chạy lệnh ngầm và chờ hoàn tất."""
        startupinfo = None
        if sys.platform == "win32":
            startupinfo = subprocess.STARTUPINFO()
            startupinfo.dwFlags |= subprocess.STARTF_USESHOWWINDOW
            startupinfo.wShowWindow = subprocess.SW_HIDE
        try:
            result = subprocess.run(
                cmd, cwd=cwd, startupinfo=startupinfo,
                capture_output=True, text=True, timeout=timeout,
                encoding="utf-8", errors="replace"
            )
            return result.returncode == 0
        except Exception as e:
            self.log(f"  [ERROR] Lệnh thất bại: {e}")
            return False


class JDKInstaller(InstallerBase):
    """Cài JDK 21 từ Adoptium Temurin."""

    def check(self) -> str:
        return scan_for_jdk21()

    def install(self) -> str:
        existing = self.check()
        if existing:
            self.log(f"  [OK] JDK 21 đã được cài: {existing}")
            return existing

        if not self.confirm(
            "Cài đặt JDK 21",
            "Không tìm thấy JDK 21 trên máy.\n\n"
            "Có muốn tự động tải và cài đặt JDK 21 (Adoptium Temurin)?\n\n"
            "- Kích thước: ~180MB (MSI)\n"
            "- Nguồn: api.adoptium.net (chính thức)\n"
            "- Cài silent, set JAVA_HOME + PATH\n\n"
            "Tiếp tục?"
        ):
            self.log("  [BỎ QUA] Người dùng không cài JDK 21.")
            return None

        self.log("  [*] Đang lấy URL tải JDK 21 mới nhất từ Adoptium API...")
        try:
            api_url = "https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=x64&image_type=jdk&os=windows&release_type=ga"
            req = Request(api_url, headers={"Accept": "application/json"})
            import json as _json
            with urlopen(req, timeout=30) as resp:
                data = _json.loads(resp.read().decode("utf-8"))
            
            msi_asset = None
            for item in data:
                if hasattr(item, 'binary') and item.get('binary', {}).get('installer', {}).get('name', '').endswith('.msi'):
                    msi_asset = item
                    break
                if isinstance(item, dict) and item.get("binary", {}).get("installer", {}).get("name", "").endswith(".msi"):
                    msi_asset = item
                    break
            
            if not msi_asset:
                raise RuntimeError("Không tìm thấy file MSI trong phản hồi API")
            
            download_url = msi_asset["binary"]["installer"]["link"]
            file_name = msi_asset["binary"]["installer"]["name"]
            installer_path = os.path.join(os.environ.get("TEMP", "."), file_name)

            self.log(f"  [*] Đang tải: {file_name}")
            download_file(download_url, installer_path, "JDK 21 MSI")

            self.log("  [*] Đang cài JDK 21 (MSI ngầm)...")
            startupinfo = subprocess.STARTUPINFO()
            startupinfo.dwFlags |= subprocess.STARTF_USESHOWWINDOW
            startupinfo.wShowWindow = subprocess.SW_HIDE
            
            subprocess.run(
                ["msiexec.exe", "/i", installer_path, "/quiet", "/norestart",
                 "ADDLOCAL=FeatureMain,FeatureEnvironment,FeatureJarFile,FeatureJavaHome"],
                startupinfo=startupinfo, timeout=180
            )
            time.sleep(2)

            # Dọn dẹp
            try:
                os.remove(installer_path)
            except Exception:
                pass

            # Tìm JDK đã cài
            installed = self.check()
            if installed:
                self.log(f"  [OK] JDK 21 đã cài: {installed}")
                return installed

            self.log("  [!] Cài xong nhưng không tìm thấy JDK trong đường dẫn chuẩn.")
            self.log("  [!] Vui lòng cài thủ công từ: https://adoptium.net/temurin/releases/?version=21")
            return None

        except Exception as e:
            self.log(f"  [ERROR] Cài JDK thất bại: {e}")
            self.log(f"  [!] Tải thủ công: https://adoptium.net/temurin/releases/?version=21")
            return None


class PythonInstaller(InstallerBase):
    """Cài Python 3.x từ python.org."""

    def check(self) -> str:
        return scan_for_python()

    def install(self) -> str:
        existing = self.check()
        if existing:
            self.log(f"  [OK] Python đã được cài: {existing}")
            return existing

        if not self.confirm(
            "Cài đặt Python 3.x",
            "Không tìm thấy Python 3.x trên máy.\n\n"
            "Có muốn tự động tải và cài đặt Python 3.12.7?\n\n"
            "- Kích thước: ~27MB (exe)\n"
            "- Nguồn: python.org (chính thức)\n"
            "- Cài ngầm, tự thêm vào PATH\n\n"
            "Tiếp tục?"
        ):
            self.log("  [BỎ QUA] Người dùng không cài Python.")
            return None

        try:
            python_ver = "3.12.7"
            download_url = f"https://www.python.org/ftp/python/{python_ver}/python-{python_ver}-amd64.exe"
            installer_path = os.path.join(os.environ.get("TEMP", "."), f"python-{python_ver}-amd64.exe")

            self.log(f"  [*] Đang tải Python {python_ver}...")
            download_file(download_url, installer_path, "Python Installer")

            self.log("  [*] Đang cài Python (ngầm)...")
            install_args = "/quiet InstallAllUsers=1 PrependPath=1 Include_test=0 Include_pip=1 Include_launcher=1"
            
            startupinfo = subprocess.STARTUPINFO()
            startupinfo.dwFlags |= subprocess.STARTF_USESHOWWINDOW
            startupinfo.wShowWindow = subprocess.SW_HIDE
            
            subprocess.run(
                [installer_path] + install_args.split(),
                startupinfo=startupinfo, timeout=180
            )
            time.sleep(3)

            try:
                os.remove(installer_path)
            except Exception:
                pass

            # Cập nhật PATH
            import winreg
            try:
                with winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE, "SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Environment", 0, winreg.KEY_READ) as key:
                    machine_path, _ = winreg.QueryValueEx(key, "PATH")
                    os.environ["PATH"] = machine_path + ";" + os.environ.get("PATH", "")
            except Exception:
                pass

            installed = self.check()
            if installed:
                self.log(f"  [OK] Python đã cài: {installed}")
                return installed

            self.log("  [!] Python đã cài nhưng không tìm thấy trong PATH. Có thể cần khởi động lại máy.")
            return None

        except Exception as e:
            self.log(f"  [ERROR] Cài Python thất bại: {e}")
            self.log(f"  [!] Tải thủ công: https://www.python.org/downloads/")
            return None


class MySQLInstaller(InstallerBase):
    """Cài MySQL thông qua XAMPP."""

    def check(self) -> str:
        return scan_for_mysql()

    def install(self) -> str:
        existing = self.check()
        if existing:
            self.log(f"  [OK] MySQL đã có sẵn: {existing}")
            return existing

        if not self.confirm(
            "Cài đặt MySQL (XAMPP)",
            "Không tìm thấy MySQL trên máy.\n\n"
            "Có muốn tự động tải và cài đặt XAMPP (chứa MySQL)?\n\n"
            "- Kích thước: ~150MB (7z/exe)\n"
            "- Nguồn: apachefriends.org (chính thức)\n"
            "- Cài vào C:\\xampp\n"
            "- Tự khởi động MySQL sau khi cài\n\n"
            "Tiếp tục?"
        ):
            self.log("  [BỎ QUA] Người dùng không cài MySQL.")
            return None

        try:
            xampp_ver = "8.2.12"
            # Thử file self-extracting exe trước
            exe_url = f"https://sourceforge.net/projects/xampp/files/XAMPP%20Windows/{xampp_ver}/xampp-windows-x64-{xampp_ver}-0-VS16.exe/download"
            installer_path = os.path.join(os.environ.get("TEMP", "."), f"xampp-{xampp_ver}.exe")

            self.log(f"  [*] Đang tải XAMPP {xampp_ver}...")
            download_file(exe_url, installer_path, "XAMPP Installer")

            self.log("  [*] Đang cài XAMPP vào C:\\xampp (ngầm)...")
            startupinfo = subprocess.STARTUPINFO()
            startupinfo.dwFlags |= subprocess.STARTF_USESHOWWINDOW
            startupinfo.wShowWindow = subprocess.SW_HIDE

            subprocess.run(
                [installer_path, "--mode", "unattended", "--unattendedmodeui", "none",
                 "--datadir", "C:\\xampp\\mysql\\data", "--install_dir", "C:\\xampp"],
                startupinfo=startupinfo, timeout=300
            )

            try:
                os.remove(installer_path)
            except Exception:
                pass

            # Khởi động MySQL
            mysqld_path = "C:\\xampp\\mysql\\bin\\mysqld.exe"
            if os.path.isfile(mysqld_path):
                self.log("  [*] Đang khởi động MySQL...")
                ini_path = "C:\\xampp\\mysql\\bin\\my.ini"
                if os.path.isfile(ini_path):
                    subprocess.Popen([mysqld_path, f"--defaults-file={ini_path}"],
                                     startupinfo=startupinfo)
                else:
                    subprocess.Popen([mysqld_path, "--standalone"], startupinfo=startupinfo)
                time.sleep(5)
                self.log(f"  [OK] XAMPP đã cài: C:\\xampp, MySQL đã khởi động")
                return "xampp:C:\\xampp"

            self.log("  [!] XAMPP đã cài nhưng không tìm thấy mysqld.exe.")
            self.log("  [!] Tải thủ công: https://www.apachefriends.org/download.html")
            return None

        except Exception as e:
            self.log(f"  [ERROR] Cài XAMPP thất bại: {e}")
            self.log(f"  [!] Tải thủ công: https://www.apachefriends.org/download.html")
            return None


class DatabaseSetup(InstallerBase):
    """Tạo database và import SQL."""

    def setup(self, mysql_info: str = None) -> bool:
        cfg = ConfigManager.load()
        host = cfg.get("mysql_host", "127.0.0.1")
        try:
            port = int(cfg.get("mysql_port", 3307))
        except (ValueError, TypeError):
            port = 3307
        user = cfg.get("mysql_user", "root")
        saved_pwd = cfg.get("mysql_password", "")
        db_name = cfg.get("mysql_database", "tsm_dodo")

        # Kiểm tra port MySQL đang chạy không
        if not check_port_listening(port, host):
            self.log(f"  [CANH BÁO] Cổng MySQL {host}:{port} không phản hồi!")
            if not check_port_listening(3306, host):
                self.log("  [!] Vui lòng khởi động MySQL trước khi tạo Database.")
                messagebox.showerror("MySQL chưa bật", f"Không kết nối được MySQL tại {host}:{port}.\nVui lòng bật MySQL trước!")
                return False

        mysql_exe = find_mysql_client(mysql_info)
        if not mysql_exe or not os.path.isfile(mysql_exe):
            self.log("  [!] Không tìm thấy file mysql.exe.")
            self.log(f"  [!] Bạn có thể dùng Navicat / DBeaver / phpMyAdmin để chạy file: SQL/tsm_dodo.sql")
            messagebox.showwarning("Thiếu mysql.exe", f"Không tìm thấy mysql.exe để tự động import.\nBạn có thể mở SQL/tsm_dodo.sql bằng Navicat/HeidiSQL/phpMyAdmin để import vào database '{db_name}'.")
            return False

        sql_file = ROOT_DIR / "SQL" / "tsm_dodo.sql"
        if not sql_file.is_file():
            self.log(f"  [!] Không tìm thấy file SQL: {sql_file}")
            return False

        if not self.confirm(
            "Tạo Database & Import SQL",
            f"Sẽ thực hiện:\n"
            f"  1. Kết nối MySQL tại: {host}:{port}\n"
            f"  2. Tạo database '{db_name}' (nếu chưa có)\n"
            f"  3. Import dữ liệu từ: SQL/tsm_dodo.sql\n"
            f"  4. Tài khoản: {user}\n\n"
            f"Tiếp tục?"
        ):
            self.log("  [BỎ QUA] Người dùng không tạo database.")
            return False

        base_conn = [mysql_exe, "-h", host, "-P", str(port), "-u", user]

        # Thử kết nối với password đã lưu trước, rồi đến danh sách phổ biến
        passwords_to_try = [saved_pwd]
        for p in ["", "root", "mysql", "admin", "123456", "1234"]:
            if p not in passwords_to_try:
                passwords_to_try.append(p)

        working_pwd = None
        self.log(f"  [*] Đang thử kết nối MySQL ({host}:{port})...")
        for pwd in passwords_to_try:
            cmd = base_conn.copy()
            if pwd:
                cmd.append(f"-p{pwd}")
            cmd.extend(["-e", "SELECT 1"])
            try:
                result = subprocess.run(cmd, capture_output=True, text=True, timeout=5,
                                        encoding="utf-8", errors="replace")
                if result.returncode == 0:
                    working_pwd = pwd
                    break
            except Exception:
                pass

        if working_pwd is None:
            # Hỏi người dùng nhập password
            working_pwd = simpledialog.askstring(
                "Mật khẩu MySQL",
                f"Không thể kết nối MySQL tại {host}:{port} với user '{user}'.\n"
                "Nhập mật khẩu MySQL của bạn (để trống nếu không có):",
                show="*"
            )
            if working_pwd is None:
                self.log("  [BỎ QUA] Người dùng hủy nhập password.")
                return False

        # Lưu lại password vừa kết nối thành công
        cfg["mysql_password"] = working_pwd
        ConfigManager.save(cfg)
        sync_application_conf_db(host, port, user, working_pwd, db_name)

        self.log(f"  [OK] Đã kết nối MySQL tại {host}:{port} (User: {user})")

        # Tạo database
        self.log(f"  [*] Đang tạo database '{db_name}'...")
        cmd = base_conn.copy()
        if working_pwd:
            cmd.append(f"-p{working_pwd}")
        cmd.extend(["-e", f"CREATE DATABASE IF NOT EXISTS `{db_name}` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"])

        result = subprocess.run(cmd, capture_output=True, text=True, timeout=30,
                                 encoding="utf-8", errors="replace")
        if result.returncode != 0:
            self.log(f"  [ERROR] Tạo database thất bại: {result.stderr}")
            return False
        self.log(f"  [OK] Database '{db_name}' đã sẵn sàng.")

        # Import SQL
        self.log(f"  [*] Đang nhập SQL: {sql_file.name}...")
        cmd = base_conn.copy()
        if working_pwd:
            cmd.append(f"-p{working_pwd}")
        cmd.append(db_name)

        with open(sql_file, "r", encoding="utf-8", errors="replace") as f:
            sql_content = f.read()

        result = subprocess.run(
            cmd, input=sql_content, capture_output=True, text=True, timeout=120,
            encoding="utf-8", errors="replace"
        )
        if result.returncode == 0:
            self.log("  [OK] Đã nhập SQL thành công!")
            messagebox.showinfo("Thành công", f"Đã tạo database '{db_name}' và import SQL thành công!")
            return True
        else:
            self.log(f"  [!] Nhập xong với cảnh báo: {result.stderr[:200] if result.stderr else '(không có)'}")
            messagebox.showinfo("Hoàn tất", f"Đã import SQL vào database '{db_name}'!")
            return True


# ============================================================================
# Quản lý tiến trình Server
# ============================================================================
class ServerProcess:
    def __init__(self, name: str, on_log=None):
        self.name = name
        self.on_log = on_log
        self.process = None
        self.is_running = False
        self._reader_thread = None

    def start(self, cmd: list, cwd: str, env: dict = None):
        if self.is_running and self.process and self.process.poll() is None:
            return False

        full_env = os.environ.copy()
        if env:
            full_env.update(env)

        startupinfo = None
        if sys.platform == "win32":
            startupinfo = subprocess.STARTUPINFO()
            startupinfo.dwFlags |= subprocess.STARTF_USESHOWWINDOW
            startupinfo.wShowWindow = subprocess.SW_HIDE

        try:
            self.process = subprocess.Popen(
                cmd, cwd=cwd, env=full_env,
                stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                startupinfo=startupinfo, text=True,
                encoding="utf-8", errors="replace", bufsize=1,
            )
            self.is_running = True
            self._reader_thread = threading.Thread(target=self._stream_output, daemon=True)
            self._reader_thread.start()
            return True
        except Exception as e:
            if self.on_log:
                self.on_log(self.name, f"[ERROR] Khởi động thất bại: {e}")
            self.is_running = False
            return False

    def _stream_output(self):
        try:
            if self.process and self.process.stdout:
                for line in iter(self.process.stdout.readline, ''):
                    line_str = line.rstrip('\r\n')
                    if line_str and self.on_log:
                        self.on_log(self.name, line_str)
        except Exception:
            pass
        finally:
            self.is_running = False
            if self.on_log:
                code = self.process.poll() if self.process else -1
                self.on_log(self.name, f"[{self.name}] tiến trình đã dừng (mã thoát: {code}).")

    def stop(self):
        if self.process:
            try:
                if sys.platform == "win32":
                    subprocess.run(
                        ["taskkill", "/F", "/T", "/PID", str(self.process.pid)],
                        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                    )
                else:
                    self.process.terminate()
            except Exception:
                pass
            self.process = None
        self.is_running = False


def sync_application_conf_db(host: str, port: int, user: str, password: str, db_name: str = "tsm_dodo") -> bool:
    """Tự động đồng bộ cấu hình MySQL vào app/src/main/resources/application.conf."""
    app_conf = ROOT_DIR / "app" / "src" / "main" / "resources" / "application.conf"
    if not app_conf.is_file():
        return False
    try:
        content = app_conf.read_text(encoding="utf-8")
        new_url = f'jdbc:mysql://{host}:{port}/{db_name}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Taipei&characterEncoding=UTF-8'
        content = re.sub(r'url\s*=\s*"jdbc:mysql://[^"]+"', f'url = "{new_url}"', content)
        content = re.sub(r'user\s*=\s*"[^"]*"', f'user = "{user}"', content)
        content = re.sub(r'password\s*=\s*"[^"]*"', f'password = "{password}"', content)
        app_conf.write_text(content, encoding="utf-8")
        return True
    except Exception:
        return False


# ============================================================================
# Giao diện chính — Bảng điều khiển Tất cả trong Một
# ============================================================================
class TSMApp(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("TSM_DODO — Công cụ Quản lý Server")
        self.geometry("1080x780")
        self.minsize(980, 680)

        self.cfg = ConfigManager.load()
        self.lan_ip = get_local_lan_ip()

        # Tiến trình server
        self.cdn_proc = ServerProcess("CDN", self._handle_log)
        self.game_proc = ServerProcess("Game Server", self._handle_log)

        # Hàng đợi log
        self.log_queue = queue.Queue()

        # Kiểu dáng
        self._init_styles()

        # Xây dựng giao diện
        self._build_header()
        self._build_status_bar()
        self._build_log_panel()
        self._build_tabs()

        # Đưa cửa sổ lên foreground
        self._bring_to_foreground()

        # Polling (kiểm tra định kỳ)
        self.after(500, self._process_log_queue)
        self.after(1000, self._periodic_health_check)

        # Kiểm tra môi trường khi khởi động
        self.after(500, self._check_env_on_startup)

    def _bring_to_foreground(self):
        """Đưa cửa sổ lên foreground mỗi lần mở."""
        try:
            self.attributes("-topmost", True)
            self.after(100, lambda: self.attributes("-topmost", False))
        except Exception:
            pass
        try:
            import ctypes
            hwnd = ctypes.windll.user32.GetForegroundWindow()
            self.after(50, lambda: self._force_foreground())
        except Exception:
            pass

    def _force_foreground(self):
        """Ép cửa sổ lên foreground trên Windows."""
        try:
            import ctypes
            user32 = ctypes.windll.user32
            # Lấy handle cửa sổ
            hwnd = self.winfo_id()
            # Thử nhiều cách khác nhau
            user32.SetForegroundWindow(hwnd)
            # Cũng thử qua tkinter focus
            self.lift()
            self.focus_force()
            self.deiconify()
        except Exception:
            self.lift()
            self.focus_force()

    def _init_styles(self):
        style = ttk.Style(self)
        try:
            style.theme_use("clam")
        except Exception:
            pass

        self.font_main = ("Segoe UI", 10)
        self.font_bold = ("Segoe UI", 10, "bold")
        self.font_title = ("Segoe UI", 13, "bold")
        self.font_code = ("Consolas", 10)

        style.configure("TLabel", font=self.font_main)
        style.configure("TButton", font=self.font_main, padding=5)
        style.configure("Primary.TButton", font=self.font_bold, foreground="white", background="#0078D4")
        style.map("Primary.TButton", background=[("active", "#005A9E")])
        style.configure("Success.TButton", font=self.font_bold, foreground="white", background="#107C41")
        style.map("Success.TButton", background=[("active", "#0B5A2F")])
        style.configure("Danger.TButton", font=self.font_bold, foreground="white", background="#D13438")
        style.map("Danger.TButton", background=[("active", "#A80000")])
        style.configure("Warning.TButton", font=self.font_bold, foreground="white", background="#FF8C00")
        style.map("Warning.TButton", background=[("active", "#CC7000")])
        style.configure("TNotebook.Tab", font=self.font_bold, padding=(14, 7))

    # ========================================================================
    # XÂY DỰNG GIAO DIỆN
    # ========================================================================
    def _build_header(self):
        header = tk.Frame(self, bg="#0078D4", height=60)
        header.pack(fill="x", side="top")

        left_f = tk.Frame(header, bg="#0078D4")
        left_f.pack(side="left", padx=16, pady=8)

        tk.Label(left_f, text="TSM_DODO — Quản lý Server Game",
                 font=("Segoe UI", 14, "bold"), fg="white", bg="#0078D4").pack(anchor="w")

        sub_text = f"Thư mục: {ROOT_DIR}  |  IP LAN: {self.lan_ip}"
        tk.Label(left_f, text=sub_text, font=("Segoe UI", 9), fg="#E0E0E0", bg="#0078D4").pack(anchor="w")

    def _build_status_bar(self):
        bar = tk.Frame(self, bg="#FFFFFF", height=48, relief="groove", bd=1)
        bar.pack(fill="x", padx=12, pady=(10, 0))

        st_frame = tk.Frame(bar, bg="#FFFFFF")
        st_frame.pack(side="left", padx=12, pady=8)

        cdn_p = self.cfg.get("cdn_port", 443)
        mysql_p = self.cfg.get("mysql_port", 3307)
        self.cdn_status_lbl = tk.Label(st_frame, text=f"CDN: Đang kiểm ({cdn_p})", font=self.font_bold, bg="#FFFFFF", fg="#555555")
        self.cdn_status_lbl.pack(side="left", padx=10)

        self.game_status_lbl = tk.Label(st_frame, text="Game: Đang kiểm (6613)", font=self.font_bold, bg="#FFFFFF", fg="#555555")
        self.game_status_lbl.pack(side="left", padx=10)

        self.mysql_status_lbl = tk.Label(st_frame, text=f"MySQL: Đang kiểm ({mysql_p})", font=self.font_bold, bg="#FFFFFF", fg="#555555")
        self.mysql_status_lbl.pack(side="left", padx=10)

        btn_f = tk.Frame(bar, bg="#FFFFFF")
        btn_f.pack(side="right", padx=12, pady=6)

        ttk.Button(btn_f, text="Bật tất cả", style="Success.TButton", command=self.start_all_servers).pack(side="left", padx=3)
        ttk.Button(btn_f, text="Tắt tất cả", style="Danger.TButton", command=self.stop_all_servers).pack(side="left", padx=3)
        ttk.Button(btn_f, text="Quét lại", command=self.rescan_environment).pack(side="left", padx=3)

    def _build_tabs(self):
        self.notebook = ttk.Notebook(self)
        self.notebook.pack(fill="both", expand=True, padx=12, pady=10)
        self.notebook.bind("<<NotebookTabChanged>>", self._on_notebook_tab_changed)

        # Tab 0: Cài đặt tự động
        self.tab_setup = ttk.Frame(self.notebook, padding=12)
        self.notebook.add(self.tab_setup, text="[1] Cài đặt")
        self._init_tab_setup()

        # Tab 1: Server
        self.tab_servers = ttk.Frame(self.notebook, padding=12)
        self.notebook.add(self.tab_servers, text="[2] Điều khiển Server")
        self._init_tab_servers()

        # Tab 2: Môi trường
        self.tab_env = ttk.Frame(self.notebook, padding=12)
        self.notebook.add(self.tab_env, text="[3] Môi trường")
        self._init_tab_env()

        # Tab 3: MySQL
        self.tab_db = ttk.Frame(self.notebook, padding=12)
        self.notebook.add(self.tab_db, text="[4] MySQL Database")
        self._init_tab_db()

        # Tab 4: Lua
        self.tab_lua = ttk.Frame(self.notebook, padding=12)
        self.notebook.add(self.tab_lua, text="[5] Công cụ Lua")
        self._init_tab_lua()

        # Tab 5: Sửa IP trong APK
        self.tab_apk = ttk.Frame(self.notebook, padding=12)
        self.notebook.add(self.tab_apk, text="[6] Đổi IP trong APK")
        self._init_tab_apk()

        # Tab 6: Quản lý nhiệm vụ
        self.tab_quest = ttk.Frame(self.notebook, padding=12)
        self.notebook.add(self.tab_quest, text="[7] Nhiệm vụ")
        self._init_tab_quest()

        # Tab 7: Cổng Teleport & Dịch chuyển
        self.tab_teleport = ttk.Frame(self.notebook, padding=12)
        self.notebook.add(self.tab_teleport, text="[8] Cổng Teleport")
        self._init_tab_teleport()

        # Tab 8: Kịch bản Eve.emg
        self.tab_eve = ttk.Frame(self.notebook, padding=12)
        self.notebook.add(self.tab_eve, text="[9] Kịch bản Eve.emg")
        self._init_tab_eve()

    def _on_notebook_tab_changed(self, event=None):
        try:
            current_tab = self.notebook.select()
            # Khi ở Tab Server, Nhiệm vụ, Cổng Teleport hoặc Eve.emg: ẩn bottom_log_frame để nhường toàn bộ diện tích làm việc
            hide_tabs = [
                str(getattr(self, "tab_servers", "")),
                str(getattr(self, "tab_quest", "")),
                str(getattr(self, "tab_teleport", "")),
                str(getattr(self, "tab_eve", "")),
            ]
            if current_tab in hide_tabs:
                if hasattr(self, "bottom_log_frame") and self.bottom_log_frame.winfo_manager() == "pack":
                    self.bottom_log_frame.pack_forget()
            else:
                if hasattr(self, "bottom_log_frame") and self.bottom_log_frame.winfo_manager() != "pack":
                    self.bottom_log_frame.pack(side="bottom", fill="x", padx=12, pady=(0, 10))

            # Tự động nạp dữ liệu khi người dùng chuyển sang tab tương ứng
            if current_tab == str(getattr(self, "tab_quest", "")):
                if not getattr(self, "quest_chars_map", None):
                    self._quest_refresh_chars()
                if hasattr(self, "quest_lib_tree") and not self.quest_lib_tree.get_children():
                    self._quest_search_library()
            elif current_tab == str(getattr(self, "tab_teleport", "")):
                if not getattr(self, "tp_chars_map", None):
                    self._tp_refresh_chars()
                if hasattr(self, "tp_scene_tree") and not self.tp_scene_tree.get_children():
                    self._tp_filter_scenes()
            elif current_tab == str(getattr(self, "tab_eve", "")):
                if hasattr(self, "eve_door_tree") and not self.eve_door_tree.get_children():
                    self._eve_load_scene()
        except Exception:
            pass

    def _build_log_panel(self):
        self.bottom_log_frame = tk.LabelFrame(self, text="Bản ghi hệ thống", font=self.font_bold)
        self.bottom_log_frame.pack(fill="x", side="bottom", padx=12, pady=(0, 10))

        top_ctrl = tk.Frame(self.bottom_log_frame)
        top_ctrl.pack(fill="x", padx=6, pady=2)

        tk.Label(top_ctrl, text="Lọc:").pack(side="left")
        self.log_filter_var = tk.StringVar(value="Tất cả")
        ttk.Combobox(top_ctrl, textvariable=self.log_filter_var,
                     values=["Tất cả", "CDN", "Game Server", "Cài đặt", "Hệ thống"],
                     state="readonly", width=14).pack(side="left", padx=6)
        ttk.Button(top_ctrl, text="Xóa", command=self._clear_logs).pack(side="right", padx=4)

        text_wrap = tk.Frame(self.bottom_log_frame)
        text_wrap.pack(fill="both", expand=True, padx=6, pady=(0, 6))

        self.log_text = tk.Text(text_wrap, height=5, font=self.font_code, wrap="word", bg="#1E1E1E", fg="#D4D4D4")
        self.log_text.pack(side="left", fill="both", expand=True)

        sc = ttk.Scrollbar(text_wrap, orient="vertical", command=self.log_text.yview)
        sc.pack(side="right", fill="y")
        self.log_text.config(yscrollcommand=sc.set)

    # ========================================================================
    # TAB: CÀI ĐẶT TỰ ĐỘNG
    # ========================================================================
    def _init_tab_setup(self):
        f = tk.Frame(self.tab_setup)
        f.pack(fill="both", expand=True)

        # Mô tả
        desc = tk.LabelFrame(f, text="Cài đặt tự động: Cài tất cả phụ thuộc", font=self.font_bold, padx=10, pady=10)
        desc.pack(fill="x", pady=6)

        tk.Label(desc, text=(
            "Tab này cài đặt tất cả phụ thuộc cho TSM_DODO.\n"
            "Mỗi thành phần sẽ hỏi xác nhận trước khi cài.\n"
            "Nhấn 'Kiểm tra trạng thái' trước để xem thiếu gì."
        ), font=("Segoe UI", 9, "italic"), fg="#005A9E", justify="left").pack(anchor="w", pady=(0, 8))

        # Nút kiểm tra trạng thái
        ttk.Button(desc, text="Kiểm tra trạng thái (Quét tất cả)", style="Primary.TButton",
                   command=self._setup_check_status).pack(anchor="w", pady=4)

        # Nhãn trạng thái
        self.setup_jdk_status = tk.Label(desc, text="JDK 21: Đang kiểm...", font=self.font_main, fg="#555555")
        self.setup_jdk_status.pack(anchor="w", pady=2)

        self.setup_python_status = tk.Label(desc, text="Python: Đang kiểm...", font=self.font_main, fg="#555555")
        self.setup_python_status.pack(anchor="w", pady=2)

        self.setup_mysql_status = tk.Label(desc, text="MySQL: Đang kiểm...", font=self.font_main, fg="#555555")
        self.setup_mysql_status.pack(anchor="w", pady=2)

        self.setup_db_status = tk.Label(desc, text="Database: Chưa kiểm tra", font=self.font_main, fg="#555555")
        self.setup_db_status.pack(anchor="w", pady=2)

        # Nút cài từng thành phần
        btn_frame = tk.LabelFrame(f, text="Cài đặt từng thành phần (mỗi bước hỏi xác nhận)", font=self.font_bold, padx=10, pady=10)
        btn_frame.pack(fill="x", pady=6)

        r1 = tk.Frame(btn_frame)
        r1.pack(fill="x", pady=4)
        ttk.Button(r1, text="Cài JDK 21", style="Primary.TButton",
                   command=self._setup_install_jdk).pack(side="left", padx=4)
        ttk.Button(r1, text="Cài Python", style="Primary.TButton",
                   command=self._setup_install_python).pack(side="left", padx=4)
        ttk.Button(r1, text="Cài MySQL (XAMPP)", style="Primary.TButton",
                   command=self._setup_install_mysql).pack(side="left", padx=4)
        ttk.Button(r1, text="Tạo DB + Import SQL", style="Primary.TButton",
                   command=self._setup_database).pack(side="left", padx=4)

        # Cài tất cả
        r2 = tk.Frame(btn_frame)
        r2.pack(fill="x", pady=8)
        ttk.Button(r2, text="Cài TẤT CẢ (JDK + Python + MySQL + DB)", style="Success.TButton",
                   command=self._setup_install_all).pack(side="left", padx=4)

        # Kiểm tra quyền Admin
        admin_frame = tk.Frame(f)
        admin_frame.pack(fill="x", pady=6)
        admin_status = "Admin" if is_admin() else "Không phải Admin"
        admin_color = "#107C41" if is_admin() else "#D13438"
        tk.Label(admin_frame, text=f"Quyền: {admin_status}", font=self.font_bold, fg=admin_color).pack(side="left")
        if not is_admin():
            ttk.Button(admin_frame, text="Khởi động lại với quyền Admin", style="Warning.TButton",
                       command=self._restart_as_admin).pack(side="left", padx=10)

    def _setup_check_status(self):
        self.append_log("Cài đặt", "Đang quét tất cả thành phần...")

        def scan_thread():
            jdk = scan_for_jdk21()
            py = scan_for_python()
            mysql = scan_for_mysql()

            self.after(0, lambda: self._update_setup_status(jdk, py, mysql))

        threading.Thread(target=scan_thread, daemon=True).start()

    def _update_setup_status(self, jdk, py, mysql):
        # JDK
        if jdk:
            self.setup_jdk_status.config(text=f"JDK 21: Có -> {jdk}", fg="#107C41")
        else:
            self.setup_jdk_status.config(text="JDK 21: KHÔNG TÌM THẤY (nhấn Cài đặt)", fg="#D13438")

        # Python
        if py:
            self.setup_python_status.config(text=f"Python: Có -> {py}", fg="#107C41")
        else:
            self.setup_python_status.config(text="Python: KHÔNG TÌM THẤY (nhấn Cài đặt)", fg="#D13438")

        # MySQL
        if mysql:
            self.setup_mysql_status.config(text=f"MySQL: Có -> {mysql}", fg="#107C41")
        else:
            self.setup_mysql_status.config(text="MySQL: KHÔNG TÌM THẤY (nhấn Cài đặt)", fg="#D13438")

        # Database
        if mysql:
            self.setup_db_status.config(text="Database: Nhấn 'Tạo DB + Import SQL' để tạo", fg="#FF8C00")
        else:
            self.setup_db_status.config(text="Database: Cần MySQL trước", fg="#D13438")

        self.append_log("Cài đặt", f"Quét xong: JDK={'OK' if jdk else 'THIẾU'}, Python={'OK' if py else 'THIẾU'}, MySQL={'OK' if mysql else 'THIẾU'}")

    def _setup_install_jdk(self):
        self.append_log("Cài đặt", "Bắt đầu cài JDK 21...")

        def install_thread():
            installer = JDKInstaller(log_callback=lambda m: self.append_log("Cài đặt", m))
            result = installer.install()
            if result:
                self.cfg["java_home"] = result
                ConfigManager.save(self.cfg)
                self.after(0, lambda: self.env_java_var.set(result))
                self.after(0, lambda: self._check_java_version_ui())
                self.after(0, lambda: self._setup_check_status())

        threading.Thread(target=install_thread, daemon=True).start()

    def _setup_install_python(self):
        self.append_log("Cài đặt", "Bắt đầu cài Python...")

        def install_thread():
            installer = PythonInstaller(log_callback=lambda m: self.append_log("Cài đặt", m))
            result = installer.install()
            if result:
                self.cfg["python_path"] = result
                ConfigManager.save(self.cfg)
                self.after(0, lambda: self._setup_check_status())

        threading.Thread(target=install_thread, daemon=True).start()

    def _setup_install_mysql(self):
        self.append_log("Cài đặt", "Bắt đầu cài MySQL/XAMPP...")

        def install_thread():
            installer = MySQLInstaller(log_callback=lambda m: self.append_log("Cài đặt", m))
            result = installer.install()
            if result:
                self.cfg["mysql_info"] = result
                ConfigManager.save(self.cfg)
                self.after(0, lambda: self._setup_check_status())

        threading.Thread(target=install_thread, daemon=True).start()

    def _setup_database(self):
        self.append_log("Cài đặt", "Bắt đầu tạo database...")

        def db_thread():
            mysql_info = self.cfg.get("mysql_info", "")
            if not mysql_info:
                mysql_info = scan_for_mysql()
            db = DatabaseSetup(log_callback=lambda m: self.append_log("Cài đặt", m))
            success = db.setup(mysql_info)
            if success:
                self.after(0, lambda: self.setup_db_status.config(text="Database: OK (tsm_dodo đã tạo + SQL đã import)", fg="#107C41"))
            else:
                self.after(0, lambda: self.setup_db_status.config(text="Database: Tạo thất bại hoặc bỏ qua", fg="#D13438"))

        threading.Thread(target=db_thread, daemon=True).start()

    def _setup_install_all(self):
        if not self.confirm("Cài tất cả", "Điều này sẽ cài TẤT CẢ thành phần còn thiếu.\nMỗi bước sẽ hỏi xác nhận.\n\nTiếp tục?"):
            return

        self.append_log("Cài đặt", "=== Bắt đầu cài đặt ĐẦY ĐỦ ===")

        def install_all_thread():
            # 1. JDK
            self.append_log("Cài đặt", "--- Bước 1/4: JDK 21 ---")
            jdk_inst = JDKInstaller(log_callback=lambda m: self.append_log("Cài đặt", m))
            jdk_result = jdk_inst.install()
            if jdk_result:
                self.cfg["java_home"] = jdk_result

            # 2. Python
            self.append_log("Cài đặt", "--- Bước 2/4: Python ---")
            py_inst = PythonInstaller(log_callback=lambda m: self.append_log("Cài đặt", m))
            py_result = py_inst.install()
            if py_result:
                self.cfg["python_path"] = py_result

            # 3. MySQL
            self.append_log("Cài đặt", "--- Bước 3/4: MySQL ---")
            mysql_inst = MySQLInstaller(log_callback=lambda m: self.append_log("Cài đặt", m))
            mysql_result = mysql_inst.install()
            if mysql_result:
                self.cfg["mysql_info"] = mysql_result

            # 4. Database
            self.append_log("Cài đặt", "--- Bước 4/4: Database ---")
            db_inst = DatabaseSetup(log_callback=lambda m: self.append_log("Cài đặt", m))
            db_inst.setup(mysql_result or scan_for_mysql())

            # Lưu cấu hình
            ConfigManager.save(self.cfg)
            self.after(0, lambda: self._setup_check_status())
            self.after(0, lambda: self.rescan_environment())
            self.append_log("Cài đặt", "=== Cài đặt ĐẦY ĐỦ xong ===")

        threading.Thread(target=install_all_thread, daemon=True).start()

    def _restart_as_admin(self):
        if messagebox.askyesno("Khởi động lại với quyền Admin", "Ứng dụng cần quyền Admin để cài phần mềm.\nKhởi động lại ngay?"):
            run_as_admin(str(Path(__file__).resolve()))
            self.destroy()

    # ========================================================================
    # TAB: ĐIỀU KHIỂN SERVER
    # ========================================================================
    def _init_tab_servers(self):
        container = tk.Frame(self.tab_servers)
        container.pack(fill="both", expand=True)

        # 1. CDN Server
        cdn_card = tk.LabelFrame(container, text="1. CDN Server (Server tài nguyên)", font=self.font_bold, padx=8, pady=6)
        cdn_card.pack(fill="x", pady=3)

        c_top = tk.Frame(cdn_card)
        c_top.pack(fill="x")

        self.cdn_card_status = tk.Label(c_top, text="Trạng thái: Tắt", font=self.font_bold, fg="#D13438")
        self.cdn_card_status.pack(side="left")

        tk.Label(c_top, text="Port:").pack(side="left", padx=(20, 4))
        self.cdn_port_entry = ttk.Entry(c_top, width=8)
        self.cdn_port_entry.insert(0, str(self.cfg.get("cdn_port", 443)))
        self.cdn_port_entry.pack(side="left")

        self.cdn_start_btn = ttk.Button(c_top, text="Bật CDN", style="Success.TButton", command=self.start_cdn_server)
        self.cdn_start_btn.pack(side="right", padx=4)
        self.cdn_stop_btn = ttk.Button(c_top, text="Tắt", style="Danger.TButton", command=self.stop_cdn_server)
        self.cdn_stop_btn.pack(side="right", padx=4)

        tk.Label(cdn_card, text="Phục vụ tài nguyên game từ cdn/mirror/tsr/... (Port 443 HTTPS chuẩn)", fg="#666666", font=("Segoe UI", 9)).pack(anchor="w", pady=(2, 0))

        # 2. Game Server
        game_card = tk.LabelFrame(container, text="2. Game Server (Logic & Kết nối người chơi)", font=self.font_bold, padx=8, pady=6)
        game_card.pack(fill="x", pady=3)

        g_top = tk.Frame(game_card)
        g_top.pack(fill="x")

        self.game_card_status = tk.Label(g_top, text="Trạng thái: Tắt", font=self.font_bold, fg="#D13438")
        self.game_card_status.pack(side="left")

        tk.Label(g_top, text="Port:").pack(side="left", padx=(20, 4))
        self.game_port_entry = ttk.Entry(g_top, width=8)
        self.game_port_entry.insert(0, str(self.cfg.get("game_port", 6613)))
        self.game_port_entry.pack(side="left")

        self.game_start_btn = ttk.Button(g_top, text="Bật Game Server", style="Success.TButton", command=self.start_game_server)
        self.game_start_btn.pack(side="right", padx=4)
        self.game_stop_btn = ttk.Button(g_top, text="Tắt", style="Danger.TButton", command=self.stop_game_server)
        self.game_stop_btn.pack(side="right", padx=4)

        tk.Label(game_card, text="Chạy qua: gradlew.bat app:run (Tự set GAMEDATA_PATH & JAVA_HOME)", fg="#666666", font=("Segoe UI", 9)).pack(anchor="w", pady=(2, 0))

        # 3. Tiến trình & Nhật ký Server trực tiếp (Live Console)
        console_card = tk.LabelFrame(container, text="3. Tiến trình & Nhật ký Server (Live Console)", font=self.font_bold, padx=8, pady=6)
        console_card.pack(fill="both", expand=True, pady=(4, 0))

        # Thanh công cụ Console
        ctrl_bar = tk.Frame(console_card)
        ctrl_bar.pack(fill="x", pady=(0, 4))

        self.srv_console_status = tk.Label(ctrl_bar, text="● Sẵn sàng hiển thị tiến trình trực tiếp", font=("Segoe UI", 9, "italic"), fg="#666666")
        self.srv_console_status.pack(side="left", padx=4)

        self.srv_autoscroll_var = tk.BooleanVar(value=True)
        ttk.Checkbutton(ctrl_bar, text="Tự cuộn xuống", variable=self.srv_autoscroll_var).pack(side="right", padx=6)
        ttk.Button(ctrl_bar, text="🗑 Xóa log", command=self._clear_active_server_log).pack(side="right", padx=3)
        ttk.Button(ctrl_bar, text="📋 Sao chép", command=self._copy_server_log).pack(side="right", padx=3)
        ttk.Button(ctrl_bar, text="🗖 CMD ngoài", command=self._open_server_in_cmd).pack(side="right", padx=3)

        # Notebook chia 3 tab tiến trình
        self.srv_notebook = ttk.Notebook(console_card)
        self.srv_notebook.pack(fill="both", expand=True)

        # Tab: Tất cả
        self.tab_all_srv = ttk.Frame(self.srv_notebook)
        self.srv_notebook.add(self.tab_all_srv, text="  📺 Tất cả tiến trình  ")
        self.srv_all_text = self._create_terminal_text(self.tab_all_srv)

        # Tab: Game Server
        self.tab_game_srv = ttk.Frame(self.srv_notebook)
        self.srv_notebook.add(self.tab_game_srv, text="  🎮 Game Server (Port 6613)  ")
        self.srv_game_text = self._create_terminal_text(self.tab_game_srv)

        # Tab: CDN Server
        self.tab_cdn_srv = ttk.Frame(self.srv_notebook)
        self.srv_notebook.add(self.tab_cdn_srv, text="  🌐 CDN Server (Port 443)  ")
        self.srv_cdn_text = self._create_terminal_text(self.tab_cdn_srv)

    def _create_terminal_text(self, parent):
        frame = tk.Frame(parent)
        frame.pack(fill="both", expand=True)

        text = tk.Text(
            frame,
            font=("Consolas", 10),
            wrap="word",
            bg="#181824",
            fg="#D4D4D4",
            insertbackground="white",
            selectbackground="#264F78",
            padx=8,
            pady=6,
        )
        text.pack(side="left", fill="both", expand=True)

        v_scroll = ttk.Scrollbar(frame, orient="vertical", command=text.yview)
        v_scroll.pack(side="right", fill="y")
        text.config(yscrollcommand=v_scroll.set)

        # Cấu hình màu sắc tag theo chuẩn Terminal IDE
        text.tag_configure("time", foreground="#7F848E")
        text.tag_configure("chan_game", foreground="#61AFEF", font=("Consolas", 10, "bold"))
        text.tag_configure("chan_cdn", foreground="#98C379", font=("Consolas", 10, "bold"))
        text.tag_configure("chan_sys", foreground="#E5C07B", font=("Consolas", 10, "bold"))
        text.tag_configure("chan_other", foreground="#ABB2BF", font=("Consolas", 10, "bold"))
        text.tag_configure("error", foreground="#E06C75", font=("Consolas", 10, "bold"))
        text.tag_configure("success", foreground="#98C379")
        text.tag_configure("warn", foreground="#E5C07B")
        text.tag_configure("task", foreground="#C678DD")
        text.tag_configure("highlight", foreground="#56B6C2")

        return text

    def _append_to_terminal(self, text_widget, timestamp, channel, msg):
        try:
            line_count = int(text_widget.index("end-1c").split(".")[0])
            if line_count > 2500:
                text_widget.delete("1.0", "500.0")
        except Exception:
            pass

        ch_tag = "chan_other"
        if "Game" in channel:
            ch_tag = "chan_game"
        elif "CDN" in channel:
            ch_tag = "chan_cdn"
        elif "Hệ thống" in channel:
            ch_tag = "chan_sys"

        msg_lower = msg.lower()
        msg_tag = None
        if any(w in msg_lower for w in ["error", "lỗi", "exception", "failed", "không thể", "refused"]):
            msg_tag = "error"
        elif any(w in msg_lower for w in ["success", "thành công", "online", "listening", "hoàn thành", "started"]):
            msg_tag = "success"
        elif any(w in msg_lower for w in ["warning", "cảnh báo", "[!]"]):
            msg_tag = "warn"
        elif msg.startswith("> Task") or "task :" in msg_lower:
            msg_tag = "task"
        elif any(w in msg_lower for w in ["http://", "https://", "port"]):
            msg_tag = "highlight"

        text_widget.insert("end", f"[{timestamp}] ", "time")
        text_widget.insert("end", f"[{channel}] ", ch_tag)
        if msg_tag:
            text_widget.insert("end", f"{msg}\n", msg_tag)
        else:
            text_widget.insert("end", f"{msg}\n")

        if self.srv_autoscroll_var.get():
            text_widget.see("end")

    def _clear_active_server_log(self):
        try:
            active_idx = self.srv_notebook.index(self.srv_notebook.select())
            targets = [self.srv_all_text, self.srv_game_text, self.srv_cdn_text]
            if 0 <= active_idx < len(targets):
                targets[active_idx].delete("1.0", "end")
        except Exception:
            if hasattr(self, "srv_all_text"):
                self.srv_all_text.delete("1.0", "end")

    def _copy_server_log(self):
        try:
            active_idx = self.srv_notebook.index(self.srv_notebook.select())
            targets = [self.srv_all_text, self.srv_game_text, self.srv_cdn_text]
            tw = targets[active_idx] if 0 <= active_idx < len(targets) else self.srv_all_text
            content = tw.get("1.0", "end-1c")
            self.clipboard_clear()
            self.clipboard_append(content)
            self.srv_console_status.config(text="✓ Đã sao chép nội dung log vào Clipboard!", fg="#107C41")
            self.after(3000, lambda: self.srv_console_status.config(text="● Sẵn sàng hiển thị tiến trình trực tiếp", fg="#666666"))
        except Exception as e:
            messagebox.showerror("Lỗi", f"Không thể sao chép log: {e}")

    def _open_server_in_cmd(self):
        msg = (
            "Bạn có thể mở cửa sổ Command Prompt (CMD) riêng biệt bên ngoài:\n\n"
            "• Chọn [Yes] để mở CMD chạy Game Server (gradlew.bat app:run)\n"
            "• Chọn [No] để mở CMD chạy CDN Server (cdn_server.py)\n"
            "• Chọn [Cancel] để đóng."
        )
        res = messagebox.askyesnocancel("Mở CMD ngoài", msg)
        if res is True:
            jh = self.env_java_var.get().strip() if hasattr(self, "env_java_var") else self.cfg.get("java_home", "")
            gamedata_path = str(ROOT_DIR / "cdn" / "mirror" / "tsr" / "2.4.76" / "ResourcesPacked").replace("\\", "/")
            server_data_path = str(ROOT_DIR / "gamedata").replace("\\", "/")
            db_host = self.db_host_var.get().strip() if hasattr(self, 'db_host_var') else self.cfg.get("mysql_host", "127.0.0.1")
            db_port = self.db_port_var.get().strip() if hasattr(self, 'db_port_var') else str(self.cfg.get("mysql_port", 3307))
            db_user = self.db_user_var.get().strip() if hasattr(self, 'db_user_var') else self.cfg.get("mysql_user", "root")
            db_pass = self.db_pass_var.get() if hasattr(self, 'db_pass_var') else self.cfg.get("mysql_password", "")
            db_name = self.db_name_var.get().strip() if hasattr(self, 'db_name_var') else self.cfg.get("mysql_database", "tsm_dodo")
            db_url = f"jdbc:mysql://{db_host}:{db_port}/{db_name}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Taipei&characterEncoding=UTF-8"

            bat_content = (
                f'@echo off\r\n'
                f'title TSM Game Server Console\r\n'
                f'set "JAVA_HOME={jh}"\r\n'
                f'set "PATH={jh}\\bin;%PATH%"\r\n'
                f'set "GAMEDATA_PATH={gamedata_path}"\r\n'
                f'set "SERVER_DATA_PATH={server_data_path}"\r\n'
                f'set "DB_URL={db_url}"\r\n'
                f'set "DB_USER={db_user}"\r\n'
                f'set "DB_PASSWORD={db_pass}"\r\n'
                f'set "JAVA_TOOL_OPTIONS=-DDB_URL=\\"{db_url}\\" -DDB_USER=\\"{db_user}\\" -DDB_PASSWORD=\\"{db_pass}\\""\r\n'
                f'echo Dang khoi dong Game Server qua CMD...\r\n'
                f'gradlew.bat app:run\r\n'
                f'pause\r\n'
            )
            tmp_bat = ROOT_DIR / "run_game_server_cmd.bat"
            tmp_bat.write_text(bat_content, encoding="utf-8")
            subprocess.Popen(f'start cmd.exe /c "{tmp_bat}"', cwd=str(ROOT_DIR), shell=True)
        elif res is False:
            port = self.cdn_port_entry.get().strip() if hasattr(self, "cdn_port_entry") else str(self.cfg.get("cdn_port", 443))
            bat_content = (
                f'@echo off\r\n'
                f'title TSM CDN Server Console (Port {port})\r\n'
                f'echo Dang khoi dong CDN Server tai port {port}...\r\n'
                f'"{sys.executable}" cdn_server.py --port {port}\r\n'
                f'pause\r\n'
            )
            tmp_bat = ROOT_DIR / "run_cdn_server_cmd.bat"
            tmp_bat.write_text(bat_content, encoding="utf-8")
            subprocess.Popen(f'start cmd.exe /c "{tmp_bat}"', cwd=str(ROOT_DIR), shell=True)

    # ========================================================================
    # TAB: MÔI TRƯỜNG
    # ========================================================================
    def _init_tab_env(self):
        f = tk.Frame(self.tab_env)
        f.pack(fill="both", expand=True)

        tk.Label(f, text="Đường dẫn môi trường tự động phát hiện. Chỉnh sửa thủ công nếu cần.",
                 font=("Segoe UI", 9, "italic"), fg="#005A9E").pack(anchor="w", pady=(0, 10))

        # Thư mục gốc
        r_box = tk.LabelFrame(f, text="Thư mục gốc (Tự phát hiện)", font=self.font_bold, padx=8, pady=8)
        r_box.pack(fill="x", pady=5)
        tk.Label(r_box, text=str(ROOT_DIR), font=self.font_code, fg="#0078D4").pack(anchor="w")

        # Java
        j_box = tk.LabelFrame(f, text="Java JDK 21", font=self.font_bold, padx=8, pady=8)
        j_box.pack(fill="x", pady=5)

        j_row = tk.Frame(j_box)
        j_row.pack(fill="x")
        self.env_java_var = tk.StringVar(value=self.cfg.get("java_home", ""))
        ttk.Entry(j_row, textvariable=self.env_java_var, font=self.font_code).pack(side="left", fill="x", expand=True, padx=(0, 6))
        ttk.Button(j_row, text="Chọn...", command=self._browse_java).pack(side="left", padx=2)
        ttk.Button(j_row, text="Quét lại", command=self._scan_java_only).pack(side="left", padx=2)

        self.java_ver_status = tk.Label(j_box, text="Đang kiểm...", font=("Segoe UI", 9))
        self.java_ver_status.pack(anchor="w", pady=(4, 0))

        # Lưu
        btn_box = tk.Frame(f)
        btn_box.pack(fill="x", pady=15)
        ttk.Button(btn_box, text="Lưu cấu hình", style="Primary.TButton", command=self._save_env_config).pack(side="left", padx=4)
        ttk.Button(btn_box, text="Quét lại toàn bộ", command=self.rescan_environment).pack(side="left", padx=4)

        self._check_java_version_ui()

    def _browse_java(self):
        d = filedialog.askdirectory(title="Chọn thư mục JDK (chứa bin/)")
        if d:
            self.env_java_var.set(d)
            self._check_java_version_ui()

    def _scan_java_only(self):
        j = scan_for_jdk21()
        if j:
            self.env_java_var.set(j)
            messagebox.showinfo("Quét Java", f"Tìm thấy JDK:\n{j}")
        else:
            messagebox.showwarning("Quét Java", "Không tìm thấy JDK 21. Dùng tab Cài đặt để cài.")
        self._check_java_version_ui()

    def _check_java_version_ui(self):
        jh = self.env_java_var.get().strip()
        java_exe = os.path.join(jh, "bin", "java.exe")
        if os.path.isfile(java_exe):
            try:
                res = subprocess.run([java_exe, "-version"], stderr=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
                ver_line = (res.stderr or res.stdout).splitlines()[0] if (res.stderr or res.stdout) else "unknown"
                self.java_ver_status.config(text=f"OK: {ver_line}", fg="#107C41")
                return True
            except Exception as e:
                self.java_ver_status.config(text=f"Lỗi chạy java: {e}", fg="#D13438")
                return False
        else:
            self.java_ver_status.config(text="Không tìm thấy java.exe trong bin/!", fg="#D13438")
            return False

    def _save_env_config(self):
        self.cfg["java_home"] = self.env_java_var.get().strip()
        ConfigManager.save(self.cfg)
        self.append_log("Hệ thống", "Cấu hình đã lưu vào launcher_config.json")
        messagebox.showinfo("Đã lưu", "Cấu hình môi trường đã lưu!")

    # ========================================================================
    # TAB: MYSQL
    # ========================================================================
    def _init_tab_db(self):
        f = tk.Frame(self.tab_db)
        f.pack(fill="both", expand=True)

        db_card = tk.LabelFrame(f, text="Cấu hình kết nối MySQL", font=self.font_bold, padx=10, pady=10)
        db_card.pack(fill="x", pady=6)

        r1 = tk.Frame(db_card)
        r1.pack(fill="x", pady=4)
        tk.Label(r1, text="Host:").pack(side="left")
        self.db_host_var = tk.StringVar(value=self.cfg.get("mysql_host", "127.0.0.1"))
        ttk.Entry(r1, textvariable=self.db_host_var, width=16).pack(side="left", padx=(4, 20))
        tk.Label(r1, text="Port:").pack(side="left")
        self.db_port_var = tk.StringVar(value=str(self.cfg.get("mysql_port", 3306)))
        ttk.Entry(r1, textvariable=self.db_port_var, width=8).pack(side="left", padx=4)

        r2 = tk.Frame(db_card)
        r2.pack(fill="x", pady=4)
        tk.Label(r2, text="Tài khoản:").pack(side="left")
        self.db_user_var = tk.StringVar(value=self.cfg.get("mysql_user", "root"))
        ttk.Entry(r2, textvariable=self.db_user_var, width=16).pack(side="left", padx=(4, 20))
        tk.Label(r2, text="Mật khẩu:").pack(side="left")
        self.db_pass_var = tk.StringVar(value=self.cfg.get("mysql_password", ""))
        ttk.Entry(r2, textvariable=self.db_pass_var, width=16, show="*").pack(side="left", padx=4)

        r3 = tk.Frame(db_card)
        r3.pack(fill="x", pady=4)
        tk.Label(r3, text="Database:").pack(side="left")
        self.db_name_var = tk.StringVar(value=self.cfg.get("mysql_database", "tsm_dodo"))
        ttk.Entry(r3, textvariable=self.db_name_var, width=20).pack(side="left", padx=4)

        r4 = tk.Frame(db_card)
        r4.pack(fill="x", pady=10)
        ttk.Button(r4, text="Kiểm tra kết nối MySQL", command=self._test_mysql_port).pack(side="left", padx=4)
        ttk.Button(r4, text="💾 Lưu cấu hình", style="Success.TButton", command=self._save_mysql_config).pack(side="left", padx=4)
        ttk.Button(r4, text="Tạo DB + Import SQL", style="Primary.TButton", command=self._setup_database).pack(side="left", padx=4)

        sql_file = ROOT_DIR / "SQL" / "tsm_dodo.sql"
        if sql_file.is_file():
            ttk.Button(r4, text="Mở thư mục SQL", command=lambda: os.startfile(str(sql_file.parent))).pack(side="left", padx=4)

        tk.Label(f, text=(
            "Ghi chú:\n"
            "- Game Server cần MySQL đang chạy với database 'tsm_dodo'.\n"
            "- Nếu MySQL của bạn dùng Port khác (như 3307) hoặc có mật khẩu, hãy điền vào ô trên rồi bấm 'Lưu cấu hình'.\n"
            "- Cấu hình sẽ tự động truyền vào Game Server khi khởi động."
        ), justify="left", fg="#555555", font=("Segoe UI", 9)).pack(anchor="w", pady=10)

    def _save_mysql_config(self):
        host = self.db_host_var.get().strip()
        port_str = self.db_port_var.get().strip()
        user = self.db_user_var.get().strip()
        pwd = self.db_pass_var.get()
        db_name = self.db_name_var.get().strip()

        try:
            port = int(port_str)
        except ValueError:
            port = 3307

        self.cfg["mysql_host"] = host
        self.cfg["mysql_port"] = port
        self.cfg["mysql_user"] = user
        self.cfg["mysql_password"] = pwd
        self.cfg["mysql_database"] = db_name
        ConfigManager.save(self.cfg)

        synced = sync_application_conf_db(host, port, user, pwd, db_name)
        sync_msg = "\n(Đã đồng bộ trực tiếp vào application.conf)" if synced else ""

        self.append_log("Hệ thống", f"Đã lưu cấu hình MySQL: {host}:{port} (User: {user}, DB: {db_name})")
        messagebox.showinfo("Thành công", f"Đã lưu cấu hình MySQL thành công!\nHost: {host}:{port}\nUser: {user}\nDatabase: {db_name}{sync_msg}")

    def _test_mysql_port(self):
        host = self.db_host_var.get().strip()
        try:
            port = int(self.db_port_var.get().strip())
        except ValueError:
            port = 3307

        if not check_port_listening(port, host, 1.0):
            messagebox.showwarning("MySQL chưa mở", f"Không kết nối được cổng {host}:{port}.\nHãy kiểm tra xem MySQL (XAMPP/MySQL Service) đã bật chưa và kiểm tra lại số Port.")
            return

        # Cổng đang mở, thử đăng nhập
        mysql_exe = find_mysql_client()
        user = self.db_user_var.get().strip()
        pwd = self.db_pass_var.get()
        if mysql_exe:
            cmd = [mysql_exe, "-h", host, "-P", str(port), "-u", user]
            if pwd:
                cmd.append(f"-p{pwd}")
            cmd.extend(["-e", "SELECT 1"])
            try:
                res = subprocess.run(cmd, capture_output=True, text=True, timeout=5, encoding="utf-8", errors="replace")
                if res.returncode == 0:
                    messagebox.showinfo("MySQL OK", f"Tuyệt vời! Cổng {host}:{port} đang mở VÀ đăng nhập thành công (User: {user})!")
                    return
                else:
                    err_msg = res.stderr.strip()
                    messagebox.showwarning("Sai thông tin đăng nhập", f"Cổng {host}:{port} ĐANG MỞ, nhưng đăng nhập thất bại!\n\nLỗi:\n{err_msg}\n\nVui lòng kiểm tra lại 'Mật khẩu' và 'Tài khoản' rồi bấm 'Lưu cấu hình'.")
                    return
            except Exception:
                pass
        messagebox.showinfo("MySQL OK", f"OK: Cổng MySQL tại {host}:{port} đang mở và kết nối được!")

    # ========================================================================
    # TAB: CÔNG CỤ LUA
    # ========================================================================
    def _init_tab_lua(self):
        f = tk.Frame(self.tab_lua)
        f.pack(fill="both", expand=True)

        net_card = tk.LabelFrame(f, text="Đổi IP server trong Network.lua", font=self.font_bold, padx=10, pady=10)
        net_card.pack(fill="x", pady=6)

        f_row = tk.Frame(net_card)
        f_row.pack(fill="x", pady=4)
        tk.Label(f_row, text="Network.lua:").pack(side="left")
        default_net = ROOT_DIR / "cdn" / "data" / "Logic" / "Network.lua"
        self.lua_net_var = tk.StringVar(value=str(default_net))
        ttk.Entry(f_row, textvariable=self.lua_net_var, font=self.font_code).pack(side="left", fill="x", expand=True, padx=6)
        ttk.Button(f_row, text="Chọn...", command=self._browse_lua_net).pack(side="right")

        cfg_row = tk.Frame(net_card)
        cfg_row.pack(fill="x", pady=8)

        tk.Label(cfg_row, text="IP/Host:").grid(row=0, column=0, sticky="w", padx=4, pady=4)
        self.lua_host_var = tk.StringVar(value=self.lan_ip)
        ttk.Entry(cfg_row, textvariable=self.lua_host_var, width=20).grid(row=0, column=1, sticky="w", padx=6, pady=4)

        tk.Label(cfg_row, text="Port:").grid(row=0, column=2, sticky="w", padx=(14, 4), pady=4)
        self.lua_port_var = tk.StringVar(value="6613")
        ttk.Entry(cfg_row, textvariable=self.lua_port_var, width=10).grid(row=0, column=3, sticky="w", padx=6, pady=4)

        tk.Label(cfg_row, text="Ten:").grid(row=0, column=4, sticky="w", padx=(14, 4), pady=4)
        self.lua_name_var = tk.StringVar(value="Dev Server")
        ttk.Entry(cfg_row, textvariable=self.lua_name_var, width=20).grid(row=0, column=5, sticky="w", padx=6, pady=4)

        preset_row = tk.Frame(net_card)
        preset_row.pack(fill="x", pady=4)
        tk.Label(preset_row, text="IP nhanh:").pack(side="left")
        ttk.Button(preset_row, text=f"LAN ({self.lan_ip})", command=lambda: self.lua_host_var.set(self.lan_ip)).pack(side="left", padx=4)
        ttk.Button(preset_row, text="Localhost", command=lambda: self.lua_host_var.set("127.0.0.1")).pack(side="left", padx=4)

        b_row = tk.Frame(net_card)
        b_row.pack(fill="x", pady=(12, 4))
        ttk.Button(b_row, text="Đọc IP hiện tại", command=self._read_current_lua_ip).pack(side="left", padx=4)
        ttk.Button(b_row, text="Áp dụng & Mã hóa", style="Primary.TButton", command=self._apply_lua_ip).pack(side="right", padx=4)

        more_card = tk.LabelFrame(f, text="Trình sửa Lua nâng cao", font=self.font_bold, padx=10, pady=10)
        more_card.pack(fill="x", pady=10)
        ttk.Button(more_card, text="Mở giao diện Lua riêng", command=self._open_dedicated_lua_gui).pack(anchor="w", pady=6)

    def _browse_lua_net(self):
        f = filedialog.askopenfilename(title="Chọn Network.lua", filetypes=[("Lua", "*.lua"), ("Tất cả", "*.*")])
        if f:
            self.lua_net_var.set(f)
            self._read_current_lua_ip()

    def _read_current_lua_ip(self):
        p = Path(self.lua_net_var.get())
        if not p.is_file():
            messagebox.showerror("Lỗi", f"Không tìm thấy file: {p}")
            return
        try:
            from lua_crypt import aes_decrypt
            enc = p.read_bytes()
            try:
                dec = aes_decrypt(enc).decode("utf-8", errors="replace")
            except Exception:
                dec = enc.decode("utf-8", errors="replace")

            m = re.search(r'this\.servers\s*=\s*\{\{id=1,\s*host="([^"]+)",\s*port=(\d+)(?:,\s*name="([^"]+)")?', dec)
            if m:
                self.lua_host_var.set(m.group(1))
                self.lua_port_var.set(m.group(2))
                if m.group(3):
                    self.lua_name_var.set(m.group(3))
                self.append_log("Hệ thống", f"Đọc Network.lua (AES-128): {m.group(1)}:{m.group(2)}")
            else:
                messagebox.showwarning("Thông tin", "Không tìm thấy cấu hình server trong file!")
        except Exception as e:
            messagebox.showerror("Lỗi", str(e))

    def _apply_lua_ip(self):
        p = Path(self.lua_net_var.get())
        if not p.is_file():
            messagebox.showerror("Lỗi", f"Không tìm thấy file: {p}")
            return

        host = self.lua_host_var.get().strip()
        port_str = self.lua_port_var.get().strip()

        try:
            port = int(port_str)
        except ValueError:
            messagebox.showerror("Lỗi", "Port phải là số!")
            return

        try:
            from lua_crypt import set_server_ip
            set_server_ip(p, host, port)
            md5_hash = hashlib.md5(p.read_bytes()).hexdigest().upper()
            self.append_log("Hệ thống", f"Đã cập nhật Network.lua -> {host}:{port} (MD5={md5_hash})")
            self._sync_mirror_network(p, md5_hash)
            messagebox.showinfo("Thành công", f"Network.lua đã cập nhật!\nHost: {host}:{port}\nMD5: {md5_hash}")
        except Exception as e:
            messagebox.showerror("Lỗi", str(e))

    def _sync_mirror_network(self, src_file: Path, new_md5: str):
        mirror_root = ROOT_DIR / "cdn" / "mirror" / "tsr"
        if mirror_root.is_dir():
            count = 0
            for ver_dir in mirror_root.iterdir():
                if ver_dir.is_dir():
                    target = ver_dir / "LuaScripts" / "Lua" / "Logic" / "Network.lua"
                    if target.parent.is_dir():
                        shutil.copy2(src_file, target)
                        count += 1
                    dat_file = ver_dir / "LuaScripts" / "LuaScripts.dat"
                    if dat_file.is_file():
                        lines = dat_file.read_text(encoding="utf-8").splitlines()
                        new_lines = [
                            f"Lua/Logic/Network.lua|{new_md5}" if "Lua/Logic/Network.lua|" in l else l
                            for l in lines
                        ]
                        dat_file.write_text("\n".join(new_lines) + "\n", encoding="utf-8")
            if count > 0:
                self.append_log("Hệ thống", f"Đã đồng bộ {count} bản sao lưu CDN.")

    def _open_dedicated_lua_gui(self):
        gui_script = ROOT_DIR / "lua_crypt_gui.py"
        if gui_script.is_file():
            subprocess.Popen([sys.executable, str(gui_script)], cwd=str(ROOT_DIR))
        else:
            messagebox.showwarning("Không tìm thấy", f"Không tìm thấy file: {gui_script}")

    # ========================================================================
    # VÒNG ĐỜI SERVER
    # ========================================================================
    def start_cdn_server(self):
        port = self.cdn_port_entry.get().strip() or "443"
        try:
            self.cfg["cdn_port"] = int(port)
            ConfigManager.save(self.cfg)
        except ValueError:
            pass
        self.append_log("Hệ thống", f"Đang khởi động CDN Server tại port {port} (HTTPS)...")
        cmd = [sys.executable, "-u", "cdn_server.py", "--port", str(port)]
        if self.cdn_proc.start(cmd, cwd=str(ROOT_DIR)):
            self.append_log("Hệ thống", f"Đã gửi lệnh khởi động CDN Server (Port {port}).")

    def stop_cdn_server(self):
        self.append_log("Hệ thống", "Đang tắt CDN Server...")
        self.cdn_proc.stop()
        try:
            val = self.cdn_port_entry.get().strip() if hasattr(self, 'cdn_port_entry') else ""
            port = int(val) if val else int(self.cfg.get("cdn_port", 443))
        except ValueError:
            port = 443
        pids = stop_listeners_on_ports({port, 443, 8080})
        more_pids = kill_processes_by_commandline(["cdn_server.py"])
        all_pids = sorted(set(pids + more_pids))
        if all_pids:
            self.append_log("Hệ thống", f"Đã dừng triệt để CDN Server (PID: {', '.join(map(str, all_pids))}).")
        else:
            self.append_log("Hệ thống", "CDN Server đã dừng.")

    def start_game_server(self):
        jh = self.env_java_var.get().strip()
        if not jh or not os.path.isfile(os.path.join(jh, "bin", "java.exe")):
            messagebox.showerror("Thiếu JDK 21", "Game Server cần JDK 21.\nVào tab Cài đặt để cài, hoặc tab Môi trường để đặt đường dẫn.")
            return

        gamedata_path = str(ROOT_DIR / "cdn" / "mirror" / "tsr" / "2.4.76" / "ResourcesPacked").replace("\\", "/")
        server_data_path = str(ROOT_DIR / "gamedata").replace("\\", "/")

        # Lấy thông tin kết nối MySQL từ tab Database / Cấu hình
        db_host = self.db_host_var.get().strip() if hasattr(self, 'db_host_var') else self.cfg.get("mysql_host", "127.0.0.1")
        db_port = self.db_port_var.get().strip() if hasattr(self, 'db_port_var') else str(self.cfg.get("mysql_port", 3307))
        db_user = self.db_user_var.get().strip() if hasattr(self, 'db_user_var') else self.cfg.get("mysql_user", "root")
        db_pass = self.db_pass_var.get() if hasattr(self, 'db_pass_var') else self.cfg.get("mysql_password", "")
        db_name = self.db_name_var.get().strip() if hasattr(self, 'db_name_var') else self.cfg.get("mysql_database", "tsm_dodo")

        try:
            port_num = int(db_port)
        except ValueError:
            port_num = 3307

        db_url = f"jdbc:mysql://{db_host}:{port_num}/{db_name}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Taipei&characterEncoding=UTF-8"

        # Tự động lưu cấu hình MySQL & đồng bộ vào application.conf
        self.cfg["mysql_host"] = db_host
        self.cfg["mysql_port"] = port_num
        self.cfg["mysql_user"] = db_user
        self.cfg["mysql_password"] = db_pass
        self.cfg["mysql_database"] = db_name
        ConfigManager.save(self.cfg)
        sync_application_conf_db(db_host, port_num, db_user, db_pass, db_name)

        # Kiểm tra nhanh MySQL port
        if not check_port_listening(port_num, db_host, 0.5):
            self.append_log("Cảnh báo", f"[!] Cổng MySQL {db_host}:{port_num} chưa mở! Game Server có thể báo lỗi kết nối DB.")

        env = {
            "JAVA_HOME": jh,
            "PATH": f"{jh}\\bin;{os.environ.get('PATH', '')}",
            "GAMEDATA_PATH": gamedata_path,
            "SERVER_DATA_PATH": server_data_path,
            "DB_URL": db_url,
            "DB_USER": db_user,
            "DB_PASSWORD": db_pass,
            "JAVA_TOOL_OPTIONS": f'-DDB_URL="{db_url}" -DDB_USER="{db_user}" -DDB_PASSWORD="{db_pass}"',
        }

        self.append_log("Hệ thống", "Đang khởi động Game Server...")
        self.append_log("Game Server", f"JAVA_HOME: {jh}")
        self.append_log("Game Server", f"GAMEDATA_PATH: {gamedata_path}")
        self.append_log("Game Server", f"MySQL: {db_host}:{port_num} (User: {db_user}, DB: {db_name})")

        cmd = ["cmd.exe", "/c", "gradlew.bat", "app:run", "--console=plain"]
        if self.game_proc.start(cmd, cwd=str(ROOT_DIR), env=env):
            self.append_log("Hệ thống", "Đã gửi lệnh khởi động Game Server (build mất 15-30s).")

    def stop_game_server(self):
        self.append_log("Hệ thống", "Đang tắt Game Server...")
        self.game_proc.stop()
        try:
            val = self.game_port_entry.get().strip() if hasattr(self, 'game_port_entry') else ""
            port = int(val) if val else int(self.cfg.get("game_port", 6613))
        except ValueError:
            port = 6613
        pids = stop_listeners_on_ports({port, 6613})
        creationflags = getattr(subprocess, "CREATE_NO_WINDOW", 0)
        try:
            gradlew = ROOT_DIR / "gradlew.bat"
            if gradlew.is_file():
                subprocess.run(
                    [str(gradlew), "-p", str(ROOT_DIR), "--stop"],
                    stdout=subprocess.DEVNULL,
                    stderr=subprocess.DEVNULL,
                    creationflags=creationflags,
                    timeout=8,
                    check=False,
                )
        except Exception:
            pass
        more_pids = kill_processes_by_commandline(["NettyGameServer", "tsm_dodo", "app:run"])
        all_pids = sorted(set(pids + more_pids))
        if all_pids:
            self.append_log("Hệ thống", f"Đã dừng triệt để Game Server (PID: {', '.join(map(str, all_pids))}).")
        else:
            self.append_log("Hệ thống", "Game Server đã dừng.")

    def start_all_servers(self):
        self.append_log("Hệ thống", "=== KHỞI ĐỘNG TẤT CẢ SERVER ===")
        self.start_cdn_server()
        self.start_game_server()

    def stop_all_servers(self):
        self.append_log("Hệ thống", "=== TẮT TẤT CẢ SERVER ===")
        self.stop_cdn_server()
        self.stop_game_server()
        stop_listeners_on_ports({443, 8080, 6613})

    # ========================================================================
    # KIỂM TRA SỨC KHỎE & TRẠNG THÁI
    # ========================================================================
    def rescan_environment(self):
        self.append_log("Hệ thống", "Đang quét lại môi trường...")
        self.lan_ip = get_local_lan_ip()
        detected_java = scan_for_jdk21()
        if detected_java:
            self.env_java_var.set(detected_java)
            self.cfg["java_home"] = detected_java

        ConfigManager.save(self.cfg)
        self._check_java_version_ui()
        self._periodic_health_check()
        self.append_log("Hệ thống", "Quét lại xong!")

    def _check_env_on_startup(self):
        """Kiểm tra xem JDK/Python/MySQL có thiếu không và chuyển sang tab Cài đặt."""
        jdk = self.cfg.get("java_home")
        py = self.cfg.get("python_path")
        mysql = self.cfg.get("mysql_info")

        missing = []
        if not jdk or not os.path.isfile(os.path.join(jdk, "bin", "java.exe")):
            missing.append("JDK 21")
        if not py:
            missing.append("Python")
        if not mysql:
            missing.append("MySQL")

        if missing:
            self.notebook.select(0)  # Switch to Auto-Setup tab
            self._setup_check_status()
            self.append_log("Hệ thống", f"Thiếu: {', '.join(missing)} — vào tab Cài đặt.")

    def _periodic_health_check(self):
        try:
            cdn_port = int(self.cdn_port_entry.get().strip() or 443)
            game_port = int(self.game_port_entry.get().strip() or 6613)
            mysql_port = int(self.db_port_var.get().strip() or 3307)
        except ValueError:
            cdn_port, game_port, mysql_port = 443, 6613, 3307

        def check():
            cdn_ok = check_port_listening(cdn_port) or check_port_listening(443) or check_port_listening(8080)
            game_ok = check_port_listening(game_port) or check_port_listening(6613)
            mysql_ok = check_port_listening(mysql_port) or check_port_listening(3307) or check_port_listening(3306)
            self.after(0, lambda: self._update_status_ui(cdn_ok, game_ok, mysql_ok, mysql_port, cdn_port))

        threading.Thread(target=check, daemon=True).start()
        self.after(2000, self._periodic_health_check)

    def _update_status_ui(self, cdn_ok, game_ok, mysql_ok, mysql_port=3307, cdn_port=443):
        # CDN
        display_cdn = self.cdn_port_entry.get().strip() if hasattr(self, 'cdn_port_entry') else str(cdn_port)
        if cdn_ok or self.cdn_proc.is_running:
            self.cdn_status_lbl.config(text=f"CDN: Online ({display_cdn})", fg="#107C41")
            self.cdn_card_status.config(text=f"Trạng thái: Đang chạy (Port {display_cdn})", fg="#107C41")
            self.cdn_start_btn.config(state="disabled")
        else:
            self.cdn_status_lbl.config(text=f"CDN: Offline ({display_cdn})", fg="#D13438")
            self.cdn_card_status.config(text="Trạng thái: Tắt", fg="#D13438")
            self.cdn_start_btn.config(state="normal")
        # Luôn cho phép bấm Dừng để force kill tiến trình kẹt
        self.cdn_stop_btn.config(state="normal")

        # Game
        display_game = self.game_port_entry.get().strip() if hasattr(self, 'game_port_entry') else "6613"
        if game_ok:
            self.game_status_lbl.config(text=f"Game: Online ({display_game})", fg="#107C41")
            self.game_card_status.config(text=f"Trạng thái: Đang chạy (Port {display_game})", fg="#107C41")
            self.game_start_btn.config(state="disabled")
        else:
            if self.game_proc.is_running:
                self.game_status_lbl.config(text="Game: Đang khởi động (Build)", fg="#FF8C00")
                self.game_card_status.config(text="Trạng thái: Đang khởi động (Gradle build...)", fg="#FF8C00")
                self.game_start_btn.config(state="disabled")
            else:
                self.game_status_lbl.config(text=f"Game: Offline ({display_game})", fg="#D13438")
                self.game_card_status.config(text="Trạng thái: Tắt", fg="#D13438")
                self.game_start_btn.config(state="normal")
        # Luôn cho phép bấm Dừng để gỡ kẹt
        self.game_stop_btn.config(state="normal")

        # MySQL
        display_mysql = self.db_port_var.get().strip() if hasattr(self, 'db_port_var') else str(mysql_port)
        if mysql_ok:
            self.mysql_status_lbl.config(text=f"MySQL: Online ({display_mysql})", fg="#107C41")
        else:
            self.mysql_status_lbl.config(text=f"MySQL: Offline ({display_mysql})", fg="#D13438")

    # ========================================================================
    # XỬ LÝ LOG
    # ========================================================================
    ANSI_ESCAPE = re.compile(r'\x1B(?:[@-Z\\-_]|\[[0-?]*[ -/]*[@-~])')

    def _handle_log(self, channel: str, msg: str):
        self.log_queue.put((channel, msg))

    def _process_log_queue(self):
        current_filter = self.log_filter_var.get() if hasattr(self, "log_filter_var") else "Tất cả"
        while not self.log_queue.empty():
            channel, msg = self.log_queue.get()
            clean_msg = self.ANSI_ESCAPE.sub('', msg).rstrip('\r\n')
            if not clean_msg:
                continue
            timestamp = time.strftime("%H:%M:%S")

            # 1. Gửi vào Live Console Tab 2 (Điều khiển Server)
            if hasattr(self, "srv_all_text"):
                self._append_to_terminal(self.srv_all_text, timestamp, channel, clean_msg)
                if "Game" in channel:
                    self._append_to_terminal(self.srv_game_text, timestamp, channel, clean_msg)
                elif "CDN" in channel:
                    self._append_to_terminal(self.srv_cdn_text, timestamp, channel, clean_msg)

            # 1b. Gửi vào Live Event Inspector Tab 9
            if "Game" in channel and hasattr(self, "_inspect_game_event_log"):
                try:
                    self._inspect_game_event_log(timestamp, clean_msg)
                except Exception:
                    pass

            # 2. Gửi vào Bản ghi hệ thống phía dưới (nếu khớp bộ lọc)
            if hasattr(self, "log_text"):
                if current_filter in ("Tất cả", "All") or current_filter == channel:
                    self.log_text.insert("end", f"[{timestamp}] [{channel}] {clean_msg}\n")
                    self.log_text.see("end")

        self.after(100, self._process_log_queue)

    def _clear_logs(self):
        self.log_text.delete("1.0", "end")

    def append_log(self, channel: str, msg: str):
        self.log_queue.put((channel, msg))

    # ========================================================================
    # TAB: SỬA IP/PORT TRONG APK
    # ========================================================================
    def _init_tab_apk(self):
        f = tk.Frame(self.tab_apk)
        f.pack(fill="both", expand=True)

        # --- Chọn APK ---
        apk_frame = tk.LabelFrame(f, text="1. Chọn file APK", font=self.font_bold, padx=10, pady=10)
        apk_frame.pack(fill="x", pady=6)
        row = tk.Frame(apk_frame)
        row.pack(fill="x")
        self.apk_path_var = tk.StringVar()
        tk.Entry(row, textvariable=self.apk_path_var, font=self.font_code, width=55).pack(side="left", fill="x", expand=True, padx=(0, 6))
        ttk.Button(row, text="Duyệt...", command=self._apk_browse).pack(side="left", padx=2)
        ttk.Button(row, text="Quét", style="Primary.TButton", command=self._apk_scan).pack(side="left", padx=4)

        # Tự phát hiện APK trong thư mục tools/
        tools_dir = ROOT_DIR / "tools"
        if tools_dir.is_dir():
            for fn in tools_dir.iterdir():
                if fn.suffix.lower() == ".apk":
                    self.apk_path_var.set(str(fn))
                    break

        # --- Cấu hình IP/Port mới ---
        cfg_frame = tk.LabelFrame(f, text="2. Cấu hình server mới", font=self.font_bold, padx=10, pady=10)
        cfg_frame.pack(fill="x", pady=6)

        r1 = tk.Frame(cfg_frame)
        r1.pack(fill="x", pady=4)
        tk.Label(r1, text="IP Game Server:").pack(side="left")
        self.apk_ip_var = tk.StringVar(value="127.0.0.1")
        tk.Entry(r1, textvariable=self.apk_ip_var, width=18).pack(side="left", padx=6)
        tk.Label(r1, text="Port:").pack(side="left", padx=(20, 4))
        self.apk_port_var = tk.StringVar(value="6613")
        tk.Entry(r1, textvariable=self.apk_port_var, width=8).pack(side="left", padx=4)

        r2 = tk.Frame(cfg_frame)
        r2.pack(fill="x", pady=4)
        tk.Label(r2, text="IP CDN Server:").pack(side="left")
        self.apk_cdn_ip_var = tk.StringVar(value="127.0.0.1")
        tk.Entry(r2, textvariable=self.apk_cdn_ip_var, width=18).pack(side="left", padx=6)
        tk.Label(r2, text="Port:").pack(side="left", padx=(20, 4))
        self.apk_cdn_port_var = tk.StringVar(value=str(self.cfg.get("cdn_port", 443)))
        tk.Entry(r2, textvariable=self.apk_cdn_port_var, width=8).pack(side="left", padx=4)

        # Nút IP nhanh
        preset_row = tk.Frame(cfg_frame)
        preset_row.pack(fill="x", pady=4)
        tk.Label(preset_row, text="IP nhanh:").pack(side="left")
        ttk.Button(preset_row, text=f"LAN ({self.lan_ip})", command=lambda: (self.apk_ip_var.set(self.lan_ip), self.apk_cdn_ip_var.set(self.lan_ip))).pack(side="left", padx=4)
        ttk.Button(preset_row, text="Localhost", command=lambda: (self.apk_ip_var.set("127.0.0.1"), self.apk_cdn_ip_var.set("127.0.0.1"))).pack(side="left", padx=4)

        # --- Ký APK ---
        sign_frame = tk.LabelFrame(f, text="3. Ký APK (Tự động ký với Keystore tích hợp)", font=self.font_bold, padx=10, pady=10)
        sign_frame.pack(fill="x", pady=6)
        ks_row = tk.Frame(sign_frame)
        ks_row.pack(fill="x", pady=4)
        tk.Label(ks_row, text="Keystore:").pack(side="left")
        default_ks = ROOT_DIR / "tools" / "debug.keystore"
        self.apk_keystore_var = tk.StringVar(value=str(default_ks) if default_ks.is_file() else "")
        tk.Entry(ks_row, textvariable=self.apk_keystore_var, font=self.font_code, width=40).pack(side="left", fill="x", expand=True, padx=6)
        ttk.Button(ks_row, text="Duyệt...", command=self._apk_browse_ks).pack(side="left", padx=2)
        ttk.Button(ks_row, text="⚡ Tạo lại Key", command=self._apk_generate_debug_keystore).pack(side="left", padx=2)

        ks_row2 = tk.Frame(sign_frame)
        ks_row2.pack(fill="x", pady=4)
        tk.Label(ks_row2, text="Bí danh Key:").pack(side="left")
        self.apk_key_alias_var = tk.StringVar(value="androiddebugkey")
        tk.Entry(ks_row2, textvariable=self.apk_key_alias_var, width=18).pack(side="left", padx=6)
        tk.Label(ks_row2, text="Password:").pack(side="left", padx=(20, 4))
        self.apk_key_pass_var = tk.StringVar(value="android")
        tk.Entry(ks_row2, textvariable=self.apk_key_pass_var, width=14, show="*").pack(side="left", padx=4)

        tk.Label(sign_frame, text="✓ Tool đã tích hợp sẵn Debug Keystore. Tự động Ký APK để cài đặt được ngay vào giả lập/điện thoại!", fg="#107C41", font=("Segoe UI", 9)).pack(anchor="w", pady=(4, 0))

        # --- Thực hiện ---
        btn_row = tk.Frame(f)
        btn_row.pack(fill="x", pady=8)
        ttk.Button(btn_row, text="Thực hiện: Đổi IP + Port trong APK", style="Success.TButton",
                   command=self._apk_run_edit).pack(side="left", padx=4)
        ttk.Button(btn_row, text="📲 Cài vào Giả lập (ADB Install)", style="Primary.TButton",
                   command=self._apk_install_via_adb).pack(side="left", padx=4)

        # --- Kết quả quét / sửa ---
        result_frame = tk.LabelFrame(f, text="Kết quả quét / sửa", font=self.font_bold, padx=8, pady=8)
        result_frame.pack(fill="both", expand=True, pady=6)
        self.apk_result_text = tk.Text(result_frame, height=8, font=self.font_code, wrap="word", bg="#1E1E1E", fg="#D4D4D4")
        self.apk_result_text.pack(side="left", fill="both", expand=True)
        sc = ttk.Scrollbar(result_frame, orient="vertical", command=self.apk_result_text.yview)
        sc.pack(side="right", fill="y")
        self.apk_result_text.config(yscrollcommand=sc.set)

    def _apk_browse(self):
        f = filedialog.askopenfilename(title="Chọn file APK", filetypes=[("File APK", "*.apk"), ("Tất cả", "*.*")])
        if f:
            self.apk_path_var.set(f)

    def _apk_browse_ks(self):
        f = filedialog.askopenfilename(title="Chọn keystore", filetypes=[("Keystore", "*.jks *.keystore"), ("Tất cả", "*.*")])
        if f:
            self.apk_keystore_var.set(f)

    def _apk_generate_debug_keystore(self):
        default_ks = ROOT_DIR / "tools" / "debug.keystore"
        try:
            ApkIpEditor._generate_debug_keystore(default_ks)
            self.apk_keystore_var.set(str(default_ks))
            self.apk_key_alias_var.set("androiddebugkey")
            self.apk_key_pass_var.set("android")
            self._apk_log(f"[KEY] Đã tạo thành công Debug Keystore: {default_ks}")
            messagebox.showinfo("Thành công", f"Đã tạo Keystore tích hợp thành công!\n{default_ks}\n\nAlias: androiddebugkey\nPassword: android")
        except Exception as e:
            messagebox.showerror("Lỗi", f"Không thể tạo Keystore: {e}")

    def _apk_log(self, msg: str):
        self.apk_result_text.insert("end", msg + "\n")
        self.apk_result_text.see("end")
        self.update_idletasks()

    def _apk_scan(self):
        apk = self.apk_path_var.get().strip()
        if not apk or not Path(apk).is_file():
            messagebox.showerror("Lỗi", "Vui lòng chọn file APK hợp lệ")
            return
        self.apk_result_text.delete("1.0", "end")
        self._apk_log(f"[SCAN] Quét APK: {Path(apk).name}")
        threading.Thread(target=self._apk_do_scan, args=(apk,), daemon=True).start()

    def _apk_do_scan(self, apk_path: str):
        try:
            tmp = Path(tempfile.mkdtemp(prefix="tsm_scan_"))
            self._apk_log(f"[EXTRACT] Giải nén tạm...")

            with zipfile.ZipFile(apk_path, "r") as zf:
                zf.extractall(tmp)

            # Quét các file mục tiêu
            targets = [
                "assets/ServerList.dat",
                "assets/bin/Data/ServerList.dat",
                "assets/bin/Data/StreamingAssets/ServerList.dat",
                "assets/bin/Data/Managed/Metadata/global-metadata.dat",
                "assets/bin/Data/data.unity3d",
            ]
            known_ips = [b"192.168.1.69", b"192.168.1.19", b"7.7.7.8"]
            known_port = b"6613"
            known_cdn_port = b"00443"

            for rel in targets:
                fp = tmp / rel
                if not fp.is_file():
                    self._apk_log(f"   [i] {rel}: không có")
                    continue
                data = fp.read_bytes()
                self._apk_log(f"\n   [FILE] {rel} ({len(data)} bytes):")
                for ip in known_ips:
                    count = data.count(ip)
                    if count > 0:
                        self._apk_log(f"      IP '{ip.decode()}': {count} lần")
                        idx = data.find(ip)
                        ctx = data[max(0, idx-40):idx+60]
                        self._apk_log(f"      Ngữ cảnh: {ctx!r}")
                count_p = data.count(known_port)
                if count_p > 0:
                    self._apk_log(f"      Game Port '{known_port.decode()}': {count_p} lần")
                count_cdn = data.count(known_cdn_port)
                if count_cdn > 0:
                    self._apk_log(f"      CDN Port '{known_cdn_port.decode()}': {count_cdn} lần")

            # Quét tất cả file tìm IP đã biết
            self._apk_log(f"\n   [SEARCH] Quét toàn bộ file...")
            for root, dirs, files in os.walk(tmp):
                for fn in files:
                    fp = Path(root) / fn
                    try:
                        data = fp.read_bytes()
                    except:
                        continue
                    for ip in known_ips:
                        if ip in data:
                            rel_p = os.path.relpath(fp, tmp)
                            self._apk_log(f"      {rel_p}: '{ip.decode()}' x{data.count(ip)}")

            shutil.rmtree(tmp, ignore_errors=True)
            self._apk_log("\n[OK] Quét xong!")
        except Exception as e:
            self._apk_log(f"\n[ERROR] Lỗi: {e}")

    def _apk_run_edit(self):
        apk = self.apk_path_var.get().strip()
        ip = self.apk_ip_var.get().strip()
        port = self.apk_port_var.get().strip()
        cdn_ip = self.apk_cdn_ip_var.get().strip() or ip
        cdn_port = self.apk_cdn_port_var.get().strip() or "443"

        if not apk or not Path(apk).is_file():
            messagebox.showerror("Lỗi", "Vui lòng chọn file APK hợp lệ")
            return
        if not ip:
            messagebox.showerror("Lỗi", "Vui lòng nhập IP mới")
            return
        if not port:
            messagebox.showerror("Lỗi", "Vui lòng nhập port mới")
            return

        ks = self.apk_keystore_var.get().strip() or None
        self.apk_result_text.delete("1.0", "end")
        self._apk_log("[TSM] Bắt đầu...\n")
        threading.Thread(target=self._apk_do_edit, args=(apk, ip, port, cdn_ip, cdn_port, ks), daemon=True).start()

    def _apk_do_edit(self, apk, ip, port, cdn_ip, cdn_port, ks):
        try:
            editor = ApkIpEditor(
                apk_path=apk,
                new_ip=ip,
                new_port=int(port),
                new_cdn_ip=cdn_ip,
                new_cdn_port=int(cdn_port),
                keystore=ks,
                key_alias=self.apk_key_alias_var.get().strip() or "androiddebugkey",
                key_pass=self.apk_key_pass_var.get().strip() or "android",
                log_fn=self._apk_log,
            )
            out = editor.run()
            self._apk_log(f"\n[DONE] Hoàn thành! File: {out}")
            messagebox.showinfo("Thành công", f"APK đã đổi IP/Port và Ký thành công!\n{out}\n\nBạn có thể bấm nút '📲 Cài vào Giả lập' để cài trực tiếp!")
        except Exception as e:
            self._apk_log(f"\n[ERROR] Lỗi: {e}")
            messagebox.showerror("Lỗi", str(e))

    def _apk_install_via_adb(self):
        apk = self.apk_path_var.get().strip()
        if not apk:
            messagebox.showerror("Lỗi", "Vui lòng chọn file APK trước!")
            return
        p_apk = Path(apk)
        modified_apk = p_apk.parent / f"{p_apk.stem}_ip_modified.apk"
        target_apk = modified_apk if modified_apk.is_file() else p_apk

        adb = scan_for_adb()
        if not adb:
            messagebox.showerror("Không tìm thấy ADB", "Không tìm thấy adb.exe của LDPlayer/Nox!\nHãy chắc chắn giả lập đã được bật.")
            return

        self._apk_log(f"\n[ADB] Đang cài đặt: {target_apk.name} vào giả lập...")

        def install_thread():
            try:
                res = subprocess.run([adb, "install", "-r", str(target_apk)], capture_output=True, text=True, timeout=120)
                out = (res.stdout or "") + "\n" + (res.stderr or "")
                if "Success" in out:
                    self._apk_log("[ADB OK] Cài đặt game vào giả lập thành công! (Success)")
                    messagebox.showinfo("Thành công", "Đã cài đặt game vào giả lập thành công!\nHãy mở game trong giả lập để chơi.")
                else:
                    self._apk_log(f"[ADB LỖI] Cài đặt thất bại:\n{out}")
                    messagebox.showerror("Cài đặt thất bại", f"Lỗi từ giả lập:\n{out[:300]}")
            except Exception as e:
                self._apk_log(f"[ADB LỖI] {e}")
                messagebox.showerror("Lỗi", str(e))

        threading.Thread(target=install_thread, daemon=True).start()

    # ========================================================================
    # TAB 7: QUẢN LÝ NHIỆM VỤ (QUEST / MISSION ADMIN)
    # ========================================================================
    def _init_tab_quest(self):
        """Khởi tạo giao diện Tab [7] Quản lý Nhiệm vụ."""
        self.quest_chars_map = {}
        self.quest_char_missions_cache = []

        # 1. Thanh công cụ trên cùng: Chọn nhân vật & Sao lưu
        top_bar = ttk.Frame(self.tab_quest)
        top_bar.pack(fill="x", padx=4, pady=(2, 8))

        ttk.Label(top_bar, text="Nhân vật:", font=self.font_bold).pack(side="left", padx=(0, 6))
        self.quest_char_combo = ttk.Combobox(top_bar, state="readonly", width=38, font=self.font_main)
        self.quest_char_combo.pack(side="left", padx=(0, 8))
        self.quest_char_combo.bind("<<ComboboxSelected>>", self._quest_on_char_selected)

        ttk.Button(top_bar, text="🔄 Làm mới", command=self._quest_refresh_chars).pack(side="left", padx=(0, 6))
        ttk.Button(top_bar, text="💾 Sao lưu SQL", command=self._quest_backup_character_ui).pack(side="left", padx=(0, 10))

        self.quest_char_info_lbl = tk.Label(
            top_bar, text="Chưa chọn nhân vật", font=self.font_bold, fg="#005A9E", bg="#F4F4F4", padx=8, pady=3, relief="groove"
        )
        self.quest_char_info_lbl.pack(side="left", fill="x", expand=True)

        # 2. Khu vực chia đôi (PanedWindow): Bên trái là nhiệm vụ NV, bên phải là Thư viện Mark_C.dat
        paned = ttk.PanedWindow(self.tab_quest, orient="horizontal")
        paned.pack(fill="both", expand=True, padx=2, pady=2)

        # KHU VỰC TRÁI: Nhiệm vụ của nhân vật
        left_box = ttk.LabelFrame(paned, text=" Nhiệm vụ đang nhận của nhân vật ", padding=6)
        paned.add(left_box, weight=3)

        # Bộ lọc nhanh nhiệm vụ nhân vật
        filter_bar = ttk.Frame(left_box)
        filter_bar.pack(fill="x", pady=(0, 4))
        ttk.Label(filter_bar, text="🔍 Lọc:").pack(side="left", padx=(0, 4))
        self.quest_filter_var = tk.StringVar()
        self.quest_filter_entry = ttk.Entry(filter_bar, textvariable=self.quest_filter_var, width=20)
        self.quest_filter_entry.pack(side="left", padx=(0, 6))
        self.quest_filter_entry.bind("<KeyRelease>", self._quest_filter_char_missions)

        self.quest_count_lbl = ttk.Label(filter_bar, text="0 nhiệm vụ", foreground="#666666")
        self.quest_count_lbl.pack(side="right")

        # Bảng Treeview nhiệm vụ nhân vật
        tree_frame = ttk.Frame(left_box)
        tree_frame.pack(fill="both", expand=True)

        q_cols = ("id", "step", "name", "desc")
        self.quest_tree = ttk.Treeview(tree_frame, columns=q_cols, show="headings", selectmode="browse")
        self.quest_tree.heading("id", text="ID")
        self.quest_tree.heading("step", text="Bước")
        self.quest_tree.heading("name", text="Tên nhiệm vụ")
        self.quest_tree.heading("desc", text="Mô tả / Tiến độ")

        self.quest_tree.column("id", width=60, anchor="center")
        self.quest_tree.column("step", width=55, anchor="center")
        self.quest_tree.column("name", width=170, anchor="w")
        self.quest_tree.column("desc", width=240, anchor="w")

        q_scroll_y = ttk.Scrollbar(tree_frame, orient="vertical", command=self.quest_tree.yview)
        self.quest_tree.configure(yscrollcommand=q_scroll_y.set)
        self.quest_tree.pack(side="left", fill="both", expand=True)
        q_scroll_y.pack(side="right", fill="y")
        self.quest_tree.bind("<<TreeviewSelect>>", self._quest_on_select_char_mission)

        # Bộ điều khiển sửa bước / gỡ kẹt
        ctrl_box = ttk.LabelFrame(left_box, text=" Thao tác nhiệm vụ ", padding=6)
        ctrl_box.pack(fill="x", pady=(6, 0))

        row1 = ttk.Frame(ctrl_box)
        row1.pack(fill="x", pady=2)
        ttk.Label(row1, text="ID:").pack(side="left", padx=(0, 4))
        self.quest_edit_id_var = tk.StringVar()
        self.quest_edit_id_ent = ttk.Entry(row1, textvariable=self.quest_edit_id_var, width=7, font=self.font_bold)
        self.quest_edit_id_ent.pack(side="left", padx=(0, 8))

        ttk.Label(row1, text="Bước:").pack(side="left", padx=(0, 4))
        self.quest_edit_step_var = tk.StringVar(value="1")
        self.quest_edit_step_spn = ttk.Spinbox(row1, textvariable=self.quest_edit_step_var, from_=1, to=999, width=5)
        self.quest_edit_step_spn.pack(side="left", padx=(0, 8))

        ttk.Button(row1, text="💾 Lưu bước", style="Primary.TButton", command=self._quest_save_step).pack(side="left", padx=(0, 4))
        ttk.Button(row1, text="⏩ +1 Bước (Vượt kẹt)", style="Success.TButton", command=self._quest_advance_step).pack(side="left", padx=(0, 4))

        row2 = ttk.Frame(ctrl_box)
        row2.pack(fill="x", pady=(4, 2))
        ttk.Button(row2, text="⚡ Về bước 1 (Làm lại)", style="Warning.TButton", command=self._quest_reset_step_1).pack(side="left", padx=(0, 4))
        ttk.Button(row2, text="🗑️ Xóa nhiệm vụ", style="Danger.TButton", command=self._quest_delete_mission).pack(side="left", padx=(0, 4))
        ttk.Button(row2, text="🔄 Tải lại", command=self._quest_load_char_missions).pack(side="right")

        # KHU VỰC PHẢI: Thư viện nhiệm vụ (Mark_C.dat)
        right_box = ttk.LabelFrame(paned, text=" Thư viện 3,448 nhiệm vụ gốc (Mark_C.dat) ", padding=6)
        paned.add(right_box, weight=2)

        lib_filter_bar = ttk.Frame(right_box)
        lib_filter_bar.pack(fill="x", pady=(0, 4))
        ttk.Label(lib_filter_bar, text="🔍 Tìm:").pack(side="left", padx=(0, 4))
        self.quest_lib_search_var = tk.StringVar()
        self.quest_lib_search_ent = ttk.Entry(lib_filter_bar, textvariable=self.quest_lib_search_var, width=18)
        self.quest_lib_search_ent.pack(side="left", fill="x", expand=True, padx=(0, 4))
        self.quest_lib_search_ent.bind("<KeyRelease>", self._quest_search_library)
        ttk.Button(lib_filter_bar, text="Tìm", width=5, command=self._quest_search_library).pack(side="left")

        lib_tree_frame = ttk.Frame(right_box)
        lib_tree_frame.pack(fill="both", expand=True)

        lib_cols = ("id", "name", "desc")
        self.quest_lib_tree = ttk.Treeview(lib_tree_frame, columns=lib_cols, show="headings", selectmode="browse")
        self.quest_lib_tree.heading("id", text="ID")
        self.quest_lib_tree.heading("name", text="Tên nhiệm vụ")
        self.quest_lib_tree.heading("desc", text="Mô tả gốc")

        self.quest_lib_tree.column("id", width=55, anchor="center")
        self.quest_lib_tree.column("name", width=150, anchor="w")
        self.quest_lib_tree.column("desc", width=180, anchor="w")

        lib_scroll_y = ttk.Scrollbar(lib_tree_frame, orient="vertical", command=self.quest_lib_tree.yview)
        self.quest_lib_tree.configure(yscrollcommand=lib_scroll_y.set)
        self.quest_lib_tree.pack(side="left", fill="both", expand=True)
        lib_scroll_y.pack(side="right", fill="y")
        self.quest_lib_tree.bind("<Double-1>", self._quest_on_lib_double_click)

        lib_bot_bar = ttk.Frame(right_box)
        lib_bot_bar.pack(fill="x", pady=(6, 0))
        ttk.Button(
            lib_bot_bar, text="➕ Gán vào Nhân vật (Bước 1)", style="Primary.TButton",
            command=self._quest_add_from_library
        ).pack(side="left", fill="x", expand=True)

        # Thanh trạng thái dưới cùng của tab
        self.quest_status_lbl = tk.Label(
            self.tab_quest, text="Sẵn sàng.", font=self.font_main, fg="#555555", anchor="w"
        )
        self.quest_status_lbl.pack(fill="x", pady=(4, 0))

    def _quest_log(self, text: str, color: str = "#333333"):
        if hasattr(self, "quest_status_lbl"):
            self.quest_status_lbl.config(text=text, fg=color)

    def _quest_refresh_chars(self):
        """Tải danh sách nhân vật từ database vào combobox."""
        def worker():
            try:
                self.after(0, lambda: self._quest_log("Đang tải danh sách nhân vật...", "#005A9E"))
                rows = MySqlExecutor.query("SELECT id, name, level, map_id, x, y FROM characters ORDER BY id;")
                items = []
                mapping = {}
                scenes = GameDataCache.get_scenes()
                for r in rows:
                    cid, name, lv, mid, x, y = int(r[0]), str(r[1]), int(r[2]), int(r[3]), int(r[4]), int(r[5])
                    map_name = scenes.get(mid, f"Map {mid}")
                    disp = f"[{cid}] {name} - Lv.{lv} ({map_name})"
                    items.append(disp)
                    mapping[disp] = {
                        "id": cid, "name": name, "level": lv, "map_id": mid,
                        "map_name": map_name, "x": x, "y": y
                    }

                def update_ui():
                    self.quest_chars_map = mapping
                    self.quest_char_combo["values"] = items
                    if items:
                        cur = self.quest_char_combo.get()
                        if not cur or cur not in mapping:
                            self.quest_char_combo.current(0)
                        self._quest_on_char_selected()
                    else:
                        self.quest_char_info_lbl.config(text="Không tìm thấy nhân vật nào trong CSDL")
                    self._quest_log(f"Đã nạp {len(items)} nhân vật từ database.", "#107C41")

                self.after(0, update_ui)
            except Exception as e:
                self.after(0, lambda: self._quest_log(f"Lỗi tải nhân vật: {e}", "#D13438"))

        threading.Thread(target=worker, daemon=True).start()

    def _quest_get_selected_char(self) -> dict | None:
        sel = self.quest_char_combo.get()
        if not sel or sel not in self.quest_chars_map:
            return None
        return self.quest_chars_map[sel]

    def _quest_on_char_selected(self, event=None):
        char = self._quest_get_selected_char()
        if not char:
            return
        self.quest_char_info_lbl.config(
            text=f"[{char['id']}] {char['name']} | Lv.{char['level']} | Map [{char['map_id']}] {char['map_name']} (X:{char['x']}, Y:{char['y']})"
        )
        self._quest_load_char_missions()

    def _quest_backup_character(self, char_id: int) -> Path:
        """Tạo file sao lưu .sql nhiệm vụ của nhân vật trước khi chỉnh sửa."""
        backup_dir = ROOT_DIR / "backups" / "quest-admin"
        backup_dir.mkdir(parents=True, exist_ok=True)
        timestamp = time.strftime("%Y%m%d_%H%M%S")
        backup_file = backup_dir / f"character_{char_id}_{timestamp}.sql"
        rows = MySqlExecutor.query(
            "SELECT mission_id, step FROM character_missions WHERE char_id = %s ORDER BY mission_id;", (char_id,)
        )
        lines = [
            f"-- TSM_Setup QuestAdmin Backup",
            f"-- Character ID: {char_id}",
            f"-- Backup Time: {time.strftime('%Y-%m-%d %H:%M:%S')}",
            f"-- Missions Count: {len(rows)}",
            "",
            f"DELETE FROM character_missions WHERE char_id = {char_id};",
        ]
        for r in rows:
            mid, step = int(r[0]), int(r[1])
            lines.append(f"INSERT INTO character_missions (char_id, mission_id, step) VALUES ({char_id}, {mid}, {step});")
        lines.append("")
        with open(backup_file, "w", encoding="utf-8") as f:
            f.write("\n".join(lines))
        return backup_file

    def _quest_backup_character_ui(self):
        char = self._quest_get_selected_char()
        if not char:
            messagebox.showwarning("Thông báo", "Vui lòng chọn nhân vật trước khi sao lưu.")
            return
        try:
            bk_path = self._quest_backup_character(char["id"])
            self._quest_log(f"Đã sao lưu nhiệm vụ: {bk_path.name}", "#107C41")
            messagebox.showinfo("Sao lưu thành công", f"Đã lưu bản sao nhiệm vụ của [{char['name']}] vào:\n{bk_path}")
        except Exception as e:
            self._quest_log(f"Lỗi sao lưu: {e}", "#D13438")
            messagebox.showerror("Lỗi sao lưu", str(e))

    def _quest_load_char_missions(self):
        """Tải toàn bộ nhiệm vụ của nhân vật đang chọn."""
        char = self._quest_get_selected_char()
        if not char:
            return

        def worker():
            try:
                char_id = char["id"]
                rows = MySqlExecutor.query(
                    "SELECT mission_id, step FROM character_missions WHERE char_id = %s ORDER BY mission_id;", (char_id,)
                )
                marks = GameDataCache.get_marks()
                missions = []
                for r in rows:
                    mid, step = int(r[0]), int(r[1])
                    m_def = marks.get(mid, {})
                    m_name = m_def.get("name", "(ID chưa có trong dữ liệu client)")
                    m_desc = m_def.get("desc", "")
                    missions.append((mid, step, m_name, m_desc))

                def update_ui():
                    self.quest_char_missions_cache = missions
                    self._quest_render_char_missions(missions)
                    self._quest_log(f"Đã tải {len(missions)} nhiệm vụ của nhân vật [{char['name']}].", "#107C41")

                self.after(0, update_ui)
            except Exception as e:
                self.after(0, lambda: self._quest_log(f"Lỗi tải nhiệm vụ: {e}", "#D13438"))

        threading.Thread(target=worker, daemon=True).start()

    def _quest_render_char_missions(self, missions: list):
        for item in self.quest_tree.get_children():
            self.quest_tree.delete(item)
        for m in missions:
            self.quest_tree.insert("", "end", values=m)
        self.quest_count_lbl.config(text=f"{len(missions)} nhiệm vụ")

    def _quest_filter_char_missions(self, event=None):
        q = self.quest_filter_var.get().strip().lower()
        if not q:
            self._quest_render_char_missions(self.quest_char_missions_cache)
            return
        filtered = [
            m for m in self.quest_char_missions_cache
            if q in str(m[0]).lower() or q in m[2].lower() or q in m[3].lower()
        ]
        self._quest_render_char_missions(filtered)

    def _quest_on_select_char_mission(self, event=None):
        sel = self.quest_tree.selection()
        if not sel:
            return
        vals = self.quest_tree.item(sel[0], "values")
        if vals:
            self.quest_edit_id_var.set(str(vals[0]))
            self.quest_edit_step_var.set(str(vals[1]))

    def _quest_save_step(self):
        """Thêm hoặc cập nhật bước nhiệm vụ cho nhân vật."""
        char = self._quest_get_selected_char()
        if not char:
            messagebox.showwarning("Thông báo", "Vui lòng chọn nhân vật.")
            return
        raw_id = self.quest_edit_id_var.get().strip()
        raw_step = self.quest_edit_step_var.get().strip()
        if not raw_id or not raw_id.isdigit():
            messagebox.showwarning("Thông báo", "Vui lòng nhập ID nhiệm vụ hợp lệ.")
            return
        if not raw_step or not raw_step.isdigit():
            messagebox.showwarning("Thông báo", "Vui lòng nhập Bước hợp lệ (1-999).")
            return

        qid = int(raw_id)
        step = int(raw_step)
        if step < 1 or step > 999:
            messagebox.showwarning("Thông báo", "Bước nhiệm vụ phải từ 1 đến 999.")
            return

        marks = GameDataCache.get_marks()
        m_name = marks.get(qid, {}).get("name", f"Nhiệm vụ {qid}")
        if qid not in marks:
            if not messagebox.askyesno("Cảnh báo", f"ID {qid} không nằm trong dữ liệu Mark_C.dat của game.\nBạn có chắc chắn muốn thêm không?"):
                return

        try:
            # Tự động sao lưu trước khi lưu
            self._quest_backup_character(char["id"])
            MySqlExecutor.execute(
                "INSERT INTO character_missions (char_id, mission_id, step) VALUES (%s, %s, %s) "
                "ON DUPLICATE KEY UPDATE step = VALUES(step);",
                (char["id"], qid, step)
            )
            self._quest_load_char_missions()
            self._quest_log(f"Đã lưu nhiệm vụ [{qid}] {m_name} ở bước {step}.", "#107C41")
            messagebox.showinfo("Thành công", f"Đã lưu thành công nhiệm vụ:\n[{qid}] {m_name}\nở Bước: {step}")
        except Exception as e:
            self._quest_log(f"Lỗi lưu nhiệm vụ: {e}", "#D13438")
            messagebox.showerror("Lỗi", str(e))

    def _quest_advance_step(self):
        """Nhảy qua bước tiếp theo (+1) để vượt đoạn hội thoại / kẹt nhiệm vụ."""
        cur = self.quest_edit_step_var.get().strip()
        try:
            val = int(cur) if cur.isdigit() else 1
            self.quest_edit_step_var.set(str(val + 1))
            self._quest_save_step()
        except Exception:
            pass

    def _quest_reset_step_1(self):
        """Đặt lại nhiệm vụ về bước 1 để làm lại từ đầu."""
        self.quest_edit_step_var.set("1")
        self._quest_save_step()

    def _quest_delete_mission(self):
        """Xóa nhiệm vụ khỏi nhân vật."""
        char = self._quest_get_selected_char()
        if not char:
            messagebox.showwarning("Thông báo", "Vui lòng chọn nhân vật.")
            return
        raw_id = self.quest_edit_id_var.get().strip()
        if not raw_id or not raw_id.isdigit():
            messagebox.showwarning("Thông báo", "Vui lòng chọn hoặc nhập ID nhiệm vụ cần xóa.")
            return
        qid = int(raw_id)
        marks = GameDataCache.get_marks()
        m_name = marks.get(qid, {}).get("name", f"Nhiệm vụ {qid}")

        if not messagebox.askyesno("Xác nhận xóa", f"Bạn có chắc muốn xóa nhiệm vụ [{qid}] {m_name} khỏi nhân vật [{char['name']}]?\n(Hệ thống sẽ tự động sao lưu SQL trước khi xóa)"):
            return

        try:
            self._quest_backup_character(char["id"])
            MySqlExecutor.execute(
                "DELETE FROM character_missions WHERE char_id = %s AND mission_id = %s;",
                (char["id"], qid)
            )
            self._quest_load_char_missions()
            self.quest_edit_id_var.set("")
            self.quest_edit_step_var.set("1")
            self._quest_log(f"Đã xóa nhiệm vụ [{qid}] {m_name}.", "#107C41")
            messagebox.showinfo("Thành công", f"Đã xóa nhiệm vụ [{qid}] {m_name} thành công.")
        except Exception as e:
            self._quest_log(f"Lỗi xóa nhiệm vụ: {e}", "#D13438")
            messagebox.showerror("Lỗi", str(e))

    def _quest_search_library(self, event=None):
        """Tìm kiếm nhiệm vụ trong kho 3,448 nhiệm vụ game."""
        q = self.quest_lib_search_var.get().strip().lower()
        marks = GameDataCache.get_marks()
        for item in self.quest_lib_tree.get_children():
            self.quest_lib_tree.delete(item)

        count = 0
        for qid, m in marks.items():
            name = m.get("name", "")
            desc = m.get("desc", "")
            if not q or (q in str(qid) or q in name.lower() or q in desc.lower()):
                self.quest_lib_tree.insert("", "end", values=(qid, name, desc))
                count += 1
                if count >= 200:  # Giới hạn 200 dòng để giao diện phản hồi cực nhanh
                    break

    def _quest_on_lib_double_click(self, event=None):
        sel = self.quest_lib_tree.selection()
        if not sel:
            return
        vals = self.quest_lib_tree.item(sel[0], "values")
        if vals:
            self.quest_edit_id_var.set(str(vals[0]))
            self.quest_edit_step_var.set("1")

    def _quest_add_from_library(self):
        sel = self.quest_lib_tree.selection()
        if not sel:
            messagebox.showwarning("Thông báo", "Vui lòng chọn 1 nhiệm vụ trong thư viện bên phải.")
            return
        vals = self.quest_lib_tree.item(sel[0], "values")
        if vals:
            self.quest_edit_id_var.set(str(vals[0]))
            self.quest_edit_step_var.set("1")
            self._quest_save_step()

    # ========================================================================
    # TAB 8: CỔNG TELEPORT & DỊCH CHUYỂN
    # ========================================================================
    def _init_tab_teleport(self):
        """Khởi tạo giao diện Tab [8] Cổng Teleport & Dịch chuyển."""
        self.tp_chars_map = {}
        self.tp_warps_cache = []

        # 1. Thanh công cụ trên cùng: Chọn nhân vật & Vị trí hiện tại
        top_bar = ttk.Frame(self.tab_teleport)
        top_bar.pack(fill="x", padx=4, pady=(2, 8))

        ttk.Label(top_bar, text="Nhân vật:", font=self.font_bold).pack(side="left", padx=(0, 6))
        self.tp_char_combo = ttk.Combobox(top_bar, state="readonly", width=38, font=self.font_main)
        self.tp_char_combo.pack(side="left", padx=(0, 8))
        self.tp_char_combo.bind("<<ComboboxSelected>>", self._tp_on_char_selected)

        ttk.Button(top_bar, text="🔄 Làm mới", command=self._tp_refresh_chars).pack(side="left", padx=(0, 6))

        self.tp_location_lbl = tk.Label(
            top_bar, text="📍 Vị trí: Chưa chọn nhân vật", font=self.font_bold, fg="#005A9E", bg="#F4F4F4", padx=10, pady=3, relief="groove"
        )
        self.tp_location_lbl.pack(side="left", fill="x", expand=True, padx=(0, 6))

        ttk.Button(top_bar, text="🔄 Tọa độ", command=self._tp_refresh_location).pack(side="right")

        # 2. Khu vực chia đôi (PanedWindow): Trái là 41 Cổng Thiên Hành, Phải là Tra cứu Map & Tự do
        paned = ttk.PanedWindow(self.tab_teleport, orient="horizontal")
        paned.pack(fill="both", expand=True, padx=2, pady=2)

        # KHU VỰC TRÁI: 41 Cổng Thiên Hành Dị Năng
        left_box = ttk.LabelFrame(paned, text=" 41 Cổng Thiên Hành Dị Năng (Chính thức) ", padding=6)
        paned.add(left_box, weight=3)

        warp_actions = ttk.Frame(left_box)
        warp_actions.pack(fill="x", pady=(0, 6))

        ttk.Button(
            warp_actions, text="🌟 Mở khóa TẤT CẢ cổng (Warp All)", style="Success.TButton",
            command=self._tp_warp_all
        ).pack(side="left", padx=(0, 6))

        ttk.Button(
            warp_actions, text="🚀 Dịch chuyển đến cổng chọn", style="Primary.TButton",
            command=self._tp_warp_to_selected
        ).pack(side="left")

        # Treeview cổng warp
        warp_tree_frame = ttk.Frame(left_box)
        warp_tree_frame.pack(fill="both", expand=True)

        w_cols = ("idx", "name", "scene", "coords", "status")
        self.tp_warp_tree = ttk.Treeview(warp_tree_frame, columns=w_cols, show="headings", selectmode="browse")
        self.tp_warp_tree.heading("idx", text="STT")
        self.tp_warp_tree.heading("name", text="Tên Cổng")
        self.tp_warp_tree.heading("scene", text="Bản đồ")
        self.tp_warp_tree.heading("coords", text="Tọa độ (X, Y)")
        self.tp_warp_tree.heading("status", text="Trạng thái")

        self.tp_warp_tree.column("idx", width=40, anchor="center")
        self.tp_warp_tree.column("name", width=120, anchor="w")
        self.tp_warp_tree.column("scene", width=150, anchor="w")
        self.tp_warp_tree.column("coords", width=110, anchor="center")
        self.tp_warp_tree.column("status", width=90, anchor="center")

        w_scroll_y = ttk.Scrollbar(warp_tree_frame, orient="vertical", command=self.tp_warp_tree.yview)
        self.tp_warp_tree.configure(yscrollcommand=w_scroll_y.set)
        self.tp_warp_tree.pack(side="left", fill="both", expand=True)
        w_scroll_y.pack(side="right", fill="y")
        self.tp_warp_tree.bind("<Double-1>", self._tp_warp_to_selected)

        # KHU VỰC PHẢI: Tra cứu 3,677 Map & Tùy chọn tọa độ & Cứu kẹt
        right_box = ttk.LabelFrame(paned, text=" Tra cứu Bản đồ & Dịch chuyển Tự do ", padding=6)
        paned.add(right_box, weight=3)

        # Thanh tìm map
        scene_filter_bar = ttk.Frame(right_box)
        scene_filter_bar.pack(fill="x", pady=(0, 4))
        ttk.Label(scene_filter_bar, text="🔍 Tìm map:").pack(side="left", padx=(0, 4))
        self.tp_scene_search_var = tk.StringVar()
        self.tp_scene_search_ent = ttk.Entry(scene_filter_bar, textvariable=self.tp_scene_search_var, width=18)
        self.tp_scene_search_ent.pack(side="left", fill="x", expand=True, padx=(0, 4))
        self.tp_scene_search_ent.bind("<KeyRelease>", self._tp_filter_scenes)
        ttk.Button(scene_filter_bar, text="Tìm", width=5, command=self._tp_filter_scenes).pack(side="left")

        # Treeview danh sách map
        scene_tree_frame = ttk.Frame(right_box)
        scene_tree_frame.pack(fill="both", expand=True)

        sc_cols = ("id", "name")
        self.tp_scene_tree = ttk.Treeview(scene_tree_frame, columns=sc_cols, show="headings", selectmode="browse")
        self.tp_scene_tree.heading("id", text="Map ID")
        self.tp_scene_tree.heading("name", text="Tên Bản đồ (3,677 Map)")
        self.tp_scene_tree.column("id", width=75, anchor="center")
        self.tp_scene_tree.column("name", width=230, anchor="w")

        sc_scroll_y = ttk.Scrollbar(scene_tree_frame, orient="vertical", command=self.tp_scene_tree.yview)
        self.tp_scene_tree.configure(yscrollcommand=sc_scroll_y.set)
        self.tp_scene_tree.pack(side="left", fill="both", expand=True)
        sc_scroll_y.pack(side="right", fill="y")
        self.tp_scene_tree.bind("<<TreeviewSelect>>", self._tp_on_scene_selected)

        # Form nhập tọa độ thủ công
        coord_box = ttk.LabelFrame(right_box, text=" Nhập tọa độ đích ", padding=6)
        coord_box.pack(fill="x", pady=(6, 4))

        coord_row = ttk.Frame(coord_box)
        coord_row.pack(fill="x", pady=2)

        ttk.Label(coord_row, text="Map ID:").pack(side="left", padx=(0, 3))
        self.tp_map_id_var = tk.StringVar(value="12001")
        ttk.Entry(coord_row, textvariable=self.tp_map_id_var, width=7, font=self.font_bold).pack(side="left", padx=(0, 6))

        ttk.Label(coord_row, text="X:").pack(side="left", padx=(0, 3))
        self.tp_x_var = tk.StringVar(value="500")
        ttk.Entry(coord_row, textvariable=self.tp_x_var, width=6).pack(side="left", padx=(0, 6))

        ttk.Label(coord_row, text="Y:").pack(side="left", padx=(0, 3))
        self.tp_y_var = tk.StringVar(value="500")
        ttk.Entry(coord_row, textvariable=self.tp_y_var, width=6).pack(side="left", padx=(0, 8))

        ttk.Button(
            coord_row, text="🚀 Dịch chuyển ngay", style="Primary.TButton",
            command=self._tp_custom_teleport
        ).pack(side="left")

        # Cứu hộ khẩn cấp
        rescue_box = ttk.LabelFrame(right_box, text=" 🆘 Cứu hộ khẩn cấp (1-Click) ", padding=6)
        rescue_box.pack(fill="x", pady=(2, 0))

        r_row1 = ttk.Frame(rescue_box)
        r_row1.pack(fill="x", pady=2)
        ttk.Button(
            r_row1, text="🏰 Về Trác Quận (12001)",
            command=lambda: self._tp_execute_teleport(12001, 310, 1530, "Cửa thành Trác Quận")
        ).pack(side="left", fill="x", expand=True, padx=(0, 4))
        ttk.Button(
            r_row1, text="🌱 Về Làng Tân Thủ (10851)",
            command=lambda: self._tp_execute_teleport(10851, 500, 500, "Làng Tân Thủ")
        ).pack(side="left", fill="x", expand=True)

        r_row2 = ttk.Frame(rescue_box)
        r_row2.pack(fill="x", pady=2)
        ttk.Button(
            r_row2, text="🔓 Thoát Nhà Tù (10701 -> 12001)", style="Warning.TButton",
            command=lambda: self._tp_execute_teleport(12001, 310, 1530, "Giải thoát khỏi Nhà Tù")
        ).pack(side="left", fill="x", expand=True, padx=(0, 4))
        ttk.Button(
            r_row2, text="🏯 Về Lạc Dương (13001)",
            command=lambda: self._tp_execute_teleport(13001, 750, 590, "Thành Lạc Dương")
        ).pack(side="left", fill="x", expand=True)

        # Thanh trạng thái dưới cùng của tab
        self.tp_status_lbl = tk.Label(
            self.tab_teleport, text="Sẵn sàng.", font=self.font_main, fg="#555555", anchor="w"
        )
        self.tp_status_lbl.pack(fill="x", pady=(4, 0))

    def _tp_log(self, text: str, color: str = "#333333"):
        if hasattr(self, "tp_status_lbl"):
            self.tp_status_lbl.config(text=text, fg=color)

    def _tp_refresh_chars(self):
        """Tải danh sách nhân vật cho Tab Teleport."""
        def worker():
            try:
                self.after(0, lambda: self._tp_log("Đang tải danh sách nhân vật...", "#005A9E"))
                rows = MySqlExecutor.query("SELECT id, name, level, map_id, x, y FROM characters ORDER BY id;")
                items = []
                mapping = {}
                scenes = GameDataCache.get_scenes()
                for r in rows:
                    cid, name, lv, mid, x, y = int(r[0]), str(r[1]), int(r[2]), int(r[3]), int(r[4]), int(r[5])
                    map_name = scenes.get(mid, f"Map {mid}")
                    disp = f"[{cid}] {name} - Lv.{lv} ({map_name})"
                    items.append(disp)
                    mapping[disp] = {
                        "id": cid, "name": name, "level": lv, "map_id": mid,
                        "map_name": map_name, "x": x, "y": y
                    }

                def update_ui():
                    self.tp_chars_map = mapping
                    self.tp_char_combo["values"] = items
                    if items:
                        cur = self.tp_char_combo.get()
                        if not cur or cur not in mapping:
                            self.tp_char_combo.current(0)
                        self._tp_on_char_selected()
                    else:
                        self.tp_location_lbl.config(text="📍 Vị trí: Không tìm thấy nhân vật nào")
                    self._tp_log(f"Đã nạp {len(items)} nhân vật.", "#107C41")

                self.after(0, update_ui)
            except Exception as e:
                self.after(0, lambda: self._tp_log(f"Lỗi tải nhân vật: {e}", "#D13438"))

        threading.Thread(target=worker, daemon=True).start()

    def _tp_get_selected_char(self) -> dict | None:
        sel = self.tp_char_combo.get()
        if not sel or sel not in self.tp_chars_map:
            return None
        return self.tp_chars_map[sel]

    def _tp_on_char_selected(self, event=None):
        self._tp_refresh_location()
        self._tp_load_warps()

    def _tp_refresh_location(self):
        """Cập nhật lại tọa độ hiện tại của nhân vật từ database."""
        char = self._tp_get_selected_char()
        if not char:
            return

        def worker():
            try:
                char_id = char["id"]
                rows = MySqlExecutor.query("SELECT map_id, x, y FROM characters WHERE id = %s;", (char_id,))
                if rows:
                    mid, x, y = int(rows[0][0]), int(rows[0][1]), int(rows[0][2])
                    scenes = GameDataCache.get_scenes()
                    map_name = scenes.get(mid, f"Map {mid}")
                    char["map_id"] = mid
                    char["map_name"] = map_name
                    char["x"] = x
                    char["y"] = y

                    def update_ui():
                        self.tp_location_lbl.config(
                            text=f"📍 [{char['id']}] {char['name']}: Map [{mid}] {map_name} (X: {x}, Y: {y})"
                        )
                        self.tp_map_id_var.set(str(mid))
                        self.tp_x_var.set(str(x))
                        self.tp_y_var.set(str(y))

                    self.after(0, update_ui)
            except Exception as e:
                self.after(0, lambda: self._tp_log(f"Lỗi kiểm tra vị trí: {e}", "#D13438"))

        threading.Thread(target=worker, daemon=True).start()

    def _tp_load_warps(self):
        """Nạp danh sách 41 cổng dịch chuyển Thiên Hành và trạng thái mở khóa."""
        char = self._tp_get_selected_char()
        warps = GameDataCache.get_warps()
        if not warps:
            return

        def worker():
            unlocked_flags = set()
            if char:
                try:
                    rows = MySqlExecutor.query(
                        "SELECT flag_id FROM character_bit_flags WHERE char_id = %s AND flag_id BETWEEN 936 AND 976;",
                        (char["id"],)
                    )
                    for r in rows:
                        unlocked_flags.add(int(r[0]))
                except Exception:
                    pass

            def update_ui():
                for item in self.tp_warp_tree.get_children():
                    self.tp_warp_tree.delete(item)
                for w in warps:
                    is_unlocked = w["bit_id"] in unlocked_flags
                    st_text = "✅ Đã mở" if is_unlocked else "🔒 Chưa mở"
                    self.tp_warp_tree.insert(
                        "", "end",
                        values=(w["index"] + 1, w["name"], f"[{w['scene_id']}] {w['scene_name']}", f"{w['x']}, {w['y']}", st_text)
                    )

            self.after(0, update_ui)

        threading.Thread(target=worker, daemon=True).start()

    def _tp_warp_all(self):
        """Mở khóa toàn bộ 41 cổng Thiên Hành Dị Năng cho nhân vật."""
        char = self._tp_get_selected_char()
        if not char:
            messagebox.showwarning("Thông báo", "Vui lòng chọn nhân vật.")
            return

        if not messagebox.askyesno(
            "Mở khóa toàn bộ cổng",
            f"Bạn có muốn mở khóa TOÀN BỘ 41 cổng dịch chuyển Thiên Hành Dị Năng cho nhân vật [{char['name']}] không?"
        ):
            return

        warps = GameDataCache.get_warps()
        char_id = char["id"]

        def worker():
            try:
                self.after(0, lambda: self._tp_log("Đang mở khóa toàn bộ cổng Thiên Hành...", "#005A9E"))
                count = 0
                for w in warps:
                    bit_id = w["bit_id"]
                    if bit_id > 0:
                        MySqlExecutor.execute(
                            "INSERT IGNORE INTO character_bit_flags (char_id, flag_id) VALUES (%s, %s);",
                            (char_id, bit_id)
                        )
                        count += 1

                def on_done():
                    self._tp_load_warps()
                    self._tp_log(f"Đã mở khóa thành công toàn bộ {count} cổng Thiên Hành!", "#107C41")
                    messagebox.showinfo("Thành công", f"Đã mở khóa thành công toàn bộ {count} cổng Thiên Hành Dị Năng cho [{char['name']}]!\nTrong game có thể tự do mở menu Thiên Hành để bay tới bất kỳ đâu.")

                self.after(0, on_done)
            except Exception as e:
                self.after(0, lambda: self._tp_log(f"Lỗi mở cổng: {e}", "#D13438"))

        threading.Thread(target=worker, daemon=True).start()

    def _tp_warp_to_selected(self, event=None):
        """Dịch chuyển nhân vật đến cổng Thiên Hành đang chọn."""
        char = self._tp_get_selected_char()
        if not char:
            messagebox.showwarning("Thông báo", "Vui lòng chọn nhân vật.")
            return

        sel = self.tp_warp_tree.selection()
        if not sel:
            messagebox.showwarning("Thông báo", "Vui lòng chọn 1 cổng trong danh sách.")
            return

        vals = self.tp_warp_tree.item(sel[0], "values")
        if not vals:
            return

        try:
            stt = int(vals[0]) - 1
            warps = GameDataCache.get_warps()
            if 0 <= stt < len(warps):
                target = warps[stt]
                self._tp_execute_teleport(target["scene_id"], target["x"], target["y"], target["name"])
        except Exception as e:
            messagebox.showerror("Lỗi", str(e))

    def _tp_filter_scenes(self, event=None):
        """Tìm kiếm map trong 3,677 bản đồ game."""
        q = self.tp_scene_search_var.get().strip().lower()
        scenes = GameDataCache.get_scenes()
        for item in self.tp_scene_tree.get_children():
            self.tp_scene_tree.delete(item)

        count = 0
        for sid, sname in scenes.items():
            if not q or (q in str(sid) or q in sname.lower()):
                self.tp_scene_tree.insert("", "end", values=(sid, sname))
                count += 1
                if count >= 150:
                    break

    def _tp_on_scene_selected(self, event=None):
        sel = self.tp_scene_tree.selection()
        if not sel:
            return
        vals = self.tp_scene_tree.item(sel[0], "values")
        if vals:
            self.tp_map_id_var.set(str(vals[0]))

    def _tp_custom_teleport(self):
        """Dịch chuyển nhân vật đến Map ID và Tọa độ nhập tay."""
        char = self._tp_get_selected_char()
        if not char:
            messagebox.showwarning("Thông báo", "Vui lòng chọn nhân vật.")
            return

        mid = self.tp_map_id_var.get().strip()
        x = self.tp_x_var.get().strip()
        y = self.tp_y_var.get().strip()

        if not mid.isdigit() or not x.isdigit() or not y.isdigit():
            messagebox.showwarning("Thông báo", "Vui lòng nhập Map ID, X, Y là các số nguyên hợp lệ.")
            return

        scenes = GameDataCache.get_scenes()
        map_name = scenes.get(int(mid), f"Map {mid}")
        self._tp_execute_teleport(int(mid), int(x), int(y), map_name)

    def _tp_execute_teleport(self, map_id: int, x: int, y: int, note: str = ""):
        """Thực thi câu lệnh SQL dịch chuyển vị trí nhân vật."""
        char = self._tp_get_selected_char()
        if not char:
            messagebox.showwarning("Thông báo", "Vui lòng chọn nhân vật trước khi dịch chuyển.")
            return

        char_id = char["id"]
        char_name = char["name"]

        try:
            # Cập nhật cả vị trí hiện tại và điểm lưu ghi nhớ (record) để nhân vật không bị văng về map cũ khi chết/hồi sinh
            MySqlExecutor.execute(
                "UPDATE characters SET map_id = %s, x = %s, y = %s, record_map_id = %s, record_x = %s, record_y = %s WHERE id = %s;",
                (map_id, x, y, map_id, x, y, char_id)
            )
            self._tp_refresh_location()
            self._tp_log(f"Đã dịch chuyển [{char_name}] đến Map [{map_id}] ({x}, {y}) - {note}.", "#107C41")
            messagebox.showinfo(
                "Dịch chuyển thành công",
                f"Đã chuyển nhân vật [{char_name}] đến:\n"
                f"- Địa điểm: {note}\n"
                f"- Map ID: {map_id}\n"
                f"- Tọa độ: X={x}, Y={y}\n\n"
                f"💡 Mẹo: Nếu nhân vật đang đăng nhập trong game, hãy chuyển map hoặc thoát game đăng nhập lại để cập nhật vị trí mới."
            )
        except Exception as e:
            self._tp_log(f"Lỗi dịch chuyển: {e}", "#D13438")
            messagebox.showerror("Lỗi dịch chuyển", str(e))

    # ========================================================================
    # TAB 9: KỊCH BẢN EVE.EMG (EVE INSPECTOR & EDITOR)
    # ========================================================================
    def _init_tab_eve(self):
        """Khởi tạo giao diện Tab [9] Kịch bản Eve.emg (Sự kiện, Cổng & NPC)."""
        self.eve_archive = None
        self.eve_current_scene = None
        self.live_events_history = []
        self.live_current_event = None

        # 1. Thanh tìm kiếm bản đồ
        top_bar = ttk.Frame(self.tab_eve)
        top_bar.pack(fill="x", padx=4, pady=(2, 8))

        ttk.Label(top_bar, text="Map ID:", font=self.font_bold).pack(side="left", padx=(0, 4))
        self.eve_scene_id_var = tk.StringVar(value="10851")
        self.eve_scene_id_ent = ttk.Entry(top_bar, textvariable=self.eve_scene_id_var, width=8, font=self.font_bold)
        self.eve_scene_id_ent.pack(side="left", padx=(0, 6))

        ttk.Button(top_bar, text="🔍 Xem Bản đồ", style="Primary.TButton", command=self._eve_load_scene).pack(side="left", padx=(0, 10))

        self.eve_scene_name_lbl = tk.Label(
            top_bar, text="[10851] Làng Tân Thủ", font=self.font_bold, fg="#005A9E", bg="#F4F4F4", padx=10, pady=3, relief="groove"
        )
        self.eve_scene_name_lbl.pack(side="left", padx=(0, 10))

        self.eve_status_lbl = tk.Label(
            top_bar, text="Eve.emg (3,823 bản đồ)", font=self.font_main, fg="#666666"
        )
        self.eve_status_lbl.pack(side="right")

        # 2. Sub-Notebook bên trong Tab 9
        self.eve_sub_nb = ttk.Notebook(self.tab_eve)
        self.eve_sub_nb.pack(fill="both", expand=True, padx=2, pady=2)

        # ── SUBTAB 1: CỔNG TELEPORT (DOORS) ──
        sub_doors = ttk.Frame(self.eve_sub_nb, padding=6)
        self.eve_sub_nb.add(sub_doors, text="🚪 Cổng Teleport & Đích đến")

        paned_door = ttk.PanedWindow(sub_doors, orient="horizontal")
        paned_door.pack(fill="both", expand=True)

        # Left: Door Data
        left_d = ttk.LabelFrame(paned_door, text=" Cổng dịch chuyển trên map (DoorData) ", padding=6)
        paned_door.add(left_d, weight=3)

        d_cols = ("id", "coords", "target", "target_coords", "type", "events")
        self.eve_door_tree = ttk.Treeview(left_d, columns=d_cols, show="headings", selectmode="browse")
        self.eve_door_tree.heading("id", text="Door ID")
        self.eve_door_tree.heading("coords", text="Tọa độ Cổng")
        self.eve_door_tree.heading("target", text="Đích đến (Nhà / Tiệm / Thành)")
        self.eve_door_tree.heading("target_coords", text="Tọa độ Đích")
        self.eve_door_tree.heading("type", text="Loại cửa")
        self.eve_door_tree.heading("events", text="EveNo")
        self.eve_door_tree.column("id", width=55, anchor="center")
        self.eve_door_tree.column("coords", width=95, anchor="center")
        self.eve_door_tree.column("target", width=180, anchor="w")
        self.eve_door_tree.column("target_coords", width=95, anchor="center")
        self.eve_door_tree.column("type", width=130, anchor="w")
        self.eve_door_tree.column("events", width=60, anchor="center")
        self.eve_door_tree.pack(side="left", fill="both", expand=True)
        d_sc = ttk.Scrollbar(left_d, orient="vertical", command=self.eve_door_tree.yview)
        self.eve_door_tree.configure(yscrollcommand=d_sc.set)
        d_sc.pack(side="right", fill="y")
        self.eve_door_tree.bind("<<TreeviewSelect>>", self._eve_on_select_door)

        # Right: SceneInfo Data
        right_i = ttk.LabelFrame(paned_door, text=" Điểm mốc dịch chuyển nạp sẵn (SceneInfoData) ", padding=6)
        paned_door.add(right_i, weight=2)

        i_cols = ("eve_no", "target_scene", "target_coords", "name")
        self.eve_info_tree = ttk.Treeview(right_i, columns=i_cols, show="headings", selectmode="browse")
        self.eve_info_tree.heading("eve_no", text="EveNo (Key)")
        self.eve_info_tree.heading("target_scene", text="Bản đồ Đích")
        self.eve_info_tree.heading("target_coords", text="Tọa độ Đích")
        self.eve_info_tree.heading("name", text="Tên Vị Trí / Khu vực")
        self.eve_info_tree.column("eve_no", width=75, anchor="center")
        self.eve_info_tree.column("target_scene", width=160, anchor="w")
        self.eve_info_tree.column("target_coords", width=100, anchor="center")
        self.eve_info_tree.column("name", width=130, anchor="w")
        self.eve_info_tree.pack(side="left", fill="both", expand=True)
        i_sc = ttk.Scrollbar(right_i, orient="vertical", command=self.eve_info_tree.yview)
        self.eve_info_tree.configure(yscrollcommand=i_sc.set)
        i_sc.pack(side="right", fill="y")
        self.eve_info_tree.bind("<<TreeviewSelect>>", self._eve_on_select_info)

        # Form chỉnh sửa cổng
        edit_box = ttk.LabelFrame(sub_doors, text=" Chỉnh sửa Đích đến của Cổng (Ghi trực tiếp vào Eve.emg) ", padding=6)
        edit_box.pack(fill="x", pady=(6, 0))

        e_row = ttk.Frame(edit_box)
        e_row.pack(fill="x")

        ttk.Label(e_row, text="EveNo:").pack(side="left", padx=(0, 3))
        self.eve_edit_eveno_var = tk.StringVar()
        ttk.Entry(e_row, textvariable=self.eve_edit_eveno_var, width=6, state="readonly").pack(side="left", padx=(0, 8))

        ttk.Label(e_row, text="Map Đích:").pack(side="left", padx=(0, 3))
        self.eve_edit_scene_var = tk.StringVar()
        ttk.Entry(e_row, textvariable=self.eve_edit_scene_var, width=8).pack(side="left", padx=(0, 8))

        ttk.Label(e_row, text="X Đích:").pack(side="left", padx=(0, 3))
        self.eve_edit_x_var = tk.StringVar()
        ttk.Entry(e_row, textvariable=self.eve_edit_x_var, width=6).pack(side="left", padx=(0, 8))

        ttk.Label(e_row, text="Y Đích:").pack(side="left", padx=(0, 3))
        self.eve_edit_y_var = tk.StringVar()
        ttk.Entry(e_row, textvariable=self.eve_edit_y_var, width=6).pack(side="left", padx=(0, 12))

        ttk.Button(e_row, text="💾 Lưu vào Eve.emg", style="Success.TButton", command=self._eve_save_door).pack(side="left")
        ttk.Button(
            e_row, text="🔓 Mở Khóa Cổng Này (Bỏ điều kiện chặn)", style="Primary.TButton",
            command=self._eve_unlock_door
        ).pack(side="left", padx=(10, 0))

        # ── SUBTAB 2: NPC & SỰ KIỆN TRÊN MAP ──
        sub_npcs = ttk.Frame(self.eve_sub_nb, padding=6)
        self.eve_sub_nb.add(sub_npcs, text="🧙 NPC & Sự kiện trên Map")

        paned_npc = ttk.PanedWindow(sub_npcs, orient="horizontal")
        paned_npc.pack(fill="both", expand=True)

        left_npc = ttk.LabelFrame(paned_npc, text=" Danh sách NPC (NpcData) ", padding=6)
        paned_npc.add(left_npc, weight=2)

        n_cols = ("id", "npc_id", "name", "coords", "events")
        self.eve_npc_tree = ttk.Treeview(left_npc, columns=n_cols, show="headings", selectmode="browse")
        self.eve_npc_tree.heading("id", text="STT")
        self.eve_npc_tree.heading("npc_id", text="NPC ID")
        self.eve_npc_tree.heading("name", text="Tên NPC")
        self.eve_npc_tree.heading("coords", text="Tọa độ (X, Y)")
        self.eve_npc_tree.heading("events", text="Events")
        self.eve_npc_tree.column("id", width=45, anchor="center")
        self.eve_npc_tree.column("npc_id", width=65, anchor="center")
        self.eve_npc_tree.column("name", width=130, anchor="w")
        self.eve_npc_tree.column("coords", width=95, anchor="center")
        self.eve_npc_tree.column("events", width=70, anchor="center")
        self.eve_npc_tree.pack(side="left", fill="both", expand=True)
        n_sc = ttk.Scrollbar(left_npc, orient="vertical", command=self.eve_npc_tree.yview)
        self.eve_npc_tree.configure(yscrollcommand=n_sc.set)
        n_sc.pack(side="right", fill="y")
        self.eve_npc_tree.bind("<<TreeviewSelect>>", self._eve_on_select_npc)

        right_ev = ttk.LabelFrame(paned_npc, text=" Chi tiết Sự kiện NPC (NpcEventData) ", padding=6)
        paned_npc.add(right_ev, weight=3)

        ev_top = ttk.Frame(right_ev)
        ev_top.pack(fill="x", pady=(0, 4))
        self.eve_ev_filter_lbl = tk.Label(
            ev_top, text="Tất cả sự kiện của Bản đồ (Click vào NPC bên trái để lọc)",
            font=self.font_bold, fg="#005A9E", bg="#F4F4F4", padx=6, pady=2, relief="groove"
        )
        self.eve_ev_filter_lbl.pack(side="left", fill="x", expand=True, padx=(0, 6))
        ttk.Button(ev_top, text="🔄 Hiện tất cả", command=self._eve_show_all_events).pack(side="right")

        ev_cols = ("eve_no", "action", "when", "conditions", "details")
        self.eve_event_tree = ttk.Treeview(right_ev, columns=ev_cols, show="headings", selectmode="browse")
        self.eve_event_tree.heading("eve_no", text="EveNo")
        self.eve_event_tree.heading("action", text="Hành động / Tác vụ chính")
        self.eve_event_tree.heading("when", text="Kích hoạt")
        self.eve_event_tree.heading("conditions", text="Số Đ/K")
        self.eve_event_tree.heading("details", text="Chi tiết (Nhiệm vụ / Vật phẩm / Dịch chuyển)")
        self.eve_event_tree.column("eve_no", width=55, anchor="center")
        self.eve_event_tree.column("action", width=180, anchor="w")
        self.eve_event_tree.column("when", width=95, anchor="center")
        self.eve_event_tree.column("conditions", width=55, anchor="center")
        self.eve_event_tree.column("details", width=260, anchor="w")
        self.eve_event_tree.pack(side="left", fill="both", expand=True)
        ev_sc = ttk.Scrollbar(right_ev, orient="vertical", command=self.eve_event_tree.yview)
        self.eve_event_tree.configure(yscrollcommand=ev_sc.set)
        ev_sc.pack(side="right", fill="y")

        # ── SUBTAB 3: TRA CỨU NHIỆM VỤ TOÀN CỤC ──
        sub_quest = ttk.Frame(self.eve_sub_nb, padding=6)
        self.eve_sub_nb.add(sub_quest, text="🔍 Phân tích Lộ trình Nhiệm vụ Toàn Cục")

        q_bar = ttk.Frame(sub_quest)
        q_bar.pack(fill="x", pady=(0, 6))

        ttk.Label(q_bar, text="Nhập ID Nhiệm vụ:", font=self.font_bold).pack(side="left", padx=(0, 4))
        self.eve_q_search_var = tk.StringVar(value="10002")
        self.eve_q_search_ent = ttk.Entry(q_bar, textvariable=self.eve_q_search_var, width=10)
        self.eve_q_search_ent.pack(side="left", padx=(0, 8))

        ttk.Button(q_bar, text="🔍 Quét Toàn Bộ 3,823 Bản Đồ", style="Primary.TButton", command=self._eve_search_mission).pack(side="left")

        self.eve_q_res_lbl = ttk.Label(q_bar, text="Sẵn sàng quét kịch bản", foreground="#666666")
        self.eve_q_res_lbl.pack(side="right")

        q_tree_frame = ttk.Frame(sub_quest)
        q_tree_frame.pack(fill="both", expand=True)

        q_cols = ("scene", "eve_no", "npc", "type", "details")
        self.eve_q_tree = ttk.Treeview(q_tree_frame, columns=q_cols, show="headings")
        self.eve_q_tree.heading("scene", text="Bản đồ")
        self.eve_q_tree.heading("eve_no", text="EveNo")
        self.eve_q_tree.heading("npc", text="NPC liên quan")
        self.eve_q_tree.heading("type", text="Loại tác động")
        self.eve_q_tree.heading("details", text="Chi tiết điều kiện / Kết quả")

        self.eve_q_tree.column("scene", width=150)
        self.eve_q_tree.column("eve_no", width=60, anchor="center")
        self.eve_q_tree.column("npc", width=130)
        self.eve_q_tree.column("type", width=120, anchor="center")
        self.eve_q_tree.column("details", width=420)

        self.eve_q_tree.pack(side="left", fill="both", expand=True)
        q_sc = ttk.Scrollbar(q_tree_frame, orient="vertical", command=self.eve_q_tree.yview)
        self.eve_q_tree.configure(yscrollcommand=q_sc.set)
        # ── SUBTAB 4: LIVE EVENT INSPECTOR (THEO DÕI THỜI GIAN THỰC) ──
        sub_live = ttk.Frame(self.eve_sub_nb, padding=6)
        self.eve_sub_nb.add(sub_live, text="🔴 Live Event Inspector (Real-time)")

        # Top control bar
        live_bar = ttk.Frame(sub_live)
        live_bar.pack(fill="x", pady=(0, 6))

        self.eve_live_status_lbl = tk.Label(
            live_bar, text="🟢 Đang giám sát sự kiện thời gian thực (Sẵn sàng)",
            font=self.font_bold, fg="#107C41", bg="#F4F4F4", padx=8, pady=3, relief="groove"
        )
        self.eve_live_status_lbl.pack(side="left", padx=(0, 10))

        ttk.Label(live_bar, text="Lọc:").pack(side="left", padx=(0, 4))
        self.eve_live_filter_var = tk.StringVar(value="Tất cả sự kiện")
        self.eve_live_filter_cb = ttk.Combobox(
            live_bar, textvariable=self.eve_live_filter_var,
            values=["Tất cả sự kiện", "Chỉ Lỗi & Kẹt (Error/Warn)", "Chỉ Cổng Teleport", "Chỉ NPC & Nhiệm vụ"],
            state="readonly", width=22
        )
        self.eve_live_filter_cb.pack(side="left", padx=(0, 10))
        self.eve_live_filter_cb.bind("<<ComboboxSelected>>", self._eve_live_apply_filter)

        self.eve_live_autoscroll_var = tk.BooleanVar(value=True)
        ttk.Checkbutton(live_bar, text="Tự động cuộn", variable=self.eve_live_autoscroll_var).pack(side="left", padx=(0, 10))

        ttk.Button(
            live_bar, text="🔄 Đọc lại nhật ký gần nhất", style="Primary.TButton",
            command=self._eve_live_reload_recent_logs
        ).pack(side="left", padx=(0, 10))

        ttk.Button(live_bar, text="🧹 Xóa Nhật Ký", command=self._eve_live_clear).pack(side="right")

        # Paned Window (Top: Treeview, Bottom: Detail Diagnostics)
        paned_live = ttk.PanedWindow(sub_live, orient="vertical")
        paned_live.pack(fill="both", expand=True)

        top_live_f = ttk.Frame(paned_live)
        paned_live.add(top_live_f, weight=3)

        live_cols = ("time", "char", "map", "action", "target", "status", "summary")
        self.eve_live_tree = ttk.Treeview(top_live_f, columns=live_cols, show="headings", selectmode="browse")
        self.eve_live_tree.heading("time", text="Thời gian")
        self.eve_live_tree.heading("char", text="Nhân vật")
        self.eve_live_tree.heading("map", text="Bản đồ")
        self.eve_live_tree.heading("action", text="Hành động")
        self.eve_live_tree.heading("target", text="Đối tượng (NPC / Cổng)")
        self.eve_live_tree.heading("status", text="Trạng thái")
        self.eve_live_tree.heading("summary", text="Tóm tắt phân tích")

        self.eve_live_tree.column("time", width=70, anchor="center")
        self.eve_live_tree.column("char", width=105, anchor="w")
        self.eve_live_tree.column("map", width=140, anchor="w")
        self.eve_live_tree.column("action", width=115, anchor="center")
        self.eve_live_tree.column("target", width=160, anchor="w")
        self.eve_live_tree.column("status", width=135, anchor="center")
        self.eve_live_tree.column("summary", width=330, anchor="w")

        # Tags styling
        self.eve_live_tree.tag_configure("success", foreground="#107C41")
        self.eve_live_tree.tag_configure("error", foreground="#D13438", font=self.font_bold)
        self.eve_live_tree.tag_configure("warn", foreground="#B8860B")
        self.eve_live_tree.tag_configure("info", foreground="#005A9E")

        self.eve_live_tree.pack(side="left", fill="both", expand=True)
        live_sc = ttk.Scrollbar(top_live_f, orient="vertical", command=self.eve_live_tree.yview)
        self.eve_live_tree.configure(yscrollcommand=live_sc.set)
        live_sc.pack(side="right", fill="y")
        self.eve_live_tree.bind("<<TreeviewSelect>>", self._eve_live_on_select)

        # Bottom detail & fix panel
        bot_live_f = ttk.LabelFrame(paned_live, text=" 🔬 Phân tích Chi tiết & Gợi ý Khắc phục Lỗi ", padding=6)
        paned_live.add(bot_live_f, weight=2)

        bot_ctrl = ttk.Frame(bot_live_f)
        bot_ctrl.pack(fill="x", pady=(0, 4))

        self.eve_live_action_map_btn = ttk.Button(
            bot_ctrl, text="🔍 Mở Bản đồ này trong Eve.emg", style="Primary.TButton",
            command=self._eve_live_open_current_map
        )
        self.eve_live_action_map_btn.pack(side="left", padx=(0, 8))

        self.eve_live_action_quest_btn = ttk.Button(
            bot_ctrl, text="⚡ Chuyển sang Tab [7] Sửa Nhiệm Vụ", style="Success.TButton",
            command=self._eve_live_open_quest_editor
        )
        self.eve_live_action_quest_btn.pack(side="left", padx=(0, 8))

        self.eve_live_action_tp_btn = ttk.Button(
            bot_ctrl, text="🚀 Chuyển sang Tab [8] Dịch Chuyển",
            command=self._eve_live_open_tp
        )
        self.eve_live_action_tp_btn.pack(side="left")

        txt_wrap = ttk.Frame(bot_live_f)
        txt_wrap.pack(fill="both", expand=True)

        self.eve_live_detail_txt = tk.Text(
            txt_wrap, height=5, font=self.font_code, wrap="word",
            bg="#1E1E1E", fg="#D4D4D4"
        )
        self.eve_live_detail_txt.pack(side="left", fill="both", expand=True)
        dt_sc = ttk.Scrollbar(txt_wrap, orient="vertical", command=self.eve_live_detail_txt.yview)
        self.eve_live_detail_txt.configure(yscrollcommand=dt_sc.set)
        dt_sc.pack(side="right", fill="y")

        self.after(500, self._eve_live_start_tailer)

    def _inspect_game_event_log(self, timestamp: str, msg: str):
        """Bắt các dòng log từ Game Server (cả tiếng Việt & tiếng Trung) và phân tích sự kiện NPC/Cổng."""
        if not hasattr(self, "live_events_history"):
            self.live_events_history = []
        if not hasattr(self, "live_current_event"):
            self.live_current_event = None

        scenes_map = GameDataCache.get_scenes()
        npc_names = GameDataCache.get_npc_names()

        # Bỏ qua các dòng không liên quan để tối ưu hiệu năng
        keywords = (
            "觸發事件", "Kích hoạt sự kiện", "ClickNpc", "MeetDoor", "ClickDoor", "Door",
            "eveNo=", "無可觸發條件", "không có điều kiện", "không khớp", "不匹配",
            "匹配成功", "khớp thành công", "conditionClass=12", "找不到 Door", "không tìm thấy Door",
            "距離過遠", "khoảng cách quá xa"
        )
        if not any(k in msg for k in keywords):
            return

        # 1. Bắt đầu sự kiện: [Prefix] PlayerName (觸發事件 | Kích hoạt sự kiện): TriggerName (sub=X) triggerID=Y mapId=Z
        m_trig = re.search(r'(?:.*?\s+-\s+)?([^\s]+)\s+(?:觸發事件|Kích hoạt sự kiện)[：:]\s*(\w+)\s+\(sub=(\d+)\)\s+triggerID=(\d+)\s+mapId=(\d+)', msg, re.IGNORECASE)
        if m_trig:
            char_name, trig_name, sub_kind, trig_id, map_id = m_trig.groups()
            map_id_int = int(map_id)
            trig_id_int = int(trig_id)
            sub_kind_int = int(sub_kind)
            map_name = scenes_map.get(map_id_int, f"Map {map_id_int}")

            action_desc = {
                1: "🖱️ Click NPC",
                2: "🚶 Va chạm NPC (Bump)",
                3: "👀 Quét NPC (Scan)",
                4: "🚪 Click Cổng (Door)",
                8: "🚪 Chạm Cổng (MeetDoor)",
            }.get(sub_kind_int, f"{trig_name} (#{sub_kind})")

            target_desc = f"Đối tượng #{trig_id}"
            if sub_kind_int in (4, 8):
                target_desc = f"Cổng Door #{trig_id}"
            else:
                arc = self._eve_get_archive()
                sc = arc.parse_scene(map_id_int)
                if sc:
                    for n in sc.get("npcs", []):
                        if n.get("id") == trig_id_int:
                            real_npcid = n.get("npc_id", 0)
                            n_name = npc_names.get(real_npcid, f"NPC #{real_npcid}")
                            target_desc = f"[{trig_id}] {n_name} (#{real_npcid})"
                            break

            ev_entry = {
                "id": len(self.live_events_history) + 1,
                "time": timestamp,
                "char": char_name,
                "map_id": map_id_int,
                "map_name": map_name,
                "sub_kind": sub_kind_int,
                "action": action_desc,
                "trigger_id": trig_id_int,
                "target": target_desc,
                "status": "⏳ Đang kiểm tra...",
                "tag": "info",
                "summary": "Đang phân tích điều kiện kịch bản...",
                "logs": [f"[{timestamp}] {msg}"],
                "player_state": {},
                "matched_eveno": None,
            }
            self.live_current_event = ev_entry
            self.live_events_history.append(ev_entry)
            self._eve_live_append_tree(ev_entry)
            return

        cur = self.live_current_event

        # 2. Thông tin trạng thái nhân vật: ClickNpc clickId=X events=[...] 玩家狀態 / Người chơi: level=...
        m_state = re.search(r'ClickNpc\s+clickId=(\d+)\s+events=(\[[^\]]*\])\s+(?:玩家狀態|Người chơi|Trạng thái)[：:]\s*level=(\d+)\s+reborn=(\d+)\s+missions=\{?(.*?)\}?\s+flags=\{?(.*?)\}?$', msg, re.IGNORECASE)
        if m_state and cur:
            cid, evs, lvl, reb, m_str, f_str = m_state.groups()
            cur["logs"].append(f"[{timestamp}] {msg}")
            cur["player_state"] = {
                "level": lvl, "reborn": reb, "missions": m_str, "flags": f_str, "events": evs
            }
            cur["summary"] = f"NPC Events {evs}. Lv={lvl}, Nhiệm vụ: {{{m_str}}}"
            self._eve_live_update_tree(cur)
            return

        # 3. Khớp điều kiện thành công: eveNo=X (匹配成功 | khớp thành công) condNo=Y stepScore=Z → N (個結果 | kết quả)
        m_succ = re.search(r'eveNo=(\d+)\s+(?:匹配成功|khớp thành công)\s+condNo=(\d+).*?→\s*(\d+)\s*(?:個結果|kết quả)', msg, re.IGNORECASE)
        if m_succ and cur:
            eno, cond_no, res_cnt = m_succ.groups()
            cur["logs"].append(f"[{timestamp}] {msg}")
            cur["matched_eveno"] = int(eno)
            cur["status"] = f"✅ Khớp EveNo {eno}"
            cur["tag"] = "success"
            cur["summary"] = f"Thỏa điều kiện condNo={cond_no}! Kích hoạt {res_cnt} hành động (Hội thoại/Trận đánh/Nhiệm vụ)"
            self._eve_live_update_tree(cur)
            return

        # 4. Cảnh báo conditionClass=12 bị kẹt tại Server:
        m_c12 = re.search(r'conditionClass=12\s+có param chưa xác minh\s+param=(\d+).*?ops=(\d+)\s+val=(\d+)', msg, re.IGNORECASE)
        if m_c12 and cur:
            p, ops, val = m_c12.groups()
            cur["logs"].append(f"[{timestamp}] {msg}")
            cur["status"] = "❌ Kẹt điều kiện Class 12"
            cur["tag"] = "error"
            cur["summary"] = f"Điều kiện Class=12 (param={p}, ops={ops}, val={val}) bị server từ chối do chưa hoàn thành sự kiện số {p}!"
            self._eve_live_update_tree(cur)
            return

        # 5. Điều kiện không khớp: eveNo=X (不匹配 | không khớp)（Y 條件 | điều kiện）
        m_fail = re.search(r'(?:Door\s+)?eveNo=(\d+)\s+(?:不匹配|không khớp)\s*[（\(]\s*(\d+)\s*(?:條件|điều kiện)\s*[）\)]', msg, re.IGNORECASE)
        if m_fail and cur:
            eno, cond_cnt = m_fail.groups()
            cur["logs"].append(f"[{timestamp}] {msg}")
            if cur["tag"] != "success":
                cur["status"] = "❌ Không thỏa điều kiện"
                cur["tag"] = "error"
                cur["summary"] = f"EveNo {eno} ({cond_cnt} điều kiện) bị từ chối! Nhân vật chưa đáp ứng điều kiện hoặc cổng bị khóa."
            self._eve_live_update_tree(cur)
            return

        # 6. Cổng Teleport kẹt: 場景 / Cảnh X doorId=Y Door (無可觸發條件 | không có điều kiện nào kích hoạt được) events=[...]
        m_door_fail = re.search(r'(?:場景|Cảnh)\s+(\d+)\s+doorId=(\d+)\s+Door\s+(?:無可觸發條件|không có điều kiện nào kích hoạt được).*?events=(\[[^\]]*\])', msg, re.IGNORECASE)
        if m_door_fail:
            mid, did, evs = m_door_fail.groups()
            map_name = scenes_map.get(int(mid), f"Map {mid}")
            if cur and cur.get("sub_kind") in (4, 8) and cur.get("trigger_id") == int(did):
                cur["status"] = "❌ Cổng kẹt / Điều kiện chặn"
                cur["tag"] = "error"
                cur["summary"] = f"Cổng Door #{did} (Events {evs}) bị kẹt điều kiện! Bấm nút [Mở khóa Cổng này] trong Eve.emg để đi tự do."
                cur["logs"].append(f"[{timestamp}] {msg}")
                self._eve_live_update_tree(cur)
            else:
                ev_entry = {
                    "id": len(self.live_events_history) + 1,
                    "time": timestamp,
                    "char": cur["char"] if cur else "Người chơi",
                    "map_id": int(mid),
                    "map_name": map_name,
                    "sub_kind": 8,
                    "action": "🚪 Chạm Cổng",
                    "trigger_id": int(did),
                    "target": f"Cổng Door #{did}",
                    "status": "❌ Cổng kẹt / Điều kiện chặn",
                    "tag": "error",
                    "summary": f"Cổng Door #{did} (Events {evs}) bị kẹt điều kiện! Bấm nút [Mở khóa Cổng này] trong Eve.emg để đi tự do.",
                    "logs": [f"[{timestamp}] {msg}"],
                    "player_state": {}
                }
                self.live_events_history.append(ev_entry)
                self._eve_live_append_tree(ev_entry)
            return

        # 7. Cooldown cổng: MeetDoor (冷卻中 | làm nguội | hồi chiêu)
        if "MeetDoor" in msg and any(k in msg for k in ("冷卻中", "làm nguội", "hồi chiêu", "Cooldown")):
            if cur:
                cur["logs"].append(f"[{timestamp}] {msg}")
                cur["status"] = "⏳ Đang hồi chiêu (<2s)"
                cur["tag"] = "warn"
                cur["summary"] = "Cổng vừa bước qua cách đây < 2s, tạm chặn để chống lặp dịch chuyển vô tận."
                self._eve_live_update_tree(cur)
            return

        # 8. Quá xa: NPC (距離過遠 | khoảng cách quá xa)
        m_far = re.search(r'NPC\s+(?:距離過遠|khoảng cách quá xa)[：:]\s*clickId=(\d+)\s+(?:player|Người chơi)=\((\d+),(\d+)\)\s+npc=\((\d+),(\d+)\)', msg, re.IGNORECASE)
        if m_far and cur:
            cid, px, py, nx, ny = m_far.groups()
            cur["logs"].append(f"[{timestamp}] {msg}")
            cur["status"] = "⚠️ Quá xa NPC"
            cur["tag"] = "warn"
            cur["summary"] = f"Nhân vật tại ({px}, {py}) cách xa NPC ({nx}, {ny}) quá tầm với tương tác."
            self._eve_live_update_tree(cur)
            return

        # 9. Door không tồn tại
        m_nod = re.search(r'(?:場景|Cảnh)\s+(\d+)\s+doorId=(\d+)\s+(?:找不到 Door|không tìm thấy Door)', msg, re.IGNORECASE)
        if m_nod:
            mid, did = m_nod.groups()
            if cur:
                cur["logs"].append(f"[{timestamp}] {msg}")
                cur["status"] = "❌ Cổng không tồn tại"
                cur["tag"] = "error"
                cur["summary"] = f"Map {mid} không có dữ liệu Door #{did} trong Eve.emg!"
                self._eve_live_update_tree(cur)
            return

    def _eve_live_reload_recent_logs(self):
        """Đọc nhanh các dòng nhật ký gần nhất từ file log để hiển thị ngay trên bảng."""
        self._eve_live_clear()
        collected = []

        # 1. Thử đọc từ remote SSH
        try:
            import paramiko
            ssh = paramiko.SSHClient()
            ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
            ssh.connect("192.168.1.69", username="Administrator", password="Jzuqmy8u@123", timeout=2)
            sftp = ssh.open_sftp()
            stat = sftp.stat(r"C:\Server-Game\tsm_dodo\logs\tsm_dodo.log")
            f = sftp.open(r"C:\Server-Game\tsm_dodo\logs\tsm_dodo.log", "r")
            f.seek(max(0, stat.st_size - 60000))
            lines = f.readlines()
            f.close()
            sftp.close()
            ssh.close()
            for l in lines:
                collected.append(l.strip())
            if hasattr(self, "eve_live_status_lbl"):
                self.eve_live_status_lbl.config(
                    text="🟢 Đang kết nối máy chủ 192.168.1.69 (Real-time)", fg="#107C41"
                )
        except Exception:
            pass

        # 2. Nếu không có remote hoặc remote trống, đọc từ local
        if not collected:
            local_log = ROOT_DIR / "logs" / "tsm_dodo.log"
            if local_log.is_file():
                try:
                    with open(local_log, "r", encoding="utf-8", errors="ignore") as lf:
                        lf.seek(max(0, local_log.stat().st_size - 60000))
                        for l in lf:
                            collected.append(l.strip())
                    if hasattr(self, "eve_live_status_lbl"):
                        self.eve_live_status_lbl.config(
                            text="🟢 Đang đọc nhật ký máy cục bộ (Local)", fg="#107C41"
                        )
                except Exception:
                    pass

        # Phân tích các dòng log
        count = 0
        for raw_line in collected:
            clean_msg = self.ANSI_ESCAPE.sub('', raw_line).rstrip('\r\n')
            m_time = re.match(r'^(\d{4}-\d{2}-\d{2}\s+)?(\d{2}:\d{2}:\d{2})', clean_msg)
            ts = m_time.group(2) if m_time else time.strftime("%H:%M:%S")
            before_cnt = len(self.live_events_history)
            self._inspect_game_event_log(ts, clean_msg)
            if len(self.live_events_history) > before_cnt:
                count += 1

        if count > 0:
            messagebox.showinfo("Thành công", f"Đã nạp thành công {count} sự kiện gần nhất từ nhật ký Server!")
        else:
            messagebox.showinfo("Thông báo", "Chưa có sự kiện NPC/Cổng nào trong các bản ghi nhật ký gần nhất.")

    def _eve_live_start_tailer(self):
        """Khởi động luồng ngầm đọc file log tsm_dodo.log."""
        if getattr(self, "_tailer_started", False):
            return
        self._tailer_started = True
        t = threading.Thread(target=self._eve_live_tail_worker, daemon=True)
        t.start()

    def _eve_live_tail_worker(self):
        """Luồng ngầm liên tục theo dõi log Game Server từ file local hoặc SSH remote."""
        local_log = ROOT_DIR / "logs" / "tsm_dodo.log"
        last_local_pos = 0
        last_remote_pos = 0
        ssh_client = None

        while True:
            time.sleep(1.0)
            try:
                # 1. Thử kết nối SSH tới 192.168.1.69
                got_remote = False
                try:
                    import paramiko
                    if ssh_client is None:
                        s = paramiko.SSHClient()
                        s.set_missing_host_key_policy(paramiko.AutoAddPolicy())
                        s.connect("192.168.1.69", username="Administrator", password="Jzuqmy8u@123", timeout=2)
                        ssh_client = s

                    sftp = ssh_client.open_sftp()
                    stat = sftp.stat(r"C:\Server-Game\tsm_dodo\logs\tsm_dodo.log")
                    if last_remote_pos == 0:
                        last_remote_pos = max(0, stat.st_size - 10000)
                    if stat.st_size > last_remote_pos:
                        f = sftp.open(r"C:\Server-Game\tsm_dodo\logs\tsm_dodo.log", "r")
                        f.seek(last_remote_pos)
                        new_lines = f.readlines()
                        last_remote_pos = stat.st_size
                        f.close()
                        for l in new_lines:
                            self.log_queue.put(("Game Server (Remote)", l.strip()))
                    sftp.close()
                    got_remote = True
                except Exception:
                    ssh_client = None

                # 2. Nếu không có remote hoặc remote lỗi, đọc local file
                if not got_remote and local_log.is_file():
                    sz = local_log.stat().st_size
                    if last_local_pos == 0:
                        last_local_pos = max(0, sz - 10000)
                    if sz > last_local_pos:
                        with open(local_log, "r", encoding="utf-8", errors="ignore") as lf:
                            lf.seek(last_local_pos)
                            for l in lf:
                                self.log_queue.put(("Game Server (Local)", l.strip()))
                            last_local_pos = lf.tell()
            except Exception:
                pass

    def _eve_live_append_tree(self, ev_entry: dict):
        if not hasattr(self, "eve_live_tree"): return
        flt = self.eve_live_filter_var.get() if hasattr(self, "eve_live_filter_var") else "Tất cả sự kiện"
        if not self._eve_live_match_filter(ev_entry, flt): return

        iid = str(ev_entry["id"])
        tag = ev_entry.get("tag", "info")
        vals = (
            ev_entry["time"],
            ev_entry["char"],
            f"[{ev_entry['map_id']}] {ev_entry['map_name']}",
            ev_entry["action"],
            ev_entry["target"],
            ev_entry["status"],
            ev_entry["summary"]
        )
        self.eve_live_tree.insert("", "end", iid=iid, values=vals, tags=(tag,))
        if hasattr(self, "eve_live_autoscroll_var") and self.eve_live_autoscroll_var.get():
            self.eve_live_tree.see(iid)

    def _eve_live_update_tree(self, ev_entry: dict):
        if not hasattr(self, "eve_live_tree"): return
        iid = str(ev_entry["id"])
        if not self.eve_live_tree.exists(iid):
            self._eve_live_append_tree(ev_entry)
            return

        flt = self.eve_live_filter_var.get() if hasattr(self, "eve_live_filter_var") else "Tất cả sự kiện"
        if not self._eve_live_match_filter(ev_entry, flt):
            self.eve_live_tree.delete(iid)
            return

        tag = ev_entry.get("tag", "info")
        vals = (
            ev_entry["time"],
            ev_entry["char"],
            f"[{ev_entry['map_id']}] {ev_entry['map_name']}",
            ev_entry["action"],
            ev_entry["target"],
            ev_entry["status"],
            ev_entry["summary"]
        )
        self.eve_live_tree.item(iid, values=vals, tags=(tag,))
        if hasattr(self, "eve_live_autoscroll_var") and self.eve_live_autoscroll_var.get():
            self.eve_live_tree.see(iid)

        sel = self.eve_live_tree.selection()
        if sel and sel[0] == iid:
            self._eve_live_show_details(ev_entry)

    def _eve_live_match_filter(self, ev_entry: dict, flt: str) -> bool:
        if flt == "Tất cả sự kiện": return True
        elif "Lỗi" in flt or "Kẹt" in flt:
            return ev_entry.get("tag") in ("error", "warn")
        elif "Cổng" in flt:
            return ev_entry.get("sub_kind") in (4, 8) or "Cổng" in ev_entry.get("target", "")
        elif "NPC" in flt:
            return ev_entry.get("sub_kind") not in (4, 8)
        return True

    def _eve_live_show_details(self, ev_entry: dict):
        if not hasattr(self, "eve_live_detail_txt"): return
        self.eve_live_detail_txt.delete("1.0", "end")
        pstate = ev_entry.get("player_state", {})
        lvl = pstate.get("level", "Chưa rõ")
        reborn = pstate.get("reborn", "0")
        missions = pstate.get("missions", "Không có")
        flags = pstate.get("flags", "Không có")
        evs = pstate.get("events", "Không có")

        logs_str = "\n".join(ev_entry.get("logs", []))

        tag = ev_entry.get("tag", "info")
        if tag == "error":
            if "Cổng" in ev_entry.get("target", ""):
                recom = (
                    "⚠️ NGUYÊN NHÂN LỖI CỔNG TELEPORT:\n"
                    f"- Cổng này trên Map {ev_entry['map_id']} kích hoạt sự kiện nhưng bị chặn bởi điều kiện nhiệm vụ hoặc không tìm thấy mốc dịch chuyển hợp lệ.\n"
                    "- CÁCH SỬA DỨT ĐIỂM (3 LỰA CHỌN):\n"
                    "  1. [Mở khóa tự do 1-Click]: Nhấn nút [🔍 Mở Bản đồ này trong Eve.emg] bên trên -> Vào Subtab [🚪 Cổng Teleport] -> "
                    "Bấm nút [🔓 Mở Khóa Cổng Này (Bỏ điều kiện chặn)] để cổng luôn mở tự do cho mọi người chơi.\n"
                    "  2. [Sửa Đích Đến]: Vào Subtab [🚪 Cổng Teleport] -> Nhập lại Map Đích, X, Y -> Bấm [💾 Lưu vào Eve.emg].\n"
                    "  3. [Dịch Chuyển Tức Thì]: Nhấn nút [🚀 Chuyển sang Tab [8] Dịch Chuyển] bên trên để bay thẳng vào map đích."
                )
            else:
                recom = (
                    "⚠️ NGUYÊN NHÂN LỖI / KẸT NHIỆM VỤ:\n"
                    "- Điều kiện kiểm tra của NPC bị từ chối do trạng thái nhân vật chưa đáp ứng (Level, bước nhiệm vụ hoặc cờ Flag).\n"
                    f"- Trạng thái nhân vật hiện tại: Level {lvl}, Nhiệm vụ: {missions}, Cờ: {flags}.\n"
                    "- CÁCH SỬA DỨT ĐIỂM:\n"
                    "  1. Nhấn nút [⚡ Chuyển sang Tab [7] Sửa Nhiệm Vụ] bên trên để chỉnh bước nhiệm vụ tương ứng cho nhân vật.\n"
                    "  2. Hoặc mở Subtab [🧙 NPC & Sự kiện] để kiểm tra chính xác điều kiện nào đang chặn."
                )
        elif tag == "warn":
            recom = (
                "ℹ️ CẢNH BÁO TẠM THỜI:\n"
                "- Thao tác bị hoãn do cooldown hồi chiêu cổng (< 2s) hoặc khoảng cách nhân vật đứng xa NPC."
            )
        else:
            recom = "✅ Tương tác bình thường: Kịch bản được kích hoạt thành công trên Game Server."

        report = (
            f"================================================================================\n"
            f"🔬 CHUẨN ĐOÁN SỰ KIỆN THỜI GIAN THỰC (LIVE EVENT INSPECTOR)\n"
            f"================================================================================\n"
            f"• Thời gian:   {ev_entry['time']}\n"
            f"• Nhân vật:    {ev_entry['char']}\n"
            f"• Bản đồ:      [{ev_entry['map_id']}] {ev_entry['map_name']}\n"
            f"• Hành động:   {ev_entry['action']}\n"
            f"• Đối tượng:   {ev_entry['target']}\n"
            f"• Trạng thái:  {ev_entry['status']}\n"
            f"--------------------------------------------------------------------------------\n"
            f"📊 TRẠNG THÁI NHÂN VẬT LÚC TƯƠNG TÁC:\n"
            f"- Cấp độ (Level): {lvl} | Tái sinh: {reborn}\n"
            f"- Danh sách Sự kiện NPC: {evs}\n"
            f"- Bước Nhiệm vụ hiện tại: {missions}\n"
            f"- Cờ Flag đánh dấu:       {flags}\n"
            f"--------------------------------------------------------------------------------\n"
            f"💡 HƯỚNG DẪN KHẮC PHỤC LỖI:\n"
            f"{recom}\n"
            f"--------------------------------------------------------------------------------\n"
            f"📝 NHẬT KÝ RAW TỪ GAME SERVER:\n"
            f"{logs_str}\n"
        )
        self.eve_live_detail_txt.insert("1.0", report)

    def _eve_live_open_current_map(self):
        sel = self.eve_live_tree.selection()
        if not sel:
            messagebox.showinfo("Thông báo", "Vui lòng chọn một dòng sự kiện trong bảng trên.")
            return
        iid = int(sel[0])
        ev = next((x for x in self.live_events_history if x["id"] == iid), None)
        if not ev: return
        self.eve_scene_id_var.set(str(ev["map_id"]))
        self._eve_load_scene()
        if ev.get("sub_kind") in (4, 8):
            self.eve_sub_nb.select(0)
        else:
            self.eve_sub_nb.select(1)

    def _eve_live_open_quest_editor(self):
        sel = self.eve_live_tree.selection()
        if not sel:
            messagebox.showinfo("Thông báo", "Vui lòng chọn một dòng sự kiện trong bảng trên.")
            return
        iid = int(sel[0])
        ev = next((x for x in self.live_events_history if x["id"] == iid), None)
        if not ev: return
        self.notebook.select(self.tab_quest)
        cname = ev.get("char", "").strip()
        if cname and hasattr(self, "quest_char_cb") and hasattr(self, "quest_chars_map"):
            for full_str, cdata in self.quest_chars_map.items():
                if cname.lower() in full_str.lower():
                    self.quest_char_var.set(full_str)
                    self._quest_on_select_char()
                    break

    def _eve_live_open_tp(self):
        sel = self.eve_live_tree.selection()
        if not sel:
            messagebox.showinfo("Thông báo", "Vui lòng chọn một dòng sự kiện trong bảng trên.")
            return
        iid = int(sel[0])
        ev = next((x for x in self.live_events_history if x["id"] == iid), None)
        if not ev: return
        self.notebook.select(self.tab_teleport)
        cname = ev.get("char", "").strip()
        if cname and hasattr(self, "tp_char_cb") and hasattr(self, "tp_chars_map"):
            for full_str, cdata in self.tp_chars_map.items():
                if cname.lower() in full_str.lower():
                    self.tp_char_var.set(full_str)
                    self._tp_on_select_char()
                    break

    def _eve_live_clear(self):
        self.live_events_history = []
        self.live_current_event = None
        for i in self.eve_live_tree.get_children():
            self.eve_live_tree.delete(i)
        self.eve_live_detail_txt.delete("1.0", "end")

    def _eve_live_on_select(self, event=None):
        sel = self.eve_live_tree.selection()
        if not sel: return
        iid = int(sel[0])
        ev = next((x for x in self.live_events_history if x["id"] == iid), None)
        if ev:
            self._eve_live_show_details(ev)

    def _eve_live_apply_filter(self, event=None):
        flt = self.eve_live_filter_var.get()
        for i in self.eve_live_tree.get_children():
            self.eve_live_tree.delete(i)
        for ev in self.live_events_history:
            if self._eve_live_match_filter(ev, flt):
                iid = str(ev["id"])
                tag = ev.get("tag", "info")
                vals = (
                    ev["time"],
                    ev["char"],
                    f"[{ev['map_id']}] {ev['map_name']}",
                    ev["action"],
                    ev["target"],
                    ev["status"],
                    ev["summary"]
                )
                self.eve_live_tree.insert("", "end", iid=iid, values=vals, tags=(tag,))

    def _eve_get_archive(self) -> EveArchive:
        if self.eve_archive is None:
            self.eve_archive = EveArchive()
        return self.eve_archive

    def _eve_load_scene(self):
        """Nạp dữ liệu bản đồ từ Eve.emg vào giao diện."""
        raw_val = self.eve_scene_id_var.get().strip()
        if not raw_val.isdigit():
            messagebox.showwarning("Thông báo", "Vui lòng nhập Map ID là số.")
            return

        sid = int(raw_val)
        arc = self._eve_get_archive()
        if sid not in arc.scenes:
            messagebox.showerror("Không tìm thấy", f"Map ID {sid} không có trong file Eve.emg!")
            return

        scenes_map = GameDataCache.get_scenes()
        npc_names = GameDataCache.get_npc_names()
        sname = scenes_map.get(sid, f"Bản đồ {sid}")
        self.eve_scene_name_lbl.config(text=f"[{sid}] {sname}")

        for i in self.eve_door_tree.get_children(): self.eve_door_tree.delete(i)
        for i in self.eve_info_tree.get_children(): self.eve_info_tree.delete(i)
        for i in self.eve_npc_tree.get_children(): self.eve_npc_tree.delete(i)
        for i in self.eve_event_tree.get_children(): self.eve_event_tree.delete(i)

        sc = arc.parse_scene(sid)
        if not sc:
            return
        self.eve_current_scene = sc

        # 1. Cổng Teleport (DoorData) — Tự động phân giải đích đến và loại cửa
        for d in sc["doors"]:
            did = d["id"]
            coords_str = f"({d['x']}, {d['y']})"
            evs = d["events"]
            target_info = None
            used_eno = ""
            for eno in evs:
                if eno in sc["scene_infos"]:
                    target_info = sc["scene_infos"][eno]
                    used_eno = str(eno)
                    break
            if not target_info:
                for eno in evs:
                    ev = sc["events"].get(eno)
                    if ev:
                        for c in ev.get("conditions", []):
                            for r in c.get("results", []):
                                if r.get("type") == 2 and r.get("param") in sc["scene_infos"]:
                                    target_info = sc["scene_infos"][r["param"]]
                                    used_eno = str(r["param"])
                                    break
                            if target_info: break
                    if target_info: break

            if target_info:
                ts_id = target_info["target_scene"]
                ts_name = scenes_map.get(ts_id, f"Map {ts_id}")
                t_coords = f"({target_info['target_x']}, {target_info['target_y']})"
                c_type = self._classify_scene_type(ts_id, ts_name)
                t_desc = f"[{ts_id}] {ts_name}"
            else:
                t_desc = "Chưa gắn đích"
                t_coords = "-"
                c_type = "❓ Chưa xác định"
                used_eno = ",".join(map(str, evs)) if evs else "-"

            self.eve_door_tree.insert("", "end", values=(
                did, coords_str, t_desc, t_coords, c_type, used_eno
            ))

        # 2. SceneInfos (Đích đến Teleport nạp sẵn)
        for k, inf in sc["scene_infos"].items():
            ts_id = inf["target_scene"]
            ts_name = scenes_map.get(ts_id, f"Map {ts_id}")
            c_type = self._classify_scene_type(ts_id, ts_name)
            self.eve_info_tree.insert("", "end", values=(
                inf["eve_no"], f"[{ts_id}] {ts_name} ({c_type})", f"({inf['target_x']}, {inf['target_y']})", inf["name"]
            ))

        # 3. NPCs — Kèm tên đầy đủ từ Npc_C.dat
        for n in sc["npcs"]:
            npcid = n["npc_id"]
            nname = npc_names.get(npcid, f"NPC #{npcid}")
            self.eve_npc_tree.insert("", "end", values=(
                n["id"], npcid, nname, f"({n['x']}, {n['y']})", ",".join(map(str, n["events"]))
            ))

        # 4. Events — Mặc định hiển thị toàn bộ sự kiện của map
        self._eve_render_events()

    def _classify_scene_type(self, scene_id: int, scene_name: str) -> str:
        nl = scene_name.lower()
        if any(k in nl for k in ["trọ", "quán trà", "khách sạn", "khách điếm"]):
            return "🏨 Quán trọ / Điếm"
        elif any(k in nl for k in ["thuốc", "y quán", "tiệm thuốc", "dược"]):
            return "💊 Tiệm thuốc"
        elif any(k in nl for k in ["rèn", "tiệm rèn", "vũ khí", "trang bị"]):
            return "⚒️ Tiệm rèn"
        elif any(k in nl for k in ["tiệm", "tạp hóa", "thương nhân", "buôn"]):
            return "🛒 Tiệm buôn"
        elif any(k in nl for k in ["nhà", "phú hộ", "bần hộ", "dân gia", "phủ", "dinh", "phòng"]):
            return "🏠 Vào nhà NPC"
        elif any(k in nl for k in ["lôi đài", "tỉ võ", "chiến trường"]):
            return "⚔️ Lôi đài"
        elif any(k in nl for k in ["động", "hang", "sơn động", "địa đạo"]):
            return "⛰️ Hang động"
        elif any(k in nl for k in ["thành môn", "cổng thành", "cửa thành"]):
            return "🏰 Cổng thành"
        elif any(k in nl for k in ["rừng", "hẻm", "sơn đạo", "bến đò", "hải cảng", "thôn", "làng"]):
            return "🌲 Dã ngoại / Rừng"
        else:
            return "🚪 Chuyển Bản đồ"

    def _summarize_event_action(self, ev: dict, scenes_map: dict) -> tuple[str, str]:
        actions = []
        details = []
        for c in ev.get("conditions", []):
            if c.get("param", 0) > 0 and c.get("class") == 2:
                details.append(f"Kiểm tra Q#{c['param']} op={c.get('ops',0)} val={c.get('val',0)}")
            for r in c.get("results", []):
                rtype = r.get("type", 0)
                rclass = r.get("class", 0)
                rparam = r.get("param", 0)
                rval = r.get("val", 0)
                rmean = r.get("mean_no", 0)
                if rtype == 2:
                    ts_id = rparam
                    ts_name = scenes_map.get(ts_id, f"Map {ts_id}")
                    actions.append(f"🚪 Chuyển sang [{ts_id}] {ts_name}")
                    details.append(f"Dịch chuyển Map {ts_id}")
                elif rtype == 1 or rclass == 2:
                    actions.append(f"⚔️ Trận chiến #{rparam}")
                    details.append(f"Đánh quái #{rparam}")
                elif rclass == 1:
                    actions.append(f"🎁 Nhận item #{rparam}")
                    details.append(f"Item #{rparam} x{rval}")
                elif rclass == 2 or (rtype == 0 and rclass == 2):
                    actions.append(f"📜 Nhiệm vụ #{rparam}")
                    details.append(f"Đổi bước Q#{rparam} = {rval}")
                elif rtype == 3 or rmean > 0:
                    actions.append("💬 Đối thoại")
                    details.append(f"Talk #{rmean or rparam}")
                elif rclass == 5:
                    actions.append("💰 Tiền vàng")
                    details.append(f"Vàng {rval}")
                elif rclass == 8:
                    actions.append(f"🧙 Thưởng tướng #{rparam}")
                    details.append(f"Tướng #{rparam}")
                elif rtype == 6:
                    actions.append("🏨 Quán trọ")
                    details.append("Hồi máu/SP")
                elif rtype == 5:
                    actions.append("🛒 Tiệm buôn")
                    details.append("Giao dịch mua bán")

        main_act = " | ".join(list(dict.fromkeys(actions))) if actions else "Kịch bản tự do"
        det_str = "; ".join(list(dict.fromkeys(details))) if details else "Không có điều kiện đặc biệt"
        return main_act, det_str

    def _eve_on_select_door(self, event=None):
        sel = self.eve_door_tree.selection()
        if not sel: return
        vals = self.eve_door_tree.item(sel[0], "values")
        if not vals: return
        # vals: (id, coords, target, target_coords, type, events)
        target_str = str(vals[2]).strip()
        tcoords_str = str(vals[3]).strip()
        eveno_str = str(vals[5]).strip()

        if eveno_str and eveno_str.isdigit():
            self.eve_edit_eveno_var.set(eveno_str)
            for item in self.eve_info_tree.get_children():
                iv = self.eve_info_tree.item(item, "values")
                if iv and str(iv[0]) == eveno_str:
                    self.eve_info_tree.selection_set(item)
                    self.eve_info_tree.see(item)
                    break

        if "[" in target_str and "]" in target_str:
            sid = target_str.split("]")[0].replace("[", "").strip()
            self.eve_edit_scene_var.set(sid)

        if "," in tcoords_str:
            coords = tcoords_str.replace("(", "").replace(")", "").split(",")
            if len(coords) == 2:
                self.eve_edit_x_var.set(coords[0].strip())
                self.eve_edit_y_var.set(coords[1].strip())

    def _eve_on_select_info(self, event=None):
        sel = self.eve_info_tree.selection()
        if not sel: return
        vals = self.eve_info_tree.item(sel[0], "values")
        if vals:
            self.eve_edit_eveno_var.set(str(vals[0]))
            ts_str = str(vals[1]).split("]")[0].replace("[", "") if "[" in str(vals[1]) else str(vals[1])
            self.eve_edit_scene_var.set(ts_str)
            coords = str(vals[2]).replace("(", "").replace(")", "").split(",")
            if len(coords) == 2:
                self.eve_edit_x_var.set(coords[0].strip())
                self.eve_edit_y_var.set(coords[1].strip())

    def _eve_on_select_npc(self, event=None):
        sel = self.eve_npc_tree.selection()
        if not sel: return
        vals = self.eve_npc_tree.item(sel[0], "values")
        if not vals: return
        # vals: (id, npc_id, name, coords, events)
        npcid = vals[1]
        nname = vals[2]
        ev_str = str(vals[4])
        ev_list = [int(x.strip()) for x in ev_str.split(",") if x.strip().isdigit()]
        self._eve_render_events(filter_events=ev_list, npc_desc=f"[{npcid}] {nname}")

    def _eve_show_all_events(self):
        self._eve_render_events()

    def _eve_render_events(self, filter_events=None, npc_desc=None):
        for i in self.eve_event_tree.get_children(): self.eve_event_tree.delete(i)
        if not hasattr(self, "eve_current_scene") or not self.eve_current_scene:
            return

        scenes_map = GameDataCache.get_scenes()
        events = self.eve_current_scene.get("events", {})

        if filter_events is not None:
            self.eve_ev_filter_lbl.config(
                text=f"Đang lọc sự kiện cho: {npc_desc} ({len(filter_events)} sự kiện)",
                fg="#005A9E"
            )
        else:
            self.eve_ev_filter_lbl.config(
                text=f"Tất cả sự kiện của Bản đồ ({len(events)} sự kiện) — Click vào NPC bên trái để lọc",
                fg="#333333"
            )

        for eno, ev in events.items():
            if filter_events is not None and eno not in filter_events:
                continue

            when_str = "Click NPC" if ev["when_happen"][0] else ("Chạm vào (Bump)" if ev["when_happen"][1] else "Tự động / Khác")
            main_act, det_str = self._summarize_event_action(ev, scenes_map)
            self.eve_event_tree.insert("", "end", values=(
                eno, main_act, when_str, f"{len(ev['conditions'])} Đ/K", det_str
            ))

    def _eve_save_door(self):
        """Lưu tọa độ đích đến mới của Cổng dịch chuyển vào file Eve.emg."""
        sid_raw = self.eve_scene_id_var.get().strip()
        eve_raw = self.eve_edit_eveno_var.get().strip()
        ts_raw = self.eve_edit_scene_var.get().strip()
        tx_raw = self.eve_edit_x_var.get().strip()
        ty_raw = self.eve_edit_y_var.get().strip()

        if not (sid_raw.isdigit() and eve_raw.isdigit() and ts_raw.isdigit() and tx_raw.isdigit() and ty_raw.isdigit()):
            messagebox.showwarning("Thông báo", "Vui lòng chọn 1 dòng cổng và nhập đầy đủ số.")
            return

        sid = int(sid_raw)
        eve_no = int(eve_raw)
        ts = int(ts_raw)
        tx = int(tx_raw)
        ty = int(ty_raw)

        if not messagebox.askyesno(
            "Xác nhận ghi",
            f"Cập nhật đích đến của EveNo={eve_no} trong Map {sid} thành:\n"
            f"- Map Đích: {ts}\n"
            f"- Tọa độ: X={tx}, Y={ty}\n\n"
            f"(Hệ thống sẽ tự động tạo file dự phòng .bak trước khi ghi đè)"
        ):
            return

        arc = self._eve_get_archive()
        ok = arc.update_door_scene_info(sid, eve_no, ts, tx, ty)
        if ok:
            messagebox.showinfo("Thành công", "Đã cập nhật file Eve.emg thành công!")
            self._eve_load_scene()
        else:
            messagebox.showerror("Lỗi", "Không thể cập nhật SceneInfo.")

    def _eve_unlock_door(self):
        """Mở khóa Cổng được chọn (gỡ bỏ điều kiện chặn, chuyển thành class: 0 tự do đi lại)."""
        sid_raw = self.eve_scene_id_var.get().strip()
        eve_raw = self.eve_edit_eveno_var.get().strip()
        if not (sid_raw.isdigit() and eve_raw.isdigit()):
            messagebox.showwarning("Thông báo", "Vui lòng chọn một Cổng trong bảng Danh sách Cổng trước.")
            return

        sid = int(sid_raw)
        eve_no = int(eve_raw)

        if not messagebox.askyesno(
            "Xác nhận Mở Khóa Cổng",
            f"Bạn có chắc muốn MỞ KHÓA TỰ DO cho Cổng (EveNo={eve_no}) trên Map {sid} không?\n\n"
            f"- Mọi điều kiện chặn hoặc yêu cầu nhiệm vụ sẽ được gỡ bỏ (chuyển thành Không Điều Kiện class: 0).\n"
            f"- Mọi nhân vật bước vào cổng sẽ được dịch chuyển ngay lập tức đến Map đích.\n"
            f"- Hệ thống sẽ tự động tạo file sao lưu .bak trước khi sửa."
        ):
            return

        arc = self._eve_get_archive()
        ok = arc.unlock_door_event(sid, eve_no)
        if ok:
            sync_msg = ""
            try:
                import paramiko
                ssh = paramiko.SSHClient()
                ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
                ssh.connect("192.168.1.69", username="Administrator", password="Jzuqmy8u@123", timeout=2)
                cmd = f'C:\\Users\\Administrator\\AppData\\Local\\Programs\\Python\\Python314\\python.exe -c "import sys; sys.path.append(r\\\"C:\\Server-Game\\tsm_dodo\\\"); from TSM_Setup import EveArchive; arc = EveArchive(); arc.unlock_door_event({sid}, {eve_no})"'
                ssh.exec_command(cmd)
                ssh.close()
                sync_msg = "\n(Đã tự động đồng bộ sang máy chủ 192.168.1.69!)"
            except Exception:
                pass

            messagebox.showinfo("Thành công", f"Đã mở khóa Cổng (EveNo={eve_no}) thành công!{sync_msg}\nKhởi động lại Game Server để áp dụng.")
            self._eve_load_scene()
        else:
            messagebox.showerror("Lỗi", f"Không thể mở khóa Cổng {eve_no}. Vui lòng kiểm tra lại sự kiện.")

    def _eve_search_mission(self):
        """Tìm kiếm luồng nhiệm vụ qua toàn bộ 3,823 map."""
        raw = self.eve_q_search_var.get().strip()
        if not raw.isdigit():
            messagebox.showwarning("Thông báo", "Vui lòng nhập Mission ID là số.")
            return

        mid = int(raw)
        self.eve_q_res_lbl.config(text="Đang quét 3,823 bản đồ...")
        self.update_idletasks()

        def worker():
            arc = self._eve_get_archive()
            matches = arc.search_mission(mid)
            scenes_map = GameDataCache.get_scenes()

            def update_ui():
                for i in self.eve_q_tree.get_children(): self.eve_q_tree.delete(i)
                for m in matches:
                    sid = m["scene_id"]
                    sname = scenes_map.get(sid, f"Map {sid}")
                    npc_text = ", ".join([f"NPC {n['npc_id']}" for n in m["npcs"]]) if m["npcs"] else "Trigger tự do"
                    for c in m["conditions"]:
                        self.eve_q_tree.insert("", "end", values=(
                            f"[{sid}] {sname}", m["eve_no"], npc_text, "Điều kiện kiểm tra",
                            f"Kiểm tra bước {c['ops']} {c['val']} (pStyle={c['pstyle']})"
                        ))
                    for r in m["results"]:
                        action = "Tăng bước (+)" if r["pstyle"] == 1 else ("Giảm/hoàn thành" if r["pstyle"] == 2 else "Hoàn tất")
                        self.eve_q_tree.insert("", "end", values=(
                            f"[{sid}] {sname}", m["eve_no"], npc_text, f"Kết quả: {action}",
                            f"ResultType={r['type']}, Value={r['val']}, MeanNo={r['mean_no']}"
                        ))
                self.eve_q_res_lbl.config(text=f"Tìm thấy {len(matches)} mắt xích cho nhiệm vụ {mid}.")

            self.after(0, update_ui)

        threading.Thread(target=worker, daemon=True).start()

    # ========================================================================
    # ĐÓNG
    # ========================================================================
    def on_close(self):
        if self.cdn_proc.is_running or self.game_proc.is_running:
            if messagebox.askyesno("Xác nhận", "Server đang chạy. Tắt tất cả trước khi thoát?"):
                self.stop_all_servers()
        self.destroy()


# ============================================================================
# Lớp sửa IP/Port trong APK
# ============================================================================
class ApkIpEditor:
    """Thay đổi IP và port trong file APK."""

    # Các file trong APK có thể chứa IP/port
    APK_TARGET_FILES = [
        "assets/ServerList.dat",
        "assets/bin/Data/ServerList.dat",
        "assets/bin/Data/StreamingAssets/ServerList.dat",
        "assets/bin/Data/Managed/Metadata/global-metadata.dat",
        "assets/bin/Data/data.unity3d",
    ]
    # Các IP đã biết để tìm
    KNOWN_IPS = [b"192.168.1.69", b"192.168.1.19", b"7.7.7.8"]
    # Port game đã biết trong ServerList.dat
    KNOWN_PORT = b"6613"
    # Port CDN đã biết trong global-metadata.dat (có pad: 00443 = port 443)
    KNOWN_CDN_PORT = b"00443"

    def __init__(self, apk_path, new_ip, new_port, new_cdn_ip=None,
                 new_cdn_port=None, keystore=None, key_alias=None,
                 key_pass=None, log_fn=None):
        self.apk_path = Path(apk_path)
        self.new_ip = new_ip
        self.new_port = new_port
        self.new_cdn_ip = new_cdn_ip or new_ip
        self.new_cdn_port = new_cdn_port or 443
        self.keystore = keystore
        self.key_alias = key_alias or "androiddebugkey"
        self.key_pass = key_pass or "android"
        self.log = log_fn or print
        self.temp_dir = None
        self.extracted_dir = None

    def _log(self, msg):
        self.log(msg)

    def extract_apk(self):
        self.temp_dir = Path(tempfile.mkdtemp(prefix="tsm_apk_"))
        self.extracted_dir = self.temp_dir / "extracted"
        self._log(f"[EXTRACT] Giải nén APK: {self.apk_path.name}")
        self._log(f"   -> {self.extracted_dir}")
        with zipfile.ZipFile(self.apk_path, "r") as zf:
            zf.extractall(self.extracted_dir)
        self._log(f"   [OK] Đã giải nén {len(list(self.extracted_dir.rglob('*')))} files")
        return self.extracted_dir

    def replace_serverlist(self):
        self._log("\n[...] Đổi IP + Port trong ServerList.dat...")
        total = 0
        for rel in self.APK_TARGET_FILES:
            if "ServerList" not in rel:
                continue
            fp = self.extracted_dir / rel
            if not fp.is_file():
                continue
            data = fp.read_bytes()
            count_ip = 0
            for old_ip_str in ["192.168.1.69", "192.168.1.19"]:
                old_ip = f'"{old_ip_str}"'.encode()
                new_ip = f'"{self.new_ip}"'.encode()
                c = data.count(old_ip)
                if c > 0:
                    data = data.replace(old_ip, new_ip)
                    count_ip += c
                    self._log(f"   [OK] {rel}: IP {old_ip_str} -> {self.new_ip}: {c} lần")
            old_port = f'"port":{self.KNOWN_PORT.decode()}'.encode()
            new_port = f'"port":{self.new_port}'.encode()
            count_port = data.count(old_port)
            if count_port > 0:
                data = data.replace(old_port, new_port)
                self._log(f"   [OK] {rel}: Port {self.KNOWN_PORT.decode()} -> {self.new_port}: {count_port} lần")
            fp.write_bytes(data)
            total += count_ip + count_port
        if total == 0:
            self._log("   [i] Không tìm thấy ServerList.dat hoặc không có IP/port cần đổi")
        return total

    def replace_global_metadata(self):
        self._log("\n[...] Đổi IP + Port trong global-metadata.dat...")
        rel = "assets/bin/Data/Managed/Metadata/global-metadata.dat"
        fp = self.extracted_dir / rel
        if not fp.is_file():
            self._log(f"   [!] Không tìm thấy: {rel}")
            return 0
        data = fp.read_bytes()
        new_ip = self.new_ip.encode()
        total = 0
        # 1. Thay thế địa chỉ IP
        for old_ip in [b"192.168.1.69", b"192.168.1.19"]:
            count = data.count(old_ip)
            if count == 0:
                continue
            if len(old_ip) == len(new_ip):
                data = data.replace(old_ip, new_ip)
                self._log(f"   [OK] Đổi {old_ip.decode()} -> {new_ip.decode()}: {count} lần (cùng độ dài)")
                total += count
            else:
                pad_len = len(old_ip) - len(new_ip)
                if pad_len >= 0:
                    padded = new_ip + b"\x00" * pad_len
                    data = data.replace(old_ip, padded)
                    self._log(f"   [OK] Đổi {old_ip.decode()} -> {new_ip.decode()} + {pad_len} null: {count} lần")
                    total += count
                else:
                    data = data.replace(old_ip, new_ip)
                    self._log(f"   [OK] Đổi {old_ip.decode()} -> {new_ip.decode()}: {count} lần (dài hơn)")
                    total += count
        # 2. Thay thế port CDN 00443
        new_cdn_port_str = f"{self.new_cdn_port:05d}".encode()
        old_cdn_port = self.KNOWN_CDN_PORT
        count_port = data.count(old_cdn_port)
        if count_port > 0:
            if len(old_cdn_port) == len(new_cdn_port_str):
                data = data.replace(old_cdn_port, new_cdn_port_str)
            else:
                if len(new_cdn_port_str) < len(old_cdn_port):
                    padded = new_cdn_port_str + b"\x00" * (len(old_cdn_port) - len(new_cdn_port_str))
                    data = data.replace(old_cdn_port, padded)
                else:
                    data = data.replace(old_cdn_port, new_cdn_port_str[:len(old_cdn_port)])
            self._log(f"   [OK] Đổi port {old_cdn_port.decode()} -> {self.new_cdn_port}: {count_port} lần")
            total += count_port
        fp.write_bytes(data)
        self._log(f"   [OK] global-metadata.dat: {total} thay đổi")
        return total

    def replace_data_unity3d(self):
        self._log("\n[...] Đổi IP trong data.unity3d...")
        rel = "assets/bin/Data/data.unity3d"
        fp = self.extracted_dir / rel
        if not fp.is_file():
            self._log(f"   [!] Không tìm thấy: {rel}")
            return 0
        data = fp.read_bytes()
        total = 0
        for old_ip in [b"7.7.7.8", b"192.168.1.69", b"192.168.1.19"]:
            count = data.count(old_ip)
            if count == 0:
                continue
            new_ip = self.new_ip.encode()
            if len(old_ip) == len(new_ip):
                data = data.replace(old_ip, new_ip)
                self._log(f"   [OK] Đổi {old_ip.decode()} -> {new_ip.decode()}: {count} lần (cùng độ dài)")
                total += count
            else:
                pad_len = len(old_ip) - len(new_ip)
                if pad_len >= 0:
                    padded_new = new_ip + b"\x00" * pad_len
                    data = data.replace(old_ip, padded_new)
                    self._log(f"   [OK] Đã thấy {count} lần (pad {pad_len} null bytes)")
                    total += count
                else:
                    self._log(f"   [X] {old_ip.decode()} ({len(old_ip)}) -> {new_ip.decode()} ({len(new_ip)}): IP mới dài hơn, bỏ qua")
        if total > 0:
            fp.write_bytes(data)
        else:
            self._log("   [i] Không tìm thấy IP cần đổi trong data.unity3d")
        return total

    def repackage_apk(self, output_path=None):
        if output_path is None:
            stem = self.apk_path.stem
            output_path = self.apk_path.parent / f"{stem}_ip_modified.apk"
        else:
            output_path = Path(output_path)
        self._log(f"\n[ZIP] Đóng gói APK: {output_path.name}")
        if output_path.exists():
            output_path.unlink()
        with zipfile.ZipFile(output_path, "w", zipfile.ZIP_DEFLATED) as zf:
            for root, dirs, files in os.walk(self.extracted_dir):
                for fn in files:
                    fp = Path(root) / fn
                    arcname = fp.relative_to(self.extracted_dir).as_posix()
                    zf.write(fp, arcname)
        self._log(f"   [OK] Đã đóng gói: {output_path}")
        self._log(f"   [i] Kích thước: {output_path.stat().st_size / 1024 / 1024:.1f} MB")
        return output_path

    @staticmethod
    def _generate_debug_keystore(dest_path: Path):
        import datetime
        from cryptography import x509
        from cryptography.x509.oid import NameOID
        from cryptography.hazmat.primitives import hashes, serialization
        from cryptography.hazmat.primitives.asymmetric import rsa
        from cryptography.hazmat.primitives.serialization import pkcs12

        dest_path.parent.mkdir(parents=True, exist_ok=True)
        key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        name = x509.Name([
            x509.NameAttribute(NameOID.COMMON_NAME, 'Android Debug'),
            x509.NameAttribute(NameOID.ORGANIZATION_NAME, 'Android'),
            x509.NameAttribute(NameOID.COUNTRY_NAME, 'US'),
        ])
        now = datetime.datetime.now(datetime.timezone.utc)
        cert = (
            x509.CertificateBuilder()
            .subject_name(name)
            .issuer_name(name)
            .public_key(key.public_key())
            .serial_number(x509.random_serial_number())
            .not_valid_before(now)
            .not_valid_after(now + datetime.timedelta(days=10000))
            .sign(key, hashes.SHA256())
        )
        p12 = pkcs12.serialize_key_and_certificates(
            name=b'androiddebugkey',
            key=key,
            cert=cert,
            cas=None,
            encryption_algorithm=serialization.BestAvailableEncryption(b'android')
        )
        dest_path.write_bytes(p12)

    @staticmethod
    def _sign_pure_python(apk_path, keystore_path, password='android'):
        import base64
        from cryptography.hazmat.primitives import hashes, serialization
        from cryptography.hazmat.primitives.serialization import pkcs12, pkcs7

        p12_data = Path(keystore_path).read_bytes()
        key, cert, cas = pkcs12.load_key_and_certificates(p12_data, password.encode())

        entries = {}
        with zipfile.ZipFile(apk_path, 'r') as zf_in:
            for name in sorted(zf_in.namelist()):
                if not name.startswith('META-INF/'):
                    entries[name] = zf_in.read(name)

        manifest_lines = ['Manifest-Version: 1.0\r\nCreated-By: 1.0 (Android Signer)\r\n\r\n']
        file_digests = {}
        for name, data in entries.items():
            digest = base64.b64encode(hashlib.sha256(data).digest()).decode('ascii')
            file_digests[name] = digest
            manifest_lines.append(f'Name: {name}\r\nSHA-256-Digest: {digest}\r\n\r\n')

        manifest_content = ''.join(manifest_lines).encode('utf-8')
        manifest_digest = base64.b64encode(hashlib.sha256(manifest_content).digest()).decode('ascii')

        sf_lines = [
            'Signature-Version: 1.0\r\n',
            'Created-By: 1.0 (Android Signer)\r\n',
            f'SHA-256-Digest-Manifest: {manifest_digest}\r\n\r\n',
        ]
        for name, digest in file_digests.items():
            entry = f'Name: {name}\r\nSHA-256-Digest: {digest}\r\n\r\n'.encode('utf-8')
            entry_digest = base64.b64encode(hashlib.sha256(entry).digest()).decode('ascii')
            sf_lines.append(f'Name: {name}\r\nSHA-256-Digest: {entry_digest}\r\n\r\n')

        sf_content = ''.join(sf_lines).encode('utf-8')

        options = [pkcs7.PKCS7Options.DetachedSignature, pkcs7.PKCS7Options.NoCapabilities, pkcs7.PKCS7Options.Binary]
        builder = pkcs7.PKCS7SignatureBuilder().set_data(sf_content)
        builder = builder.add_signer(cert, key, hashes.SHA256())
        rsa_content = builder.sign(serialization.Encoding.DER, options)

        tmp_out = str(apk_path) + '.signed.tmp'
        with zipfile.ZipFile(tmp_out, 'w', zipfile.ZIP_DEFLATED) as zf_out:
            zf_out.writestr('META-INF/MANIFEST.MF', manifest_content)
            zf_out.writestr('META-INF/CERT.SF', sf_content)
            zf_out.writestr('META-INF/CERT.RSA', rsa_content)
            for name, data in entries.items():
                zf_out.writestr(name, data)

        os.replace(tmp_out, str(apk_path))
        return True

    def sign_apk(self, apk_path):
        self._log(f"\n[SIGN] Bắt đầu Ký APK...")
        default_ks = ROOT_DIR / "tools" / "debug.keystore"
        ks = self.keystore or (str(default_ks) if default_ks.is_file() else None)
        if not ks or not Path(ks).is_file():
            self._log("   [*] Không có keystore tùy chọn -> Tự tạo Keystore Debug tích hợp...")
            try:
                self._generate_debug_keystore(default_ks)
                ks = str(default_ks)
                self.keystore = ks
                self.key_alias = "androiddebugkey"
                self.key_pass = "android"
                self._log(f"   [OK] Đã tạo Keystore: {default_ks}")
            except Exception as e:
                self._log(f"   [!] Không thể tạo keystore: {e}")
                return False

        # 1. Thử apksigner
        apksigner = shutil.which("apksigner")
        if apksigner:
            cmd = [apksigner, "sign", "--ks", ks, "--ks-key-alias", self.key_alias,
                   "--ks-pass", f"pass:{self.key_pass}", "--key-pass", f"pass:{self.key_pass}", str(apk_path)]
            self._log("   -> Đang ký bằng apksigner...")
            result = subprocess.run(cmd, capture_output=True, text=True)
            if result.returncode == 0:
                self._log("   [OK] apksigner thành công!")
                return True
            else:
                self._log(f"   [!] apksigner: {result.stderr[:200]}")

        # 2. Thử jarsigner
        jarsigner = shutil.which("jarsigner")
        if not jarsigner:
            cfg = ConfigManager.load()
            jh = cfg.get("java_home")
            if jh and os.path.isfile(os.path.join(jh, "bin", "jarsigner.exe")):
                jarsigner = os.path.join(jh, "bin", "jarsigner.exe")

        if jarsigner:
            cmd = [jarsigner, "-keystore", ks, "-storepass", self.key_pass,
                   "-keypass", self.key_pass, str(apk_path), self.key_alias]
            self._log("   -> Đang ký bằng jarsigner...")
            result = subprocess.run(cmd, capture_output=True, text=True)
            if result.returncode == 0:
                self._log("   [OK] jarsigner thành công!")
                zipalign = shutil.which("zipalign")
                if zipalign:
                    aligned = Path(apk_path).with_suffix(".aligned.apk")
                    cmd2 = [zipalign, "-f", "-v", "4", str(apk_path), str(aligned)]
                    subprocess.run(cmd2, capture_output=True)
                    if aligned.exists():
                        shutil.move(str(aligned), apk_path)
                        self._log("   [OK] zipalign thành công")
                return True
            else:
                self._log(f"   [!] jarsigner thất bại: {result.stderr[:200]}")

        # 3. Ký tự động bằng bộ ký Python PKCS#7 tích hợp (Hoàn toàn độc lập, không cần Java)
        try:
            self._log("   -> Đang ký bằng bộ ký PKCS#7 tích hợp (Tự động hoàn toàn)...")
            self._sign_pure_python(apk_path, ks, self.key_pass)
            self._log("   [OK] Đã ký APK thành công với chứng chỉ Debug (Sẵn sàng cài đặt)!")
            return True
        except Exception as e:
            self._log(f"   [ERROR] Ký APK thất bại: {e}")
            return False

    def cleanup(self):
        if self.temp_dir and self.temp_dir.exists():
            shutil.rmtree(self.temp_dir, ignore_errors=True)
            self._log("[CLEAN] Đã dọn dẹp thư mục tạm")

    def run(self, output_path=None):
        self._log("=" * 60)
        self._log("[TSM] Công cụ sửa IP/Port APK")
        self._log(f"   APK: {self.apk_path}")
        self._log(f"   IP mới: {self.new_ip}")
        self._log(f"   Port mới: {self.new_port}")
        self._log(f"   CDN IP: {self.new_cdn_ip}")
        self._log(f"   CDN Port: {self.new_cdn_port}")
        self._log("=" * 60)
        try:
            self.extract_apk()
            self.replace_serverlist()
            self.replace_global_metadata()
            self.replace_data_unity3d()
            out = self.repackage_apk(output_path)
            self.sign_apk(str(out))
            self._log("\n" + "=" * 60)
            self._log(f"[DONE] Hoàn thành! APK đã đổi IP/Port: {out}")
            self._log("=" * 60)
            return out
        except Exception as e:
            self._log(f"\n[ERROR] Lỗi: {e}")
            raise
        finally:
            self.cleanup()


# ============================================================================
# ĐIỂM VÀO CHƯƠNG TRÌNH CHÍNH
# ============================================================================
def main():
    app = TSMApp()
    app.protocol("WM_DELETE_WINDOW", app.on_close)
    app.mainloop()


if __name__ == "__main__":
    main()
