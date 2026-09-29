# HANDOFF — TS Online Mobile Đa Vũ Trụ

> Checkpoint bền vững để mở chat mới và tiếp tục ngay.
>
> Cập nhật: 2026-09-29
> Repo: `BauTroiDo/tsm-dodo-server`
> Branch: `main`
> Code checkpoint gần nhất: `97e5ffa`

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

#### G. Pet lifecycle / pet ảo / HP bất thường — runtime PASS
Commit:
- `9cc236b Align captured pets with DVT lifecycle`

Triệu chứng người dùng:
- pet mới bắt hiện LV1 nhưng không lên cấp đúng;
- có pet/đối tượng theo sau tên Trung, người dùng cho rằng không có trong đội;
- HP pet hiển thị bất thường.

Bằng chứng:
- DVT `Role.FollowNpcAppear` khởi tạo pet mới ở **LV1**, EXP=`Calc.GetUpExp(1, turn)`, sau đó MaxHP/MaxSP=`Calc.GetMaxHp/Sp`.
- PC `bboycs/tsonline-server-tl/TS_Server/Client/TSPet.cs:initPet()` cũng khởi tạo LV1.
- Với NPC 10046 Binh Dao Lẻ: HPX=8, SPX=4; Formula.dat DVT cho LV1 **MaxHP=113, MaxSP=69, EXP floor=6**, khớp chính screenshot người dùng.
- Kotlin cũ sai: `addFollowNpc()` dùng `npcDef.level` (spawn level) và `hpBase/spBase` làm pet state.
- Production DB charId=31 hiện có 4 carried pets (slots 1..4), nên một số pet nhìn thấy trên map có thể là record carried thật; PC source cũng broadcast toàn bộ carried pets ra map. Tuy nhiên client stale slots vẫn có thể tồn tại vì S15-8 chỉ add/update, không xóa slot vắng.

Đã sửa trong `9cc236b`:
- pet mới bắt/nhận bắt đầu LV1, EXP floor đúng, loyalty 60;
- HP/SP khởi tạo từ Calc.GetMaxHp/Sp, không dùng hpBase/spBase;
- HP/SP legacy vượt max bị clamp khi load;
- item heal SP/HP clamp theo max thật thay vì 999999/99999;
- level-up restore HP/SP như PC;
- login gửi recall + xóa 4 follow slots trước S15-8 để loại stale/ghost client object;
- snapshot pet được sort theo slot.
- Full `./gradlew test build`: PASS.

Runtime: **PASS**. Sau các bản wire + migration tiếp theo, người dùng xác nhận pet mới/cũ hoạt động đúng theo lifecycle DVT.

#### H. Duplicate capture + persistence hardening — chờ runtime
Commit:
- `c9c44f3 Harden DVT capture and persistence`

Duplicate capture:
- DVT `FightRoleController.DoClick` chặn cùng `npcId` nếu đang ở `Role.GetFollowNpc()` hoặc `Inn.HaveNpc()`.
- `Inn.HaveNpc()` kiểm tra inn + warriorRepository; **không kiểm tra Cart.followNpcs**.
- PC `TSCharacter.addPet()` cũng chỉ kiểm tra mảng pet mang theo, cart là vùng riêng.
- Kotlin cũ chặn cả cart nên nghiêm hơn Lua DVT.
- `c9c44f3`: manual capture cho phép bắt lại cùng loại khi bản cũ đã cất trong cart; vẫn chặn nếu cùng loại còn mang theo hoặc nằm trong inn/warehouse. Không cho phép 2 bản cùng npcId cùng tồn tại trong carried roster, đúng client DVT.

Persistence:
- Production log xác nhận AutoSaveService thực sự chạy nhưng chu kỳ cũ là **3 phút** (ví dụ 04:47:48 → 04:50:48).
- Đây là write-back cache; MySQL không tự mất record. Nếu reset/kill server trước autosave/disconnect thì runtime state gần nhất chưa kịp ghi DB có thể mất.
- `c9c44f3`: autosave giảm còn **30 giây**; graceful shutdown gọi flush đồng bộ trước khi đóng socket; battle end checkpoint ghi ngay character EXP/level/stats + bag + gold + pet sau mỗi trận.
- Full `./gradlew test build`: PASS.

Runtime cần test:
1. cất pet loại A vào cart, để carried còn slot trống;
2. bắt lại cùng loại A — phải bắt được;
3. đánh 1 trận nhận EXP/vàng/drop, restart server ngay;
4. login lại xác nhận EXP/vàng/drop/pet vẫn còn.

#### I. Hidden carried pets / S15-8 wire — runtime PASS
Commit:
- `764204e Fix DVT carried pet full-sync wire`

