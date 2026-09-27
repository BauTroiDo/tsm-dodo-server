# HANDOFF — Audit & vá TSOnline Mobile theo Lua Đa Vũ Trụ

> Mục đích: checkpoint bền vững để một chat/AI mới có thể tiếp tục công việc mà không phải audit lại từ đầu.
>
> Cập nhật checkpoint: 2026-09-28.

## 1. Mục tiêu

Repository: `BauTroiDo/tsm-dodo-server`.

Mục tiêu hiện tại là dùng **Lua client TSOnline Mobile Đa Vũ Trụ làm nguồn bằng chứng chính** để audit protocol/semantics và vá Kotlin server bám sát client. Không viết lại server, không thêm behavior tùy ý.

Chuỗi làm việc chuẩn:

`Lua evidence → Kotlin implementation → mismatch → minimal fix → regression test → build/test → document → batch tiếp theo`.

## 2. Nguyên tắc bắt buộc

- Bám sát Lua: ưu tiên `Network.lua`, `protocal.lua`, UI/Skill/Battle/Item/Role/NPC/EVE Lua và dữ liệu liên quan.
- Kiểm tra cả C→S và S→C: main/sub opcode, Byte/UInt16/Int16/Int32/UInt32, endian, thứ tự field, count/loop, packet length, điều kiện gửi/nhận và semantics.
- **Không đoán game logic/công thức.** Nếu client chỉ chứng minh animation/status/target/duration nhưng không chứng minh damage/reflection/transfer/hit formula thì giữ PARTIAL/UNKNOWN.
- **Không sửa Lua để chiều Kotlin.** Nếu có mismatch có bằng chứng, ưu tiên vá server.
- Không fake success, không dummy/no-op chỉ để compile, không refactor ngoài phạm vi khi chưa cần.
- Compile success ≠ test success ≠ runtime verified. Luôn ghi đúng mức bằng chứng.
- Mỗi lỗi phải có bằng chứng cụ thể trước khi sửa.

## 3. Transaction/state safety

Các packet thay đổi nhiều state phải validate toàn bộ trước mutation khi semantics yêu cầu.

Ví dụ `C 28:1` học skill DVT đã xử lý:
- Lua có thể gửi batch qua `pairs`, không được phụ thuộc thứ tự.
- Resolve prerequisite trong toàn batch.
- Không partial mutation/partial point deduction nếu có skill không tồn tại, duplicate, vượt cấp, thiếu điểm hoặc prerequisite lỗi.
- Target đã đạt không bị tính điểm lại.
- Cycle không có điểm bắt đầu bị từ chối.

Không quay lại thay đổi phần này nếu không có regression/bằng chứng mới.

## 4. Protocol audit hiện có

Tài liệu nằm tại `docs/protocol-dvt/`:
- `README.md`
- `inventory.csv`
- `inventory.json`
- các `STAGE*.md`

Trạng thái inventory: MATCH / PARTIAL / MISSING / MISMATCH.

Không hiểu MISSING là chắc chắn phải implement: có thể là optional/deprecated/debug/dynamic/feature chưa bật.

`README.md` chứa một số thông tin lịch sử từ commit `59c2e0a81c3d4443aa1aad3584d216096256cf16` và câu “chưa commit/push”; **không dùng phần lịch sử đó làm HEAD hiện tại**. Luôn kiểm tra GitHub HEAD thật và STAGE mới nhất.

## 5. Những phần quan trọng đã làm

Đã audit/vá nhiều phần Login, Character, Scene/Map, Item/Inventory/Bank/Storage, Pet/Skill/Battle và một phần NPC/EVE; nhiều lỗi wire type/layout đã được sửa theo Lua.

Pet skill `C 28:2` đã có implementation một phần theo bằng chứng Lua.

Battle status đã tiến theo grouped-status semantics. Các group như Seal/Vary/Faint/Spec/Strong có thể coexist; apply status chỉ replace group của chính nó. Duration/source phải giữ nhất quán (`skill.round`, `skillId`, `currentRound`) để expiry/restore packet đúng.

## 6. Batch 141–150 vừa hoàn thành

Tài liệu chuẩn: `docs/protocol-dvt/STAGE141-150.md`.

Bug đã xác định: một số friendly Vary/Spec/Strong status skills không phải heal/dispell nhưng rơi xuống `calculateAttack`, có thể tạo fake attack damage lên ally và chịu offensive hit/status chance sai.

Đã vá đường friendly utility status:
- apply modeled status trực tiếp khi có bằng chứng;
- không tạo HP/SP attr change giả;
- giữ grouped status;
- lưu duration/source nhất quán.

Boundary quan trọng: MirrorAtk và Protect/MagicProtect có bằng chứng presentation/identity nhưng chưa có authoritative server damage-transfer/reflection formula, nên **không invent formula**.

