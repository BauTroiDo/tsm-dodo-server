#!/usr/bin/env python3
"""
tsm_dodo CDN Server — HTTPS CDN game di động Thôn Thực Thiên Địa M
================================================================

Máy chủ file tĩnh thuần túy, cung cấp toàn bộ tài nguyên CDN từ cdn/mirror/.
Hãy dùng download_cdn.py để tải tài nguyên trước, sau đó mới khởi chạy máy chủ này.

Thiết lập kèm theo:
- Nox hosts điều hướng: tsrtwftp.chinesegamer.net → IP máy tính (PC)
- Nox cài đặt chứng chỉ CA tự ký
- APK đã được patch (CheckVersion NOP + CDN domain)

Cách dùng:
  python cdn_server.py                    # Khởi động HTTPS CDN (cổng 443)
  python cdn_server.py --port 8443        # Tùy chỉnh cổng
  python cdn_server.py --gen-cert         # Chỉ tạo chứng chỉ, không khởi động server
  python cdn_server.py --http             # Chế độ HTTP (dùng gỡ lỗi, mặc định cổng 8080)
"""

import argparse
import ctypes
import hashlib
import ipaddress
import json
import os
import shutil
import ssl
import sys

if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass
from datetime import datetime, timedelta, timezone
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from pathlib import Path

# ============================================================
# HẰNG SỐ
# ============================================================
SCRIPT_DIR = Path(__file__).parent
MIRROR_DIR = SCRIPT_DIR / "cdn" / "mirror"
CERT_DIR = SCRIPT_DIR / "cdn" / "cert"

CDN_DOMAIN = "tsrtwftp.chinesegamer.net"
GAME_VERSION = "2.7"         # Phiên bản client thấy (tăng số mỗi khi sửa Lua để buộc tải lại)
MIRROR_DATA_DIR = "2.7"      # Tên thư mục thực tế trên ổ đĩa tại cdn/mirror/tsr/
EXE_VERSION = "2.6"

# Tạo động file ResourcePath_ANDROID.dat
RESOURCE_PATH_JSON = json.dumps(
    [{"ExeVer": EXE_VERSION, "DataVer": GAME_VERSION}],
    separators=(",", ":"),
).encode("utf-8")


# ============================================================
# MÃ MÀU ANSI
# ============================================================
class C:
    RESET = "\033[0m"
    BOLD = "\033[1m"
    RED = "\033[91m"
    GREEN = "\033[92m"
    YELLOW = "\033[93m"
    CYAN = "\033[96m"
    DIM = "\033[2m"


# Ngưỡng streaming: nhỏ hơn giá trị này thì đọc trực tiếp bằng read_bytes(), lớn hơn thì truyền phát stream
STREAM_THRESHOLD = 0
STREAM_CHUNK = 16 * 1024

# Bảng ánh xạ Content-Type
CONTENT_TYPES = {
    ".json": "application/json",
    ".lua": "text/plain; charset=utf-8",
}
# Các định dạng còn lại mặc định là application/octet-stream (gồm .dat, .zfp, .unity3d, .lz4)


def _content_type(path: str) -> str:
    """Xác định Content-Type theo phần mở rộng của file."""
    ext = Path(path).suffix.lower()
    return CONTENT_TYPES.get(ext, "application/octet-stream")


def _human_size(n: int) -> str:
    if n < 1024:
        return f"{n} B"
    elif n < 1024 * 1024:
        return f"{n / 1024:.1f} KB"
    else:
        return f"{n / 1024 / 1024:.1f} MB"


def _is_windows_admin() -> bool:
    """Kiểm tra Windows hiện tại có đang chạy dưới quyền Quản trị viên (Administrator) hay không."""
    if os.name != "nt":
        return True
    try:
        return bool(ctypes.windll.shell32.IsUserAnAdmin())
    except Exception:
        return False


