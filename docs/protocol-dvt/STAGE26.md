# Bước 26 — Battle command ACK và kiểm tra SP authoritative

## Lỗi đã vá

Trước đây BattleCommandHandler luôn gửi S53:5 ActSetOK sau C50:1/C50:2, kể cả khi BattleInstance từ chối action vì:
- sai chủ sở hữu fighter;
- fighter chết/stun;
- skill chưa học;
- summon Quang/Ám sai điều kiện;
- battle không ở WAITING_COMMANDS.

Điều này làm client chuyển sang nhân vật điều khiển tiếp theo dù server chưa ghi pendingAction.

## Thay đổi

BattleInstance.setPlayerAction trả Boolean.
BattleManager.handlePlayerAction truyền Boolean đó lên handler.
BattleCommandHandler chỉ gửi ActSetOK khi accepted=true.

Skill command còn được kiểm tra trước ACK:
- skill ID phải tồn tại (trừ HAND_FIGHT);
- fighter phải đủ SP theo SkillDef.requireSp;
- learned-skill và summon guard vẫn áp dụng.

BattleSkillResolver vẫn giữ kiểm tra SP lần hai khi resolve để bảo vệ trường hợp SP thay đổi trước lượt hành động.

## Ý nghĩa

Client không còn nhận ACK giả cho packet forged/invalid hoặc skill thiếu SP. Server vẫn là nguồn quyết định cuối cùng.
