# HANDOFF — TS Online Mobile Đa Vũ Trụ

> Checkpoint bền vững để mở chat mới và tiếp tục ngay.
>
> Cập nhật: 2026-09-29
> Repo: `BauTroiDo/tsm-dodo-server`
> Branch: `main`
> Code checkpoint gần nhất: `1706f00`

## 1. Nguyên tắc tối cao

**SERVER PHẢI THEO LUA ĐA VŨ TRỤ. Không sửa/bẻ Lua để chiều Kotlin.**

Thứ tự bằng chứng:
1. Lua Đa Vũ Trụ.
2. Client gốc Đa Vũ Trụ + File.dat/data.
3. Kotlin server.
4. Source PC chỉ tham khảo phụ khi DVT không đủ bằng chứng.

Không invent protocol/formula/semantics. Biết chắc → implement; biết một phần → PARTIAL; chưa đủ bằng chứng → UNKNOWN.

## 2. Client gốc DVT đã có để đối chiếu

Client Windows gốc Đa Vũ Trụ trên máy người dùng:

`C:\Users\QT\Downloads\TSNPCVN`

Có:
- `tsnpc.exe`
- `tsnpc_Data\Managed\Assembly-CSharp.dll`
- AssetBundles
- `ServerList.json`
- Lua/data đi kèm

Đây là nguồn xác nhận trực tiếp protocol/UI/client behavior. Khi cần audit khó, ưu tiên Lua + Assembly-CSharp.dll/client data trước khi suy từ server.

## 3. Môi trường local/remote đang dùng

Máy người dùng Windows:
- Repo chính local: `D:\Games\Ts-Offline\TSMobileOnLine\tsm_dodo`
- **Không reset/rebase/ghi đè repo này tùy tiện** vì từng có local commit riêng `387373f`.
- Clone sạch dùng cho audit/build của ChatGPT:
  `D:\Games\Ts-Offline\TSMobileOnLine\tsm_dodo_chatgpt_main`
- Java:
  `E:\Soft\AndroidStudio\jbr`
- Build:
  `./gradlew test build`

Production server:
- IP: `192.168.1.69`
- user: `Administrator`
- repo: `C:\Server-Game\tsm_dodo`
- SSH key auth đã cấu hình thành công từ máy người dùng.
- Không lưu/repeat mật khẩu trong tài liệu này.
- Trước khi pull/deploy phải kiểm tra `git status`, HEAD và process/log; không phá uncommitted work.

## 4. Quy trình cố định

1. Đọc Lua DVT/client/data liên quan.
2. Đọc Kotlin hiện tại.
3. Chỉ vá khi có mismatch có bằng chứng.
4. Thêm regression test.
5. Chạy `./gradlew test build`.
6. Commit/push trực tiếp `main`.
7. Người dùng pull/restart/runtime test.
8. Chỉ ghi runtime PASS khi người dùng xác nhận.

Người dùng thích tiến độ nhanh, ít hỏi lại; cứ tiếp tục batch tiếp theo sau khi đã có đủ bằng chứng.

## 5. Các runtime PASS quan trọng đã xác nhận

Đã PASS trước checkpoint:
- Monster spawn.
- Battle cơ bản.
- AGI combo kiểu PC.
- EXP cộng/level-up/EXP bar runtime.
- Chat EXP/gold.
- Drop item sau battle.
- Tên tiếng Việt nhân vật/pet/monster/dynamic.
- Mission ADD_STEP.
- Function Unlock Marker Missions.
- Encounter cooldown 27 giây.
- HP/SP item recovery sync.
- Stage 501–620, 621–640.
- Self-target skill 13008, 14002, 20006.

### PASS mới gần nhất

#### A. MineData / map Mỏ Khoáng
Commit:
`55e2139 Restore DVT MineData encounters`

Nguyên nhân:
Kotlin cũ parse Eve.emg nhưng `skipMineSection()` bỏ toàn bộ MineData; trong Lua DVT `EEventTrigger.Mine = 7` là trigger do server kích hoạt.

Đã sửa:
- parse MineData;
- server trigger encounter theo vùng;
- ưu tiên vùng MineData cụ thể hơn whole-map;
- dùng đúng Eve/FightData;
- giữ cooldown 27s.

Map test: **12591 Mỏ Khoáng**
Runtime: **PASS**

#### B. Inventory / equipment / bank / storage lệch packet
Commit:
`fe7633c Fix DVT NFT item wire alignment`

