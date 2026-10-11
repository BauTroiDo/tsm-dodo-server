# DVT: Ledger phần thưởng dungeon và từng người tham chiến

## Cơ sở từ server hiện tại

- `BattleManager.handleBattleEnd` phát KillNum, tiêu hao Phúc Thần, rơi vật phẩm, EXP người chơi/võ tướng, vàng, bắt tướng **trước** callback `onBattleEndCallback`. PR #36 bổ sung Eve reward fence cho nhân vật có `EventSession`, nhưng không giải quyết mọi người tham gia trận.
- `BattleManager.nextBattleId` bắt đầu lại từ 1 mỗi lần khởi động JVM. **Không dùng numerical battleId làm khóa bền vững** vì có thể khóa nhầm hoặc nhận thưởng lặp.
- `DungeonSystem.settleAfterBattle` lưu `run.settled` rồi mới cộng lượt ngày và trao item; `settleWeeklyBattle` trao item, ghi mission/dailyFlag và xóa run ở các bước khác nhau. `claimWeeklyAllPass` trao item trước khi đánh dấu nhiệm vụ. Không có một transaction bao phủ toàn bộ.
- `DungeonRunRecord` đang lưu `dungeonId`, `instanceId`, `startedAtMillis` qua restart. Bộ khóa này có ích cho dungeon thật, nhưng luồng legacy `onSceneReady` tạo run mới khi không khôi phục được dữ liệu cũ: **không thể xem đó là cùng lượt** mà không đối soát.
- PR #35 và #36 dùng EveCount làm số lượt Eve. Không được lấy EveCount làm biên nhận cho thưởng dungeon/chiến đấu độc lập, tránh tăng sai quest count.

## Primitive mới trong PR #37 (chưa nối luồng gameplay)

Bảng `character_reward_ledger` khóa duy nhất `(character_id,reward_kind,source_key)` có `attempt_token`, thời điểm reserve, thời điểm settle nullable.

- `reserve`: chỉ trả TRUE khi INSERT thật sự thêm hàng mới. Khi hàng `PENDING` hoặc `SETTLED` đã tồn tại, trả FALSE ngay cả cùng token. Đây là khác biệt cố ý so với ledger Eve; không cho tái phát item/gold/EXP sau crash giữa một phần các hiệu ứng.
- `character_reward_pending_scopes`: khóa riêng `(character_id, reward_kind)` trỏ đến source + token duy nhất đang chờ. INSERT scope và INSERT ledger thực hiện **cùng transaction**. Vì vậy một run mới thay `source_key` cũng không thể nhận phần thưởng cùng loại khi người chơi vẫn còn `PENDING` cũ. `settle` đánh dấu biên nhận và xóa *chính xác scope hiện tại* trong một transaction; biên nhận cũ vẫn giữ vĩnh viễn.
- Khóa scope này cố ý bảo thủ: một claim dungeon thường chưa giải quyết có thể chặn dungeon thường khác của cùng nhân vật. Không chặn các `reward_kind` độc lập như BATTLE_GENERIC; quy tắc gộp nhóm nguồn thưởng DVT cần xác minh riêng.
- `settle`: khóa hàng, kiểm tra token, ghi `settled_at_epoch_ms` **một lần**, giữ biên nhận vĩnh viễn. Gọi lại không thành công, không xóa hàng.
- `find` / `unresolvedForCharacter`: chỉ đọc, phục vụ quản trị viên đối soát. Không tự hủy `PENDING` vì không thể biết phần thưởng đã cấp được bao nhiêu.
- `RewardLedgerSourceKeys.dungeon`: dùng `dungeonId`, `startedAtMillis`, `instanceId` đã được khôi phục, cộng với bước thưởng. Một lượt dungeon khác cần khóa khác.
- `RewardLedgerSourceKeys.battle`: yêu cầu UUID **được lưu bền trước khi vào trận**, không chấp nhận battleId số nguyên tạm. Mỗi nhân vật dùng primary key riêng.
- `RewardLedgerSourceKeys.weeklyAllPass`: tuần/chu kỳ theo chính sách DVT và trang thưởng; chu kỳ phải tính từ dữ liệu ngày reset chính xác, không dùng `System.currentTimeMillis()` tại điểm phát thưởng.

## Điều kiện ngăn lách ledger bằng lượt mới

Bảo vệ theo `source_key` **chưa đủ**: nếu một phần thưởng cũ có trạng thái `PENDING`, một lượt dungeon hoặc trận chiến được tạo mới với UUID khác có thể vẫn phát thưởng tương đương. Trước khi cho người chơi bắt đầu một run mới, điều phối phải tra `unresolvedForCharacter` và áp dụng chính sách chặn đúng phạm vi (dungeon/weekly/nguồn thưởng tương ứng) cho đến khi đối soát. Đã dùng bảng khóa scope riêng với khóa chính `(character_id, reward_kind)` để serialize đồng thời bước giữ quyền ngay trong DB. Còn cần kiểm tra chính sách `reward_kind` và game-client thực tế để không chặn nhầm các lượt thưởng không liên quan.

