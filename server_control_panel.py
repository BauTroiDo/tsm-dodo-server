#!/usr/bin/env python3
"""
server_control_panel.py — Bảng điều khiển Máy chủ TS Mobile Online (tsm_dodo)
=============================================================================
Tính năng:
  - Tự động quét & tương thích khi di chuyển thư mục sang ổ đĩa hoặc máy tính khác.
  - Quản lý & Bật/Tắt CDN Server (port 8080 / 443).
  - Quản lý & Bật/Tắt Game Server (port 6613, Gradle app:run).
  - Quản lý & Giám sát ADB Reverse (chuyển tiếp port giả lập Nox / LDPlayer -> PC).
  - Kiểm tra kết nối MySQL database (port 3306).
  - Tích hợp công cụ giải mã / mã hóa / đổi IP Network.lua tự động.
  - Xem nhật ký (logs) thời gian thực của từng máy chủ.
"""

import hashlib
import json
import os
import queue
import re
import shutil
import socket
import string
import subprocess
import sys
import threading
import time
import tkinter as tk
from tkinter import ttk, filedialog, messagebox
from pathlib import Path

# Đảm bảo UTF-8 trên console Windows
if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

# ============================================================================
# Đường dẫn gốc ĐỘNG (tự thích ứng khi đổi ổ đĩa / máy tính)
# ============================================================================
ROOT_DIR = Path(__file__).resolve().parent
CONFIG_FILE = ROOT_DIR / "launcher_config.json"


# ============================================================================
# Bộ hàm quét & phát hiện môi trường tự động
# ============================================================================
def get_local_lan_ip() -> str:
    """Lấy địa chỉ IP mạng nội bộ (LAN) của PC."""
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


def get_available_drives() -> list[str]:
    """Lấy danh sách các ổ đĩa hiện có trên máy tính Windows (C:, D:, E:...)."""
    if sys.platform == "win32":
        return [f"{d}:\\" for d in string.ascii_uppercase if os.path.exists(f"{d}:\\")]
    return ["/"]


def scan_for_jdk21() -> str | None:
    """
    Quét tự động JDK 21 (hoặc JDK mới nhất) trên toàn bộ các ổ đĩa của máy tính:
    1. Kiểm tra biến môi trường JAVA_HOME hiện tại
    2. Kiểm tra lệnh java trong PATH
    3. Quét tất cả các thư mục cài đặt Java phổ biến trên mọi ổ đĩa
    """
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

    # 3. Quét trên tất cả các ổ đĩa
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

    # Ưu tiên bản có số 21 trong tên
    for c in candidates:
        if "21" in os.path.basename(c):
            return c

    return candidates[0] if candidates else None


