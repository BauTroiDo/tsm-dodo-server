# TS Origin VTC 1.3 — IL2CPP research snapshot

Snapshot dùng để audit/reverse TS Online Mobile Đa Vũ Trụ mà không phụ thuộc máy/LDPlayer của phiên chat cũ.

## Nguồn
- Package: `com.vtcmobile.gz06`
- App label: `TS Origin VTC: Xuất Chiến`
- versionName: `1.3`
- versionCode: `8`
- ABI dùng nghiên cứu: `arm64-v8a`
- APK lấy trực tiếp từ LDPlayer bằng ADB.
- `libil2cpp.so`, `libunity.so`, `global-metadata.dat` đều được trích từ cùng APK này.

## File
- `apk/gz06_base.apk` — 111,110,656 bytes
- `native/arm64-v8a/libil2cpp.so` — 48,445,680 bytes
- `native/arm64-v8a/libunity.so` — 25,175,672 bytes
- `metadata/global-metadata.dat` — 8,222,732 bytes

## SHA256
- APK: `59A4E819305906D746211B7987A23CAE98B38659385320CB7995F71EAD1DF908`
- libil2cpp.so: `1D2B18F6575900DF71432033596F5B6A77866ABC044D0ED927B657AE01FD1681`
- libunity.so: `8EE0B3B8FAA4A0F11AC8DAA2590AB6B8C6D4852C3EC35554F22C7163470C4221`
- global-metadata.dat: `1193B2F7E42AE8AB3CC9C540C79C182A4C6FE74D0479E847980634E0A42CCBD9`

## Kiểm tra kỹ thuật
- ELF: `ELFCLASS64 / EM_AARCH64`
- IL2CPP metadata sanity: `0xFAB11BAF`
- Metadata version: `31`
- Cặp `libil2cpp.so + global-metadata.dat` đã được test cùng nhau bằng tool trong repo.

## Mục đích
Ưu tiên dùng snapshot này khi Lua DVT/data/client chưa đủ bằng chứng để xác định semantics server-side.
Không dùng native reverse để ghi đè bằng chứng rõ ràng từ Lua DVT; thứ tự nguồn sự thật dự án vẫn giữ nguyên.

Tool nghiên cứu nằm tại:
`tools/ts-il2cpp-research`
