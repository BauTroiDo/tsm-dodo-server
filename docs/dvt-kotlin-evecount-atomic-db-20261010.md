# TS Online Đa Vũ Trụ — Sửa lost update cho Kotlin EveCount

## Nguồn lỗi trong backend Kotlin

`QuestRepositoryImpl.insertCompletedEvent` trước đây thực hiện `SELECT completion_count`, sau đó tính `newCount=oldCount+1` trong Kotlin và `UPDATE`. Hai session/battle-completion đồng thời cho cùng character/map/eve có thể cùng đọc count N rồi ghi N+1, **mất một lần hoàn thành thực**. Với `EveConditionEvaluator` class12 param0, nhân vật có thể bị kẹt tại NPC dù đã thực hiện đủ lượt.

Bảng `CharacterCompletedEvents` đã có primary key `(charId,mapId,eveNo)`. Không cần migration.

## Sửa Kotlin

Đổi logic hai bước thành một `CharacterCompletedEvents.upsert(onUpdate={ ... })` với biểu thức `completionCount = completionCount + 1` do database tính. Chèn mới ghi 1; key đã tồn tại được cộng nguyên tử tại database. Không thay Lua/IL2CPP, MarkStep, Eve hay dữ liệu nhân vật.

Ngữ nghĩa `completionCount` và `conditionClass=12` được giữ nguyên, chỉ ngăn race làm mất cập nhật.

## Integration tests

`EveCompletedEventAtomicRepositoryTest` chạy với H2 in-memory, không dùng production:
- Lần ghi đầu insert count1 và 4 lần thành count4.
- 12 lần hoàn thành đồng thời phải đạt count12, không mất một increment.
- Tách biệt char/map/eve và reset scene xóa count cũ, ghi mới bắt đầu từ1.

Exposed có thể phát sinh SQL khác nhau theo dialect; PostgreSQL/MySQL/H2 sẽ được kiểm chứng riêng ở tầng adapter. Sự tương thích với MySQL production phải được xác minh trên CI/DB thử, không sửa trực tiếp bảng character_completed_events của người chơi thật trong kiểm thử. Không tuyên bố hệ thống nhiệm vụ ACID toàn bộ sau thay đổi này.

## An toàn triển khai

Chờ CI; sao lưu MySQL và `QuestRepositoryImpl.kt` gốc trên live; chỉ áp dụng diff chọn lọc giữ lại toàn bộ local patches; test :tests:test rồi restart duy nhất JVM6613 có log riêng và xác minh ready. Không git pull/reset vào worktree live.