def _print_bind_error(args, exc: BaseException) -> None:
    """Chuyển lỗi socket bind thành hướng dẫn sửa lỗi dễ hiểu, tránh in cả đoạn traceback dài."""
    proto = "HTTP" if args.http else "HTTPS"
    bind = args.bind
    port = args.port

    print(f"\n  {C.RED}[Lỗi] Máy chủ CDN không thể khởi động: {proto} {bind}:{port}{C.RESET}")
    print(f"  Nguyên nhân: {exc}")

    if os.name == "nt":
        print("\n  Các nguyên nhân phổ biến trên Windows:")
        print("  1. Cổng 443/80 yêu cầu chạy CMD/PowerShell bằng quyền 'Run as administrator'")
        print("  2. Cổng đã bị chương trình khác chiếm dụng, ví dụ: IIS, Nginx, Apache, phần mềm proxy")
        print("  3. Cổng nằm trong dải loại trừ/đặt trước của Windows (excluded/reserved range), bị hệ thống giữ lại")
        print("  4. Tường lửa hoặc phần mềm diệt virus chặn Python liên kết với cổng này")

        if port in (80, 443) and not _is_windows_admin():
            print(f"\n  {C.YELLOW}Cách khắc phục 1 (khuyên dùng cho CDN chính thức):{C.RESET}")
            print("  Đóng cửa sổ này → Chuột phải vào CMD/PowerShell → Chọn 'Run as administrator'")
            print(f"  Sau đó chạy lại lệnh: python cdn_server.py --bind {bind} --port {port}")

        print(f"\n  {C.YELLOW}Kiểm tra xem cổng có bị chiếm dụng không:{C.RESET}")
        print(f"  netstat -ano | findstr :{port}")
        print("  Nếu thấy trạng thái LISTENING, lấy PID ở cuối cùng để tìm tiến trình tương ứng:")
        print("  tasklist | findstr <PID>")

        print(f"\n  {C.YELLOW}Kiểm tra xem Windows có đặt trước cổng này không:{C.RESET}")
        print("  netsh interface ipv4 show excludedportrange protocol=tcp")

    print(f"\n  {C.YELLOW}Để gỡ lỗi có thể đổi sang cổng HTTP 8080:{C.RESET}")
    print(f"  python cdn_server.py --http --bind {bind} --port 8080")
    print("  Lưu ý: Game/APK dùng HTTPS CDN thật thường cần cổng 443; nếu dùng 8080 thì client/config phải trỏ đúng cổng.")

    sys.exit(1)


# ============================================================
# CDN Request Handler — Phục vụ file tĩnh dạng stream
# ============================================================
class CdnHandler(BaseHTTPRequestHandler):
    """Phục vụ file tĩnh từ cdn/mirror/, truyền tải file lớn dưới dạng stream."""

    def do_GET(self):
        self._handle(send_body=True)

    def do_HEAD(self):
        self._handle(send_body=False)

    def _handle(self, send_body: bool):
        path = self.path.split("?")[0]  # Loại bỏ tham số truy vấn
        method = "GET" if send_body else "HEAD"

        # Tạo động ResourcePath (có thể cấu hình phiên bản)
        if path.endswith("ResourcePath_ANDROID.dat") or path.endswith("ResourcePath_STANDALONE.dat"):
            self._serve_bytes(RESOURCE_PATH_JSON, "application/json", "ResourcePath", send_body)
            return

        # Tạo động APP_DownloadPath
        if path.endswith("APP_DownloadPath.dat"):
            app_dl_json = json.dumps([
                {"Platform": "TW_Android", "Url": "https://play.google.com/store/apps/details?id=net.chinesegamer.tsm"},
                {"Platform": "TW_MyCard", "Url": "https://cgdl.chinesegamer.net/apk/tsm/tsr_TW_Release_MyCard_FAT.apk"}
            ]).encode("utf-8")
            self._serve_bytes(app_dl_json, "application/json", "APP_DownloadPath", send_body)
            return

        # Các file khác: tìm trong thư mục mirror/
        rel = path.lstrip("/")

        # Viết lại đường dẫn phiên bản: Client yêu cầu /tsr/{GAME_VERSION}/ → Trên ổ đĩa chuyển thành /tsr/{MIRROR_DATA_DIR}/
        if GAME_VERSION != MIRROR_DATA_DIR and rel.startswith(f"tsr/{GAME_VERSION}/"):
            rel = f"tsr/{MIRROR_DATA_DIR}/" + rel[len(f"tsr/{GAME_VERSION}/"):]

        file_path = MIRROR_DIR / rel

        if file_path.is_file():
            file_size = file_path.stat().st_size
            ct = _content_type(path)
            range_header = self.headers.get("Range")

            if range_header and range_header.startswith("bytes="):
                range_spec = range_header.split("=", 1)[1].split(",", 1)[0]
                start_text, _, end_text = range_spec.partition("-")
                try:
                    start = int(start_text) if start_text else 0
                    end = int(end_text) if end_text else file_size - 1
                    start = max(0, min(start, file_size - 1))
                    end = max(start, min(end, file_size - 1))
                except ValueError:
                    start, end = 0, file_size - 1

                content_length = end - start + 1
                self.send_response(206)
                self.send_header("Content-Type", ct)
                self.send_header("Content-Length", str(content_length))
                self.send_header("Content-Range", f"bytes {start}-{end}/{file_size}")
                self.send_header("Accept-Ranges", "bytes")
                self.send_header("Connection", "close")
                self.end_headers()
                if send_body:
                    remaining = content_length
                    with open(file_path, "rb") as f:
                        f.seek(start)
                        while remaining > 0:
                            chunk = f.read(min(STREAM_CHUNK, remaining))
                            if not chunk:
                                break
                            self.wfile.write(chunk)
                            remaining -= len(chunk)
                        self.wfile.flush()

                print(f"  {C.GREEN}206{C.RESET} {method} {rel} ({start}-{end}/{_human_size(file_size)})", flush=True)
                return

            if not send_body or file_size <= STREAM_THRESHOLD:
                # File nhỏ hoặc yêu cầu HEAD: đọc trực tiếp qua read_bytes
                data = file_path.read_bytes() if send_body else b""
                self._send_headers(200, ct, file_size)
                if send_body:
                    self.wfile.write(data)
            else:
                # File lớn: truyền tải dạng stream
                self._send_headers(200, ct, file_size)
                with open(file_path, "rb") as f:
                    while True:
                        chunk = f.read(STREAM_CHUNK)
                        if not chunk:
                            break
                        self.wfile.write(chunk)
                        self.wfile.flush()
                self.wfile.flush()

            print(f"  {C.GREEN}200{C.RESET} {method} {rel} ({_human_size(file_size)})", flush=True)
        else:
            self._serve_404(method, rel)

    def _serve_bytes(self, data: bytes, content_type: str, label: str, send_body: bool):
        self._send_headers(200, content_type, len(data))
        if send_body:
            self.wfile.write(data)
            self.wfile.flush()
        print(f"  {C.GREEN}200{C.RESET} {label} ({_human_size(len(data))})", flush=True)

    def _send_headers(self, code: int, content_type: str, content_length: int):
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(content_length))
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Connection", "close")
        self.end_headers()

    def _serve_404(self, method: str, path: str):
        self.send_response(404)
        self.send_header("Content-Length", "0")
        self.send_header("Connection", "close")
        self.end_headers()
        print(f"  {C.RED}404{C.RESET} {method} {path}", flush=True)

    def log_message(self, format, *args):
        pass


