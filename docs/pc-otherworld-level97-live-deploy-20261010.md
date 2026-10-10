# TS Đa Vũ Trụ PC — chọn cấp quái Dị giới, C097-002, 10/10/2026

**Trạng thái:** PR #15 đã merge vào GitHub main, code đã triển khai live, server khởi động thành công; **chưa thử thao tác dropdown và lượng EXP trực tiếp sau restart bằng client**.

## Báo lỗi và bằng chứng gốc

- Người chơi vào **Dịch Chuyển → Giới hạn Khu luyện công → Dị giới TS** (scene 49942), ảnh UI trong map có timer, menu **Đẳng cấp quái dị giới** đang chọn **55**, danh sách 10,25,40,55,70,85...
- Log server lúc 07:19–07:22 ngày 10/10 cho charId31 vẫn ghi `missionId=13137 step=1`, event MineData được kích hoạt nhưng nhận **90 EXP** mỗi trận, tức đang đánh nhóm của stage1 chứ không theo Lv55 trên giao diện.
- Lua DVT gốc `_native_dump_dvt115/_lua_client_decrypted/UI/UIMain.lua`:
  - `UpdateAnotherWorldLv(missionId)`: đọc nhiệm vụ, số lần chuyển sinh `turn`, đặt menu theo `anotherWorldLvDatas`.
  - `OnDropChange_AnotherWorldLv(step)`: `step = step + turn*(table.Count(anotherWorldLvDatas)/4)+1`, gửi `C:097-002` với **1 byte** stage.
  - DVT `Common/protocal.lua` khai báo C97-002 chính xác. Backend `LimitTimeDungeonHandler` trước sửa bỏ qua packet này và ghi comment sai là client không có sender.
- `AssetBundles/Data/IOS_AnotherWorldLv_C.dat` có **60 rows** = Int32 count + 60 × {id UInt16,level Byte,turn Byte}; 4 nhóm chuyển sinh, mỗi nhóm 15 cấp: **10,25,40,55,70,85,100,110,120,130,140,150,160,170,180**. Vd turn0 Lv55→id4, turn1 Lv55→id19, turn2→id34, turn3→id49.
- Các 60 stage 1..60 tương ứng với MineData/Eve missionId13137 và FightData thật tại map49942. Không có quái đứng tĩnh trong map.

## Bản vá GitHub