Triệu chứng:
- UI đội ngũ chỉ hiện 1 pet, nhưng MySQL của `CaoYếnDao` (character_id=31) đang có **4 pet storage_type=1** ở slot 1..4.
- Vì server cache thấy carried roster đủ 4 slot, manual catch bị validator từ chối; client lại chỉ thấy 1 pet nên người chơi bấm lưới nhưng nhân vật đứng yên, tưởng pet bị ẩn.
- Client log thực tế sau login đã chứng minh lỗi parse:
  `Received Message 015 008`
  → `FollowNpc[13][1] Appear !!`
  → `FollowNpc[13][0] Appear !!`
  thay vì slot 1,2,3,4.

Nguồn sự thật mạnh nhất:
- Đã giải AES trực tiếp **client gốc TSNPCVN** `C:\Users\QT\Downloads\TSNPCVN\AssetBundles\Lua\Logic\Role.lua`.
- Client gốc khác bản Lua trong repo ở đúng wire S15-8:
  - tên pet: `ReadString(len, DataManager.encoding_Unicode)` → UTF-16LE;
  - `sublimeCount`: UInt16;
  - `hpPillCount`: UInt16;
  - `spPillCount`: UInt16.
- Kotlin cũ gửi name Big5 và ba field trên bằng Byte, làm record đầu tiên lệch offset; các pet sau bị đọc sai/ẩn.
- `LuaHelper.CheckDefine("NFT")` trong Assembly-CSharp client gốc đã decompile và xác nhận **NFT=true**, nên ThingData 56-byte hiện tại là đúng; lỗi không nằm ở NFT extension.

Đã sửa `sendFollowNpcFullData()`:
- name UTF-16LE byte-len;
- sublime/hpPill/spPill UInt16;
- thêm test `DvtFollowNpcFullWireTest` parse 4 pet liên tiếp theo chính layout client gốc.
- Full `./gradlew test build`: PASS.

Runtime: **PASS**. Người dùng xác nhận roster pet đã hiện đúng sau khi sửa S15-8; không còn tình trạng pet bị ẩn do packet lệch.

#### J. Legacy pet level seed — runtime PASS
Commit:
- `4bbb782 Migrate legacy DVT pet seed levels`

Sau khi S15-8 được sửa, roster hiện đúng nhưng các pet cũ vẫn mang level sai do đã được tạo trước `9cc236b`: code cũ lấy `NpcDef.level` (cấp quái ngoài map) làm level khởi đầu.

Bản `4bbb782` thêm cột DB `character_pets.seed_fixed`:
- record cũ được MySQL/Exposed thêm cột với mặc định false;
- pet mới tạo bằng code hiện tại có `seedFixed=true`;
- khi login, mỗi record cũ chỉ migrate **một lần**;
- trừ phần cumulative EXP seed sai ở spawn level, cộng lại seed LV1 chuẩn DVT;
- giữ nguyên EXP thực tế người chơi đã kiếm sau lúc bắt;
- tính lại level từ EXP sau sửa;
- tái dựng skill point còn lại theo số level đã thực tăng và skill level đã tiêu;
- HP/SP chỉ clamp về max mới, không xóa trang bị/pill/progress khác.

Với snapshot DB hiện tại của `CaoYếnDao` trước migration:
- Thủy Đậu Yêu: spawn LV3, current LV18 / EXP24670 → corrected EXP24629, vẫn LV18 (đã train thật nhiều);
- Sĩ Binh: spawn LV6, EXP405 đúng bằng seed sai → corrected EXP6, **LV1**;
- Binh Đao Lẻ: spawn LV17, EXP22199 → giữ 4080 EXP kiếm thật → corrected EXP4086, **LV11**;
- Binh Tinh Giáp: spawn LV18, EXP22672 → giữ 180 EXP kiếm thật → corrected EXP186, **LV4**.

Không ép tất cả pet cũ về LV1 vì sẽ làm mất EXP người chơi đã thực kiếm. Pet mới bắt sau patch luôn bắt đầu LV1.

Full `./gradlew test build`: PASS.
Runtime `4bbb782`: **PASS**. Người dùng xác nhận sau khi thả/cất pet cũ và bắt pet mới, pet bắt mới hoạt động đúng theo seed LV1.

#### K. Hằng ngày/Jiugongge mission progress — chờ runtime
Commit:
- `5c686ed Fix DVT daily mission progress tracking`

Triệu chứng:
- 9 ô Hằng ngày đã hiện đúng, thời gian hoạt động đã mở;
- người dùng làm đủ điều kiện nhưng ô vẫn giữ 0/x và không hoàn thành.

