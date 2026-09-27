#!/usr/bin/env python3
"""
lua_crypt_gui.py — Giao dien truc quan giai ma & ma hoa Lua cho TS Mobile Online
================================================================================
"""

import hashlib
import os
import re
import sys
import tkinter as tk
from tkinter import ttk, filedialog, messagebox
from pathlib import Path

# Import logic tu lua_crypt.py
from lua_crypt import (
    SCRIPT_DIR,
    CDN_DATA_DIR,
    CDN_MIRROR_DIRS,
    TSMLUA_ZIP,
    decrypt_file,
    encrypt_file,
    show_info,
    resolve_relative_path,
    find_reference_plain,
    find_reference_encrypted,
    compute_keystream,
    apply_keystream,
    aes_decrypt,
    aes_encrypt,
)


class LuaCryptGUI(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("TS Mobile — Lua Decrypt & Encrypt Tool")
        self.geometry("820x680")
        self.minsize(760, 580)

        # Style & Font
        self._setup_styles()

        # Layout
        self._create_header()
        self._create_tabs()
        self._create_log_area()

        # Default Network.lua path
        default_net = CDN_DATA_DIR / "Logic" / "Network.lua"
        if default_net.exists():
            self.fast_input_var.set(str(default_net.resolve()))
            self.dec_input_var.set(str(default_net.resolve()))
            self.dec_output_var.set(str(default_net.with_name("Network.plain.lua").resolve()))
            self.enc_output_var.set(str(default_net.resolve()))
            self._load_current_server_info()

    def _setup_styles(self):
        # Configure ttk style for clean modern look
        style = ttk.Style(self)
        try:
            style.theme_use("clam")
        except Exception:
            pass

        self.font_main = ("Segoe UI", 10)
        self.font_bold = ("Segoe UI", 10, "bold")
        self.font_title = ("Segoe UI", 12, "bold")
        self.font_code = ("Consolas", 10)

        # Colors
        self.color_primary = "#0078D4"
        self.color_bg = "#F3F3F3"
        self.configure(bg=self.color_bg)

        style.configure("TLabel", font=self.font_main, background=self.color_bg)
        style.configure("TButton", font=self.font_main, padding=5)
        style.configure("Primary.TButton", font=self.font_bold, foreground="white", background="#0078D4")
        style.map("Primary.TButton", background=[("active", "#005A9E")])
        style.configure("Success.TButton", font=self.font_bold, foreground="white", background="#107C41")
        style.map("Success.TButton", background=[("active", "#0B5A2F")])
        style.configure("TNotebook", background=self.color_bg, padding=5)
        style.configure("TNotebook.Tab", font=self.font_bold, padding=(12, 6))

    def _create_header(self):
        header_frame = tk.Frame(self, bg="#0078D4", height=60)
        header_frame.pack(fill="x", side="top")

        title_lbl = tk.Label(
            header_frame,
            text="TS Mobile Online — Lua Crypt Tool",
            font=("Segoe UI", 14, "bold"),
            fg="white",
            bg="#0078D4",
        )
        title_lbl.pack(side="left", padx=16, pady=10)

        sub_lbl = tk.Label(
            header_frame,
            text="Công cụ Giải mã & Mã hóa file .lua (Network.lua)",
            font=("Segoe UI", 9),
            fg="#E0E0E0",
            bg="#0078D4",
        )
        sub_lbl.pack(side="left", padx=8, pady=(14, 10))

    def _create_tabs(self):
        self.notebook = ttk.Notebook(self)
        self.notebook.pack(fill="both", expand=True, padx=12, pady=(10, 5))

        # Tab 1: Đổi nhanh Server IP
        self.tab_fast = ttk.Frame(self.notebook, padding=12)
        self.notebook.add(self.tab_fast, text="⚡ Đổi nhanh IP Server")
        self._init_tab_fast()

        # Tab 2: Trình soạn thảo & Mã hóa
        self.tab_editor = ttk.Frame(self.notebook, padding=12)
        self.notebook.add(self.tab_editor, text="📝 Trình soạn thảo Lua")
        self._init_tab_editor()

        # Tab 3: Giải mã (Decrypt)
        self.tab_dec = ttk.Frame(self.notebook, padding=12)
        self.notebook.add(self.tab_dec, text="🔓 Giải mã (Decrypt)")
        self._init_tab_dec()

        # Tab 4: Mã hóa (Encrypt)
        self.tab_enc = ttk.Frame(self.notebook, padding=12)
        self.notebook.add(self.tab_enc, text="🔒 Mã hóa (Encrypt)")
        self._init_tab_enc()

    def _create_log_area(self):
        log_frame = tk.LabelFrame(self, text="Nhật ký hoạt động (Logs)", font=self.font_bold, bg=self.color_bg)
        log_frame.pack(fill="x", side="bottom", padx=12, pady=(0, 8))

        self.log_text = tk.Text(log_frame, height=5, font=self.font_code, wrap="word", bg="#1E1E1E", fg="#D4D4D4")
        self.log_text.pack(side="left", fill="both", expand=True, padx=4, pady=4)

        scrollbar = ttk.Scrollbar(log_frame, orient="vertical", command=self.log_text.yview)
        scrollbar.pack(side="right", fill="y")
        self.log_text.config(yscrollcommand=scrollbar.set)

    def log(self, message: str, level: str = "INFO"):
        prefix = f"[{level}] " if level else ""
        self.log_text.insert("end", f"{prefix}{message}\n")
        self.log_text.see("end")

    # ============================================================
    # Tab 1: Đổi nhanh IP Server
    # ============================================================
    def _init_tab_fast(self):
        # File selector
        file_frame = tk.LabelFrame(self.tab_fast, text="File Network.lua cần cấu hình", font=self.font_bold, bg=self.color_bg)
        file_frame.pack(fill="x", pady=(0, 10))

        self.fast_input_var = tk.StringVar()
        entry = ttk.Entry(file_frame, textvariable=self.fast_input_var, font=self.font_main)
        entry.pack(side="left", fill="x", expand=True, padx=8, pady=8)

        browse_btn = ttk.Button(file_frame, text="Chọn file...", command=self._browse_fast_file)
        browse_btn.pack(side="right", padx=8, pady=8)

        # Server Settings Frame
        cfg_frame = tk.LabelFrame(self.tab_fast, text="Thông tin kết nối Server", font=self.font_bold, bg=self.color_bg)
        cfg_frame.pack(fill="x", pady=(0, 10))

        # Host
        tk.Label(cfg_frame, text="Địa chỉ IP / Host:", font=self.font_main, bg=self.color_bg).grid(row=0, column=0, sticky="w", padx=12, pady=8)
        self.fast_host_var = tk.StringVar(value="192.168.0.157")
        host_entry = ttk.Entry(cfg_frame, textvariable=self.fast_host_var, font=self.font_main, width=28)
        host_entry.grid(row=0, column=1, sticky="w", padx=8, pady=8)

        # Port
        tk.Label(cfg_frame, text="Cổng (Port):", font=self.font_main, bg=self.color_bg).grid(row=1, column=0, sticky="w", padx=12, pady=8)
        self.fast_port_var = tk.StringVar(value="6613")
        port_entry = ttk.Entry(cfg_frame, textvariable=self.fast_port_var, font=self.font_main, width=12)
        port_entry.grid(row=1, column=1, sticky="w", padx=8, pady=8)

        # Server Name
        tk.Label(cfg_frame, text="Tên Server:", font=self.font_main, bg=self.color_bg).grid(row=2, column=0, sticky="w", padx=12, pady=8)
        self.fast_name_var = tk.StringVar(value="Dev Server")
        name_entry = ttk.Entry(cfg_frame, textvariable=self.fast_name_var, font=self.font_main, width=28)
        name_entry.grid(row=2, column=1, sticky="w", padx=8, pady=8)

        # Sync options
        self.fast_sync_mirror_var = tk.BooleanVar(value=True)
        sync_cb = tk.Checkbutton(
            self.tab_fast,
            text="Tự động đồng bộ sang CDN Mirror (cdn/mirror/tsr/.../Network.lua) & cập nhật LuaScripts.dat",
            variable=self.fast_sync_mirror_var,
            font=self.font_main,
            bg=self.color_bg,
        )
        sync_cb.pack(anchor="w", pady=(0, 15))

        # Buttons
        btn_frame = tk.Frame(self.tab_fast, bg=self.color_bg)
        btn_frame.pack(fill="x", pady=5)

        read_btn = ttk.Button(btn_frame, text="🔄 Đọc cấu hình từ file", command=self._load_current_server_info)
        read_btn.pack(side="left", padx=4)

        apply_btn = ttk.Button(btn_frame, text="🚀 Cập nhật & Mã hóa file ngay", style="Primary.TButton", command=self._apply_server_settings)
        apply_btn.pack(side="right", padx=4)

    def _browse_fast_file(self):
        f = filedialog.askopenfilename(
            title="Chọn file Network.lua mã hóa",
            filetypes=[("Lua files", "*.lua"), ("All files", "*.*")],
            initialdir=str(CDN_DATA_DIR / "Logic") if (CDN_DATA_DIR / "Logic").exists() else ".",
        )
        if f:
            self.fast_input_var.set(f)
            self._load_current_server_info()

    def _load_current_server_info(self):
        p = Path(self.fast_input_var.get())
        if not p.exists():
            return
        try:
            # Giai ma trong bo nho bang AES-128-CBC
            enc_data = p.read_bytes()
            try:
                dec = aes_decrypt(enc_data).decode("utf-8", errors="replace")
            except Exception:
                dec = enc_data.decode("utf-8", errors="replace")

            # Parse line 161
            # this.servers = {{id=1, host="192.168.0.157", port=6613, name="Dev Server", lightKind=0}};
            m = re.search(r'this\.servers\s*=\s*\{\{id=1,\s*host="([^"]+)",\s*port=(\d+)(?:,\s*name="([^"]+)")?', dec)
            if m:
                self.fast_host_var.set(m.group(1))
                self.fast_port_var.set(m.group(2))
                if m.group(3):
                    self.fast_name_var.set(m.group(3))
                self.log(f"Đã đọc cấu hình Network.lua (AES-128): Host={m.group(1)}, Port={m.group(2)}")
        except Exception as e:
            self.log(f"Không thể phân tích file: {e}", "WARN")

    def _apply_server_settings(self):
        p = Path(self.fast_input_var.get())
        if not p.exists():
            messagebox.showerror("Lỗi", f"Không tìm thấy file: {p}")
            return

        new_host = self.fast_host_var.get().strip()
        new_port_str = self.fast_port_var.get().strip()
        new_name = self.fast_name_var.get().strip()

        if not new_host:
            messagebox.showerror("Lỗi", "Vui lòng nhập IP / Host!")
            return

        try:
            new_port = int(new_port_str)
        except ValueError:
            messagebox.showerror("Lỗi", "Port phải là số nguyên hợp lệ!")
            return

        try:
            # 1. Giai ma file bang AES-128-CBC
            enc_data = p.read_bytes()
            try:
                content = aes_decrypt(enc_data).decode("utf-8", errors="replace")
            except Exception:
                content = enc_data.decode("utf-8", errors="replace")

            # 2. Thay the host, port, name
            pattern = r'(this\.servers\s*=\s*\{\{id=1,\s*host=")[^"]+(".*?port=)\d+(.*?name=")[^"]+(".*\}\};?)'
            replacement = f'\\g<1>{new_host}\\g<2>{new_port}\\g<3>{new_name}\\g<4>'
            new_content = re.sub(pattern, replacement, content, count=1)

            # 3. Ma hoa lai bang AES-128-CBC voi PKCS7 padding
            encrypted = aes_encrypt(new_content.encode("utf-8"))
            p.write_bytes(encrypted)

            md5_hash = hashlib.md5(encrypted).hexdigest().upper()
            self.log(f"Đã cập nhật Network.lua thành công! MD5={md5_hash}", "OK")

            # Tự động đồng bộ sang CDN Mirror nếu được chọn
            if self.fast_sync_mirror_var.get():
                self._sync_to_mirror(p, md5_hash)

            messagebox.showinfo("Thành công", f"Đã cập nhật và mã hóa Network.lua thành công!\nHost: {new_host}:{new_port}\nMD5: {md5_hash}")

        except Exception as e:
            self.log(f"Lỗi khi cập nhật: {e}", "ERROR")
            messagebox.showerror("Lỗi", str(e))

    def _sync_to_mirror(self, src_file: Path, new_md5: str):
        synced_count = 0
        mirror_root = SCRIPT_DIR / "cdn" / "mirror" / "tsr"
        if mirror_root.is_dir():
            for ver_dir in mirror_root.iterdir():
                if ver_dir.is_dir():
                    target = ver_dir / "LuaScripts" / "Lua" / "Logic" / "Network.lua"
                    if target.parent.is_dir():
                        import shutil
                        shutil.copy2(src_file, target)
                        synced_count += 1
                        self.log(f"Đã đồng bộ file -> {target.relative_to(SCRIPT_DIR)}")

                    # Cập nhật LuaScripts.dat
                    dat_file = ver_dir / "LuaScripts" / "LuaScripts.dat"
                    if dat_file.is_file():
                        lines = dat_file.read_text(encoding="utf-8").splitlines()
                        new_lines = []
                        updated = False
                        for line in lines:
                            if "Lua/Logic/Network.lua|" in line:
                                new_lines.append(f"Lua/Logic/Network.lua|{new_md5}")
                                updated = True
                            else:
                                new_lines.append(line)
                        if updated:
                            dat_file.write_text("\n".join(new_lines) + "\n", encoding="utf-8")
                            self.log(f"Đã cập nhật MD5 trong {dat_file.relative_to(SCRIPT_DIR)}")

        if synced_count > 0:
            self.log(f"Đã tự động đồng bộ sang {synced_count} phiên bản CDN mirror!", "OK")

    # ============================================================
    # Tab 2: Trình soạn thảo trực tiếp
    # ============================================================
    def _init_tab_editor(self):
        top_frame = tk.Frame(self.tab_editor, bg=self.color_bg)
        top_frame.pack(fill="x", pady=(0, 6))

        open_btn = ttk.Button(top_frame, text="📂 Mở file mã hóa (.lua)...", command=self._editor_open_encrypted)
        open_btn.pack(side="left", padx=4)

        open_plain_btn = ttk.Button(top_frame, text="📄 Mở file plain text...", command=self._editor_open_plain)
        open_plain_btn.pack(side="left", padx=4)

        save_btn = ttk.Button(top_frame, text="💾 Lưu & Mã hóa lại", style="Success.TButton", command=self._editor_save_encrypted)
        save_btn.pack(side="right", padx=4)

        self.editor_path_label = tk.Label(top_frame, text="Chưa mở file nào", font=self.font_main, bg=self.color_bg, fg="#555555")
        self.editor_path_label.pack(side="left", padx=10)

        # Code text editor
        text_frame = tk.Frame(self.tab_editor)
        text_frame.pack(fill="both", expand=True)

        self.editor_text = tk.Text(text_frame, font=self.font_code, wrap="none", undo=True)
        self.editor_text.pack(side="left", fill="both", expand=True)

        x_scroll = ttk.Scrollbar(self.tab_editor, orient="horizontal", command=self.editor_text.xview)
        x_scroll.pack(fill="x")
        y_scroll = ttk.Scrollbar(text_frame, orient="vertical", command=self.editor_text.yview)
        y_scroll.pack(side="right", fill="y")
        self.editor_text.config(xscrollcommand=x_scroll.set, yscrollcommand=y_scroll.set)

        self.editor_current_enc_path = None

    def _editor_open_encrypted(self):
        f = filedialog.askopenfilename(
            title="Chọn file Lua mã hóa cần xem/sửa",
            filetypes=[("Lua files", "*.lua"), ("All files", "*.*")],
            initialdir=str(CDN_DATA_DIR / "Logic") if (CDN_DATA_DIR / "Logic").exists() else ".",
        )
        if f:
            p = Path(f)
            try:
                # Giải mã tạm
                tmp_out = p.with_name(p.stem + ".view_tmp.lua")
                dec = decrypt_file(p, tmp_out)
                text = dec.decode("utf-8", errors="replace")
                tmp_out.unlink(missing_ok=True)

                self.editor_text.delete("1.0", "end")
                self.editor_text.insert("1.0", text)
                self.editor_current_enc_path = p
                self.editor_path_label.config(text=f"Đang sửa file mã hóa: {p.name} ({p})", fg="#0078D4")
                self.log(f"Đã mở và giải mã file trong trình soạn thảo: {p.name}")
            except Exception as e:
                self.log(f"Lỗi mở file: {e}", "ERROR")
                messagebox.showerror("Lỗi", str(e))

    def _editor_open_plain(self):
        f = filedialog.askopenfilename(
            title="Chọn file Lua plain text",
            filetypes=[("Lua files", "*.lua"), ("All files", "*.*")],
            initialdir=str(LUA_DIR) if LUA_DIR.exists() else ".",
        )
        if f:
            p = Path(f)
            try:
                text = p.read_text(encoding="utf-8", errors="replace")
                self.editor_text.delete("1.0", "end")
                self.editor_text.insert("1.0", text)
                self.editor_current_enc_path = None
                self.editor_path_label.config(text=f"Đang sửa file plain text: {p.name}", fg="#333333")
                self.log(f"Đã nạp file plain text: {p.name}")
            except Exception as e:
                self.log(f"Lỗi: {e}", "ERROR")

    def _editor_save_encrypted(self):
        if not self.editor_current_enc_path:
            # Chọn file đích để mã hóa
            f = filedialog.asksaveasfilename(
                title="Chọn nơi lưu file mã hóa (.lua)",
                filetypes=[("Lua files", "*.lua")],
                initialdir=str(CDN_DATA_DIR / "Logic") if (CDN_DATA_DIR / "Logic").exists() else ".",
                defaultextension=".lua",
            )
            if not f:
                return
            self.editor_current_enc_path = Path(f)

        try:
            content = self.editor_text.get("1.0", "end-1c")
            tmp_plain = self.editor_current_enc_path.with_name(self.editor_current_enc_path.stem + ".save_tmp.lua")
            tmp_plain.write_bytes(content.encode("utf-8"))

            encrypt_file(tmp_plain, self.editor_current_enc_path)
            tmp_plain.unlink(missing_ok=True)

            md5_hash = hashlib.md5(self.editor_current_enc_path.read_bytes()).hexdigest().upper()
            self.log(f"Đã lưu và mã hóa file -> {self.editor_current_enc_path} (MD5={md5_hash})", "OK")
            messagebox.showinfo("Thành công", f"Đã lưu và mã hóa file thành công!\nFile: {self.editor_current_enc_path}\nMD5: {md5_hash}")
        except Exception as e:
            self.log(f"Lỗi khi lưu mã hóa: {e}", "ERROR")
            messagebox.showerror("Lỗi", str(e))

    # ============================================================
    # Tab 3: Giải mã (Decrypt)
    # ============================================================
    def _init_tab_dec(self):
        f_in = tk.LabelFrame(self.tab_dec, text="1. Chọn file mã hóa đầu vào (Encrypted .lua)", font=self.font_bold, bg=self.color_bg)
        f_in.pack(fill="x", pady=6)

        self.dec_input_var = tk.StringVar()
        ttk.Entry(f_in, textvariable=self.dec_input_var, font=self.font_main).pack(side="left", fill="x", expand=True, padx=8, pady=8)
        ttk.Button(f_in, text="Browse...", command=self._browse_dec_input).pack(side="right", padx=8, pady=8)

        f_out = tk.LabelFrame(self.tab_dec, text="2. Chọn nơi lưu file plain text đầu ra", font=self.font_bold, bg=self.color_bg)
        f_out.pack(fill="x", pady=6)

        self.dec_output_var = tk.StringVar()
        ttk.Entry(f_out, textvariable=self.dec_output_var, font=self.font_main).pack(side="left", fill="x", expand=True, padx=8, pady=8)
        ttk.Button(f_out, text="Browse...", command=self._browse_dec_output).pack(side="right", padx=8, pady=8)

        btn_run = ttk.Button(self.tab_dec, text="🔓 Tiến hành Giải mã (Decrypt)", style="Primary.TButton", command=self._run_decrypt)
        btn_run.pack(pady=15)

    def _browse_dec_input(self):
        f = filedialog.askopenfilename(
            title="Chọn file .lua cần giải mã",
            filetypes=[("Lua files", "*.lua"), ("All files", "*.*")],
            initialdir=str(CDN_DATA_DIR / "Logic") if (CDN_DATA_DIR / "Logic").exists() else ".",
        )
        if f:
            self.dec_input_var.set(f)
            p = Path(f)
            self.dec_output_var.set(str(p.with_name(p.stem + ".decrypted.lua")))

    def _browse_dec_output(self):
        f = filedialog.asksaveasfilename(
            title="Chọn nơi lưu file plain text",
            filetypes=[("Lua files", "*.lua"), ("Text files", "*.txt")],
            defaultextension=".lua",
        )
        if f:
            self.dec_output_var.set(f)

    def _run_decrypt(self):
        in_p = Path(self.dec_input_var.get())
        out_p = Path(self.dec_output_var.get())
        if not in_p.is_file():
            messagebox.showerror("Lỗi", "File đầu vào không hợp lệ!")
            return
        try:
            decrypt_file(in_p, out_p)
            self.log(f"Giải mã thành công: {in_p.name} -> {out_p.name}", "OK")
            messagebox.showinfo("Thành công", f"Đã giải mã thành công!\nKết quả lưu tại: {out_p}")
        except Exception as e:
            self.log(f"Lỗi giải mã: {e}", "ERROR")
            messagebox.showerror("Lỗi giải mã", str(e))

    # ============================================================
    # Tab 4: Mã hóa (Encrypt)
    # ============================================================
    def _init_tab_enc(self):
        f_in = tk.LabelFrame(self.tab_enc, text="1. Chọn file plain text đầu vào (.lua)", font=self.font_bold, bg=self.color_bg)
        f_in.pack(fill="x", pady=6)

        self.enc_input_var = tk.StringVar()
        ttk.Entry(f_in, textvariable=self.enc_input_var, font=self.font_main).pack(side="left", fill="x", expand=True, padx=8, pady=8)
        ttk.Button(f_in, text="Browse...", command=self._browse_enc_input).pack(side="right", padx=8, pady=8)

        f_out = tk.LabelFrame(self.tab_enc, text="2. Chọn nơi lưu file mã hóa đầu ra (.lua cho CDN)", font=self.font_bold, bg=self.color_bg)
        f_out.pack(fill="x", pady=6)

        self.enc_output_var = tk.StringVar()
        ttk.Entry(f_out, textvariable=self.enc_output_var, font=self.font_main).pack(side="left", fill="x", expand=True, padx=8, pady=8)
        ttk.Button(f_out, text="Browse...", command=self._browse_enc_output).pack(side="right", padx=8, pady=8)

        btn_run = ttk.Button(self.tab_enc, text="🔒 Tiến hành Mã hóa (Encrypt)", style="Primary.TButton", command=self._run_encrypt)
        btn_run.pack(pady=15)

    def _browse_enc_input(self):
        f = filedialog.askopenfilename(
            title="Chọn file plain text cần mã hóa",
            filetypes=[("Lua files", "*.lua"), ("All files", "*.*")],
        )
        if f:
            self.enc_input_var.set(f)
            p = Path(f)
            self.enc_output_var.set(str(CDN_DATA_DIR / "Logic" / p.name) if (CDN_DATA_DIR / "Logic").exists() else str(p.with_name(p.stem + ".encrypted.lua")))

    def _browse_enc_output(self):
        f = filedialog.asksaveasfilename(
            title="Chọn nơi lưu file mã hóa",
            filetypes=[("Lua files", "*.lua")],
            defaultextension=".lua",
        )
        if f:
            self.enc_output_var.set(f)

    def _run_encrypt(self):
        in_p = Path(self.enc_input_var.get())
        out_p = Path(self.enc_output_var.get())
        if not in_p.is_file():
            messagebox.showerror("Lỗi", "File đầu vào không hợp lệ!")
            return
        try:
            encrypted = encrypt_file(in_p, out_p)
            md5_hash = hashlib.md5(encrypted).hexdigest().upper()
            self.log(f"Mã hóa thành công: {in_p.name} -> {out_p.name} (MD5={md5_hash})", "OK")
            messagebox.showinfo("Thành công", f"Đã mã hóa thành công!\nFile: {out_p}\nMD5: {md5_hash}")
        except Exception as e:
            self.log(f"Lỗi mã hóa: {e}", "ERROR")
            messagebox.showerror("Lỗi mã hóa", str(e))


if __name__ == "__main__":
    app = LuaCryptGUI()
    app.mainloop()
