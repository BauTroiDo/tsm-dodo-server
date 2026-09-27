#!/usr/bin/env python3
"""
tsm_admin.py — Công cụ Quản trị Server TSM_DODO (Tiếng Việt)
=============================================================================
Tính năng:
  1. Tổng quan Server: trạng thái CDN, Game Server, MySQL, JDK, dung lượng
  2. Quản lý Database: xem/sửa bảng, chạy SQL, xem người chơi, chỉnh dữ liệu
  3. Cấu hình Server: chỉnh application.conf, NPC drops, game config
  4. Dò lỗi Server: quét log tự động, phát hiện lỗi, cảnh báo
  5. Quản lý người chơi: xem/kick/cấp quyền GM online
  6. Sao lưu/Khôi phục: backup database & config
  7. CompreseData: đọc/chỉnh sửa/giải nén/nén file LZ4 (.emg, .mmg, .dat)
     + TextData_C.dat: sửa text UI/nhiệm vụ/event (thư mục Data/)

Cách chạy:  python tsm_admin.py
            (hoặc double-click tsm_admin.bat)
=============================================================================
"""

import json
import os
import queue
import re
import shutil
import socket
import subprocess
import sys
import threading
import time
import tkinter as tk
from datetime import datetime
from pathlib import Path
from tkinter import ttk, filedialog, messagebox, simpledialog

# ============================================================================
# Đảm bảo sys.path có thư mục gốc project (để các module local như
# lz4_packed.py, dat_packer.py, lua_crypt.py import được từ bất kỳ thư mục nào)
# ============================================================================
_PROJECT_ROOT = Path(__file__).resolve().parent
if str(_PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(_PROJECT_ROOT))

# ============================================================================
# Import module LZ4 ngay từ đầu để tránh lỗi "No module named 'lz4'"
# khi gọi trong threading worker. Nếu thiếu lz4, hiển thị thông báo ngay.
# ============================================================================
try:
    import lz4.block  # noqa: F401  — kiểm tra module C extension
    import lz4_packed  # module local trong project root
except ImportError as _lz4_err:
    print("=" * 60)
    print("[LỖI] Không tìm thấy module LZ4!")
    print("       Cài đặt bằng:  pip install lz4")
    print(f"       Chi tiết: {_lz4_err}")
    print("=" * 60)
    lz4_packed = None  # sẽ được kiểm tra khi dùng

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
APP_CONF = ROOT_DIR / "app" / "src" / "main" / "resources" / "application.conf"
LOGS_DIR = ROOT_DIR / "logs"
APP_LOGS_DIR = ROOT_DIR / "app" / "logs"
SQL_DIR = ROOT_DIR / "SQL"
NPC_DROPS_FILE = ROOT_DIR / "gamedata" / "npc_drops.json"
COMPRESSE_DATA_DIR = ROOT_DIR / "cdn" / "mirror" / "tsr" / "2.4.76" / "ResourcesPacked" / "CompreseData"
DATA_DIR = ROOT_DIR / "cdn" / "mirror" / "tsr" / "2.4.76" / "ResourcesPacked" / "Data"

# Các pattern lỗi thường gặp trong log
ERROR_PATTERNS = [
    (r"Exception|Error|FATAL|CRITICAL", "Lỗi nghiêm trọng"),
    (r"OutOfMemoryError", "Hết bộ nhớ"),
    (r"StackOverflowError", "Tràn stack"),
    (r"NullPointerException", "Null pointer"),
    (r"SQLException|CommunicationsException|Cannot connect", "Lỗi database"),
    (r"Connection refused|Connection timed out", "Kết nối bị từ chối"),
    (r"BindException|Address already in use", "Port bị chiếm"),
    (r"FileNotFoundException|NoSuchFileException", "Không tìm thấy file"),
    (r"NumberFormatException|IllegalArgumentException", "Tham số sai"),
    (r"Deadlock|deadlock", "Deadlock"),
    (r"OutOfDiskSpace|No space left on device", "Hết dung lượng ổ đĩa"),
    (r"ClassNotFoundException|NoClassDefFoundError", "Thiếu class/library"),
    (r"AccessDeniedException|Permission denied", "Không đủ quyền"),
]

WARNING_PATTERNS = [
    (r"WARN|WARNING", "Cảnh báo"),
    (r"Deprecated", "Phân cảnh báo bỏ qua"),
    (r"Slow query|slow query", "Query chậm"),
    (r"Timeout|timed out", "Timeout"),
    (r"Retry|retry", "Thử lại"),
]


# ============================================================================
# Hàm tiện ích
# ============================================================================
def get_local_lan_ip() -> str:
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            s.connect(("8.8.8.8", 80))
            return s.getsockname()[0]
    except Exception:
        return "127.0.0.1"


def check_port_listening(port: int, host: str = "127.0.0.1", timeout: float = 0.5) -> bool:
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.settimeout(timeout)
            return s.connect_ex((host, port)) == 0
    except Exception:
        return False


def human_size(n: int) -> str:
    if n < 1024:
        return f"{n} B"
    elif n < 1024 * 1024:
        return f"{n / 1024:.1f} KB"
    elif n < 1024 * 1024 * 1024:
        return f"{n / 1024 / 1024:.1f} MB"
    else:
        return f"{n / 1024 / 1024 / 1024:.2f} GB"


def dir_size(path: Path) -> int:
    total = 0
    if path.is_dir():
        for entry in path.rglob("*"):
            if entry.is_file():
                try:
                    total += entry.stat().st_size
                except Exception:
                    pass
    return total


def load_config() -> dict:
    if CONFIG_FILE.is_file():
        try:
            return json.loads(CONFIG_FILE.read_text(encoding="utf-8"))
        except Exception:
            pass
    return {}


def save_config(cfg: dict):
    CONFIG_FILE.write_text(json.dumps(cfg, indent=2, ensure_ascii=False), encoding="utf-8")


def find_mysql_client() -> str:
    """Tìm mysql.exe client."""
    # Thử PATH
    w = shutil.which("mysql")
    if w:
        return w
    # Thử XAMPP
    for x in ["C:\\xampp", "D:\\xampp", "E:\\xampp"]:
        p = os.path.join(x, "mysql", "bin", "mysql.exe")
        if os.path.isfile(p):
            return p
    # Thử từ config
    cfg = load_config()
    mysql_info = cfg.get("mysql_info", "")
    if mysql_info.startswith("xampp:"):
        return os.path.join(mysql_info.split(":", 1)[1], "mysql", "bin", "mysql.exe")
    elif mysql_info.startswith("mysql:"):
        return os.path.join(mysql_info.split(":", 1)[1], "bin", "mysql.exe")
    return None


def get_mysql_password() -> str:
    cfg = load_config()
    return cfg.get("mysql_password", "Jzuqmy8u@123")


def run_mysql_query(sql: str, database: str = "tsm_dodo", timeout: int = 30) -> tuple:
    """Chạy SQL query qua mysql client, trả về (stdout, stderr, returncode)."""
    mysql_exe = find_mysql_client()
    if not mysql_exe:
        return "", "Không tìm thấy mysql.exe! Cài XAMPP hoặc thêm vào PATH.", -1

    pwd = get_mysql_password()
    args = [mysql_exe, "-u", "root"]
    if pwd:
        args.append(f"-p{pwd}")
    args.extend(["-e", sql])
    if database:
        args.append(database)

    try:
        result = subprocess.run(
            args, capture_output=True, text=True, timeout=timeout,
            encoding="utf-8", errors="replace"
        )
        return result.stdout, result.stderr, result.returncode
    except subprocess.TimeoutExpired:
        return "", f"Query timeout sau {timeout}s", -1
    except Exception as e:
        return "", str(e), -1


def run_mysql_multi(sql: str, database: str = "tsm_dodo", timeout: int = 120) -> tuple:
    """Chạy nhiều SQL statement qua stdin."""
    mysql_exe = find_mysql_client()
    if not mysql_exe:
        return "", "Không tìm thấy mysql.exe!", -1

    pwd = get_mysql_password()
    args = [mysql_exe, "-u", "root"]
    if pwd:
        args.append(f"-p{pwd}")
    if database:
        args.append(database)

    try:
        result = subprocess.run(
            args, input=sql, capture_output=True, text=True, timeout=timeout,
            encoding="utf-8", errors="replace"
        )
        return result.stdout, result.stderr, result.returncode
    except subprocess.TimeoutExpired:
        return "", f"Timeout sau {timeout}s", -1
    except Exception as e:
        return "", str(e), -1


def parse_mysql_table(text: str) -> list:
    """Parse output dạng bảng của mysql -e thành list of dicts."""
    lines = text.strip().splitlines()
    if len(lines) < 2:
        return []
    # Dòng đầu là header, phân cách bằng tab
    headers = [h.strip() for h in lines[0].split("\t")]
    rows = []
    for line in lines[1:]:
        cols = [c.strip() for c in line.split("\t")]
        if len(cols) == len(headers):
            rows.append(dict(zip(headers, cols)))
    return rows


