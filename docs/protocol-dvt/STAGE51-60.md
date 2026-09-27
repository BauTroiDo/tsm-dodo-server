# Batch Steps 51–60 — Status identity and battle command audit

## Step 51 — hitStatus is not BattleStatus enum
BattleDamageCalc trước đây map hitStatus 1/2/3 trực tiếp thành Ice/Whirl/Clump.
Lua FightRoleController.HandleStatus nhận status group riêng, sau đó SealSkill/FaintSkill xác định concrete status từ skillId.
Đã bỏ giả định enum trực tiếp.

## Step 52 — Seal skill mapping
Bổ sung mapping skillId có bằng chứng từ SealSkill cho IceBound, WhirlWind, Clump, Trance, DizzyAtk.

## Step 53 — Faint skill mapping
Bổ sung mapping skillId có bằng chứng từ FaintSkill cho Poison, Chaos, Tardy, Landslip.

## Step 54 — Light/Dark status identity
Dark summon chain 23011..23014 được client FaintSkill map Poison. Server nay nhận diện đúng concrete status thay vì bỏ qua.
Light 22011..22014 là clear Vary theo client; chưa invent dispel execution khi server vẫn chưa có multi-group model.

## Step 55 — Status chance boundary
Chance hiện tại của server vẫn là công thức reconstructed 50 + int diff/2. Chưa có Lua evidence authoritative cho chance nên batch này không đổi.

## Step 56 — Multi-group boundary
Client giữ status_Kind riêng cho Seal/Vary/Faint/Spec/Strong/Passive/Diversity. Server hiện chỉ có một statusEffect.
Không chuyển model trong batch này cho tới khi audit đủ apply/restore/clear packet semantics để tránh phá stacking.

## Step 57 — NPC SP command validation
NPC AI trước đây có thể queue skill không đủ SP; resolver reject muộn làm mất lượt.
Nay không queue skill definition có requireSp > current SP. Sentinel requireSp=1 vẫn yêu cầu ít nhất 1 SP.

## Step 58 — Area 255 exact enumeration
Lua GetEnemyIdx(255) duyệt toàn bộ fightHum đang tồn tại, không kiểm tra death.
Step41 dùng getAllAlive chưa hoàn toàn tương đương.
BattleGrid nay có getAll() gồm fighter tồn tại chưa FlyOut và area255 dùng danh sách này.

## Step 59 — Poison/Chaos execution boundary
Đã nhận diện identity nhưng chưa invent poison tick damage hay chaos redirect. Cần authoritative result semantics trước.

## Step 60 — Build gate
Chạy ./gradlew test build. Batch tiếp theo audit dispel/status-group model và effect execution.
