# Kotlin DVT EveCount: khôi phục đúng số lần hoàn thành từ database

## Phát hiện từ mã server

- `EveStateBuilder.buildPlayerState` đọc `QuestRepository.getCompletedEventCounts` của nhân vật/map từ bảng `character_completed_events`, rồi gọi `EveEventStateMachine.prePopulateCompleted`.
- `EveConditionEvaluator` dùng `completedEveCounts[currentEveNo]` để kiểm tra `conditionClass=12`, `parameter=0`, `parameterStyle=0/1`.
- `prePopulateCompleted` trước đây dùng `putIfAbsent`: một event đã có count1 trong RAM không được cập nhật khi database có count4; điều kiện `count >= 3` tiếp tục trả false dù tiến độ lưu bền đã cao hơn.

## Sửa đúng Kotlin

Sử dụng `ConcurrentHashMap.merge(maxOf)` cho từng eveNo trong cùng nhân vật/map: nhận giá trị lớn hơn từ database, nhưng không để snapshot DB cũ làm lùi count đã tăng trong RAM. Hợp nhất theo khóa là atomic với `complete()` vốn đang dùng `merge(+1)`. Bỏ qua eveNo0 (fallback Talk) và các count không dương; `clearSceneEvents` vẫn cho phép khởi tạo một baseline mới sau reset cảnh.

## Regression

`EveCompletedCountHydrationRegressionTest` kiểm tra:
1. RAM1 + DB4 mở được nhánh `conditionClass12 count>=3`.
2. RAM5 + DB1 không làm lùi tiến độ.
3. Lặp hydrate không cộng trùng, không trộn event, map hay character.
4. Reset scene rồi hydrate thấp hơn là hợp lệ.
5. Count0/âm và fallback EveNo0 không tạo log hoàn thành giả.

## Ranh giới bảo đảm

Đây là sửa **cache hydration** Kotlin, không phải cơ chế thưởng, hoàn thành nhiệm vụ hay database transaction. Không động tới IL2CPP/Lua/client/Eve.emg và không sửa dữ liệu nhân vật. Tiếp tục điều tra `QuestRepositoryImpl.insertCompletedEvent` mất cập nhật trong giao dịch cạnh tranh, class13/11 reward backend, class14 lịch hoạt động và kiểm thử E2E có nhân vật thử. Chỉ deploy chọn lọc sau CI/test, sao lưu live và xác nhận không mất local patches.