Triệu chứng:
- đồ đang mặc có vẻ biến mất sau restart;
- bank/storage sai;
- xuất hiện vật phẩm ma `343 Bình Trà Thái`.

Nguyên nhân thật:
DVT NFT client đọc ThingData **56 bytes**, server chỉ gửi base **35 bytes**, lệch 21 bytes/record. Byte bị lệch tạo vật phẩm ma; DB thực tế vẫn còn trang bị.

Đã sửa:
- ThingData wire = 56 bytes theo Lua DVT NFT;
- bag/equipment/bank/storage dùng codec chung;
- clear bank/storage client cache trước snapshot DB;
- regression test ngăn phantom item.

Runtime: **PASS**

Lưu ý còn một việc hardening riêng:
- các thao tác nhiều item DB chưa phải toàn bộ atomic transaction;
- restart bình thường an toàn, nhưng crash đúng giữa multi-record write vẫn nên audit/transaction hóa về sau.

#### C. Pet tự bơm máu sau battle
Commit:
`c13c543 Fix pet auto supply after battle`

Nguyên nhân:
Sau battle server chỉ cập nhật HP/SP pet trong PetSystem server. Client `MachineBox.Supply()` đọc `Role.GetFollowNpcs()`, nên client vẫn thấy HP/SP cũ và không dùng thuốc.

Đã sửa:
- sync HP/SP pet về client bằng `S:008-002`;
- sau đó mới gọi `S:065-010 MachineBox.Supply(true)`;
- chỉ trigger supply khi MacroBox đang active.

Runtime: **PASS**

Lưu ý kỹ thuật còn tồn tại:
`PetSystem.modifyFollowNpcHp/Sp` hiện clamp bằng số lớn 999999/99999 thay vì max thật. Nếu audit pet item healing sâu hơn, cần lấy max HP/SP đúng theo NpcDef/equip formula, không invent.

#### D. EXP bar luôn 0% sau đăng nhập lại
Commit:
`271e554 Restore persisted EXP on login`

Nguyên nhân:
DB lưu EXP đúng, nhưng `PlayerDataDto.fromCharacterRecord()` không gán `record.exp`, nên `S:005-003` login luôn gửi EXP=0.

Lua DVT:
- `Role.ReceivePlayerData()` đọc `EAttribute.Exp` trực tiếp từ S:005-003.
- `UIMain.UpdateInfo()` tính bar từ EXP cumulative.

Đã sửa:
- `exp = record.exp` khi build DTO login;
- thêm regression test kiểm tra EXP DB đi đúng vào packet.

Runtime: **PASS**

#### E. Pet battle EXP / SP / chat
Các commit:
- `def5f55 Restore battle EXP for active pet`
- `32b8b6f Fix pet SP sync and EXP chat`

Đã sửa:
- pet đang xuất chiến nhận EXP và lên cấp;
- EXP pet dùng cumulative semantics;
- MaxSP pet dùng đúng `Calc.GetMaxSp` thay vì công thức gần đúng;
- HP/SP pet được sync mỗi round;
- chat hệ thống hiện EXP pet sau battle.

Runtime: **PASS** (người dùng xác nhận sau `32b8b6f`).

#### F. Hằng ngày / Jiugongge — đang xử lý
Các commit liên quan:
- `709efb0 Restore DVT daily nine-grid`
- `a22b623 Refresh DVT daily activity clock`
- `bfe87d0 Rearm DVT daily UI after clock sync`
- `5461ff6 Fix DVT daily activity lifetime` — runtime vẫn FAIL
- `1706f00 Fix DVT daily runtime window` — chờ runtime test

Bằng chứng mới:
- Client gốc `C:\Users\QT\Downloads\TSNPCVN` đã được giải AES bằng key/tool của repo; các file `UIJiugongge.lua`, `Jiugongge.lua`, `ActivityTime.lua`, `protocal.lua`, `CGTimer.lua` khớp logic với bộ Lua audit.
- Ba file data Jiugongge/ActivityTime trong client gốc có SHA256 trùng File.dat của repo.
- `UIActivity.UpdateTime()` chứng minh `S:077-001 serverStartTime/serverCloseTime` là **runtime opening window** vì client so `DayOfWeek`; không phải lifetime 2022–2028.
- Activity 12 là daily: openDay=127, giờ 00:00–23:59.
- Server/client máy test cùng `SE Asia Standard Time`.

Bản `1706f00` giữ server clock theo local wall-clock OLE Date và gửi **cửa sổ ngày hiện tại 00:00–23:59** cho activity 12. Chưa ghi PASS cho tới khi người dùng xác nhận.