## 7. Mốc bàn giao

Mốc code trước file checkpoint này:
`c19928e868bd4221299e16ae1d2d31fb925f97ce`

Commit:
`Document DVT friendly status execution batch 141-150`

Chủ dự án đã báo build thành công. Điều đó là build gate, chưa tự động có nghĩa runtime game đã verified.

Sau khi file checkpoint này được commit, HEAD tất nhiên sẽ mới hơn mốc trên; đừng nhầm commit tài liệu checkpoint với một batch gameplay mới.

## 8. Điểm đang dở — NEXT = STEP 151

Không quay lại Step 1. Không audit lại từ đầu.

Theo cuối `STAGE141-150.md`, batch tiếp theo phải audit:

**enemy debuff status-only skills vs enemy damaging + status skills.**

Cần xác định bằng Lua/data trước:
1. status-only enemy skill;
2. damage + status skill;
3. damage-only skill;
4. utility/special skill.

Sau đó đối chiếu Kotlin, đặc biệt:
- `resolveSkill`
- `calculateAttack`
- `statusFromSkillId`
- `fightWay`
- `round`
- target selection
- hit chance / status chance
- grouped status
- damage path
- result/packet trả client.

Không được dùng quy tắc suy diễn đơn giản `enemy target = calculateAttack()`.

Câu hỏi cần trả lời cho từng skill: Lua/data chứng minh fightWay/target/status/damage/hit/miss/duration/group gì? Status-only có thật sự cần damage calculator không? Damage+status phải giữ cả hai behavior nào? Nếu chưa đủ bằng chứng thì dừng ở boundary và ghi UNKNOWN/PARTIAL.

## 9. Cách làm việc thủ công hiện tại

**QUY TRÌNH LÀM VIỆC CỐ ĐỊNH — KHÔNG TỰ Ý ĐỔI SANG WORK/ASTRA:**

- Làm việc trực tiếp trong ChatGPT chat.
- ChatGPT đọc/audit Lua, data, opcode tham khảo và Kotlin trực tiếp từ GitHub.
- Khi có đủ bằng chứng, ChatGPT **vá trực tiếp file trên GitHub** và commit/push thay đổi lên `main`.
- ChatGPT không giả định rằng mình đã build/test local nếu chưa có kết quả thực tế.
- Sau một patch/batch ổn định, **chủ dự án sẽ pull/full source từ GitHub về máy và tự chạy build/test**.
- Chủ dự án gửi log/kết quả build/test lại trong chat; ChatGPT phân tích lỗi, phân biệt regression mới với baseline, rồi tiếp tục vá trên GitHub.
- Chỉ ghi BUILD/TEST SUCCESS khi có kết quả build/test thực tế do chủ dự án cung cấp hoặc CI thực sự chạy.
- Không chuyển công việc sang Work mode hoặc Astra chỉ vì cần sửa repository; quy trình chủ động của dự án này là **chat → GitHub patch → user pull/build/test → phản hồi → patch tiếp**.

Mỗi bước nhỏ:
1. kiểm tra HEAD/STAGE hiện tại trên GitHub;
2. tìm và đọc Lua/data liên quan;
3. giải semantics dựa trên bằng chứng;
4. đọc Kotlin tương ứng;
5. chỉ ra mismatch cụ thể;
6. tạo minimal patch;
7. thêm regression test;
8. build/test;
9. phân biệt baseline failure với regression mới;
10. ghi STAGE và commit/push khi batch ổn định.

Không gom hàng chục thay đổi chưa kiểm chứng vào một patch.

## 10. Quy tắc cho chat/AI mới

Khi đọc file này:
1. kiểm tra HEAD GitHub hiện tại;
2. đọc commit sau checkpoint;
3. đọc `docs/protocol-dvt/STAGE*.md` mới nhất;
4. nếu chưa có batch sau 141–150 thì tiếp tục **Step 151**;
5. nếu đã có STAGE mới hơn, tiếp tục từ điểm chưa hoàn thành của STAGE mới nhất.

Không yêu cầu chủ dự án kể lại toàn bộ lịch sử. Không quay về `59c2e0a...` trừ khi cần tra lịch sử.

## 11. Triết lý dự án

Không viết server theo suy đoán của chúng ta. Mục tiêu là phục dựng hành vi server phù hợp client Đa Vũ Trụ dựa trên bằng chứng.

- Biết chắc → implement.
- Biết một phần → PARTIAL.
- Chưa biết → UNKNOWN.
- Client chứng minh server sai → fix server.
- Thiếu bằng chứng → dừng và audit tiếp.

**Điểm tiếp tục hiện tại: STEP 151 — enemy status-only vs damage+status execution audit.**