Không tự coi pending nghĩa là đã nhận phần thưởng; cũng không tự xóa để mở khóa. Phải ghi đủ loại nguồn thưởng, chu kỳ reset và run identifier trong báo cáo đối soát. Mọi hành động quản trị (hoàn tiền, trao bù, release) cần được audit để không nhân đôi.

## UUID dungeon bền vững (bản cập nhật)

- `ActiveDungeonRun.runToken` là UUID ngẫu nhiên được sinh **một lần khi tạo lượt**, lưu ở `CharacterDungeonRuns.run_token`, nạp lại qua `DungeonRunRepositoryImpl.load` và giữ nguyên khi `copy(settled=true)`.
- `RewardLedgerSourceKeys.dungeon` dùng UUID đã lưu, không chỉ dựa trên `startedAtMillis` và `instanceId`. Lượt chơi mới vẫn có khóa riêng dù bắt đầu cùng mili-giây.
- Cột `run_token` có mặc định chuỗi rỗng để migration không giả mạo UUID cho hàng cũ. `DungeonSystem` kiểm tra token của phiên RAM và phiên DB giống nhau, khác rỗng và có nguồn dungeon hợp lệ trước khi cho payout. Hàng cũ cần admin xác minh, không tự phát UUID thay thế khi đọc.
- Trước khi ghi claim, hệ thống kiểm tra read-only túi còn đủ chỗ cho batch vật phẩm hợp lệ. Từ chối túi đầy khi chưa có claim để tránh giữ `PENDING` vô ích; nhưng nếu sức chứa đổi giữa preflight và ghi item thì claim vẫn có thể bị treo và phải đối soát.

## Thử nghiệm tích hợp dungeon thường (PR #38, chưa phát hành)

- `ServerModule` đã inject `RewardLedgerRepositoryImpl` vào `DungeonSystem` trên nhánh PR này (không áp dụng live). Với `settleAfterBattle(playerWin=true)`, trước khi tăng dailyFlag, cập nhật inventory hoặc đánh dấu settled trong RAM/DB, hệ thống kiểm tra `DungeonRunRepository.load` khớp **dungeonId + startedAtMillis + instanceId** với lượt hiện tại và có điểm gốc được lưu. Lượt legacy dựng lại từ scene nhưng không xác minh được nguồn sẽ fail closed.
- Sau đó dùng `RewardLedgerSourceKeys.dungeon(run, "TERMINAL")` để `reserve` trong database, chặn source cũ và tất cả source mới cùng `DUNGEON_NORMAL` khi claim pending. Nếu MySQL unavailable hoặc claim đã tồn tại, trả trạng thái lỗi mà **không trao item và không tăng dailyFlag**.
- Khi đã có claim, các bước settledRun, daily counter, xác định và thêm batch reward diễn ra như luồng cũ. Chỉ khi xong mới `settle` để viết biên nhận. Packet hiển thị item chỉ được gửi sau biên nhận thành công. Nếu lỗi ở bất kỳ bước nào sau reserve, claim pending vẫn tồn tại để đối soát, không tự retry.
- Trường hợp dungeon không có dòng thưởng được hỗ trợ vẫn ghi dailyFlag và chốt biên nhận, giữ hành vi thành công của DVT nếu không có item hợp lệ. Nhánh thua trận không có payout không cần giữ claim và dùng hành vi cũ.
- Đã bổ sung MockK regression với mã `DungeonSystem` thật: đúng thứ tự reserve/counter/item/receipt/packet, claim bị từ chối, lỗi khi chốt sau khi item đã trao, và run không thể xác minh.
- **Giới hạn:** đây là fail-closed, không phải giao dịch toàn bộ bag/dailyCount/receipt. Nếu server chết sau khi reserve hoặc đang ghi item, claim sẽ khóa lượt tiếp đến khi admin xác minh. Chưa có quy trình bù thưởng/rollback tự động và chưa có client acceptance test. Việc ghép PR #38 với PR #36 (chưa merge) cần xử lý xung đột GameModule/DatabaseFactory sau này.

## Thử nghiệm tích hợp dungeon tuần và Soul Dungeon (PR #38, chưa phát hành)

- `settleWeeklyBattle` giữ nguyên nhánh thua và nhánh nhiệm vụ đã đủ lượt. Với lượt thắng có thưởng, repository được kiểm tra trước khi có hiệu ứng tiền/vật phẩm/cờ; dùng UUID bền vững đã khớp `DungeonRunRepository.load` và khóa `WEEKLY_TERMINAL`.
- Quyền thưởng được phân loại theo `DUNGEON_WEEKLY` và `DUNGEON_SOUL`, để không vô tình khóa lẫn hai nhóm dungeon độc lập. Mỗi loại vẫn chỉ có tối đa một claim `PENDING` trên một nhân vật.
- Đọc trước sức chứa túi, rồi `reserve`; sau đó mới đánh dấu run settled, thêm vật phẩm, cập nhật dailyFlag. Chỉ sau khi `settle` trả thành công mới gửi packet thưởng và `finishWeeklyRun`.
- Lỗi từ chối claim, túi đầy hoặc dữ liệu run không xác minh được trả lỗi mà chưa ghi thưởng. Lỗi xảy ra sau reserve giữ `PENDING`; không gửi thông báo thành công hoặc tự phát lại.
- Regression tests mới cho phép kiểm tra thứ tự DB claim / reward / mission / receipt / client, claim trùng, túi đầy và mất ACK. Thưởng `claimWeeklyAllPass` nay đã dùng `QuestSystem.dungeonWeekKey()` cùng timezone/chu kỳ với reset DVT và có ledger riêng; vẫn cần kiểm thử ngoài thực tế trước live.

