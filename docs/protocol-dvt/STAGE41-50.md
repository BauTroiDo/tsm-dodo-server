# Batch Steps 41–50 — Battle area/item lifecycle audit

## Step 41 — Fight area 255
Lua FightField.GetEnemyIdx(kind, target) có nhánh kind == 255 với chú thích `敵我雙方全體` (toàn thể cả ta lẫn địch) và duyệt toàn bộ fightHum.
BattleGrid trước đây không hỗ trợ 255 nên fallback thành một target.
Server nay resolve area 255 thành toàn bộ fighter còn sống của cả hai phe.

## Step 42 — Revive target semantics
FightField.UpdateSelectFrame xác nhận item kind=50 là `復活道具目標是死人` (đạo cụ hồi sinh, mục tiêu là người đã chết) và gọi OpenSelectFrame(..., true).
Prevalidation Step32 trước đó vô tình yêu cầu target sống. Đã tách kind=50 để cho phép target chết cùng phe và từ chối fighter đã FlyOut.

## Step 43 — Revive queue/resolve
BattleInstance không còn chặn dead target trước BattleItemResolver. Queue vẫn kiểm tra item ownership và restriction phe.

## Step 44 — Revive state
Khi kind=50 resolve thành công, server bỏ isDead và clear battle status lifecycle trước khi áp HP recovery.
Nếu definition không cho HP recovery cụ thể, fallback HP=1 để fighter thực sự sống lại; không áp dụng cho FlyOut.

## Step 45 — HP/SP persistence audit
BattleManager.broadcastRoundResult đã sync main fighter HP/SP vào PlayerState mỗi round.
handleBattleEnd sync lại cuối battle và PetSystem.updateFightNpcHpSp sync võ tướng.
Không thêm mutation trùng lặp.

## Step 46 — Status group model audit
Client FightRoleController có các group độc lập Seal/Vary/Faint/Spec/Strong/Passive/Diversity.
Server hiện chỉ giữ một BattleStatus trên fighter. Chưa chuyển sang multi-group trong batch này vì cần audit đầy đủ packet/result và stacking/clear rules trước khi đổi data model.

## Step 47 — Poison boundary
Client xác nhận item kind=19 chỉ target phe địch. Công thức poison tick/duration chưa đủ bằng chứng authoritative trong source đã audit; chưa invent.

## Step 48 — Revive animation/result boundary
Item vẫn dùng skillId 19001 UseItem result path như battle item hiện tại. Chưa invent packet riêng cho revive khi chưa có receive-side evidence.

## Step 49 — Status execution boundary
Chaos redirect, poison tick, trance break-on-hit và buff stacking tiếp tục để pending cho audit sâu; không suy diễn từ animation.

## Step 50 — Build gate
Batch chỉ chứa các thay đổi có evidence trực tiếp hoặc lifecycle invariant rõ ràng. Chạy ./gradlew test build trước batch tiếp theo.
