# Bước 20 — Realtime Turn3 skill/element synchronization

Nguồn client:
- UISell 57/58 callback chỉ hiện message; callback không tự sửa Role state.
- Role.ReceivePlayerSkillData (S8:13) gọi ClearSkill() trước khi đọc toàn bộ skill.
- RoleController:SetSkill(..., updateOther) refresh UISkillTree khi cần.
- S8:1 attr 254 cập nhật Turn3Element trực tiếp.

## Unlock skill — sell 57

Sau persist Turn3 skill:
1. gửi S8:1 Turn3Element để client giữ element authoritative;
2. đọc normal persisted skills;
3. merge normal + Turn3 skills;
4. gửi full S8:13;
5. gửi S84:2 success.

Không gửi riêng Turn3 skill qua S8:13 vì packet này ClearSkill() trước; gửi riêng sẽ làm mất skill thường trên client.

## Change element — sell 58

Sau persist target element + filtered skill set:
1. cập nhật GameSession.turn3Element;
2. gửi S8:1 attr254 target;
3. gửi full merged S8:13 với skill set sau đổi hệ;
4. gửi S84:2 success.

Nhờ đó skill cũ bị loại khỏi client ngay và UI skill tree refresh không cần relog.

## First element selection — C8:12

Sau persist:
- cập nhật GameSession.turn3Element;
- gửi S8:1 attr254;
- sau đó gửi S8:12 result.

Client vì vậy có Turn3Element đúng ngay sau lần chọn đầu.
