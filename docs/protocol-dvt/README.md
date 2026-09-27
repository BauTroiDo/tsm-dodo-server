# Audit protocol Lua Đa Vũ Trụ ↔ Kotlin — 2026-09-27

Nguồn: [BauTroiDo/tsm-dodo-server](https://github.com/BauTroiDo/tsm-dodo-server), branch `main`, commit `59c2e0a81c3d4443aa1aad3584d216096256cf16` — `Update lua da vu tru`. Đây là HEAD tải trực tiếp từ GitHub khi bắt đầu audit. Lua được giữ nguyên; bản vá chỉ sửa Kotlin, thêm test và tài liệu/công cụ audit. Thay đổi nằm ở checkout cục bộ, chưa commit/push lên GitHub.

## Phạm vi và cách đọc kết quả

Quét đủ **792 file Lua**, **543 call site gửi** (gồm `this.Send` trong Network.lua), **1.448 callback nhận** trong protocal.lua, **39 handler được ServerModule đăng ký**, **57 constant trong object Opcodes**. Đối chiếu writer trong toàn bộ module Kotlin, gồm các ResponseSender và header gửi trực tiếp. Tài liệu `C:/S:` trong protocal.lua có **1.907 dòng** được giữ riêng trong JSON để không nhầm comment với đường chạy thật.

Inventory gồm **1.902 cặp direction/main/sub**: 453 C→S, 1.448 callback S→C và một opcode S→C chỉ có phía server. Các giá trị enum/UI động là tập ứng viên từ source, không phải cam kết mọi nhánh đang hoạt động trong bản client chạy thật. Một call site debug `UIDebug.lua:462` nhập tùy ý main/sub được ghi riêng là unresolved; không thể suy ra cặp cố định. Hai writer debug tùy ý phía Kotlin cũng được ghi riêng.

| Trạng thái | Baseline | Sau bản vá |
|---|---:|---:|
| MATCH | 1 | 11 |
| PARTIAL | 319 | 332 |
| MISSING | 1558 | 1558 |
| MISMATCH | 24 | 1 |

- **MATCH**: phạm vi wire contract đã đối chiếu và có test đọc độc lập/golden bytes. Không đồng nghĩa cả subsystem đã chạy end-to-end.
- **PARTIAL**: có route/writer, nhưng payload, nhánh, game logic hoặc profile chưa chứng minh đầy đủ. Những lỗi wire đã sửa nhưng chưa có integration test vẫn có thể là PARTIAL.
- **MISSING**: chưa tìm thấy route/writer nghiệp vụ tương ứng trong source; có cả callback client tùy chọn, chức năng nâng cấp, debug và callback rỗng. Đây **không phải 1.558 hệ thống cần viết mới**. Nhánh `28:2` chỉ log TODO cũng được đánh dấu MISSING.
- **MISMATCH**: có bằng chứng lệch contract. `baseline_status` giữ kết quả trước vá; `status` phản ánh kết quả sau vá. 23 cặp protocol đã sửa; constant Elf/reconnect được ghi thêm bên dưới.

`inventory.csv` để lọc nhanh; `inventory.json` chứa từng call site, callback alias, route/writer Kotlin, lý do phân loại, comment spec, opcode constants và các trường hợp động. Bộ quét bỏ comment/chuỗi, giữ số dòng, nhận diện sub mặc định 0, callback gán bằng tên hàm, range dispatch và chỉ lấy constant trong object Opcodes (không trộn ProtocolEvent). Đây là static inventory với review thủ công các luồng ưu tiên, **chưa phải chứng nhận hành vi cho toàn bộ 1.902 cặp**.

## Ưu tiên và phát hiện

Nhóm dưới đây theo main opcode để tiện lọc; một opcode có thể phục vụ nhiều hệ thống.

| Nhóm | MATCH | PARTIAL | MISSING | MISMATCH |
|---|---:|---:|---:|---:|
| Login | 2 | 6 | 19 | 0 |
| Character | 1 | 13 | 11 | 0 |
| Scene/Map | 1 | 14 | 25 | 0 |
| NPC/EVE/Quest | 0 | 24 | 94 | 0 |
| Item/Pet/Skill | 7 | 85 | 155 | 0 |
| Battle | 0 | 29 | 35 | 0 |

1. **Login:** `S 1:2` thiếu Int32 chẩn đoán cuối nhánh thành công; `S 0:0` thiếu hai Int32. Đã sửa. `Network.lua:640-701` còn gửi metadata channel/device/networkDisMsg chưa được dùng; login bên thứ ba và chuyển server chưa đầy đủ. Version 258 khớp.
2. **Character:** `S 5:3` ghi dư HP/SP sau turn3Skills; `Role.ReceivePlayerData` tái sử dụng biến hp/sp đọc đầu gói, không đọc lại. Đã bỏ 6 byte dư. `3:0` đúng thứ tự trường đã xem nhưng còn hardcode một số dữ liệu nhân vật; encoding tên cần xác nhận qua ByteBuffer/native client, không kết luận từ tên hàm.
3. **Scene/Map:** test `S 12:0` xác nhận trang bị, outfit count rồi title đúng thứ tự Lua. Giới hạn single-area và những chức năng MapNpc chưa phát packet được giữ PARTIAL/MISSING; không thêm thông báo thành công giả.
4. **NPC/EVE/Quest:** `20:1..6` nhận qua alias `EventManager.ReceiveCommonEvent`, không phải anonymous function; inventory đã tính đầy đủ. Trigger/advance/choice có route thật. 8 lỗi baseline của evaluator khiến chưa thể xác nhận chuỗi quest end-to-end. Không đổi evaluator khi chưa có đối chiếu semantics Lua đủ chắc.
5. **Item/Pet/Skill:** DVT dùng UInt16 cho ô túi đồ/ngân hàng nhưng Byte cho slot kho phụ, pet và vị trí trang bị. Đã sửa những luồng cơ bản có bằng chứng, gồm gửi/rút, sắp xếp, dùng/vứt đồ và mặc/tháo trang bị. Pet skill upgrade `C 28:2` có nhánh TODO, vẫn MISSING. `ThingData` non-NFT là 35 byte; nhánh NFT thêm 21 byte, chưa biết define runtime nên chưa tuyên bố hỗ trợ profile NFT.
6. **Battle:** giải được `Network.Send(50, kind)` thành sub 1/2; thứ tự tọa độ, action UInt16 và hai check byte khớp parser. Logic/check-byte/result toàn trận vẫn PARTIAL. Xóa constant RECONNECT=50 không được dùng để tránh nhầm với BATTLE_COMMAND.

**Mismatch còn lại:** `S 13:15` của PartyPetResponseSender có writer nhưng không có callback ĐVT. Chưa có bằng chứng packet thay thế nên giữ nguyên và báo rõ, không tự đổi sang opcode khác.

## File production đã sửa

| File | Thay đổi và bằng chứng Lua |
|---|---|
| `app/src/main/kotlin/tw/tsm/app/response/LoginResponseSender.kt` | Bổ sung trường chẩn đoán `1:2`, `0:0`; bỏ HP/SP lặp ở `5:3`. `protocal.lua:37,229,286,383`; `Role.lua:2071`. Giá trị chẩn đoán 0 là không override/không suffix theo nhánh Lua. |
| `app/src/main/kotlin/tw/tsm/app/response/InventoryResponseSender.kt` | UInt16 ô túi/ngân hàng: `23:5,8,9,10,16,17,22,23`, `30:1,4,5`; giữ Byte của kho phụ. `protocal.lua:3722,3740,3747,3754,3831,3842,3908,3920,6659,6668,6675,14570`. |
| `app/src/main/kotlin/tw/tsm/app/handlers/ItemHandler.kt` | UInt16 ô túi trong `23:3,10,11,12,15,17`; đọc đủ destination suggestion `23:18`; kiểm tra độ dài trước mutation. `Item.lua:1486,1514,3678,3765,3830`; `UIBag.lua:600-608`. |
| `app/src/main/kotlin/tw/tsm/app/handlers/BankHandler.kt` | UInt16 index + Int32 quantity cho cả gửi/rút, kiểm tra bản ghi 6 byte. `UIBank.lua:671-678` ưu tiên code thật hơn comment cũ. |
| `app/src/main/kotlin/tw/tsm/app/handlers/StorageHandler.kt` | UInt16 bag index khi gửi; rút kho phụ vẫn Byte. `UIBank.lua:671-678`. |
| `domain/src/main/kotlin/tw/tsm/domain/constants/Opcodes.kt` | ELF 110→107 theo `UIElf.lua` và `protocal.lua:15126`; bỏ RECONNECT=50 không có người dùng. Không tạo ElfHandler giả. |

## File mới

- `tests/src/test/kotlin/tw/tsm/tests/DvtLoginWireTest.kt`: 5 test, JDK ByteBuffer độc lập, kiểm tra consumption và endian.
- `tests/src/test/kotlin/tw/tsm/tests/DvtBankWireTest.kt`: 6 test, slot >255, quantity, boundary batch, malformed packet, kho phụ Byte.
- `tests/src/test/kotlin/tw/tsm/tests/DvtItemWireTest.kt`: 8 test, handler decode, golden responses, item-list alignment, malformed batch và pet equipment.
- `tools/protocol_inventory.py`, `tools/protocol_dynamic_calls.json`, `tools/protocol_reviews.json`, `tools/test_protocol_inventory.py`.
- `docs/protocol-dvt/README.md`, `inventory.csv`, `inventory.json`.

## Kết quả chạy thực tế

JDK **21.0.5** (JetBrains Runtime), Gradle wrapper **8.13**, không sửa build config/toolchain.

| Kiểm tra | Kết quả |
|---|---|
| Baseline `test build` trước sửa | 113 test, 8 failed; compile và app packaging thành công |
| Test mới `:tests:test --tests '*Dvt*Test'` | **19/19 pass**, BUILD SUCCESSFUL |
| Python inventory unittest | **3/3 pass** |
| Toàn bộ `test build --continue` sau sửa | **132 test, 124 pass, 8 failed**; BUILD FAILED vì đúng 8 lỗi baseline |
| Riêng `:app:build` | **BUILD SUCCESSFUL**, artifact ZIP/TAR được tạo ở lần full build và xác nhận up-to-date |
| `git diff --check` | Pass |
| `git diff --quiet -- lua` | Exit 0: không sửa Lua |

Không tắt test, không thêm dummy/no-op production để compile. Chưa chạy client thật, SQL Server/game-session integration hay kiểm chứng runtime defines. Bản vá này không được coi là server đã tương thích hoàn toàn.

Tám lỗi còn lại (đã có trước sửa):

- `tw.tsm.tests.EveConditionEvaluatorTest` — ops=4 大於: io.kotest.assertions.AssertionFailedError: expected:<false> but was:<true>
- `tw.tsm.tests.EveConditionEvaluatorTest` — 只檢查錢莊/寶庫彙總數量，不重用背包數量: io.kotest.assertions.AssertionFailedError: expected:<true> but was:<false>
- `tw.tsm.tests.EveConditionEvaluatorTest` — pStyle=3 未開始任務 → actual=0: io.kotest.assertions.AssertionFailedError: expected:<true> but was:<false>
- `tw.tsm.tests.EveConditionEvaluatorTest` — val=1 ops=2(<) → 當前 eveNo 完成次數 < 1 才通過: io.kotest.assertions.AssertionFailedError: expected:<true> but was:<false>
- `tw.tsm.tests.EveConditionEvaluatorTest` — val=2 ops=1(==) → 當前 eveNo 完成 2 次才通過: io.kotest.assertions.AssertionFailedError: expected:<false> but was:<true>
- `tw.tsm.tests.EveConditionEvaluatorTest` — 未知 param 不可因 value=0/ops=0 誤通過: io.kotest.assertions.AssertionFailedError: expected:<false> but was:<true>
- `tw.tsm.tests.EveConditionEvaluatorTest` — 會用技能等級比對 conditionValue: io.kotest.assertions.AssertionFailedError: expected:<false> but was:<true>
- `tw.tsm.tests.EveConditionEvaluatorTest` — 未學技能等級視為 0: io.kotest.assertions.AssertionFailedError: expected:<true> but was:<false>

## Tái chạy và áp dụng

Trong repo, dùng JDK 21:

```text
python tools/protocol_inventory.py
python -m unittest discover -s tools -p test_protocol_inventory.py -v
gradlew.bat :tests:test --tests '*Dvt*Test'
gradlew.bat test build --continue
gradlew.bat :app:build
```

`changes.patch` chứa đủ code/test/tool/report, áp dụng vào checkout commit nguồn bằng `git apply --check changes.patch`, rồi `git apply changes.patch`. `changed-files.zip` chứa các file thay đổi theo đúng cấu trúc repo. Các log build và XML test được giao kèm. Chưa publish/merge/deploy.