def scan_for_adb() -> str | None:
    """
    Quét tự động tệp ADB (Nox / LDPlayer / MEmu / Platform-tools) trên toàn bộ máy tính:
    """
    # 1. Kiểm tra PATH
    for cmd_name in ["nox_adb", "adb"]:
        w = shutil.which(cmd_name)
        if w:
            return w

    # 2. Quét các đường dẫn cài đặt giả lập trên tất cả ổ đĩa
    drives = get_available_drives()
    patterns = [
        "Program Files/Nox/bin/nox_adb.exe",
        "Program Files (x86)/Nox/bin/nox_adb.exe",
        "Nox/bin/nox_adb.exe",
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
# Quản lý Cấu hình (launcher_config.json)
# ============================================================================
class ConfigManager:
    @staticmethod
    def load() -> dict:
        default_cfg = {
            "java_home": "",
            "adb_path": "",
            "cdn_port": 8080,
            "game_port": 6613,
            "mysql_host": "127.0.0.1",
            "mysql_port": 3306,
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

        # Tự động quét lại nếu đường dẫn đã lưu không còn tồn tại trên máy/ổ đĩa này
        saved_java = default_cfg.get("java_home")
        if not saved_java or not os.path.isfile(os.path.join(saved_java, "bin", "java.exe")):
            detected_java = scan_for_jdk21()
            if detected_java:
                default_cfg["java_home"] = detected_java

        saved_adb = default_cfg.get("adb_path")
        if not saved_adb or not os.path.isfile(saved_adb):
            detected_adb = scan_for_adb()
            if detected_adb:
                default_cfg["adb_path"] = detected_adb

        return default_cfg

    @staticmethod
    def save(cfg: dict):
        try:
            with open(CONFIG_FILE, "w", encoding="utf-8") as f:
                json.dump(cfg, f, indent=2, ensure_ascii=False)
        except Exception:
            pass


# ============================================================================
# Lớp Quản lý Tiến trình Máy chủ (Process Runner)
# ============================================================================
class ServerProcess:
    def __init__(self, name: str, on_log=None):
        self.name = name
        self.on_log = on_log
        self.process: subprocess.Popen | None = None
        self.is_running = False
        self._reader_thread: threading.Thread | None = None

    def start(self, cmd: list[str], cwd: str, env: dict | None = None):
        if self.is_running and self.process and self.process.poll() is None:
            return False

        full_env = os.environ.copy()
        if env:
            full_env.update(env)

        # Ẩn cửa sổ cmd con trên Windows nhưng vẫn bắt được stdout
        startupinfo = None
        if sys.platform == "win32":
            startupinfo = subprocess.STARTUPINFO()
            startupinfo.dwFlags |= subprocess.STARTF_USESHOWWINDOW
            startupinfo.wShowWindow = subprocess.SW_HIDE

        try:
            self.process = subprocess.Popen(
                cmd,
                cwd=cwd,
                env=full_env,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                startupinfo=startupinfo,
                text=True,
                encoding="utf-8",
                errors="replace",
                bufsize=1,
            )
            self.is_running = True

            # Luồng đọc output thời gian thực
            self._reader_thread = threading.Thread(target=self._stream_output, daemon=True)
            self._reader_thread.start()
            return True
        except Exception as e:
            if self.on_log:
                self.on_log(self.name, f"[ERROR] Không thể khởi động {self.name}: {e}")
            self.is_running = False
            return False

    def _stream_output(self):
        try:
            if self.process and self.process.stdout:
                for line in self.process.stdout:
                    line_str = line.rstrip()
                    if line_str and self.on_log:
                        self.on_log(self.name, line_str)
        except Exception:
            pass
        finally:
            self.is_running = False
            if self.on_log:
                code = self.process.poll() if self.process else -1
                self.on_log(self.name, f"[{self.name}] Tiến trình đã kết thúc (Exit Code: {code}).")

    def stop(self):
        if self.process:
            try:
                # Trên Windows cần kill cả cây tiến trình con
                if sys.platform == "win32":
                    subprocess.run(
                        ["taskkill", "/F", "/T", "/PID", str(self.process.pid)],
                        stdout=subprocess.DEVNULL,
                        stderr=subprocess.DEVNULL,
                    )
                else:
                    self.process.terminate()
            except Exception:
                pass
            self.process = None
        self.is_running = False


# ============================================================================
# Giao diện chính của Control Panel
# ============================================================================
class TSMControlPanel(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("TS Mobile Online — Bảng điều khiển Máy chủ (Control Panel)")
        self.geometry("960x760")
        self.minsize(860, 640)

        # Cấu hình nạp
        self.cfg = ConfigManager.load()
        self.lan_ip = get_local_lan_ip()

        # Tiến trình server
        self.cdn_proc = ServerProcess("CDN Server", self._handle_server_log)
        self.game_proc = ServerProcess("Game Server", self._handle_server_log)
        self.adb_proc = ServerProcess("ADB Reverse", self._handle_server_log)

        # Log queue
        self.log_queue = queue.Queue()

        # Styles
        self._init_styles()

        # Layout
        self._build_header()
        self._build_quick_bar()
        self._build_tabs()
        self._build_bottom_log()

        # Polling trạng thái định kỳ
        self.after(500, self._process_log_queue)
        self.after(1000, self._periodic_health_check)

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
        style.configure("TNotebook.Tab", font=self.font_bold, padding=(14, 7))

    def _build_header(self):
        header = tk.Frame(self, bg="#0078D4", height=65)
        header.pack(fill="x", side="top")

        left_f = tk.Frame(header, bg="#0078D4")
        left_f.pack(side="left", padx=16, pady=8)

        t_lbl = tk.Label(
            left_f,
            text="TS Mobile Online — Control Panel",
            font=("Segoe UI", 14, "bold"),
            fg="white",
            bg="#0078D4",
        )
        t_lbl.pack(anchor="w")

        sub_text = f"Thư mục gốc: {ROOT_DIR}  |  IP LAN máy tính: {self.lan_ip}"
        sub_lbl = tk.Label(left_f, text=sub_text, font=("Segoe UI", 9), fg="#E0E0E0", bg="#0078D4")
        sub_lbl.pack(anchor="w")

    def _build_quick_bar(self):
        bar = tk.Frame(self, bg="#FFFFFF", height=50, relief="groove", bd=1)
        bar.pack(fill="x", padx=12, pady=(10, 0))

        # Status Indicators
        st_frame = tk.Frame(bar, bg="#FFFFFF")
        st_frame.pack(side="left", padx=12, pady=8)

        self.cdn_status_lbl = tk.Label(st_frame, text=f"CDN: 🔴 Offline ({self.cdn_port_entry.get().strip() or self.cfg.get('cdn_port', 443)})", font=self.font_bold, bg="#FFFFFF", fg="#D13438")
        self.cdn_status_lbl.pack(side="left", padx=10)

        self.game_status_lbl = tk.Label(st_frame, text="Game: 🔴 Offline (6613)", font=self.font_bold, bg="#FFFFFF", fg="#D13438")
        self.game_status_lbl.pack(side="left", padx=10)

        self.mysql_status_lbl = tk.Label(st_frame, text="MySQL: ⚪ Đang kiểm tra (3306)", font=self.font_bold, bg="#FFFFFF", fg="#555555")
        self.mysql_status_lbl.pack(side="left", padx=10)

        # Quick Action Buttons
        btn_f = tk.Frame(bar, bg="#FFFFFF")
        btn_f.pack(side="right", padx=12, pady=6)

        start_all_btn = ttk.Button(btn_f, text="⚡ BẬT TẤT CẢ SERVER", style="Success.TButton", command=self.start_all_servers)
        start_all_btn.pack(side="left", padx=4)

        stop_all_btn = ttk.Button(btn_f, text="🛑 TẮT TẤT CẢ", style="Danger.TButton", command=self.stop_all_servers)
        stop_all_btn.pack(side="left", padx=4)

        scan_btn = ttk.Button(btn_f, text="🔍 Quét lại", command=self.rescan_environment)
        scan_btn.pack(side="left", padx=4)

    def _build_tabs(self):
        self.notebook = ttk.Notebook(self)
        self.notebook.pack(fill="both", expand=True, padx=12, pady=10)

        # Tab 1: Quản lý Server
        self.tab_servers = ttk.Frame(self.notebook, padding=12)
        self.notebook.add(self.tab_servers, text="🖥️ Quản lý Server")
        self._init_tab_servers()

        # Tab 2: Cấu hình Môi trường (Auto-Scan)
        self.tab_env = ttk.Frame(self.notebook, padding=12)
        self.notebook.add(self.tab_env, text="⚙️ Cấu hình Môi trường (Tự quét)")
        self._init_tab_env()

        # Tab 3: Công cụ Lua (Network.lua)
        self.tab_lua = ttk.Frame(self.notebook, padding=12)
        self.notebook.add(self.tab_lua, text="🔧 Công cụ Lua & Đổi IP")
        self._init_tab_lua()

        # Tab 4: MySQL & Database
        self.tab_db = ttk.Frame(self.notebook, padding=12)
        self.notebook.add(self.tab_db, text="🗄️ MySQL Database")
        self._init_tab_db()

    def _build_bottom_log(self):
        log_frame = tk.LabelFrame(self, text="Nhật ký hệ thống (Console Logs)", font=self.font_bold)
        log_frame.pack(fill="both", expand=False, side="bottom", padx=12, pady=(0, 10))

        top_ctrl = tk.Frame(log_frame)
        top_ctrl.pack(fill="x", padx=6, pady=2)

        tk.Label(top_ctrl, text="Kênh nhật ký:").pack(side="left")
        self.log_filter_var = tk.StringVar(value="Tất cả")
        filter_cb = ttk.Combobox(top_ctrl, textvariable=self.log_filter_var, values=["Tất cả", "CDN Server", "Game Server", "ADB Reverse", "Hệ thống"], state="readonly", width=14)
        filter_cb.pack(side="left", padx=6)

        clear_btn = ttk.Button(top_ctrl, text="Xóa log", command=self._clear_logs)
        clear_btn.pack(side="right", padx=4)

        text_wrap = tk.Frame(log_frame)
        text_wrap.pack(fill="both", expand=True, padx=6, pady=(0, 6))

        self.log_text = tk.Text(text_wrap, height=7, font=self.font_code, wrap="word", bg="#1E1E1E", fg="#D4D4D4")
        self.log_text.pack(side="left", fill="both", expand=True)

        sc = ttk.Scrollbar(text_wrap, orient="vertical", command=self.log_text.yview)
        sc.pack(side="right", fill="y")
        self.log_text.config(yscrollcommand=sc.set)

    def _handle_server_log(self, channel: str, msg: str):
        self.log_queue.put((channel, msg))

    def _process_log_queue(self):
        current_filter = self.log_filter_var.get()
        while not self.log_queue.empty():
            channel, msg = self.log_queue.get()
            if current_filter == "Tất cả" or current_filter == channel:
                timestamp = time.strftime("%H:%M:%S")
                self.log_text.insert("end", f"[{timestamp}] [{channel}] {msg}\n")
                self.log_text.see("end")
        self.after(200, self._process_log_queue)

    def _clear_logs(self):
        self.log_text.delete("1.0", "end")

    def append_log(self, channel: str, msg: str):
        self.log_queue.put((channel, msg))

    # ========================================================================
    # TAB 1: Quản lý Server
    # ========================================================================
    def _init_tab_servers(self):
        # Grid 3 thẻ: CDN, Game Server, ADB
        container = tk.Frame(self.tab_servers)
        container.pack(fill="both", expand=True)

        # 1. Card CDN Server
        cdn_card = tk.LabelFrame(container, text="1. CDN Server (Máy chủ tải tài nguyên Game)", font=self.font_bold, padx=10, pady=10)
        cdn_card.pack(fill="x", pady=6)

        c_top = tk.Frame(cdn_card)
        c_top.pack(fill="x")

        self.cdn_card_status = tk.Label(c_top, text="Trạng thái: 🔴 Chưa chạy", font=self.font_bold, fg="#D13438")
        self.cdn_card_status.pack(side="left")

        tk.Label(c_top, text="Port:").pack(side="left", padx=(20, 4))
        self.cdn_port_entry = ttk.Entry(c_top, width=8)
        self.cdn_port_entry.insert(0, str(self.cfg.get("cdn_port", 443)))
        self.cdn_port_entry.pack(side="left")

        self.cdn_start_btn = ttk.Button(c_top, text="▶ Khởi động CDN", style="Success.TButton", command=self.start_cdn_server)
        self.cdn_start_btn.pack(side="right", padx=4)

        self.cdn_stop_btn = ttk.Button(c_top, text="⏹ Dừng", style="Danger.TButton", command=self.stop_cdn_server)
        self.cdn_stop_btn.pack(side="right", padx=4)

        tk.Label(cdn_card, text="Phục vụ tài nguyên từ: cdn/mirror/tsr/... cho game client", fg="#666666", font=("Segoe UI", 9)).pack(anchor="w", pady=(4, 0))

        # 2. Card Game Server
        game_card = tk.LabelFrame(container, text="2. Game Server (Máy chủ logic & kết nối người chơi)", font=self.font_bold, padx=10, pady=10)
        game_card.pack(fill="x", pady=6)

        g_top = tk.Frame(game_card)
        g_top.pack(fill="x")

        self.game_card_status = tk.Label(g_top, text="Trạng thái: 🔴 Chưa chạy", font=self.font_bold, fg="#D13438")
        self.game_card_status.pack(side="left")

        tk.Label(g_top, text="Port:").pack(side="left", padx=(20, 4))
        self.game_port_entry = ttk.Entry(g_top, width=8)
        self.game_port_entry.insert(0, str(self.cfg.get("game_port", 6613)))
        self.game_port_entry.pack(side="left")

        self.game_start_btn = ttk.Button(g_top, text="▶ Khởi động Game Server", style="Success.TButton", command=self.start_game_server)
        self.game_start_btn.pack(side="right", padx=4)

        self.game_stop_btn = ttk.Button(g_top, text="⏹ Dừng", style="Danger.TButton", command=self.stop_game_server)
        self.game_stop_btn.pack(side="right", padx=4)

        self.game_card_desc = tk.Label(game_card, text="Chạy qua: gradlew.bat app:run (Tự động nạp GAMEDATA_PATH & JAVA_HOME động)", fg="#666666", font=("Segoe UI", 9))
        self.game_card_desc.pack(anchor="w", pady=(4, 0))

        # 3. Card ADB Reverse
        adb_card = tk.LabelFrame(container, text="3. Chuyển tiếp cổng Giả lập (ADB Reverse Nox / LDPlayer)", font=self.font_bold, padx=10, pady=10)
        adb_card.pack(fill="x", pady=6)

        a_top = tk.Frame(adb_card)
        a_top.pack(fill="x")

        self.adb_card_status = tk.Label(a_top, text="Trạng thái: 🔴 Chưa kích hoạt", font=self.font_bold, fg="#D13438")
        self.adb_card_status.pack(side="left")

        self.adb_start_btn = ttk.Button(a_top, text="▶ Bật ADB Reverse", style="Success.TButton", command=self.start_adb_reverse)
        self.adb_start_btn.pack(side="right", padx=4)

        self.adb_stop_btn = ttk.Button(a_top, text="⏹ Dừng", style="Danger.TButton", command=self.stop_adb_reverse)
        self.adb_stop_btn.pack(side="right", padx=4)

        tk.Label(adb_card, text="Cho phép Game trong giả lập kết nối 127.0.0.1:8080 (CDN) và 127.0.0.1:6613 (Game Server)", fg="#666666", font=("Segoe UI", 9)).pack(anchor="w", pady=(4, 0))

    # ========================================================================
    # TAB 2: Cấu hình Môi trường (Auto-Scan)
    # ========================================================================
    def _init_tab_env(self):
        f = tk.Frame(self.tab_env)
        f.pack(fill="both", expand=True)

        desc = (
            "Hệ thống tự động phát hiện và tương thích khi bạn di chuyển thư mục game\n"
            "sang bất kỳ ổ đĩa nào (C:, D:, E:...) hoặc chuyển sang máy tính khác."
        )
        tk.Label(f, text=desc, font=("Segoe UI", 9, "italic"), fg="#005A9E", justify="left").pack(anchor="w", pady=(0, 10))

        # Root directory
        r_box = tk.LabelFrame(f, text="Thư mục gốc Dự án (Root Directory - Luôn tự nhận diện)", font=self.font_bold, padx=8, pady=8)
        r_box.pack(fill="x", pady=5)
        self.env_root_lbl = tk.Label(r_box, text=str(ROOT_DIR), font=self.font_code, fg="#0078D4")
        self.env_root_lbl.pack(anchor="w")

        # Java JDK
        j_box = tk.LabelFrame(f, text="Java JDK 21 (Bắt buộc để chạy Game Server Kotlin/Java)", font=self.font_bold, padx=8, pady=8)
        j_box.pack(fill="x", pady=5)

        j_row = tk.Frame(j_box)
        j_row.pack(fill="x")
        self.env_java_var = tk.StringVar(value=self.cfg.get("java_home", ""))
        self.env_java_entry = ttk.Entry(j_row, textvariable=self.env_java_var, font=self.font_code)
        self.env_java_entry.pack(side="left", fill="x", expand=True, padx=(0, 6))
        ttk.Button(j_row, text="Duyệt...", command=self._browse_java).pack(side="left", padx=2)
        ttk.Button(j_row, text="Tự quét lại", command=self._scan_java_only).pack(side="left", padx=2)

        self.java_ver_status = tk.Label(j_box, text="Đang kiểm tra...", font=("Segoe UI", 9))
        self.java_ver_status.pack(anchor="w", pady=(4, 0))

        # ADB Path
        adb_box = tk.LabelFrame(f, text="Trình gỡ lỗi ADB / Nox ADB (Dành cho giả lập)", font=self.font_bold, padx=8, pady=8)
        adb_box.pack(fill="x", pady=5)

        a_row = tk.Frame(adb_box)
        a_row.pack(fill="x")
        self.env_adb_var = tk.StringVar(value=self.cfg.get("adb_path", ""))
        self.env_adb_entry = ttk.Entry(a_row, textvariable=self.env_adb_var, font=self.font_code)
        self.env_adb_entry.pack(side="left", fill="x", expand=True, padx=(0, 6))
        ttk.Button(a_row, text="Duyệt...", command=self._browse_adb).pack(side="left", padx=2)
        ttk.Button(a_row, text="Tự quét lại", command=self._scan_adb_only).pack(side="left", padx=2)

        # Actions
        btn_box = tk.Frame(f)
        btn_box.pack(fill="x", pady=15)

        save_cfg_btn = ttk.Button(btn_box, text="💾 Lưu Cấu hình Môi trường", style="Primary.TButton", command=self._save_env_config)
        save_cfg_btn.pack(side="left", padx=4)

        full_scan_btn = ttk.Button(btn_box, text="🔄 Quét toàn bộ máy tính tìm JDK & ADB", command=self.rescan_environment)
        full_scan_btn.pack(side="left", padx=4)

        self._check_java_version_ui()

    def _browse_java(self):
        d = filedialog.askdirectory(title="Chọn thư mục cài đặt JDK (chứa thư mục bin)")
        if d:
            self.env_java_var.set(d)
            self._check_java_version_ui()

    def _browse_adb(self):
        f = filedialog.askopenfilename(
            title="Chọn tệp nox_adb.exe hoặc adb.exe",
            filetypes=[("Executable", "*.exe"), ("All Files", "*.*")],
        )
        if f:
            self.env_adb_var.set(f)

    def _scan_java_only(self):
        j = scan_for_jdk21()
        if j:
            self.env_java_var.set(j)
            messagebox.showinfo("Quét Java", f"Đã tìm thấy JDK tại:\n{j}")
        else:
            messagebox.showwarning("Quét Java", "Không tìm thấy thư mục cài đặt JDK 21 tự động.\nVui lòng chọn thủ công bằng nút 'Duyệt...'.")
        self._check_java_version_ui()

    def _scan_adb_only(self):
        a = scan_for_adb()
        if a:
            self.env_adb_var.set(a)
            messagebox.showinfo("Quét ADB", f"Đã tìm thấy ADB tại:\n{a}")
        else:
            messagebox.showwarning("Quét ADB", "Không tìm thấy ADB tự động.\nVui lòng mở Nox trước hoặc chọn thủ công.")

    def _check_java_version_ui(self):
        jh = self.env_java_var.get().strip()
        java_exe = os.path.join(jh, "bin", "java.exe")
        if os.path.isfile(java_exe):
            try:
                res = subprocess.run([java_exe, "-version"], stderr=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
                ver_line = (res.stderr or res.stdout).splitlines()[0]
                self.java_ver_status.config(text=f"🟢 Hợp lệ: {ver_line}", fg="#107C41")
                return True
            except Exception as e:
                self.java_ver_status.config(text=f"⚠️ Lỗi chạy java.exe: {e}", fg="#D13438")
                return False
        else:
            self.java_ver_status.config(text="🔴 Không tìm thấy java.exe trong thư mục bin!", fg="#D13438")
            return False

    def _save_env_config(self):
        self.cfg["java_home"] = self.env_java_var.get().strip()
        self.cfg["adb_path"] = self.env_adb_var.get().strip()
        ConfigManager.save(self.cfg)
        self.append_log("Hệ thống", "Đã lưu cấu hình môi trường vào launcher_config.json.")
        messagebox.showinfo("Thông báo", "Đã lưu cấu hình môi trường thành công!")

    # ========================================================================
    # TAB 3: Công cụ Lua (Đổi IP Network.lua)
    # ========================================================================
    def _init_tab_lua(self):
        f = tk.Frame(self.tab_lua)
        f.pack(fill="both", expand=True)

        net_card = tk.LabelFrame(f, text="Đổi IP Server trong Network.lua (Tự động giải mã -> sửa IP -> mã hóa lại)", font=self.font_bold, padx=10, pady=10)
        net_card.pack(fill="x", pady=6)

        # File target
        f_row = tk.Frame(net_card)
        f_row.pack(fill="x", pady=4)
        tk.Label(f_row, text="File Network.lua:").pack(side="left")
        default_net = ROOT_DIR / "cdn" / "data" / "Logic" / "Network.lua"
        self.lua_net_var = tk.StringVar(value=str(default_net))
        ttk.Entry(f_row, textvariable=self.lua_net_var, font=self.font_code).pack(side="left", fill="x", expand=True, padx=6)
        ttk.Button(f_row, text="Chọn...", command=self._browse_lua_net).pack(side="right")

        # IP Host, Port, Name
        cfg_row = tk.Frame(net_card)
        cfg_row.pack(fill="x", pady=8)

        tk.Label(cfg_row, text="IP / Host:").grid(row=0, column=0, sticky="w", padx=4, pady=4)
        self.lua_host_var = tk.StringVar(value=self.lan_ip)
        ttk.Entry(cfg_row, textvariable=self.lua_host_var, width=20, font=self.font_main).grid(row=0, column=1, sticky="w", padx=6, pady=4)

        tk.Label(cfg_row, text="Port:").grid(row=0, column=2, sticky="w", padx=(14, 4), pady=4)
        self.lua_port_var = tk.StringVar(value="6613")
        ttk.Entry(cfg_row, textvariable=self.lua_port_var, width=10, font=self.font_main).grid(row=0, column=3, sticky="w", padx=6, pady=4)

        tk.Label(cfg_row, text="Tên hiển thị:").grid(row=0, column=4, sticky="w", padx=(14, 4), pady=4)
        self.lua_name_var = tk.StringVar(value="Dev Server")
        ttk.Entry(cfg_row, textvariable=self.lua_name_var, width=20, font=self.font_main).grid(row=0, column=5, sticky="w", padx=6, pady=4)

        # Quick IP presets
        preset_row = tk.Frame(net_card)
        preset_row.pack(fill="x", pady=4)
        tk.Label(preset_row, text="Gợi ý nhanh IP:").pack(side="left")
        ttk.Button(preset_row, text=f"IP LAN máy tính ({self.lan_ip})", command=lambda: self.lua_host_var.set(self.lan_ip)).pack(side="left", padx=4)
        ttk.Button(preset_row, text="Localhost (127.0.0.1)", command=lambda: self.lua_host_var.set("127.0.0.1")).pack(side="left", padx=4)
        ttk.Button(preset_row, text="Dev Gốc (192.168.0.157)", command=lambda: self.lua_host_var.set("192.168.0.157")).pack(side="left", padx=4)

        # Buttons
        b_row = tk.Frame(net_card)
        b_row.pack(fill="x", pady=(12, 4))
        ttk.Button(b_row, text="🔄 Đọc IP hiện tại từ file", command=self._read_current_lua_ip).pack(side="left", padx=4)
        ttk.Button(b_row, text="🚀 Cập nhật & Mã hóa Network.lua ngay", style="Primary.TButton", command=self._apply_lua_ip).pack(side="right", padx=4)

        # Standalone Lua Decrypt/Encrypt section
        more_card = tk.LabelFrame(f, text="Mở rộng: Trình soạn thảo & Giải mã / Mã hóa Lua tùy ý", font=self.font_bold, padx=10, pady=10)
        more_card.pack(fill="x", pady=10)

        tk.Label(more_card, text="Bạn có thể mở giao diện Soạn thảo & Giải mã chi tiết tất cả các file .lua:").pack(anchor="w")
        ttk.Button(more_card, text="📝 Mở Trình soạn thảo & Giải mã Lua GUI chuyên dụng", command=self._open_dedicated_lua_gui).pack(anchor="w", pady=6)

    def _browse_lua_net(self):
        f = filedialog.askopenfilename(
            title="Chọn file Network.lua",
            filetypes=[("Lua Files", "*.lua"), ("All Files", "*.*")],
        )
        if f:
            self.lua_net_var.set(f)
            self._read_current_lua_ip()

    def _read_current_lua_ip(self):
        p = Path(self.lua_net_var.get())
        if not p.is_file():
            messagebox.showerror("Lỗi", f"Không tìm thấy file: {p}")
            return
        try:
            from lua_crypt import find_reference_plain, compute_keystream, apply_keystream
            enc = p.read_bytes()
            ref_plain, _ = find_reference_plain("Logic/Network.lua", "Network.lua")
            if not ref_plain:
                messagebox.showerror("Lỗi", "Không tìm thấy plain text reference để giải mã!")
                return
            ks = compute_keystream(ref_plain, enc)
            dec = apply_keystream(enc, ks).decode("utf-8", errors="replace")
            m = re.search(r'this\.servers\s*=\s*\{\{id=1,\s*host="([^"]+)",\s*port=(\d+),\s*name="([^"]+)"', dec)
            if m:
                self.lua_host_var.set(m.group(1))
                self.lua_port_var.set(m.group(2))
                self.lua_name_var.set(m.group(3))
                self.append_log("Hệ thống", f"Đọc được IP Network.lua: {m.group(1)}:{m.group(2)} ({m.group(3)})")
            else:
                messagebox.showwarning("Thông báo", "Không tìm thấy mẫu cấu hình this.servers trong file!")
        except Exception as e:
            messagebox.showerror("Lỗi", str(e))

    def _apply_lua_ip(self):
        p = Path(self.lua_net_var.get())
        if not p.is_file():
            messagebox.showerror("Lỗi", f"Không tìm thấy file: {p}")
            return

        host = self.lua_host_var.get().strip()
        port_str = self.lua_port_var.get().strip()
        name = self.lua_name_var.get().strip()

        try:
            port = int(port_str)
        except ValueError:
            messagebox.showerror("Lỗi", "Port phải là số!")
            return

        try:
            from lua_crypt import set_server_ip
            set_server_ip(p, host, port)
            md5_hash = hashlib.md5(p.read_bytes()).hexdigest().upper()
            self.append_log("Hệ thống", f"Đã cập nhật Network.lua -> Host={host}, Port={port} (MD5={md5_hash})")

            # Đồng bộ sang CDN Mirror
            self._sync_mirror_network(p, md5_hash)
            messagebox.showinfo("Thành công", f"Đã cập nhật Network.lua thành công!\nHost: {host}:{port}\nMD5: {md5_hash}")
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
                self.append_log("Hệ thống", f"Đã đồng bộ sang {count} bản CDN mirror và cập nhật LuaScripts.dat.")

    def _open_dedicated_lua_gui(self):
        try:
            gui_script = ROOT_DIR / "lua_crypt_gui.py"
            subprocess.Popen([sys.executable, str(gui_script)], cwd=str(ROOT_DIR))
        except Exception as e:
            messagebox.showerror("Lỗi", f"Không thể mở GUI Lua: {e}")

    # ========================================================================
    # TAB 4: MySQL & Database
    # ========================================================================
    def _init_tab_db(self):
        f = tk.Frame(self.tab_db)
        f.pack(fill="both", expand=True)

        db_card = tk.LabelFrame(f, text="Cấu hình Kết nối MySQL Database", font=self.font_bold, padx=10, pady=10)
        db_card.pack(fill="x", pady=6)

        # Host & Port
        r1 = tk.Frame(db_card)
        r1.pack(fill="x", pady=4)
        tk.Label(r1, text="Host:").pack(side="left")
        self.db_host_var = tk.StringVar(value=self.cfg.get("mysql_host", "127.0.0.1"))
        ttk.Entry(r1, textvariable=self.db_host_var, width=16).pack(side="left", padx=(4, 20))

        tk.Label(r1, text="Port:").pack(side="left")
        self.db_port_var = tk.StringVar(value=str(self.cfg.get("mysql_port", 3306)))
        ttk.Entry(r1, textvariable=self.db_port_var, width=8).pack(side="left", padx=4)

        # User & Password
        r2 = tk.Frame(db_card)
        r2.pack(fill="x", pady=4)
        tk.Label(r2, text="User:").pack(side="left")
        self.db_user_var = tk.StringVar(value=self.cfg.get("mysql_user", "root"))
        ttk.Entry(r2, textvariable=self.db_user_var, width=16).pack(side="left", padx=(4, 20))

        tk.Label(r2, text="Mật khẩu:").pack(side="left")
        self.db_pass_var = tk.StringVar(value=self.cfg.get("mysql_password", ""))
        ttk.Entry(r2, textvariable=self.db_pass_var, width=16, show="*").pack(side="left", padx=4)

        # Database name
        r3 = tk.Frame(db_card)
        r3.pack(fill="x", pady=4)
        tk.Label(r3, text="Tên Database:").pack(side="left")
        self.db_name_var = tk.StringVar(value=self.cfg.get("mysql_database", "tsm_dodo"))
        ttk.Entry(r3, textvariable=self.db_name_var, width=20).pack(side="left", padx=4)

        # Actions
        r4 = tk.Frame(db_card)
        r4.pack(fill="x", pady=10)
        ttk.Button(r4, text="🔍 Kiểm tra kết nối MySQL Port", command=self._test_mysql_port).pack(side="left", padx=4)

        sql_file = ROOT_DIR / "SQL" / "tsm_dodo.sql"
        if sql_file.is_file():
            ttk.Button(r4, text="📂 Mở thư mục chứa file SQL (tsm_dodo.sql)", command=lambda: os.startfile(str(sql_file.parent))).pack(side="left", padx=4)

        info_lbl = tk.Label(
            f,
            text="Ghi chú:\n"
                 "- Game Server yêu cầu MySQL đang chạy (port 3306) với database 'tsm_dodo'.\n"
                 "- Nếu dùng XAMPP / phpMyAdmin / Navicat, hãy tạo CSDL 'tsm_dodo' và import file 'SQL/tsm_dodo.sql'.\n"
                 "- Cấu hình kết nối được lưu trong app/src/main/resources/application.conf.",
            justify="left",
            fg="#555555",
            font=("Segoe UI", 9),
        )
        info_lbl.pack(anchor="w", pady=10)

    def _test_mysql_port(self):
        host = self.db_host_var.get().strip()
        try:
            port = int(self.db_port_var.get().strip())
        except ValueError:
            port = 3306

        is_open = check_port_listening(port, host, timeout=1.0)
        if is_open:
            messagebox.showinfo("MySQL", f"🟢 Kết nối thành công tới MySQL tại {host}:{port}!")
            self.append_log("Hệ thống", f"Cổng MySQL {host}:{port} đang mở và hoạt động tốt.")
        else:
            messagebox.showwarning("MySQL", f"🔴 Không thể kết nối tới MySQL tại {host}:{port}.\nHãy chắc chắn bạn đã bật XAMPP / MySQL Service.")
            self.append_log("Hệ thống", f"Cổng MySQL {host}:{port} hiện đang đóng (offline).")

    # ========================================================================
    # Server Lifecycle Actions
    # ========================================================================
    def start_cdn_server(self):
        port = self.cdn_port_entry.get().strip() or "8080"
        self.append_log("Hệ thống", f"Đang khởi động CDN Server trên cổng {port}...")
        cmd = [sys.executable, "cdn_server.py", "--port", str(port)]
        success = self.cdn_proc.start(cmd, cwd=str(ROOT_DIR))
        if success:
            self.append_log("Hệ thống", "Lệnh khởi động CDN Server đã được gửi.")

    def stop_cdn_server(self):
        self.append_log("Hệ thống", "Đang dừng CDN Server...")
        self.cdn_proc.stop()
        try:
            val = self.cdn_port_entry.get().strip()
            port = int(val) if val else int(self.cfg.get("cdn_port", 443))
        except ValueError:
            port = 443

        # Quét và diệt cả cổng cấu hình lẫn các cổng CDN phổ biến 443, 8080
        ports_to_kill = {port, 443, 8080}
        pids = stop_listeners_on_ports(ports_to_kill)
        # Diệt triệt để bất kỳ tiến trình python nào đang chạy cdn_server.py
        more_pids = kill_processes_by_commandline(["cdn_server.py"])
        all_pids = sorted(set(pids + more_pids))
        if all_pids:
            self.append_log("Hệ thống", f"Đã dừng triệt để CDN Server (PID: {', '.join(map(str, all_pids))}).")
        else:
            self.append_log("Hệ thống", "CDN Server đã dừng.")

    def start_game_server(self):
        jh = self.env_java_var.get().strip()
        if not jh or not os.path.isfile(os.path.join(jh, "bin", "java.exe")):
            messagebox.showerror(
                "Thiếu Java JDK 21",
                "Game Server yêu cầu Java JDK 21 để chạy.\n"
                "Vui lòng vào tab 'Cấu hình Môi trường' để Quét hoặc Chọn thư mục JDK 21!",
            )
            return

        # Môi trường động - giải quyết triệt để lỗi hardcode đường dẫn ổ đĩa
        gamedata_path = str(ROOT_DIR / "cdn" / "mirror" / "tsr" / "2.4.76" / "ResourcesPacked").replace("\\", "/")
        server_data_path = str(ROOT_DIR / "gamedata").replace("\\", "/")

        env = {
            "JAVA_HOME": jh,
            "PATH": f"{jh}\\bin;{os.environ.get('PATH', '')}",
            "GAMEDATA_PATH": gamedata_path,
            "SERVER_DATA_PATH": server_data_path,
        }

        self.append_log("Hệ thống", f"Đang khởi động Game Server...")
        self.append_log("Game Server", f"Sử dụng JAVA_HOME: {jh}")
        self.append_log("Game Server", f"GAMEDATA_PATH: {gamedata_path}")

        # Lệnh gradlew.bat app:run
        cmd = ["cmd.exe", "/c", "gradlew.bat", "app:run"]
        success = self.game_proc.start(cmd, cwd=str(ROOT_DIR), env=env)
        if success:
            self.append_log("Hệ thống", "Lệnh khởi động Game Server đã được gửi (Quá trình build có thể mất 15-30 giây).")

    def stop_game_server(self):
        self.append_log("Hệ thống", "Đang dừng Game Server...")
        self.game_proc.stop()
        try:
            val = self.game_port_entry.get().strip()
            port = int(val) if val else int(self.cfg.get("game_port", 6613))
        except ValueError:
            port = 6613

        # 1. Dừng listener trên cổng game
        pids = stop_listeners_on_ports({port, 6613})

        # 2. Dừng Gradle Daemon
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

        # 3. Quét diệt triệt để các tiến trình java liên quan tsm_dodo, NettyGameServer hoặc app:run
        more_pids = kill_processes_by_commandline(["NettyGameServer", "tsm_dodo", "app:run"])
        all_pids = sorted(set(pids + more_pids))
        if all_pids:
            self.append_log("Hệ thống", f"Đã dừng triệt để Game Server (PID: {', '.join(map(str, all_pids))}).")
        else:
            self.append_log("Hệ thống", "Game Server đã dừng.")

    def start_adb_reverse(self):
        adb_script = ROOT_DIR / "adb_reverse_nox_127001.bat"
        if not adb_script.is_file():
            messagebox.showerror("Lỗi", "Không tìm thấy adb_reverse_nox_127001.bat!")
            return

        self.append_log("Hệ thống", "Đang kích hoạt trình giám sát ADB Reverse...")
        cmd = ["cmd.exe", "/c", str(adb_script)]
        self.adb_proc.start(cmd, cwd=str(ROOT_DIR))

    def stop_adb_reverse(self):
        self.append_log("Hệ thống", "Đang dừng ADB Reverse...")
        self.adb_proc.stop()
        kill_processes_by_commandline(["adb_reverse_nox"])
        self.append_log("Hệ thống", "Đã dừng ADB Reverse.")

    def start_all_servers(self):
        self.append_log("Hệ thống", "=== KÍCH HOẠT CHẾ ĐỘ BẬT TẤT CẢ SERVER ===")
        # 1. ADB Reverse
        self.start_adb_reverse()
        # 2. CDN Server
        self.start_cdn_server()
        # 3. Game Server
        self.start_game_server()

    def stop_all_servers(self):
        self.append_log("Hệ thống", "=== DỪNG TẤT CẢ SERVER ===")
        self.stop_cdn_server()
        self.stop_game_server()
        self.stop_adb_reverse()
        stop_listeners_on_ports({443, 8080, 6613})

    def rescan_environment(self):
        self.append_log("Hệ thống", "Đang quét lại toàn bộ môi trường và các cổng...")
        self.lan_ip = get_local_lan_ip()
        detected_java = scan_for_jdk21()
        if detected_java:
            self.env_java_var.set(detected_java)
            self.cfg["java_home"] = detected_java

        detected_adb = scan_for_adb()
        if detected_adb:
            self.env_adb_var.set(detected_adb)
            self.cfg["adb_path"] = detected_adb

        ConfigManager.save(self.cfg)
        self._check_java_version_ui()
        self._periodic_health_check()
        self.append_log("Hệ thống", "Đã quét lại hoàn tất!")
        messagebox.showinfo("Quét hoàn tất", f"Đã quét lại hệ thống!\nIP LAN: {self.lan_ip}\nJDK: {detected_java or 'Chưa tìm thấy'}\nADB: {detected_adb or 'Chưa tìm thấy'}")

    # ========================================================================
    # Định kỳ giám sát trạng thái cổng & tiến trình (Health Check)
    # ========================================================================
    def _periodic_health_check(self):
        try:
            val = self.cdn_port_entry.get().strip()
            cdn_port = int(val) if val else int(self.cfg.get("cdn_port", 443))
        except ValueError:
            cdn_port = 443

        try:
            val = self.game_port_entry.get().strip()
            game_port = int(val) if val else int(self.cfg.get("game_port", 6613))
        except ValueError:
            game_port = 6613

        try:
            val = self.db_port_var.get().strip()
            mysql_port = int(val) if val else int(self.cfg.get("mysql_port", 3307))
        except ValueError:
            mysql_port = 3307

        # Check in a separate thread so UI does not stutter
        def check():
            cdn_ok = check_port_listening(cdn_port) or check_port_listening(443) or check_port_listening(8080)
            game_ok = check_port_listening(game_port) or check_port_listening(6613)
            mysql_ok = check_port_listening(mysql_port) or check_port_listening(3307) or check_port_listening(3306)
            self.after(0, lambda: self._update_status_ui(cdn_ok, game_ok, mysql_ok))

        threading.Thread(target=check, daemon=True).start()
        self.after(2000, self._periodic_health_check)

    def _update_status_ui(self, cdn_ok: bool, game_ok: bool, mysql_ok: bool):
        # CDN
        display_cdn_port = self.cdn_port_entry.get().strip() or str(self.cfg.get("cdn_port", 443))
        if cdn_ok or self.cdn_proc.is_running:
            self.cdn_status_lbl.config(text=f"CDN: 🟢 Online ({display_cdn_port})", fg="#107C41")
            self.cdn_card_status.config(text=f"Trạng thái: 🟢 Đang chạy (Port {display_cdn_port})", fg="#107C41")
            self.cdn_start_btn.config(state="disabled")
        else:
            self.cdn_status_lbl.config(text=f"CDN: 🔴 Offline ({display_cdn_port})", fg="#D13438")
            self.cdn_card_status.config(text="Trạng thái: 🔴 Chưa chạy", fg="#D13438")
            self.cdn_start_btn.config(state="normal")
        # Luôn cho phép nhấn nút Dừng để diệt tiến trình bị kẹt / zombie
        self.cdn_stop_btn.config(state="normal")

        # Game Server
        display_game_port = self.game_port_entry.get().strip() or str(self.cfg.get("game_port", 6613))
        if game_ok:
            self.game_status_lbl.config(text=f"Game: 🟢 Online ({display_game_port})", fg="#107C41")
            self.game_card_status.config(text=f"Trạng thái: 🟢 Đang chạy (Port {display_game_port})", fg="#107C41")
            self.game_start_btn.config(state="disabled")
        else:
            if self.game_proc.is_running:
                self.game_status_lbl.config(text="Game: 🟡 Đang khởi động (Build)", fg="#FF8C00")
                self.game_card_status.config(text="Trạng thái: 🟡 Đang khởi động (Gradle build...)", fg="#FF8C00")
                self.game_start_btn.config(state="disabled")
            else:
                self.game_status_lbl.config(text=f"Game: 🔴 Offline ({display_game_port})", fg="#D13438")
                self.game_card_status.config(text="Trạng thái: 🔴 Chưa chạy", fg="#D13438")
                self.game_start_btn.config(state="normal")
        # Luôn cho phép nhấn nút Dừng để giải phóng port bị kẹt
        self.game_stop_btn.config(state="normal")

        # MySQL
        display_mysql_port = self.db_port_var.get().strip() or str(self.cfg.get("mysql_port", 3307))
        if mysql_ok:
            self.mysql_status_lbl.config(text=f"MySQL: 🟢 Online ({display_mysql_port})", fg="#107C41")
        else:
            self.mysql_status_lbl.config(text=f"MySQL: 🔴 Offline ({display_mysql_port})", fg="#D13438")

        # ADB
        if self.adb_proc.is_running:
            self.adb_card_status.config(text="Trạng thái: 🟢 Đang giám sát chuyển tiếp cổng", fg="#107C41")
            self.adb_start_btn.config(state="disabled")
        else:
            self.adb_card_status.config(text="Trạng thái: 🔴 Chưa kích hoạt", fg="#D13438")
            self.adb_start_btn.config(state="normal")
        self.adb_stop_btn.config(state="normal")

    def on_close(self):
        # Tắt sạch server khi đóng ứng dụng
        has_running = (
            self.cdn_proc.is_running or self.game_proc.is_running or self.adb_proc.is_running
            or check_port_listening(6613) or check_port_listening(443)
        )
        if has_running:
            if messagebox.askyesno("Xác nhận", "Các máy chủ vẫn đang chạy. Bạn có muốn tắt tất cả máy chủ trước khi thoát?"):
                self.stop_all_servers()
        self.destroy()


if __name__ == "__main__":
    app = TSMControlPanel()
    app.protocol("WM_DELETE_WINDOW", app.on_close)
    app.mainloop()
