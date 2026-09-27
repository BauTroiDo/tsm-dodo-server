# Batch Steps 32–40 — Battle authoritative hardening

Batch này gom nhiều lỗi battle/protocol để giảm vòng build thủ công.

## Step 32 — Item ACK authoritative
C50:2 trước đây có thể được S53:5 ACK dù item không tồn tại, không có trong bag, sai phe hoặc effect chưa hỗ trợ; fail chỉ xảy ra khi resolve.
Thêm BattleItemResolver.canQueueItemUse() để prevalidate không mutation trước khi ghi pending action.

## Step 33 — Item ownership/target validation
Queue item yêu cầu target đang sống, owner session hợp lệ, item tồn tại trong bag và đúng restriction phe theo client FightField.UpdateSelectFrame.

## Step 34 — S53:1 status_kind semantics
FightManager.RevRestoreStatus đọc:
col,row,status_kind,skillId và chỉ nhận status_kind 1..6.
EFightStatusKind: Seal=1,Vary=2,Faint=3,Spec=4,Strong=5,Passive=6.
Server trước đây gửi BattleStatus.id (ví dụ ICE_BOUND=6, CLUMP=7), sai namespace.
Restore giờ gửi status group; concrete status được client suy ra từ skillId qua FightRoleController:HandleStatus/SealSkill.

## Step 35 — Skill target prevalidation
Trước ACK, skill phải có target sống.
Heal chỉ target cùng phe; attack chỉ target phe đối diện. Điều này khớp client select-frame cơ bản và ngăn forged packet tự đánh/heal địch.

## Step 36 — Không trừ SP khi target rỗng
Resolver trước đây trừ SP trước resolveTargets. Nếu target biến mất/chết trước lượt hành động thì skill có thể mất SP nhưng không có target.
Resolve target trước mutation; empty target => spConsumed=0.

## Step 37 — Status duration phase
Trước đây statusRounds giảm ở đầu resolveRound, trước khi lọc actingFighters. round=1 có thể hết ngay trước phase hành động nên không khóa lượt kế tiếp.
Chuyển decrement/restore xuống sau action phase.

## Step 38 — Không age status vừa apply
Status gây ra giữa round không được trừ ngay cuối cùng round đó.
BattleFighter lưu statusAppliedRound; chỉ decrement nếu status đã tồn tại từ round trước.

## Step 39 — Source skill giữ nguyên
Kế thừa Step31: statusSkillId được giữ đến S53:1 restore để client HandleStatus(group, skillId) đóng đúng concrete effect.

## Step 40 — Boundary
Chưa triển khai poison tick/chaos redirect/trance break-on-hit vì cần thêm authoritative evidence về server result semantics. Không suy đoán từ animation.
