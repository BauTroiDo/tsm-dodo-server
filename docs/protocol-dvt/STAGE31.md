# Bước 31 — Theo dõi nguồn skill của battle status

## Audit lifecycle

BattleFighter giữ statusEffect + statusRounds. BattleSkillResolver apply status từ kết quả skill. BattleInstance giảm round và tạo StatusRestore khi hết hiệu lực.

StatusRestore đã có field skillId và mô tả là skill gây ra trạng thái, nhưng trước đây BattleInstance luôn tạo:

`StatusRestore(col, row, statusEffect.id, 0)`

Nghĩa là nguồn status bị mất ngay sau khi apply.

## Vá

BattleFighter thêm statusSkillId.
BattleSkillResolver ghi statusSkillId = skillId cùng lúc với statusEffect/statusRounds.
Khi status hết hạn, BattleInstance đưa statusSkillId vào StatusRestore rồi reset về 0.

## Chưa triển khai

POISON, CHAOS, TRANCE và các status đặc biệt không được suy đoán từ tên animation. BattleStatus enum có các giá trị này nhưng SkillData.hitStatus chuẩn chỉ chứng minh mapping IceBound/WhirlWind/Clump ở bước 30. Cần protocol/data evidence riêng trước khi thêm tick damage, đổi target hoặc break-on-hit.