# ============================================================
# TẠO CHỨNG CHỈ (CERTIFICATE GENERATION)
# ============================================================
def _import_crypto():
    """Tải thư viện cryptography."""
    try:
        from cryptography import x509
        from cryptography.x509.oid import NameOID, ExtendedKeyUsageOID
        from cryptography.hazmat.primitives import hashes, serialization
        from cryptography.hazmat.primitives.asymmetric import rsa
        return x509, NameOID, ExtendedKeyUsageOID, hashes, serialization, rsa
    except ImportError:
        print(f"{C.RED}[Lỗi] Cần thư viện cryptography: pip install cryptography{C.RESET}")
        sys.exit(1)


def generate_ca(cert_dir: Path) -> tuple[Path, Path, Path]:
    """
    Tạo chứng chỉ gốc Root CA (không liên kết IP/domain).

    Chứng chỉ CA sau khi nhúng vào APK sẽ không bao giờ cần cập nhật lại.
    Khi đổi IP chỉ cần ký lại chứng chỉ máy chủ (Server Certificate).

    Trả về: (ca_pem_path, ca_key_path, ca_der_path)
    """
    x509, NameOID, _, hashes, serialization, rsa = _import_crypto()

    cert_dir.mkdir(parents=True, exist_ok=True)
    ca_key_path = cert_dir / "ca.key"
    ca_pem_path = cert_dir / "ca.pem"
    ca_der_path = cert_dir / "ca.der"

    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    now = datetime.now(timezone.utc)
    subject = issuer = x509.Name([
        x509.NameAttribute(NameOID.COMMON_NAME, "tsm_dodo CA"),
        x509.NameAttribute(NameOID.ORGANIZATION_NAME, "tsm_dodo"),
    ])

    cert = (
        x509.CertificateBuilder()
        .subject_name(subject)
        .issuer_name(issuer)
        .public_key(key.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(now)
        .not_valid_after(now + timedelta(days=3650))
        .add_extension(
            x509.BasicConstraints(ca=True, path_length=0),
            critical=True,
        )
        .add_extension(
            x509.KeyUsage(
                digital_signature=False, content_commitment=False,
                key_encipherment=False, data_encipherment=False,
                key_agreement=False, key_cert_sign=True, crl_sign=True,
                encipher_only=False, decipher_only=False,
            ),
            critical=True,
        )
        .sign(key, hashes.SHA256())
    )

    ca_pem_path.write_bytes(cert.public_bytes(serialization.Encoding.PEM))
    ca_der_path.write_bytes(cert.public_bytes(serialization.Encoding.DER))
    ca_key_path.write_bytes(key.private_bytes(
        serialization.Encoding.PEM,
        serialization.PrivateFormat.TraditionalOpenSSL,
        serialization.NoEncryption(),
    ))

    print(f"  {C.GREEN}Đã tạo chứng chỉ CA{C.RESET}")
    print(f"    PEM: {ca_pem_path}")
    print(f"    DER: {ca_der_path} (Dùng để nhúng vào APK)")
    print(f"    Khóa bí mật (Private key): {ca_key_path}")
    return ca_pem_path, ca_key_path, ca_der_path


def generate_server_cert(cert_dir: Path, domain: str) -> tuple[Path, Path]:
    """
    Dùng CA để ký phát chứng chỉ máy chủ.

    Hỗ trợ cả tên miền và địa chỉ IP (tự động thêm SAN).
    Yêu cầu file ca.key + ca.pem đã tồn tại.

    Trả về: (server_pem_path, server_key_path)
    """
    x509, NameOID, ExtendedKeyUsageOID, hashes, serialization, rsa = _import_crypto()

    ca_key_path = cert_dir / "ca.key"
    ca_pem_path = cert_dir / "ca.pem"

    if not ca_key_path.exists() or not ca_pem_path.exists():
        print(f"  {C.RED}[Lỗi] CA chưa tồn tại, hãy chạy --gen-ca trước{C.RESET}")
        sys.exit(1)

    # Đọc thông tin CA
    ca_key = serialization.load_pem_private_key(ca_key_path.read_bytes(), password=None)
    ca_cert = x509.load_pem_x509_certificate(ca_pem_path.read_bytes())

    cert_path = cert_dir / "server.pem"
    key_path = cert_dir / "server.key"

    server_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    now = datetime.now(timezone.utc)
    subject = x509.Name([
        x509.NameAttribute(NameOID.COMMON_NAME, domain),
        x509.NameAttribute(NameOID.ORGANIZATION_NAME, "tsm_dodo"),
    ])

    # Tự động thêm các SAN phổ biến để hỗ trợ cả IP và domain
    candidates = [
        domain,
        "tsrtwftp.chinesegamer.net",
        "localhost",
        "192.168.1.69",
        "192.168.0.157",
        "192.168.1.99",
        "127.0.0.1",
    ]
    san_entries = []
    seen = set()
    for item in candidates:
        if not item or item in seen:
            continue
        seen.add(item)
        try:
            ip = ipaddress.ip_address(item)
            san_entries.append(x509.IPAddress(ip))
        except ValueError:
            san_entries.append(x509.DNSName(item))

    print(f"  {C.CYAN}Danh sách SAN{C.RESET}: {[str(s.value) for s in san_entries]}")

    cert = (
        x509.CertificateBuilder()
        .subject_name(subject)
        .issuer_name(ca_cert.subject)
        .public_key(server_key.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(now)
        .not_valid_after(now + timedelta(days=365))
        .add_extension(
            x509.SubjectAlternativeName(san_entries),
            critical=False,
        )
        .add_extension(
            x509.BasicConstraints(ca=False, path_length=None),
            critical=True,
        )
        .add_extension(
            x509.ExtendedKeyUsage([ExtendedKeyUsageOID.SERVER_AUTH]),
            critical=False,
        )
        .sign(ca_key, hashes.SHA256())
    )

    cert_path.write_bytes(cert.public_bytes(serialization.Encoding.PEM))
    key_path.write_bytes(server_key.private_bytes(
        serialization.Encoding.PEM,
        serialization.PrivateFormat.TraditionalOpenSSL,
        serialization.NoEncryption(),
    ))

    print(f"  {C.GREEN}Đã tạo chứng chỉ máy chủ{C.RESET} (do CA ký phát)")
    print(f"    Chứng chỉ: {cert_path}")
    print(f"    Khóa bí mật: {key_path}")
    return cert_path, key_path


# ============================================================
# CHƯƠNG TRÌNH CHÍNH
# ============================================================
def main():
    parser = argparse.ArgumentParser(
        description="tsm_dodo CDN — Máy chủ HTTPS CDN game di động Thôn Thực Thiên Địa M",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("--port", type=int, default=None, help="Cổng lắng nghe (HTTPS mặc định 443; HTTP mặc định 8080)")
    parser.add_argument("--http", action="store_true", help="Chế độ HTTP (không có SSL, dùng gỡ lỗi)")
    parser.add_argument("--gen-ca", action="store_true", help="Chỉ tạo chứng chỉ CA (dùng nhúng vào APK)")
    parser.add_argument("--gen-cert", action="store_true", help="Tạo chứng chỉ máy chủ (do CA ký phát)")
    parser.add_argument("--renew-cert", action="store_true", help="Bắt buộc cấp lại chứng chỉ máy chủ (dùng khi đổi IP)")
    parser.add_argument("--bind", default="0.0.0.0", help="Địa chỉ IP lắng nghe (mặc định 0.0.0.0)")
    parser.add_argument(
        "--domain",
        default=CDN_DOMAIN,
        help=f"Tên miền hoặc IP cho chứng chỉ (mặc định: {CDN_DOMAIN})",
    )
    args = parser.parse_args()

    # Chế độ debug HTTP mặc định đổi sang 8080, tránh việc dùng --http mà vẫn chạy 443 gây ra lỗi WinError 10013.
    if args.port is None:
        args.port = 8080 if args.http else 443

    print(f"\n{C.BOLD}  Máy chủ CDN tsm_dodo{C.RESET}")
    print(f"  {'─' * 30}")

    # Xử lý CA
    ca_key_path = CERT_DIR / "ca.key"
    ca_pem_path = CERT_DIR / "ca.pem"

    if args.gen_ca or not ca_key_path.exists():
        print(f"\n  Đang tạo chứng chỉ CA...")
        generate_ca(CERT_DIR)
        if args.gen_ca:
            print(f"\n{C.GREEN}  Hoàn tất!{C.RESET}\n")
            return

    # Xử lý chứng chỉ máy chủ
    cert_path = CERT_DIR / "server.pem"
    key_path = CERT_DIR / "server.key"

    need_server_cert = (
        args.gen_cert
        or args.renew_cert
        or (not args.http and not cert_path.exists())
    )

    if need_server_cert:
        print(f"\n  Đang tạo chứng chỉ máy chủ (CN={args.domain})...")
        generate_server_cert(CERT_DIR, args.domain)
        if args.gen_cert or args.renew_cert:
            print(f"\n{C.GREEN}  Hoàn tất!{C.RESET}\n")
            return

    # Kiểm tra thư mục mirror
    if not MIRROR_DIR.exists():
        print(f"\n  {C.RED}[Lỗi] Thư mục mirror không tồn tại: {MIRROR_DIR}{C.RESET}")
        print(f"  Hãy chạy python download_cdn.py trước để tải tài nguyên")
        sys.exit(1)

    file_count = sum(1 for _ in MIRROR_DIR.rglob("*") if _.is_file())
    total_bytes = sum(f.stat().st_size for f in MIRROR_DIR.rglob("*") if f.is_file())
    print(f"\n  {C.GREEN}Tài nguyên{C.RESET}: {file_count} tệp tin, {total_bytes / 1024 / 1024:.1f} MB")
    print(f"  {C.GREEN}Phiên bản{C.RESET}: ExeVer={EXE_VERSION}, DataVer={GAME_VERSION}")

    # Khởi tạo máy chủ
    try:
        server = ThreadingHTTPServer((args.bind, args.port), CdnHandler)
    except PermissionError as e:
        _print_bind_error(args, e)
    except OSError as e:
        _print_bind_error(args, e)

    if not args.http:
        if not cert_path.exists() or not key_path.exists():
            print(f"\n  {C.RED}[Lỗi] Chứng chỉ không tồn tại, hãy chạy --gen-cert trước{C.RESET}")
            sys.exit(1)
        ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        ctx.load_cert_chain(str(cert_path), str(key_path))
        server.socket = ctx.wrap_socket(server.socket, server_side=True)
        proto = "HTTPS"
    else:
        proto = "HTTP"

    print(f"\n  {C.GREEN}Khởi động{C.RESET} máy chủ {proto} → {args.bind}:{args.port}")
    print(f"  {C.DIM}Nhấn Ctrl+C để dừng{C.RESET}\n")

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print(f"\n  {C.DIM}Đã dừng máy chủ{C.RESET}")
        server.shutdown()


if __name__ == "__main__":
    main()