Nguyên nhân đã xác nhận:
- Jiugongge client **không tự suy ra gameplay từ server**; nó đọc `RoleCount`/BitFlag local.
- Khi nhận S:085-002, `RoleCount.Set()` phát `CheckCondition(ECondition.RoleCount)`.
- Khi nhận S:081-001 với flag 1490..1499, `BitFlag.Set()` phát `CheckCondition(ECondition.Flag)`.
- Khi điều kiện đạt, `Jiugongge.CheckCompeleteKind()` mới gửi C:091-002 về server.
- Kotlin trước `5c686ed` chỉ load RoleCount lúc login nhưng **không tăng các counter tương ứng khi gameplay diễn ra**. Production DB character 31 không có row 151/160/163/190/191/192 và không có flag 1497..1499, nên UI luôn 0.

Board 1 authoritative từ `IOS_JiugonggeMission_C.dat` + `IOS_TextData_C.dat` client gốc:
- mission 2: RoleCount 160 >=1 — `Lần đầu nhận được thú cưỡi`
- mission 3: RoleCount 151 >=2 — `Bắt được võ tướng`
- mission 4: flag 1499 — `Xuất chiến võ tướng`
- mission 5: RoleCount 190 >=10 — `Phân phối điểm kỹ năng của nhân vật`
- mission 6: RoleCount 191 >=20 — `Phân phối điểm thuộc tính của nhân vật`
- mission 7: RoleCount 163 >=50 — `Tiến hành chiến đấu`
- mission 8: FriendCount >=5 — `Kết giao hảo hữu`
- mission 9: flag 1498 — `Lần đầu tổ đội`
- mission 10: flag 1497 — `Gia nhập quân đoàn`

`5c686ed` thêm `DailyProgressSystem` và nối các gameplay event:
- bắt pet thành công → RoleCount 151 +1 + S85;
- nhận thú cưỡi lần đầu (item hoặc quest tutorial) → RoleCount 160 +1 + S85;
- pet xuất chiến → flag1499 + S81;
- tiêu skill point nhân vật → RoleCount190 + số điểm thật đã tiêu + S85;
- phân phối stat → RoleCount191 + số điểm + S85;
- kết thúc battle → RoleCount163 +1 + S85;
- friend count dùng historical maximum RoleCount192 và S14-17, đúng `Social.maxRecordFriendCount`;
- tổ đội lần đầu → flag1498 + S81;
- login reconcile mount đã sở hữu và pet đang xuất chiến để người chơi cũ không phải làm lại hai điều kiện đó.

Mission 10 / flag1497 chưa có gameplay hook vì server hiện **chưa có subsystem quân đoàn/organization**; không invent semantics.

Regression:
- `DvtDailyProgressTest`: kiểm tra RoleCount S85 + flag S81 bridge.
- `DvtJiugonggeTest`: khóa chính xác toàn bộ mapping mission 2..10 theo File.dat.
- Full clean `gradlew clean test build`: **BUILD SUCCESSFUL**.

Runtime `5c686ed`: phần gameplay bridge đã hoạt động một phần; người dùng xác nhận bảng bắt đầu check điều kiện, nhưng UI dừng ở ô skill-point và còn `New Text`.

#### L. Hằng ngày/Jiugongge special RoleCount wire + reward claim — chờ runtime
Commit:
- `36d3bb0 Fix DVT daily special RoleCount wire`

Triệu chứng runtime:
- ba ô đầu render bình thường;
- ô `Phân phối điểm kỹ năng của nhân vật` hiện description nhưng progress còn `New Text`;
- các ô phía sau giữ prefab trắng/`New Text`;
- người dùng báo chưa nhận được quà Daily.

Nguyên nhân UI đã xác nhận trực tiếp từ Lua DVT:
- `RoleCount.Get(index)` có nhánh đặc biệt cho **190..195** và đọc `this.countDatas[index].value >> 16`;
- nhánh này không nil-check `countDatas[index]`;
- server cũ không seed 190..195 khi login, nên board văng đúng ở mission dùng RoleCount190;
- server cũng gửi 190/191 dưới dạng số logic thô, trong khi client DVT đọc high-16, nên sau khi tiêu điểm client vẫn thấy 0.

Đã sửa:
- seed RoleCount190..195 = 0 khi login;
- giữ DB/runtime server ở số logic bình thường;
- chỉ encode S:085-001 và S:085-002 của index190..195 thành `logicalValue << 16`, đúng client DVT;
- skill/stat Daily tiếp tục cộng số điểm thực đã tiêu, không đổi semantics server;
- thêm regression test cho cả login wire và runtime update wire.

Reward authoritative:
- board Daily có **7 reward**, không phải 9 reward theo từng mission:
  - 3 hàng;
  - 3 cột;
  - 1 reward tổng;
