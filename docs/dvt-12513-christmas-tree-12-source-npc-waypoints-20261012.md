# DVT Christmas tree mission12513 — 12 original stage-specific NPC routes

2026-10-12 VN. Data-only quest navigation fix, no manual character-level E2E until the one consolidated test requested by owner.

## Original DVT source, not guessed targets

Quest12513 has 14 MarkStep rows: original stage1 and stage40 already return to Santa NPC1 in scene49945 at `(49945,270,420,1,1)`; twelve stages2,3,4,11,12,13,21,22,23,31,32,33 were all `END=(0,0,0,0,0)` despite explicit search locations in mission text. The authentic source `gamedata/Eve.emg` has NPC38289 (`close=true`) at twelve stage-specific scenes and placements. Each NPC has a **ClickNpc Eve** (`when_happen=[True,False,False,False]`), not a Door or auto-scene-entry trigger. The source stage gate is `conditionClass2/param12513/pStyle1/ops5/stage/and_num2`. Two original subbranches check item29465: `conditionClass1/param29465/pStyle1/ops2/value0` on the progress branch, and `ops1/value1` on the other branch. The progress branch consumes one item29465 and applies original quest resultClass2 value(s); never bypass either item gate. **No original Eve, Kotlin, Lua, quests, rewards, invisible NPC flags or item sources modified.**

| Step | Location clue | Scene | NPC placement / original NPC position | Eve | Original quest-result values |
|---|---|---:|---|---:|---|
| 2 | Đỉnh Khổng tước đài | 12106 | 9 / (470,300) | 2 | 9,10,11 |
| 3 | Quảng trường Diệp Thành | 12140 | 2 / (190,380) | 3 | 8,9,10 |
| 4 | Gần cổng Diệp Thành | 12171 | 3 / (650,360) | 4 | 7,8,9 |
| 11 | Nhà có 1 con lợn, Trác Quận | 12004 | 3 / (350,360) | 6 | 10,11,12 |
| 12 | Nhà trống, Trác Quận | 12137 | 2 / (510,460) | 3 | 9,10,11 |
| 13 | Nhà chắc chắn, Trác Quận | 12015 | 4 / (470,320) | 6 | 8,9,10 |
| 21 | Nhà giàu có, Hứa Xương | 13151 | 10 / (150,320) | 5 | 10,11,12 |
| 22 | Nhà có rèm, Hứa Xương | 13172 | 5 / (350,280) | 5 | 9,10,11 |
| 23 | Hiệu không có chủ quán, Hứa Xương | 13283 | 9 / (110,460) | 3 | 8,9,10 |
| 31 | Lễ đường, Kiến Nghiệp | 18304 | 10 / (310,400) | 10 | 9 |
| 32 | Nhà gần rừng, Kiến Nghiệp | 18171 | 3 / (650,360) | 5 | 8 |
| 33 | Đường lớn Kiến Nghiệp / Hội Kê | 18134 | 3 / (610,300) | 5 | 7 |

Each of the twelve new END fields is `(scene,x,y,1,npcPlacement)`, matching the actual original NPC coordinates. Original `close=true` of all twelve NPCs remains unchanged and dynamic visibility must be checked in future game E2E. Note that source quest-result values are **not** simple +1 in this mission; preserve all source branches exactly and never invent linear mission stages.

## Guarded patch & exact-version integrity

`tools/patch_dvt_12513_christmas_tree_source_npc_routes_20261012.py` checks 12 exact original NPC/Eve placements, ClickNpc source trigger, quest stage, both item29465 condition branches and source result set, mission-text location clue, source edition SHA, packed END offsets and unchanged original Santa entry/return stages. Writes exactly twelve 8-byte END fields (96 allowable bytes, **93 actual different bytes**), with SHA-verified adjacent backup and atomic replacement. Unknown editions fail closed.

- Server/Git 892087 bytes predecessor SHA `94bbeeb0fde711f0435d490928ffba94d86afaaa880f6868e4dc6320dff1c424` → **`b2bb1ae382f5c0e50efe49e8ec8b98ce3a44bbba97e8bed955d94a1fd9749753`**.
- Two client staging editions 928425 bytes predecessor SHA `985eac0351fc923ab904cf34791e96cdb983e02d1a521cfcdd6cf04230950825` → **`4d6a4ea028df1649b77602fe0e9faebca0542032f5186d3b0e9e97e176d0dcc4`**.
- Git+DEV END offsets (stages2,3,4,11,12,13,21,22,23,31,32,33): `772477,772708,772931,773156,773367,773640,773885,774120,774405,774606,774859,775078`. Both client file offsets = Git offset +1820.
- Source-valid located MarkStep targets **5805→5817**, audit missing-END metric **190→178**, raw all-zero END fields **184→172**; all 4505 MarkStep records retained. Regression `tools/test_dvt_12513_christmas_tree_source_npc_routes_20261012.py` pins twelve NPCs, dual item conditions, neighboring Santa returns and SHA/scene/NPC census.

## Deferred full gameplay E2E, not completed

At each of quest12513 stages2–4 (Diệp Thành), 11–13 (Trác Quận), 21–23 (Hứa Xương), 31–33 (Kiến Nghiệp/Hội Kê), press mission and verify real client path to **NPC38289**, original `close=true` only reveals NPC when allowed, correct dialogue + item29465 conditions and original non-linear quest result branch, no duplicate rewards after repeat/relog; verify original final stage40 Santa route. Acquire item29465 only via verified original source, no manufactured item grant. Record also whether one NPC interaction is gated on missing visibility flags. **All not tested in game yet.**

Separate known blocker: Christmas socks quest12509/5–6 has real source turn-in NPC38286 scene49944 Eve8 but sock sources item29451/29466 are still unknown; do **not** presume this mission's auto-go should target item source or turn-in until that distinction is established. Other still-blank fields include multi-NPC puzzles and time-limited followers.