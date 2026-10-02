# TS IL2CPP Research Tool

Wails/Go tool dùng để trích xuất và nghiên cứu IL2CPP của TS Online Mobile.

## Chức năng
- Kéo `base.apk` trực tiếp từ LDPlayer qua ADB.
- Trích `libil2cpp.so`, `libunity.so`, `global-metadata.dat`.
- Kiểm tra ELF / metadata sanity / metadata version / SHA256.
- Search text, UInt16 LE, UInt32 LE theo offset.
- Dump ASCII strings kèm offset.
- Xuất report Markdown.
- Có hook chạy `Il2CppDumper.exe` nếu người dùng cung cấp executable.

## Build
Yêu cầu Go + Wails v2 + Node.js.
Chạy từ thư mục này:

`wails build`

Binary build tham chiếu được lưu ở `bin/TS-IL2CPP-Research.exe`.