- reward 1 chỉ nhận khi mission 2/3/4 cùng hoàn thành;
- reward 7 chỉ nhận sau khi 6 reward hàng/cột đã nhận.
- snapshot production char31 lúc audit: mount=1/1, catch=1/2, deploy flag1499=true, nên **chưa có hàng hoàn chỉnh** và client đúng ra chưa gửi C:091-003.

Đã thêm regression:
- hoàn thành hàng đầu → claim reward index1;
- item 31207 x2 được add bag;
- getFlag1541 được set;
- server trả S:091-003 success.
- Full `gradlew clean test build`: **BUILD SUCCESSFUL**.

Runtime `36d3bb0`: **PASS**.
- người dùng xác nhận UI Daily đã hết lỗi `New Text`/ô trắng;
- progress/condition đã hoạt động;
- reward claim hoạt động;
- còn 3 mission phụ thuộc môi trường server test/multi-user/subsystem nên chưa thể runtime-test riêng; đây là UNTESTABLE trong môi trường hiện tại, không tính FAIL của Jiugongge.

#### M. ActivityModel C:124-004 timed refresh — chờ runtime
Commit:
- `97e5ffa Handle DVT ActivityModel refresh`

Triệu chứng production:
- log lặp warning `Chưa đăng ký mainKind=124 subKind=4 (0 bytes)`.

Bằng chứng Lua DVT:
- `Game.lua` gọi `UIActivityModel.UpdateRedDot()` sau khi vào game;
- `UIActivityModel.UpdateRedDot()` tự đăng timer 180 giây và gửi `Network.Send(124, 4)` không payload;
- `S:124-004` có wire:
  `count(UInt32) + << missionId(UInt32) + finishCount(UInt32) >>`;
- client xử lý từng record bằng `ActivityModel.GetFinishCount(data)`;
- `ActivityModel.ActivityData` và `PlayerAwardCount` khởi tạo rỗng và chỉ được populate khi server gửi dynamic ActivityModel definitions qua `S:124-000`.

Current server chưa implement/publish `S:124-000` hay subsystem ActivityModel động. Vì vậy không được invent mission/progress. Compatibility semantics hiện tại:
- nhận đúng `C:124-004` payload rỗng;
- trả `S:124-004` với `count=0`;
- payload bất ngờ bị reject;
- các subKind 124 khác vẫn giữ unsupported/warn cho tới khi audit riêng.

Đã sửa:
- thêm `Opcodes.ACTIVITY_MODEL = 124`;
- thêm/register `ActivityModelHandler`;
- thêm regression `DvtActivityModelWireTest` khóa packet `124,4,0 + UInt32(0)`;
- full `gradlew clean test build`: **BUILD SUCCESSFUL**.

Runtime `97e5ffa`: **PENDING**. Sau deploy/restart cần xác nhận warning 124:4 không còn lặp và không phát sinh lỗi client.

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

### Ưu tiên hiện tại: runtime `mainKind=124 subKind=4`

Daily/Jiugongge đã runtime PASS.

`97e5ffa` đã implement compatibility handler dựa trên Lua DVT authoritative:
- C:124-004 là refresh request rỗng mỗi 180 giây;
- current server chưa publish S:124-000 nên response hiện tại là S:124-004 count=0;
- full build/test PASS.

Việc kế tiếp:
1. deploy/restart production với `97e5ffa`;
2. xác nhận warning `mainKind=124 subKind=4 (0 bytes)` biến mất;
3. kiểm tra client không có regression;
4. nếu PASS thì chuyển sang EVE param8.

### Sau đó: EVE `conditionClass=7, conditionParameter=8`

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
97e5ffa Handle DVT ActivityModel refresh              — runtime PENDING
36d3bb0 Fix DVT daily special RoleCount wire         — runtime PASS
5c686ed Fix DVT daily mission progress tracking      — gameplay bridge working; superseded by 36d3bb0 for UI wire
1706f00 Fix DVT daily runtime window                 — runtime timing fixed; feature still needed progress bridge
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
2. Daily/Jiugongge `36d3bb0` đã runtime PASS; không mở lại nếu không có regression mới;
3. ưu tiên deploy/restart và runtime test `97e5ffa` cho **mainKind=124 subKind=4**;
4. xác nhận production không còn warning 124:4 lặp mỗi 180 giây và client không phát sinh lỗi;
5. nếu PASS, chuyển sang **EVE conditionClass=7 / param=8**;
6. mission Daily phụ thuộc environment/subsystem chưa test riêng vẫn là UNTESTABLE, không tự invent;
7. không yêu cầu người dùng kể lại lịch sử.

**Điểm tiếp tục hiện tại: `97e5ffa` ActivityModel C124-4 đã build/test PASS, đang chờ production runtime; sau đó là EVE conditionClass=7 / param=8.**
