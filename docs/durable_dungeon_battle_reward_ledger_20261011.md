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
- `settle`: khóa hàng, kiểm tra token, ghi `settled_at_epoch_ms` **một lần**, giữ biên nhận vĩnh viễn. Gọi lại không thành công, không xóa hàng.
- `find` / `unresolvedForCharacter`: chỉ đọc, phục vụ quản trị viên đối soát. Không tự hủy `PENDING` vì không thể biết phần thưởng đã cấp được bao nhiêu.
- `RewardLedgerSourceKeys.dungeon`: dùng `dungeonId`, `startedAtMillis`, `instanceId` đã được khôi phục, cộng với bước thưởng. Một lượt dungeon khác cần khóa khác.
- `RewardLedgerSourceKeys.battle`: yêu cầu UUID **được lưu bền trước khi vào trận**, không chấp nhận battleId số nguyên tạm. Mỗi nhân vật dùng primary key riêng.
- `RewardLedgerSourceKeys.weeklyAllPass`: tuần/chu kỳ theo chính sách DVT và trang thưởng; chu kỳ phải tính từ dữ liệu ngày reset chính xác, không dùng `System.currentTimeMillis()` tại điểm phát thưởng.

## Điều kiện ngăn lách ledger bằng lượt mới

Bảo vệ theo `source_key` **chưa đủ**: nếu một phần thưởng cũ có trạng thái `PENDING`, một lượt dungeon hoặc trận chiến được tạo mới với UUID khác có thể vẫn phát thưởng tương đương. Trước khi cho người chơi bắt đầu một run mới, điều phối phải tra `unresolvedForCharacter` và áp dụng chính sách chặn đúng phạm vi (dungeon/weekly/nguồn thưởng tương ứng) cho đến khi đối soát. Việc tra rồi thêm bản ghi không nguyên tử có thể bị request song song vượt qua: cần một transaction/khóa cấp nhân vật hoặc unique guard riêng để serialize bước cấp quyền.

Không tự coi pending nghĩa là đã nhận phần thưởng; cũng không tự xóa để mở khóa. Phải ghi đủ loại nguồn thưởng, chu kỳ reset và run identifier trong báo cáo đối soát. Mọi hành động quản trị (hoàn tiền, trao bù, release) cần được audit để không nhân đôi.

## Chưa hoàn thiện để triển khai

1. Lưu UUID định danh trận chiến vào DB từ lúc tạo battle, và lấy lại được qua reconnect/crash; cần tương thích nhiều người tham gia và DVT battle scene.
2. Gắn reserve **trước hiệu ứng đầu tiên** của từng nhóm thưởng (battle generic, capture, normal dungeon, weekly dungeon, weekly all-pass), không chỉ đặt cuối đường callback. Sửa đường thất bại theo hướng fail-closed; tránh trả success UI khi chưa được trả thưởng.
3. Dungeon step `SETTLED` phải chỉ được gắn sau khi các thành phần thưởng/mission được xác nhận; hiện các phần thưởng này nằm trong những giao dịch riêng, nên ledger **không bảo đảm exactly-once**.
4. Thử lỗi tại mỗi ranh giới: sau reserve, sau item, sau EXP/vàng, trước settle, sau commit mất ACK; khôi phục và chơi lại cùng run, party nhiều người, nhiệm vụ farm lặp, server reset chu kỳ tuần.
5. Công cụ quản trị read-only đối chiếu claim với vật phẩm, pet, tiền, mission và log; cần quy trình xác nhận thủ công trước khi giải quyết `PENDING`, tuyệt đối không xóa tự động.
6. Trước deployment: sao lưu MySQL/source dirty production, kiểm tra schema migration `SchemaUtils.createMissingTablesAndColumns` trên môi trường staging, test Gradle, và xác minh game-client.

**Phạm vi đã được làm:** domain contract, bảng và repository transaction, chính sách khóa nguồn, H2 unit/integration tests, query audit. **Chưa sửa luồng trả thưởng live.** PR #37 phải giữ Draft cho đến khi các khóa nguồn và luồng payout được tích hợp/kiểm nghiệm.