## 6. EXP semantics hiện tại

DVT dùng **cumulative lifetime EXP**, không phải remainder EXP.

UI:
`slider_Exp.value = exp - Calc.GetNeedTExp(level, turn)`

Server battle đã có migration cho dữ liệu legacy remainder:
- nếu EXP < cumulative floor của level hiện tại thì chuyển sang cumulative.
- level-up không trừ EXP khỏi state.
- persistence lưu cumulative EXP.

Không đổi lại về remainder.

## 7. EVE/operator fixes quan trọng đã làm

Các commit liên quan:
- `b86d4f5 Restore DVT Eve condition comparison semantics`
- `66e1ccd Align Eve condition tests with DVT Lua operators`

Theo `DungeonCriteriaData.CompareResult`:
- 1 = ==
- 2 = <
- 3 = <=
- 4 = >
- 5 = >=
- 6 = !=
- Eve giữ extension 0 = ==

`conditionClass=12`:
- param=0 dùng completion count của current Eve.
- non-zero param chưa đủ authoritative semantics → giữ conservative false, không tự đoán.

## 8. Điểm audit đang dở / NEXT

### Ưu tiên chính: EVE `conditionClass=7, conditionParameter=8`

Đã scan Eve.emg:
- khoảng **468 occurrence**.
- local user commit `387373f` từng đoán param8 liên quan arena participant, nhưng **chưa đủ authoritative DVT proof**.
- Không copy/push logic đó chỉ vì tồn tại ở local branch.

Cần tiếp tục đối chiếu:
- Lua DVT;
- client gốc `TSNPCVN\tsnpc_Data\Managed\Assembly-CSharp.dll`;
- Eve.emg scene mẫu;
- TalkData/UI arena/PK/event liên quan;
- File.dat nếu cần.

Chỉ khi xác định rõ param8 là gì mới vá Kotlin.

### Gap lớn khác đã phát hiện: mainKind 36 — UIMessageboard

Lua DVT có protocol đầy đủ:
- C:036-001 request board
- C:036-002 post message
- C:036-003 delete message
- S:036-001 list
- S:036-002 add
- S:036-003 open

Current Kotlin chưa có handler mainKind36.
Có thể làm sau EVE param8 hoặc khi user ưu tiên.
Cần audit board kind/persistence/date format/RoleCount[13]/broadcast semantics trước khi implement, không tự đoán.

### Inventory durability hardening
Sau lỗi item wire đã PASS, còn nên audit:
- equip/unequip;
- move bag↔bank/storage;
- multi-record DB transaction;
- crash-safety.

Đây là hardening khác với lỗi phantom item đã sửa.

## 9. Những phần không nên mở lại nếu không có triệu chứng mới

- HP/SP item sync: PASS.
- Encounter cooldown 27s: PASS.
- MineData map 12591: PASS.
- ThingData 56-byte NFT alignment: PASS.
- Pet post-battle auto supply: PASS.
- Login EXP persistence: PASS.
- Generic mission ADD_STEP / unlock markers: PASS.
- AGI combo: PASS.

## 10. Recent commits để chat mới định vị nhanh

```text
1706f00 Fix DVT daily runtime window                 — runtime PENDING
32b8b6f Fix pet SP sync and EXP chat                 — runtime PASS
def5f55 Restore battle EXP for active pet            — runtime PASS via 32b8b6f batch
5461ff6 Fix DVT daily activity lifetime              — runtime FAIL
271e554 Restore persisted EXP on login               — runtime PASS
c13c543 Fix pet auto supply after battle             — runtime PASS
fe7633c Fix DVT NFT item wire alignment              — runtime PASS
55e2139 Restore DVT MineData encounters              — runtime PASS
```

## 11. Cách bắt đầu chat mới

Chat mới chỉ cần đọc file này, sau đó:
1. kiểm tra HEAD GitHub hiện tại;
2. ưu tiên xác nhận runtime `1706f00` cho Hằng ngày/Jiugongge;
3. nếu vẫn FAIL, audit tiếp packet/state runtime dựa trên client gốc TSNPCVN, không đoán;
4. sau khi Hằng ngày PASS mới quay lại **EVE conditionClass=7 / param=8**;
5. không yêu cầu người dùng kể lại lịch sử và không mở lại các phần đã runtime PASS nếu không có regression mới.

**Điểm tiếp tục hiện tại: Hằng ngày/Jiugongge đang chờ runtime test `1706f00`; pet EXP/SP/chat đã PASS.**