[PR #15](https://github.com/BauTroiDo/tsm-dodo-server/pull/15), squash merge commit **`09c91323661cc2cf8e9ea5f6a1f4a7c536917450`**, CI run **`38009245881` SUCCESS**.

8 file:
1. `domain/src/main/kotlin/tw/tsm/domain/data/AnotherWorldLvDef.kt`: row type id/level/turn.
2. `data/src/main/kotlin/tw/tsm/data/loaders/AnotherWorldLvDatLoader.kt`: parser chính xác `AnotherWroldLvData.lua`.
3. `data/src/main/kotlin/tw/tsm/data/GameDataManager.kt`: nạp `IOS_AnotherWorldLv_C.dat` và expose `anotherWorldLvDefs`.
4. `gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/QuestSystem.kt`: `selectTrainingMissionStage` dưới mutex theo charId, lưu DB, trả stage cũ.
5. `gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/LimitTimeDungeonSystem.kt`: `selectAnotherWorldLevel`, xác thực map, quyền nhân vật, rebirth group, không trong trận, thời gian còn, ID hợp lệ; ghi mới rồi gửi `S024-001` (+delta) hoặc `S024-002` (-delta).
6. `app/src/main/kotlin/tw/tsm/app/handlers/LimitTimeDungeonHandler.kt`: thực thi C097-002 1-byte, chặn event đang xử lý / battle.
7. `app/src/main/kotlin/tw/tsm/app/ServerModule.kt`: DI bảng level DVT.
8. `tests/src/test/kotlin/tw/tsm/tests/AnotherWorldLevelSelectionRegressionTest.kt`: 7 test, gồm level55 turn0→stage4, giảm cấp, giả mạo turn, sai map, đang đánh, hết giờ, lưu nguyên tử/idempotent.

Các luồng quest mainline/Elf/shop không thay đổi. **Không có hệ số nhân kinh nghiệm riêng được thêm** vì không tìm được multiplier xác thực cho map trong bảng/Lua này; EXP vẫn theo `BattleRewardCalc` (NPC level và buff). Nếu người chơi nhớ map nhân x2/x5/x10, cần xác minh gốc hoặc yêu cầu hệ số cụ thể trước khi áp dụng.

## Triển khai Windows production

- Host `192.168.1.69`, thư mục `C:\Server-Game\tsm_dodo`; service task `TSM_DODO_ONESHOT`, port `6613`.
- Lấy 7 file từ đúng nhánh GitHub, so Git blob SHA; `GameDataManager.kt` **trên Windows có thay đổi cục bộ**: SHA git blob `1b30fc4d321917c1b12b4bc9b575fbdc324ed32b` trong khi GitHub main trước PR là `b6932045c7aabf2dfaa46e8e7089b21a3d703e4e`. **Không ghi đè**: chỉ chèn đúng hai đoạn khai báo/load bảng DVT vào nội dung server hiện có, xác minh SHA gốc trước khi patch và đọc lại file sau khi ghi. Các file còn lại đều trùng GitHub main trước PR.
- Sao lưu nguyên vẹn các file cũ và manifest tại `C:\Server-Game\tsm_dodo\backups\OtherWorld97_before_PR15_20261010`; Gradle full log `full_test.log`. Không `git pull`, reset, checkout để không ảnh hưởng >170 local changes.
- Windows `.\gradlew.bat :tests:test --offline --console=plain`: **BUILD SUCCESSFUL in 1m56s**. XML: **847 tests, 0 failures, 0 errors, 5 skipped, 158 suites**. 7/7 `AnotherWorldLevelSelectionRegressionTest` passed.
- `schtasks.exe /End /TN TSM_DODO_ONESHOT`, kiểm tra port6613 ngừng listen; `schtasks.exe /Run /TN TSM_DODO_ONESHOT`.
- Runtime **07:37:05.981 UTC+7** log `Nạp IOS_AnotherWorldLv xong: 60 mốc cấp quái`. Runtime **07:37:08.891 10/10/2026** `tw.tsm.network.NettyGameServer - Máy chủ khởi động: 0.0.0.0:6613`, `tw.tsm.app.Main - Máy chủ đã sẵn sàng, đang chờ kết nối...`. Live PID **7072** lúc xác minh. Không có exception trong đoạn startup đã kiểm.

## Việc còn cần người chơi xác minh

1. Thoát hẳn game, đăng nhập lại, vào **Dị giới TS**, chọn **55** ở dropdown, đi bộ để kích hoạt trận đánh.
2. Theo dõi log `DVT training monster level: cid=31 map=49942 turn=0 level=55 mission=13137 step=1 -> 4`; MineData điều kiện `missionId=13137 step=4` phải đúng, quái cấp xấp xỉ55 và lượng EXP sẽ theo quái, cao hơn nhóm 10.
3. Nếu không có log trên, cần kiểm tra client có gửi C97-002 khi click dropdown, hoặc Lua runtime trong staging client khác Lua dump gốc.
4. Người chơi hỏi về map được **X kinh nghiệm**. Chưa có bằng chứng multiplier riêng từ `IOS_AnotherWorldLv_C.dat` hay Lua; không được tự quy định X. Cần xác minh với bản gốc hoặc hỏi hệ số X mong muốn trước khi thêm boost. Không tự thay EXP vô căn cứ.
5. Nếu còn lỗi, điều tra log `C097-002` / `DVT training monster level` / `Mine encounter`, cập nhật PR, CI, triển khai chọn lọc và báo cáo.

Tài liệu cũ: [PR14 sửa MineData khởi tạo mission](pc-limit-training-map49942-encounters-live-20261010.md).