**Lưu ý:** Ledger không làm các cập nhật vật phẩm, nhiệm vụ và receipt trở thành một transaction nguyên tử. Trường hợp crash giữa các bước phải được đối soát thủ công. Không merge hay triển khai live khi chưa có quy trình giải quyết claim tồn đọng.

## Thưởng vượt ải tuần: claimWeeklyAllPass (đã nối ở PR #38, chưa live)

- Sau `refreshPeriodicProgress`, `QuestSystem.dungeonWeekKey()` lấy **cùng khóa thứ Hai 00:00 theo Asia/Taipei** với `QuestSystem.refreshDungeonWeekly` (và `CharacterDungeonWeeks.week_key`). `RewardLedgerSourceKeys.weeklyAllPass(weekKey, page)` tạo khóa độc lập theo kỳ và trang thưởng; không dùng giờ tùy ý lúc trao item.
- Chỉ nhận nếu cả 20 node trong trang hợp lệ, đủ tiến độ theo cơ chế DVT và chưa có cờ thưởng; `completionBitId(costPoint)` kiểm tra cờ Mark tồn tại mà không ghi dữ liệu, `InventorySystem.canAddItemToBag` kiểm tra sức chứa túi trước khi reserve.
- Ledger `DUNGEON_ALL_PASS` được giữ **trước** khi `addItemsToBagExact` và `questSystem.completeMission`; chỉ `settle` thành công mới gửi item/Mark packets. Claim bị từ chối, MySQL unavailable hoặc túi đầy không cấp phần thưởng; sau reserve lỗi vẫn để `PENDING` đối soát.
- Không tự retry sau khi item/Mark có thể đã ghi. Một claim `PENDING` trong kỳ sẽ khóa loại thưởng này cho cùng nhân vật đến khi giải quyết bằng quy trình quản trị. Các trang trong cùng kỳ có `source_key` khác nhau, nhưng khóa pending-scope vẫn serialize.
- Regression test `DungeonWeeklyAllPassLedgerRegressionTest`: thứ tự reserve → item → Mark → receipt → client, bị từ chối, túi đầy, mất ACK và khóa tuần mới. Admin reward-repeat-test cũng không được dùng để vượt qua biên nhận bền vững.
- **Giới hạn:** code trao item, ghi Mark và receipt vẫn là các giao dịch riêng. Rollover giữa thao tác có thể cần chính sách hòa giải theo kỳ; chưa có validation client và chưa mở công cụ admin release. Không được quảng bá đã giải quyết exactly-once trọn gói.

## Chưa hoàn thiện để triển khai

1. Lưu UUID định danh trận chiến vào DB từ lúc tạo battle, và lấy lại được qua reconnect/crash; cần tương thích nhiều người tham gia và DVT battle scene.
2. Gắn reserve **trước hiệu ứng đầu tiên** của từng nhóm thưởng (battle generic, capture, normal dungeon, weekly dungeon, weekly all-pass), không chỉ đặt cuối đường callback. Sửa đường thất bại theo hướng fail-closed; tránh trả success UI khi chưa được trả thưởng.
3. Dungeon step `SETTLED` phải chỉ được gắn sau khi các thành phần thưởng/mission được xác nhận; hiện các phần thưởng này nằm trong những giao dịch riêng, nên ledger **không bảo đảm exactly-once**.
4. Thử lỗi tại mỗi ranh giới: sau reserve, sau item, sau EXP/vàng, trước settle, sau commit mất ACK; khôi phục và chơi lại cùng run, party nhiều người, nhiệm vụ farm lặp, server reset chu kỳ tuần.
5. Công cụ quản trị read-only đối chiếu claim với vật phẩm, pet, tiền, mission và log; cần quy trình xác nhận thủ công trước khi giải quyết `PENDING`, tuyệt đối không xóa tự động.
6. Trước deployment: sao lưu MySQL/source dirty production, kiểm tra schema migration `SchemaUtils.createMissingTablesAndColumns` trên môi trường staging, test Gradle, và xác minh game-client.

**Phạm vi đã được làm trên PR:** domain contract, bảng và repository transaction, chính sách khóa nguồn, H2 tests, query audit, và wiring thử nghiệm dungeon thường. **Chưa triển khai hay xác minh gameplay trên live.** PR #38 phải giữ Draft cho đến khi các khóa nguồn và luồng payout được tích hợp/kiểm nghiệm.
