# Bước 27 — Đồng bộ skill snapshot và bỏ blocking DB trong battle

## Vấn đề 1: normal skill stale khi UISell 57/58

S8:13 xóa toàn bộ bảng skill client rồi nạp lại. Trước đây UISellHandler merge Turn3 skills với SkillRepository.findByCharacterId().

Skill thường đang online được SkillSystem giữ trong cache và có thể dirty sau C28 nhưng chưa autosave. Vì vậy query DB có thể trả bản cũ, làm S8:13 vô tình xóa skill vừa học khỏi client.

Vá:
- inject SkillSystem vào UISellHandler;
- sendMergedSkillState ưu tiên SkillSystem.getCache(charId).skills;
- chỉ fallback repository nếu cache không tồn tại.

## Vấn đề 2: runBlocking trong battle creation

createPlayerFighter trước đây gọi SkillRepository.findTurn3State bằng runBlocking trong hàm non-suspend. Battle creation vốn đã chạy trong coroutine/suspend path.

Vá:
- createPlayerFighter chuyển thành suspend;
- Turn3 state được await trực tiếp;
- joinBattle chuyển thành suspend để giữ toàn bộ call chain không blocking.

Battle skill snapshot vẫn merge:
- normal skill từ live SkillSystem cache;
- Turn3 skill từ authoritative Turn3 repository state.

Kết quả: không block thread khi tạo battle và không dùng normal skill DB cũ trong full skill sync.
