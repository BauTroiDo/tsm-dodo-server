# Bước 18 — Turn3 combat snapshot và HP/SP login

## Battle snapshot

UIStatus.lua xác nhận Turn3Int/Atk/Def/Agi là bonus cộng vào bốn combat stat. Player battle fighter nay dùng:
- atk = base + equipAtk + Turn3Atk
- def = base + equipDef + Turn3Def
- agi = base + equipAgi + Turn3Agi
- int = base + equipInt + Turn3Int

Điều này đồng thời sửa omission cũ: player fighter trước đây không cộng equipAtk/Def/Agi/Int dù runtime đã giữ các bonus đó.

Turn3 stat được nạp vào PlayerState khi login và cập nhật ngay sau C8:1, nên battle mới lấy snapshot mới nhất.

## Career runtime

Login trước đây truyền Turn nhưng bỏ Career khi init PlayerState, khiến runtime career về 0. Nay truyền persisted career.

## MaxHP/MaxSP Turn3

Calculator.lua khi Turn >= 3:
- turn3Lv = Lv - 200;
- base calculation dùng Lv=200;
- HPX tự cộng min(turn3Lv,70), rồi Turn3Hpx;
- SPX tự cộng turn3Lv, rồi Turn3Spx.

PlayerDataDto login nay làm đúng quy tắc này thay vì dùng Lv 201..250 trực tiếp với turn3Lv=0.

## Persistence fix

Bước 16 tăng runtime Lv Tam Chuyển nhưng thiếu persist Characters.level. Nay mỗi Turn3 level-up gọi updateLevel, tránh relog bị tụt level.
