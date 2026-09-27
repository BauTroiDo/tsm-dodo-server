# Bước 19 — Audit lifecycle Tam Chuyển

Audit chuỗi: C23:81 -> DB/runtime -> S3:0/S5:3 login -> C8:12 -> UISell57/58 -> stat -> battle -> relog.

## Lỗi đã sửa

1. Own S3:0 hard-code Turn3Element=0.
   - ResponseSender nay nhận persisted Turn3 element.
   - Login gửi đúng element ngay trong appear packet.

2. Other-player S3:0 hard-code:
   - Turn3Element=0
   - Turn=0
   - Career=0
   Nay lấy từ GameSession.

3. GameSession trước đây không giữ turn/career/Turn3Element.
   - thêm ba field appearance state;
   - load turn/career từ CharacterRecord;
   - load Turn3Element từ Turn3State.

4. Runtime mutation:
   - C23 rebirth cập nhật session.turn/session.career;
   - C8:12 first Light/Dark selection cập nhật session.turn3Element;
   - UISell58 đổi hệ cập nhật session.turn3Element.

Nhờ vậy người chơi mới vào map/được người khác nhìn thấy không còn mất thông tin Tam Chuyển cho đến lần relog.

## State path hiện tại

- Rebirth: Characters.turn/career + PlayerState + GameSession.
- Turn3 element/stats/skills/exp: character_turn3_*.
- Login S5:3: đọc Turn3State.
- Login/runtime: PlayerState giữ combat stats, GameSession giữ appearance Turn3 state.
- Battle: snapshot base + equipment + Turn3.
- Level: Characters.level; Turn3 EXP: character_turn3_states.exp.

## Còn cần audit sau bước này

- S3:0 broadcast ngay khi đổi Turn3 element: hiện session state đúng cho lần appear kế tiếp; cần chứng minh packet realtime nào original server dùng trước khi broadcast thêm.
- Turn3 skill unlock/change response có thể cần skill-tree refresh packet ngoài S84:2.
- Rebirth eligibility/mission consumption vẫn chưa được suy đoán.