# ============================================================================
# Lớp Quản trị Server (UI chính)
# ============================================================================
class TSMAdminApp(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("TSM_DODO — Công cụ Quản trị Server")
        self.geometry("1100x780")
        self.minsize(900, 660)

        self.cfg = load_config()
        self.lan_ip = get_local_lan_ip()

        # Hàng đợi log
        self.log_queue = queue.Queue()

        # Trạng thái
        self.server_running = False
        self._log_monitor_thread = None
        self._log_monitor_stop = threading.Event()

        # Kiểu dáng
        self._init_styles()
        self._build_header()
        self._build_status_bar()
        self._build_tabs()
        self._build_log_panel()

        # Polling
        self.after(300, self._process_log_queue)
        self.after(1000, self._periodic_check)

    # ========================================================================
    # STYLE
    # ========================================================================
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
        self.font_small = ("Segoe UI", 9)

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
        style.configure("Treeview", font=self.font_main, rowheight=24)
        style.configure("Treeview.Heading", font=self.font_bold)

    # ========================================================================
    # XÂY DỰNG GIAO DIỆN
    # ========================================================================
    def _build_header(self):
        header = tk.Frame(self, bg="#6C3D9A", height=56)
        header.pack(fill="x", side="top")

        left_f = tk.Frame(header, bg="#6C3D9A")
        left_f.pack(side="left", padx=16, pady=8)

        tk.Label(left_f, text="TSM_DODO — Quản trị Server",
                 font=("Segoe UI", 14, "bold"), fg="white", bg="#6C3D9A").pack(anchor="w")
        tk.Label(left_f, text=f"Thư mục: {ROOT_DIR}  |  IP LAN: {self.lan_ip}",
                 font=("Segoe UI", 9), fg="#E0D0F0", bg="#6C3D9A").pack(anchor="w")

    def _build_status_bar(self):
        bar = tk.Frame(self, bg="#FFFFFF", height=44, relief="groove", bd=1)
        bar.pack(fill="x", padx=12, pady=(8, 0))

        st = tk.Frame(bar, bg="#FFFFFF")
        st.pack(side="left", padx=12, pady=8)

        self.lbl_cdn = tk.Label(st, text="CDN: ???", font=self.font_bold, bg="#FFFFFF", fg="#555")
        self.lbl_cdn.pack(side="left", padx=8)

        self.lbl_game = tk.Label(st, text="Game: ???", font=self.font_bold, bg="#FFFFFF", fg="#555")
        self.lbl_game.pack(side="left", padx=8)

        self.lbl_mysql = tk.Label(st, text="MySQL: ???", font=self.font_bold, bg="#FFFFFF", fg="#555")
        self.lbl_mysql.pack(side="left", padx=8)

        self.lbl_disk = tk.Label(st, text="Disk: ???", font=self.font_bold, bg="#FFFFFF", fg="#555")
        self.lbl_disk.pack(side="left", padx=8)

    def _build_tabs(self):
        self.notebook = ttk.Notebook(self)
        self.notebook.pack(fill="both", expand=True, padx=12, pady=8)

        # Tab 0: Tổng quan
        self.tab_overview = ttk.Frame(self.notebook, padding=12)
        self.notebook.add(self.tab_overview, text="[1] Tổng quan")
        self._init_tab_overview()

        # Tab 1: Database
        self.tab_db = ttk.Frame(self.notebook, padding=12)
        self.notebook.add(self.tab_db, text="[2] Database")
        self._init_tab_db()

        # Tab 2: Cấu hình
        self.tab_config = ttk.Frame(self.notebook, padding=12)
        self.notebook.add(self.tab_config, text="[3] Cấu hình")
        self._init_tab_config()

        # Tab 3: Dò lỗi
        self.tab_errors = ttk.Frame(self.notebook, padding=12)
        self.notebook.add(self.tab_errors, text="[4] Dò lỗi")
        self._init_tab_errors()

        # Tab 4: Người chơi
        self.tab_players = ttk.Frame(self.notebook, padding=12)
        self.notebook.add(self.tab_players, text="[5] Người chơi")
        self._init_tab_players()

        # Tab 5: Sao lưu
        self.tab_backup = ttk.Frame(self.notebook, padding=12)
        self.notebook.add(self.tab_backup, text="[6] Sao lưu")
        self._init_tab_backup()

        # Tab 6: CompreseData
        self.tab_compdata = ttk.Frame(self.notebook, padding=12)
        self.notebook.add(self.tab_compdata, text="[7] CompreseData")
        self._init_tab_compdata()

    def _build_log_panel(self):
        lf = tk.LabelFrame(self, text="Nhật ký hệ thống", font=self.font_bold)
        lf.pack(fill="both", expand=False, side="bottom", padx=12, pady=(0, 8))

        tc = tk.Frame(lf)
        tc.pack(fill="x", padx=6, pady=2)

        tk.Label(tc, text="Lọc:").pack(side="left")
        self.log_filter_var = tk.StringVar(value="Tất cả")
        ttk.Combobox(tc, textvariable=self.log_filter_var,
                     values=["Tất cả", "Database", "Cấu hình", "Lỗi", "Người chơi", "Hệ thống"],
                     state="readonly", width=14).pack(side="left", padx=6)
        ttk.Button(tc, text="Xóa", command=self._clear_logs).pack(side="right", padx=4)

        tw = tk.Frame(lf)
        tw.pack(fill="both", expand=True, padx=6, pady=(0, 6))

        self.log_text = tk.Text(tw, height=6, font=self.font_code, wrap="word", bg="#1E1E1E", fg="#D4D4D4")
        self.log_text.pack(side="left", fill="both", expand=True)

        sc = ttk.Scrollbar(tw, orient="vertical", command=self.log_text.yview)
        sc.pack(side="right", fill="y")
        self.log_text.config(yscrollcommand=sc.set)

    # ========================================================================
    # TAB: TỔNG QUAN
    # ========================================================================
    def _init_tab_overview(self):
        f = tk.Frame(self.tab_overview)
        f.pack(fill="both", expand=True)

        # Trạng thái server
        card = tk.LabelFrame(f, text="Trạng thái Server", font=self.font_bold, padx=10, pady=10)
        card.pack(fill="x", pady=6)

        self.ov_cdn = tk.Label(card, text="CDN Server: Đang kiểm...", font=self.font_main, fg="#555")
        self.ov_cdn.pack(anchor="w", pady=2)

        self.ov_game = tk.Label(card, text="Game Server: Đang kiểm...", font=self.font_main, fg="#555")
        self.ov_game.pack(anchor="w", pady=2)

        self.ov_mysql = tk.Label(card, text="MySQL: Đang kiểm...", font=self.font_main, fg="#555")
        self.ov_mysql.pack(anchor="w", pady=2)

        self.ov_jdk = tk.Label(card, text="JDK: Đang kiểm...", font=self.font_main, fg="#555")
        self.ov_jdk.pack(anchor="w", pady=2)

        # Thông tin game
        info_card = tk.LabelFrame(f, text="Thông tin Game", font=self.font_bold, padx=10, pady=10)
        info_card.pack(fill="x", pady=6)

        self.ov_db_info = tk.Label(info_card, text="Database: Đang tải...", font=self.font_main, fg="#555")
        self.ov_db_info.pack(anchor="w", pady=2)

        self.ov_player_count = tk.Label(info_card, text="Người chơi online: ???", font=self.font_main, fg="#555")
        self.ov_player_count.pack(anchor="w", pady=2)

        self.ov_account_count = tk.Label(info_card, text="Tổng tài khoản: ???", font=self.font_main, fg="#555")
        self.ov_account_count.pack(anchor="w", pady=2)

        # Dung lượng
        disk_card = tk.LabelFrame(f, text="Dung lượng", font=self.font_bold, padx=10, pady=10)
        disk_card.pack(fill="x", pady=6)

        self.ov_disk_cdn = tk.Label(disk_card, text="cdn/: Đang tính...", font=self.font_main, fg="#555")
        self.ov_disk_cdn.pack(anchor="w", pady=2)

        self.ov_disk_logs = tk.Label(disk_card, text="logs/: Đang tính...", font=self.font_main, fg="#555")
        self.ov_disk_logs.pack(anchor="w", pady=2)

        # Nút làm mới
        btn_f = tk.Frame(f)
        btn_f.pack(fill="x", pady=8)
        ttk.Button(btn_f, text="Làm mới tổng quan", style="Primary.TButton",
                   command=self._refresh_overview).pack(side="left", padx=4)

    def _refresh_overview(self):
        """Làm mới toàn bộ thông tin tổng quan."""
        self.append_log("Hệ thống", "Đang làm mới tổng quan...")

        def worker():
            # CDN
            cdn_port = int(self.cfg.get("cdn_port", 443))
            cdn_ok = check_port_listening(cdn_port)
            self.after(0, lambda: self.ov_cdn.config(
                text=f"CDN Server (port {cdn_port}): {'Đang chạy' if cdn_ok else 'Tắt'}",
                fg="#107C41" if cdn_ok else "#D13438"
            ))
            self.after(0, lambda: self.lbl_cdn.config(
                text=f"CDN: {'ON' if cdn_ok else 'OFF'}",
                fg="#107C41" if cdn_ok else "#D13438"
            ))

            # Game
            game_port = int(self.cfg.get("game_port", 6613))
            game_ok = check_port_listening(game_port)
            self.after(0, lambda: self.ov_game.config(
                text=f"Game Server (port {game_port}): {'Đang chạy' if game_ok else 'Tắt'}",
                fg="#107C41" if game_ok else "#D13438"
            ))
            self.after(0, lambda: self.lbl_game.config(
                text=f"Game: {'ON' if game_ok else 'OFF'}",
                fg="#107C41" if game_ok else "#D13438"
            ))

            # MySQL
            mysql_ok = check_port_listening(3307)
            self.after(0, lambda: self.ov_mysql.config(
                text=f"MySQL (port 3307): {'Đang chạy' if mysql_ok else 'Tắt'}",
                fg="#107C41" if mysql_ok else "#D13438"
            ))
            self.after(0, lambda: self.lbl_mysql.config(
                text=f"MySQL: {'ON' if mysql_ok else 'OFF'}",
                fg="#107C41" if mysql_ok else "#D13438"
            ))

            # JDK
            jh = self.cfg.get("java_home", "")
            java_exe = os.path.join(jh, "bin", "java.exe") if jh else ""
            if java_exe and os.path.isfile(java_exe):
                self.after(0, lambda: self.ov_jdk.config(text=f"JDK: {jh}", fg="#107C41"))
            else:
                self.after(0, lambda: self.ov_jdk.config(text="JDK: Không tìm thấy!", fg="#D13438"))

            # Database info
            if mysql_ok:
                out, err, rc = run_mysql_query("SHOW TABLES", "tsm_dodo")
                if rc == 0 and out.strip():
                    tables = [l.strip() for l in out.strip().splitlines() if l.strip()]
                    self.after(0, lambda: self.ov_db_info.config(
                        text=f"Database: tsm_dodo ({len(tables)} bảng)", fg="#107C41"
                    ))
                    self.after(0, lambda: self.ov_db_tables_ref.delete(*self.ov_db_tables_ref.get_children())
                               if hasattr(self, 'ov_db_tables_ref') else None)

                # Player count
                out2, _, _ = run_mysql_query("SELECT COUNT(*) AS cnt FROM accounts", "tsm_dodo")
                rows = parse_mysql_table(out2)
                if rows:
                    cnt = rows[0].get("cnt", "?")
                    self.after(0, lambda: self.ov_account_count.config(text=f"Tổng tài khoản: {cnt}"))

                out3, _, _ = run_mysql_query("SELECT COUNT(*) AS cnt FROM characters", "tsm_dodo")
                rows3 = parse_mysql_table(out3)
                if rows3:
                    cnt = rows3[0].get("cnt", "?")
                    self.after(0, lambda: self.ov_player_count.config(text=f"Tổng nhân vật: {cnt}"))
            else:
                self.after(0, lambda: self.ov_db_info.config(text="Database: MySQL không chạy", fg="#D13438"))

            # Disk
            cdn_size = dir_size(ROOT_DIR / "cdn")
            self.after(0, lambda: self.ov_disk_cdn.config(text=f"cdn/: {human_size(cdn_size)}"))

            log_size = dir_size(LOGS_DIR) + dir_size(APP_LOGS_DIR)
            self.after(0, lambda: self.ov_disk_logs.config(text=f"logs/: {human_size(log_size)}"))
            self.after(0, lambda: self.lbl_disk.config(text=f"Logs: {human_size(log_size)}"))

            self.after(0, lambda: self.append_log("Hệ thống", "Làm mới tổng quan xong!"))

        threading.Thread(target=worker, daemon=True).start()

    # ========================================================================
    # TAB: DATABASE
    # ========================================================================
    def _init_tab_db(self):
        f = tk.Frame(self.tab_db)
        f.pack(fill="both", expand=True)

        # Danh sách bảng
        tables_card = tk.LabelFrame(f, text="Danh sách bảng", font=self.font_bold, padx=10, pady=10)
        tables_card.pack(fill="x", pady=6)

        tc = tk.Frame(tables_card)
        tc.pack(fill="x")
        ttk.Button(tc, text="Nạp danh sách bảng", style="Primary.TButton",
                   command=self._load_tables).pack(side="left", padx=4)
        self.lbl_table_count = tk.Label(tc, text="(Chưa nạp)", font=self.font_small, fg="#666")
        self.lbl_table_count.pack(side="left", padx=10)

        # Treeview danh sách bảng
        tree_f = tk.Frame(tables_card)
        tree_f.pack(fill="both", expand=True, pady=(6, 0))

        self.db_tree = ttk.Treeview(tree_f, columns=("name", "rows"), show="headings", height=8)
        self.db_tree.heading("name", text="Tên bảng")
        self.db_tree.heading("rows", text="Số dòng")
        self.db_tree.column("name", width=250)
        self.db_tree.column("rows", width=100, anchor="e")
        self.db_tree.pack(side="left", fill="both", expand=True)
        ttk.Scrollbar(tree_f, orient="vertical", command=self.db_tree.yview).pack(side="right", fill="y")
        self.db_tree.configure(yscrollcommand=lambda *a: None)
        self.db_tree.bind("<Double-1>", self._on_table_double_click)

        # Trình chạy SQL
        sql_card = tk.LabelFrame(f, text="Chạy SQL", font=self.font_bold, padx=10, pady=10)
        sql_card.pack(fill="both", expand=True, pady=6)

        tk.Label(sql_card, text="Nhập câu lệnh SQL:", font=self.font_small).pack(anchor="w")

        sql_input_f = tk.Frame(sql_card)
        sql_input_f.pack(fill="both", expand=True, pady=4)

        self.sql_text = tk.Text(sql_input_f, height=5, font=self.font_code, wrap="word",
                                bg="#1E1E1E", fg="#D4D4D4", insertbackground="white")
        self.sql_text.pack(side="left", fill="both", expand=True)
        ttk.Scrollbar(sql_input_f, orient="vertical",
                      command=self.sql_text.yview).pack(side="right", fill="y")

        btn_f = tk.Frame(sql_card)
        btn_f.pack(fill="x", pady=4)
        ttk.Button(btn_f, text="Chạy SQL", style="Success.TButton",
                   command=self._run_sql).pack(side="left", padx=4)
        ttk.Button(btn_f, text="Xóa", command=lambda: self.sql_text.delete("1.0", "end")).pack(side="left", padx=4)
        ttk.Button(btn_f, text="Mở file SQL", command=self._open_sql_file).pack(side="left", padx=4)

        # Kết quả
        tk.Label(sql_card, text="Kết quả:", font=self.font_small).pack(anchor="w")
        self.sql_result = tk.Text(sql_card, height=6, font=self.font_code, wrap="word",
                                  bg="#1A1A2E", fg="#90EE90", state="disabled")
        self.sql_result.pack(fill="both", expand=True, pady=4)

    def _load_tables(self):
        self.append_log("Database", "Đang nạp danh sách bảng...")
        self.lbl_table_count.config(text="Đang nạp...")

        def worker():
            out, err, rc = run_mysql_query("SHOW TABLES", "tsm_dodo")
            if rc != 0:
                self.after(0, lambda: self.lbl_table_count.config(text=f"Lỗi: {err[:100]}"))
                self.after(0, lambda: self.append_log("Database", f"Lỗi nạp bảng: {err}"))
                return

            tables = [l.strip() for l in out.strip().splitlines() if l.strip()]
            self.after(0, lambda: self.db_tree.delete(*self.db_tree.get_children()))

            for table in tables:
                out2, _, rc2 = run_mysql_query(f"SELECT COUNT(*) AS cnt FROM `{table}`", "tsm_dodo")
                rows = parse_mysql_table(out2)
                cnt = rows[0].get("cnt", "?") if rows else "?"
                self.after(0, lambda t=table, c=cnt: self.db_tree.insert("", "end", values=(t, c)))

            self.after(0, lambda: self.lbl_table_count.config(text=f"{len(tables)} bảng"))
            self.after(0, lambda: self.append_log("Database", f"Đã nạp {len(tables)} bảng."))

        threading.Thread(target=worker, daemon=True).start()

    def _on_table_double_click(self, event):
        sel = self.db_tree.selection()
        if not sel:
            return
        values = self.db_tree.item(sel[0], "values")
        if not values:
            return
        table_name = values[0]
        self.sql_text.delete("1.0", "end")
        self.sql_text.insert("1.0", f"SELECT * FROM `{table_name}` LIMIT 100;")
        self._run_sql()

    def _run_sql(self):
        sql = self.sql_text.get("1.0", "end-1c").strip()
        if not sql:
            return

        self.append_log("Database", f"Chạy SQL: {sql[:80]}...")

        def worker():
            # Nếu là SELECT, dùng -e; nếu là multi-statement, dùng stdin
            if sql.lower().startswith("select") or sql.lower().startswith("show") or sql.lower().startswith("describe"):
                out, err, rc = run_mysql_query(sql, "tsm_dodo")
            else:
                out, err, rc = run_mysql_multi(sql, "tsm_dodo")

            self.after(0, lambda: self._show_sql_result(out, err, rc))

        threading.Thread(target=worker, daemon=True).start()

    def _show_sql_result(self, out, err, rc):
        self.sql_result.config(state="normal")
        self.sql_result.delete("1.0", "end")
        if rc == 0:
            if out.strip():
                self.sql_result.insert("end", out)
            else:
                self.sql_result.insert("end", "(Thành công — không có dữ liệu trả về)")
        else:
            self.sql_result.insert("end", f"LỖI (code={rc}):\n{err}")
        self.sql_result.config(state="disabled")

    def _open_sql_file(self):
        fp = filedialog.askopenfilename(
            title="Chọn file SQL",
            filetypes=[("SQL", "*.sql"), ("Tất cả", "*.*")],
            initialdir=str(SQL_DIR)
        )
        if fp:
            try:
                content = Path(fp).read_text(encoding="utf-8", errors="replace")
                self.sql_text.delete("1.0", "end")
                self.sql_text.insert("1.0", content)
                self.append_log("Database", f"Đã mở file: {fp}")
            except Exception as e:
                messagebox.showerror("Lỗi", str(e))

    # ========================================================================
    # TAB: CẤU HÌNH
    # ========================================================================
    def _init_tab_config(self):
        f = tk.Frame(self.tab_config)
        f.pack(fill="both", expand=True)

        # Cấu hình application.conf
        conf_card = tk.LabelFrame(f, text="Cấu hình Server (application.conf)", font=self.font_bold, padx=10, pady=10)
        conf_card.pack(fill="both", expand=True, pady=6)

        btn_f = tk.Frame(conf_card)
        btn_f.pack(fill="x", pady=4)
        ttk.Button(btn_f, text="Nạp cấu hình", style="Primary.TButton",
                   command=self._load_app_conf).pack(side="left", padx=4)
        ttk.Button(btn_f, text="Lưu cấu hình", style="Success.TButton",
                   command=self._save_app_conf).pack(side="left", padx=4)
        ttk.Button(btn_f, text="Khôi phục mặc định", style="Warning.TButton",
                   command=self._reset_app_conf).pack(side="left", padx=4)

        tk.Label(conf_card, text="Nội dung file application.conf:", font=self.font_small).pack(anchor="w", pady=(4, 2))

        conf_input_f = tk.Frame(conf_card)
        conf_input_f.pack(fill="both", expand=True)

        self.conf_text = tk.Text(conf_input_f, height=12, font=self.font_code, wrap="none",
                                 bg="#1E1E1E", fg="#D4D4D4", insertbackground="white")
        self.conf_text.pack(side="left", fill="both", expand=True)
        ttk.Scrollbar(conf_input_f, orient="vertical",
                      command=self.conf_text.yview).pack(side="right", fill="y")

        # NPC Drops
        drops_card = tk.LabelFrame(f, text="Cấu hình rơi vật phẩm NPC (npc_drops.json)", font=self.font_bold, padx=10, pady=10)
        drops_card.pack(fill="x", pady=6)

        tk.Label(drops_card, text="Chỉnh sửa file npc_drops.json — thêm/bớt/sửa tỉ lệ rơi đồ của NPC.",
                 font=self.font_small, fg="#005A9E").pack(anchor="w")

        btn_f2 = tk.Frame(drops_card)
        btn_f2.pack(fill="x", pady=4)
        ttk.Button(btn_f2, text="Nạp npc_drops.json", style="Primary.TButton",
                   command=self._load_npc_drops).pack(side="left", padx=4)
        ttk.Button(btn_f2, text="Lưu npc_drops.json", style="Success.TButton",
                   command=self._save_npc_drops).pack(side="left", padx=4)
        ttk.Button(btn_f2, text="Thêm NPC mới", command=self._add_npc_drop).pack(side="left", padx=4)
        ttk.Button(btn_f2, text="Xóa NPC", style="Danger.TButton",
                   command=self._del_npc_drop).pack(side="left", padx=4)

        # Treeview NPC drops
        drops_tree_f = tk.Frame(drops_card)
        drops_tree_f.pack(fill="both", expand=True, pady=4)

        self.drops_tree = ttk.Treeview(drops_tree_f, columns=("npc_id", "items", "total_rate"), show="headings", height=6)
        self.drops_tree.heading("npc_id", text="NPC ID")
        self.drops_tree.heading("items", text="Số vật phẩm")
        self.drops_tree.heading("total_rate", text="Tổng tỉ lệ (%)")
        self.drops_tree.column("npc_id", width=120)
        self.drops_tree.column("items", width=100, anchor="center")
        self.drops_tree.column("total_rate", width=120, anchor="e")
        self.drops_tree.pack(side="left", fill="both", expand=True)
        ttk.Scrollbar(drops_tree_f, orient="vertical",
                      command=self.drops_tree.yview).pack(side="right", fill="y")
        self.drops_tree.bind("<Double-1>", self._on_drop_double_click)

        # Thông tin chi tiết
        self.drop_detail = tk.Label(drops_card, text="(Chọn NPC để xem chi tiết)", font=self.font_small, fg="#666",
                                    justify="left")
        self.drop_detail.pack(anchor="w", pady=4)

    def _load_app_conf(self):
        if not APP_CONF.is_file():
            messagebox.showerror("Lỗi", f"Không tìm thấy file: {APP_CONF}")
            return
        try:
            content = APP_CONF.read_text(encoding="utf-8")
            self.conf_text.delete("1.0", "end")
            self.conf_text.insert("1.0", content)
            self.append_log("Cấu hình", f"Đã nạp application.conf ({len(content)} bytes)")
        except Exception as e:
            messagebox.showerror("Lỗi", str(e))

    def _save_app_conf(self):
        content = self.conf_text.get("1.0", "end-1c")
        try:
            # Backup
            if APP_CONF.is_file():
                bak = APP_CONF.with_suffix(".conf.bak")
                shutil.copy2(APP_CONF, bak)
            APP_CONF.write_text(content, encoding="utf-8")
            self.append_log("Cấu hình", "Đã lưu application.conf (backup tại .conf.bak)")
            messagebox.showinfo("Thành công", "Đã lưu cấu hình!\nCần khởi động lại Game Server để áp dụng.")
        except Exception as e:
            messagebox.showerror("Lỗi", str(e))

    def _reset_app_conf(self):
        if not messagebox.askyesno("Xác nhận", "Khôi phục cấu hình mặc định?\nFile hiện tại sẽ được backup."):
            return
        default_conf = '''server {
    port = 6613
    port = ${?SERVER_PORT}
    host = "0.0.0.0"
    host = ${?SERVER_HOST}
}

database {
    url = "jdbc:mysql://localhost:3306/tsm_dodo?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Taipei&characterEncoding=UTF-8"
    url = ${?DB_URL}
    driver = "com.mysql.cj.jdbc.Driver"
    user = "root"
    user = ${?DB_USER}
    password = ""
    password = ${?DB_PASSWORD}
    maximumPoolSize = 10
}

game {
    heartbeatInterval = 20
    clientVersion = 258
    gamedataPath = "cdn/mirror/tsr/2.4.76/ResourcesPacked"
    gamedataPath = ${?GAMEDATA_PATH}
    serverDataPath = "gamedata"
    serverDataPath = ${?SERVER_DATA_PATH}
}
'''
        try:
            if APP_CONF.is_file():
                shutil.copy2(APP_CONF, APP_CONF.with_suffix(".conf.bak"))
            APP_CONF.write_text(default_conf, encoding="utf-8")
            self.conf_text.delete("1.0", "end")
            self.conf_text.insert("1.0", default_conf)
            self.append_log("Cấu hình", "Đã khôi phục application.conf mặc định")
            messagebox.showinfo("Thành công", "Đã khôi phục cấu hình mặc định!")
        except Exception as e:
            messagebox.showerror("Lỗi", str(e))

    def _load_npc_drops(self):
        if not NPC_DROPS_FILE.is_file():
            messagebox.showerror("Lỗi", f"Không tìm thấy: {NPC_DROPS_FILE}")
            return
        try:
            data = json.loads(NPC_DROPS_FILE.read_text(encoding="utf-8"))
            self.drops_tree.delete(*self.drops_tree.get_children())
            for npc_id, drops in data.items():
                total_rate = sum(d.get("rate", 0) for d in drops)
                self.drops_tree.insert("", "end", values=(npc_id, len(drops), total_rate))
            self._npc_drops_data = data
            self.append_log("Cấu hình", f"Đã nạp npc_drops.json ({len(data)} NPC)")
        except Exception as e:
            messagebox.showerror("Lỗi", str(e))

    def _save_npc_drops(self):
        if not hasattr(self, '_npc_drops_data'):
            messagebox.showwarning("Cảnh báo", "Hãy nạp npc_drops.json trước!")
            return
        try:
            content = json.dumps(self._npc_drops_data, indent=2, ensure_ascii=False)
            # Backup
            if NPC_DROPS_FILE.is_file():
                shutil.copy2(NPC_DROPS_FILE, NPC_DROPS_FILE.with_suffix(".json.bak"))
            NPC_DROPS_FILE.write_text(content, encoding="utf-8")
            self.append_log("Cấu hình", "Đã lưu npc_drops.json")
            messagebox.showinfo("Thành công", "Đã lưu npc_drops.json!")
        except Exception as e:
            messagebox.showerror("Lỗi", str(e))

    def _on_drop_double_click(self, event):
        sel = self.drops_tree.selection()
        if not sel:
            return
        values = self.drops_tree.item(sel[0], "values")
        if not values:
            return
        npc_id = values[0]
        if not hasattr(self, '_npc_drops_data') or npc_id not in self._npc_drops_data:
            return

        drops = self._npc_drops_data[npc_id]
        lines = [f"NPC {npc_id} — {len(drops)} vật phẩm:"]
        for i, d in enumerate(drops):
            lines.append(f"  [{i}] itemId={d.get('itemId')}, tỉ lệ={d.get('rate')}%, số lượng={d.get('minQty')}-{d.get('maxQty')}")

        # Hỏi sửa
        edit = messagebox.askyesno("Chi tiết NPC", "\n".join(lines) + "\n\nCó muốn sửa không?")
        if edit:
            self._edit_npc_drop(npc_id)

    def _edit_npc_drop(self, npc_id: str):
        drops = self._npc_drops_data.get(npc_id, [])
        lines = ["Nhập dữ liệu mới cho từng dòng (mỗi dòng 1 vật phẩm):",
                 "Định dạng: itemId,rate,minQty,maxQty (cách nhau bởi phẩy)"]
        for d in drops:
            lines.append(f"{d.get('itemId')},{d.get('rate')},{d.get('minQty')},{d.get('maxQty')}")

        result = simpledialog.askstring("Sửa NPC drops", "\n".join(lines),
                                        initialvalue="\n".join(
                                            f"{d.get('itemId')},{d.get('rate')},{d.get('minQty')},{d.get('maxQty')}"
                                            for d in drops
                                        ))
        if result is None:
            return

        new_drops = []
        for line in result.strip().splitlines():
            parts = [p.strip() for p in line.split(",")]
            if len(parts) >= 4:
                try:
                    new_drops.append({
                        "itemId": int(parts[0]),
                        "rate": int(parts[1]),
                        "minQty": int(parts[2]),
                        "maxQty": int(parts[3])
                    })
                except ValueError:
                    pass

        self._npc_drops_data[npc_id] = new_drops
        total_rate = sum(d.get("rate", 0) for d in new_drops)
        # Update tree
        for item in self.drops_tree.get_children():
            if self.drops_tree.item(item, "values")[0] == npc_id:
                self.drops_tree.item(item, values=(npc_id, len(new_drops), total_rate))
                break
        self.append_log("Cấu hình", f"Đã sửa NPC {npc_id}: {len(new_drops)} vật phẩm")

    def _add_npc_drop(self):
        npc_id = simpledialog.askstring("Thêm NPC", "Nhập NPC ID:")
        if not npc_id:
            return
        if not hasattr(self, '_npc_drops_data'):
            self._npc_drops_data = {}
        if npc_id in self._npc_drops_data:
            messagebox.showwarning("Cảnh báo", f"NPC {npc_id} đã tồn tại!")
            return
        self._npc_drops_data[npc_id] = []
        self.drops_tree.insert("", "end", values=(npc_id, 0, 0))
        self.append_log("Cấu hình", f"Đã thêm NPC {npc_id}")
        self._edit_npc_drop(npc_id)

    def _del_npc_drop(self):
        sel = self.drops_tree.selection()
        if not sel:
            messagebox.showwarning("Cảnh báo", "Chọn NPC cần xóa!")
            return
        values = self.drops_tree.item(sel[0], "values")
        npc_id = values[0]
        if not messagebox.askyesno("Xác nhận", f"Xóa NPC {npc_id} khỏi danh sách drop?"):
            return
        if hasattr(self, '_npc_drops_data') and npc_id in self._npc_drops_data:
            del self._npc_drops_data[npc_id]
        self.drops_tree.delete(sel[0])
        self.append_log("Cấu hình", f"Đã xóa NPC {npc_id}")

    # ========================================================================
    # TAB: DÒ LỖI
    # ========================================================================
    def _init_tab_errors(self):
        f = tk.Frame(self.tab_errors)
        f.pack(fill="both", expand=True)

        # Nút dò lỗi
        scan_card = tk.LabelFrame(f, text="Dò lỗi Server", font=self.font_bold, padx=10, pady=10)
        scan_card.pack(fill="x", pady=6)

        tk.Label(scan_card,
                 text="Quét file log tự động phát hiện lỗi, cảnh báo, và vấn đề phổ biến.\n"
                      "Log nằm trong thư mục logs/ và app/logs/.",
                 font=self.font_small, fg="#005A9E", justify="left").pack(anchor="w", pady=(0, 8))

        btn_f = tk.Frame(scan_card)
        btn_f.pack(fill="x", pady=4)
        ttk.Button(btn_f, text="Dò lỗi ngay", style="Danger.TButton",
                   command=self._scan_errors).pack(side="left", padx=4)
        ttk.Button(btn_f, text="Dò lỗi gần đây (24h)", style="Primary.TButton",
                   command=lambda: self._scan_errors(recent_only=True)).pack(side="left", padx=4)
        ttk.Button(btn_f, text="Mở thư mục logs", command=self._open_logs_dir).pack(side="left", padx=4)
        ttk.Button(btn_f, text="Xóa kết quả", command=self._clear_errors).pack(side="left", padx=4)

        # Tự động giám sát
        self.var_auto_monitor = tk.BooleanVar(value=False)
        ttk.Checkbutton(scan_card, text="Tự động giám sát log (cập nhật mỗi 5 giây)",
                        variable=self.var_auto_monitor,
                        command=self._toggle_auto_monitor).pack(anchor="w", pady=4)

        # Bảng kết quả
        result_card = tk.LabelFrame(f, text="Kết quả dò lỗi", font=self.font_bold, padx=10, pady=10)
        result_card.pack(fill="both", expand=True, pady=6)

        rf = tk.Frame(result_card)
        rf.pack(fill="both", expand=True)

        self.err_tree = ttk.Treeview(rf, columns=("time", "level", "source", "desc", "file"), show="headings", height=12)
        self.err_tree.heading("time", text="Thời gian")
        self.err_tree.heading("level", text="Mức")
        self.err_tree.heading("source", text="Nguồn")
        self.err_tree.heading("desc", text="Mô tả")
        self.err_tree.heading("file", text="File")
        self.err_tree.column("time", width=140)
        self.err_tree.column("level", width=60)
        self.err_tree.column("source", width=80)
        self.err_tree.column("desc", width=350)
        self.err_tree.column("file", width=150)
        self.err_tree.pack(side="left", fill="both", expand=True)
        sc = ttk.Scrollbar(rf, orient="vertical", command=self.err_tree.yview)
        sc.pack(side="right", fill="y")
        self.err_tree.configure(yscrollcommand=sc.set)

        # Chi tiết
        tk.Label(result_card, text="Chi tiết lỗi:", font=self.font_small).pack(anchor="w", pady=(6, 2))
        self.err_detail = tk.Text(result_card, height=4, font=self.font_code, wrap="word",
                                   bg="#1E1E1E", fg="#FF9999", state="disabled")
        self.err_detail.pack(fill="x", pady=2)
        self.err_tree.bind("<<TreeviewSelect>>", self._on_error_select)

        # Thống kê
        self.err_stats = tk.Label(result_card, text="(Chưa dò)", font=self.font_bold, fg="#555")
        self.err_stats.pack(anchor="w", pady=4)

    def _scan_errors(self, recent_only=False):
        self.err_tree.delete(*self.err_tree.get_children())
        self.append_log("Lỗi", f"Đang dò lỗi {'(24h gần đây)' if recent_only else '(toàn bộ)'}...")

        def worker():
            errors = []
            warnings = []

            log_dirs = [LOGS_DIR, APP_LOGS_DIR]
            now = time.time()
            cutoff = now - 86400 if recent_only else 0

            for log_dir in log_dirs:
                if not log_dir.is_dir():
                    continue
                for log_file in log_dir.glob("*.log"):
                    try:
                        mtime = log_file.stat().st_mtime
                        if cutoff and mtime < cutoff:
                            continue
                        self._scan_log_file(log_file, errors, warnings)
                    except Exception as e:
                        self.append_log("Lỗi", f"Lỗi đọc {log_file.name}: {e}")

            # Hiển thị
            total = len(errors) + len(warnings)
            for err in errors:
                self.after(0, lambda e=err: self.err_tree.insert("", "end", values=e, tags=("error",)))
            for warn in warnings:
                self.after(0, lambda w=warn: self.err_tree.insert("", "end", values=w, tags=("warning",)))

            self.err_tree.tag_configure("error", background="#FFD0D0")
            self.err_tree.tag_configure("warning", background="#FFF0D0")

            stats_text = f"Tổng: {total} vấn đề — {len(errors)} lỗi, {len(warnings)} cảnh báo"
            self.after(0, lambda: self.err_stats.config(text=stats_text, fg="#D13438" if errors else "#FF8C00"))
            self.after(0, lambda: self.append_log("Lỗi", stats_text))

        threading.Thread(target=worker, daemon=True).start()

    def _scan_log_file(self, log_file: Path, errors: list, warnings: list):
        try:
            content = log_file.read_text(encoding="utf-8", errors="replace")
        except Exception:
            return

        lines = content.splitlines()
        for i, line in enumerate(lines):
            # Bỏ qua dòng trống
            if not line.strip():
                continue

            # Kiểm tra pattern lỗi
            for pattern, desc in ERROR_PATTERNS:
                if re.search(pattern, line, re.IGNORECASE):
                    # Lấy thời gian nếu có
                    time_match = re.search(r"\[(\d{4}-\d{2}-\d{2}[\sT]\d{2}:\d{2}:\d{2})", line)
                    time_str = time_match.group(1) if time_match else "N/A"
                    # Lấy context (3 dòng trước và sau)
                    context_start = max(0, i - 3)
                    context_end = min(len(lines), i + 4)
                    context = "\n".join(lines[context_start:context_end])
                    errors.append((time_str, "LỖI", desc, line.strip()[:300], log_file.name))
                    break
            else:
                for pattern, desc in WARNING_PATTERNS:
                    if re.search(pattern, line, re.IGNORECASE):
                        time_match = re.search(r"\[(\d{4}-\d{2}-\d{2}[\sT]\d{2}:\d{2}:\d{2})", line)
                        time_str = time_match.group(1) if time_match else "N/A"
                        warnings.append((time_str, "CW", desc, line.strip()[:300], log_file.name))
                        break

    def _on_error_select(self, event):
        sel = self.err_tree.selection()
        if not sel:
            return
        values = self.err_tree.item(sel[0], "values")
        if not values:
            return
        time_str, level, source, desc, file = values
        self.err_detail.config(state="normal")
        self.err_detail.delete("1.0", "end")
        self.err_detail.insert("end", f"[{time_str}] [{level}] [{source}] ({file})\n{desc}")
        self.err_detail.config(state="disabled")

    def _clear_errors(self):
        self.err_tree.delete(*self.err_tree.get_children())
        self.err_detail.config(state="normal")
        self.err_detail.delete("1.0", "end")
        self.err_detail.config(state="disabled")
        self.err_stats.config(text="(Đã xóa)", fg="#555")

    def _open_logs_dir(self):
        if LOGS_DIR.is_dir():
            os.startfile(str(LOGS_DIR))
        else:
            messagebox.showwarning("Cảnh báo", f"Không tìm thấy: {LOGS_DIR}")

    def _toggle_auto_monitor(self):
        if self.var_auto_monitor.get():
            self._log_monitor_stop.clear()
            self._log_monitor_thread = threading.Thread(target=self._auto_monitor_worker, daemon=True)
            self._log_monitor_thread.start()
            self.append_log("Lỗi", "Bật tự động giám sát log (mỗi 5s)")
        else:
            self._log_monitor_stop.set()
            self.append_log("Lỗi", "Tắt tự động giám sát log")

    def _auto_monitor_worker(self):
        last_pos = {}
        while not self._log_monitor_stop.is_set():
            log_dirs = [LOGS_DIR, APP_LOGS_DIR]
            for log_dir in log_dirs:
                if not log_dir.is_dir():
                    continue
                for log_file in log_dir.glob("*.log"):
                    try:
                        size = log_file.stat().st_size
                        key = str(log_file)
                        if key in last_pos and last_pos[key] == size:
                            continue  # Không thay đổi

                        with open(log_file, "r", encoding="utf-8", errors="replace") as f:
                            if key in last_pos:
                                f.seek(last_pos[key])
                            new_content = f.read()
                            last_pos[key] = size

                        if new_content.strip():
                            for line in new_content.splitlines():
                                for pattern, desc in ERROR_PATTERNS:
                                    if re.search(pattern, line, re.IGNORECASE):
                                        time_str = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
                                        self.after(0, lambda l=line, d=desc, fn=log_file.name:
                                                   self.err_tree.insert("", "end",
                                                                        values=(time_str, "LỖI", d, l[:300], fn),
                                                                        tags=("error",)))
                                        self.after(0, lambda: self.err_tree.see(self.err_tree.get_children()[-1])
                                                   if self.err_tree.get_children() else None)
                                        break
                    except Exception:
                        pass

            self._log_monitor_stop.wait(5)

    # ========================================================================
    # TAB: NGƯỜI CHƠI
    # ========================================================================
    def _init_tab_players(self):
        f = tk.Frame(self.tab_players)
        f.pack(fill="both", expand=True)

        # Danh sách tài khoản
        card = tk.LabelFrame(f, text="Quản lý người chơi & tài khoản", font=self.font_bold, padx=10, pady=10)
        card.pack(fill="both", expand=True, pady=6)

        btn_f = tk.Frame(card)
        btn_f.pack(fill="x", pady=4)
        ttk.Button(btn_f, text="Nạp danh sách tài khoản", style="Primary.TButton",
                   command=self._load_accounts).pack(side="left", padx=4)
        ttk.Button(btn_f, text="Nạp danh sách nhân vật", style="Primary.TButton",
                   command=self._load_characters).pack(side="left", padx=4)
        ttk.Button(btn_f, text="Làm mới", command=self._refresh_players).pack(side="left", padx=4)

        self.lbl_player_count = tk.Label(card, text="(Chưa nạp)", font=self.font_small, fg="#666")
        self.lbl_player_count.pack(anchor="w", pady=2)

        # Treeview
        tree_f = tk.Frame(card)
        tree_f.pack(fill="both", expand=True, pady=4)

        self.player_tree = ttk.Treeview(tree_f, columns=("id", "name", "level", "gold", "gm", "status"),
                                        show="headings", height=12)
        self.player_tree.heading("id", text="ID")
        self.player_tree.heading("name", text="Tên")
        self.player_tree.heading("level", text="Cấp")
        self.player_tree.heading("gold", text="Vàng")
        self.player_tree.heading("gm", text="GM")
        self.player_tree.heading("status", text="Trạng thái")
        self.player_tree.column("id", width=60)
        self.player_tree.column("name", width=180)
        self.player_tree.column("level", width=60, anchor="center")
        self.player_tree.column("gold", width=100, anchor="e")
        self.player_tree.column("gm", width=40, anchor="center")
        self.player_tree.column("status", width=80, anchor="center")
        self.player_tree.pack(side="left", fill="both", expand=True)
        sc = ttk.Scrollbar(tree_f, orient="vertical", command=self.player_tree.yview)
        sc.pack(side="right", fill="y")
        self.player_tree.configure(yscrollcommand=sc.set)

        # Nút thao tác
        action_f = tk.LabelFrame(card, text="Thao tác", font=self.font_bold, padx=8, pady=8)
        action_f.pack(fill="x", pady=6)

        r1 = tk.Frame(action_f)
        r1.pack(fill="x", pady=2)
        ttk.Button(r1, text="Cấp quyền GM", style="Warning.TButton",
                   command=self._set_gm_level).pack(side="left", padx=4)
        ttk.Button(r1, text="Sửa vàng", command=self._edit_gold).pack(side="left", padx=4)
        ttk.Button(r1, text="Sửa cấp độ", command=self._edit_level).pack(side="left", padx=4)

        r2 = tk.Frame(action_f)
        r2.pack(fill="x", pady=2)
        ttk.Button(r2, text="Xem chi tiết", command=self._view_player_detail).pack(side="left", padx=4)
        ttk.Button(r2, text="Xóa nhân vật", style="Danger.TButton",
                   command=self._delete_char).pack(side="left", padx=4)

        # Ghi chú
        tk.Label(card, text=(
            "Lưu ý:\n"
            "- Cấp quyền GM: 0 = người chơi, 1+ = admin\n"
            "- Cần khởi động lại Game Server sau khi sửa database\n"
            "- Chỉnh sửa khi server đang tắt để tránh xung đột dữ liệu"
        ), font=self.font_small, fg="#555", justify="left").pack(anchor="w", pady=6)

    def _load_accounts(self):
        self.append_log("Người chơi", "Đang nạp danh sách tài khoản...")
        self.lbl_player_count.config(text="Đang nạp...")

        def worker():
            out, err, rc = run_mysql_query(
                "SELECT id, login_name, gm_level, status FROM accounts ORDER BY id LIMIT 200",
                "tsm_dodo"
            )
            if rc != 0:
                self.after(0, lambda: self.lbl_player_count.config(text=f"Lỗi: {err[:80]}"))
                return

            rows = parse_mysql_table(out)
            self.after(0, lambda: self.player_tree.delete(*self.player_tree.get_children()))
            for r in rows:
                self.after(0, lambda r=r: self.player_tree.insert("", "end", values=(
                    r.get("id", ""),
                    r.get("login_name", ""),
                    "",
                    "",
                    r.get("gm_level", "0"),
                    "Hoạt động" if r.get("status", "1") == "1" else "Khóa"
                )))
            self.after(0, lambda: self.lbl_player_count.config(text=f"{len(rows)} tài khoản"))
            self.append_log("Người chơi", f"Đã nạp {len(rows)} tài khoản")

        threading.Thread(target=worker, daemon=True).start()

    def _load_characters(self):
        self.append_log("Người chơi", "Đang nạp danh sách nhân vật...")
        self.lbl_player_count.config(text="Đang nạp...")

        def worker():
            # Thử các tên cột phổ biến
            out, err, rc = run_mysql_query(
                "SELECT c.id, c.name, c.level, c.gold, a.gm_level "
                "FROM characters c LEFT JOIN accounts a ON c.account_id = a.id "
                "ORDER BY c.level DESC LIMIT 200",
                "tsm_dodo"
            )
            if rc != 0:
                # Thử query đơn giản hơn
                out, err, rc = run_mysql_query("SELECT * FROM characters LIMIT 200", "tsm_dodo")
                if rc != 0:
                    self.after(0, lambda: self.lbl_player_count.config(text=f"Lỗi: {err[:80]}"))
                    return
                rows = parse_mysql_table(out)
                self.after(0, lambda: self.player_tree.delete(*self.player_tree.get_children()))
                for r in rows:
                    self.after(0, lambda r=r: self.player_tree.insert("", "end", values=(
                        r.get("id", ""),
                        r.get("name", ""),
                        r.get("level", ""),
                        r.get("gold", ""),
                        r.get("gm_level", "0"),
                        ""
                    )))
            else:
                rows = parse_mysql_table(out)
                self.after(0, lambda: self.player_tree.delete(*self.player_tree.get_children()))
                for r in rows:
                    self.after(0, lambda r=r: self.player_tree.insert("", "end", values=(
                        r.get("id", ""),
                        r.get("name", ""),
                        r.get("level", ""),
                        r.get("gold", ""),
                        r.get("gm_level", "0"),
                        ""
                    )))
            self.after(0, lambda: self.lbl_player_count.config(text=f"{len(rows)} nhân vật"))
            self.append_log("Người chơi", f"Đã nạp {len(rows)} nhân vật")

        threading.Thread(target=worker, daemon=True).start()

    def _refresh_players(self):
        if self.lbl_player_count.cget("text").endswith("tài khoản"):
            self._load_accounts()
        else:
            self._load_characters()

    def _get_selected_player_id(self) -> str:
        sel = self.player_tree.selection()
        if not sel:
            messagebox.showwarning("Cảnh báo", "Chọn người chơi trước!")
            return None
        values = self.player_tree.item(sel[0], "values")
        return values[0] if values else None

    def _set_gm_level(self):
        pid = self._get_selected_player_id()
        if not pid:
            return
        level = simpledialog.askinteger("Cấp quyền GM", f"Cấp GM cho tài khoản ID {pid}:", minvalue=0, maxvalue=99)
        if level is None:
            return
        out, err, rc = run_mysql_query(f"UPDATE accounts SET gm_level={level} WHERE id={pid}", "tsm_dodo")
        if rc == 0:
            self.append_log("Người chơi", f"Đã set GM={level} cho account {pid}")
            messagebox.showinfo("Thành công", f"Đã cấp GM cấp {level}!")
            self._refresh_players()
        else:
            messagebox.showerror("Lỗi", err)

    def _edit_gold(self):
        pid = self._get_selected_player_id()
        if not pid:
            return
        gold = simpledialog.askinteger("Sửa vàng", f"Số vàng mới cho nhân vật ID {pid}:", minvalue=0)
        if gold is None:
            return
        out, err, rc = run_mysql_query(f"UPDATE characters SET gold={gold} WHERE id={pid}", "tsm_dodo")
        if rc == 0:
            self.append_log("Người chơi", f"Đã set gold={gold} cho char {pid}")
            messagebox.showinfo("Thành công", f"Đã sửa vàng = {gold}!")
            self._refresh_players()
        else:
            messagebox.showerror("Lỗi", err)

    def _edit_level(self):
        pid = self._get_selected_player_id()
        if not pid:
            return
        level = simpledialog.askinteger("Sửa cấp độ", f"Cấp độ mới cho nhân vật ID {pid}:", minvalue=1, maxvalue=200)
        if level is None:
            return
        out, err, rc = run_mysql_query(f"UPDATE characters SET level={level} WHERE id={pid}", "tsm_dodo")
        if rc == 0:
            self.append_log("Người chơi", f"Đã set level={level} cho char {pid}")
            messagebox.showinfo("Thành công", f"Đã sửa cấp độ = {level}!")
            self._refresh_players()
        else:
            messagebox.showerror("Lỗi", err)

    def _view_player_detail(self):
        pid = self._get_selected_player_id()
        if not pid:
            return
        out, err, rc = run_mysql_query(f"SELECT * FROM characters WHERE id={pid}", "tsm_dodo")
        if rc == 0:
            rows = parse_mysql_table(out)
            if rows:
                lines = ["=== Chi tiết nhân vật ==="]
                for k, v in rows[0].items():
                    lines.append(f"{k}: {v}")
                messagebox.showinfo("Chi tiết", "\n".join(lines))
            else:
                messagebox.showinfo("Chi tiết", "Không tìm thấy nhân vật")
        else:
            messagebox.showerror("Lỗi", err)

    def _delete_char(self):
        pid = self._get_selected_player_id()
        if not pid:
            return
        if not messagebox.askyesno("Xác nhận xóa", f"CÓ CHẮN muốn xóa nhân vật ID {pid}?\nHành động này KHÔNG THỂ HOÀN TÁC!"):
            return
        out, err, rc = run_mysql_query(f"DELETE FROM characters WHERE id={pid}", "tsm_dodo")
        if rc == 0:
            self.append_log("Người chơi", f"Đã xóa nhân vật {pid}")
            messagebox.showinfo("Thành công", "Đã xóa nhân vật!")
            self._refresh_players()
        else:
            messagebox.showerror("Lỗi", err)

    # ========================================================================
    # TAB: SAO LƯU
    # ========================================================================
    def _init_tab_backup(self):
        f = tk.Frame(self.tab_backup)
        f.pack(fill="both", expand=True)

        # Sao lưu database
        db_card = tk.LabelFrame(f, text="Sao lưu Database", font=self.font_bold, padx=10, pady=10)
        db_card.pack(fill="x", pady=6)

        tk.Label(db_card,
                 text="Xuất toàn bộ database ra file SQL để sao lưu.\n"
                      "Có thể khôi phục lại bằng cách import file SQL.",
                 font=self.font_small, fg="#005A9E", justify="left").pack(anchor="w", pady=(0, 8))

        r1 = tk.Frame(db_card)
        r1.pack(fill="x", pady=4)
        ttk.Button(r1, text="Sao lưu database ngay", style="Success.TButton",
                   command=self._backup_database).pack(side="left", padx=4)
        ttk.Button(r1, text="Khôi phục từ file SQL", style="Warning.TButton",
                   command=self._restore_database).pack(side="left", padx=4)

        self.lbl_backup_status = tk.Label(db_card, text="(Chưa sao lưu)", font=self.font_small, fg="#666")
        self.lbl_backup_status.pack(anchor="w", pady=4)

        # Sao lưu cấu hình
        conf_card = tk.LabelFrame(f, text="Sao lưu Cấu hình", font=self.font_bold, padx=10, pady=10)
        conf_card.pack(fill="x", pady=6)

        tk.Label(conf_card,
                 text="Sao lưu các file cấu hình quan trọng:\n"
                      "- application.conf\n"
                      "- npc_drops.json\n"
                      "- launcher_config.json",
                 font=self.font_small, fg="#005A9E", justify="left").pack(anchor="w", pady=(0, 8))

        r2 = tk.Frame(conf_card)
        r2.pack(fill="x", pady=4)
        ttk.Button(r2, text="Sao lưu cấu hình", style="Primary.TButton",
                   command=self._backup_config).pack(side="left", padx=4)
        ttk.Button(r2, text="Khôi phục cấu hình", command=self._restore_config).pack(side="left", padx=4)

        # Dọn dẹp log cũ
        clean_card = tk.LabelFrame(f, text="Dọn dẹp log cũ", font=self.font_bold, padx=10, pady=10)
        clean_card.pack(fill="x", pady=6)

        tk.Label(clean_card,
                 text="Xóa các file log cũ để tiết kiệm dung lượng ổ đĩa.",
                 font=self.font_small, fg="#005A9E").pack(anchor="w", pady=(0, 8))

        r3 = tk.Frame(clean_card)
        r3.pack(fill="x", pady=4)
        ttk.Button(r3, text="Xóa log cũ (>7 ngày)", style="Danger.TButton",
                   command=lambda: self._clean_old_logs(7)).pack(side="left", padx=4)
        ttk.Button(r3, text="Xóa log cũ (>30 ngày)", style="Danger.TButton",
                   command=lambda: self._clean_old_logs(30)).pack(side="left", padx=4)
        ttk.Button(r3, text="Xóa toàn bộ log", style="Danger.TButton",
                   command=lambda: self._clean_old_logs(0)).pack(side="left", padx=4)

    def _backup_database(self):
        mysql_exe = find_mysql_client()
        if not mysql_exe:
            messagebox.showerror("Lỗi", "Không tìm thấy mysql.exe!")
            return

        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        default_name = f"tsm_dodo_backup_{timestamp}.sql"
        fp = filedialog.asksaveasfilename(
            title="Lưu file backup database",
            defaultextension=".sql",
            filetypes=[("SQL", "*.sql")],
            initialdir=str(SQL_DIR),
            initialfile=default_name
        )
        if not fp:
            return

        pwd = get_mysql_password()
        args = [mysql_exe, "-u", "root"]
        if pwd:
            args.append(f"-p{pwd}")
        args.extend(["tsm_dodo"])

        self.append_log("Hệ thống", f"Đang sao lưu database -> {fp}")
        self.lbl_backup_status.config(text="Đang sao lưu...")

        def worker():
            if lz4_packed is None:
                self.after(0, lambda: messagebox.showerror("Lỗi", "Module LZ4 chưa được cài.\nChạy: pip install lz4"))
                self.after(0, lambda: self.compdata_status.config(text="Lỗi: thiếu module lz4", fg="#D13438"))
                return
            try:
                result = subprocess.run(
                    args, input="", capture_output=True, text=True, timeout=120,
                    encoding="utf-8", errors="replace",
                    # Dùng --opt cho mysqldump thay vì mysql -e
                )
                # Dùng mysqldump
                mysqldump = mysql_exe.replace("mysql.exe", "mysqldump.exe")
                if os.path.isfile(mysqldump):
                    dump_args = [mysqldump, "-u", "root"]
                    if pwd:
                        dump_args.append(f"-p{pwd}")
                    dump_args.append("tsm_dodo")
                    result = subprocess.run(
                        dump_args, capture_output=True, timeout=120,
                    )
                    if result.returncode == 0:
                        Path(fp).write_bytes(result.stdout)
                        size = human_size(len(result.stdout))
                        self.after(0, lambda: self.lbl_backup_status.config(
                            text=f"Đã sao lưu: {fp} ({size})", fg="#107C41"
                        ))
                        self.after(0, lambda: self.append_log("Hệ thống", f"Sao lưu xong: {fp} ({size})"))
                        messagebox.showinfo("Thành công", f"Đã sao lưu database!\n{fp}\nKích thước: {size}")
                    else:
                        err = result.stderr.decode("utf-8", errors="replace") if result.stderr else "Unknown"
                        self.after(0, lambda: self.lbl_backup_status.config(text=f"Lỗi: {err[:80]}", fg="#D13438"))
                        messagebox.showerror("Lỗi", f"Sao lưu thất bại:\n{err}")
                else:
                    self.after(0, lambda: self.lbl_backup_status.config(
                        text="Không tìm thấy mysqldump.exe!", fg="#D13438"
                    ))
                    messagebox.showerror("Lỗi", "Không tìm thấy mysqldump.exe!")
            except Exception as e:
                self.after(0, lambda: self.lbl_backup_status.config(text=f"Lỗi: {e}", fg="#D13438"))
                messagebox.showerror("Lỗi", str(e))

        threading.Thread(target=worker, daemon=True).start()

    def _restore_database(self):
        fp = filedialog.askopenfilename(
            title="Chọn file SQL để khôi phục",
            filetypes=[("SQL", "*.sql"), ("Tất cả", "*.*")],
            initialdir=str(SQL_DIR)
        )
        if not fp:
            return

        if not messagebox.askyesno("Xác nhận", f"Khôi phục database từ:\n{fp}\n\nDữ liệu hiện tại sẽ bị ghi đè!"):
            return

        self.append_log("Hệ thống", f"Đang khôi phục database từ {fp}...")
        self.lbl_backup_status.config(text="Đang khôi phục...")

        def worker():
            if lz4_packed is None:
                self.after(0, lambda: messagebox.showerror("Lỗi", "Module LZ4 chưa được cài.\nChạy: pip install lz4"))
                self.after(0, lambda: self.compdata_status.config(text="Lỗi: thiếu module lz4", fg="#D13438"))
                return
            try:
                content = Path(fp).read_text(encoding="utf-8", errors="replace")
                out, err, rc = run_mysql_multi(content, "tsm_dodo", timeout=300)
                if rc == 0:
                    self.after(0, lambda: self.lbl_backup_status.config(
                        text=f"Đã khôi phục từ: {fp}", fg="#107C41"
                    ))
                    self.after(0, lambda: self.append_log("Hệ thống", "Khôi phục database xong!"))
                    messagebox.showinfo("Thành công", "Đã khôi phục database!")
                else:
                    self.after(0, lambda: self.lbl_backup_status.config(text=f"Lỗi: {err[:80]}", fg="#D13438"))
                    messagebox.showerror("Lỗi", f"Khôi phục thất bại:\n{err}")
            except Exception as e:
                self.after(0, lambda: self.lbl_backup_status.config(text=f"Lỗi: {e}", fg="#D13438"))
                messagebox.showerror("Lỗi", str(e))

        threading.Thread(target=worker, daemon=True).start()

    def _backup_config(self):
        backup_dir = ROOT_DIR / "config_backups"
        backup_dir.mkdir(exist_ok=True)
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        sub = backup_dir / f"backup_{timestamp}"
        sub.mkdir()

        files_to_backup = [
            APP_CONF,
            NPC_DROPS_FILE,
            CONFIG_FILE,
        ]

        count = 0
        for f in files_to_backup:
            if f.is_file():
                shutil.copy2(f, sub / f.name)
                count += 1

        self.append_log("Hệ thống", f"Đã sao lưu {count} file cấu hình -> {sub}")
        messagebox.showinfo("Thành công", f"Đã sao lưu {count} file cấu hình!\n{sub}")

    def _restore_config(self):
        backup_dir = ROOT_DIR / "config_backups"
        if not backup_dir.is_dir():
            messagebox.showwarning("Cảnh báo", "Không có bản sao lưu cấu hình nào!")
            return

        backups = sorted([d for d in backup_dir.iterdir() if d.is_dir()], reverse=True)
        if not backups:
            messagebox.showwarning("Cảnh báo", "Không có bản sao lưu nào!")
            return

        # Hiển thị danh sách
        lines = ["Chọn bản sao lưu để khôi phục:"]
        for i, b in enumerate(backups[:10]):
            lines.append(f"{i}: {b.name}")

        choice = simpledialog.askinteger("Khôi phục", "\n".join(lines), minvalue=0, maxvalue=len(backups)-1)
        if choice is None:
            return

        src = backups[choice]
        if not messagebox.askyesno("Xác nhận", f"Khôi phục cấu hình từ:\n{src.name}\n\nCấu hình hiện tại sẽ bị ghi đè!"):
            return

        count = 0
        for f in src.iterdir():
            if f.name == "application.conf":
                target = APP_CONF
            elif f.name == "npc_drops.json":
                target = NPC_DROPS_FILE
            elif f.name == "launcher_config.json":
                target = CONFIG_FILE
            else:
                continue
            shutil.copy2(f, target)
            count += 1

        self.append_log("Hệ thống", f"Đã khôi phục {count} file cấu hình")
        messagebox.showinfo("Thành công", f"Đã khôi phục {count} file cấu hình!")

    def _clean_old_logs(self, days: int):
        if days == 0:
            if not messagebox.askyesno("Xác nhận", "XÓA TOÀN BỘ file log?\nHành động này không thể hoàn tác!"):
                return
        else:
            if not messagebox.askyesno("Xác nhận", f"Xóa các file log cũ hơn {days} ngày?"):
                return

        cutoff = time.time() - (days * 86400) if days > 0 else time.time()
        deleted = 0

        for log_dir in [LOGS_DIR, APP_LOGS_DIR]:
            if not log_dir.is_dir():
                continue
            for f in log_dir.glob("*.log"):
                try:
                    if f.stat().st_mtime < cutoff or days == 0:
                        f.unlink()
                        deleted += 1
                except Exception:
                    pass

        self.append_log("Hệ thống", f"Đã xóa {deleted} file log")
        messagebox.showinfo("Thành công", f"Đã xóa {deleted} file log!")

    # ========================================================================
    # TAB: COMPRESSEDATA (LZ4)
    # ========================================================================
    def _init_tab_compdata(self):
        f = tk.Frame(self.tab_compdata)
        f.pack(fill="both", expand=True)

        # Mô tả
        desc = tk.LabelFrame(f, text="Quản lý file nén LZ4 (CompreseData)", font=self.font_bold, padx=10, pady=10)
        desc.pack(fill="x", pady=6)

        tk.Label(desc, text=(
            "Đọc và chỉnh sửa các file LZ4 / .dat / .emg / .mmg.\n"
            "Hỗ trợ: giải nén (.lz4 -> gốc), nén lại (gốc -> .lz4), xem nội dung, hex viewer.\n"
            "Nhấn 'Chọn thư mục' để duyệt thư mục khác (Data, CompreseData, v.v.)."
        ), font=self.font_small, fg="#005A9E", justify="left").pack(anchor="w", pady=(0, 8))

        if not COMPRESSE_DATA_DIR.is_dir():
            tk.Label(desc, text="CẢNH BÁO: Không tìm thấy thư mục CompreseData!", font=self.font_bold, fg="#D13438").pack(anchor="w")
            # Không return nữa — vẫn cho phép chọn thư mục khác

        # Banner hướng dẫn cấu trúc file
        banner = tk.Frame(desc, bg="#FFF3CD", padx=10, pady=6)
        banner.pack(fill="x", pady=(4, 0))
        tk.Label(banner, bg="#FFF3CD", fg="#856404", font=self.font_small,
                 text=(
                     "⚠ Các file KHÔNG bị mã hóa — chỉ là định dạng nhị phân riêng:\n"
                     "  Talk_C.dat = text đối thoại UTF-16-LE → nhấn 'Edit Talk_C.dat' bên dưới\n"
                     "  TextData_C.dat = text UI/nhiệm vụ/event (thư mục Data) → nhấn 'Edit TextData_C.dat'  |  Eve.emg / Ground.mmg = file nhị phân (chỉ xem hex)"
                 ), justify="left").pack(anchor="w")

        # Danh sách file (không expand để action_card luôn nhìn thấy)
        list_card = tk.LabelFrame(f, text="Danh sách file", font=self.font_bold, padx=10, pady=10)
        list_card.pack(fill="x", pady=6)

        btn_f = tk.Frame(list_card)
        btn_f.pack(fill="x", pady=4)
        ttk.Button(btn_f, text="Nạp danh sách", style="Primary.TButton",
                   command=self._load_compdata_files).pack(side="left", padx=4)
        ttk.Button(btn_f, text="Chọn thư mục", style="Primary.TButton",
                   command=self._choose_compdata_dir).pack(side="left", padx=4)
        ttk.Button(btn_f, text="Làm mới", command=self._load_compdata_files).pack(side="left", padx=4)
        ttk.Button(btn_f, text="Mở thư mục", command=self._open_compdata_dir).pack(side="left", padx=4)

        # Label hiển thị thư mục hiện tại
        self._compdata_dir_var = tk.StringVar(value=str(COMPRESSE_DATA_DIR))
        self._compdata_dir = COMPRESSE_DATA_DIR  # mặc định
        dir_lbl = tk.Label(btn_f, textvariable=self._compdata_dir_var, font=self.font_small,
                          fg="#005A9E", anchor="w")
        dir_lbl.pack(side="left", padx=8, fill="x", expand=True)

        cols = ("name", "type", "size", "status")
        self.compdata_tree = ttk.Treeview(list_card, columns=cols, show="headings", height=5)
        self.compdata_tree.heading("name", text="Tên file")
        self.compdata_tree.heading("type", text="Loại")
        self.compdata_tree.heading("size", text="Kích thước")
        self.compdata_tree.heading("status", text="Trạng thái")
        self.compdata_tree.column("name", width=220)
        self.compdata_tree.column("type", width=80)
        self.compdata_tree.column("size", width=100, anchor="e")
        self.compdata_tree.column("status", width=120)
        tree_sb = ttk.Scrollbar(list_card, orient="vertical", command=self.compdata_tree.yview)
        self.compdata_tree.configure(yscrollcommand=tree_sb.set)
        tree_sb.pack(side="right", fill="y", pady=4)
        self.compdata_tree.pack(fill="x", pady=4)
        self.compdata_tree.bind("<<TreeviewSelect>>", self._on_compdata_select)
        self.compdata_tree.bind("<Double-1>", self._on_compdata_double_click)

        # Thanh nút cố định — LUÔN nhìn thấy, không bị ẩn
        quick_bar = tk.Frame(f)
        quick_bar.pack(fill="x", pady=4)
        ttk.Button(quick_bar, text=">> Mở Bảng Thao Tác (cửa sổ riêng) <<", style="Primary.TButton",
                   command=self._open_action_window).pack(side="left", padx=4)
        ttk.Button(quick_bar, text="Giải nén", style="Success.TButton",
                   command=self._decompress_selected).pack(side="left", padx=3)
        ttk.Button(quick_bar, text="Nén", style="Warning.TButton",
                   command=self._compress_selected).pack(side="left", padx=3)
        ttk.Button(quick_bar, text="Hex Viewer", style="Primary.TButton",
                   command=self._open_hex_window).pack(side="left", padx=3)
        ttk.Button(quick_bar, text="Edit Talk_C.dat", style="Primary.TButton",
                   command=self._edit_talk_data).pack(side="left", padx=3)
        ttk.Button(quick_bar, text="Edit TextData_C.dat", style="Primary.TButton",
                   command=self._edit_textdata).pack(side="left", padx=3)

        # Trạng thái + thông tin
        self.compdata_status = tk.Label(f, text="(Chọn file để xem thông tin)", font=self.font_small, fg="#555",
                                        justify="left")
        self.compdata_status.pack(fill="x", anchor="w", pady=4)

    def _open_action_window(self):
        """Mở cửa sổ popup chứa toàn bộ nút thao tác — luôn truy cập được."""
        win = tk.Toplevel(self)
        win.title("Bảng Thao Tác — CompreseData")
        win.geometry("580x620")
        win.transient(self)

        ttk.Label(win, text="Chọn file trong danh sách rồi nhấn nút bên dưới:",
                  font=self.font_bold).pack(anchor="w", padx=10, pady=(8, 4))

        info = tk.Label(win, text="", font=self.font_small, fg="#005A9E", justify="left")
        info.pack(fill="x", padx=10)
        fp = self._get_selected_compdata_file()
        if fp:
            info.config(text=f"Đang chọn: {fp.name}")
        else:
            info.config(text="(chưa chọn file nào)")

        def update_info(event=None):
            # Guard against destroyed widget
            try:
                fp2 = self._get_selected_compdata_file()
                if fp2:
                    info.config(text=f"Đang chọn: {fp2.name}")
                else:
                    info.config(text="(chưa chọn file nào)")
            except Exception:
                pass

        # Bind on tree selection — but unbind when window closes
        def on_select(event):
            update_info()
        self.compdata_tree.bind("<<TreeviewSelect>>", on_select, add="+")
        win._orig_select = self._on_compdata_select  # keep ref
        # Cleanup on close
        def on_close():
            try:
                self.compdata_tree.unbind("<<TreeviewSelect>>")
                # Re-bind the original handler
                self.compdata_tree.bind("<<TreeviewSelect>>", self._on_compdata_select)
            except Exception:
                pass
            win.destroy()
        win.protocol("WM_DELETE_WINDOW", on_close)

        # Bảng nút trong canvas có scrollbar
        canvas = tk.Canvas(win, highlightthickness=0, bg="#F0F0F0")
        sb = ttk.Scrollbar(win, orient="vertical", command=canvas.yview)
        inner = tk.Frame(canvas, bg="#F0F0F0")
        inner_id = canvas.create_window((0, 0), window=inner, anchor="nw")
        canvas.configure(yscrollcommand=sb.set)
        sb.pack(side="right", fill="y")
        canvas.pack(side="left", fill="both", expand=True, padx=(10, 0), pady=4)

        def on_resize(event):
            canvas.itemconfig(inner_id, width=event.width - 20)
        canvas.bind("<Configure>", on_resize)

        def on_inner_config(event):
            canvas.configure(scrollregion=canvas.bbox("all"))
        inner.bind("<Configure>", on_inner_config)

        # Row 1: Nén / Giải nén
        r1 = tk.LabelFrame(inner, text="Nén / Giải nén", font=self.font_bold, padx=8, pady=6)
        r1.pack(fill="x", pady=4)
        ttk.Button(r1, text=">> Giải nén (.lz4 -> file gốc)", style="Success.TButton",
                   command=self._decompress_selected).pack(side="left", padx=4)
        ttk.Button(r1, text=">> Nén (file gốc -> .lz4)", style="Warning.TButton",
                   command=self._compress_selected).pack(side="left", padx=4)

        # Row 2: Text editors
        r2 = tk.LabelFrame(inner, text="Chỉnh sửa Text", font=self.font_bold, padx=8, pady=6)
        r2.pack(fill="x", pady=4)
        ttk.Button(r2, text=">> EDIT Talk_C.dat (đọc/sửa text đối thoại) <<", style="Primary.TButton",
                   command=self._edit_talk_data).pack(fill="x", pady=2)
        ttk.Button(r2, text=">> EDIT TextData_C.dat (đọc/sửa text UI/nhiệm vụ/event) <<", style="Primary.TButton",
                   command=self._edit_textdata).pack(fill="x", pady=2)
        ttk.Button(r2, text=">> EDIT Eve.emg (xem/sửa index event scripts) <<", style="Primary.TButton",
                   command=self._edit_eve_data).pack(fill="x", pady=2)
        ttk.Button(r2, text=">> EDIT Ground.mmg (xem/sửa index bản đồ) <<", style="Primary.TButton",
                   command=self._edit_ground_data).pack(fill="x", pady=2)
        ttk.Button(r2, text=">> Xem & Chỉnh sửa Text tổng quát (auto giải nén + nén lại)",
                   command=self._edit_text_decompressed).pack(fill="x", pady=2)
        ttk.Button(r2, text=">> ĐỌC FILE .DAT BẤT KỲ (chọn file, auto phát hiện format) <<",
                   style="Primary.TButton",
                   command=self._open_any_dat_editor).pack(fill="x", pady=2)

        # Row 3: Hex & tìm kiếm
        r3 = tk.LabelFrame(inner, text="Hex & Tìm kiếm", font=self.font_bold, padx=8, pady=6)
        r3.pack(fill="x", pady=4)
        ttk.Button(r3, text=">> Hex Viewer (cửa sổ riêng)", style="Primary.TButton",
                   command=self._open_hex_window).pack(side="left", padx=4)
        ttk.Button(r3, text="Xem text chỉ đọc", command=self._view_text_decompressed).pack(side="left", padx=4)
        ttk.Button(r3, text="Tìm text", command=self._search_in_file).pack(side="left", padx=4)

        # Row 4: Tiện ích
        r4 = tk.LabelFrame(inner, text="Tiện ích", font=self.font_bold, padx=8, pady=6)
        r4.pack(fill="x", pady=4)
        ttk.Button(r4, text="Sao lưu file", command=self._backup_compdata_file).pack(side="left", padx=4)
        ttk.Button(r4, text="Xóa file tạm", style="Danger.TButton",
                   command=self._cleanup_temp_files).pack(side="left", padx=4)
        ttk.Button(r4, text="Mở thư mục", command=self._open_compdata_dir).pack(side="left", padx=4)

        # Nút đóng
        ttk.Button(win, text="Đóng", command=on_close).pack(pady=6)

    def _get_scan_dir(self) -> Path:
        """Trả về thư mục data hiện tại đang chọn (mặc định = CompreseData)."""
        d = getattr(self, '_compdata_dir', None)
        if d and d.is_dir():
            return d
        return COMPRESSE_DATA_DIR

    def _choose_compdata_dir(self):
        """Cho phép chọn thư mục tùy ý để duyệt file."""
        scan_dir = self._get_scan_dir()
        new_dir = filedialog.askdirectory(
            title="Chọn thư mục chứa file data",
            initialdir=str(scan_dir.parent if scan_dir.exists() else ROOT_DIR)
        )
        if not new_dir:
            return
        self._compdata_dir = Path(new_dir)
        self._compdata_dir_var.set(new_dir)
        self.append_log("Hệ thống", f"Đã chọn thư mục: {new_dir}")
        self._load_compdata_files()

    def _load_compdata_files(self):
        """Nạp danh sách file trong thư mục hiện tại (self._compdata_dir)."""
        self.compdata_tree.delete(*self.compdata_tree.get_children())
        scan_dir = self._get_scan_dir()
        if not scan_dir.is_dir():
            messagebox.showerror("Lỗi", f"Không tìm thấy thư mục:\n{scan_dir}")
            return

        count = 0
        for f in sorted(scan_dir.iterdir()):
            if f.is_file() and f.name != ".DS_Store":
                size = f.stat().st_size
                is_lz4 = f.suffix == ".lz4"
                ftype = "LZ4 nén" if is_lz4 else "Gốc"
                status = "Có thể giải nén" if is_lz4 else "Có thể nén"
                self.compdata_tree.insert("", "end", values=(f.name, ftype, human_size(size), status))
                count += 1

        self._compdata_dir_var.set(str(scan_dir))
        self.append_log("Hệ thống", f"Đã nạp {count} file từ: {scan_dir}")

    def _on_compdata_select(self, event):
        """Khi chọn file trong tree - hiển thị thông tin."""
        fp = self._get_selected_compdata_file()
        if not fp:
            return
        self._show_compdata_info(fp)

    def _on_compdata_double_click(self, event):
        """Double-click: mở hex viewer."""
        fp = self._get_selected_compdata_file()
        if fp:
            self._open_hex_window()

    def _show_compdata_info(self, fp: Path):
        """Hiển thị thông tin file trong label trạng thái."""
        try:
            data = fp.read_bytes()
            size = len(data)
            is_lz4 = fp.suffix == ".lz4"

            if is_lz4:
                if lz4_packed.is_lz4_packed(data):
                    info = lz4_packed.get_block_info(data)
                    total_uncomp = sum(b["uncomp_size"] for b in info)
                    total_comp = sum(b["comp_size"] for b in info)
                    avg_ratio = (1 - total_comp / total_uncomp) * 100 if total_uncomp > 0 else 0
                    info_text = (
                        f"File: {fp.name}  |  Nén: {human_size(size)} -> Giải nén: {human_size(total_uncomp)}  |  "
                        f"Blocks: {len(info)}  |  Tỉ lệ nén: {avg_ratio:.1f}%  |  Magic: {data[:4].hex(' ')}"
                    )
                else:
                    info_text = f"File: {fp.name}  |  Không phải LZ4 packed!"
            else:
                magic = data[:16]
                info_text = (
                    f"File: {fp.name}  |  Kích thước: {human_size(size)}  |  "
                    f"Hex đầu: {magic.hex(' ')}  |  "
                    f"ASCII: {''.join(chr(b) if 32 <= b < 127 else '.' for b in magic)}"
                )

            self.compdata_status.config(text=info_text, fg="#333")
        except Exception as e:
            self.compdata_status.config(text=f"Lỗi: {e}", fg="#D13438")

    def _get_selected_compdata_file(self):
        """Trả về Path của file đang chọn trong tree."""
        sel = self.compdata_tree.selection()
        if not sel:
            messagebox.showwarning("Cảnh báo", "Chọn file trước!")
            return None
        values = self.compdata_tree.item(sel[0], "values")
        if not values:
            return None
        scan_dir = self._get_scan_dir()
        return scan_dir / values[0]

    def _decompress_selected(self):
        """Giải nén file .lz4 đang chọn."""
        fp = self._get_selected_compdata_file()
        if not fp:
            return
        if fp.suffix != ".lz4":
            messagebox.showwarning("Cảnh báo", "File này không phải .lz4!\nChọn file .lz4 để giải nén.")
            return

        out_path = fp.with_suffix("")
        if out_path.exists():
            if not messagebox.askyesno("Xác nhận", f"File đã tồn tại:\n{out_path.name}\nGhi đè?"):
                return

        self.compdata_status.config(text="Đang giải nén...", fg="#005A9E")
        self.append_log("Hệ thống", f"Giải nén {fp.name}...")

        def worker():
            if lz4_packed is None:
                self.after(0, lambda: messagebox.showerror("Lỗi", "Module LZ4 chưa được cài.\nChạy: pip install lz4"))
                self.after(0, lambda: self.compdata_status.config(text="Lỗi: thiếu module lz4", fg="#D13438"))
                return
            try:
                data = fp.read_bytes()
                dec = lz4_packed.decompress(data)
                out_path.write_bytes(dec)
                self.after(0, lambda: self.compdata_status.config(
                    text=f"Đã giải nén: {out_path.name} ({human_size(len(dec))})", fg="#107C41"
                ))
                self.after(0, lambda: self.append_log("Hệ thống", f"Giải nén xong: {out_path.name} ({human_size(len(dec))})"))
                self.after(0, lambda: messagebox.showinfo("Thành công",
                    f"Đã giải nén:\n{out_path.name}\nKích thước: {human_size(len(dec))}"))
                self.after(0, self._load_compdata_files)
            except Exception as e:
                self.after(0, lambda: self.compdata_status.config(text=f"Lỗi: {e}", fg="#D13438"))
                self.after(0, lambda: messagebox.showerror("Lỗi", str(e)))

        threading.Thread(target=worker, daemon=True).start()

    def _compress_selected(self):
        """Nén file gốc đang chọn thành .lz4."""
        fp = self._get_selected_compdata_file()
        if not fp:
            return
        if fp.suffix == ".lz4":
            messagebox.showwarning("Cảnh báo", "File này đã là .lz4!\nChọn file gốc (.emg, .mmg, .dat) để nén.")
            return

        out_path = fp.with_suffix(fp.suffix + ".lz4")
        if out_path.exists():
            if not messagebox.askyesno("Xác nhận", f"File .lz4 đã tồn tại:\n{out_path.name}\nGhi đè?"):
                return

        self.compdata_status.config(text="Đang nén...", fg="#005A9E")
        self.append_log("Hệ thống", f"Nén {fp.name}...")

        def worker():
            if lz4_packed is None:
                self.after(0, lambda: messagebox.showerror("Lỗi", "Module LZ4 chưa được cài.\nChạy: pip install lz4"))
                self.after(0, lambda: self.compdata_status.config(text="Lỗi: thiếu module lz4", fg="#D13438"))
                return
            try:
                data = fp.read_bytes()
                comp = lz4_packed.compress(data, high_compression=True)
                out_path.write_bytes(comp)
                ratio = (1 - len(comp) / len(data)) * 100 if len(data) > 0 else 0
                self.after(0, lambda: self.compdata_status.config(
                    text=f"Đã nén: {out_path.name} ({human_size(len(comp))}), tỉ lệ {ratio:.1f}%", fg="#107C41"
                ))
                self.after(0, lambda: self.append_log("Hệ thống",
                    f"Nén xong: {out_path.name} ({human_size(len(comp))}), tỉ lệ {ratio:.1f}%"))
                self.after(0, lambda: messagebox.showinfo("Thành công",
                    f"Đã nén:\n{out_path.name}\nKích thước: {human_size(len(comp))}\nTỉ lệ nén: {ratio:.1f}%"))
                self.after(0, self._load_compdata_files)
            except Exception as e:
                self.after(0, lambda: self.compdata_status.config(text=f"Lỗi: {e}", fg="#D13438"))
                self.after(0, lambda: messagebox.showerror("Lỗi", str(e)))

        threading.Thread(target=worker, daemon=True).start()

    def _open_hex_window(self):
        """Mở cửa sổ Hex Viewer riêng cho file đang chọn."""
        fp = self._get_selected_compdata_file()
        if not fp:
            return

        try:
            data = fp.read_bytes()
            is_lz4 = fp.suffix == ".lz4"
            if is_lz4:
                if lz4_packed.is_lz4_packed(data):
                    data = lz4_packed.decompress(data)
        except Exception as e:
            messagebox.showerror("Lỗi", str(e))
            return

        win = tk.Toplevel(self)
        win.title(f"Hex Viewer - {fp.name}")
        win.geometry("750x600")

        top = tk.Frame(win)
        top.pack(fill="x", padx=8, pady=4)
        ttk.Label(top, text=f"File: {fp.name}  |  Kích thước: {human_size(len(data))}",
                  font=self.font_bold).pack(side="left")

        btn_f = tk.Frame(top)
        btn_f.pack(side="right")
        ttk.Button(btn_f, text="Đầu file", command=lambda: self._hex_nav(win, data, 0)).pack(side="left", padx=2)
        ttk.Button(btn_f, text="Cuối file", command=lambda: self._hex_nav(win, data, max(0, len(data) - 512))).pack(side="left", padx=2)
        ttk.Button(btn_f, text="Đi đến offset...", command=lambda: self._hex_goto(win, data)).pack(side="left", padx=2)
        ttk.Button(btn_f, text="Đóng", command=win.destroy).pack(side="left", padx=2)

        # Frame chứa text + scrollbar
        body = tk.Frame(win)
        body.pack(fill="both", expand=True, padx=8, pady=4)

        hex_txt = tk.Text(body, font=self.font_code, wrap="none",
                          bg="#1E1E1E", fg="#90EE90", state="disabled")
        sb_y = ttk.Scrollbar(body, orient="vertical", command=hex_txt.yview)
        sb_x = ttk.Scrollbar(body, orient="horizontal", command=hex_txt.xview)
        hex_txt.configure(yscrollcommand=sb_y.set, xscrollcommand=sb_x.set)
        sb_y.pack(side="right", fill="y")
        sb_x.pack(side="bottom", fill="x")
        hex_txt.pack(fill="both", expand=True)

        win._hex_text = hex_txt
        win._hex_data = data

        self._hex_nav(win, data, 0)

    def _hex_nav(self, win: tk.Toplevel, data: bytes, offset: int):
        """Hiển thị 512 byte từ offset trong hex viewer."""
        hex_txt = win._hex_text
        hex_txt.config(state="normal")
        hex_txt.delete("1.0", "end")

        chunk = data[offset:offset + 512]
        hex_txt.insert("end", f"=== Offset {offset} - {offset + len(chunk)} / {len(data)} ===\n\n")

        for i in range(0, len(chunk), 16):
            line_data = chunk[i:i + 16]
            addr = offset + i
            hex_part = " ".join(f"{b:02X}" for b in line_data)
            ascii_part = "".join(chr(b) if 32 <= b < 127 else "." for b in line_data)
            hex_txt.insert("end", f"{addr:08X}  {hex_part:<48}  {ascii_part}\n")

        hex_txt.config(state="disabled")

    def _hex_goto(self, win: tk.Toplevel, data: bytes):
        """Đi đến offset người dùng nhập."""
        val = simpledialog.askinteger("Đi đến offset", f"Nhập offset (0 - {len(data) - 1}):",
                                       minvalue=0, maxvalue=len(data) - 1)
        if val is not None:
            self._hex_nav(win, data, val)

    def _search_in_file(self):
        """Tìm text trong file và hiển thị kết quả trong hex viewer."""
        fp = self._get_selected_compdata_file()
        if not fp:
            return

        search = simpledialog.askstring("Tìm text", "Nhập text cần tìm:")
        if not search:
            return

        try:
            data = fp.read_bytes()
            if fp.suffix == ".lz4":
                if lz4_packed.is_lz4_packed(data):
                    data = lz4_packed.decompress(data)

            search_bytes = search.encode("utf-8", errors="replace")
            positions = []
            offset = 0
            while True:
                pos = data.find(search_bytes, offset)
                if pos == -1:
                    break
                positions.append(pos)
                offset = pos + 1
                if len(positions) >= 100:
                    break

            if positions:
                # Mở hex viewer và hiển thị tại vị trí đầu tiên
                win = tk.Toplevel(self)
                win.title(f"Tìm thấy '{search}' - {fp.name}")
                win.geometry("750x600")

                top = tk.Frame(win)
                top.pack(fill="x", padx=8, pady=4)
                ttk.Label(top, text=f"Tìm thấy {len(positions)} kết quả.",
                          font=self.font_bold, foreground="#107C41").pack(side="left")

                btn_f = tk.Frame(top)
                btn_f.pack(side="right")

                # Nút chuyển kết quả
                self._search_positions = positions
                self._search_idx = 0
                self._search_data = data

                def goto_result(idx):
                    self._search_idx = idx
                    pos = positions[idx]
                    start = max(0, pos - 32)
                    self._hex_nav(win, data, start)

                def next_result():
                    if self._search_idx + 1 < len(positions):
                        goto_result(self._search_idx + 1)

                def prev_result():
                    if self._search_idx > 0:
                        goto_result(self._search_idx - 1)

                ttk.Button(btn_f, text="Trước", command=prev_result).pack(side="left", padx=2)
                ttk.Button(btn_f, text="Sau", command=next_result).pack(side="left", padx=2)
                ttk.Button(btn_f, text="Đóng", command=win.destroy).pack(side="left", padx=2)

                body = tk.Frame(win)
                body.pack(fill="both", expand=True, padx=8, pady=4)

                hex_txt = tk.Text(body, font=self.font_code, wrap="none",
                                  bg="#1E1E1E", fg="#90EE90", state="disabled")
                sb_y = ttk.Scrollbar(body, orient="vertical", command=hex_txt.yview)
                sb_x = ttk.Scrollbar(body, orient="horizontal", command=hex_txt.xview)
                hex_txt.configure(yscrollcommand=sb_y.set, xscrollcommand=sb_x.set)
                sb_y.pack(side="right", fill="y")
                sb_x.pack(side="bottom", fill="x")
                hex_txt.pack(fill="both", expand=True)

                win._hex_text = hex_txt
                win._hex_data = data

                goto_result(0)
                self.compdata_status.config(text=f"Tìm thấy {len(positions)} kết quả.", fg="#107C41")
            else:
                self.compdata_status.config(text=f"Không tìm thấy '{search}'", fg="#D13438")
                messagebox.showinfo("Kết quả", f"Không tìm thấy '{search}' trong file")
        except Exception as e:
            messagebox.showerror("Lỗi", str(e))

    def _backup_compdata_file(self):
        """Sao lưu file đang chọn."""
        fp = self._get_selected_compdata_file()
        if not fp:
            return

        backup_dir = self._get_scan_dir() / "backups"
        backup_dir.mkdir(exist_ok=True)
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        dest = backup_dir / f"{fp.name}.{timestamp}.bak"

        try:
            shutil.copy2(fp, dest)
            self.append_log("Hệ thống", f"Đã sao lưu: {fp.name} -> {dest.name}")
            messagebox.showinfo("Thành công", f"Đã sao lưu:\n{dest}")
        except Exception as e:
            messagebox.showerror("Lỗi", str(e))

    def _view_text_decompressed(self):
        """Giải nén tạm và xem nội dung text (chỉ đọc)."""
        fp = self._get_selected_compdata_file()
        if not fp:
            return

        try:
            data = fp.read_bytes()
            if fp.suffix == ".lz4":
                if lz4_packed.is_lz4_packed(data):
                    data = lz4_packed.decompress(data)

            text = data.decode("utf-8", errors="replace")

            win = tk.Toplevel(self)
            win.title(f"Xem text - {fp.name}")
            win.geometry("800x600")

            top = tk.Frame(win)
            top.pack(fill="x", padx=8, pady=4)
            ttk.Label(top, text=f"File: {fp.name}  |  Kích thước: {human_size(len(data))}  |  Chỉ đọc",
                      font=self.font_bold).pack(side="left")
            ttk.Button(top, text="Đóng", command=win.destroy).pack(side="right")

            body = tk.Frame(win)
            body.pack(fill="both", expand=True, padx=8, pady=4)

            txt = tk.Text(body, font=self.font_code, wrap="word", bg="#1E1E1E", fg="#D4D4D4")
            sb = ttk.Scrollbar(body, orient="vertical", command=txt.yview)
            txt.configure(yscrollcommand=sb.set)
            sb.pack(side="right", fill="y")
            txt.pack(fill="both", expand=True)
            txt.insert("1.0", text[:200000])
            txt.config(state="disabled")

        except Exception as e:
            messagebox.showerror("Lỗi", str(e))

    def _edit_text_decompressed(self):
        """Giải nén, chỉnh sửa text, rồi nén lại. Mở cửa sổ editor đầy đủ."""
        fp = self._get_selected_compdata_file()
        if not fp:
            return

        try:
            data = fp.read_bytes()
            is_lz4 = fp.suffix == ".lz4"
            if is_lz4:
                if lz4_packed.is_lz4_packed(data):
                    data = lz4_packed.decompress(data)
                else:
                    messagebox.showerror("Lỗi", "File không phải LZ4 packed!")
                    return
        except Exception as e:
            messagebox.showerror("Lỗi", f"Giải nén thất bại:\n{e}")
            return

        win = tk.Toplevel(self)
        win.title(f"Chỉnh sửa - {fp.name}")
        win.geometry("1000x700")

        # Top bar
        top = tk.Frame(win)
        top.pack(fill="x", padx=8, pady=4)

        info_text = f"File: {fp.name}  |  Kích thước: {human_size(len(data))}"
        if is_lz4:
            info_text += "  |  LZ4 (lưu sẽ tự nén lại)"
        ttk.Label(top, text=info_text, font=self.font_bold).pack(side="left")

        btn_f = tk.Frame(top)
        btn_f.pack(side="right")
        ttk.Button(btn_f, text=">> LƯU & NÉN LẰI <<", style="Success.TButton",
                   command=lambda: self._save_edited_lz4(fp, win, is_lz4)).pack(side="left", padx=4)
        ttk.Button(btn_f, text="Hủy", command=win.destroy).pack(side="left", padx=4)

        # Notebook: Text editor + Hex viewer
        nb = ttk.Notebook(win)
        nb.pack(fill="both", expand=True, padx=8, pady=4)

        # Tab 1: Text editor
        text_tab = ttk.Frame(nb)
        nb.add(text_tab, text="Text Editor")

        txt = tk.Text(text_tab, font=self.font_code, wrap="word", bg="#1E1E1E", fg="#D4D4D4",
                      insertbackground="white")
        sb_t = ttk.Scrollbar(text_tab, orient="vertical", command=txt.yview)
        txt.configure(yscrollcommand=sb_t.set)
        sb_t.pack(side="right", fill="y")
        txt.pack(fill="both", expand=True)
        txt.insert("1.0", data.decode("utf-8", errors="replace"))

        # Tab 2: Hex viewer
        hex_tab = ttk.Frame(nb)
        nb.add(hex_tab, text="Hex Viewer")

        hex_txt = tk.Text(hex_tab, font=self.font_code, wrap="none",
                          bg="#1E1E1E", fg="#90EE90", state="disabled")
        sb_h = ttk.Scrollbar(hex_tab, orient="vertical", command=hex_txt.yview)
        sb_hx = ttk.Scrollbar(hex_tab, orient="horizontal", command=hex_txt.xview)
        hex_txt.configure(yscrollcommand=sb_h.set, xscrollcommand=sb_hx.set)
        sb_h.pack(side="right", fill="y")
        sb_hx.pack(side="bottom", fill="x")
        hex_txt.pack(fill="both", expand=True)

        # Hex nav buttons
        hex_btn_f = tk.Frame(hex_tab)
        hex_btn_f.pack(fill="x", pady=2)
        ttk.Button(hex_btn_f, text="Đầu file", command=lambda: self._hex_nav_tab(hex_txt, data, 0)).pack(side="left", padx=2)
        ttk.Button(hex_btn_f, text="Cuối file",
                   command=lambda: self._hex_nav_tab(hex_txt, data, max(0, len(data) - 512))).pack(side="left", padx=2)

        self._hex_nav_tab(hex_txt, data, 0)

        win._text_widget = txt
        win._hex_widget = hex_txt
        win._is_lz4 = is_lz4
        win._orig_data = data

    def _hex_nav_tab(self, hex_txt: tk.Text, data: bytes, offset: int):
        """Hiển thị hex dump trong tab hex của editor."""
        hex_txt.config(state="normal")
        hex_txt.delete("1.0", "end")

        chunk = data[offset:offset + 512]
        hex_txt.insert("end", f"=== Offset {offset} - {offset + len(chunk)} / {len(data)} ===\n\n")

        for i in range(0, len(chunk), 16):
            line_data = chunk[i:i + 16]
            addr = offset + i
            hex_part = " ".join(f"{b:02X}" for b in line_data)
            ascii_part = "".join(chr(b) if 32 <= b < 127 else "." for b in line_data)
            hex_txt.insert("end", f"{addr:08X}  {hex_part:<48}  {ascii_part}\n")

        hex_txt.config(state="disabled")

    def _save_edited_lz4(self, fp: Path, win: tk.Toplevel, is_lz4: bool):
        """Lưu nội dung đã chỉnh sửa, nén lại nếu cần."""
        txt = win._text_widget
        content = txt.get("1.0", "end-1c")
        data = content.encode("utf-8", errors="replace")

        if not messagebox.askyesno("Xác nhận",
                f"Lưu file:\n{fp.name}\n\n"
                + ("Nội dung sẽ được NÉN LZ4 lại và ghi đè file gốc!" if is_lz4 else "Nội dung sẽ ghi đè file gốc!")
                + "\n\nFile gốc sẽ được backup trước khi lưu."):
            return

        # Backup
        backup_dir = self._get_scan_dir() / "backups"
        backup_dir.mkdir(exist_ok=True)
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        shutil.copy2(fp, backup_dir / f"{fp.name}.{timestamp}.bak")
        self.append_log("Hệ thống", f"Đã backup: {fp.name} -> backups/{fp.name}.{timestamp}.bak")

        try:
            if is_lz4:
                comp = lz4_packed.compress(data, high_compression=True)
                fp.write_bytes(comp)
                ratio = (1 - len(comp) / len(data)) * 100 if len(data) > 0 else 0
                self.append_log("Hệ thống", f"Đã lưu (nén): {fp.name} ({human_size(len(comp))}), tỉ lệ {ratio:.1f}%")
                messagebox.showinfo("Thành công",
                    f"Đã lưu và nén lại:\n{fp.name}\n"
                    f"Kích thước gốc: {human_size(len(data))}\n"
                    f"Kích thước nén: {human_size(len(comp))}\n"
                    f"Tỉ lệ nén: {ratio:.1f}%")
            else:
                fp.write_bytes(data)
                self.append_log("Hệ thống", f"Đã lưu: {fp.name} ({human_size(len(data))})")
                messagebox.showinfo("Thành công", f"Đã lưu:\n{fp.name}")

            win.destroy()
            self._load_compdata_files()
        except Exception as e:
            messagebox.showerror("Lỗi", str(e))

    def _cleanup_temp_files(self):
        """Xóa các file đã giải nén tạm (không có .lz4)."""
        scan_dir = self._get_scan_dir()
        if not scan_dir.is_dir():
            return
        if not messagebox.askyesno("Xác nhận",
                "Xóa các file đã giải nén tạm?\n"
                "Chỉ xóa file KHÔNG có đuôi .lz4 (file .emg, .mmg, .dat...).\n"
                "File .lz4 sẽ được giữ lại."):
            return

        deleted = 0
        for f in scan_dir.iterdir():
            if f.is_file() and f.suffix != ".lz4":
                try:
                    f.unlink()
                    deleted += 1
                except Exception:
                    pass

        self.append_log("Hệ thống", f"Đã xóa {deleted} file tạm từ: {scan_dir}")
        messagebox.showinfo("Thành công", f"Đã xóa {deleted} file tạm!")
        self._load_compdata_files()

    def _open_compdata_dir(self):
        """Mở thư mục hiện tại trong Explorer."""
        scan_dir = self._get_scan_dir()
        if scan_dir.is_dir():
            os.startfile(str(scan_dir))
        else:
            messagebox.showwarning("Cảnh báo", f"Không tìm thấy: {scan_dir}")

    # ========================================================================
    # TALK_C.DAT EDITOR — Đọc/sửa text đối thoại
    # ========================================================================
    def _edit_talk_data(self):
        """Mở editor chuyên dụng cho Talk_C.dat: parse -> sửa text -> đóng gói lại."""
        # Tìm file Talk_C.dat hoặc Talk_C.dat.lz4
        scan_dir = self._get_scan_dir()
        talk_lz4 = scan_dir / "Talk_C.dat.lz4"
        talk_raw = scan_dir / "Talk_C.dat"
        if talk_lz4.exists():
            fp = talk_lz4
        elif talk_raw.exists():
            fp = talk_raw
        else:
            messagebox.showerror("Lỗi", "Không tìm thấy Talk_C.dat hoặc Talk_C.dat.lz4!")
            return

        self.compdata_status.config(text="Đang giải nén & parse Talk_C.dat...", fg="#005A9E")
        self.append_log("Hệ thống", f"Parse Talk_C.dat: {fp.name}")

        def worker():
            if lz4_packed is None:
                self.after(0, lambda: messagebox.showerror("Lỗi", "Module LZ4 chưa được cài.\nChạy: pip install lz4"))
                self.after(0, lambda: self.compdata_status.config(text="Lỗi: thiếu module lz4", fg="#D13438"))
                return
            try:
                data = fp.read_bytes()
                if fp.suffix == ".lz4":
                    if lz4_packed.is_lz4_packed(data):
                        data = lz4_packed.decompress(data)
                entries = dat_packer.unpack_talk_data(data)
                self.after(0, lambda: self._open_talk_editor(fp, entries))
            except Exception as e:
                self.after(0, lambda: self.compdata_status.config(text=f"Lỗi: {e}", fg="#D13438"))
                self.after(0, lambda: messagebox.showerror("Lỗi", str(e)))

        threading.Thread(target=worker, daemon=True).start()

    def _open_talk_editor(self, fp: Path, entries: dict):
        """Mở cửa sổ editor Talk_C.dat với bảng liệt kê + chỉnh sửa text."""
        win = tk.Toplevel(self)
        win.title(f"Talk_C.dat Editor — {len(entries)} mục")
        win.geometry("1100x750")

        # Top bar
        top = tk.Frame(win)
        top.pack(fill="x", padx=8, pady=4)
        ttk.Label(top, text=f"File: {fp.name}  |  {len(entries)} mục đối thoại",
                  font=self.font_bold).pack(side="left")

        btn_f = tk.Frame(top)
        btn_f.pack(side="right")
        ttk.Button(btn_f, text=">> LƯU & NÉN LẠI <<", style="Success.TButton",
                   command=lambda: self._save_talk_data(fp, entries, win)).pack(side="left", padx=4)
        ttk.Button(btn_f, text="Xuất JSON", command=lambda: self._export_talk_json(entries)).pack(side="left", padx=4)
        ttk.Button(btn_f, text="Nhập JSON", command=lambda: self._import_talk_json(entries, win)).pack(side="left", padx=4)
        ttk.Button(btn_f, text="Đóng", command=win.destroy).pack(side="left", padx=4)

        # Search bar
        search_f = tk.Frame(win)
        search_f.pack(fill="x", padx=8, pady=2)
        ttk.Label(search_f, text="Tìm:").pack(side="left")
        search_var = tk.StringVar()
        search_entry = ttk.Entry(search_f, textvariable=search_var)
        search_entry.pack(side="left", fill="x", expand=True, padx=4)
        ttk.Button(search_f, text="Tìm ID", command=lambda: self._talk_search_id(entries, tree, search_var.get())).pack(side="left", padx=2)
        ttk.Button(search_f, text="Tìm text", command=lambda: self._talk_search_text(entries, tree, search_var.get())).pack(side="left", padx=2)

        # Split: tree (left) + text editor (right)
        paned = ttk.Panedwindow(win, orient="horizontal")
        paned.pack(fill="both", expand=True, padx=8, pady=4)

        # Left: tree
        tree_frame = ttk.Frame(paned)
        paned.add(tree_frame, weight=1)

        cols = ("id", "preview")
        tree = ttk.Treeview(tree_frame, columns=cols, show="headings", height=20)
        tree.heading("id", text="ID")
        tree.heading("preview", text="Nội dung (preview)")
        tree.column("id", width=60, anchor="e")
        tree.column("preview", width=350)
        tree_sb = ttk.Scrollbar(tree_frame, orient="vertical", command=tree.yview)
        tree.configure(yscrollcommand=tree_sb.set)
        tree_sb.pack(side="right", fill="y")
        tree.pack(fill="both", expand=True)

        # Populate tree
        for eid, text in sorted(entries.items()):
            preview = text[:60].replace("\n", " ") if text else "(rỗng)"
            tree.insert("", "end", values=(eid, preview))

        # Right: text editor
        editor_frame = ttk.Frame(paned)
        paned.add(editor_frame, weight=2)

        ttk.Label(editor_frame, text="Nội dung đối thoại:", font=self.font_bold).pack(anchor="w")
        ttk.Label(editor_frame, text="(Text tiếng Trung — chỉnh sửa trực tiếp, lưu sẽ tự nén LZ4 lại)",
                  font=self.font_small, fg="#005A9E").pack(anchor="w")

        id_label = ttk.Label(editor_frame, text="ID: (chọn mục bên trái)", font=self.font_bold, fg="#D13438")
        id_label.pack(anchor="w", pady=(4, 0))

        text_widget = tk.Text(editor_frame, font=self.font_code, wrap="word",
                               bg="#1E1E1E", fg="#D4D4D4", insertbackground="white", height=15)
        text_sb = ttk.Scrollbar(editor_frame, orient="vertical", command=text_widget.yview)
        text_widget.configure(yscrollcommand=text_sb.set)
        text_sb.pack(side="right", fill="y")
        text_widget.pack(fill="both", expand=True)

        # State
        win._talk_entries = entries
        win._talk_tree = tree
        win._talk_text = text_widget
        win._talk_id_label = id_label
        win._talk_current_id = [None]

        def on_tree_select(event):
            sel = tree.selection()
            if not sel:
                return
            values = tree.item(sel[0], "values")
            if not values:
                return
            eid = int(values[0])
            win._talk_current_id[0] = eid
            id_label.config(text=f"ID: {eid}")
            text_widget.delete("1.0", "end")
            text_widget.insert("1.0", entries.get(eid, ""))

        tree.bind("<<TreeviewSelect>>", on_tree_select)

        def on_text_change():
            eid = win._talk_current_id[0]
            if eid is None:
                return
            new_text = text_widget.get("1.0", "end-1c")
            entries[eid] = new_text
            # Update preview in tree
            sel = tree.selection()
            if sel:
                preview = new_text[:60].replace("\n", " ")
                tree.item(sel[0], values=(eid, preview))

        text_widget.bind("<<Modified>>", lambda e: (on_text_change(), text_widget.edit_modified(False)))

        self.compdata_status.config(text=f"Đã nạp {len(entries)} mục đối thoại", fg="#107C41")

    def _talk_search_id(self, entries: dict, tree: ttk.Treeview, query: str):
        """Tìm theo ID."""
        if not query:
            return
        try:
            target_id = int(query)
        except:
            messagebox.showwarning("Lỗi", "ID phải là số!")
            return
        for item in tree.get_children():
            values = tree.item(item, "values")
            if int(values[0]) == target_id:
                tree.selection_set(item)
                tree.see(item)
                return
        messagebox.showinfo("Kết quả", f"Không tìm thấy ID={target_id}")

    def _talk_search_text(self, entries: dict, tree: ttk.Treeview, query: str):
        """Tìm theo nội dung text."""
        if not query:
            return
        found = 0
        for item in tree.get_children():
            values = tree.item(item, "values")
            eid = int(values[0])
            text = entries.get(eid, "")
            if query in text:
                tree.selection_set(item)
                tree.see(item)
                found += 1
                if found >= 1:
                    break
        if found == 0:
            messagebox.showinfo("Kết quả", f"Không tìm thấy text chứa '{query}'")

    def _save_talk_data(self, fp: Path, entries: dict, win: tk.Toplevel):
        """Đóng gói entries thành Talk_C.dat(.lz4) và ghi đè."""
        if not messagebox.askyesno("Xác nhận",
                f"Lưu file:\n{fp.name}\n\n"
                f"{len(entries)} mục sẽ được đóng gói lại và NÉN LZ4.\n"
                f"File gốc sẽ được backup trước khi lưu."):
            return

        # Backup
        backup_dir = self._get_scan_dir() / "backups"
        backup_dir.mkdir(exist_ok=True)
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        shutil.copy2(fp, backup_dir / f"{fp.name}.{timestamp}.bak")
        self.append_log("Hệ thống", f"Đã backup: {fp.name}")

        def worker():
            if lz4_packed is None:
                self.after(0, lambda: messagebox.showerror("Lỗi", "Module LZ4 chưa được cài.\nChạy: pip install lz4"))
                self.after(0, lambda: self.compdata_status.config(text="Lỗi: thiếu module lz4", fg="#D13438"))
                return
            try:
                import dat_packer, lz4_packed
                packed = dat_packer.pack_talk_data(entries)
                if fp.suffix == ".lz4":
                    comp = lz4_packed.compress(packed, high_compression=True)
                    fp.write_bytes(comp)
                    ratio = (1 - len(comp) / len(packed)) * 100 if len(packed) > 0 else 0
                    self.after(0, lambda: self.append_log("Hệ thống",
                        f"Đã lưu Talk_C.dat (nén): {human_size(len(comp))}, tỉ lệ {ratio:.1f}%"))
                    self.after(0, lambda: messagebox.showinfo("Thành công",
                        f"Đã lưu và nén lại:\n{fp.name}\n"
                        f"Mục: {len(entries)}\n"
                        f"Kích thước gốc: {human_size(len(packed))}\n"
                        f"Kích thước nén: {human_size(len(comp))}\n"
                        f"Tỉ lệ nén: {ratio:.1f}%"))
                else:
                    fp.write_bytes(packed)
                    self.after(0, lambda: self.append_log("Hệ thống",
                        f"Đã lưu Talk_C.dat: {human_size(len(packed))}"))
                    self.after(0, lambda: messagebox.showinfo("Thành công",
                        f"Đã lưu:\n{fp.name}\n{len(entries)} mục"))
                self.after(0, win.destroy)
                self.after(0, self._load_compdata_files)
            except Exception as e:
                self.after(0, lambda: messagebox.showerror("Lỗi", str(e)))

        threading.Thread(target=worker, daemon=True).start()

    def _export_talk_json(self, entries: dict):
        """Xuất entries ra file JSON."""
        from tkinter import filedialog
        path = filedialog.asksaveasfilename(
            title="Xuất Talk_C.json",
            defaultextension=".json",
            initialdir=str(self._get_scan_dir()),
            filetypes=[("JSON", "*.json"), ("All", "*.*")])
        if not path:
            return
        try:
            import json
            with open(path, "w", encoding="utf-8") as f:
                json.dump(entries, f, ensure_ascii=False, indent=1)
            self.append_log("Hệ thống", f"Đã xuất JSON: {path} ({len(entries)} mục)")
            messagebox.showinfo("Thành công", f"Đã xuất:\n{path}\n{len(entries)} mục")
        except Exception as e:
            messagebox.showerror("Lỗi", str(e))

    def _import_talk_json(self, entries: dict, win: tk.Toplevel):
        """Nhập entries từ file JSON."""
        from tkinter import filedialog
        path = filedialog.askopenfilename(
            title="Nhập Talk_C.json",
            initialdir=str(self._get_scan_dir()),
            filetypes=[("JSON", "*.json"), ("All", "*.*")])
        if not path:
            return
        try:
            import json
            with open(path, "r", encoding="utf-8") as f:
                new_entries = json.load(f)
            new_entries = {int(k): v for k, v in new_entries.items()}
            entries.clear()
            entries.update(new_entries)
            # Refresh tree
            tree = win._talk_tree
            tree.delete(*tree.get_children())
            for eid, text in sorted(entries.items()):
                preview = text[:60].replace("\n", " ") if text else "(rỗng)"
                tree.insert("", "end", values=(eid, preview))
            self.append_log("Hệ thống", f"Đã nhập JSON: {path} ({len(entries)} mục)")
            messagebox.showinfo("Thành công", f"Đã nhập:\n{path}\n{len(entries)} mục\nNhớ bấm LƯU & NÉN LẠI để ghi vào file!")
        except Exception as e:
            messagebox.showerror("Lỗi", str(e))

    # ========================================================================
    # EVE.EMG EDITOR — Xem & sửa index sự kiện
    # ========================================================================
    def _edit_eve_data(self):
        """Mở editor cho Eve.emg: parse -> xem/sửa tên + vị trí -> đóng gói lại."""
        scan_dir = self._get_scan_dir()
        eve_lz4 = scan_dir / "Eve.emg.lz4"
        eve_raw = scan_dir / "Eve.emg"
        if eve_lz4.exists():
            fp = eve_lz4
        elif eve_raw.exists():
            fp = eve_raw
        else:
            messagebox.showerror("Lỗi", "Không tìm thấy Eve.emg hoặc Eve.emg.lz4!")
            return

        self.compdata_status.config(text="Đang parse Eve.emg...", fg="#005A9E")
        self.append_log("Hệ thống", f"Parse Eve.emg: {fp.name}")

        def worker():
            if lz4_packed is None:
                self.after(0, lambda: messagebox.showerror("Lỗi", "Module LZ4 chưa được cài.\nChạy: pip install lz4"))
                self.after(0, lambda: self.compdata_status.config(text="Lỗi: thiếu module lz4", fg="#D13438"))
                return
            try:
                data = fp.read_bytes()
                if fp.suffix == ".lz4":
                    if lz4_packed.is_lz4_packed(data):
                        data = lz4_packed.decompress(data)
                entries = dat_packer.unpack_eve_data(data)
                self.after(0, lambda: self._open_eve_editor(fp, entries, data))
            except Exception as e:
                self.after(0, lambda: self.compdata_status.config(text=f"Lỗi: {e}", fg="#D13438"))
                self.after(0, lambda: messagebox.showerror("Lỗi", str(e)))

        threading.Thread(target=worker, daemon=True).start()

    def _open_eve_editor(self, fp: Path, entries: list, raw_data: bytes):
        """Mở cửa sổ editor Eve.emg — xem/sửa index + xem hex từng entry."""
        win = tk.Toplevel(self)
        win.title(f"Eve.emg Editor — {len(entries)} sự kiện")
        win.geometry("1100x750")

        # Top bar
        top = tk.Frame(win)
        top.pack(fill="x", padx=8, pady=4)
        ttk.Label(top, text=f"File: {fp.name}  |  {len(entries)} sự kiện  |  Size: {human_size(len(raw_data))}",
                  font=self.font_bold).pack(side="left")

        btn_f = tk.Frame(top)
        btn_f.pack(side="right")
        ttk.Button(btn_f, text=">> LƯU & NÉN LẠI <<", style="Success.TButton",
                   command=lambda: self._save_eve_data(fp, entries, win)).pack(side="left", padx=4)
        ttk.Button(btn_f, text="Xuất JSON", command=lambda: self._export_eve_json(entries)).pack(side="left", padx=4)
        ttk.Button(btn_f, text="Đóng", command=win.destroy).pack(side="left", padx=4)

        # Search bar
        search_f = tk.Frame(win)
        search_f.pack(fill="x", padx=8, pady=2)
        ttk.Label(search_f, text="Tìm:").pack(side="left")
        search_var = tk.StringVar()
        ttk.Entry(search_f, textvariable=search_var).pack(side="left", fill="x", expand=True, padx=4)

        # Paned: tree (left) + detail (right)
        paned = ttk.Panedwindow(win, orient="horizontal")
        paned.pack(fill="both", expand=True, padx=8, pady=4)

        # Left: tree
        tree_frame = ttk.Frame(paned)
        paned.add(tree_frame, weight=1)

        cols = ("idx", "name", "position", "size")
        tree = ttk.Treeview(tree_frame, columns=cols, show="headings", height=20)
        tree.heading("idx", text="#")
        tree.heading("name", text="Tên .eve")
        tree.heading("position", text="Offset")
        tree.heading("size", text="Size")
        tree.column("idx", width=50, anchor="e")
        tree.column("name", width=150)
        tree.column("position", width=100, anchor="e")
        tree.column("size", width=80, anchor="e")
        tree_sb = ttk.Scrollbar(tree_frame, orient="vertical", command=tree.yview)
        tree.configure(yscrollcommand=tree_sb.set)
        tree_sb.pack(side="right", fill="y")
        tree.pack(fill="both", expand=True)

        for e in entries:
            tree.insert("", "end", values=(e["index"], e["name"], e["position"], e["size"]))

        def do_search():
            q = search_var.get().strip()
            if not q:
                return
            for item in tree.get_children():
                vals = tree.item(item, "values")
                if q in str(vals[1]) or q in str(vals[0]):
                    tree.selection_set(item)
                    tree.see(item)
                    break

        ttk.Button(search_f, text="Tìm", command=do_search).pack(side="left", padx=2)

        # Right: detail panel
        detail_frame = ttk.Frame(paned)
        paned.add(detail_frame, weight=2)

        # Editable fields
        edit_frame = tk.LabelFrame(detail_frame, text="Thông tin entry", font=self.font_bold, padx=8, pady=8)
        edit_frame.pack(fill="x", pady=4)

        tk.Label(edit_frame, text="Tên .eve:").grid(row=0, column=0, sticky="w", pady=2)
        name_var = tk.StringVar()
        name_entry = ttk.Entry(edit_frame, textvariable=name_var, width=25)
        name_entry.grid(row=0, column=1, sticky="w", pady=2, padx=4)

        tk.Label(edit_frame, text="Position:").grid(row=1, column=0, sticky="w", pady=2)
        pos_var = tk.StringVar()
        ttk.Entry(edit_frame, textvariable=pos_var, width=15).grid(row=1, column=1, sticky="w", pady=2, padx=4)

        tk.Label(edit_frame, text="Size:").grid(row=2, column=0, sticky="w", pady=2)
        size_var = tk.StringVar()
        ttk.Entry(edit_frame, textvariable=size_var, width=15).grid(row=2, column=1, sticky="w", pady=2, padx=4)

        ttk.Button(edit_frame, text="Áp dụng thay đổi", style="Primary.TButton",
                   command=lambda: self._eve_apply_edit(entries, tree, name_var, pos_var, size_var)).grid(row=3, column=0, columnspan=2, pady=6)

        # Hex viewer for selected entry
        hex_frame = tk.LabelFrame(detail_frame, text="Hex dump (dữ liệu entry)", font=self.font_bold, padx=8, pady=8)
        hex_frame.pack(fill="both", expand=True, pady=4)

        hex_txt = tk.Text(hex_frame, font=self.font_code, wrap="none",
                         bg="#1E1E1E", fg="#90EE90", state="disabled", height=15)
        hex_sb = ttk.Scrollbar(hex_frame, orient="vertical", command=hex_txt.yview)
        hex_txt.configure(yscrollcommand=hex_sb.set)
        hex_sb.pack(side="right", fill="y")
        hex_txt.pack(fill="both", expand=True)

        def on_tree_select(event):
            sel = tree.selection()
            if not sel:
                return
            vals = tree.item(sel[0], "values")
            idx = int(vals[0])
            entry = entries[idx]
            name_var.set(entry["name"])
            pos_var.set(str(entry["position"]))
            size_var.set(str(entry["size"]))
            # Show hex dump of entry data
            pos = entry["position"]
            sz = entry["size"]
            if pos + sz <= len(raw_data) and sz > 0:
                chunk = raw_data[pos:pos + min(sz, 512)]
            else:
                chunk = b""
            self._hex_nav_tab(hex_txt, chunk, 0)

        tree.bind("<<TreeviewSelect>>", on_tree_select)

        win._eve_entries = entries
        win._eve_tree = tree
        self.compdata_status.config(text=f"Đã nạp {len(entries)} sự kiện Eve.emg", fg="#107C41")

    def _eve_apply_edit(self, entries, tree, name_var, pos_var, size_var):
        """Áp dụng thay đổi từ form vào entries."""
        sel = tree.selection()
        if not sel:
            messagebox.showwarning("Cảnh báo", "Chọn entry trong danh sách trước!")
            return
        vals = tree.item(sel[0], "values")
        idx = int(vals[0])
        try:
            entries[idx]["name"] = name_var.get().strip()[:23]  # max 23 chars
            entries[idx]["position"] = int(pos_var.get())
            entries[idx]["size"] = int(size_var.get())
        except ValueError:
            messagebox.showerror("Lỗi", "Position và Size phải là số nguyên!")
            return
        # Update tree
        tree.item(sel[0], values=(idx, entries[idx]["name"], entries[idx]["position"], entries[idx]["size"]))
        self.append_log("Hệ thống", f"Đã sửa Eve entry [{idx}]: name={entries[idx]['name']}")

    def _save_eve_data(self, fp: Path, entries: list, win: tk.Toplevel):
        """Đóng gói entries thành Eve.emg(.lz4) và ghi đè."""
        if not messagebox.askyesno("Xác nhận",
                f"Lưu file:\n{fp.name}\n\n"
                f"{len(entries)} entry sẽ được đóng gói lại và NÉN LZ4.\n"
                f"File gốc sẽ được backup trước khi lưu."):
            return

        backup_dir = self._get_scan_dir() / "backups"
        backup_dir.mkdir(exist_ok=True)
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        shutil.copy2(fp, backup_dir / f"{fp.name}.{timestamp}.bak")
        self.append_log("Hệ thống", f"Đã backup: {fp.name}")

        def worker():
            if lz4_packed is None:
                self.after(0, lambda: messagebox.showerror("Lỗi", "Module LZ4 chưa được cài.\nChạy: pip install lz4"))
                self.after(0, lambda: self.compdata_status.config(text="Lỗi: thiếu module lz4", fg="#D13438"))
                return
            try:
                import dat_packer, lz4_packed
                packed = dat_packer.pack_eve_data(entries)
                if fp.suffix == ".lz4":
                    comp = lz4_packed.compress(packed, high_compression=True)
                    fp.write_bytes(comp)
                    self.after(0, lambda: self.append_log("Hệ thống",
                        f"Đã lưu Eve.emg (nén): {human_size(len(comp))}"))
                    self.after(0, lambda: messagebox.showinfo("Thành công",
                        f"Đã lưu và nén lại:\n{fp.name}\nEntries: {len(entries)}\nSize: {human_size(len(comp))}"))
                else:
                    fp.write_bytes(packed)
                    self.after(0, lambda: self.append_log("Hệ thống",
                        f"Đã lưu Eve.emg: {human_size(len(packed))}"))
                self.after(0, win.destroy)
                self.after(0, self._load_compdata_files)
            except Exception as e:
                self.after(0, lambda: messagebox.showerror("Lỗi", str(e)))

        threading.Thread(target=worker, daemon=True).start()

    def _export_eve_json(self, entries: list):
        """Xuất Eve entries ra JSON."""
        path = filedialog.asksaveasfilename(
            title="Xuất Eve.json", defaultextension=".json",
            initialdir=str(self._get_scan_dir()),
            filetypes=[("JSON", "*.json"), ("All", "*.*")])
        if not path:
            return
        try:
            with open(path, "w", encoding="utf-8") as f:
                json.dump(entries, f, ensure_ascii=False, indent=1)
            self.append_log("Hệ thống", f"Đã xuất JSON: {path} ({len(entries)} entry)")
            messagebox.showinfo("Thành công", f"Đã xuất:\n{path}\n{len(entries)} entry")
        except Exception as e:
            messagebox.showerror("Lỗi", str(e))

    # ========================================================================
    # GENERIC .DAT VIEWER — Đọc bất kỳ file .dat nào
    # Tự động thử nhiều format: TextData, Talk, raw text, hex
    # ========================================================================
    def _open_any_dat_editor(self):
        """Mở dialog chọn file .dat bất kỳ, rồi tự động phát hiện format và hiển thị."""
        path = filedialog.askopenfilename(
            title="Chọn file .dat để đọc",
            initialdir=str(self._get_scan_dir()),
            filetypes=[("DAT files", "*.dat"), ("LZ4 files", "*.lz4"),
                       ("Data files", "*.dat *.emg *.mmg *.bin"), ("All", "*.*")])
        if not path:
            return
        fp = Path(path)
        if not fp.exists():
            messagebox.showerror("Lỗi", f"Không tìm thấy file:\n{fp}")
            return

        self.compdata_status.config(text=f"Đang đọc {fp.name}...", fg="#005A9E")
        self.append_log("Hệ thống", f"Đọc file: {fp.name} ({human_size(fp.stat().st_size)})")

        def worker():
            if lz4_packed is None:
                self.after(0, lambda: messagebox.showerror("Lỗi", "Module LZ4 chưa được cài.\nChạy: pip install lz4"))
                self.after(0, lambda: self.compdata_status.config(text="Lỗi: thiếu module lz4", fg="#D13438"))
                return
            try:
                data = fp.read_bytes()
                # Nếu là .lz4 thì giải nén
                if fp.suffix == ".lz4":
                    if lz4_packed.is_lz4_packed(data):
                        data = lz4_packed.decompress(data)
                self.after(0, lambda: self._open_dat_viewer(fp, data))
            except Exception as e:
                self.after(0, lambda: self.compdata_status.config(text=f"Lỗi: {e}", fg="#D13438"))
                self.after(0, lambda: messagebox.showerror("Lỗi", str(e)))

        threading.Thread(target=worker, daemon=True).start()

    def _open_dat_viewer(self, fp: Path, data: bytes):
        """Mở cửa sổ viewer/editor cho file .dat bất kỳ — tự phát hiện format."""
        win = tk.Toplevel(self)
        win.title(f"DAT Viewer — {fp.name}")
        win.geometry("1200x750")

        # Top bar
        top = tk.Frame(win)
        top.pack(fill="x", padx=8, pady=4)
        ttk.Label(top, text=f"File: {fp.name}  |  Size: {human_size(len(data))}",
                  font=self.font_bold).pack(side="left")

        # Thông tin format
        info_var = tk.StringVar(value="Đang phân tích format...")
        info_lbl = tk.Label(top, textvariable=info_var, font=self.font_small, fg="#005A9E")
        info_lbl.pack(side="left", padx=10)

        # Paned: left = list, right = detail
        paned = ttk.PanedWindow(win, orient=tk.HORIZONTAL)
        paned.pack(fill="both", expand=True, padx=8, pady=4)

        # Left: entry list
        tree_frame = tk.Frame(paned)
        paned.add(tree_frame, weight=2)
        cols = ("index", "id", "preview")
        tree = ttk.Treeview(tree_frame, columns=cols, show="headings", selectmode="browse")
        tree.heading("index", text="#")
        tree.heading("id", text="ID")
        tree.heading("preview", text="Text (preview)")
        tree.column("index", width=60, anchor="e")
        tree.column("id", width=100, anchor="e")
        tree.column("preview", width=500, anchor="w")
        tree_sb = ttk.Scrollbar(tree_frame, orient="vertical", command=tree.yview)
        tree.configure(yscrollcommand=tree_sb.set)
        tree_sb.pack(side="right", fill="y")
        tree.pack(fill="both", expand=True)

        # Right: detail
        detail_frame = tk.LabelFrame(paned, text="Chi tiết", padx=8, pady=8)
        paned.add(detail_frame, weight=3)

        # Tab control for different views
        nb = ttk.Notebook(detail_frame)
        nb.pack(fill="both", expand=True)

        # Tab 1: Text view
        text_tab = tk.Frame(nb)
        nb.add(text_tab, text="Text")
        tk.Label(text_tab, text="Nội dung text:", font=self.font_small).pack(anchor="w")
        text_widget = tk.Text(text_tab, wrap="word", font=self.font_code, height=10)
        text_sb = ttk.Scrollbar(text_tab, orient="vertical", command=text_widget.yview)
        text_widget.configure(yscrollcommand=text_sb.set)
        text_sb.pack(side="right", fill="y")
        text_widget.pack(fill="both", expand=True)

        # Tab 2: Hex view
        hex_tab = tk.Frame(nb)
        nb.add(hex_tab, text="Hex")
        hex_widget = tk.Text(hex_tab, font=self.font_code, wrap="none",
                             bg="#1E1E1E", fg="#90EE90", state="disabled")
        hex_sb = ttk.Scrollbar(hex_tab, orient="vertical", command=hex_widget.yview)
        hex_widget.configure(yscrollcommand=hex_sb.set)
        hex_sb.pack(side="right", fill="y")
        hex_widget.pack(fill="both", expand=True)

        # Tab 3: Info
        info_tab = tk.Frame(nb)
        nb.add(info_tab, text="Info")
        info_text = tk.Text(info_tab, font=self.font_code, wrap="word", height=15)
        info_text.pack(fill="both", expand=True)

        # Status
        status_lbl = tk.Label(detail_frame, text="", font=self.font_small, fg="#555")
        status_lbl.pack(fill="x", pady=(4, 0))

        # Parse: thử TextData format, Talk format, hoặc raw
        entries = []  # list of [id, text]
        fmt_name = "Không xác định"

        # Thử TextData format: uint32 count + uint32 first_id + [uint16 len + UTF16LE text + uint32 id]
        import struct
        parsed_ok = False
        if len(data) >= 8:
            count = struct.unpack_from('<I', data, 0)[0]
            first_id = struct.unpack_from('<I', data, 4)[0]
            # Heuristic: count hợp lý (1-100000), first_id hợp lý (1-100000)
            if 1 <= count <= 200000 and 1 <= first_id <= 200000:
                try:
                    test_entries = []
                    pos = 8
                    ok = True
                    for _ in range(count):
                        if pos + 6 > len(data):
                            ok = False
                            break
                        text_len = struct.unpack_from('<H', data, pos)[0]
                        pos += 2
                        if text_len % 2 != 0 or pos + text_len + 4 > len(data):
                            ok = False
                            break
                        text = data[pos:pos+text_len].decode('utf-16-le', errors='replace')
                        pos += text_len
                        text_id = struct.unpack_from('<I', data, pos)[0]
                        pos += 4
                        test_entries.append([text_id, text])
                    if ok and len(test_entries) >= count - 1:
                        entries = test_entries
                        fmt_name = f"TextData format (count={count}, first_id={first_id}, parsed={len(entries)})"
                        parsed_ok = True
                except Exception:
                    pass

        # Thử Talk format (dat_packer.unpack_talk_data)
        if not parsed_ok:
            try:
                import dat_packer
                talk_entries = dat_packer.unpack_talk_data(data)
                if talk_entries and isinstance(talk_entries, dict):
                    entries = [[k, v] for k, v in talk_entries.items()]
                    fmt_name = f"Talk format ({len(entries)} entries)"
                    parsed_ok = True
            except Exception:
                pass

        # Nếu không parse được, thử đọc raw UTF-16LE hoặc ASCII
        if not parsed_ok:
            # Thử đọc raw UTF-16LE
            try:
                raw_text = data.decode('utf-16-le', errors='replace')
                if len(raw_text) > 10 and any(c.isprintable() for c in raw_text[:100]):
                    entries = [[0, raw_text[:5000]]]
                    fmt_name = f"Raw UTF-16LE (size={len(data)})"
                    parsed_ok = True
            except Exception:
                pass

        if not parsed_ok:
            # Raw ASCII
            try:
                raw_text = data.decode('ascii', errors='replace')
                entries = [[0, raw_text[:5000]]]
                fmt_name = f"Raw ASCII/binary (size={len(data)})"
            except Exception:
                entries = []
                fmt_name = "Binary (không đọc được text)"

        info_var.set(f"Format: {fmt_name}")

        # Fill tree
        for i, (eid, text) in enumerate(entries):
            preview = text[:80].replace('\n', ' ').replace('\r', '')
            if len(text) > 80:
                preview += "…"
            tree.insert("", tk.END, values=(i, eid, preview))

        # Info tab
        info_lines = [
            f"File: {fp.name}",
            f"Đường dẫn: {fp}",
            f"Kích thước: {human_size(len(data))} ({len(data)} bytes)",
            f"Format phát hiện: {fmt_name}",
            f"Số entries: {len(entries)}",
            "",
            f"8 bytes đầu: {data[:8].hex(' ')}",
            f"16 bytes đầu: {data[:16].hex(' ')}",
            "",
            "=== PHÂN TÍCH HEADER ===",
        ]
        if len(data) >= 8:
            u32_0 = struct.unpack_from('<I', data, 0)[0]
            u32_4 = struct.unpack_from('<I', data, 4)[0]
            info_lines.append(f"  uint32 @0: {u32_0} (0x{u32_0:08x})")
            info_lines.append(f"  uint32 @4: {u32_4} (0x{u32_4:08x})")
        if len(data) >= 4:
            u16_0 = struct.unpack_from('<H', data, 0)[0]
            u16_2 = struct.unpack_from('<H', data, 2)[0]
            info_lines.append(f"  uint16 @0: {u16_0} (0x{u16_0:04x})")
            info_lines.append(f"  uint16 @2: {u16_2} (0x{u16_2:04x})")
        info_text.insert("1.0", "\n".join(info_lines))
        info_text.config(state="disabled")

        # Hex view (first 2048 bytes)
        hex_widget.config(state="normal")
        hex_lines = []
        for offset in range(0, min(len(data), 2048), 16):
            chunk = data[offset:offset+16]
            hex_part = " ".join(f"{b:02x}" for b in chunk)
            ascii_part = "".join(chr(b) if 32 <= b < 127 else "." for b in chunk)
            hex_lines.append(f"{offset:08x}  {hex_part:<48}  {ascii_part}")
        hex_widget.insert("1.0", "\n".join(hex_lines))
        if len(data) > 2048:
            hex_widget.insert("end", f"\n\n... ({human_size(len(data) - 2048)} còn lại)")
        hex_widget.config(state="disabled")

        # Tree select handler
        def on_tree_select(event=None):
            sel = tree.selection()
            if not sel:
                return
            values = tree.item(sel[0], "values")
            idx = int(values[0])
            if idx >= len(entries):
                return
            eid, text = entries[idx]
            text_widget.delete("1.0", tk.END)
            text_widget.insert("1.0", text)
            status_lbl.config(text=f"Entry #{idx} | ID={eid} | {len(text)} chars")

        tree.bind("<<TreeviewSelect>>", on_tree_select)

        # Auto-select first
        children = tree.get_children()
        if children:
            tree.selection_set(children[0])
            on_tree_select()

        # Search bar
        search_frame = tk.Frame(win)
        search_frame.pack(fill="x", padx=8, pady=(0, 4))
        tk.Label(search_frame, text="Tìm:", font=self.font_small).pack(side="left")
        search_var = tk.StringVar()
        search_entry = ttk.Entry(search_frame, textvariable=search_var, width=30)
        search_entry.pack(side="left", padx=4)

        def do_search(*args):
            q = search_var.get().strip().lower()
            tree.selection_remove(tree.selection())
            if not q:
                return
            for item in tree.get_children():
                vals = tree.item(item, "values")
                idx = int(vals[0])
                text = entries[idx][1]
                if q in text.lower() or q == str(entries[idx][0]):
                    tree.see(item)
                    tree.selection_add(item)
                    tree.focus(item)
                    return

        search_var.trace_add("write", do_search)

        self.compdata_status.config(text=f"Đã nạp {fp.name} — {len(entries)} entries", fg="#107C41")


    def _edit_textdata(self):
        """Mở editor cho TextData_C.dat: parse -> sửa text -> đóng gói lại."""
        fp = self._get_scan_dir() / "TextData_C.dat"
        if not fp.exists():
            messagebox.showerror("Lỗi", f"Không tìm thấy TextData_C.dat!\n{fp}")
            return

        self.compdata_status.config(text="Đang parse TextData_C.dat...", fg="#005A9E")
        self.append_log("Hệ thống", f"Parse TextData_C.dat: {fp.name}")

        def worker():
            if lz4_packed is None:
                self.after(0, lambda: messagebox.showerror("Lỗi", "Module LZ4 chưa được cài.\nChạy: pip install lz4"))
                self.after(0, lambda: self.compdata_status.config(text="Lỗi: thiếu module lz4", fg="#D13438"))
                return
            try:
                entries = self._parse_textdata(fp)
                self.after(0, lambda: self._open_textdata_editor(fp, entries))
            except Exception as e:
                self.after(0, lambda: self.compdata_status.config(text=f"Lỗi: {e}", fg="#D13438"))
                self.after(0, lambda: messagebox.showerror("Lỗi", str(e)))

        threading.Thread(target=worker, daemon=True).start()

    @staticmethod
    def _parse_textdata(fp: Path):
        """Parse TextData_C.dat -> list of (text_id, text)."""
        import struct
        data = fp.read_bytes()
        if len(data) < 8:
            raise ValueError("File quá nhỏ")
        count = struct.unpack_from('<I', data, 0)[0]
        first_id = struct.unpack_from('<I', data, 4)[0]
        entries = []
        pos = 8
        while pos + 6 <= len(data):
            text_len = struct.unpack_from('<H', data, pos)[0]
            pos += 2
            if pos + text_len + 4 > len(data):
                break
            text = data[pos:pos+text_len].decode('utf-16-le', errors='replace')
            pos += text_len
            text_id = struct.unpack_from('<I', data, pos)[0]
            pos += 4
            entries.append([text_id, text])
        return entries

    @staticmethod
    def _pack_textdata(entries):
        """Pack list of (text_id, text) -> binary TextData_C.dat."""
        import struct
        buf = bytearray()
        buf += struct.pack('<I', len(entries))
        buf += struct.pack('<I', 10001)  # first_id
        for text_id, text in entries:
            text_bytes = text.encode('utf-16-le')
            buf += struct.pack('<H', len(text_bytes))
            buf += text_bytes
            buf += struct.pack('<I', text_id)
        return bytes(buf)

    def _open_textdata_editor(self, fp: Path, entries: list):
        """Mở cửa sổ editor TextData_C.dat với bảng liệt kê + chỉnh sửa text."""
        win = tk.Toplevel(self)
        win.title(f"TextData_C.dat Editor — {len(entries)} mục")
        win.geometry("1200x750")

        # Top bar
        top = tk.Frame(win)
        top.pack(fill="x", padx=8, pady=4)
        ttk.Label(top, text=f"File: {fp.name}  |  {len(entries)} mục text (UI/nhiệm vụ/event)",
                  font=self.font_bold).pack(side="left")

        btn_f = tk.Frame(top)
        btn_f.pack(side="right")
        ttk.Button(btn_f, text="💾 Lưu", style="Primary.TButton",
                   command=lambda: self._save_textdata(fp, entries, win)).pack(side="left", padx=3)
        ttk.Button(btn_f, text="Xuất JSON", command=lambda: self._export_textdata_json(entries)).pack(side="left", padx=3)
        ttk.Button(btn_f, text="Nhập JSON", command=lambda: self._import_textdata_json(entries, tree, win)).pack(side="left", padx=3)

        # Search bar
        search_f = tk.Frame(win)
        search_f.pack(fill="x", padx=8, pady=(0, 4))
        tk.Label(search_f, text="Tìm:", font=self.font_small).pack(side="left")
        search_var = tk.StringVar()
        search_entry = ttk.Entry(search_f, textvariable=search_var, width=30)
        search_entry.pack(side="left", padx=4)

        # Paned: left = list, right = editor
        paned = ttk.PanedWindow(win, orient=tk.HORIZONTAL)
        paned.pack(fill="both", expand=True, padx=8, pady=4)

        # Left: entry list
        tree_frame = tk.Frame(paned)
        paned.add(tree_frame, weight=2)
        cols = ("index", "id", "preview")
        tree = ttk.Treeview(tree_frame, columns=cols, show="headings", selectmode="browse")
        tree.heading("index", text="#")
        tree.heading("id", text="ID")
        tree.heading("preview", text="Text (preview)")
        tree.column("index", width=50, anchor="e")
        tree.column("id", width=80, anchor="e")
        tree.column("preview", width=450, anchor="w")
        tree_sb = ttk.Scrollbar(tree_frame, orient="vertical", command=tree.yview)
        tree.configure(yscrollcommand=tree_sb.set)
        tree_sb.pack(side="right", fill="y")
        tree.pack(fill="both", expand=True)

        # Fill entries
        for i, (eid, text) in enumerate(entries):
            preview = text[:60].replace('\n', ' ').replace('\r', '')
            if len(text) > 60:
                preview += "…"
            tree.insert("", tk.END, values=(i, eid, preview))

        # Right: editor
        editor_frame = tk.LabelFrame(paned, text="Chỉnh sửa", padx=8, pady=8)
        paned.add(editor_frame, weight=3)

        id_frame = tk.Frame(editor_frame)
        id_frame.pack(fill="x", pady=(0, 5))
        tk.Label(id_frame, text="Text ID:", font=self.font_small).pack(side="left")
        id_var = tk.StringVar()
        id_entry = ttk.Entry(id_frame, textvariable=id_var, width=12)
        id_entry.pack(side="left", padx=5)

        tk.Label(editor_frame, text="Nội dung text:", font=self.font_small).pack(anchor="w")
        text_widget = tk.Text(editor_frame, wrap="word", font=self.font_code, undo=True, height=12)
        text_sb = ttk.Scrollbar(editor_frame, orient="vertical", command=text_widget.yview)
        text_widget.configure(yscrollcommand=text_sb.set)
        text_sb.pack(side="right", fill="y")
        text_widget.pack(fill="both", expand=True)

        btn_row = tk.Frame(editor_frame)
        btn_row.pack(fill="x", pady=5)
        ttk.Button(btn_row, text="✅ Áp dụng", style="Primary.TButton", command=on_text_change).pack(side="left", padx=4)
        info_lbl = tk.Label(btn_row, text="(chọn entry để sửa)", font=self.font_small, fg="#555")
        info_lbl.pack(side="left", padx=8)

        def on_tree_select(event=None):
            sel = tree.selection()
            if not sel:
                return
            values = tree.item(sel[0], "values")
            idx = int(values[0])
            if idx < 0 or idx >= len(entries):
                return
            eid, text = entries[idx]
            id_var.set(str(eid))
            text_widget.delete("1.0", tk.END)
            text_widget.insert("1.0", text)
            info_lbl.config(text=f"Entry #{idx} | ID={eid} | {len(text)} chars")

        def on_text_change(event=None):
            sel = tree.selection()
            if not sel:
                return
            values = tree.item(sel[0], "values")
            idx = int(values[0])
            if idx < 0 or idx >= len(entries):
                return
            # Validate ID
            try:
                eid = int(id_var.get())
                if eid <= 0:
                    raise ValueError
            except ValueError:
                info_lbl.config(text="⚠ ID phải là số nguyên dương!", fg="#D13438")
                return
            new_text = text_widget.get("1.0", tk.END + "-1c")
            entries[idx][0] = eid
            entries[idx][1] = new_text
            preview = new_text[:60].replace('\n', ' ').replace('\r', '')
            if len(new_text) > 60:
                preview += "…"
            tree.item(sel[0], values=(idx, eid, preview))
            info_lbl.config(text=f"Entry #{idx} | ID={eid} | {len(new_text)} chars | ✅", fg="#107C41")

        def do_search(*args):
            q = search_var.get().strip().lower()
            tree.selection_remove(tree.selection())
            if not q:
                return
            for item in tree.get_children():
                vals = tree.item(item, "values")
                idx = int(vals[0])
                text = entries[idx][1]
                if q in text.lower() or q == str(entries[idx][0]):
                    tree.see(item)
                    tree.selection_add(item)
                    tree.focus(item)
                    return

        def on_add():
            max_id = max(e[0] for e in entries) if entries else 10001
            new_id = simpledialog.askinteger("Thêm Entry", "Text ID:", initialvalue=max_id + 1, parent=win)
            if new_id is None or new_id <= 0:
                return
            new_text = simpledialog.askstring("Thêm Entry", "Text:", parent=win)
            if new_text is None:
                return
            entries.append([new_id, new_text])
            preview = new_text[:60].replace('\n', ' ').replace('\r', '')
            if len(new_text) > 60:
                preview += "…"
            idx = len(entries) - 1
            tree.insert("", tk.END, values=(idx, new_id, preview))
            tree.see(tree.get_children()[-1])
            self.append_log("Hệ thống", f"TextData: thêm entry ID={new_id}")

        def on_delete():
            sel = tree.selection()
            if not sel:
                return
            values = tree.item(sel[0], "values")
            idx = int(values[0])
            if not messagebox.askyesno("Xác nhận", f"Xóa entry #{idx} (ID={entries[idx][0]})?"):
                return
            del entries[idx]
            # Rebuild tree indices
            tree.delete(*tree.get_children())
            for i, (eid, text) in enumerate(entries):
                preview = text[:60].replace('\n', ' ').replace('\r', '')
                if len(text) > 60:
                    preview += "…"
                tree.insert("", tk.END, values=(i, eid, preview))
            self.append_log("Hệ thống", f"TextData: xóa entry #{idx}")

        # Add/Delete buttons in search bar
        ttk.Button(search_f, text="➕ Thêm", command=on_add).pack(side="left", padx=4)
        ttk.Button(search_f, text="🗑️ Xóa", style="Danger.TButton", command=on_delete).pack(side="left", padx=4)

        tree.bind("<<TreeviewSelect>>", on_tree_select)
        search_var.trace_add("write", do_search)

        # Auto-select first entry
        children = tree.get_children()
        if children:
            tree.selection_set(children[0])
            on_tree_select()

        self.compdata_status.config(text=f"Đã nạp {len(entries)} mục TextData_C.dat", fg="#107C41")

    def _save_textdata(self, fp: Path, entries: list, win: tk.Toplevel):
        """Đóng gói entries thành TextData_C.dat và ghi đè."""
        if not messagebox.askyesno("Xác nhận",
                f"Lưu file:\n{fp.name}\n\n"
                f"{len(entries)} mục sẽ được đóng gói lại.\n"
                f"File gốc sẽ được backup trước khi lưu."):
            return

        # Backup
        backup_dir = DATA_DIR / "backups"
        backup_dir.mkdir(exist_ok=True)
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        shutil.copy2(fp, backup_dir / f"{fp.name}.{timestamp}.bak")
        self.append_log("Hệ thống", f"Đã backup: {fp.name}")

        def worker():
            if lz4_packed is None:
                self.after(0, lambda: messagebox.showerror("Lỗi", "Module LZ4 chưa được cài.\nChạy: pip install lz4"))
                self.after(0, lambda: self.compdata_status.config(text="Lỗi: thiếu module lz4", fg="#D13438"))
                return
            try:
                packed = self._pack_textdata(entries)
                fp.write_bytes(packed)
                self.after(0, lambda: self.append_log("Hệ thống",
                    f"Đã lưu TextData_C.dat: {human_size(len(packed))}, {len(entries)} mục"))
                self.after(0, lambda: messagebox.showinfo("Thành công",
                    f"Đã lưu:\n{fp.name}\n{len(entries)} mục\nKích thước: {human_size(len(packed))}"))
                self.after(0, win.destroy)
            except Exception as e:
                self.after(0, lambda: messagebox.showerror("Lỗi", str(e)))

        threading.Thread(target=worker, daemon=True).start()

    def _export_textdata_json(self, entries: list):
        """Xuất entries ra file JSON."""
        path = filedialog.asksaveasfilename(
            title="Xuất TextData_C.json",
            defaultextension=".json",
            initialdir=str(self._get_scan_dir()),
            filetypes=[("JSON", "*.json"), ("All", "*.*")])
        if not path:
            return
        try:
            export = [{"id": eid, "text": text} for eid, text in entries]
            with open(path, "w", encoding="utf-8") as f:
                json.dump(export, f, ensure_ascii=False, indent=1)
            self.append_log("Hệ thống", f"Đã xuất JSON: {path} ({len(entries)} entry)")
            messagebox.showinfo("Thành công", f"Đã xuất:\n{path}\n{len(entries)} entry")
        except Exception as e:
            messagebox.showerror("Lỗi", str(e))

    def _import_textdata_json(self, entries: list, tree: ttk.Treeview, win: tk.Toplevel):
        """Nhập entries từ file JSON."""
        path = filedialog.askopenfilename(
            title="Nhập TextData_C.json",
            initialdir=str(self._get_scan_dir()),
            filetypes=[("JSON", "*.json"), ("All", "*.*")])
        if not path:
            return
        try:
            with open(path, "r", encoding="utf-8") as f:
                data = json.load(f)
            entries.clear()
            entries.extend([[d["id"], d["text"]] for d in data])
            # Rebuild tree
            tree.delete(*tree.get_children())
            for i, (eid, text) in enumerate(entries):
                preview = text[:60].replace('\n', ' ').replace('\r', '')
                if len(text) > 60:
                    preview += "…"
                tree.insert("", tk.END, values=(i, eid, preview))
            self.append_log("Hệ thống", f"Đã nhập JSON: {path} ({len(entries)} entry)")
            messagebox.showinfo("Thành công", f"Đã nhập:\n{path}\n{len(entries)} entry")
        except Exception as e:
            messagebox.showerror("Lỗi", str(e))


    def _edit_ground_data(self):
        """Mở editor cho Ground.mmg: parse index -> xem/sửa -> đóng gói lại."""
        scan_dir = self._get_scan_dir()
        gnd_lz4 = scan_dir / "Ground.mmg.lz4"
        gnd_raw = scan_dir / "Ground.mmg"
        if gnd_lz4.exists():
            fp = gnd_lz4
        elif gnd_raw.exists():
            fp = gnd_raw
        else:
            messagebox.showerror("Lỗi", "Không tìm thấy Ground.mmg hoặc Ground.mmg.lz4!")
            return

        self.compdata_status.config(text="Đang parse Ground.mmg (27MB, có thể mất vài giây)...", fg="#005A9E")
        self.append_log("Hệ thống", f"Parse Ground.mmg: {fp.name}")

        def worker():
            if lz4_packed is None:
                self.after(0, lambda: messagebox.showerror("Lỗi", "Module LZ4 chưa được cài.\nChạy: pip install lz4"))
                self.after(0, lambda: self.compdata_status.config(text="Lỗi: thiếu module lz4", fg="#D13438"))
                return
            try:
                data = fp.read_bytes()
                if fp.suffix == ".lz4":
                    if lz4_packed.is_lz4_packed(data):
                        data = lz4_packed.decompress(data)
                info = dat_packer.unpack_ground_data(data)
                self.after(0, lambda: self._open_ground_editor(fp, info, data))
            except Exception as e:
                self.after(0, lambda: self.compdata_status.config(text=f"Lỗi: {e}", fg="#D13438"))
                self.after(0, lambda: messagebox.showerror("Lỗi", str(e)))

        threading.Thread(target=worker, daemon=True).start()

    def _open_ground_editor(self, fp: Path, info: dict, raw_data: bytes):
        """Mở cửa sổ editor Ground.mmg — xem index + hex từng map."""
        maps = info["maps"]
        win = tk.Toplevel(self)
        win.title(f"Ground.mmg Editor — {len(maps)} bản đồ")
        win.geometry("1100x750")

        top = tk.Frame(win)
        top.pack(fill="x", padx=8, pady=4)
        ttk.Label(top,
                  text=f"File: {fp.name}  |  {len(maps)} bản đồ  |  Size: {human_size(len(raw_data))}\n"
                       f"Width: {info['width']}  |  Height: {info['height']}  |  Index offset: {info['index_offset']}",
                  font=self.font_bold, justify="left").pack(side="left")

        btn_f = tk.Frame(top)
        btn_f.pack(side="right")
        ttk.Button(btn_f, text="Đóng", command=win.destroy).pack(side="left", padx=4)

        # Search bar
        search_f = tk.Frame(win)
        search_f.pack(fill="x", padx=8, pady=2)
        ttk.Label(search_f, text="Tìm map:").pack(side="left")
        search_var = tk.StringVar()
        ttk.Entry(search_f, textvariable=search_var).pack(side="left", fill="x", expand=True, padx=4)
        def do_search():
            q = search_var.get().strip()
            if not q:
                return
            for item in tree.get_children():
                vals = tree.item(item, "values")
                if q in str(vals[1]):
                    tree.selection_set(item)
                    tree.see(item)
                    break
        ttk.Button(search_f, text="Tìm", command=do_search).pack(side="left", padx=2)

        # Paned: tree + hex
        paned = ttk.Panedwindow(win, orient="horizontal")
        paned.pack(fill="both", expand=True, padx=8, pady=4)

        tree_frame = ttk.Frame(paned)
        paned.add(tree_frame, weight=1)

        cols = ("idx", "name", "position", "size")
        tree = ttk.Treeview(tree_frame, columns=cols, show="headings", height=20)
        tree.heading("idx", text="#")
        tree.heading("name", text="Tên map")
        tree.heading("position", text="Offset")
        tree.heading("size", text="Size")
        tree.column("idx", width=50, anchor="e")
        tree.column("name", width=120)
        tree.column("position", width=120, anchor="e")
        tree.column("size", width=80, anchor="e")
        tree_sb = ttk.Scrollbar(tree_frame, orient="vertical", command=tree.yview)
        tree.configure(yscrollcommand=tree_sb.set)
        tree_sb.pack(side="right", fill="y")
        tree.pack(fill="both", expand=True)

        for i, m in enumerate(maps):
            tree.insert("", "end", values=(i, m["name"], m["position"], m["size"]))

        # Right: hex viewer
        detail_frame = ttk.Frame(paned)
        paned.add(detail_frame, weight=2)

        hex_frame = tk.LabelFrame(detail_frame, text="Hex dump (dữ liệu map)", font=self.font_bold, padx=8, pady=8)
        hex_frame.pack(fill="both", expand=True, pady=4)

        hex_txt = tk.Text(hex_frame, font=self.font_code, wrap="none",
                         bg="#1E1E1E", fg="#90EE90", state="disabled")
        hex_sb = ttk.Scrollbar(hex_frame, orient="vertical", command=hex_txt.yview)
        hex_txt.configure(yscrollcommand=hex_sb.set)
        hex_sb.pack(side="right", fill="y")
        hex_txt.pack(fill="both", expand=True)

        def on_tree_select(event):
            sel = tree.selection()
            if not sel:
                return
            vals = tree.item(sel[0], "values")
            idx = int(vals[0])
            m = maps[idx]
            pos = m["position"]
            sz = m["size"]
            if pos + sz <= len(raw_data) and sz > 0:
                chunk = raw_data[pos:pos + min(sz, 512)]
            else:
                chunk = b""
            self._hex_nav_tab(hex_txt, chunk, 0)

        tree.bind("<<TreeviewSelect>>", on_tree_select)

        # Note
        tk.Label(detail_frame, text="⚠ Ground.mmg chứa dữ liệu bản đồ nhị phân (tile grid).\n"
                "Chỉ xem hex, không nên sửa trực tiếp trừ khi biết rõ format.",
                font=self.font_small, fg="#D13438", justify="left").pack(anchor="w")

        self.compdata_status.config(text=f"Đã nạp {len(maps)} bản đồ Ground.mmg", fg="#107C41")

    # Backward-compat alias
    def _view_eve_index(self):
        self._edit_eve_data()

    # ========================================================================
    # LOG & POLLING
    # ========================================================================
    def append_log(self, channel: str, msg: str):
        self.log_queue.put((channel, msg))

    def _process_log_queue(self):
        try:
            while True:
                channel, msg = self.log_queue.get_nowait()
                timestamp = datetime.now().strftime("%H:%M:%S")
                line = f"[{timestamp}] [{channel}] {msg}\n"
                self.log_text.insert("end", line)
                self.log_text.see("end")
        except queue.Empty:
            pass
        self.after(300, self._process_log_queue)

    def _clear_logs(self):
        self.log_text.delete("1.0", "end")

    def _periodic_check(self):
        """Kiểm tra trạng thái port định kỳ."""
        def worker():
            cdn_port = int(self.cfg.get("cdn_port", 443))
            game_port = int(self.cfg.get("game_port", 6613))
            mysql_port = int(self.cfg.get("mysql_port", 3307))

            cdn_ok = check_port_listening(cdn_port)
            game_ok = check_port_listening(game_port)
            mysql_ok = check_port_listening(mysql_port)

            self.after(0, lambda: self.lbl_cdn.config(
                text=f"CDN: {'ON' if cdn_ok else 'OFF'}",
                fg="#107C41" if cdn_ok else "#D13438"
            ))
            self.after(0, lambda: self.lbl_game.config(
                text=f"Game: {'ON' if game_ok else 'OFF'}",
                fg="#107C41" if game_ok else "#D13438"
            ))
            self.after(0, lambda: self.lbl_mysql.config(
                text=f"MySQL: {'ON' if mysql_ok else 'OFF'}",
                fg="#107C41" if mysql_ok else "#D13438"
            ))

        threading.Thread(target=worker, daemon=True).start()
        self.after(5000, self._periodic_check)


# ============================================================================
# Main
# ============================================================================
def main():
    app = TSMAdminApp()
    app.mainloop()


if __name__ == "__main__":
    main()
