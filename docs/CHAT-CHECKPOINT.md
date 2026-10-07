# CHAT CHECKPOINT — TS Online Mobile DVT
Cập nhật: 2026-10-01

## Nguyên tắc bắt buộc
- Server phải theo Lua Đa Vũ Trụ (DVT); không sửa/bẻ Lua để khớp Kotlin.
- Lua DVT là nguồn sự thật chính; PC bboycs chỉ là tham khảo khi DVT không đủ bằng chứng.
- Lua DVT pin: `59c2e0a81c3d4443aa1aad3584d216096256cf16`.
- Làm từng issue nhỏ: evidence → patch server → test → PASS/FAIL → bước tiếp.
- Không reset/clean working tree vì đang có nhiều thay đổi hợp lệ chưa commit.

## Repo / working copy hiện tại
- Repo chính: `BauTroiDo/tsm-dodo-server`.
- Working copy đúng: `D:\Games\Ts-Offline\TSMobileOnLine\tsm_dodo_chatgpt_main`.
- Nhánh hiện tại: `feature/9-pet`.
- Main/origin-main cũ hơn working tree; chưa merge/push batch EVE hiện tại.
- Android/JBR dùng build: `E:\Soft\AndroidStudio\jbr`.

## EVE hard-block audit đã hoàn tất
- A1 C7-P4 / mission 11731 / map 59131 / NPC 1: PASS.
- A2 C9-P14641 / mission 11794 / map 29021 / door 7: PASS.
- A3 C9-P17367 / mission 11190 / map 18000 / mine 7: PASS.
- A4 full hard-block audit: PASS, hard-block count = 0.
- B1 cleanup + full `gradlew test build --rerun-tasks`: PASS.

## Patch mới — cổng có quái trấn giữ
Triệu chứng người dùng: đánh thắng quái trấn giữ cổng nhưng event không tiếp tục / không cho qua map.

### Lỗi 1: class8 bỏ qua FightData key
- DVT Eve.emg dùng `conditionClass=8.conditionParameter` làm FightData key.
- Audit: 2090/2107 condition class8 có parameter != 0 trùng FightData của trận trong cùng event.
- Đã thêm `fightDataKey` vào EventSession và PlayerEventState.
- EveBattleService ghi key khi vào AwaitingBattle.
- EveConditionEvaluator: parameter=0 giữ wildcard legacy; parameter!=0 phải khớp trận vừa đánh.
- Regression đúng key / sai key / wildcard đều PASS.

### Lỗi 2: race C:020-006 trong lúc Door warp
- Lua DVT EventHandler[2] bật ConductGate; NextEvent có thể gửi C:020-006 ở frame kế tiếp.
- Server trước đây có thể advance/complete event trước khi S:020-007 / scene load hoàn tất.
- Đã thêm EventPhase.AwaitingWarp.
- EveDoorWarpService chuyển phase sang AwaitingWarp trước packet resultType=2.
- EveFlowController bỏ qua C:020-006 khi phase AwaitingWarp.
- Regression `nextPacketDuringDoorWarpDoesNotCompleteEvent`: PASS.

### Kiểm chứng dữ liệu
- 10058/10058 resultType=2/resultClass=7 trỏ tới SceneInfo hợp lệ.
- Không sửa Lua/data cho lỗi cổng.
- Full `gradlew test build --rerun-tasks`: PASS, 21/21 tasks executed.
- `git diff --check`: PASS.

## Runtime test server — trạng thái cuối phiên
- Game runtime đang chạy local trên máy dev, IP LAN `192.168.1.99`, port `6713`.
- DB dùng `192.168.1.69:3307/tsm_dodo_9pet`.
- 9-pet staging enabled, max carried = 9, progression enabled.
- Runtime được khởi động bằng `D:\Games\Ts-Offline\TSMobileOnLine\start_9pet_staging.bat`.
- Snapshot đang chạy khi ghi checkpoint: `server-runtime\20261001-150829-0bac9063`.
- Log startup đã xác nhận: server listen `0.0.0.0:6713` và ready.
- Kiểm tra cuối: listener count = 1, TCP local port 6713 = PASS.
- Không lưu mật khẩu/credential trong checkpoint này.

## Việc cần làm tiếp ngay khi mở chat mới
1. Đọc file này trước.
2. Không build/reset nếu chưa cần; server cuối phiên đã được restart bằng patch mới.
3. Người dùng sẽ vào client test cổng có quái trấn giữ.
4. Nếu PASS: ghi PASS và chuyển checkpoint tiếp theo.
5. Nếu FAIL: giữ client tại đúng map/cổng bị lỗi, đọc runtime log ngay; lấy mapId/eveNo/doorId/fightDataKey và trace:
   `trigger → battle → claimBattleResult → resumeAfterBattle → class8 → resultType2/class7 → AwaitingWarp → scene load`.
6. Không hard-code map nếu có thể sửa theo contract DVT chung.

## File code liên quan patch cổng
- `EveConditionEvaluator.kt`
- `EveStateBuilder.kt`
- `EveEventStateMachine.kt`
- `EveBattleService.kt`
- `EveDoorWarpService.kt`
- `EveFlowController.kt`
- Regression: `EveConditionEvaluatorTest.kt`, `QuestEventInputGuardTest.kt`.

## Lệnh mở đầu cho chat mới
Người dùng có thể nói:
`Đọc docs/CHAT-CHECKPOINT.md trong working copy tsm_dodo_chatgpt_main và tiếp tục đúng checkpoint, không sửa Lua DVT.`

## 2026-10-01 — Fix kẹt cổng Lô Long khẩu / mission 10044
- Runtime log bắt được char31 ở map 12421 (Lô Long khẩu), door 2, battle fightDataKey=1.
- Sau thắng, event có HAI chain class8-win cùng hợp lệ:
  1. cond2+3: cập nhật tài nguyên và mission 10044.
  2. cond6: resultType=2/resultClass=7 sceneInfo=2 để chuyển cổng.
- Resolver chung trước đây chỉ chọn best chain nên mission tăng nhưng Door warp bị bỏ.
- Audit toàn Eve.emg: pattern "specific win chain + generic single class8 Door warp" chỉ xuất hiện đúng 1 event: scene12421/eve2.
- Không đổi semantics resolve chung.
- Thêm EveChainResolver.findPostBattleDoorContinuation(): chỉ nối khi matched chain dài >1 có class8, chưa warp, và chain class8 đơn lẻ khác khớp với đúng 1 result type2/class7.
- EveFlowController.resumeAfterBattle nối Door continuation sau results của best chain.
- Regression trong EveChainResolverTest mô phỏng đúng mission10044/door2: PASS.
- Targeted tests: PASS 18/18 tasks.
- Full test build --rerun-tasks: PASS 21/21 tasks.
- Runtime đã restart sạch: snapshot server-runtime/20261001-153033-46a1ba9f, port 6713, listener=1, TCP PASS.
- DB staging hiện ghi mission10044 step=11 do người chơi đã thắng lặp nhiều lần khi cổng còn lỗi; direct DB recovery bị tool safety chặn nên chưa sửa DB.
- Khi test lại: vào Lô Long khẩu, đánh thắng guard; log mong đợi phải có doorContinuation=1 rồi Door dịch chuyển.

## 2026-10-01 — Damage audit D1/D2
- D1 PASS: base damage theo PC mapping nhị phân.
  - SkillDef.level = PC grade.
  - SkillDef.atkKind = PC unk17; atkKind==1 là physical, còn lại magic.
  - SkillDef.learnLimit = PC unk20.
  - skillLv được truyền vào BattleDamageCalc.
  - numerical không còn bị dùng như hệ số damage tấn công chung.
  - Regression D1 PASS; nhóm battle/skill PASS.
- D2 PASS: element + variance baseline.
  - 4 hệ cổ điển dùng advantage=1.2, disadvantage=0.8 theo BattleAbstract.cs.
  - PC RandomGen.getInt(-2,2) dùng Random.Next(min,max), cận trên exclusive => variance thực tế {-2,-1,0,+1}.
  - Bỏ random ±10%.
  - Gỡ crit/THUNDER giả suy từ AGI khỏi damage path; crit Mobile thật để D4 theo Crit/CritDamage.
  - Giữ ICE_BOUND x1.5 tách riêng, chờ audit buff/debuff D6.
  - Regression D2 PASS; battle/skill suite PASS; full test build --rerun-tasks PASS 21/21.
- Runtime đã deploy/restart: snapshot server-runtime/20261001-160339-b11750c1, port 6713, DB tsm_dodo_9pet.
- Bước kế tiếp sau runtime test D2: D3 multi-target damage division theo fightArea (/2,/3,/4 baseline PC).

## 2026-10-01 — Damage D3 multi-target
- DVT SkillData:GetFightArea confirms fightArea semantics:
  1 single, 2 two same column, 3 row three, 4 jump three, 5 cross five, 6 six, 7 row five, 8 all one side, 255 both sides/all.
- DVT growType=1: Lv1->1, Lv2-3->2, Lv4-6->5, Lv7-9->6, Lv10->8.
- DVT growType=2: Lv1-3->3, Lv4-6->5, Lv7-9->6, Lv10->8.
- PC makeExecutionPacket divides c.dmg BEFORE getSkillEffect/DEF/element:
  area 2/3/4 -> /2; 5/6/7 -> /3; 8 -> /4; 1 -> no division.
- Kotlin BattleSkillResolver now derives damageDivisor from resolved fightArea and passes it to BattleDamageCalc.
- BattleDamageCalc applies integer division to base damage before target level/DEF, element, reborn and variance.
- Area255: only one IOS skill found (22025 Hành Vân Thanh Lưu Trảm, fightWay22). PC baseline has no 255 contract; D3 leaves divisor=1 instead of guessing.
- BattleDamageD3RegressionTest covers divisor mapping, integer truncation, and DVT growType progression.
- D3 targeted regression PASS.
- D1+D2+D3 + DvtBattle/DvtSkill/DvtPetSkill suite PASS.
- Full test build --rerun-tasks PASS 21/21; git diff --check PASS.
- Runtime restarted: snapshot server-runtime/20261001-161510-d12a76b8, port 6713, DB tsm_dodo_9pet.
- Next planned damage step: D4 Mobile Crit/CritDamage + Hit/Dodge attributes, with DVT evidence first; do not reuse AGI fake crit.

## 2026-10-01 — Damage D4 Mobile Crit/CritDamage + Hit/Dodge
- DVT RoleController/UIStatusDetail confirms:
  - EAttribute.Crit=103, base 0%, direct role attribute.
  - EAttribute.CritDamage=104, UI base critical extra damage = 50% plus attribute value.
  - EAttribute.Dodge=105 and Hit=106 are direct percentages, UI contract 1..30%.
  - EAttackResult.Thounder=2 is a valid hit/result channel and is used for real critical result.
- IOS_Item_C audit:
  - Dodge rune items 55273..55278 use raw values 1,1,1,2,3,4.
  - Hit rune items 55279..55284 use raw values 1,1,1,2,3,4.
  - Therefore Mobile attrs 103..106 are direct percentages; do NOT decode with legacy raw-100.
- EquipBonusData/EquipBonusCalculator now carry crit/critDamage/dodge/hit and use per-attribute decoding.
- PlayerState carries 103..106; PlayerDataDto sends them through existing S:005-003 newAttrs.
- Realtime equip refresh S:008-001 sends direct 103..106 values; DVT RoleController:SetAttribute sets these attributes absolutely.
- BattleFighter now carries crit/critDamage/dodge/hit; player and follower snapshots receive available runtime values.
- Ordinary damaging attacks no longer use AGI for hit/miss:
  - PC baseline miss threshold = (targetLv - attackerLv + 5) * 0.2.
  - DVT Hit/Dodge are applied as percentage-point offsets: baseline + target Dodge - attacker Hit.
  - Hit/Dodge each clamp 0..30; final miss chance clamp 0..100.
  - When Hit=Dodge=0, behavior collapses to PC baseline.
- Status-only/control-skill hit logic is intentionally NOT rewritten here; it is a separate INT/level mechanic and remains for later dedicated audit.
- Crit:
  - roll chance = Crit% (clamp 0..100).
  - crit multiplier = 1 + (50 + CritDamage)/100.
  - critical attack returns EAttackResult.THUNDER=2.
  - Fake AGI crit remains removed.
- BattleDamageD4RegressionTest covers PC miss baseline, Hit/Dodge offsets/clamps, 50% crit base, direct-vs-100-based attribute decoding, guaranteed Crit=100 -> THUNDER, Crit=0 -> HIT.
- D1+D2+D3+D4 + DvtBattle/DvtSkill/DvtPetSkill suite PASS.
- Full test build --rerun-tasks PASS 21/21; git diff --check PASS.
- Runtime restarted: snapshot server-runtime/20261001-170420-24ebafc7, port 6713, DB tsm_dodo_9pet.
- Known source blocker: Myth Enchant/Astrolabe advanced-stat backend is not yet implemented in this server. Do not fabricate those sources; D4 battle/runtime contract is ready to consume 103..106 from real supported sources.
- Next planned damage step: D5 %ATK/%DEF and total damage modifiers from DVT.

## 2026-10-01 — Damage D5 ATK%/DEF% + total-damage attribute contract
- DVT EAttribute constants:
  - AtkPercent=101, DefPercent=102.
  - IncreaseTotalDamage=113, DamageReduction=114, ReduceTotalDamage=117, IncreaseDamageTakenSelf=118.
- DVT UIStatusDetail proves ATK/DEF percentage placement:
  - displayed ATK = base Atk + EquipAtk + math.round(base Atk * AtkPercent * 0.01) + other bonuses.
  - displayed DEF = base Def + EquipDef + math.round(base Def * DefPercent * 0.01) + other bonuses.
  - Percentage term applies to BASE Atk/Def only, not Equip/Turn3/Collection bonuses.
- DVT Common/functions.lua proves math.round: fractional part <0.5 -> floor, otherwise ceil. BattlePercentStatCalc uses Math.round for non-negative combat stats.
- IOS_Item_C audit:
  - ATK% rune IDs 55261..55266 raw values = 1,1,2,3,4,5.
  - DEF% rune IDs 55267..55272 raw values = 1,1,2,3,4,5.
  - Thus Mobile percentage attrs are direct values, not legacy raw-100.
  - All nonzero 101/102 rows are Myth Enchant kind=91; current server still lacks the Myth Enchant backend, so normal gameplay currently has no real nonzero source for these runes.
  - Item_C contains no 113/114/117/118 rows.
- EAttribute server constants now cover 110..118.
- EquipBonusData/EquipBonusCalculator, PlayerState, PlayerDataDto.newAttrs, realtime S:008-001, BattleFighter now carry 101/102/113/114/117/118.
- Player battle snapshot now applies DVT ATK%/DEF% exactly:
  - effective Atk = base Atk + round(base Atk * AtkPercent/100) + EquipAtk + Turn3Atk + CollectionAtk.
  - effective Def = base Def + round(base Def * DefPercent/100) + EquipDef + Turn3Def + CollectionDef.
- FollowNpc battle snapshot applies the same base-only percentage rule, then equip + collection.
- 113/114/117/118 are CONTRACT-READY ONLY. They are NOT applied to final damage yet:
  - exhaustive local source/decompile search found only enum/set-attribute/UI display semantics, no authoritative server-side formula/order.
  - GitHub search found no implementation.
  - Do not invent additive/multiplicative stacking; wait for authoritative DVT/server evidence or controlled runtime measurement.
- BattleDamageD5RegressionTest covers direct percentage decoding, legacy base100 preservation and DVT round-half-up base percentage behavior.
- D1+D2+D3+D4+D5 regression + DvtBattle/DvtSkill/DvtPetSkill suite PASS.
- Full test build --rerun-tasks PASS 21/21; git diff --check PASS.
- Runtime restarted: snapshot server-runtime/20261001-171851-3034a983, port 6713, DB tsm_dodo_9pet.
- D5 status:
  - D5a AtkPercent/DefPercent: PASS.
  - D5b total damage modifiers 113/114/117/118: runtime contract PASS, damage formula BLOCKED pending authoritative evidence.

## 2026-10-01 — D5 runtime confirmation
- User runtime tested the deployed D5 build and confirmed PASS.
- Damage audit progression confirmed by runtime: D1 ✅ → D2 ✅ → D3 ✅ → D4 ✅ → D5a ✅.
- D5b attributes 113/114/117/118 remain intentionally formula-blocked pending authoritative DVT/server evidence; do not guess or activate them.
- Current deployed runtime remains server-runtime/20261001-171851-3034a983 on port 6713 / DB tsm_dodo_9pet.

## 2026-10-01 — Damage D6 buff/debuff/status modifier audit
- DVT FightRoleController status mapping + IOS_Skill_C descriptions were used as primary evidence.
- PC bboycs BattleAbstract.cs used only as fallback for numeric multipliers missing from DVT descriptions.
- Removed fabricated generic ICE_BOUND x1.5 damage-taken modifier.
  - Ordinary IceBound skill 11014 now has no generic damage multiplier.
  - PC fallback proves only special 20026 Tam Nhan Bang Phong applies pre-DEF x0.75.
- Added missing DVT mapping: skill 13011 -> BattleStatus.SHRINK.
- BattleDamageCalc now applies classic pre-DEF status modifiers by exact groupedStatuses.skillId, preserving status lifetime and group behavior:
  - target 11002 Ice Wall -> x0.75.
  - target 10031 Gold Shield -> x0.50.
  - target 20026 special IceBound -> x0.75.
  - attacker 13012 Enlarge -> x1.25.
  - attacker 13011 Shrink -> x0.75.
  - attacker 12025 WildFightSoul/Cuong No -> x1.25.
  - attacker 14040 AngryMind/Ba Y -> x1.50.
- PC sequential semantics preserved: each multiplier truncates to Int immediately and order matches calculateHit.
- Defend corrected to both PC stages:
  1. pre-DEF c.dmg x0.75.
  2. existing final damage /2 remains; disadvantage element 0.8 still forces final=1.
- BattleStatus comments cleaned:
  - removed false ICE_BOUND x1.5 claim.
  - removed unproven Trance auto-wake-on-hit claim.
  - removed unproven generic Poison per-round HP claim.
- D6 blockers intentionally NOT guessed:
  - Mobile-only/newer buffs 11049/22006/23008 have qualitative DVT descriptions but no authoritative numeric magnitude/order.
  - poison/DOT: PC 14015 uses internal 20003 fixed 90, but DVT has many newer poison skills and no authoritative generalized Mobile formula.
  - Trance auto-clear on hit not implemented because no DVT/PC evidence found.
  - D5b 113/114/117/118 still contract-ready only; D6 did not find an authoritative numeric stacking formula.
- BattleDamageD6RegressionTest covers:
  - 11002/10031/20026 defensive multipliers.
  - 13012/13011/12025/14040 attacker multipliers.
  - generic 11014 IceBound no longer x1.5.
  - two-stage Defend behavior.
  - statusFromSkillId(13011)==SHRINK.
- Targeted D2+D6 regression PASS 18/18 tasks.
- D1..D6 + DvtBattle/DvtSkill/DvtPetSkill suite PASS.
- Full test build --rerun-tasks PASS 21/21; git diff --check PASS.
- Runtime restarted: snapshot server-runtime/20261001-180140-39088950, port 6713, DB tsm_dodo_9pet.
- Next suggested checkpoint after runtime D6 test: D7 Myth Enchant/Astrolabe backend OR D8 multi-hit/howMuchTimes audit; keep D5b formulas blocked until evidence exists.

## 2026-10-01 — D7 Astrolabe + Myth Enchant backend

### D7-A Astrolabe — PASS
- Lua DVT primary contract:
  - protocol mainKind=78, C:078-001 sends nextStar U8 only.
  - S:078-001 = isLevelUp bool + exactly 7 star level U8.
  - 7 stars, max level 10 each; natalPoint max 65535.
  - FindNextStar round-robin ordering copied exactly from Logic/Astrolabe.lua.
  - upgrade cost: needPoint from IOS_Astrolabe + gold = nextLv^2 * 10000.
  - star 1..6 bonuses Hpx/Def/Int/Spx/Agi/Atk applied to runtime; star7 Criticle is stored but NOT added to Crit because DVT UIStatusDetail does not consume Astrolabe.GetAttribute(7).
- IOS_Astrolabe loader active; runtime log confirms 7 stars loaded.
- Persistence: CharacterAstrolabes table + repository + cache/mutex + login load + packet send.
- Fixed D7 integration holes:
  - ItemHandler Koin wiring now receives AstrolabeSystem instead of default null.
  - disconnect flow unloads Astrolabe cache.
  - direct-use Astrolabe-point item now uses InventorySystem.setBagItemsAtomic.
  - AstrolabeSystem.restore(snapshot) added for compensation if inventory persistence fails after natalPoint mutation.
- Regression:
  - round-robin order, upgrade cost/persist, invalid no-mutate, level bonus sum, natalPoint ceiling.
  - restore compensation.
  - byte-level S:078-001 packet contract.

### D7-B Myth Enchant 108-002 — PASS
- Lua DVT primary contract:
  - mainKind=108, subKind=2.
  - request = myth bagIndex U16 + enchant-rune bagIndex U16.
  - TS cost = 10.
  - target must be EItemKind.Myth(90), rune EItemKind.Enchant(91).
  - rune threshold <= myth threshold.
  - rune attr1 kind must match one of 4 myth sub-attribute kinds.
  - success packet reads result=1 + bagIndex U16 + newLevel U16 + 4*[kind U8,value U16,extra U16].
  - result codes 2/3/4 map star-too-high / insufficient TS / no matching attr.
- ThingData wire DVT NFT extension preserved exactly:
  - 56 bytes total = base35 + mythLv1 + 4*(kind1+value2+extra2).
- MythEnchantSystem:
  - per-character mutex.
  - validates kind/threshold/matching attr.
  - rolls inclusive rune range from ItemDef attr1Value..attr2Value.
  - changes myth.extra only, preserves myth.value.
  - consumes one rune + 10 TS.
  - bag mutation uses setBagItemsAtomic; TS refunded if inventory persistence fails.
- Fixed protocol bug:
  - GodskinResponseSender previously sent mythSlot + runeSlot on success.
  - DVT actually reads mythSlot + mythLevel; now sends mythThing.mythLevel.
- Fixed gameplay integration:
  - equipped kind90 Myth sub-attrs now affect EquipBonus/Battle.
  - effective sub value = myth.value + myth.extra exactly as DVT ItemData:GetGodSubAttrText displays.
  - no legacy raw-100 decode is applied to Myth sub values.
  - supported final EAttribute kinds feed existing EquipBonus fields (legacy equip stats, 101/102, 103..106, and contract-ready 113/114/117/118).
- Regression:
  - enchant success/consume/refund/result codes.
  - ThingData 56-byte exact round-trip.
  - success packet proves second U16 is mythLevel, not rune slot.
  - equipped Myth sub attrs prove value+extra flows into ATK/ATK%/Dodge/Hit.

### Intentionally not implemented / blocked
- Myth MAIN attribute is NOT yet applied:
  - ItemDef attr1Kind on kind90 is a SacredEquipPrimaryDatas key, not final EAttribute.
  - main value depends on SacredEquipPrimaryDatas[index].upgradeLv[mythLevel].
  - needs authoritative SacredEquipPrimary loader/backend; do not treat Item_C attr1Value=0 as stat value.
- Godskin subKind 1 Upgrade and subKind 3 Remake remain non-mutating failure responses:
  - formulas/cost/success-rate/remake semantics need separate authoritative DVT data audit.
- Astrolabe star7 Criticle remains stored/display-contract only until DVT shows it contributes to player Crit.

### Validation/deploy
- Targeted Astrolabe/Myth/ThingData/D7Integration tests PASS.
- Wider Item/Inventory/Login/D4/D5/D7 integration suite PASS.
- Full test build --rerun-tasks PASS 21/21.
- git diff --check PASS.
- Runtime snapshot: server-runtime/20261001-210027-c0f49089
- port 6713 / DB tsm_dodo_9pet / 9-pet staging.

## D8 — Sacred/Myth main attributes

### D8-A1 SacredEquipPrimary loader — PASS
- DVT file: `IOS_SacredEquipPrimaryStatus_C.dat`.
- Contract: Int32LE count, then count × 35 bytes.
- Record: no U16 + attNo U8 + upgradeLv[0..15] U16.
- Real DVT file: 102 records.
- Added SacredEquipPrimaryDef + SacredEquipPrimaryDatLoader + GameDataManager wiring.
- Regression on real DAT + truncated-file rejection: PASS.

### D8-A2.1 Myth main attribute bridge — PASS
- For kind90 Myth, ItemDef.attr1Kind is SacredEquipPrimary key, not final EAttribute.
- Effective main attr = SacredEquipPrimaryDatas[attr1Kind].attNo with value upgradeLv[mythLevel].
- Legacy Item_C attr1Value is not treated as Myth stat.
- Existing supported equip/mobile attributes now flow through EquipBonusCalculator.

### D8-A2.2a Player HP%/INT% — PASS
- EAttribute 107 HpPercent and 109 IntPercent aggregated from Myth main/sub attrs.
- Lua MaxHP contract implemented as floor(finalBaseMaxHp * (100 + HpPercent) / 100).
- Lua INT% contract implemented as base INT + round(base INT * percent / 100), then flat equip/turn3/collection/astrolabe adds.
- Login, runtime equip refresh, battle snapshot, level-up, Astrolabe refresh and Collection refresh keep HP% consistently.
- Packet equip refresh sends HP%/INT%.
- D7/D5 regressions remain PASS.

### D8-A2.2a FollowNpc/Pet HP%/INT% — PASS
- Lua Item.lua confirms advanced equip attrs apply to FollowNpc too.
- PetSystem receives SacredEquipPrimary table.
- Pet load/EXP max-HP normalization applies Myth HpPercent.
- BattleManager pet fighter applies Myth HpPercent to MaxHP and IntPercent to base INT.
- TradeSystem pet preview uses Sacred main table and HpPercent MaxHP.
- 9-pet roster/deployment semantics unchanged.
- Added pet runtime regression and battle snapshot regression:
  - base MaxHP 1000 + Myth HP%10 => 1100.
  - base INT 100 + Myth INT%20 => 120.
- DvtCartTransition fixture updated to provide nonzero mocked stat capacity; production logic unchanged.

### D8-A2.2b ResistancePercent 90 — EVIDENCE BLOCKED / NOT IMPLEMENTED
- Lua defines ResistancePercent=90 as abnormal-status resistance rate.
- Lua stores/displays it for Player and FollowNpc advanced equip attrs.
- Client Lua does not expose the actual server-side resistance roll/formula.
- Local PC fallback source currently available did not yield an authoritative formula.
- No server mutation/combat roll added. Do not guess.

### D8 validation
- Targeted D8 Player/Pet/Battle + DvtPetExp + NinePetRosterPolicy + DvtItemWire: PASS.
- DvtCartTransition: PASS after fixing test fixture.
- Full `gradlew test build --rerun-tasks`: PASS, 471 tests passed, 1 skipped.
- `git diff --check`: PASS (CRLF warnings only).
- Next safe D8 step: Godskin 108-001 Upgrade audit in a separate small contract; 108-003 Remake stays separate.

## D8-B1 — Godskin Upgrade 108-001

### B1.1 packet contract — PASS
- C:108-001 request = mythSlot U16LE + supportSlot U16LE.
- supportSlot=0 is normal Money/TS probability path.
- supportSlot>0 is Godskin-stone path.
- S:108-001 result U8.
- success payload = mythSlot U16LE + newLevel U8 + 4*(kind U8,value U16,extra U16).
- result 2 = probability fail; result 3 = insufficient currency; result 255 = generic error.

### B1.2 SacredEquipUpgrade data — PASS
- IOS_SacredEquipUpgrade_C.dat: count=15, record=15 bytes.
- record = lv U8 + pro U8 + currency kind U8 + spend[1..6] U16.
- Lv1..6 currency kind 3=Money; Lv7..15 kind 8=TS.
- spend index is item threshold 1..6.
- Added SacredEquipUpgradeDef + SacredEquipUpgradeDatLoader + GameDataManager wiring.
- Real-DAT regression PASS.

### B1.3a Godskin Stone path — PASS
- EItemKind.SpecialAbility=38; EItemUseKind.GodskinkStone=246.
- support stone must be kind38/specialAbility246 and skillLink > current mythLv.
- stone skillLink is target Myth level.
- DVT text 23646 = "Nhất Định"; stone path is deterministic success.
- Added MythUpgradeSystem stone-only path with per-character mutex.
- Atomic mutation: Myth mythLevel -> stone.skillLink, four Myth attrs preserved, exactly one stone consumed.
- Added exact S:108-001 byte regression proving newLevel is U8.
- GodskinHandler now handles sub1 stone path; supportSlot=0 still returns non-mutating invalid until fail-consumption semantics are authoritative.
- Remake sub3 remains separate/non-mutating.

### B1.3b normal Money/TS path — EVIDENCE BLOCKED
- DVT gives rate/cost table and result=2 probability failure.
- DVT/public system description confirms failure does not destroy equipment or reduce Myth level.
- Available evidence does not explicitly prove whether Money/TS is consumed on a failed probability roll.
- Do not guess or mutate this path yet.

### B1 validation
- MythUpgradeD8B1RegressionTest + SacredEquipUpgradeD8B1RegressionTest + D7IntegrationRegressionTest: PASS.
- Full gradlew test build --rerun-tasks: PASS.
- git diff --check: PASS (CRLF warnings only).

## D8-C1 — Godskin Remake 108-003 audit

### C1.1 packet/cost/hammer contract — PASS
- C:108-003 request = mythSlot U16LE + hammerSlot U16LE.
- S:108-003 success = result 1 + mythSlot U16LE + 4*(kind U8,value U16,extra U16).
- Success packet does not send mythLevel; remake preserves Myth upgrade level.
- Hammer item kind = EItemKind.Hammer(92).
- Hammer threshold must be >= target Myth item threshold.
- TS remake cost is keyed by HAMMER threshold:
  threshold 1..6 => [5,10,20,40,100,100] TS.
- result 0 = insufficient hammer, 2 = hammer star/threshold too low, 3 = insufficient TS, 255 = generic error.
- DVT UI warns when existing myth.extra > 0; official behavior indicates remake rerolls sub attrs and enchant extras are lost.

### C1.2a SacredEquip sub/domain data loaders — PASS
- IOS_SacredEquipSubStatus_C.dat:
  count=18, 23 bytes/record.
  record = no U16 + attNo U8 + random[10] U16.
  first: no1 att207 random first=180 last=450.
  last: no18 att108 random first=3 last=7.
- IOS_SacredEquipSubStatusDomain_C.dat:
  count=73, 2 bytes/record.
  physical/domain contract = threshold U8 + attNo U8.
  first=(1,207), last=(6,90).
- Added SacredEquipSubDef / SacredEquipSubDomainDef.
- Added SacredEquipSubDatLoader / SacredEquipSubDomainDatLoader.
- Added GameDataManager wiring for both tables.
- SacredEquipSubD8C1RegressionTest: PASS.
- MythUpgradeD8B1RegressionTest remains PASS.

### C1.2b Remake reroll algorithm — NOT IMPLEMENTED YET
- Remaining authoritative question: exact selection/reroll algorithm for four Myth sub-attribute slots as a function of mythLevel and threshold.
- DVT client receives final four attrs from server and does not expose the server roll algorithm.
- Do not invent slot-count/unlock/reroll behavior.

### C1.2b Remake reroll evidence — BLOCKED AFTER EXTENDED AUDIT
- Official DVT system page confirms:
  - +3/+6/+9/+12 unlock sub slots 1/2/3/4 if not yet open.
  - If the relevant slot is already open, that milestone increases a random existing sub-attribute.
  - +15 always increases a random existing sub-attribute.
  - Remake rerolls all opened sub-attribute kinds/values, including the random enhancement gains; Myth level/main attr stay unchanged and enchant extras disappear.
- Full SubStatus table strongly forms 5 min/max pairs in random[10] for every attribute.
  Examples:
  - att207: 180-210, 240-270, 300-330, 360-390, 420-450.
  - att108: 3-3, 4-4, 5-5, 6-6, 7-7.
- Domain table gives allowed attribute kinds per Myth threshold 1..6; no duplicates should be assumed without direct server evidence.
- Neither Lua/client, public source search, decompiled client folders, nor old PC server reference contains the authoritative backend selection algorithm tying enhancement roll-counts to random[10] pair index / redistribution during Remake.
- DB sample correlation was attempted read-only but blocked by tool credential/runtime access policy.
- Therefore no Remake mutation/reroll code added. Do not infer the missing algorithm from pattern alone.

### D8 current validation / closure
- Sacred primary, upgrade, sub and sub-domain loaders: targeted PASS.
- Godskin Stone Upgrade 108-001 deterministic path: PASS.
- Myth Enchant 108-002: PASS from D7.
- Full `gradlew test build --rerun-tasks` after D8-C1.2a: PASS.
- `git diff --check`: PASS except existing CRLF normalization warnings.
- Evidence-blocked, intentionally non-mutating:
  1. normal Money/TS Godskin upgrade path (failed-roll currency consumption unresolved);
  2. Godskin Remake reroll algorithm;
  3. ResistancePercent=90 combat-roll formula.
- No D9 roadmap exists in repo/checkpoint yet; do not invent a D9 feature name without a new evidence-driven audit target.

## D9 — Evidence-driven backend gaps

### D9-A Achievement Honor condition — PASS
Lua DVT evidence:
- CheckCondition.lua: ECondition.Honor=13 and current value = Role.player:GetAttribute(EAttribute.Honor).
- EAttribute.Honor=62.
- Role.ReceivePlayerData derives Honor from KillNum.
- Calculator.lua: Calc.GetHonor(value) returns value unchanged.
Therefore current DVT Honor == KillNum; no separate Honor persistence field is required.

Server fix:
- AchievementSystem COND_HONOR no longer hardcodes 0.
- It now returns runtime PlayerState.killNum.
- No unrelated Honor field/schema added.

Regression:
- AchievementHonorD9ARegressionTest:
  - KillNum=120 with Honor >=120 succeeds and sets complete flag.
  - KillNum=119 fails condition and does not set flag.
- targeted test: PASS.
- full gradlew test build --rerun-tasks: PASS.
- git diff --check: PASS except existing CRLF normalization warnings.

### D9-B Battle fightArea kind=4 — EVIDENCE BLOCKED
- Kotlin historical JUMP_THREE currently targets selected row and +/-2 rows.
- DVT FightField.GetEnemyIdx explicitly implements kinds 1,2,3,5,6,7,8,255 and skips kind=4 entirely.
- No Lua contract exists for kind=4 in this DVT snapshot.
- Do not change combat shape based on inference.

### Bank C:030-003 audit — KEEP STUB
- protocal.lua only comments C:030-003 bank upgrade.
- No protocolTable[30][3] response handler/payload contract is present.
- DVT bank expansion currently routes through the other expansion/UISell path.
- No backend mutation added.

### D9-C Message Board — AUDIT READY, NOT IMPLEMENTED
Eve.emg evidence:
- resultClass=7 pStyle=7: message-board view/open, 11 records.
- pStyle=8: message-board open flow, 7 records.
Lua protocol:
- C:036-001 = add leave-message, raw string.
- C:036-002 = delete message, index U16.
- S:036-003 = open UIMessageboard, no payload.
- S:036-004 = all messages repeated:
  roleId I64 + nameLen U8/name + sex U8 + headStyle U8 +
  color1 I32 + color2 I32 + time Double + messageLen U8/message.
- S:036-005 = one newly added message, same identity/header/time then remaining payload message.
- S:036-006 = delete message index U16.
- S:036-007 = result; result=1 then cause U8 (cause=7 has client error text).
Client limits:
- UIMessageboard accepts non-empty messages <=60 characters.
- client taboo filter exists, but server-side validation should still be authoritative/safe.
- ERoleCount.Messageboard=13, max 3 sends/day.
- normal Chat.maxLeaveMessage=500 (internal build 23).
- new leave messages are inserted at the front and oldest entries are trimmed.
Server currently has no mainKind=36 handler.
Safe next split:
1. D9-C1 Eve pStyle7/8 -> S36-003 open bridge, with regression.
2. D9-C2 add-message contract + RoleCount13 daily cap + <=60 validation.
3. D9-C3 global store/persistence/broadcast/delete only after checking session-broadcast and persistence infrastructure.

### D9-C1 Message Board open bridge — PASS
- DVT Eve.emg resultClass=7 pStyle=7/8 both route to Message Board.
- Lua protocal.lua: S:036-003 has no payload and calls UI.Open(UIMessageboard).
- Added EvePlayerResultKind.OPEN_MESSAGE_BOARD.
- EveResultClassPolicy.playerOperation(type=7, pStyle=7/8) maps to OPEN_MESSAGE_BOARD.
- EveResultExecutor sends responseSender.sendOpenMessageBoard(session).
- ResponseSender + ResponseSenderImpl + NpcEventResponseSender now serialize S:036-003 as bytes [36,3,0].
- Regression MessageBoardD9C1RegressionTest:
  - pStyle 7/8 map correctly; non-type7 does not.
  - byte-level packet exactly [36,3,0].
- Targeted test PASS.
- Full gradlew test build --rerun-tasks PASS.
- git diff --check PASS except existing CRLF normalization warnings.
- D9-C2 remains separate: C36-001 add message + RoleCount[13] daily cap + <=60 validation. No persistence/broadcast/delete added in C1.

### D9-C2 Message Board add validation — PASS
- Added mainKind 36 MessageBoardHandler and registered it in PacketDispatcher/Koin.
- C36-001 payload is treated as the whole remaining message string.
- Server validates 1..60 Unicode code points.
- RoleCount[13] is capped at 3 successful sends per day.
- Valid add increments RoleCount[13] and sends the RoleCount update immediately.
- Rejects use S36-007 result=1 cause=7, bytes [36,7,0,1,7].
- No message persistence/broadcast/delete is implemented in C2.
- MessageBoardD9C2RegressionTest targeted PASS; full test/build PASS; diff check clean except CRLF warnings.

### D9-C3 Message Board persistence/snapshot/broadcast — PASS
- Added MessageBoardMessages persistence table and MessageBoardRepository/Impl.
- Stored fields match S36-004/005 identity payload: character/player id, name, sex, headStyle, colors, timestamp, message.
- Repository keeps the newest 500 messages and returns snapshot oldest -> newest; DVT client inserts each at index 1, resulting in newest-first UI order.
- LoginService sends S36-004 exactly once per character login after RoleCount init; Chat.Clear() resets leaveMessages on session reset, so reopening UI does not duplicate snapshot rows.
- S36-004 uses message byte-length; S36-005 uses raw message to packet end exactly as protocal.lua reads data.length.
- C36-001 flow is now validate -> persist -> increment RoleCount[13] -> broadcast S36-005 to all IN_GAME sessions.
- Persist failure sends S36-007 failure and does not consume daily quota.
- MessageBoardD9C3RegressionTest locks S36-004/005 bytes, broadcast, and persist-before-quota behavior.
- C36-002/S36-006 delete remains intentionally unimplemented: current DVT UI has no Network.Send(36,2) path, so delete authorization/semantics are not inferred.
- Targeted C1+C2+C3 tests PASS; full gradlew test build --rerun-tasks PASS; git diff --check PASS except existing CRLF warnings.

## Git / push safety rule — IMPORTANT FOR NEXT CHAT
- DO NOT push temporary/generated/local-only files to GitHub.
- Before every commit/push, inspect `git status` and explicitly exclude temp/runtime/cache artifacts (for example `.kotlin/`, generated runtime snapshots, scratch scripts, local test helpers outside intended repo sources, temporary dump/query scripts, and similar machine-local files).
- Only commit/push source, tests, migrations/data definitions, and documentation that are intentional and already validated/PASS.
- Current working branch is `feature/9-pet`; working tree contains many valid uncommitted changes from D7/D8/D9, so never use broad cleanup/reset commands that could discard valid work.
- If unsure whether a file is temporary, leave it uncommitted and verify before push.

## D9-D quest audit — 10143 / 12292

### Đại Phá Quân Ô Tôn — Mark 10143/10144
- IOS_MarkStep_C.dat: 10143 step1 -> scene 12820, end pos ~1390,310, eventKind=1/eventId=1, checkTeam=true.
- Exact Eve.emg scene12820/eve6 contract:
  - when 10143 step=1, 10144 absent and skill14001 state matches:
    Talk 10735 -> Talk 10737 -> battle fightDataKey=11.
  - battle win -> Talk10739 + item46285 x1 + skill14002 x1 + COMPLETE 10143 + ADD 10144 +1.
  - lose/escape -> Talk10736.
- Runtime log proves current character had 10143=1 on map12820.
- Runtime log contains no fightDataKey=11, so observed run never reached the required quest battle.
- NpcEventHandler C20-006 correctly delegates to EveFlowController.advanceEvent; EveFlowController type=1 Talk waits for C20-006, then next result can reach type=3 battle.
- No server patch yet: old log does not prove the client sent both required C20-006 advances.
- Separate real runtime observation: Mine battle fightDataKey=1 and ServerBump fightDataKey=15 can overlap on map12820; BattleManager rejects the second battle while one is active. Suspicious but not yet proven causal for mission10143.

### Quân đoàn Hương Dũng — Mark 12292/12293
- Current runtime state from log: 12292=3.
- Therefore current expected step is step3: Tốt Bá Lv11~15 -> scene12705, end pos ~590,320, event1/1.
- Full route:
  step1 scene12992; step2 scene12706; step3 scene12705; step4 scene12992; step5 scene12704; step6 scene12703; step7 scene12702.
- Lua MarkManager.AddMissionStep is additive; Kotlin QuestSystem additive semantics are correct. Do NOT convert resultValue to absolute step.
- No runtime visit to map12705 exists in current logs, so no runtime failure is proven yet.
- Focused Eve chains use supported condition/result classes; no obvious unsupported-policy blocker found.

### Next runtime verification
1. For 10143: click the quest NPC at Lư Long khẩu and advance through BOTH Talk messages. Expected server log must show fightDataKey=11. Win should complete10143 and add10144=1.
2. For 12292=3: navigate to scene12705 and trigger event1/1 near 590,320. Capture log if it fails to progress.

### Runtime fix — 10143 bị Mine/ServerBump chen ngang
- Runtime 2026-10-02: ClickNpc map12820/eve6 dispatch Talk đầu tiên của mission10143, sau ~0.9s Mine map12820/mine1/eve7 tự kích hoạt và mở fightDataKey=1 trước khi quest chain đi tiếp tới fightDataKey=11.
- Root cause class: ambient Eve trigger có thể ghi đè EventSession quest giữa lúc hội thoại đang chạy.
- Fix: EveEventStateMachine.tryActivateIfIdle() dùng ConcurrentHashMap.putIfAbsent để đóng race check->activate.
- EveTriggerRouter Mine(triggerKind7) và ServerBump(triggerKind3) dùng onlyIfIdle=true; không được overwrite active quest/dialog/choice/battle/warp session.
- Regression: QuestEventInputGuardTest.backgroundEventCannotOverwriteActiveQuestSession.
- Targeted QuestEventInputGuardTest: BUILD SUCCESSFUL.
- Targeted MineEncounterPacingTest: BUILD SUCCESSFUL.
- Full `gradlew test build --rerun-tasks`: BUILD SUCCESSFUL.
- Chưa push GitHub. Cần restart server để runtime test lại 10143; expected Talk -> Talk -> fightDataKey=11 -> win -> complete10143 + add10144=1.
### Runtime fix — Hương Dũng 12292 step3 / scene12705
- Runtime log: char mission12292=3, click NPC1 on map12705; all NPC events failed and server fell back to generic Talk.
- Focused Eve.emg audit shows the correct catch-up branch is eve6 chain C5..C9, including class7/param0 pStyle1 ops2 value14 and mission12292 step>=3.
- Root cause: class7 param0 (player level) was incorrectly using compareStep(), where ops2 means "<". DVT Eve level data here uses ops1 "<" and ops2 ">", so Lv39 was rejected from the intended level>14 branch.
- Fix: added compareLevel() for class7/param0 only. Keeps level semantics 0==,1<,2>,3<=,4>,5>=,6!=; mission/skill compareStep remains unchanged.
- Added EveConditionEvaluatorTest regression for Hương Dũng Lv39: ops2/14 true, ops1/15 false.
- Targeted EveConditionEvaluatorTest PASS.
- Full `gradlew test build --rerun-tasks` BUILD SUCCESSFUL.
- Need restart server and retest NPC Tốt Bá on scene12705. Expected: no fallback greeting; eve6 quest chain should trigger/progress instead.
- Previous ambient-event race fix for Mine/ServerBump remains in place and full suite is still green.
- Not pushed to GitHub yet.
## D10 — Eve Quest Engine Hardening — NEXT CHAT START HERE

### Why D10
Recent runtime failures proved the remaining quest issues are engine-wide, not isolated quest data bugs:
- Ambient Eve triggers (Mine/ServerBump) could overwrite an active quest/dialogue session.
- Eve level conditions (class7/param0) were using the wrong comparator family, causing valid quest branches such as Hương Dũng 12292 step3 to be rejected and fall back to generic NPC Talk.
Therefore the next phase must harden the Eve/quest engine globally instead of fixing quest IDs one-by-one.

### Non-negotiable rules
- Lua DVT + Eve.emg + real client data remain the source of truth.
- Do not modify Lua to fit Kotlin.
- Do not guess unknown semantics.
- Fix by condition/result shape, not by hardcoding specific quest IDs.
- Preserve all stable D7/D8/D9/9-pet work.
- Do not reset/clean the working tree broadly.
- Do not push temp/scratch/runtime/cache files.
- Before any commit/push, inspect git status and include only intentional PASS source/tests/docs/data changes.

### D10-A — Full Eve shape inventory
Goal: generate an authoritative inventory from real Eve.emg of:
- conditionClass
- conditionParameter
- conditionParameterStyle
- conditionOps
- conditionSubItem
- resultClass
- resultParameter
- resultParameterStyle
- resultType
- scene/eve frequency
Classify every shape:
1. implemented + evidenced
2. implemented but semantics conflict with data
3. unsupported/fallback false
4. policy blocked
5. insufficient evidence
Deliver a machine-readable/report file under docs/tools only if it is intentional; scratch analyzers must stay outside repo or be deleted.

### D10-B — Comparator/condition semantics audit
Audit condition families globally:
- class1 Item
- class2 Mission/Mark step+flag
- class4 Skill
- class5 Money
- class7 general attributes:
  - param0 Level
  - Moral
  - Bag/Pet space
  - Equipment/storage
  - Element
  - Honor
  - Sex
  - ServerId
  - Career
  - Marriage/children
  - Mount, etc.
- class8 Battle result
- class9 FollowNpc
- class10 Choice
- class12 Team
- remaining known classes
For each family, compare Kotlin semantics against Eve.emg patterns and Lua DVT evidence.
Keep distinct comparator families where DVT semantics differ; do not reuse compareStep blindly.

### D10-C — Event continuation/state-machine hardening
Audit every chain type:
- Talk
- Choice
- Battle
- Warp
- immediate result
- post-battle continuation
- C20-006 advance
- C20-009 choice
Verify:
- active quest session cannot be overwritten by Mine/ServerBump/background events
- stale client inputs cannot resolve the wrong event
- battle result resumes the same Eve chain correctly
- fallback Talk is never used when a valid quest branch should resolve
- session cleanup happens only after true end/abort
Create targeted regressions for race/continuation behavior.

### D10-D — Static quest reachability audit
Build a static analyzer over Eve.emg/Mark data to find:
- mission steps with no reachable matching Eve branch
- mutually impossible condition groups
- branches blocked only by unsupported semantics
- result chains that can never reach mission increment/complete
- Talk/Choice/Battle chains that terminate before required reward/progression
- scene/MarkStep targets whose event does not exist
Prioritize main/tutorial/guidance quests first.

### D10-E — Data-backed regression suite
For every semantics fixed and proven:
- add regression from real Eve/Mark examples
- prefer representative shape-level tests over quest-ID hardcodes
- include a small number of known quest regressions as integration locks:
  - 10143 Đại Phá Quân Ô Tôn
  - 12292 Hương Dũng
Run targeted tests first, then full test build.

### D10-F — Remaining evidence blockers report
At the end, report only semantics that still lack authoritative DVT evidence.
Do not implement those by inference.
The goal is for unsupported/fallback quest failures to be explicit and measurable rather than silently producing generic NPC dialogue.

### Current runtime checkpoints before D10
1. 10143 Đại Phá Quân Ô Tôn:
   - scene12820/eve6 contract is Talk -> Talk -> fightDataKey11 -> win -> item46285 + skill14002 + complete10143 + add10144=1.
   - ambient Mine/ServerBump overwrite race was patched using EveEventStateMachine.tryActivateIfIdle() + onlyIfIdle for background triggers.
   - full test/build PASS.
   - still requires runtime retest after server restart.

2. 12292 Hương Dũng:
   - current runtime mission step was 3.
   - scene12705 valid catch-up branch for high-level character was rejected because class7/param0 Level used compareStep().
   - introduced dedicated compareLevel() for class7/param0; Lv39 now satisfies ops2/value14 (>14) and rejects ops1/value15 (<15).
   - targeted EveConditionEvaluatorTest PASS.
   - full test/build PASS.
   - requires runtime retest after server restart.

### NEXT CHAT FIRST ACTION
Do not start by fixing another individual quest.
Start D10-A immediately:
1. inspect current Eve parser/tools
2. generate full condition/result shape inventory from real gamedata/Eve.emg
3. compare inventory against EveConditionEvaluator + EveResultClassPolicy + EveResultExecutor
4. produce the first mismatch list
5. pick the highest-impact evidenced mismatch and patch it as D10-B1
6. targeted regression -> full test/build -> checkpoint

### D10-A production Eve inventory — PASS
- Switched D10 inventory generation to the production Kotlin EveDataLoader; direct scratch binary parsing is not trusted for patch decisions.
- Trusted baseline from gamedata/Eve.emg:
  - parsed scene objects: 3,797
  - condition rows: 77,001
  - unique condition shapes: 7,686
  - result rows: 164,910
  - unique result shapes: 8,236
- Intentional report saved as docs/d10-eve-inventory.md.
- All _D10*ScratchTest.kt files were removed from the repo before full validation.
- Confirmed blockers:
  - conditionClass17: child/foster subsystem missing; keep conservative false.
  - class7/param12: WaterWar/warship warehouse capacity; server subsystem missing; do not fake.
  - class7/param11: Turn2Ball/Tiên Châu status; exact pStyle mapping still pending evidence.

### D10-B1 class7/param9 reincarnation predicate — PASS
- Production Eve inventory: exactly 41 rows, only pStyle1..4 with ops=0/value=0/subItem=0.
- Previous Kotlin mapping param9 to player element was wrong.
- Evidence:
  - scene12093 pStyle1/2 surrounds Mark10869 "Thoát Thai Chuyển Sinh".
  - scene12180 progression tiers pair pStyle3 with Lv>39, pStyle1 with Lv>79, pStyle2 with Lv>119, matching Nhị Chuyển / Nhất Chuyển / chưa chuyển sinh.
  - scenes12064/12711/59411 pStyle4 directly lead to Talk52419 "sau khi Nhị Chuyển hãy đến tìm ta"; pStyle3 continues post-Nhị-Chuyển flow.
  - scene49950 has separate battle tables pStyle4 vs pStyle3 over identical level bands, consistent with pre/post Nhị Chuyển state.
- Corrected class7/param9 semantics:
  - pStyle1 => turn >= 1 (đã Nhất Chuyển)
  - pStyle2 => turn < 1 (chưa Nhất Chuyển)
  - pStyle3 => turn >= 2 (đã Nhị Chuyển)
  - pStyle4 => turn < 2 (chưa Nhị Chuyển)
- Threshold semantics intentionally make Turn3 remain "đã qua Nhị Chuyển".
- Uses existing PlayerEventState.rebornCount populated from runtime player turn; no schema changes.
- Replaced the old element-based regression with Turn0/1/2/3 predicate coverage.
- Targeted EveConditionEvaluatorTest PASS.
- Full gradlew test build --rerun-tasks PASS.
- git diff --check PASS except existing CRLF normalization warnings.
- Not pushed to GitHub.

### D10-B2 class7/param11 Turn2Ball completion — PASS
- Production Eve inventory: 10 rows total, only pStyle1/2 with ops=0,value=0,subItem=0 in scene12064/eve70.
- DVT Talk33147 explicitly says pStyle1 branch means "đã thu thập đủ 12 Thiên Châu".
- UISkillTree.lua: Turn2_MaxBall=12; S23-77 carries available Turn2Ball count plus activated ball indexes.
- Server already has authoritative persistence/cache:
  - Turn2SkillState.availableBalls
  - Turn2SkillState.activeBalls
  - CharacterTurn2Skills kind1/index1..12
  - SkillSystem.SkillCache.activeTurn2Balls
- Important: availableTurn2Balls is NOT used as completion evidence.
- Added PlayerEventState.activeTurn2Balls from SkillSystem cache.
- Implemented class7/param11:
  - pStyle1 => all indexes 1..12 active
  - pStyle2 => not all indexes 1..12 active
- Indexes outside 1..12 do not satisfy completion.
- EveConditionEvaluatorTest covers 11/12, 12/12, and noisy extra-index state.
- Targeted EveConditionEvaluatorTest PASS.
- Full gradlew test build --rerun-tasks PASS.
- git diff --check clean except existing CRLF normalization warnings.
- No schema change required; uses existing authoritative Turn2 persistence.
- Not pushed to GitHub.

### D10-B3 resultClass15 novice-tutorial operation — EVIDENCE BLOCKED
- Trusted production loader shows exactly one row: scene10821/eve3 type0/class15 param1/pStyle1/value0.
- Mission12280 + Talk/Surface data prove this row belongs to choice "Bỏ qua Hướng Dẫn Tân Thủ".
- Exact server-side mutation remains unknown.
- C5-008 tutorial-complete exists separately; same numeric 1 is not enough to equate class15 with guideId1.
- No authoritative Lua/decompile/PC handler found.
- No patch made.

### D10-B4 class7/param16 mount-state predicate — PASS
- Production inventory: 77 rows total; 38 subItem1 + 39 subItem2, all pStyle1/ops0/value0.
- Talk37776 on every subItem1 blocker says "Xin lỗi, mời bạn xuống ngựa được không?".
- Representative scene12901/eve2 proves subItem1 blocks mounted players while subItem2 participates in the normal unmounted continuation chain.
- Fixed semantics:
  - subItem1 => mounted.
  - subItem2 => not mounted.
- Regression updated in EveConditionEvaluatorTest for both states.
- Targeted EveConditionEvaluatorTest PASS.
- Scratch test/scripts removed before full validation.

## D10-B5 — class7/param0 pStyle2 Element + pStyle3 TransformNpcId — PASS
- Production Eve proves class7/param0 is overloaded by pStyle, not always player level.
- pStyle1 remains player level with compareLevel().
- pStyle2 uses element identity. Lua DVT EElement: Earth=1, Water=2, Fire=3, Wind=4. Representative scene11525/eve5 has four mutually exclusive pStyle2/ops5/value1..4 branches whose rewards are the four elemental items 23086..23089; therefore ops5 is exact identity, not a numeric threshold.
- pStyle3 uses current transform NPC/model ID. DVT S:005-005 writes Role.players[roleId].npcId. Representative Eve values 38028/38029/38030/38073/38079/11050 are real NPC IDs and match Transform Doll malePicId targets; scene12093 checks transform 11050 (Hứa Chử) with ops5/ops6.
- PlayerEventState/EveStateBuilder already carry transformNpcId from PlayerState. ItemHandler Transform Doll path already mutates runtimeState.transformNpcId and broadcasts S:005-005.
- Added compareIdentity(): ops0/5 ==, ops6 !=; unknown ops fail closed. pStyle2/3 use this comparator instead of generic compareStep/Eve numeric threshold behavior.
- Permanent EveConditionEvaluatorTest covers pStyle2 element exclusivity and pStyle3 transform equality/inequality, including protection against accidentally using player level.
- Removed temporary D10B5ScratchTest.kt from repo.
- Full suite initially exposed two stale strict mocks in DvtItemWireTest because ItemHandler now checks ItemDef.kind before NPC-picture handling. Fixture now explicitly sets kind=0 (non-Transform); production logic unchanged.
- Targeted EveConditionEvaluatorTest PASS.
- Targeted DvtItemWireTest PASS.
- Full gradlew test build --rerun-tasks PASS.
- git diff --check PASS except existing CRLF normalization warnings.


# CHECKPOINT 2026-10-02 — PRODUCTION SERVER CONSOLIDATED

## Authoritative runtime topology
- Development/work machine: `192.168.1.99`.
- Production game server: `192.168.1.69:6613`.
- Production server source path: `C:\Server-Game\tsm_dodo`.
- MySQL used by production: `192.168.1.69:3307`, database `tsm_dodo`.
- Client staging `ServerList.json` has been corrected to point to `192.168.1.69:6613`.
- The old staging/test runtime on `192.168.1.99:6713` has been STOPPED.
- Going forward, use `192.168.1.69:6613` as the only runtime test server unless the user explicitly asks for an isolated staging runtime.

## Synchronization state
Canonical commit deployed today before this checkpoint:
`e50ec724d92c289e061569619b13d66391c704c5`

Verified synchronized at that commit:
- LOCAL dev branch `feature/9-pet`
- GitHub `feature/9-pet`
- GitHub `main`
- Production server source `C:\Server-Game\tsm_dodo` on `192.168.1.69`

Production runtime was restarted after the source update and successfully reached:
- DB connection established to `192.168.1.69:3307`
- game data loaded
- Netty bound to `0.0.0.0:6613`
- server ready / waiting for connections

No startup `ERROR`, `Exception`, or `WARN` was found in the checked runtime log window.

IMPORTANT: Source deployment is complete, but gameplay features still require runtime verification feature-by-feature. Do not confuse successful startup with gameplay PASS.

## What e50ec724 contains
This deployment includes the accumulated D7-D10 backend work and previously committed 9-pet/server fixes, including:
- D7 Astrolabe backend and Myth Enchant backend
- D8 Sacred/Myth support completed where DVT evidence exists
- D9 Achievement/Message Board work and other evidenced fixes
- D10 Eve semantics work through D10-B5
- 9-pet server-side fixes that had already been committed
- Eve/quest/runtime fixes accumulated before this checkpoint

The older D10 notes above that say `Not pushed to GitHub` are historical snapshots and are superseded by this checkpoint: the committed D7-D10 batch is now pushed and deployed.

## Production-only local differences / cleanup
- Production `app/src/main/resources/application.conf` currently differs from Git because the production machine retains its local DB runtime configuration. Do not overwrite it casually during cleanup or pulls.
- Production has one untracked local file named `ProgramDatasshadministrators_authorized_keys.txt`. Leave it untouched unless explicitly investigating SSH setup.
- Server cleanup was performed conservatively: disposable Gradle/build/cache/restart-log artifacts were removed where safe; tracked files accidentally identified as cache/report were restored immediately.
- Critical data directories were preserved: `cdn`, `gamedata`, `File.dat`, `SQL`, `backups`, source trees, runtime config, and current logs.

## Runtime rule from now on
1. Build/edit/audit source on `192.168.1.99` working copy.
2. Commit + push to GitHub.
3. Fast-forward production source on `192.168.1.69`.
4. Restart production runtime on `192.168.1.69:6613`.
5. Test gameplay only against `192.168.1.69:6613`.
6. Do not launch a `.99:6713` staging runtime unless the user explicitly asks for it.

## D10 current semantic state
Confirmed PASS before deployment:
- D10-A production Eve inventory
- D10-B1 class7/param9 reincarnation
- D10-B2 class7/param11 Turn2Ball completion
- D10-B4 class7/param16 mount-state
- D10-B5 class7/param0 overload: level / element / transformNpcId

Evidence-blocked, do not guess:
- resultClass15 novice tutorial mutation
- conditionClass17 child/foster subsystem
- class7/param12 WaterWar warship storage predicate

## NEXT CHAT FIRST ACTION
Read this entire checkpoint first, then continue from this section as the newest source of truth.
Do NOT recreate or use the old `.99:6713` staging runtime.
Use production runtime `192.168.1.69:6613` for gameplay verification.
Continue D10 from the next high-frequency unsupported/fallback result-policy shape with authoritative DVT evidence (candidate D10-B6: global resultClass11 or resultClass13). Audit Lua DVT/EventHandler and production Eve inventory first; patch only when evidence is sufficient.
After each patch: targeted regression -> full `gradlew test build --rerun-tasks` -> `git diff --check` -> commit/push -> fast-forward `.69` -> restart -> runtime test -> mark PASS/FAIL.

## 2026-10-02 — IL2CPP research snapshot + tool — PASS
- Full native research snapshot is persisted under `docs/research/il2cpp/TS-Origin-VTC-1.3/`.
- Source APK: package `com.vtcmobile.gz06`, app label `TS Origin VTC: Xuất Chiến`, versionName `1.3`, versionCode `8`.
- Stored artifacts: original APK, ARM64 `libil2cpp.so`, `libunity.so`, and matching `global-metadata.dat`.
- Verified native pair: `ELFCLASS64 / EM_AARCH64`, metadata sanity `0xFAB11BAF`, metadata version `31`.
- Exact sizes and SHA256 hashes are documented in `docs/research/il2cpp/TS-Origin-VTC-1.3/README.md`.
- Wails/Go research tool source and reference executable are stored under `tools/ts-il2cpp-research/`.
- Tool supports LDPlayer APK pull, APK extraction, pair verification, SHA256, text/U16/U32 search with offsets, ASCII string dump, Markdown report, and optional Il2CppDumper execution.
- Real DVT tests PASS: pair inspection, binary search, string dump, Wails build, and executable launch.
- For future crafting/native audits, use this snapshot when Lua DVT/client/data are insufficient. Lua DVT remains the primary source of truth.

## 2026-10-02 — Generic Compound completion audit
- Re-audited Lua DVT `UICompound.lua` / `CompoundData.lua`, `IOS_Compound.dat`, IL2CPP dump, public PC source, local legacy server sources/binaries and historical/official TS documentation.
- Confirmed DVT client contract: three input slots; per-slot quantity is sent in C:023-014; generic input filter rejects Pearl/Gem/Remove/Ice/Item_22/Reinforced/ExclusiveWeapon plus `restrict & 4`.
- `restrict` semantics from DVT ItemData: bit4 = cannot be compound material; bit8 = cannot be compound result.
- `IOS_Compound.dat`: 1374 rows; effective ratios are 1:1 or 1:1:1. Legacy dirty rows are normalized by material presence: 3 rows materialC>0 with degreeC=0; 12 rows materialC=0 with nonzero degree.
- Batch semantics: N complete recipe sets consume N of each degree-normalized material and create N results. Feather remains exact 3 same-rank stacks -> next rank, quantity N -> N; mixed element output follows selected input element roll.
- Grade ceiling is `floor(weighted average material grade) + 2`; output may fall as low as grade 1. This matches TS M documented behavior.
- Generic crafting quality uses exactly the three official crafting skills: 14005 煉丹術, 14011 鎔鑄術, 14019 鍛造術. 14032 精煉術 is a separate refining skill and is not included in generic compound quality.
- Exact original server-side probability table could not be recovered from Lua/client/native/PC/public/local legacy sources. Do not describe the fallback percentages as original DVT. Current offline fallback is isolated in `CompoundSystem.rollOutputGrade()`: best-of-rolls over valid grades <= ceiling, with one additional quality-roll step per 5 total generic crafting skill levels and probabilistic partial step. This preserves only evidenced invariants: higher skill improves high-grade chance, never raises ceiling, and low-grade failure remains possible.
- Removed old incorrect behavior `round(avg) +1 if any skill learned`.
- Targeted `DvtCompoundTest`: PASS.
- Full `gradlew test build --rerun-tasks`: PASS 21/21.
- `git diff --check`: PASS.

## 2026-10-02 — Teleport/Memory audit T1 — PASS (code)
User moved to the Dịch Chuyển UI, screenshot shows UITeleport tab Memory / Điểm lưu trữ. Continue this subsystem from here in later chats.

### DVT UI contract
- `EUITeleportTag`: 1 Skypoint/Chủ thành, 2 Memory/Điểm lưu trữ, 3 FightArea/Khu luyện công, 4 LimitFightArea, 5 Turn/Chuyển sinh, 6 Mining.
- Memory has 5 base slots + max 5 extension slots.
- `Lưu trữ` sends `C:023-073` payload `kind=2,index`.
- Memory data is received by `S:023-118`: extendCount + recordCount + repeated index/sceneId/x/y.
- `Dịch Chuyển` uses `UISell.Launch(7, ..., 1, index)`; slot expansion uses sellId=8.
- Skypoint/Chủ thành uses `C:068-001 sceneId(U16)+warpNo(U8)`.
- FightArea and Turn are client navigation through `sceneFightDatas`, not a direct teleport packet.
- LimitFightArea sends `C:097-001 dungeonId(U8)`; no mainKind97 handler exists yet.
- Mining sends `C:170-002 mineKind(U8)`; no mainKind170 handler exists yet.

### T1 bug found and fixed
- Lua `UITeleport.OnClick_Memory()` directly reads `flyPoints[index].sceneId` for an unlocked slot.
- `UITeleport.Clear()` empties `flyPoints`; only `S:023-118` creates entries.
- Previous server `sendFlyPointData()` sent only persisted/saved points. Therefore a brand-new unlocked empty slot could remain nil and clicking Lưu trữ could dereference nil client-side.
- Fix: `S:023-118` now always emits one record for every unlocked slot:
  - indexes 1..5 by default;
  - plus purchased extension slots up to 10;
  - empty slot => sceneId=0,x=0,y=0;
  - persisted point keeps real scene/x/y;
  - extension count clamped to Lua MaxFlyScrollExtend=5.
- No Lua modification.
- Regression added: `DvtTeleportMemoryTest`
  - five default records including empty slots;
  - real saved record preserved;
  - extension count clamped to 5 / total 10 slots;
  - out-of-range persisted row is not emitted.
- Targeted `DvtTeleportMemoryTest`: PASS.
- Full `gradlew test build --rerun-tasks`: PASS 21/21.
- `git diff --check`: PASS.
- Next: deploy T1 to production `.69`, runtime test Lưu trữ -> S23-118 refresh -> Dịch Chuyển; then audit/fix T2 Skypoint and missing T3 mainKind97 / T4 mainKind170.

## 2026-10-02 — UITeleport remaining tabs audit T2/T3/T4 — IN PROGRESS
User pointed out the 3 remaining UITeleport tabs visible below Memory: LimitFightArea / Chuyển sinh / Mỏ thôn thực. Do not forget these after T1 Memory.

### T2 — Giới hạn Khu luyện công / LimitFightArea
- Client contract from Lua:
  - click sends C:097-001 + dungeonId(U8).
  - S:097-001 result codes: 0 success; 1 level too low; 2 time exhausted; 3 battle; 4 event; 5 team; 6 already in scene.
- DVT data source: AssetBundles/Data/IOS_LimitTimeDungeon_C.dat.
- Exact file shape: count(Int32) + record[15 bytes]:
  index U8, msg U32, sceneId U16, limitIndex U16, limitTime U16, limitLv U16, missionId U16.
- Current DVT file has exactly 1 record:
  - index=1
  - msg=21770
  - sceneId=49942
  - limitIndex=27
  - limitTime=120
  - limitLv=0
  - missionId=13137
- SceneSet_C.dat contains scene 49942 (nameId=21770).
- Eve.emg contains scene 49942:
  - exit door 1 at (280,220)
  - sceneInfo 1 exits to scene12003 at (25,25), direction5
  - mines/events exist inside.
- No ordinary inbound Eve sceneInfo targets 49942; entry coordinate is special-server semantics and has not yet been proven. Do NOT guess coordinates.
- Current Kotlin server has no mainKind97 handler and does not load IOS_LimitTimeDungeon_C.dat yet.

### T3 — Chuyển sinh
- No direct teleport protocol. UITeleport uses the same IOS_SceneFight_C.dat navigation table and calls MarkManager.StartNavigation().
- Exact SceneFight record shape = 25 bytes:
  group U8, groupName U32, sceneId U16, x U16, y U16, npcLow U16, npcHigh U16, element U8, infoStr U32, mark U16, open U8, areaId U8, info U8.
- Current DVT IOS_SceneFight_C.dat: 381 rows.
- Turn groups are group >100 and contain 18 rows total:
  - group101: 9 rows
  - group102: 7 rows
  - group103: 2 rows
- Therefore this tab is primarily client-side navigation/data; server work should focus only on any destination door/Eve blockers found at runtime, not invent a separate teleport handler.

### T4 — Mỏ thôn thực / Mining
- UITeleport click sends C:170-002 + mineType(U8), currently hardcoded type=1.
- Full protocol family from Lua:
  - C170-001 mine info
  - C170-002 enter mine
  - C170-010 refill machine durability
  - C170-011 set mining state
  - corresponding S170 responses drive UIMine.
- UIMine.MaxMine = 600000000.
- Four fixed machine/fuel slots:
  - items 55431..55434
  - skins 22120..22123
  - durability cap 65535
  - client consumes durability over 360-second mining ticks.
- Critical scene evidence: UIMain.UpdateMining() only displays mining UI when SceneManager.sceneId == 49996.
- Eve.emg scene49996 exists:
  - NPC22111 placements x6
  - door1 at (2480,960), door2 at (900,60)
  - sceneInfo1 exits to scene12003 at (25,25), direction5
- No ordinary inbound Eve sceneInfo targets 49996; C170-002 must therefore implement special entry semantics. Entry coordinate is not yet proven; do NOT guess.
- Current Kotlin server has no mainKind170 handler.

### Cleanup
- Temporary TeleportScratchTest and scratch output files used only to inspect Eve.emg were deleted after audit.
- No Lua files modified.
- Next implementation order: T2 mainKind97 only after authoritative entry-coordinate evidence; T3 runtime navigation/door verification; T4 mainKind170 after authoritative entry-coordinate + persistence/timer semantics are recovered.

## 2026-10-02 — UITeleport T2/T3/T4 implementation — DEV PASS
Continuation from checkpoint 149ef88.

### T2 LimitFightArea / protocol 97
- Added `LimitTimeDungeonDatLoader` and `LimitTimeDungeonDef`; server now loads tracked DVT `File.dat/Data-ios/IOS_LimitTimeDungeon_C.dat`.
- Added opcode 97 + `LimitTimeDungeonHandler`.
- C:097-001 validates exact Lua result classes:
  1 level too low, 2 time exhausted, 3 battle, 4 event, 5 party, 6 already in scene, 0 success.
- Added `LimitTimeDungeonSystem`:
  - tracks RoleCount index 27 while in scene49942;
  - increments once/minute;
  - persists RoleCount and sends updates;
  - restores tracking on C:012-001 scene ready after relog/restart;
  - exits through Eve sceneInfo when 120-minute limit is reached;
  - stops job on disconnect.
- Entry to scene49942 has no authoritative inbound spawn in Lua/Eve/data. Current offline fallback is isolated in `SpecialSceneTeleportService`: when no NPC exists, first Eve door + 4 grids, snapped to 20px. For scene49942 this resolves to (360,300). This is NOT claimed as original DVT coordinate.

### T3 Chuyển sinh
- Added `SceneFightDatLoader` + `SceneFightDef`; server now loads tracked `IOS_SceneFight_C.dat`.
- Verified exact DVT table: 381 rows, 18 Turn rows in groups 101/102/103 = 9/7/2.
- No server teleport opcode was invented. UITeleport Turn remains client MarkManager navigation using the DVT table. Runtime issues, if any, must be fixed at destination Eve/door semantics.

### T4 Mỏ thôn thực / protocol 170
- Added opcode 170 + `MiningHandler` for C170-001/002/010/011.
- Added `CharacterMining` persistence table, `MiningRepository`, `MiningSystem` and Koin wiring.
- `DatabaseFactory` creates/migrates `character_mining`.
- DVT constants locked from Lua:
  - mine scene 49996;
  - MaxMine=600000000;
  - four fuel items 55431..55434;
  - machine skins 22120..22123;
  - durability max65535;
  - one durability point consumed per 360 seconds.
- Refill validates exact bag slot/item/quantity and removes items atomically through InventorySystem; S170-010 returns wire index + added durability.
- S170-001 packet shape: result/type/remainingOre/setTime/four machine pairs/dayLimit.
- S170-002 packet shape: result/type/remainingOre/people/isMining.
- Scene49996 has no authoritative inbound spawn. Offline fallback uses centroid of six real Eve NPC placements, snapped to 20px => (440,400). This is NOT claimed as original DVT coordinate.
- Eve sceneInfo is used for exit back to scene12003.
- Koin runtime wiring bug found during audit and fixed before deploy: MiningRepository, MiningSystem and MiningHandler are now all bound.

### Regression / validation
- `DvtTeleportRemainingTabsTest` now locks:
  - exact LimitTimeDungeon row;
  - exact 18 Turn rows/group counts;
  - deterministic Eve-derived fallback entries 49942=(360,300), 49996=(440,400);
  - mining durability settlement/persistence every 360 seconds.
- Targeted `DvtTeleportRemainingTabsTest`: PASS.
- Full `gradlew test build --rerun-tasks`: PASS 21/21.
- `git diff --check`: PASS.
- Production deploy/runtime validation pending immediately after this checkpoint.

### Production validation for commit 5aab227 — PASS
- Commit deployed to production `192.168.1.69` at `C:\Server-Game\tsm_dodo`.
- Production full `gradlew test build --rerun-tasks`: PASS 21/21.
- Runtime restart PASS; port 6613 LISTEN, PID 4548.
- Runtime log confirms:
  - `IOS_LimitTimeDungeon` loaded: 1 record.
  - `IOS_SceneFight` loaded: 381 records, turn=18.
  - Eve/Ground and all prior DVT data load normally.
  - Koin resolved LimitTimeDungeonHandler + MiningHandler (no NoBeanDefFound / InstanceCreation errors).
  - Server reached ready state on 0.0.0.0:6613.
  - no ERROR / Exception / FATAL in startup log.
- Production machine has no mysql.exe CLI, so direct SHOW TABLES check was unavailable; DatabaseFactory startup completed without schema error after adding CharacterMining.
- Runtime client verification still required for:
  1. LimitFightArea button -> scene49942 -> RoleCount27 timer/exit.
  2. Turn tab navigation to the 18 DVT destinations.
  3. Mining button -> scene49996 -> UIMine info/refill/state.
- Important: entry coordinates 49942=(360,300) and 49996=(440,400) remain explicitly offline Eve-geometry fallbacks, not recovered original DVT spawn coordinates. If client runtime shows blocked/invalid spawn, adjust only SpecialSceneTeleportService entry policy from real map evidence; do not alter Lua/data.

### 2026-10-03 — UITeleport T2/T3/T4 — USER RUNTIME PASS
- User runtime-tested and confirmed PASS for all 3 remaining UITeleport tabs after production deploy commit 5aab227:
  - T2 Giới hạn Khu luyện công / protocol 97.
  - T3 Chuyển sinh navigation / SceneFight groups 101-103.
  - T4 Mỏ thôn thực / protocol 170.
- Treat these three tabs as closed/PASS unless a future regression is reported.
- Production runtime used for validation: 192.168.1.69:6613.
- Keep the special-scene entry coordinates documented as offline Eve-geometry fallbacks, but no runtime issue was reported during this PASS.


## 2026-10-03 — UIFurnace / “Lò” backend — CODE PASS, runtime verification pending

### Root cause
- Icon “Lò” is `UIFurnace`.
- Client opens it by sending `C:089-001` with no payload.
- Server had no mainKind 89 handler, so the client waited for `S:089-001` and appeared to do nothing.

### Authoritative DVT contract
- Lua: `UI/UIFurnace.lua`, `Common/protocal.lua`.
- Data:
  - `IOS_FurnaceSlot_C.dat`: 2036 rows.
    - kind1 NPC = 430
    - kind2 Equip = 771
    - kind3 TurnItem = 835
  - `IOS_FurnaceSelect_C.dat`: 42 weighted shop layouts.
    - select kind counts = 1:6, 2:6, 3:8, 4:8, 5:6, 6:8
    - each kind has sample sum 10000
    - every layout contains exactly 8 store slots.
- S89-1:
  - result
  - baseRate(Double)
  - activeRate(Double)
  - bOpen(Byte)
  - category count
  - each category: kind, isCrit, item count, repeated itemId(U16)+qty(I32).
- Client converts `isCrit=1` into price multiplier 0.5, otherwise 1.0.
- Purchase flags confirmed:
  - Npc 1518..1525
  - Equip 1526..1533
  - GoldNpc 7067..7074
  - GoldEquip 7075..7082
  - TurnItem 7257..7264
  - GoldTurnItem 7265..7272.
- Buy currency/debris:
  - Npc/GoldNpc/TurnItem/GoldTurnItem use RoleCount.NpcDebris=110.
  - Equip/GoldEquip use RoleCount.EquipDebris=111.
  - Gold categories use `goldStoreRate=2`.
- Dismantle:
  - NPC -> NpcDebris=110
  - Equip -> EquipDebris=111
  - ExclusiveWeapon -> SWeaponDebris=654
  - Godskin/Myth -> item 55403.
- Item filter mirrors Lua: reject locked, styleLv>0, WeaponRecipe specialAbility234, RandomWeaponRecipe235, invalid furnaceKind.

### Implemented
- Added `Opcodes.FURNACE=89`.
- Added Furnace data models/loaders and GameDataManager wiring.
- Added `FurnaceSystem`, `FurnaceHandler`, `FurnaceResponseSender`.
- Implemented:
  - query/open shop
  - six categories x eight slots
  - deterministic per-character shop state derived from persisted UISell refresh count
  - purchase validation, debris deduction, bag award, purchased BitFlag
  - dismantle validation, bag deduction, debris/item reward
  - category refresh through UISell 24/25/33/34/59/60
  - refresh clears only that category’s 8 purchased flags.
- Shop composition follows DVT FurnaceSelect/FurnaceSlot tables.
- Exact original server RNG seed and crit probability are not present in Lua/data:
  - offline deterministic seed is used to prevent free reroll on reopen/relog
  - crit is held at false/1.0; do not invent probability.

### UISell data audit
- Working-copy `File.dat/Data/UISell_C.dat` and `UISell_Formula_C.dat` synchronized from DVT CDN 2.4.76.
- DVT/CDN refresh formulas:
  - 24: kind1 value100
  - 25: kind1 value5
  - 33: kind2 value80000
  - 34: kind2 value69
  - 59: kind14 value50 params[0]=10
  - 60: NO formula in every checked DVT/CDN copy (2.4.69, 2.4.76, 2.7, 9pet_resources).
- Therefore sellId60 remains evidence-blocked and returns NO_DATA; do not invent a price.

### Validation
- Targeted `DvtFurnaceTest`: PASS 6/6.
  - exact 42/2036 data invariants
  - stable 6x8 shop
  - buy/debris/BitFlag
  - dismantle/debris
  - byte-level S89-1/S89-2/S89-3
  - UISell refresh data + sell60 evidence-block.
- Full `gradlew test build --rerun-tasks`: PASS, 21/21 tasks executed.
- `git diff --check`: PASS (only existing CRLF normalization warnings).
- Runtime gameplay test on production `192.168.1.69:6613` still required after deploy.

### Git / deployment state for Furnace batch
- Furnace implementation commit: a5cbad34f5aad5ba6c48b0b9f975c0c03ec4423d (feat: implement DVT furnace backend).
- Pushed successfully to both origin/feature/9-pet and origin/main.
- Production 192.168.1.69 is online:
  - TCP 6613 = open (game runtime still running)
  - TCP 22 = open
  - TCP 5985 = open.
- Deployment is NOT complete yet:
  - Remote Desktop Commander currently exposes only dev machine DESKTOP-Q96IFHO / .99.
  - SSH qt@192.168.1.69 with current dev key fails authentication.
  - SMB admin share \\192.168.1.69\C$\Server-Game\tsm_dodo is not accessible with current credentials.
  - WinRM by IP is blocked by TrustedHosts/authentication requirements; do not change TrustedHosts or guess credentials automatically.
- Therefore production has not yet fast-forwarded/pulled a5cbad3 and has not been restarted for this Furnace batch.
- Do not ask the user to gameplay-test Furnace until .69 is actually updated and restarted.

### 2026-10-03 — UIFurnace production deploy — PASS
- Production SSH access restored and deployment completed.
- Production repo C:\Server-Game\tsm_dodo fast-forwarded from 5aab227 to db73656 without touching local application.conf or untracked production files.
- Production full gradlew test build --rerun-tasks: PASS, BUILD SUCCESSFUL, 21/21 tasks executed.
- Runtime restarted successfully.
- Production runtime state:
  - HEAD db73656
  - 192.168.1.69:6613 LISTEN
  - runtime PID 13048
  - IOS_FurnaceSlot loaded 2036 records
  - IOS_FurnaceSelect loaded 42 samples, kinds 1..6
  - UISell loaded 70 records
  - UISellFormula loaded 58 records
  - server reached ready state
  - no ERROR / Exception / FATAL / Koin creation error in startup log.
- Furnace is now ready for user runtime gameplay test.
- sellId60 refresh remains intentionally evidence-blocked because no DVT/CDN formula exists; all other Furnace flows are deployed.

### 2026-10-03 — UIFurnace / Lò — USER RUNTIME PASS
- User runtime-tested Furnace on production after deploy and confirmed PASS.
- Treat UIFurnace / protocol 89 as closed/PASS unless a future regression is reported.
- Production validation target: 192.168.1.69:6613, deployed code db73656.
- Implemented and now runtime-validated:
  - open/query shop (C/S 089-001)
  - six shop categories x eight slots
  - purchase (089-002), debris deduction, purchased BitFlags
  - dismantle (089-003), debris/item rewards
  - refresh flow through UISell for supported DVT formulas
  - stable per-character shop state across reopen/relog unless refreshed.
- Known evidence boundary remains unchanged: sellId60 has no formula in checked DVT/CDN data, so it stays intentionally blocked rather than guessed.

## 2026-10-03 — Monster stats / damage audit — CORE FIXED, passive evidence-blocked

### Scope
- User requested audit of monster stats and attack/damage correctness.
- Re-audited DVT Npc_C.dat -> NpcDef -> BattleFighter path and BattleDamageCalc D1-D6.
- Sources: Lua DVT/Data, tracked Mobile Npc_C/Skill_C, Kotlin server, PC source only as numeric fallback where Mobile server semantics are absent.

### Confirmed bug 1 — wild/event NPC HP/SP were overinflated
- Old BattleManager.createNpcFighter used:
  - HP = hpBase + level * hpx
  - SP = spBase + level * spx.
- PC fallback BattleNpcAI uses npcinfo.hpmax/spmax directly, no level-growth addition.
- Cross-data audit Mobile vs PC:
  - Mobile comparison file: 8407 NPC; PC: 5676; common IDs: 5393.
  - 5236 common NPCs matched exactly across Lv/HP/SP/HPX/SPX/INT/ATK/DEF/AGI/element.
  - Example ID10001 Lv120: DVT/PC HP=1299, old Kotlin=4899.
  - Example ID10004 Lv53: DVT/PC HP=335, old Kotlin=1342.
- Tracked production Npc_C.dat has 8427/8427 NPC with hpBase>0, so direct HP cannot create zero-HP NPC.
- BattleRoleSerializer uses UInt32 HP/SP, so large NPC HP is not truncated.
- Fix: wild/event NPC now uses hpBase/spBase directly. HPX/SPX remain growth fields for pet/player progression, not wild battle spawn.

### Confirmed bug 2 — enemy skills were effectively Lv1
- BattleSkillResolver resolves skillLv from attacker.skillLevels[skillId] ?: 1.
- Old wild NPC fighter populated skills but left skillLevels empty.
- PC BattleParticipant.getSkillLvl() returns SkillData.max_lvl for NPC type3.
- Fix: every wild/event NPC battle skill now receives SkillDef.maxLv (minimum 1).
- This fixes damage, grow-type fightArea/target count, and other skill-level-dependent behavior.

### Confirmed bug 3 — NPC reborn/turn was dropped
- Old wild NPC BattleFighter kept default turn=0.
- PC BattleNpcAI copies npcinfo.reborn.
- Cross-data turn matched Mobile vs PC for 5389/5393 common IDs.
- Fix: BattleFighter.turn = npcDef.turn.
- This restores the existing damage reborn multiplier (10% per positive attacker turn difference), already matched to PC D1/D2 logic.

### Confirmed bug 4 — fourth NPC skill was dropped
- Mobile NpcData.specialSkill occupies the old PC skill4 position.
- Cross-data specialSkill Mobile == PC skill4 for 5380/5393 common IDs; 223 common NPCs actually have specialSkill > 0.
- Existing NPC AI already supports skills[3] with its skill4 probability branch.
- Fix: wild/event NPC skill list now includes specialSkill as the fourth skill, de-duplicated.

### Core damage formula re-check
- BattleDamageCalc.calculateBaseDamage still matches PC BattleAbstract.CalcDmg:
  - normal: ATK*1.6, skillBase=level*2
  - physical skill: ATK*1.6 + INT*0.4
  - magic skill: INT*2
  - skill base/growth, cap50000 match PC source order.
- Existing D1-D6 regressions were rerun together with the monster regression and PASS.

### Passive-skill boundary
- Tracked Npc_C.dat currently has 478 NPC with passiveSkill (48 passive skill/level pairs).
- Lua FightRoleController maps many passive effects, e.g. passive INT/ATK/DEF/HPX/SPX/AGI, elemental damage, shields, counter, boss passives.
- However Skill_C for these passives commonly has numerical=0 and attribute=0; descriptions are qualitative, not a numeric server formula.
- Examples: 14067 says increase ATK, 14068 increase DEF, 14057 halve physical damage, 21045 increase crit/crit damage, but no exact amount/probability is encoded in Lua/Skill_C.
- Therefore passive numeric effects are NOT invented in this batch. They remain evidence-blocked server-side.

### Implementation
- Added gamelogic/battle/NpcBattleProfileCalc.kt to isolate authoritative wild-NPC battle snapshot semantics.
- BattleManager.createNpcFighter now uses this profile:
  - direct HP/SP
  - direct ATK/DEF/INT/AGI/element/level
  - turn/reborn
  - 3 normal skills + specialSkill
  - maxLv skillLevels.
- Added DvtNpcBattleStatsTest using tracked DVT Npc_C.dat + Skill_C.dat.

### Validation
- Targeted DvtNpcBattleStatsTest + BattleDamageD* regressions: PASS.
- Full gradlew test build --rerun-tasks: PASS, 21/21 tasks executed.
- git diff --check: PASS (only CRLF normalization warning).
- Runtime production validation pending after deploy.

### 2026-10-03 — Monster stats production deploy — PASS, user runtime test pending
- Monster-stat fix commit 4187f28 deployed to production C:\Server-Game\tsm_dodo.
- Production full gradlew test build --rerun-tasks: PASS, 21/21 tasks executed.
- Runtime restarted successfully.
- Production runtime state:
  - HEAD 4187f28
  - 192.168.1.69:6613 LISTEN
  - runtime PID 8696
  - server reached ready state
  - no ERROR / Exception / FATAL / Koin creation error in startup log.
- Runtime gameplay validation still needed by user:
  1. fight ordinary wild monsters and confirm HP is no longer inflated;
  2. observe enemy skills using proper max-level damage/fight-area;
  3. observe reborn/turn monsters for expected stronger damage;
  4. verify no regression in normal player/pet damage.
- PassiveSkill numeric effects remain evidence-blocked and were intentionally not fabricated.

## 2026-10-03 — Monster Passive/BOSS P1 audit — metadata READY, formulas evidence-blocked

### Runtime confirmation from user
- User gameplay-tested commit 4187f28 monster core fixes and confirmed PASS.
- Treat direct NPC HP/SP, max-level enemy skills, NPC turn/reborn and fourth specialSkill as runtime-validated.

### Passive inventory
- Tracked DVT Npc_C.dat currently has 478 NPC records with passiveSkill > 0.
- 21 distinct passive skill IDs are used by those NPCs.
- passiveSkill/passiveSkillLv are authoritative NpcData fields; Lua RoleController:GetSkillLv also returns npcDatas[npcId].passiveSkillLv for owned NPC roles.

### Exact/partial semantics found in DVT data
- 14057 `Giảm nửa vật lý`: description says physical damage is reduced by one half.
  - multiplier amount 0.5 is explicit, but the authoritative server ordering (pre-DEF vs post-DEF/final) is not present in Lua/data/PC source.
  - NOT activated yet; applying it at the wrong stage materially changes damage.
- 14061 `Thiên Lôi Thuẫn`: description says Tian-Lei skills hitting self cannot deal damage.
  - Skill_C contains several Tian-Lei-related skills, but DVT does not provide the authoritative skill-ID membership for this passive.
  - NOT activated yet.
- 14080 `Phượng Sồ Chuyển Sinh`: auto-revive is explicit, but restored HP amount is not numerically defined.
- 14058/14059/14052/14043/14085/14094/14095/40000/40001/45000 and others remain qualitative or probability/threshold dependent.
- Newer 14066..14076 passive stat/element skills map to INT/ATK/DEF/HPX/SPX/AGI/crit/element effects in FightRoleController, but Skill_C numerical=0/attribute=0 and no exact server magnitude is present.

### Auxiliary DVT table audit
- IOS_NpcSkillProb_C.dat is only 4 bytes (`02 00 00 00`) and contains no per-NPC record payload; it cannot supply the missing AI/passive probability formula.
- IOS_NpcFightValue_C.dat:
  - 53 records, fixed 23 bytes each.
  - most keys are template IDs 1..24; only 45361..45364 match real Npc_C IDs.
  - 45361..45364 are `Khảo thí 1..4` and are also referenced by IOS_Festival.dat.
  - therefore this is special-mode/event fight-value data, not a global wild-monster stat multiplier. Do NOT apply it to ordinary Npc_C battle fighters.
- IOS_SkillTrigger_C.dat:
  - 25 records, fixed 11 bytes each.
  - record IDs are 1..25 rather than passive/skill IDs; field schema/consumer is not exposed in Lua.
  - no passive behavior is inferred from it yet.

### P1 implementation
- NpcBattleProfile now preserves passiveSkillId + passiveSkillLevel from NpcDef.
- BattleFighter now carries passiveSkillId + passiveSkillLevel for wild/event NPC runtime.
- BattleManager.createNpcFighter wires the passive metadata into the battle fighter.
- This batch intentionally changes NO damage/trigger behavior; it only prevents passive metadata from being lost before later evidence-backed implementations.
- DvtNpcBattleStatsTest now locks passive metadata preservation against tracked Npc_C.dat.

### Validation
- Targeted DvtNpcBattleStatsTest + BattleDamageD* regression: PASS, 18 tasks executed.
- Full `gradlew test build --rerun-tasks`: PASS, 21/21 tasks executed.
- Next passive step: recover exact server ordering/trigger formulas for a small evidence-backed subset before activating any passive effect.

### 2026-10-03 — Monster Passive/BOSS P1 production deploy — PASS
- P1 implementation commit a2616f6 deployed to production C:\Server-Game\tsm_dodo.
- Production full `gradlew test build --rerun-tasks`: PASS, 21/21 tasks executed.
- Runtime restarted successfully.
- Production state:
  - HEAD a2616f6
  - 192.168.1.69:6613 LISTEN
  - runtime PID 8556
  - no ERROR / Exception / FATAL / Koin creation error found in startup log.
- P1 is metadata-only by design: no passive damage/probability effect was activated.
- Next P2 target: recover authoritative ordering/trigger formulas for exact passive subset (especially 14057/14061) before enabling effects.


## 2026-10-03 — Monster Passive/BOSS P2 — 14057 + Tian-Lei contract

### Evidence recovered after P1
- DVT `FightManager.lua` defines packet `Attack_Result=2` explicitly as `Thounder / 天雷`; it is not documented as a generic critical-hit marker.
- DVT `TaiChiLightning.lua` (skill 14054, Càn Khôn Thiên Lôi) and `SKILL14060.lua` (Thiên Lôi Toàn Thể) branch on `EAttackResult.Thounder` and move the lightning/hit presentation onto the caster. This is the concrete client-side Tian-Lei self-hit channel.
- Therefore the previous D4 mapping `critical -> EAttackResult.THUNDER` was incorrect. Crit keeps its DVT Crit/CritDamage numerical multiplier but serializes as normal `HIT`.
- 14061 `Thiên Lôi Thuẫn` description says a Tian-Lei skill that hits self cannot cause damage. The safe server contract is now locked as: an incoming hit already marked `THUNDER` against a 14061 holder has HP damage 0.
- No authoritative source found for the probability/trigger that makes 14054/14060 turn into a self-hit. P2 does NOT invent or emit THUNDER randomly; 14061 remains trigger-ready but does not fabricate the missing proc.
- 14057 `Giảm nửa vật lý` has an explicit DVT magnitude of one half. P2 applies it to the final incoming HP damage for physical attacks only, using the already-audited damage classification: hand attack (`skill=null`) or `SkillDef.atkKind == 1`. Magic/non-physical skills are unchanged.

### Implementation
- Added `NpcPassiveCombatRules` as the evidence-gated passive layer.
- 14057: final physical incoming damage is halved; positive damage remains at least 1.
- 14061: `THUNDER` incoming damage is forced to 0; no proc rate is invented.
- `BattleDamageCalc` no longer emits THUNDER for critical hits. Crit multiplier remains unchanged.
- `BattleSkillResolver` combo scaling now preserves zero-damage immunity instead of clamping 0 back to 1.
- `BattleFighter` passive comments updated: metadata is consumed only by evidence-backed rules.
- Added `NpcPassiveCombatRulesTest`; updated D4 regression so 100% crit must remain `HIT`.

### Still evidence-blocked
- Exact Tian-Lei self-hit probability/trigger generation for 14054/14060.
- 14058/14059 HP thresholds + probabilities/counter formula.
- 14052 absorption formula.
- 14066..14076 stat/element magnitudes.
- 14080 revive HP amount.
- 14085/14094/14095/21045/40000/40001/45000 numerical formulas.
- No percentages or thresholds are guessed for these.

### Validation
- Targeted: `NpcPassiveCombatRulesTest` + D4 + D6 + `DvtNpcBattleStatsTest`: PASS, 18 tasks executed.
- Full `gradlew test build --rerun-tasks`: PASS, 21/21 tasks executed before final combo guard; targeted suite re-run after combo guard: PASS, 18/18 tasks executed.
- Final full `gradlew test build --rerun-tasks` after combo zero-damage guard: PASS, 21/21 tasks executed.
- `git diff --check`: PASS (checkpoint file only has the existing CRLF normalization warning).
- Production deploy/runtime verification follows after the P2 commit is pushed.
- SSH note: production access is stable through Git Bash OpenSSH (`C:\Program Files\Git\bin\bash.exe`, `/usr/bin/ssh`) to `Administrator@192.168.1.69`; prefer this path over PowerShell/CMD SSH wrappers.

### 2026-10-03 — Monster Passive/BOSS P2 production deploy — PASS
- P2 implementation commit `f93740f` pushed to `origin/main`.
- Production `C:\Server-Game\tsm_dodo` fast-forwarded from `a2616f6` to `f93740f` via a verified Git bundle transferred over Git Bash SSH, because production GitHub HTTPS fetch cannot prompt for credentials.
- Production local `app/src/main/resources/application.conf` was preserved unchanged; local operational/bundle files were not cleaned or overwritten.
- Production full `gradlew test build --rerun-tasks`: PASS, 21/21 tasks executed.
- Runtime restarted with a detached Windows process and log `logs\restart-monster-passive-p2-f93740f.log`.
- Runtime state after restart:
  - commit: `f93740f`
  - server PID: `10104`
  - `[::]:6613` LISTEN
  - startup reached Netty ready state and accepted local health-check connections
  - no startup `ERROR`, `Exception`, `FATAL`, or Koin creation failure found.
- SSH operational note: Git Bash OpenSSH is stable for `Administrator@192.168.1.69`; prefer `C:\Program Files\Git\bin\bash.exe` + `/usr/bin/ssh` for future production work.
- Gameplay validation to do in client:
  1. monster with passive 14057: physical incoming damage should be halved; magic/non-physical unchanged;
  2. ordinary critical hits should still receive crit damage but serialize as normal HIT, not Tian-Lei THUNDER;
  3. 14061 remains trigger-ready only: no random Tian-Lei self-hit probability is fabricated until authoritative evidence is found.

## 2026-10-03 — Monster Passive/BOSS P2 gameplay validation — PASS
- User runtime-tested the P2 monster passive changes in client and confirmed PASS.
- P2 remains production baseline: 14057 half physical damage, 14061 THUNDER immunity contract, critical HIT/THUNDER correction, combo zero-damage preservation.

## 2026-10-03 — Monster Passive/BOSS P3A — IOS_ExclusiveSkill raw contract
### DVT passive inventory
- Direct parse of `File.dat/Data-ios/IOS_Npc_C.dat`: 478 NPC records have `passiveSkill > 0`, with 21 distinct IDs:
  `10022, 11020, 12020, 14038, 14039, 14043, 14045, 14051, 14052, 14057, 14058, 14059, 14061, 14080, 14085, 14094, 14095, 23031, 40000, 40001, 45000`.
- Therefore 14066..14076 are not current `Npc_C.passiveSkill` entries. They exist in ExclusiveWeapon/ExclusiveSkill progression and must not be treated as wild/event NPC passive rows without separate evidence.

### IOS_ExclusiveSkill_C.dat contract recovered
- Tracked DVT file: `File.dat/Data-ios/IOS_ExclusiveSkill_C.dat`.
- File size 15502 bytes; Int32 count=574; all 574 `(skillId, level)` keys are unique.
- Fixed record size is 27 bytes:
  - `skillId UInt16`
  - `level UInt8`
  - 6-byte raw header whose semantics are still unknown and are preserved byte-for-byte
  - 2 effect blocks x 9 bytes
- Each 9-byte effect block is safely decoded at byte level as:
  - `kind UInt8`
  - `arg1 UInt8`
  - `arg2 UInt8`
  - `parameter UInt16`
  - `value UInt32`
- P3A deliberately keeps these fields generic; it does not assign gameplay meaning where the consumer has not been proven.

### Evidence-bearing records
- 14080 level3 raw effect0: `kind=4 arg1=1 arg2=1 parameter=0 value=70`.
- 40001 level1 raw effect0: `kind=2 ... value=10`.
- 40000 and 45000 level1 have identical two raw effect blocks:
  - `kind=59 parameter=90 value=1400`
  - `kind=59 parameter=114 value=20`
- DVT `EAttribute` maps 90 to `ResistancePercent` and 114 to `DamageReduction`.
- Other kind59 records correlate with EAttribute directly (e.g. 103 Crit, 104 CritDamage, 113 IncreaseTotalDamage, 117 ReduceTotalDamage), so kind59 is clearly attribute-oriented.
- However Resistance values in this table are 800..1800 while UI runtime displays ResistancePercent directly. The missing effect-consumer scaling is not present in Lua, so P3A does NOT assume 1400 means 14%.
- 14080 values 50/70/100 and 40001 value 10 are preserved but NOT interpreted as revive percent or hit count yet; once-per-battle/counter semantics remain unproven.

### Implementation
- Added `ExclusiveSkillDef` / `ExclusiveSkillEffectDef` raw domain models.
- Added `ExclusiveSkillDatLoader`.
- Added `GameDataManager.exclusiveSkillDefs` and loads `IOS_ExclusiveSkill_C.dat` from DVT Data-ios.
- Added `DvtExclusiveSkillDataTest` locking 574 records and known raw payloads.
- No battle/combat consumer was added in P3A; gameplay remains identical to P2.

### Validation
- Targeted `DvtExclusiveSkillDataTest + NpcPassiveCombatRulesTest + DvtNpcBattleStatsTest`: PASS, 18/18 tasks executed.
- Full `gradlew test build --rerun-tasks`: PASS, 21/21 tasks executed.
- `git diff --check`: PASS (only existing CRLF normalization warning on GameDataManager).
- Still evidence-blocked for activation: 14080 revive unit/once-per-battle rule; 40001 exact hit-counter direction; 40000/45000 Resistance scaling and damage-reduction stacking/order; 14085/14094/14095 BOSS formulas.

### 2026-10-03 — Monster Passive/BOSS P3A production deploy — PASS
- Commit `62fc3c7 feat: load DVT exclusive skill effects` pushed to `origin/main`.
- Production `C:\Server-Game\tsm_dodo` fast-forwarded from `0049404` to `62fc3c7` using a verified Git bundle over Git Bash SSH.
- Production local `app/src/main/resources/application.conf` and operational/untracked files were preserved.
- Production full `gradlew test build --rerun-tasks`: PASS, 21/21 tasks executed.
- Runtime restarted detached with `logs\restart-monster-passive-p3a-62fc3c7.log`.
- Runtime loader evidence:
  - `IOS_ExclusiveSkill_C.dat` loaded successfully.
  - 574 records, 123 distinct skill IDs.
  - source: `File.dat\Data-ios\IOS_ExclusiveSkill_C.dat`.
- Runtime ready:
  - game PID `12984`
  - port `6613` LISTEN on IPv4 and IPv6
  - Netty reached ready state and local health-check connections succeeded.
- Startup scan found no real `ERROR`, exception, fatal, or Koin failure; only Gradle task names containing `ConfigurationErrors` matched the text scan.
- P3A is loader-only: no new passive effect is activated, so P2 gameplay behavior is unchanged.

## 2026-10-03 — Monster Passive/BOSS P3B — 14080 Phượng Sồ Chuyển Sinh
### Evidence lock
- DVT Skill_C 14080 description: chết sẽ tự động hồi sinh và hồi một lượng HP.
- IOS_ExclusiveSkill_C.dat effect kind=4 contains exact per-level values:
  - lv1-2 = 50
  - lv3-4 = 70
  - lv5-6 = 100
- The same effect kind=4 appears on 42003 Thuần Dương Liên Tâm, whose DVT description also explicitly says revive.
- DVT client FightManager.RevChangeAttrib handles S:051-001 as:
  - isRevive byte
  - repeated col,row,attrKind,value
  - if HP rises from <=0 to >0, clear dead state; isRevive=1 triggers revive visual.
- A 2019-12-18 maintenance announcement repost records the exact 14080 rule:
  one automatic revive per battle, restoring 50%-100% HP depending on exclusive-weapon level.
  Source: https://forum.gamer.com.tw/Co.php?bPage=0&bsn=34184&sn=9098&subbsn=8
- IOS_SkillTrigger_C.dat was fully checked (25 records x 11 bytes) and contains none of
  14080/40001/40000/45000/42003, so it is not evidence for these passive counters.

### Implementation
- Added BattleFighter.passiveReviveUsed as per-battle one-shot state.
- NpcPassiveCombatRules now consumes DVT ExclusiveSkill 14080 values instead of hard-coding a guessed percentage.
- On lethal non-fly-out damage, 14080 revives immediately in authoritative server state;
  the lethal multi-hit chain stops at that hit.
- Fly-out remains non-revivable, preserving existing battlefield semantics.
- TargetResult carries passiveReviveHp so packet emission stays separate from damage calculation.
- BattleManager splits combo packet groups at a passive revive boundary.
- After lethal S:050-001, BattleManager sends S:051-001 isRevive=1 with absolute restored HP.
- Added ResponseSender.sendBattleRevive and byte-stable BattleResponseSender implementation.
- BattleManager now injects GameDataManager.exclusiveSkillDefs into BattleSkillResolver.

### Validation
- Targeted P3B suite (DvtPassiveReviveTest + DvtExclusiveSkillDataTest + NpcPassiveCombatRulesTest):
  PASS, 18/18 tasks executed.
- DvtPassiveReviveTest covers:
  - configured 70% restore
  - only once per battle
  - fly-out suppression
  - lethal resolver integration at 50%
  - exact S:051-001 wire bytes.
- Full local `gradlew test build --rerun-tasks`: PASS, 21/21 tasks executed.
- `git diff --check`: PASS except normal CRLF normalization warnings.

### Still evidence-blocked
- 40001 Ngự Ảnh Linh Thuẫn: description says damage only after a certain number of times,
  but no authoritative source was found for exact counter direction/threshold semantics.
- 40000/45000: kind59 exposes ResistancePercent=1400 and DamageReduction=20,
  but Resistance scaling and D5b damage-modifier order remain unresolved.
- 14085/14094/14095 remain formula/trigger blocked.

### 2026-10-03 — Monster Passive/BOSS P3B production deploy — PASS
- Commit `4f4910a feat: implement DVT phoenix rebirth passive` pushed to `origin/main`.
- Production `C:\Server-Game\tsm_dodo` fast-forwarded from `3a56597` to `4f4910a` via verified Git bundle.
- Production local `application.conf` and operational untracked files were preserved.
- Production full `gradlew test build --rerun-tasks`: PASS, 21/21 tasks executed.
- Runtime restarted detached with `logs\restart-monster-passive-p3b-4f4910a.log`.
- Runtime validation:
  - PID `10080`
  - port `6613` LISTEN on IPv4/IPv6
  - `IOS_ExclusiveSkill_C.dat`: 574 records / 123 skill IDs loaded
  - Netty reached ready state
  - no real ERROR/Exception/FATAL/Koin startup failure.
- Direct `IOS_Npc_C.dat` lookup finds one current NPC row using passive 14080:
  - npcId `45368`, name `Khảo thí 8`
  - passiveSkillLv `3`
  - therefore DVT P3B expected runtime restore is 70% HP, once per battle, unless fly-out.

### 2026-10-03 — Production runtime launch hardening
- The first P3B detached launch via PowerShell Start-Process was still attached to the Windows OpenSSH job: it reached ready state but terminated silently after the SSH session ended.
- This was an operational launch issue, not an application crash; the log contained no shutdown exception/fatal stack.
- Production runtime is now launched through WMI Win32_Process.Create using `_start_prod_detached.cmd`, which runs outside the SSH job.
- Verified by closing SSH completely, reconnecting, and checking again:
  - production HEAD `7455ac7`
  - `java.exe` PID `5032` in Services session 0
  - port `6613` still LISTEN on IPv4/IPv6
  - stable runtime log: `logs\runtime-production.log`.

## 2026-10-03 — Monster Passive/BOSS P3C audit — 14085 / 14094 / 14095
- DVT Skill_C:
  - 14085 `BOSS Linh Quang Kính`: "Linh kính dành riêng cho BOSS".
  - 14094 `BOSS Miễn Dịch Thuẫn`: "BOSS Sử dụng lá chắn và hồi ma".
  - 14095 `BOSS Tự Kỷ Suy Nhược`: "Boss tự suy yếu + hồi ma pháp".
- IOS_ExclusiveSkill_C raw level-1 payloads:
  - 14085: effect0 `kind=5 arg1=1 arg2=200 parameter=4 value=110`.
  - 14094: effect0 `kind=5 arg1=1 arg2=250 parameter=0 value=117`; effect1 `kind=63 arg1=0 arg2=250 parameter=0 value=25`.
  - 14095: effect0 `kind=184 arg1=0 arg2=250 parameter=5 value=0`; effect1 identical kind63 block with value=25.
- Current IOS_Npc_C usage:
  - 14085: 109 NPC rows, all passiveSkillLv=1.
  - 14094: 35 NPC rows, all passiveSkillLv=1.
  - 14095: 24 NPC rows, all passiveSkillLv=1.
- Cross-record evidence:
  - kind5 also appears on 14081/14083/22023 and other exclusive-skill effects with different semantics.
  - kind184 appears on many debuff/status enhancement records.
  - kind63 appears only on 14094/14095 in the tracked table, so its `value=25` unit cannot be proven by cross-record comparison.
- No direct Lua/native consumer was found for these boss IDs, and web/source searches did not produce an authoritative TS rule that resolves the exact meaning/scaling/order of these fields.
- Decision: P3C is evidence-blocked; no combat behavior was activated for 14085/14094/14095. Continue searching or move to another passive with an explicit quantitative contract.

## 2026-10-03 — DVT runtime provenance lock from LDPlayer
- Authoritative emulator install is LDPlayer9 at `F:\LDPlayer\LDPlayer9`.
- Confirmed foreground DVT package: `net.chinesegamer.tsn`, versionName `1.15`, versionCode `26`.
- `com.vtcmobile.gz06` is VTC 1.3 and must NOT be used as DVT authority.
- Pulled the exact DVT base APK from LDPlayer:
  - APK SHA256 `93912a278ba270582ecf8d8030d50690c2fa2913c4b01f751cdd76a8d985a22b`
  - global-metadata SHA256 `960418f46cd4749bccf4509168795148e8b798d6eb42b0b568c72e3028c003e9`
  - arm64 libil2cpp SHA256 `125bc0d6ec5f49c2cfc3dcbe75c90958a2c21ba9d235af7dd13651fa0e52dce8`
- Created a separate DVT 1.15 native dump under `_native_dump_dvt115`.
- Old `_native_dump_vtc13` is VTC-only reference and must never decide DVT gameplay semantics.
- Runtime Lua files were pulled directly from:
  `/sdcard/Android/data/net.chinesegamer.tsn/files/Lua`.
- Runtime Lua is AES-128-CBC encrypted. After decrypting with the existing project key/IV and NFC normalization,
  all 5 audited files match repo `lua/` exactly in logic and line structure:
  `FightManager.lua`, `FightRoleController.lua`, `ExclusiveWeapon.lua`, `SkillData.lua`, `Logic/Fight/Skill.lua`.

### DVT runtime data cross-check
- Pulled runtime `IOS_ExclusiveSkill_C.dat`, `IOS_Npc_C.dat`, and `IOS_Skill_C.dat` directly from the DVT package storage.
- `IOS_ExclusiveSkill_C.dat` is byte-identical to repo Data-ios:
  SHA256 `d5ed08ef5bf07e886f40b52a687338ccc22c65a1bd2f7ddd3b49bb4be683b3da`.
- Runtime `Npc_C` and `Skill_C` have different raw hashes/sizes because localized strings differ,
  but numeric semantics are identical:
  - 8427/8427 NPC records, semantic diffs = 0.
  - 548/548 skill records, semantic diffs = 0.
- Therefore existing P1/P2/P3 numeric passive audits are validated against the live DVT 1.15 client data.

## 2026-10-03 — Monster Passive/BOSS P3D audit
- 40001 `Ngự Ảnh Linh Thuẫn` remains evidence-blocked:
  - effect kind=2 only appears on 14078 and 40001.
  - 14078 encodes its 1/2/3 counter in `arg2` with value=0.
  - 40001 encodes `arg2=0`, `value=10`.
  - No third record or authoritative consumer proves how the 40001 mode interprets `value=10`, so no 10-hit rule is invented.
- 14045 `Tâm Nhãn` remains evidence-blocked:
  - DVT packet contract proves `EAttackResult.HeartEye=3`.
  - client displays `EFightBeh.MagicalEye` when this result arrives.
  - neither DVT Lua nor PC fallback exposes the server trigger/probability that should emit HeartEye.
- Decision: no P3D gameplay patch for 40001/14045. Move to the next monster-combat subsystem rather than guess.

## 2026-10-03 — Monster AI-A — corpse persistence + NPC revive targeting
### Evidence
- Live DVT 1.15 runtime `IOS_NpcSkillProb_C.dat` pulled from LDPlayer is exactly 4 bytes: `02 00 00 00`; byte-identical to repo. It contains no per-NPC probability payload.
- Therefore NPC skill selection probability remains PC fallback only.
- PC reference `TSBattleNPC.seekTarget` uses one `Random.Next(0,100)` roll, then priority modulo gates `%5 -> %4 -> %3 -> %2`; Kotlin already matches this structure and range.
- With all four NPC skills present, the actual unconditional distribution over 0..99 is skill4=20%, skill3=20%, skill2=20%, skill1=14%, basic=26%; the old comment's 20/25/33/50 wording described conditional gates, not independent rolls.
- DVT `FightField.UpdateSelectFrame` explicitly exposes dead friendly targets for `fightWay=8`.
- DVT `FightManager.RevChangeAttrib` revives a role by coordinates after HP rises from <=0 to >0, which requires a normal corpse to remain addressable on its battlefield cell.
- PC `TSBattleNPC.seekTarget` likewise scans dead friendly positions for skill type/fightWay 8.

### Implementation
- `BattleGrid` now exposes dead friendlies separately for each side; fly-out units are excluded.
- `BattleInstance.resolveRound` no longer removes ordinary dead fighters from the grid at round end.
- Fly-out and successful escape still remove the fighter from the grid.
- Successful capture now removes the captured NPC immediately so it cannot be revived as a corpse.
- NPC `fightWay=8` AI now prefers a dead friendly target when one exists; when none exists it preserves the historical no-op behavior instead of inventing a different attack.
- `resolveNpcActions` accepts an optional skill selector for deterministic regression tests; production default remains the existing PC-fallback selector.
- No changes were made yet to ambiguous AI targeting for fightWay 4/5/18/19.

### Validation
- Targeted battle command/status/geometry/passive-revive suite: PASS, 18/18 tasks executed.
- Added regressions proving:
  - ordinary corpse remains on its grid cell across a round boundary;
  - fly-out corpse leaves the grid and cannot be revived;
  - NPC revive AI targets a dead friendly deterministically.
- Full local `gradlew test build --rerun-tasks`: PASS, 21/21 tasks executed.
- `git diff --check`: PASS; only normal line-ending warnings where applicable.

### 2026-10-03 — Monster AI-A production deploy — PASS
- Production Git is at `0ff3289 fix: preserve DVT revive targets for NPC AI`.
- Production full `gradlew test build --rerun-tasks`: PASS, 21/21 tasks executed.
- Runtime launcher was revalidated with WMI `Win32_Process.Create`, not PowerShell Start-Process.
- After closing the original SSH session and reconnecting:
  - runtime Java PID `12728`
  - port `0.0.0.0:6613` and `[::]:6613` LISTEN
  - local health-check connections continued every few seconds
  - production HEAD remained `0ff3289`.
- This confirms the WMI-launched runtime is independent of the SSH job.

## 2026-10-03 — Monster AI-B — NPC target pools by fightWay
### Evidence
- Live DVT Lua `FightField.UpdateSelectFrame` groups fightWay 4/5/6/7/14/18/19 on the caster-side selection frame;
  fightWay 8 explicitly selects dead friendlies.
- Exact PC fallback `bboycs/tsonline-server-tl/TS_Server/Server/TSBattleNPC.cs::seekTarget`:
  - 4/6/7/14/19 => NPC side (rows 0..1)
  - 5/18 => any living battlefield position (all four rows)
  - 8 => dead NPC-side target
  - otherwise => opposing side.
- DVT Skill_C descriptions support the classification:
  - fightWay 4 = shields/stealth/protection
  - fightWay 19 = buffs/protection centered on own side
  - fightWay 5 = status dispels
  - fightWay 18 includes wide/mixed dispel effects such as 22045.

### Implementation
- Added `NpcAiTargetRules` so NPC target-pool semantics are isolated and testable.
- NPC fightWay 4/6/7/14/19 now choose living fighters on their own side.
- NPC fightWay 5/18 use all living fighters on the battlefield, matching PC server AI fallback.
- fightWay 8 keeps AI-A semantics: dead friendly first; living friendly fallback preserves no-op behavior when no corpse exists.
- Other fightWay values continue targeting the opposing living side.
- Player/manual command legality is intentionally unchanged; AI-B only changes server-side NPC target selection.

### Validation
- Added `NpcAiTargetRulesTest`:
  - 4/19 candidate pool is NPC side only
  - 5/18 candidate pool includes both sides and excludes dead fighters
  - ordinary hostile target pool remains opponent side only.
- Targeted AI-B + battle command/status/revive suite: PASS, 18/18 tasks executed.
- Full local `gradlew test build --rerun-tasks`: PASS, 21/21 tasks executed.
- `git diff --check`: PASS.
- Next Monster AI target: audit PC special summon remap
  `10016/11016/12016/13015 -> +3` against DVT summon semantics before enabling it.

### 2026-10-03 — Monster AI-B production deploy — PASS
- Commit `18d4bc6 fix: align DVT NPC AI target pools` pushed to `origin/main`.
- Production fast-forwarded from `0ff3289` to `18d4bc6`; local config/operational files preserved.
- Production full `gradlew test build --rerun-tasks`: PASS, 21/21 tasks executed.
- Runtime restarted through WMI and verified after closing/reconnecting SSH:
  - production HEAD `18d4bc6`
  - Java PID `8940`
  - port 6613 LISTEN on IPv4/IPv6
  - health-check connections continued after reconnect.
- AI-B is therefore live on production.

## 2026-10-03 — Monster AI-C — elemental NPC summon normalization
### Evidence
- Live DVT Npc_C uses base summon IDs heavily:
  - 10016 on 87 NPC rows
  - 11016 on 43 NPC rows
  - 12016 on 61 NPC rows
  - 13015 on 113 NPC rows.
- Their +3 variants 10019/11019/12019/13018 occur on 0 Npc_C rows.
- DVT Skill.lua/Attack.lua explicitly distinguishes each pair as small summon vs large summon:
  Rocky3/Rocky1, WaterGod3/WaterGod1, Phoenix3/Phoenix1, Dragon3/Dragon1.

- DVT Skill_C confirms the +3 variants are maxLv1 large forms with fightArea=8.
- Exact PC fallback `bboycs/tsonline-server-tl/TS_Server/Server/TSBattleNPC.cs::seekTarget`
  remaps only the four standard elemental base summons by `skill += 3` before queuing NPC commands.
- No PC/DVT evidence extends this remap to light/dark summons 22011/23011, so they are left unchanged.
- This also fixes a concrete targeting error: raw 11016 has fightWay=18, while 11019 is the large hostile summon.

### Implementation
- Added `NpcAiSkillRules.normalizeSelectedSkill`.
- NPC AI remaps 10016->10019, 11016->11019, 12016->12019, 13015->13018.
- Remap happens immediately after NPC skill selection, before SkillDef lookup, SP validation and target selection.
- Player/manual skill selection and light/dark summon IDs are unchanged.

### Validation
- Added `NpcAiSkillRulesTest` for all four remaps and non-remapped controls.
- Added integration regression proving an NPC selecting 11016 queues 11019 and targets the enemy side.
- Targeted AI-C + AI-B + battle semantics suite: PASS, 18/18 tasks executed.
- Full local `gradlew test build --rerun-tasks`: PASS, 21/21 tasks executed.
- `git diff --check`: PASS.

### 2026-10-03 — Monster AI-C production deploy — PASS
- Commit `6e21e3b fix: normalize DVT NPC elemental summons` pushed to `origin/main`.
- Production fast-forwarded from `18d4bc6` to `6e21e3b`; local config/operational files preserved.
- Production full `gradlew test build --rerun-tasks`: PASS, 21/21 tasks executed.
- Runtime restarted through WMI and verified after closing/reconnecting SSH:
  - production HEAD `6e21e3b`
  - Java PID `10832`
  - port `0.0.0.0:6613` and `[::]:6613` LISTEN
  - local health-check connections continued after reconnect.
- AI-C is live on production.
- Next Monster AI audit: verify how Kotlin constructs NPC skill slots versus DVT Npc_C/PC skill1..skill4,
  especially `specialSkill` / `upgradeSkill` ordering and whether the current one-roll selector sees the intended fourth skill.

## 2026-10-03 — Monster AI-D — preserve NPC skill-slot probabilities
### Evidence
- DVT `Role.maxNpcSkill = 3`; NpcData.New reads skill1..skill3 plus field [35] `specialSkill`.
- Exact PC NPC AI treats these as four probability slots: skill4 -> skill3 -> skill2 -> skill1,
  using one roll with modulo gates %5 -> %4 -> %3 -> %2.
- Previous `NpcBattleProfileCalc` filtered zero IDs and applied `distinct()`, which compressed slot positions.
- Live DVT Npc_C audit found this changes real data:
  - 3 NPC rows contain a zero gap before a later nonzero slot.
  - 32 NPC rows contain duplicate skill IDs across slots.
- Examples locked in regression:
  - NPC 45367: [14080, 0, 0, 21001]
  - NPC 35061: [20052, 20052, 20052, 0].

### Implementation
- `NpcBattleProfileCalc` now preserves exactly four AI slots:
  [skill1, skill2, skill3, specialSkill], including zeros and duplicates.
- `skillLevels` still stores only distinct nonzero IDs at SkillData.maxLv.
- Moved exact one-roll selector into `NpcAiSkillRules.selectByRoll` for deterministic regression.
- BattleInstance delegates random roll to that pure selector; probability semantics are unchanged.

### Validation
- `DvtNpcBattleStatsTest` now asserts four-slot preservation and the two live DVT gap/duplicate records.
- `NpcAiSkillRulesTest` now covers:
  - elemental summon remaps
  - non-remapped light/dark/ordinary IDs
  - zero-gap roll priority
  - duplicate-slot independent modulo gates.
- Targeted AI-D + AI-C + AI-B + battle semantics suite: PASS, 18/18 tasks executed.
- Full local `gradlew test build --rerun-tasks`: PASS, 21/21 tasks executed.
- `git diff --check`: PASS.

### 2026-10-03 — Monster AI-D production deploy — PASS
- Commit `555a30e fix: preserve DVT NPC AI skill slots` is on `origin/main` and production `C:\Server-Game\tsm_dodo`.
- Production full `gradlew test build --rerun-tasks`: PASS, 21/21 tasks executed.
- Runtime restarted through the proven WMI/Win32_Process.Create path using `_start_prod_detached.cmd`.
- Verified only after closing the SSH session and reconnecting:
  - production HEAD `555a30e`
  - game Java PID `6900`
  - process runs in Windows Services session 0
  - `0.0.0.0:6613` and `[::]:6613` LISTEN
  - runtime log loaded `IOS_ExclusiveSkill_C.dat` 574 records / 123 skill IDs
  - no real startup ERROR/Exception/FATAL/Koin failure.
- Production local `app/src/main/resources/application.conf` and operational/untracked files were preserved.
- Deployment bundle/temp compiler session artifacts are not part of source and must not be committed.

### START NEXT CHAT FROM HERE — Monster AI-E
- Source of truth remains: live DVT 1.15 / Lua DVT > client/data/Eve.emg > Kotlin > PC fallback.
- DVT live package is `net.chinesegamer.tsn` in `F:\LDPlayer\LDPlayer9`; do NOT use `com.vtcmobile.gz06` or `_native_dump_vtc13` as DVT authority.
- Production baseline is commit `555a30e`, runtime PID `6900`, port `6613`.
- AI-A PASS: preserve normal corpses for revive targets; fly-out/capture leave grid.
- AI-B PASS: DVT/PC target pools for fightWay 4/5/8/18/19.
- AI-C PASS: elemental NPC summon remap 10016->10019, 11016->11019, 12016->12019, 13015->13018.
- AI-D PASS: preserve exactly four NPC AI skill slots [skill1, skill2, skill3, specialSkill], including zero gaps and duplicates; selector keeps the PC one-roll modulo priority.
- Next audit AI-E: verify NPC skill level, SP cost/consumption, invalid-skill fallback, and whether the selected slot should retry/fallback when the chosen skill cannot be cast.
- Keep `tools/start-staging-runtime.ps1` untracked; never commit it.
- Prefer Git Bash OpenSSH for production. Use Git bundle + SCP when production GitHub HTTPS fetch cannot authenticate.
- For production restarts, use WMI `Win32_Process.Create` with `_start_prod_detached.cmd`, then close SSH and reconnect to prove the server remains alive.

## 2026-10-03 — Monster AI-E — NPC SP execution semantics
### Evidence
- Live DVT Lua:
  - `FightRoleController.lua` checks insufficient SP only while Players / FollowNpc select their own command.
  - Enemy NPC commands are server-driven and bypass that input gate.
  - `FightField.AttackSkill` always subtracts `skillDatas[skillId].requireSp` after the server-selected skill executes.
  - `FightRoleController:SetAttrEffect` clamps HP/SP to a minimum of 0.
- PC fallback `TSBattleNPC.seekTarget` selects exactly one skill slot and does not check SP before `pushCommand`.
- PC execution subtracts `SkillData.sp_cost`; `BattleParticipant.setSp` clamps NPC SP at 0.
- Live DVT data proves this is not theoretical:
  - 21 NPC skill slots have starting/max SP below the selected skill's SP cost.
  - 592 skill slots can deplete NPC SP within <=3 casts.
  - Example NPC 40456: max SP=50, skill2=13025 costs 52 SP; skill3=13015 normalizes to 13018 costing 120 SP.

### Implementation
- Removed the Kotlin NPC-AI fallback that changed an already-selected skill into basic attack when current SP was too low.
- `BattleSkillResolver.resolveSkill` now has an explicit `allowInsufficientSp` flag.
- Only server-controlled enemy NPCs (`EHuman.MINE_NPC`) enable that flag.
- For such NPCs, a selected skill executes even when current SP < requireSp and consumes only the SP actually available, clamping to 0.
- Player-controlled units retain the strict insufficient-SP rejection.
- Invalid targets still consume 0 SP.

### Validation
- Added regression proving player-controlled skill remains blocked below cost.
- Added regression proving server NPC executes selected skill below cost and SP clamps to 0.
- Added AI command regression proving low-SP NPC keeps the selected skill rather than switching to hand attack.
- Added live-data regression for NPC 40456 low-SP skill slots.
- Targeted AI-E/resource/command/NPC-profile suite: PASS, 18/18 tasks executed.
- Full local `gradlew test build --rerun-tasks`: PASS, 21/21 tasks executed.
- `git diff --check`: PASS.

### 2026-10-03 — Monster AI-E production deploy — PASS
- Commit `04442de fix: align DVT NPC SP execution` pushed to `origin/main`.
- Production `C:\Server-Game\tsm_dodo` fast-forwarded from `555a30e` to `04442de` via verified Git bundle over Git Bash SSH.
- Production full `gradlew test build --rerun-tasks`: PASS, 21/21 tasks executed.
- Runtime restarted through WMI `Win32_Process.Create` with `_start_prod_detached.cmd`.
- Verified only after closing SSH and reconnecting:
  - production HEAD `04442de`
  - game Java PID `11868`
  - process runs in Windows Services session 0
  - `0.0.0.0:6613` and `[::]:6613` LISTEN
  - Netty logged server ready and recurring local health-check connect/disconnect succeeded
  - startup loaded `IOS_ExclusiveSkill_C.dat` 574 records / 123 skill IDs
  - no real ERROR / Exception / FATAL / Koin startup failure.
- Production local `application.conf` and operational/untracked files were preserved.

### LATEST START NEXT CHAT FROM HERE
- Production gameplay baseline: `04442de`, runtime PID `11868`, port `6613`.
- Live DVT source is locked to LDPlayer9 package `net.chinesegamer.tsn` v1.15.
- Monster AI completed and live:
  - AI-A corpse/revive target preservation
  - AI-B target pools for fightWay 4/5/8/18/19
  - AI-C elemental summon normalization
  - AI-D exact four-slot NPC skill probability preservation
  - AI-E NPC SP execution semantics aligned to DVT/PC.
- AI-E key rule: enemy NPCs do not fallback to hand attack just because SP < skill cost; the selected skill executes and SP clamps to 0. Player/FollowNpc command input remains strictly SP-gated.
- Continue the next monster-combat audit from this baseline; read the entire checkpoint first.

### 2026-10-03 — Monster AI-E production deploy — PASS
- Commit `04442de fix: align DVT NPC SP execution` is on `origin/main` and production `C:\Server-Game\tsm_dodo`.
- Production full `gradlew test build --rerun-tasks`: PASS, 21/21 tasks executed on exact HEAD `04442de`.
- Runtime is live from the WMI/Win32_Process.Create launch path and survives SSH reconnect:
  - production HEAD `04442de`
  - game Java PID `11868`
  - `0.0.0.0:6613` and `[::]:6613` LISTEN
  - runtime log reached Netty ready state
  - `IOS_ExclusiveSkill_C.dat` loaded 574 records / 123 skill IDs
  - no real startup ERROR/Exception/FATAL/Koin failure observed.
- Production local `app/src/main/resources/application.conf` and operational/untracked files remain preserved.

### START NEXT CHAT FROM HERE — Monster AI-F
- Source of truth: live DVT 1.15 (`net.chinesegamer.tsn` from LDPlayer) / Lua DVT > client data/Eve.emg > Kotlin > PC fallback.
- Production baseline: `04442de`, Java PID `11868`, port `6613`.
- AI-A PASS: ordinary corpses persist for revive targeting; fly-out/captured targets leave grid.
- AI-B PASS: NPC target pools for fightWay 4/5/8/18/19 aligned with DVT/PC evidence.
- AI-C PASS: elemental summon remap 10016->10019, 11016->11019, 12016->12019, 13015->13018.
- AI-D PASS: preserve exact four NPC AI skill slots [skill1, skill2, skill3, specialSkill], including zero gaps and duplicate IDs.
- AI-E PASS: enemy NPC keeps the selected skill even when current SP is below cost, executes it, and SP clamps to 0; player-controlled units still reject insufficient SP.
- Next audit AI-F:
  1. invalid/missing selected skill fallback semantics;
  2. what happens when selected skill has no legal target;
  3. whether server NPC retries another slot, falls back to hand attack, or performs no-op;
  4. verify skill-level source for NPC slots against DVT/PC before changing it.
- Keep `tools/start-staging-runtime.ps1` untracked and never commit it.
- Use Git Bash OpenSSH for production. If GitHub HTTPS fetch cannot authenticate, deploy via Git bundle + SCP.
- Restart production only via WMI `Win32_Process.Create` + `_start_prod_detached.cmd`, then close/reconnect SSH to prove lifecycle independence.

## 2026-10-03 — Monster AI-F — invalid skill / no-target / NPC skill-level semantics
### Evidence
- Live DVT RoleController:GetElementSkill returns ordinary battlefield NPC skills with Lv = 1.
- Live DVT RoleController:GetSkillLv also returns 1 for enemy NPC base skills; PC fallback uses SkillData.max_lvl, so DVT wins.
- Live DVT Attack.GetAttackNewFunction maps an unknown/unlisted skill ID to HandFight.
- Current DVT Npc_C has 17 non-Skill_C values in AI slots: 1306, 12000, 45005, 31001, 31002, 31003, 31004, 31005, 31007, 31008, 31009, 31010, 31011, 31015, 31021, 31022, 31024.
- 31001/31005 are also used by DVT ArmyHorse/WarElephant as NPC/resource IDs, so they are not ordinary Skill_C definitions.
- PC TSBattleNPC chooses exactly one slot per roll and does not retry another slot.
- PC fightWay=8 with no corpse still pushes the selected command; it does not reroll.
- DVT FightManager.RevAttackSkill calls FightField.AttackSkill only when S:050-001 target count is greater than zero.

### Implementation / validation
- NpcBattleProfileCalc now uses live-DVT level 1 for enemy NPC normal/effective skills instead of PC maxLv.
- Unknown/non-Skill_C selected IDs remain unchanged in the packet and use HandFight semantics; no retry/alternate-slot fallback is added.
- Revive with no corpse remains a zero-target no-op and consumes no SP.
- Added regressions for DVT Lv1, exact 17-ID non-Skill_C inventory, unknown-ID HandFight fallback, and no-corpse revive/no-retry.
- Targeted AI-F suite PASS: 31 tests.
- Full local gradlew test build --rerun-tasks PASS: 21/21 tasks; git diff --check PASS.

### Production deploy — PASS
- Commit 8da432f fix: align DVT NPC skill level semantics pushed to origin/main.
- Production fast-forwarded from 04442de to 8da432f via verified Git bundle; application.conf and operational files preserved.
- Production full gradlew test build --rerun-tasks PASS: 21/21 tasks.
- Runtime restarted through WMI/Win32_Process.Create and verified from a fresh SSH session.
- Production HEAD 8da432f; Java PID 11920; 0.0.0.0:6613 and [::]:6613 LISTEN; Netty logged server ready.

### START NEXT — Monster AI-G
- Audit whether enemy NPC AI should really use NpcData.specialSkill as a fourth probability slot.
- New DVT evidence: enemy-NPC RoleController:GetElementSkill/GetSkillLv exposes only the three Role.maxNpcSkill base skills, while FollowNpc handles specialSkill separately at level 1; PC TSBattleNPC uses a fourth skill slot.
- Do not change AI-D until live DVT/client/data evidence resolves this conflict. If DVT proves only three enemy slots, correct selector/probabilities with deterministic tests and deploy; otherwise document why the fourth server-only slot is valid.
- Continue automatically with DVT > data > Kotlin > PC priority.

## 2026-10-03 — Monster AI-G — fourth NPC AI slot provenance — PASS / no runtime change
- DVT NpcData.New reads three base skills as original fields [25-27], then immediately reads specialSkill while retaining the original field number [35]. The iOS compact file omits original drop/unknown fields [28-34].
- PC NpcInfo has the same original layout: skill1, skill2, skill3, six drop fields, one unknown byte, then skill4, reborn. Thus PC skill4 is positionally the same original field that DVT names specialSkill.
- PC TSBattleNPC server AI explicitly checks skill4 first, then skill3/2/1 with the one-roll modulo gates.
- DVT Role.maxNpcSkill=3 is the ordinary base-skill array; FollowNpc exposes specialSkill separately when learned. It does not prove server enemy AI must ignore original field [35].
- Because DVT has no client-side implementation of enemy server AI selection and PC is the recovered server fallback, keep the fourth AI slot mapping [skill1, skill2, skill3, specialSkill].
- Updated NpcBattleProfileCalc comments to record this provenance and removed stale wording implying enemy NPCs use PC maxLv.
- No gameplay behavior changed, so production runtime 8da432f / PID 11920 does not require restart for AI-G.

### START NEXT — Monster AI-H
- Audit queued NPC target validity between command selection and execution: if the chosen target dies, flies out, is captured, or leaves its cell earlier in the round, determine whether DVT/PC retargets, keeps coordinates, or no-ops.
- Verify Kotlin resolveRound/grid lookup does not accidentally hit a new occupant at the same coordinates or silently retarget another fighter.
- Use DVT packet/client behavior first, PC execution fallback second; add deterministic regression before any behavior change.

## 2026-10-03 — Monster AI-H — queued target validity / coordinate semantics — PASS
### Evidence
- PC `BattleAbstract.execute()` re-reads the queued target cell at AGI execution time. If the cell is absent/dead (or PC-only untargetable statuses), `changeTarget()` is invoked.
- PC `changeTarget()` only redirects ordinary hostile HandFight / skill_type 1-2; same-side actions, items and skill_type >2 keep the queued coordinate. Retarget scan order is row-first, alternating front/back on the originally selected side.
- PC commands store destination coordinates, not target identity. If another fighter occupies the same queued cell before execution, execution acts on the current occupant at that coordinate.
- DVT `FightManager.RevAttackSkill` consumes S:050-001 target coordinates and only calls `FightField.AttackSkill` when target count >0, supporting zero-target no-op packets rather than client-side retry.

### AI-H1 — hostile stale-target retarget
- Commit `d13ba9f fix: retarget stale hostile battle actions`.
- Added `BattleGrid.firstAliveOnSelectedSidePcOrder()` using PC row-first front/back scan.
- `BattleSkillResolver` re-checks stale hostile HandFight/fightWay1-2 at execution and retargets on the same selected side.
- If no replacement exists, action resolves to zero targets and consumes no SP.

### AI-H2 — remove over-broad SINGLE fallback
- Found Kotlin `BattleGrid.resolveTargets(SINGLE)` was silently retargeting any stale single target, which could make support/debuff/higher-fightWay actions jump to another unit contrary to PC execution semantics.
- Fixed geometry so SINGLE only resolves the queued cell; retargeting is execution policy owned by `BattleSkillResolver`.
- Higher-fightWay/support/debuff/revive actions therefore keep their queued coordinate and may become zero-target no-op.
- If a new fighter occupies the exact queued coordinate before execution, the action affects that current occupant, matching coordinate-based PC commands.
- Commit: `0141f99 fix: preserve queued target coordinate semantics`.

### Validation / production
- Targeted `DvtBattleCommandSemanticsTest` + `DvtBattleTargetGeometryTest`: PASS, 18 tasks executed.
- Full local `gradlew test build --rerun-tasks`: PASS, 21/21 tasks.
- `git diff --check`: PASS.
- `0141f99` pushed to `origin/main` and production fast-forwarded by verified Git bundle.
- Production full `gradlew test build --rerun-tasks`: PASS, 21/21 tasks, exact HEAD `0141f99`.
- Runtime restarted through WMI `Win32_Process.Create` + `_start_prod_detached.cmd`; verified from a fresh SSH session:
  - production HEAD `0141f99`
  - Java PID `11480`, Session 0
  - `0.0.0.0:6613` and `[::]:6613` LISTEN
  - Netty reached ready state; recurring local health-check connect/disconnect succeeds
  - no real runtime ERROR/Exception/FATAL/Koin failure; text scan only matched Gradle task names containing `ConfigurationErrors`.
- Production `application.conf` and operational/untracked files were preserved. Temporary WMI helper was deleted; tracked `.kotlin` session file restored after build.

### START NEXT — Monster AI-I
- Audit stale-coordinate behavior for multi-target / higher fightArea actions when the originally selected center is dead/removed but surrounding cells remain valid.
- Compare DVT `FightField.GetEnemyIdx`/S:050-001 behavior first, then PC `makeExecutionPacket` as fallback.
- Verify Kotlin does not incorrectly drop the entire area, switch target side, or include a dead center where DVT/PC would still hit living surrounding targets.
- Separately identify PC `buff_type 13005/13025` in live DVT Lua before considering their special retarget/untargetable rule; do not implement from PC alone.

## 2026-10-03 — Monster AI-I — stale multi-target anchors — PASS
### Evidence / behavior
- DVT `FightField.GetEnemyIdx` expands fightArea 2/3/5/6/7/8 from the queued target coordinates/side; it does not choose a new center itself.
- PC `BattleAbstract.execute/changeTarget` distinguishes an empty queued cell from an occupied corpse:
  - empty cell: ordinary skills may retarget on the originally selected side;
  - occupied corpse: same-side or skill_type >2 keeps that coordinate;
  - hostile HandFight / skill_type 1-2 may retarget to the first living target in PC row-first front/back order.
- PC commands store coordinates, not target identity. A replacement occupant in the same cell becomes the effective target.
- DVT `FightManager.RevAttackSkill` uses the first S:050-001 target as the animation anchor and does not perform client-side retry.

### Implementation
- Commit `5942cc7 fix: preserve DVT stale target anchors`.
- Empty queued cells follow the PC retarget path while fightWay=8 revive remains a DVT-proven zero-target no-op when its corpse is gone.
- An occupied dead center for a higher fightWay is preserved as a MISS/no-effect packet anchor; living surrounding cells in the same fightArea still resolve normally.
- This prevents area 3/5/6 effects from shifting their visual/geometry center to the first surviving neighbour.
- Targeted command/geometry regressions PASS; full local build PASS 21/21.
- Production source/build/runtime validation was completed before AI-J; this behavior is included in the current production baseline.

### 13005 / 13025 evidence boundary
- Live DVT maps 13005 to `EStatus.Limpid` and 13025 to `EStatus.Invisible` and has their animation/status handlers.
- No live-DVT target-selection check was found that proves these statuses are globally untargetable or must force server retargeting.
- Therefore the PC-only `buff_type 13005/13025` special retarget rule is NOT imported into Kotlin. Keep evidence-blocked unless a DVT server/native/data contract is recovered.

## 2026-10-03 — Monster AI-J1 — Chaos NPC command suppression — PASS
### Evidence
- DVT `FightField.IsCanControl/CheckCanAct` explicitly treats `EStatus.Chaos` as unable to accept a normal command.
- PC `TSBattleNPC` explicitly skips enemy NPC AI command generation while `debuff_type == 14021` (Chaos) before `seekTarget`.
- DVT maps 14021 and the newer Chaos-family skill IDs to the same `EStatus.Chaos`, so the runtime status is the safe abstraction rather than hard-coding one skill ID.

### Implementation
- `BattleInstance.resolveNpcActions` now excludes enemy NPC fighters carrying `BattleStatus.CHAOS` before skill selection/target selection.
- This avoids queuing a command that would only be cancelled later and prevents a Chaos NPC from entering the pre-execution action/combo ordering.
- Taunt is deliberately NOT folded into this filter. DVT proves Taunt removes manual control, but does not expose whether server AI should skip, force HandFight, or force a specific target.
- Added deterministic regression `chaos enemy npc does not queue an AI command`.

### Validation / production
- Targeted `DvtBattleCommandSemanticsTest`: PASS, 18 tasks executed.
- Full local `gradlew test build --rerun-tasks`: PASS, 21/21 tasks; `git diff --check` PASS.
- Commit `226c465 fix: skip chaos npc ai commands` pushed to `origin/main`.
- Production fast-forwarded from `5942cc7` to `226c465` by verified Git bundle; local `application.conf` and operational/untracked files preserved.
- Production full `gradlew test build --rerun-tasks`: PASS, 21/21 tasks on exact HEAD `226c465`.
- Runtime restarted via WMI launcher and verified from a fresh SSH connection:
  - production HEAD `226c465`
  - Java PID `3000`
  - `0.0.0.0:6613` and `[::]:6613` LISTEN
  - runtime log reached server-ready state and recurring local health checks succeed.
- Tracked `.kotlin` session file was restored after build; `tools/start-staging-runtime.ps1` remains untracked locally and is not committed.

### START NEXT — Monster AI-J2 / AI-K
- J2 Taunt: recover authoritative server-side action semantics for `EStatus.Taunt` (skip turn vs forced basic attack vs forced target). Do not change current behavior without DVT evidence.
- If Taunt remains blocked, move to AI-K: audit NPC action ordering / AGI / Tardy and other evidenced status effects that can alter enemy AI execution order.
- Source priority remains live DVT 1.15/Lua > DVT data > Kotlin > PC fallback.

## 2026-10-03 — Monster AI-J2 / AI-K — status semantics
### AI-J2 Taunt — EVIDENCE BLOCKED
- DVT `FightField.IsCanControl/CheckCanAct` blocks manual control while `EStatus.Taunt` is active.
- DVT `FaintSkill` maps 10042/10045/22030 to `EStatus.Taunt`.
- Tracked Skill_C 10042 `Hữu Khách Lai` has `round=0`, fightWay=21 and description `Làm kẻ địch tự đánh chính mình`.
- Therefore Taunt is not safely equivalent to skip-turn. Exact forced-action/target/duration semantics are absent from Lua/data.
- No Taunt gameplay mutation was added; keep evidence-blocked rather than inventing a forced self-attack rule.

### AI-K Tardy / ShakeTake boundary
- DVT maps `EStatus.Tardy`, but no Lua consumer proving an AGI multiplier was found; PC `getAgi()` also uses base AGI. Do NOT invent an AGI penalty.
- DVT SealSkill maps a large `EStatus.ShakeTake` family, but most current Skill_C rows have round=0 and the server-side proc/duration formula is not exposed.
- 11041 `Trảm Kỵ` is the only audited ShakeTake row with round=1, but proc probability remains unproven, so ShakeTake activation is still blocked.

### AI-K status round encoding — PASS
- Live DVT `SkillData.lua` defines raw `round`: 0=none, 1/2/3=exact rounds, 4=continuous three rounds.
- Kotlin previously persisted raw 4 as four rounds, extending affected statuses by one round.
- Current tracked Skill_C has exactly six `round=4` rows: 13050, 14015, 20023, 20027, 21042, 46002.
- All six already map to modeled server statuses (WhirlWind or Poison); no new status/probability behavior was introduced.
- Added `BattleDamageCalc.statusDurationFromSkillRound`: 1/2/3 stay unchanged, 4->3, other values ->0.
- Both damage/status-only application and friendly utility status persistence now use the decoded duration.
- Added permanent regressions for encoding and exact six-row DVT inventory; temporary scratch test deleted before validation.
- Targeted `DvtBattleStatusExecutionTest`: PASS, 18/18 tasks.
- Full local `gradlew test build --rerun-tasks`: PASS, 21/21 tasks; `git diff --check` PASS.
- Code commit `3e741ce fix: decode DVT status round duration` pushed to `origin/main`.
- Production fast-forwarded to exact HEAD `3e741ce` via verified Git bundle; `application.conf` and operational files preserved.
- Production full `gradlew test build --rerun-tasks`: PASS, 21/21 tasks.
- Runtime restarted through the WMI launcher and verified from a fresh SSH session:
  - production HEAD `3e741ce`
  - Java PID `9176`
  - `0.0.0.0:6613` and `[::]:6613` LISTEN
  - runtime log reached server-ready state and recurring local health checks succeed.
- Production tracked Kotlin compiler session file restored after build.

### START NEXT — Monster AI-L
- Audit control/debuff hit/proc probability. Current Kotlin still carries legacy AGI/INT chance logic that is not established by DVT Lua/data.
- PC fallback has explicit separate hit thresholds for disable skill_type=3 and debuff skill_type=15; compare these only where current DVT fightWay/data semantics match.
- Do not globally rewrite damaging status skills whose DVT fightWay differs from the old PC status categories without direct evidence.
- Prioritize a safe evidenced subset first, add deterministic/pure probability regressions, then full build/deploy/checkpoint.

## 2026-10-03 — Monster AI-L — DVT status-only control/debuff execution — PASS
### Evidence / scope
- Current DVT Skill_C hostile status-only subset is represented by fightWay 3 or 15 with an encoded duration and a client-proven concrete status mapping; the audited subset contains 30 rows and uses attribute=0, so `SkillData.attribute` is not a valid discriminator for this family.
- PC fallback preserves the same category split for status-only execution: skill_type/fightWay 3 uses +20 miss threshold and fightWay 15 uses +15, with the ordinary level-difference term. DVT Lua/data does not expose a newer server RNG formula, so this fallback is restricted to the matching 3/15 subset only.
- A successful status-only hit is the proc gate for this subset. The previous Kotlin second AGI/INT roll was legacy behavior with no DVT evidence and could incorrectly turn an already successful control/debuff hit into no status.

### Implementation
- Commit `13e89f1 fix: align DVT status-only battle skills`.
- `BattleSkillResolver` now recognizes hostile status-only skills by fightWay 3/15 + decoded duration + known DVT status mapping instead of modeled status attributes.
- `BattleDamageCalc.calculateStatusOnly` uses the scoped PC fallback miss threshold; on HIT it applies the mapped DVT status directly with the decoded duration and never fabricates HP damage.
- Removed the unproven second AGI/INT status proc roll for this evidenced status-only subset.
- Damaging skills that also carry statuses are intentionally untouched; their proc contract remains a separate audit target.
- Added regressions for the 3/15 miss threshold, the exact current DVT subset shape, zero HP damage, and status application on successful status-only hits.

### Validation / production
- Full local validation for the code commit was completed before production deploy; `13e89f1` is on `origin/main`.
- Production exact HEAD `13e89f18fdc57b8e6a64eafd7c254f114a85a4c7`.
- Clean production `gradlew test build --rerun-tasks`: PASS, 21/21 tasks.
- Tracked Kotlin compiler session file restored after the build; production-local `application.conf` and operational/untracked files preserved.
- Runtime restarted through WMI after the old process was fully stopped; verified from a fresh SSH session:
  - Java game PID `12232`
  - `0.0.0.0:6613` and `[::]:6613` LISTEN
  - runtime log reached Netty server-ready state at 15:39:41 and recurring local health checks succeed.

### START NEXT — Monster AI-M
- Audit damaging skills that also inflict a modeled status. Kotlin still uses a legacy `50 + (attacker INT - target INT)/2` second proc roll after damage; this is not yet established by DVT Lua/data.
- Inventory the current DVT Skill_C damaging+status rows by fightWay/round/status family, then compare their matching PC server branches. Do not generalize the AI-L 3/15 rule unless the data categories and server fallback actually match.
- Also verify whether multi-hit damaging status skills roll status once per execution or once per hit before changing behavior.

## 2026-10-03 — Monster AI-M — damaging skill + status proc audit — EVIDENCE BLOCKED / no gameplay change
### Evidence
- Inventory of current DVT Skill_C mapped-status rows with encoded duration but outside status-only fightWay 3/15 found 57 rows; the hostile/damaging candidates are concentrated in fightWay 1/2.
- The audited fightWay 1/2 damage+status subset currently has `howMuchTimes=1` for every row, so there is no current multi-hit ambiguity about rolling status once per hit versus once per execution.
- DVT `FightRoleController.RunEffect` applies a status only when S:050-001 includes a status Attr_Kind (Seal/Vary/Faint/Spec/Strong/etc.); the client does not calculate the server proc probability.
- PC `BattleAbstract` has explicit hit/proc branches for status-only skill_type 3 and 15, but ordinary damage skill_type 1/2 only performs damage and provides no generic attached-status probability fallback for DVT's newer combined damage+status skills.
- Several DVT descriptions prove that attached statuses are intended (for example 21033 says damage with a chance to stun), but neither Lua nor current data exposes the exact server probability formula.

### Existing Kotlin boundary
- `BattleDamageCalc.calculateAttack` currently uses `50 + (attacker INT - target INT)/2`, clamped 10..90, as a second attached-status proc roll.
- Git history shows that formula originates in the initial Kotlin server commit `75900f4`; no DVT/PC provenance was recovered for it.
- Per project rules, AI-M does not replace this legacy fallback with another guessed probability and does not make attached statuses deterministic without stronger evidence.
- Temporary inventory test/output were deleted after audit; working tree returned to only the intentionally untracked `tools/start-staging-runtime.ps1`.

### START NEXT — Monster AI-N
- Audit status-group collision/reapplication semantics. PC status-only branches refuse a new disable/debuff when the target already has a status in that category; Kotlin currently can overwrite a grouped status.
- Compare DVT group storage (`Seal/Vary/Faint/Spec/Strong`), selection behavior and clear/reapply packets first, then use PC fallback only for matching fightWay 3/15 categories.
- Determine whether an already-statused target should MISS, preserve the old status, refresh duration, or replace it; add deterministic tests before any mutation.

## 2026-10-03 — Monster AI-N — active status-group collision — PASS
### Evidence / implementation
- PC `BattleAbstract.calculateHit` refuses status-only skill_type 3 when `disable_type != 0` and skill_type 15 when `debuff_type != 0`.
- The current DVT status-only subset remains aligned with client groups: fightWay 3 maps to modeled Seal statuses; fightWay 15 maps to modeled Faint statuses.
- Commit `d94c30e fix: preserve active DVT status groups` makes a status-only 3/15 hit return MISS for a target whose matching group is already occupied, preserving the existing status identity and remaining duration instead of overwriting/refreshing it.
- The check is per target, so an area skill can miss an already-statused target while resolving other targets normally. SP remains consumed because the skill was executed and missed.
- Added regressions for active Seal + fightWay3, active Faint + fightWay15, and a permanent DVT Skill_C group invariant for all 30 current status-only rows.

### Validation / production
- Targeted `DvtBattleStatusExecutionTest`: PASS.
- Full local `gradlew test build --rerun-tasks`: PASS, 21/21 tasks; `git diff --check` PASS.
- `d94c30e` pushed to `origin/main` and production fast-forwarded via verified Git bundle; production-local `application.conf` and operational files preserved.
- Production full `gradlew test build --rerun-tasks`: PASS, 21/21 tasks on exact HEAD `d94c30e`.
- Runtime restarted through separate stop and WMI-launch commands to avoid launch race; fresh verification:
  - production HEAD `d94c30e0809bb0fa35d48b3c34350a2a93ccb2af`
  - Java game PID `9204`
  - `0.0.0.0:6613` and `[::]:6613` LISTEN
  - recurring local health-check connect/disconnect succeeds.

### START NEXT — Monster AI-O
- Audit status duration lifecycle: when grouped-status rounds decrement relative to action execution, and whether raw duration 1/2/3 blocks exactly the intended number of turns.
- Compare DVT round/status restore handling first, then PC `BattleParticipant` decrement timing as fallback.
- Verify statuses inflicted mid-round are not decremented too early and statuses expiring at round end restore the correct group/packet state.

## 2026-10-03 — Monster AI-O — initial lifecycle / restore packet — SUPERSEDED for duration (see correction below)
### Evidence
- Live DVT `SkillData.lua` defines raw `round` as the occupied battle-round count: 1/2/3 exact rounds, 4 = continuous three rounds.
- PC `TSBattleNPC.start_round()` calls `BattleParticipant.updateStatus()` before the next round accepts commands. Status duration therefore consumes one round before the following command phase.
- The old Kotlin code skipped decrement for `appliedRound >= currentRound`, so a raw round=1 status applied during round N remained active through all of round N+1: one round too long.
- DVT `FightManager.RevRestoreStatus` calls `HandleStatus(statusKind, skillId)`. Every modeled group maps `skillId=0` to its generic clear state; re-sending the source skill ID would map the same status back onto the client instead of clearing it.

### Implementation / validation
- Commit `04544c4 fix: align DVT status expiry lifecycle`.
- Statuses now consume one duration unit after the round's actions, which is equivalent to PC decrementing before the next command phase: round=1 clears before the next countdown; round=2 survives exactly one following round.
- Expiry S:053-001 now serializes `skillId=0` for generic DVT clear while active grouped status still retains its source skill internally.
- Added lifecycle regressions for duration 1, duration 2, independent group expiry and generic-clear packet skill 0.
- Targeted `DvtBattleStatusLifecycleTest`: PASS, 18 tasks executed.
- Full local `gradlew test build --rerun-tasks`: PASS, 21/21 tasks; `git diff --check` PASS.
- `04544c4` pushed to `origin/main`; production fast-forwarded via verified `monster-ai-o.bundle`, preserving local `application.conf` and operational files.
- Production full `gradlew test build --rerun-tasks`: PASS, 21/21 tasks on exact HEAD `04544c4`.
- Runtime restarted through separate WMI terminate/create steps and verified from a fresh SSH session: Java PID `11052`, port `6613` LISTEN on IPv4/IPv6, Netty server-ready at 16:24:44, recurring health checks succeed.

### START NEXT — Monster AI-P
- Audit periodic effects attached to active statuses at the round boundary, especially Poison and PC Tangle/Clump behavior.
- PC `BattleParticipant.updateStatus()` executes poison/tangle effects during the same status tick used for duration decrement; Kotlin currently only decrements/removes grouped statuses.
- Compare DVT Lua status/skill handlers and current Skill_C first. Do not import PC periodic damage formulas or synthetic skill IDs unless DVT data/client behavior supports them.
- Verify whether periodic ticks happen before or after duration reaches zero, whether they can kill/fly a fighter, and how S:050/S:053 ordering should be represented before adding gameplay mutation.

## 2026-10-03 — Monster AI-P — initial legacy periodic ticks — SUPERSEDED for timing (see correction below)
### Evidence / scope
- DVT client still exposes synthetic server-driven tick animations: skill `20001` = ClumpAtk/Cây tinh hút máu and `20003` = special reflected/periodic damage animation.
- Current DVT Skill_C keeps both synthetic rows but `numerical=0`, so DVT data does not provide tick magnitude.
- PC `BattleParticipant.updateStatus()` is the recovered server fallback: while the status remains active after duration decrement, source `10004` executes `20001` for fixed 30 HP and source `14015` executes `20003` for fixed 90 HP.
- PC does not tick once duration reaches zero. Newer DVT Clump/Poison variants have no recovered server magnitude/periodic contract, so they remain evidence-blocked and are not generalized.

### Implementation / validation
- Commit `4c12825 fix: restore legacy DVT periodic status ticks`.
- `BattleInstance` now emits a self-target S:050-compatible `ActionResult` only for exact source mappings `10004 -> 20001/30` and `14015 -> 20003/90` while the status remains active after decrement.
- Tick damage is fixed direct HP loss, matching PC special-damage behavior; no ATK/DEF calculation, no SP cost, no invented fly-out rule.
- Added lifecycle regressions for legacy Clump 30, legacy Poison 90, final-expiry no-tick, and explicit non-inheritance by newer variants 20025/20023.
- Targeted `DvtBattleStatusLifecycleTest`: PASS, 18 tasks executed.
- Full local `gradlew test build --rerun-tasks`: PASS, 21/21 tasks; `git diff --check` PASS.
- Production fast-forwarded via verified `monster-ai-p.bundle`; local `application.conf` and operational files preserved.
- Production full `gradlew test build --rerun-tasks`: PASS, 21/21 tasks on exact HEAD `4c12825`; tracked `.salive` restored afterwards.
- Runtime restarted through WMI terminate/create and verified from a fresh SSH session: Java PID `4524`, IPv4/IPv6 port `6613` LISTEN, Netty server-ready at 16:41:21, recurring local health checks succeed.

### START NEXT — Monster AI-Q
- Audit Chaos forced-action semantics. Current AI-J1 suppresses ordinary NPC AI while `BattleStatus.CHAOS`, but PC `TSBattleNPC.start_round()` creates a forced HandFight command against a random living fighter on the same side for Chaos before normal AI.
- DVT `IsCanControl/CheckCanAct` blocks manual control while Chaos; verify DVT skill descriptions/data plus PC fallback before changing Kotlin from skip-turn to forced same-side action.
- Audit player/follow-NPC timeout behavior too: timeout auto-command currently only checks stun, so Chaos/Taunt may accidentally receive a normal hostile auto-attack.
- Keep Taunt separate unless its exact forced-action contract is proven.


## 2026-10-03 — AI-O / AI-P correction — complete PC counter audit
### Authoritative evidence and corrected interpretation
- Read checkpoint first. Initial local/origin HEAD was `34b538c`; production source/runtime was `4c12825`, Java PID 4524, IPv4/IPv6 6613 LISTEN. User explicitly authorized taking over the existing uncommitted BattleInstance change.
- DVT `lua/Data/SkillData.lua:19` documents 1/2/3 occupied rounds and raw 4 continuous three rounds. Lua/data does not establish an application-round decrement, so the initial AI-O claim of an extra round was unsupported.
- PC fallback must include BOTH application and tick: `BattleAbstract.cs:592,712` initializes disable/debuff to `ceil(skill_lvl/2)+1`; `TSBattleNPC.cs:139` invokes `updateStatus()` before countdown/command registration; `BattleParticipant.cs:203+` decrements first, clears type at zero, then only executes Clump/Poison while source type remains active.
- Kotlin stores semantic remaining duration without the PC sentinel +1. Therefore a status applied in action round N skips that boundary decrement, remains active for the next duration command rounds, and expires after those rounds. This is the scoped PC fallback consistent with DVT duration encoding, not a claim that DVT Lua exposes server tick code.
- DVT `FightManager.lua:566-582` forwards S:053-001 group/skill to HandleStatus; generic skillId 0 clears the group. Expiry keeps this corrected clear packet and retains source skill internally only while active.
- DVT `Fight/Skill.lua:63,105,106,281` exposes Clump 10004, ClumpAtk 20001, MirrorAtk 20003 and Poison 14015. `FightRoleController.lua:2614,2729` maps current status families. DVT synthetic data numerical=0 provides no magnitude; exact legacy PC fallback remains 10004->20001/30 and 14015->20003/90. New variants stay evidence-blocked.
### Implementation / validation
- `6dd88f6 fix: align DVT status duration lifecycle` commits only BattleInstance.kt and DvtBattleStatusLifecycleTest.kt, as requested.
- `da4e08e fix: run periodic status effects before battle commands` separately connects BattleManager to the round-start phase, broadcasts paced S:050 results before countdown, and checks battle end after periodic deaths before accepting commands.
- Application boundary does not tick. Each subsequent surviving round starts with the legacy tick; expired statuses do not tick. End-of-round S:053 clears expired groups before the next round's S:050 periodic effects/countdown. Direct fixed HP loss can kill, preserves corpse for revive, and does not invent fly-out behavior.
- Targeted lifecycle tests PASS: 7 tests, 0 failures/errors, 18/18 Gradle tasks. Full local `gradlew.bat test build --rerun-tasks` PASS 21/21 tasks; diff check PASS. Local tracked .salive restored.
- Tests cover duration 1/2, duration 3 via Poison, independent group clears with skillId=0, delayed legacy tick, final expiry, newer variant non-inheritance and lethal tick before commands.
- Both commits pushed HEAD:main; local/origin rechecked at `da4e08e9263007d34f5c4470213d8e370dcbbc9b`. Production source fast-forwarded from verified base `4c128254bc5a8b1fba32baf351a7e5313a568ef4` using verified SCP bundle. Production application.conf and operational files preserved.
- Production full `gradlew.bat test build --rerun-tasks`: PASS 21/21 tasks, 3m45s, exact code HEAD da4e08e. Tracked .salive restored after build and again after app:run startup removed it.
- Restart: old game PID 4524 terminated successfully through WMI; detached launcher created via `_launch_prod_wmi.cmd` -> `_start_prod_detached.cmd`, WMI return 0. Fresh SSH verification: Java game PID 11520, Session 0; `0.0.0.0:6613` and `[::]:6613` LISTEN; Netty bind and Main server-ready at 17:04:50 on 2026-10-03 Asia/Bangkok; recurring local health-check connections succeed. Startup scan contains no runtime ERROR/Exception/FATAL (only Gradle ConfigurationErrors task labels).
- Runtime code remains da4e08e; subsequent checkpoint commit is docs-only and requires no restart. `application.conf` local modification and all existing untracked operational files retained. `tools/start-staging-runtime.ps1` is untracked and never committed.

### START NEXT / evidence boundaries
- AI-O duration and AI-P legacy periodic timing correction is deployed and verified. DVT native/server evidence for newer periodic magnitudes and exact multi-group cleanse contract remains evidence-blocked; do not generalize PC formulas or purge selectors to new DVT skills.
- Next audit may continue AI-Q Chaos forced-action semantics from the previous checkpoint, keeping Taunt separate and requiring DVT Lua/data first, PC only scoped fallback. No AI-Q gameplay change in this task.


## 2026-10-04 — Dungeon F0/F1 — DVT data contract + single-player entry — PASS

### F0 evidence / inventory
- Lua DVT `lua/Logic/Dungeon.lua`, `lua/UI/UIDungeon.lua` và `lua/Common/protocal.lua` xác nhận mainKind `47` là **Dungeon/Phó bản**. Constant Kotlin cũ đặt `MISSION=47` chỉ là nhãn sai; protocol 47 không phải mission transport.
- `IOS_Dungeon_C.dat` được giải đúng theo `DungeonData.New`: 57 records, 42 bytes/record; kind distribution = kind1 multiplayer 5, kind2 single 12, kind3 weekly 20, kind5 soul challenge 20.
- C:047-002 thực tế gửi `dungeonId UInt16 + usePassword Byte`, dù comment protocol cũ chỉ ghi dungeonId.
- UIDungeon phân loại: kind1/4 multiplayer, kind2 single, kind3 weekly, kind5 soul. Single join gọi `SendCreateDungeon(id,false,kind)`.
- Client tính lượt đã dùng bằng `MarkManager.missions[dailyFlag].step`.
- `costPoint` không được suy thành phí vào cửa: weekly UI tái dùng trường này làm all-pass claim mark. F1 không trừ tiền/điểm.
- Protocol 97 LimitTimeDungeon là hệ thống riêng và giữ nguyên.

### F1 implementation
- Commit code `0a00a47 feat: add DVT dungeon F1 core`.
- Thêm `DungeonDef`/`DungeonNeedDef` và `DungeonDatLoader`; GameDataManager nạp `IOS_Dungeon_C.dat`.
- `Opcodes.DUNGEON=47`; giữ alias deprecated `MISSION=DUNGEON` để không phá source cũ.
- Thêm `DungeonSystem` và `DungeonHandler`, đăng ký qua Koin/PacketDispatcher.
- F1 chỉ bật nhánh có bằng chứng chắc chắn: kind=2 + maxPlayer=1.
- Validation single: dungeon tồn tại, level range, không đang battle, không ở party, dailyFlag chưa hết lượt, chưa ở chính scene đích.
- C:047-001 hiện trả room list rỗng; multiplayer room registry để F2.
- C:047-002 trả đúng result code DVT; thành công single dùng `SpecialSceneTeleportService` để phát scene change riêng như client contract.
- Non-single trong F1 trả result=7 thay vì giả lập room semantics.
- Chưa kích hoạt skip/reward/reset/weekly/soul criteria hoặc tự suy `missionFlag` mutation.

### Validation / production
- Targeted `DungeonDatLoaderTest`: PASS, 18/18 Gradle tasks.
- Full local `gradlew.bat test build --rerun-tasks`: PASS, 21/21 tasks; `git diff --check` PASS.
- `origin/main` exact code HEAD `0a00a47d508c01f5dc853fc57624ffc51fefd8d2`.
- Production fast-forward qua verified `dungeon-f1.bundle`; local `application.conf` và operational/untracked files được giữ nguyên.
- Production full `gradlew.bat test build --rerun-tasks`: PASS, 21/21 tasks.
- Runtime restart qua WMI: Java game PID `5840`; IPv4/IPv6 port `6613` LISTEN.
- Startup production xác nhận `Nạp IOS_Dungeon xong: 57 bản ghi` lúc 02:22:10 ngày 2026-10-04.
- Tracked Kotlin `.salive` được khôi phục sau build/startup.

### START NEXT — Dungeon F2
- Audit và triển khai room lifecycle protocol 47 cho multiplayer: room list/create/join/password/name/invite/recruit/leave/prepare/start/kick/owner migration.
- Dùng Lua DVT packet layouts làm contract; chưa gộp reward/end/weekly criteria nếu chưa đủ evidence.
- Room registry trước hết là runtime/in-memory; persistence chỉ thêm nếu DVT contract chứng minh room sống qua restart/relog.


## 2026-10-04 — Dungeon F2-A — normal multiplayer room lifecycle — PASS

### Evidence / boundaries
- Continued from Dungeon F1 checkpoint `d112597`; Lua DVT remains authoritative.
- `DungeonPlayerData.New` reads exactly 11 fixed equipment/style UInt16 slots: `Role.maxEquip=6` + `Role.maxStyle=5`. S:047-003/S:047-013 now pad/truncate to exactly 11 so packet alignment cannot drift with session array length.
- S:047-003 join success contains only players already present in the room; the joining client appends itself locally. Existing members receive the new player separately through S:047-013.
- Normal multiplayer page is DVT dungeon `kind=1`; weekly `kind=3` and soul `kind=5` criteria remain deliberately disabled in this milestone.
- Dungeon lobby is distinct from normal PartySystem; being in a normal party returns DVT result 5.
- Lua proves that create-with-password receives a password string back from server, but does not expose the server generation algorithm. Offline fallback is explicitly custom: stable four decimal digits derived from playerId+dungeonId. No gameplay rule is inferred from this fallback.
- Room registry is runtime/in-memory only. No persistence across server restart is added because DVT exposes no room persistence contract.
- Multiplayer start is intentionally NOT enabled yet: current scene-change wire hardcodes `instanceId=0`; teleporting multiple dungeon rooms to the same scene without isolation would mix players across rooms.

### Implementation
- Code commit: `deff339 feat: add DVT dungeon multiplayer rooms`.
- Added `DungeonRoomRegistry`: UInt32 room ids, member/prepared state, capacity, lock/password state, rename, kick, leave, disband and owner migration.
- Extended `DungeonSystem` with shared normal-multiplayer validation using existing DVT level/battle/daily-count/PartySystem checks.
- Extended `DungeonHandler`:
  - C:047-001 room list.
  - C:047-002 create normal multiplayer room (plus existing F1 single path).
  - C:047-003 join room with capacity/password validation.
  - C:047-007 rename room by owner.
  - C:047-010 leave room.
  - C:047-011 prepare/cancel prepare for non-owner.
  - C:047-017 owner kick.
  - S:047-001/002/003/007/010/011/013/014/017 byte layouts implemented to DVT Lua client contract.
- Explicit owner leave migrates owner to first remaining member and clears the new owner's prepare flag before S:047-014.
- Password invite-bypass, C:047-006 password regenerate/toggle, C:047-008 invite, C:047-009 recruit, C:047-012 start, C:047-013 quick join, and disconnect cleanup remain next milestones rather than guessed behavior.

### Validation / production
- Targeted `DungeonRoomRegistryTest + DungeonRoomPacketTest`: PASS, 18/18 Gradle tasks.
- Byte-level join regression verifies existing-member-only list, fixed 11-slot layout, prepare byte, trailing dungeonId, and S:047-013 new-member notification.
- Password response regression verifies a four-digit offline fallback and room lock state.
- Full local `./gradlew test build --rerun-tasks`: PASS, 21/21 tasks; `git diff --check` PASS.
- `origin/main` code HEAD: `deff339a8ba9eb15e5d5da044f84f13b9f70aa83`.
- Production fast-forwarded from exact F1 code base `0a00a47` through verified `dungeon-f2.bundle`; local `application.conf` and all operational/untracked files preserved.
- Production full build: PASS, 21/21 tasks in 2m45s. Tracked Kotlin `.salive` restored after build/startup.
- Runtime restart: old PID 5840 terminated through WMI, detached launcher returned success; new Java PID `8256`.
- Production IPv4/IPv6 `6613` LISTEN at PID 8256; `logs/runtime-production.log` confirms `Máy chủ khởi động: 0.0.0.0:6613` at 02:50:27 on 2026-10-04.
- Runtime scan showed no ERROR/Exception/FATAL entries; Gradle `checkKotlinGradlePluginConfigurationErrors` task names are not runtime errors.

### START NEXT — Dungeon F2-B
- Add real dungeon instance isolation before C:047-012 start. Current S:012 scene-change serializer writes `instanceId=0`; do not start multiplayer rooms until WorldManager/scene visibility can separate same-scene rooms.
- Audit and implement owner C:047-012 -> S:047-011 owner countdown (client hardcodes 5 seconds) -> isolated scene entry -> S:047-018 start timestamp/timer.
- Then add disconnect room cleanup/owner migration and invitation bypass for locked rooms; keep weekly/soul/reward/reset as later F3+ milestones.


## 2026-10-04 — Dungeon F2-B — isolated instance start + countdown — PASS

### Evidence / contract
- Continued from F2-A checkpoint `33155e8`; Lua DVT remains authoritative.
- DVT S:012-000 explicitly carries `sceneTag UInt16 + instanceId UInt16`; client passes both into `Role.ChangeScene`. Instance isolation is therefore a real protocol field, not a custom hidden flag.
- `UIDungeon.StartCountdown()` hardcodes 5 seconds. `Dungeon.RecivePlayerPrePare()` starts that countdown when S:047-011 roleId is the room owner.
- C:047-012 is owner start. Client only sends it after `Dungeon.CheckTeamIsReady()` confirms every non-owner is prepared.
- S:047-018 carries OADate start time; `Dungeon.ReciveEndTime()` derives end time from `DungeonData.time`.
- Ordinary world scenes remain instance 0. No weekly/soul/reward semantics were added in this milestone.

### Implementation
- Code commit: `5ed42ce feat: start isolated DVT dungeon instances`.
- `GameSession` now carries `instanceId`; `GameSessionImpl` defaults it to 0.
- `WorldManager` scopes player presence, appearance and map broadcasts by `(mapId, instanceId)`. Normal maps use 0; dungeon rooms use 1..65535.
- Static NPC patrol remains shared scene data and is broadcast to all active instances of the map; player-specific map lookups/broadcasts are instance-scoped.
- S:012 scene-change serializer now writes the real instanceId instead of hardcoded 0.
- `SpecialSceneTeleportService` supports explicit instance entry; existing callers keep default instance 0.
- `DungeonRoomRegistry` allocates a unique active UInt16 instanceId per room, tracks `starting/started`, hides starting/started rooms from lobby listing, blocks late joins, and cancels countdown state on member leave.
- C:047-012 owner start validates all non-owner members prepared, sends S:047-011(owner,true) to trigger client 5-second countdown, then commits room start, sends S:047-018 OADate start time and teleports all current room members into the same dungeon scene + instanceId.
- Runtime `DungeonRoomRegistry` is now a Koin singleton shared with `DungeonHandler`; disconnect callback removes the player from the room and migrates owner when needed.
- Reconnect/resume persistence and cross-scene dungeon instance propagation are NOT invented here; they remain later evidence work.

### Validation / production
- Targeted DungeonRoomRegistryTest + DungeonRoomPacketTest + DvtLoginWireTest: PASS, 18/18 Gradle tasks.
- New regression verifies each active room receives a distinct nonzero UInt16 instance, room cannot start before non-owner ready, started room disappears from lobby, and S:012 carries nonzero instanceId.
- Full local `./gradlew test build --rerun-tasks`: PASS, 21/21 tasks; `git diff --check` PASS.
- `origin/main` code HEAD `5ed42cec91c69becdc3c8448a34a41f882b489f3`.
- Production verified exact base `deff339` before deploy; fast-forwarded through verified `dungeon-f2b.bundle`. Local `application.conf` and all operational/untracked files preserved.
- Production full `gradlew.bat test build --rerun-tasks`: PASS, 21/21 tasks in 2m49s.
- Restart: old PID 8256 terminated; `_launch_prod_wmi.cmd` returned WMI success; new Java game PID `8952` owns IPv4/IPv6 port 6613.
- Runtime log: `Nạp IOS_Dungeon xong: 57 bản ghi`; Netty `Máy chủ khởi động: 0.0.0.0:6613` and Main server-ready at 03:32:02 on 2026-10-04. Health-check connects continue; inspected startup tail contains no ERROR/Exception/FATAL.
- Tracked Kotlin `.salive` restored after build/startup.

### START NEXT — Dungeon F2-C
- Audit/implement C:047-006 password toggle/regenerate, C:047-008 invite + S:047-015, C:047-009 recruit + S:047-016, C:047-013 quick join/invite bypass semantics.
- Keep invite bypass for locked rooms evidence-driven; do not invent trust/authorization state beyond packet contract.
- After lobby protocol is complete, move to F3: dungeon mission lifecycle/end/result/reward/daily count and timeout, then weekly/soul criteria.


## 2026-10-04 — Dungeon F2-C — invite / recruit / quick join — PASS

### Evidence / scope
- Continued from F2-B checkpoint `198be344`; Lua DVT remains authoritative.
- C:047-008 invite and S:047-015 invitation layout are implemented from `DungeonInvitation.New` / `protocal.lua`.
- C:047-009 recruit and S:047-016 public recruit are implemented for World channel=2, which is fully evidenced by DVT UI. Organization routing remains disabled because current offline runtime has no authoritative organization-recipient contract.
- C:047-013 quick join selects an available unlocked room of the requested dungeon.
- Invitation state may bypass a locked-room password exactly for the invited target; invitation is consumed on join.
- C:047-006 exists in `Dungeon.SendSetPassword()`, but `UIDungeon.OnClickTeamPass` has the call commented out in live DVT Lua. Therefore the current client UI does not emit it. Server toggle/regenerate semantics remain evidence-blocked and are intentionally not invented.

### Implementation
- Code commit: `6ce26a33 feat: complete DVT dungeon lobby joins`.
- `DungeonRoomRegistry` now tracks per-room invite targets and clears invitation state on successful join/disband.
- `DungeonHandler` adds C:047-008, C:047-009, C:047-013 plus S:047-008/015/009/016 behavior.
- Locked-room join accepts exact password or a still-valid explicit invitation.
- Public recruit broadcasts the DVT S:047-016 wire to online sessions for World channel only.
- Quick join excludes locked/full/starting/started rooms through lobby snapshot semantics.
- No weekly/soul/reward/daily-reset semantics are mixed into F2-C.

### Validation / production
- F2-C tests present in `DungeonRoomPacketTest` cover invitation wire + password bypass, quick join and world recruit packet ordering.
- Production exact HEAD `6ce26a33f4bcd6d8cda63f156aa6f9d4696f55e8`.
- Production full `gradlew.bat test build --rerun-tasks`: PASS, 21/21 tasks in 3m17s.
- Runtime restarted through WMI; Java game PID `12844`.
- IPv4 `0.0.0.0:6613` and IPv6 `[::]:6613` are LISTENING on PID 12844.
- Tracked Kotlin `.salive` restored after build.
- Production `application.conf` and operational/untracked files preserved.

### START NEXT — Dungeon F3
- Implement server-authoritative dungeon mission lifecycle/end/result/timeout/daily count only after locking reward semantics.
- DVT client proves timeout UI is display-only: `Dungeon.UpdateTime()` stops timer at zero and contains `--todo Trigger`; server must own timeout/end.
- `Dungeon.CheckInDungeon()` keys current dungeon state through `dungeonData.missionFlag`, so completion must follow mission lifecycle rather than sceneId alone.
- `IOS_DungeonCombo_C.dat` = 180 records x 31 bytes. `lootTable` rows have multiple `kind`/threshold semantics; do not apply one generic reward formula until `DungeonCombo.kind` is resolved.
- Single-player server state must retain active dungeonId/start time because multiple dungeon definitions can share the same scene.


## 2026-10-04 — Dungeon F3-A — active run state + single start timer — PASS

### Evidence
- DVT `Dungeon.SendCreateDungeon()` sets `nowDungeonId/nowDungeonKind` before C:047-002; successful kind=2 closes UI.
- DVT S:047-018 carries OADate start time; `Dungeon.ReciveEndTime()` computes `nowDungeonEndTime = startTime + DungeonData.time`.
- Before F3-A the server sent S:047-018 only for multiplayer start, so single-player dungeon had no authoritative timer start packet.
- Multiple dungeon definitions share scene ids; server cannot infer active dungeon solely from `mapId`. Runtime state must retain exact dungeonId/startTime/instanceId.
- DVT `Dungeon.UpdateTime()` only stops the display timer at zero and contains `--todo Trigger`; timeout/end remains server-authoritative.

### Implementation
- Code commit `c6af125 feat: track DVT dungeon active runs`.
- Added `ActiveDungeonRun(dungeonId,startTime,instanceId)` runtime state keyed by playerId in `DungeonSystem`.
- Single create success now teleports, records the exact active run, then sends S:047-018 OADate start time.
- Multiplayer start records the same dungeonId/startTime/instanceId for every current room member; failed scene entry rolls active state back.
- Disconnect clears active run state for that player.
- No reward/daily mutation or timeout result is invented in F3-A.

### Validation / production
- Targeted `DungeonRoomPacketTest + DungeonActiveRunTest`: PASS, 18/18 tasks.
- Regression verifies single create emits S:047-002 success followed by S:047-018 and records exact dungeon id/start time; multiplayer members share instance/start state.
- Full local `gradlew test build --rerun-tasks`: PASS, 21/21 tasks; `git diff --check` PASS.
- Production fast-forwarded by verified `dungeon-f3a.bundle` to exact code HEAD `c6af125935f233713a1d4c6af14ef03588e57e89`.
- Production full build: PASS, 21/21 tasks in 2m54s.
- Runtime restarted through WMI; Java game PID `12672`.
- IPv4 `0.0.0.0:6613` and IPv6 `[::]:6613` LISTEN on PID 12672.
- `application.conf` and operational/untracked production files preserved; tracked Kotlin `.salive` restored.

### F3-B evidence already locked
- `IOS_Mark_C.dat`: dungeon `missionFlag` entries are mission kind 4/5; dungeon `dailyFlag` entries are kind 8 counters with bitId=0.
- Examples: 12336 = daily Ba Đậu Yêu challenge count; 12334 = daily Thiên Sư multiplayer count.
- Direct EveDataLoader audit shows missionFlag mutations occur inside dungeon scenes via resultClass=2 pStyle 1/3/4/5.
- No audited Eve result mutates dailyFlag; daily count is therefore dungeon-backend responsibility, not Eve-script responsibility.
- Reward table `IOS_DungeonCombo_C.dat` = 180 records x 31 bytes. kind=1 uses second-like thresholds displayed by multiplayer UI; kind=2 uses 10/50/99/100-style thresholds. Exact selection semantics must be locked before reward mutation.

### START NEXT — Dungeon F3-B
- Determine authoritative dailyFlag consumption point (start vs successful clear/skip) before mutating count.
- Resolve DungeonCombo kind=1/kind=2 reward selection, then hook active mission completion to S:047-012 result/rewards and timeout failure.
- Keep weekly/soul all-pass/reset-count in later F4 unless their semantics become necessary to complete F3.


## 2026-10-04 — Dungeon F3-B1 — Combo data + durable normal-dungeon daily reset

### Reconciled baseline
- Read the complete 2450-line checkpoint before code changes. Local feature/9-pet and freshly fetched origin/main were fe3de71; production source/runtime was c6af125, game PID12672, port6613.
- No saved F3-B selector/source patch was present. The existing untracked DungeonEveContextScratchTest was read/run and preserved, excluded from commits.
- Do not redo F1/F2-A/F2-B/F2-C/F3-A. Source priority remains Lua DVT > client/data/Eve > Kotlin > PC fallback; no Lua/client files changed.

### Implemented, verified evidence
- DungeonComboData.New: exact count header + 31-byte rows; 180 real rows. DataManager.OnLoadDungeonComboData appends duplicate IDs in file order. Added DungeonComboDatLoader + GameDataManager loading and immutable models preserving kind/kindValue and unsigned UInt32 values.
- DungeonComboData:GetReward: choose the current turn slot, fall back through lower nonzero slots, finally slot0. Implemented rewardForTurn; no guessed RNG weights, threshold comparator, or reward grants.
- MarkData.ReadInfo explicitly defines kind8 as daily clear/hidden. Only dailyFlag IDs referenced by normal dungeon kinds1/2 AND Mark kind8/bitId0 are reset. Active missionFlag kind4/5, completion flags, weekly/soul state and unrelated daily missions remain untouched.
- Added character_dungeon_days. Reset selected mission rows and advance persisted day marker in one repository transaction under the existing per-character quest mutex. Same/older dates never reset; relog/restart cannot grant another reset. First observation preserves legacy counts because their prior day is unknown.
- Login resets before S24-006 mission snapshot; dungeon entry validation checks boundary before quota; online autosave refreshes every30s and sends S24-004 for actually removed counters.
- Calendar policy is the EXISTING offline DispatchSystem.currentDayKey: midnight Asia/Taipei (23:00 previous day Asia/Bangkok). This is explicitly an offline-server policy, NOT recovered original DVT dungeon reset-hour evidence. No new date inferred from host timezone.

### Validation
- Targeted DungeonF3BDataTest + DungeonDailyResetTest + QuestStepIntegrityTest + DungeonRoomPacketTest: BUILD SUCCESSFUL, 18/18 tasks.
- New regressions: 4 Combo tests + 4 daily-reset tests; covers real180-row order, unsigned values, rebirth fallback, malformed/trailing data, first-use preservation, restart, day rollover, clock rollback, concurrent single reset, protected noncounter missions.
- Full local gradlew test build --rerun-tasks: BUILD SUCCESSFUL, 21/21 tasks. git diff --check PASS.
- Production deployment and runtime verification to be appended after commit/push/build/restart.

### F3-B remaining — evidence blocked, not complete
- Raw tables: kind1 thresholds include200/400/600 and600/900/1200; kind2 includes10/50/99/100; kind4 extraLoot uses turn1/2/3. Preserve semantics separately; do not call these probability weights.
- TextData10295 says single BaDauYeu reward depends on enemy damage; 21363/21473 say treasure/search reward depends on remaining time. This proves score category, but not exact damage denominator/aggregation or equality at tier boundaries. No selector from score to row activated.
- Trusted Eve context: scene62001/eve2 fightDataKey1 win/lose/escape ALL complete mission12322; only win adds12323. Therefore mission disappearance alone is not success. Scene62002/eve13 winning fight4 removes12332 then adds12321; eve14 explicit exit also removes12332. Never grant from generic REMOVE_MISSION.
- No audited Eve mutation writes dungeon dailyFlag. The exact consume point (entry/start vs terminal result/clear/skip), failure/timeout quota semantics and per-run result/reward transaction still need authoritative evidence; no guessed consumption added.
- Completion/timeout/S47-012 reward/EXP backend remains outstanding. Neither data-loader PASS nor daily-reset PASS is full F3-B gameplay PASS.
- Next: lock score formula, tier boundary comparator, daily consumption and EXP reward-item semantics from DVT/native/server evidence or controlled official runtime trace, then implement per-run idempotent settlement with targeted tests. Weekly/soul remain F4.


### 2026-10-04 — F3-B1 production deploy — BUILD/STARTUP PASS
- Code commit d43e60c73f67a34231fdb4bcd12cc013089de874 pushed to origin/main; production fast-forwarded from verified c6af125 via verified Git bundle + Git Bash SCP/SSH.
- Full local suite: 594 tests, 0 failures/errors, 3 skipped (includes the pre-existing local scratch audit); full local build21/21. The two new F3-B suites contribute8 passing tests.
- Production exact code HEAD d43e60c: full gradlew test build --rerun-tasks PASS21/21, 3m13s.
- Old game PID12672 terminated via WMI only after build PASS. WMI Win32_Process.Create launched the existing _start_prod_detached.cmd with return0.
- Fresh SSH after the launcher session closed: game java.exe PID7064, Services session0; [::]:6613 LISTEN; TCP127.0.0.1:6613 PASS.
- Startup logs/runtime-production.log: IOS_Dungeon57 and IOS_DungeonCombo180 loaded; autosave30s started; Netty bound0.0.0.0:6613 and Main ready at05:41:55 on2026-10-04 Asia/Bangkok. Startup scan showed no actual ERROR/Exception/FATAL/Koin failure (Gradle ConfigurationErrors task names excluded).
- Production application.conf hash stayed25A3A41691F3800F093001F34B98962AE818A0EF098CAA0BBBBD5FADF77F4742; local operational files preserved. Tracked compiler .salive restored after build/startup. Existing dev scratch/bundles/start-staging-runtime.ps1 remain untracked and were not pushed.
- Subsequent checkpoint commit is docs-only; runtime remains code d43e60c and does not need another restart.
- Calendar rollover/database mutation has regression coverage; user gameplay and a real midnight rollover are not claimed PASS. Initial day registration deliberately preserves existing counters.
- F3-B is PARTIAL: loader/turn selector + daily reset deployed. Automatic reward, quota consumption, terminal/timeout settlement remain evidence-blocked as documented above. Continue there, not from F1/F2/F3-A.


## 2026-10-04 — Dungeon F3-B2 — entry / Eve encounter activation — DEPLOYED

### Root cause and DVT evidence
- User runtime symptom: normal dungeon scenes (notably 62001/62003) could load without usable monsters and BaDauYeu could place the player on a blocked Ground cell.
- Lua Dungeon.CheckInDungeon requires DungeonData.missionFlag to exist as an active Mark mission before dungeon Eve logic can be considered active.
- Real Eve dungeon gates use mission-step comparisons that differ from the legacy ordinary-quest compareStep mapping. Dungeon Mark kinds 4/5 use numeric Eve operators; staged result values 1002/1003 encode absolute dungeon mission steps 2/3.
- Ground.mmg uses the client MapData column-major block grid; obstacle bits 0x01 and 0x04 are non-walkable. The old 62001 centroid (820,1200) is blocked.
- Eve roleStatus isHide/isVisible must be retained because dungeon encounter NPCs are initially hidden and revealed through Eve results.
- IL2CPP dump contains the native Lua/network/reader bridge (NetworkManager, LuaHelper, ReadUInt16/ReadUInt32), but no native DungeonData/DungeonCombo/MapManager/GetStartEmptyBlock/IsObstacle gameplay implementation. Those dungeon semantics are Lua-side.

### Implementation
- Commit 7050aff012493885da6e1cb82250031671b9b788 — fix: initialize DVT dungeon Eve encounters.
- Added GroundWalkGridLoader + MapWalkGrid and loaded exact Ground.mmg walkability data.
- Dungeon entry now derives a walkable point from dungeon scene data and rejects entry before mission mutation if no valid grid point exists.
- Single and multiplayer dungeon entry initialize the exact DungeonData.missionFlag at step 1 before scene teleport; no dailyFlag or completion flag is granted at entry.
- EveDataLoader now preserves NPC isHide/isVisible.
- EveStateBuilder identifies dungeon Mark kinds 4/5.
- EveConditionEvaluator applies real numeric Eve comparisons only to dungeon missions, leaving ordinary quest step semantics unchanged.
- EveResultExecutor maps DVT staged values 1001..1255 to absolute dungeon steps only for Mark kinds 4/5; ordinary SET_FLAG behavior remains unchanged.
- Regression covers walkability for every normal kind 1/2 dungeon, real 62001/62003 hidden-NPC state, mission-before-teleport ordering, treasure step branches, multiplayer 1002/1003 staging, and BaDauYeu win/failure terminal markers.

### Validation / production
- Targeted DungeonEntryRegressionTest: PASS, 18/18 tasks.
- Full local test build --rerun-tasks: PASS, 21/21; git diff --check PASS.
- Pushed to origin/main as 7050aff.
- Production fast-forwarded from 22dee13 to 7050aff; application.conf and operational files preserved.
- Production full test build --rerun-tasks: BUILD SUCCESSFUL, 21/21.
- Runtime restart through existing WMI launcher: old game PID 7064 stopped; new game Java PID 5724 owns port 6613.
- Startup log: Ground 4469 maps, Eve 3797 scenes / 22076 placements / 25457 events, IOS_Dungeon 57, IOS_DungeonCombo 180; Netty bound 0.0.0.0:6613 and server ready at 06:17:13.
- Local health-check connections succeeded after startup.

### Reward boundary — do not guess
- S:047-012 Dungeon End is exact in Lua: result byte + dungeonId UInt16 + reward count byte + repeated itemId UInt16 + quantity UInt32. UIResult displays exactly what the server sends.
- DungeonComboData.New and GetReward are exact and already loaded: row id/kind/kindValue plus four turn slots; GetReward selects current rebirth slot with lower-slot fallback.
- UIDungeon proves multiplayer preview tiers use lootTable rows and single preview uses first/last lootTable rows; extraLoot selects the row whose kindValue equals player Turn.
- However neither Lua DVT nor the IL2CPP client binary contains the authoritative server-side selector that turns runtime score into a lootTable row. Known text only states BaDauYeu depends on enemy damage and other dungeon rewards can depend on remaining time; denominator/aggregation/tier-boundary equality are not present.
- Therefore automatic base reward settlement remains intentionally disabled rather than inventing a formula. Exact packet/data/rebirth semantics are ready; the missing selector requires authoritative server evidence or controlled official-server trace before activation.


## 2026-10-04 — Dungeon F3-B3 — reward settlement + Leave Dungeon — DEPLOYED; weekly battle evidence-blocked

### User report / scope
- User reported: normal dungeon has no reward, Leave Dungeon button does nothing, Weekly Dungeon and Crazy Weekly Dungeon are missing.
- Continue strict source priority: Lua DVT + client/data + IL2CPP first; no Lua edits; no fabricated weekly enemy formations.

### Reward evidence locked
- DVT S:047-012 is exact: result Byte + dungeonId UInt16 + count Byte + repeated itemId UInt16 + quantity UInt32. UIResult displays exactly the server-sent reward list.
- DVT DungeonComboData:GetReward selects current rebirth slot and falls back through lower nonzero slots.
- Normal multiplayer kind1 loot tables are remaining-time tiers. UIDungeon displays row N using the previous row's kindValue/60 as that tier's lower-bound time label.
- Real data examples:
  - dungeon1 table1: 600/900/1200 seconds -> item46921 qty1/5/10.
  - dungeon14 table23 -> item46922 qty1/5/10.
  - dungeon15 table24 -> item46923 qty1/2/3.
  - dungeon16 table25 -> item46924 qty1/2/3.
  - dungeon17 table26 -> item32649 qty1/2/3.
- Screenshot bags match IOS_Item + IOS_BlissBag exactly:
  - 46921 "Túi phụ bản cấp 20 II" -> 1 equipment bag + item65412 x20 + item65464 x3.
  - 46923 level80 II -> 1 equipment bag + item65412 x70 + item65464 x15.
  - 46924 level110 II -> 1 equipment bag + item65412 x70 + item65464 x15.
  - 32649 level140 III -> 1 equipment bag + item65412 x70 + item65464 x20.
- Settlement grants the BAG item itself; opening specialAbility48 lets existing BlissBag logic grant the three inner items. No screenshot contents are hard-coded into dungeon settlement.
- Timed kind1 returns no reward at remainingSeconds<=0. Secondary original-game documentation also states team-dungeon reward is larger when turned in sooner and overtime can yield no gift.
- Single dungeon kind2 tables use 10/50/99/100 damage rows. Current server only activates the unambiguous 100 row on terminal full PvE victory; lower partial-damage rows remain deliberately unsupported rather than guessed.

### Implementation
- Gameplay commit: edb5f86cf0bd98255b4b0759c7c782ab7f37b83f "fix: settle DVT dungeon rewards and leave".
- ActiveDungeonRun now retains origin map/x/y/instance + runtime start millis + idempotent settled marker.
- Single run is marked BEFORE teleport; multiplayer members keep their individual pre-dungeon origins.
- C:013-004 is overloaded by Lua Dungeon.LeaveSinglePlayDungeon. PartyHandler now checks active dungeon first, teleports back to captured origin and clears dungeon-room/run state; ordinary party leave remains fallback.
- Battle end ordering is Mine -> Eve resume -> Dungeon settlement. Intermediate fights keep missionFlag active and do not settle; terminal Eve removal allows one settlement.
- Reward insertion uses InventorySystem exact bag planning/mutation, realtime bag-slot sync, then S:047-012.
- DungeonHandler now has S:047-012 serializer with UInt32 reward quantity.
- C:047-022 weekly all-clear claim is future-ready from exact client fields: 20 kind3 nodes, first node costPoint=12762 claim Mark, allPassPrize=34366. It grants item34366 and sets its completion flag only when all 20 progress marks are complete and the claim flag is absent.

### Weekly / Crazy Weekly audit — remaining blocker
- IOS_Dungeon_C has exactly 20 kind3 weekly stages (IDs18..37) and 20 kind5 crazy/soul weekly stages (IDs63..82).
- All 40 point at scene62015, missionFlag=0, time=30 minutes. They do NOT use the normal Eve mission lifecycle.
- Lua FightManager defines dedicated battle style WeekDungeon=24.
- Scene62015 does not exist in Eve.emg, so no weekly enemies can be spawned through Eve.
- IOS_DungeonCriteria is entry restrictions only; IOS_DungeonArea is display/region geometry only; IOS_WeekDungeon_C.dat is count=0. IL2CPP exposes bridge/runtime types but no weekly enemy formation table.
- Searches of local PC/reference/old-server sources did not recover an authoritative 40-stage enemy formation table.
- Therefore kind3/kind5 combat remains intentionally disabled. Do NOT remove the kind1-only validation until the backend-only formations are recovered.
- Reward/progress data is nevertheless exact:
  - kind3 weekly stage reward table -> item29845 x1 per stage.
  - kind5 crazy weekly -> item29846 x stage index (1..20).
  - weekly all-clear item34366 ("Hộp Bách Chiến Thiên Quan") data exists and is wired for future claim.
- Official game notice says weekly progress resets every Monday 00:00 and each stage is independent, with stage reward + all-clear reward. Weekly reset persistence is NOT implemented yet because combat itself remains blocked.

### Validation / production
- New DungeonRewardLeaveRegressionTest covers timed reward boundaries, 100% single-dungeon row, weekly fixed table preservation, origin return, C13-4 priority and S47-12 UInt32 quantities.
- Targeted dungeon regression: PASS 18/18 tasks.
- Full local test build --rerun-tasks: PASS 21/21 before final zero-time guard; final targeted test after guard PASS 18/18.
- Production fast-forwarded from 7050aff to edb5f86 through verified bundle. application.conf and operational files preserved.
- Production full test build --rerun-tasks: BUILD SUCCESSFUL 21/21 in 3m31s.
- Runtime restart: old game PID5724 stopped; new game Java PID7188.
- IPv4 0.0.0.0:6613 and IPv6 [::]:6613 LISTEN on PID7188.
- Startup ready at 07:01:42; IOS_Dungeon57, IOS_DungeonCombo180, Ground4469 and Eve3797 loaded.
- Post-start cleanup restored tracked .kotlin/sessions; deployment bundle removed. Production source HEAD exactly edb5f86.

### START NEXT — Weekly battle formation recovery / runtime user test
1. User test normal dungeon reward + result + Leave button first.
2. Recover authoritative WeekDungeon style24 enemy formations for IDs18..37 and63..82 from original backend source or official packet/runtime trace. Do not infer NPC IDs/positions/stats from videos alone.
3. Once formations are locked, implement kind3/5 start, criteria, per-stage progress/weekly Monday reset, fixed stage reward, all-clear claim, then production test.

## 2026-10-04 — Dungeon F4 research — weekly reset + Bách Chiến formation recovery

### Weekly calendar / Mark evidence
- IOS_Mark_C.dat confirms the active kind3/kind5 stage counters are Mark kind=9 (weekly), not kind8 daily counters.
- Current kind3 Bách Chiến IDs18..37 use dailyFlag 12722,12724,...,12760, all bitId=0.
- Current kind5 Soul/Crazy weekly IDs63..82 use dailyFlag 13328,13330,...,13366, all bitId=0.
- Bách Chiến page 1 all-clear claim Mark is 12762, kind9, bitId=1706; DungeonData id18 uses costPoint=12762 and allPassPrize=34366.
- Current kind5 IDs63..82 have costPoint=0 and allPassPrize=0; do not invent a Soul all-clear reward.
- Official TS Online M notice independently states weekly progress resets every Monday 00:00, stages are independent, each clear grants a reward, and clearing all Bách Chiến stages grants an extra all-clear reward.
- Safe migration rule for production: first observation of a new weekly calendar marker must preserve existing counters/claim state because the previous reset boundary is unknown; only a proven later Monday boundary should clear scoped kind9 counters and the scoped Bách Chiến all-clear bit1706.
- Do not reset every kind9 Mark globally. Mark_C contains legacy/future weekly pages (21..60 and additional TSN markers); reset scope must come from the currently loaded DungeonData rows plus their explicit claim Mark.

### Empty/non-authoritative formation sources ruled out
- IOS_WeekDungeon_C.dat is only 4 bytes: UInt32 count=60 with no body records; same in its earliest imported Git revision. It is not an enemy formation table.
- IOS_SceneFight_C.dat has 381 records and no scene62015.
- A scan of likely iOS Fight/Battle/Dungeon/Npc/Soul/War tables found no separate 62015 formation table; IOS_Dungeon_C.dat only points special dungeons at scene62015.
- TSOnlineOS/MobileClient, old TSOnline2026/newts decompiled server sources and current repo history do not expose a backend WeekDungeon formation table.
- Lua/client still proves EFFWar_Style.WeekDungeon = 24.

### High-confidence DVT-client reconstruction — Bách Chiến 1..20
IOS_Npc_C.dat contains a structured special-NPC block beginning at id40258. The stage grouping below is reconstructed from DVT client data, not copied from a backend formation file. Confidence is high because independent public battle descriptions for stages18/19/20 match the reconstructed groups exactly and the earlier blocks align with the DVT stage restrictions/themes.

- Stage1: 40258..40267
- Stage2: 40268..40277
- Stage3: 40278..40287
- Stage4: 40288..40297
- Stage5: 40298..40307
- Stage6: 40308..40317
- Stage7: 40318..40327
- Stage8: 40328..40337
- Stage9: 40338..40347
- Stage10: 40348..40357
- Stage11: 40358..40367
- Stage12: 40368..40377
- Stage13: 40378..40387
- Stage14: 40388..40397
- Stage15: 40398..40407
- Stage16: 40408..40417
- Stage17: 40418..40427
- Stage18: 40428..40437 = Tả Từ, Vu Cát, Gia Cát Lượng, Tư Mã Ý, Lục Tốn + 5 Cận vệ quân. This independently matches the public stage18 battle description.
- Stage19: 40438..40447 = Đổng Trác, Điêu Thuyền, Lý Nho, Lý Biệt, Lý Xiêm, Quách Tị, Lý Giác, Đổng Mân, Đổng Hoàng, Ngưu phụ. Public stage19 description independently says there are 10 enemies and highlights Đổng Trác/Lý Nho/Điêu Thuyền.
- Stage20 special block: 40448..40455 are 8 Lv200 Lữ Bố in earth/water/fire/wind pairs; 40480..40481 are 2 more Lv200 Lữ Bố with element5. Public stage20 description independently calls it the five-color Lữ Bố stage and warns about the heart-element confusion skill. This strongly suggests these 10 are the intended five-element pair set, but the non-contiguous final pair means exact positional/order formation must remain explicitly marked reconstructed until a backend trace is recovered.

Strong internal consistency:
- Stage5 block is ten basic soldiers with unusually large HP, consistent with DungeonCriteria kind7/10-round survival semantics.
- Stage14/15/16/17 blocks transition through Wei/Shu/Wu/heavenly-soldier themed high-stat groups, matching late-stage progression.
- Stage20’s five Lữ Bố element pairs provide a direct data explanation for the known five-color Lữ Bố fight.

### Battle engine implications before activation
- BattleManager.addCenteredEnemies() currently maps a 10-entry enemy list as first five in enemy front column and next five in enemy back column; it ignores Eve locationPos. Using reconstructed NPC IDs therefore still requires evidence for exact order/positions.
- createPveBattle() is single-session and brings the custom multi-pet deployment roster. Weekly rooms need a separate multiplayer special-battle path and must not accidentally allow the 9-pet extension to occupy the entire player side.
- Add EWarStyle.WEEK_DUNGEON = 24 only when the special battle path is implemented; DVT client directly proves this value.
- kind3 criteria 1..5 are entry/team restrictions; kinds6/7 are round constraints and must be enforced by battle/settlement semantics, not just lobby validation.
- kind5 criteria 8/9/10 depend on the Soul subsystem/state, which the current Kotlin server has not implemented sufficiently. Do not fake Soul qualification.

### Soul/Crazy weekly formation status
- IOS_Npc_C.dat has a suspicious Activity-NPC cluster around 38216..38250 containing Cuồng-Lữ Bố, Huyễn-Lữ Bố, Ma-Lữ Bố, light/dark elements 7/8 and later high-level Wei groups.
- This is plausible evidence for the newer Crazy Weekly content but is not yet enough to derive 20 exact stage formations. Keep kind5 combat blocked until a complete grouping can be recovered and cross-checked.

### NEXT
1. Implement durable Monday-week reset for only the loaded kind3/kind5 counter Marks and explicit claim flags, with first-observation preservation and rollback protection.
2. Add byte/data regression locking the reconstructed Bách Chiến NPC ranges and exact stage18/19/20 names/elements.
3. Continue recovering exact order/position and special round-win semantics before enabling kind3 combat.
4. Continue targeted IOS_Npc_C.dat/public-trace audit for Crazy Weekly; do not enable kind5 while Soul qualification/formation remains evidence-blocked.


## 2026-10-04 — Dungeon F4-A — weekly reset persisted; Crazy Weekly formation anchors recovered

### Code pushed — validation/deploy still pending
- Commit `611e09195039fe48475d8d3b1090d098b135795d` — `fix: persist DVT weekly dungeon reset`.
- This commit has NOT been claimed build-PASS and has NOT been deployed to production. Remote Desktop Commander reached its monthly quota before Gradle/production validation; production remains gameplay HEAD `edb5f86`.
- Added `character_dungeon_weeks` persistent Monday marker and `refreshDungeonWeek()`.
- First-ever observation records the current week without deleting legacy progress/claim state. Only a later Monday boundary clears scoped weekly data. Same/older week keys are no-ops.
- Reset scope comes only from loaded DungeonData kind3/kind5:
  - stage counters must be Mark kind9 + bitId0.
  - claim flags come only from positive `costPoint` Mark kind9 bitIds. Current DVT data therefore scopes Bách Chiến claim bit1706 only; kind5 currently has no claim mark.
- Login refreshes the weekly boundary before the initial mission/flag snapshot.
- Protocol47 requests refresh online state so a player staying connected across Monday receives mission removals and cleared claim bits without relog.
- Added `DungeonWeeklyResetTest` for first-observation preservation, next-Monday scoped clear, rollback/same-week no-op and concurrent single-reset behavior.
- Pending execution checks when a runner is available: compile new Exposed `inList` deletes/selects, MockK `arg<Set<Int>>(3)`, full `gradlew test build --rerun-tasks`, then production deploy/restart/6613 verify.

### CI fallback added
- Commit `6f0499fc257477044798543a7c01ae11a81bff79` adds `.github/workflows/ci.yml` with Java21 and `./gradlew test build --rerun-tasks` on main/PR/manual dispatch.
- At the time of this checkpoint GitHub Actions had not produced a run yet; do not treat the CI file itself as validation.

### Crazy/Soul weekly — strong DVT Npc_C block
The earlier 382xx suspicion is superseded by much stronger evidence in the DVT `IOS_Npc_C.dat` block `40687..40859`. This is still reconstruction from client data + independent battle descriptions, not an original backend formation table, so exact position/order remains evidence-sensitive.

High-confidence anchors:
- Stage8 candidate group centers on `40748..40756`: six consecutive `Hồn - Công Tôn ...` NPCs plus three support NPCs. This matches the public Crazy Weekly stage8 note that all 公孫 enemies use 青龍 and should be prioritized.
- Stage16 candidate `40823..40828`: Ôn Dịch Vương / Ôn Dịch Trư Vương / Ôn Dịch Trư Tướng / Ôn Dịch Lang Nhân / Ôn Dịch Thi / Ôn Dịch Hộ Vệ. This matches the public stage16 pig-head + strong-poison mechanic.
- Stage17 candidate `40829..40835`: Hủ Hủ Vương, Hủ Hủ Thi Vương, Hủ Hủ Thi Suất, Hủ Yêu Nữ, Khô Cốt Nam/Nữ, Hủ Bạch Khô Lâu. `40829` directly carries skill `23011 = Huyền vũ`, matching the public Skeleton King/Huyền Vũ warning.
- Stage18 candidate `40836..40843`: Bạo Ngược Vương + wolf group. `40836` has special `21042 = Gia Cát Cuồng Long Chú`, matching public stage18.
- Stage19 candidate `40844..40850`: Giảo Hoạt Vương/support group; includes Crazy Dragon/support-style skills, matching public stage19 Crazy Dragon + heal/status-clear notes.
- Stage20 candidate `40851..40859`: Lưu Bị BDY, Quan Vũ BDY, Trương Phi BDY, Dị Giới Hoàng Kim/Lưu Lang/Ôn Dịch/Hủ Hủ/Bạo Ngược/Giảo Hoạt. The set contains `21042` Crazy Dragon, `23011` Huyền Vũ and soul-drain/support mechanics, matching public stage20 behavior.

Earlier block progression also strongly looks stage-structured:
- `40687..40700`: early Hồn groups.
- `40701..40707`: pirate group.
- `40708..40717`: Yellow-Turban-themed group.
- `40718..40727`, `40728..40737`, `40738..40747`: progressively stronger Hồn groups.
- `40757..40767`, `40768..40777`, `40778..40822`: later special groups before the high-confidence stage16 anchor.
Exact stage1..15 boundaries are NOT yet locked and must not be hard-coded as DVT truth.

### Hidden formation-table scan
- `IOS_MarkGroupData`, `PassAward`, `RequiemBoss`, `ActivityInfo`, `MissionAward`, `GS` do not reference the high-confidence weekly NPC anchors.
- `IOS_SceneFight_C.dat` remains confirmed to contain no scene62015.
- `IOS_WeekDungeon_C.dat` remains header-only.
- `IOS_Festival.dat` contains scattered occurrences of several weekly NPC IDs but also many repeated byte-pattern hits; this is not yet evidence of a formation table and must be parsed by its own real format before use.
- `IOS_MarkStep_C.dat` contains many raw occurrences of one Bách Chiến NPC ID but not the Crazy anchors; raw UInt16 coincidence alone is insufficient.

### Current safety boundary
- Do not enable kind3/kind5 combat merely by removing the existing kind1 validation.
- kind3 Bách Chiến NPC membership is high-confidence, but exact position/order plus special round outcomes (stage4 max3, stage5 survive10, stage19 max20) still need a dedicated special-battle implementation.
- kind5 is additionally blocked on Soul qualification kinds8/9/10 because the Kotlin server does not yet have sufficient equipped-Soul element/level state.
- DVT client directly proves `EFFWar_Style.WeekDungeon = 24`; when combat is implemented it needs a separate multiplayer special-battle path using warStyle24, not ordinary Eve PvE.

### START NEXT
1. Get an executable CI/runner result for `611e091`; repair compile/tests before any production deploy.
2. Parse/identify any remaining server-like table that references the `40687..40859` Crazy block; otherwise keep reconstruction explicitly labeled.
3. Add data-only regression locking Bách Chiến stage18/19/20 and Crazy stage8/16/17/18/19/20 anchors.
4. Implement server-side weekly qualification kinds1..5 only where current Pet/Npc state has exact DVT semantics; keep Soul kinds8..10 blocked.
5. Only then build the WeekDungeon=24 battle lifecycle and its round constraints/reward/progress settlement.

## 2026-10-04 — Dungeon F4-B1 — weekly round objective data contract — PASS

### Evidence
- Lua DVT `UIDungeon.ENeedKindTag` defines `kind=6 MaxRound -- 回合數(上限)` and `kind=7 LimitRound -- 回合數(下限)`.
- Therefore kind6 is an upper-bound round requirement and kind7 is a lower-bound round requirement at the data-contract level.
- Exact server-side pass/fail comparison timing/equality at battle settlement is still not exposed by Lua/client and is NOT activated in this milestone.
- Real `IOS_Dungeon_C.dat` anchors:
  - dungeon id21 / Bách Chiến stage4 => kind6 param3.
  - dungeon id22 / stage5 => kind7 param10.
  - dungeon id36 / stage19 => kind6 param20.
  - Soul weekly id81 => kind6 param20, but Soul entry/combat remains blocked independently.

### Implementation
- Added pure, unwired `DvtWeeklyRoundObjective`.
- It decodes only weekly kind3/kind5 `DungeonData.needPar` into nullable `maxRound` / `minRound`.
- Nonpositive parameters are ignored; ordinary dungeons never receive weekly objectives.
- No battle settlement, automatic victory/failure, progress/reward mutation, formation placement or Soul behavior was changed.

### Validation
- Added `DungeonWeeklyRoundObjectiveTest` using real tracked `IOS_Dungeon_C.dat`.
- Targeted test: BUILD SUCCESSFUL, 18/18 Gradle tasks.
- Full local `gradlew test build --rerun-tasks`: BUILD SUCCESSFUL, 21/21 tasks.
- `git diff --check`: PASS.
- Next blocker remains authoritative WeekDungeon formation placement/order and exact round-settlement boundary before kind3 combat can be wired.

## 2026-10-04 — Dungeon F4-B2 — WeekDungeon style + order-safe membership API — PASS

### Evidence / safety boundary
- DVT `FightManager.lua` directly defines `EFFWar_Style.WeekDungeon = 24`.
- No audited DVT Lua/data/backend source exposes exact front/back/row positions for Bách Chiến.
- Public/server-source searches did not recover a stronger DVT-specific formation table. PC source still only provides generic 5+5 list placement and therefore remains insufficient to declare DVT weekly order.
- The reconstructed Bách Chiến roster membership remains high-confidence, but membership must not be treated as positional order.

### Implementation
- Added `EWarStyle.WEEK_DUNGEON = 24` from exact DVT Lua.
- Added pure, unwired `DvtBachChienMembership`.
- The helper returns a `Set<Int>`, intentionally preventing list order from being interpreted as front/back placement.
- Stages1..19 map to ten-NPC sets beginning at 40258; stage20 maps to 40448..40455 + 40480..40481.
- No battle creation, placement, weekly progress/reward, or Soul behavior was enabled.

### Validation
- Extended `DungeonWeeklyFormationDataTest` to lock warStyle24 and helper membership for all20 stages while continuing to assert real DVT NPC rows/names/elements.
- Targeted formation test: BUILD SUCCESSFUL, 18/18 Gradle tasks.
- Full local `gradlew test build --rerun-tasks`: BUILD SUCCESSFUL, 21/21 tasks.
- `git diff --check`: PASS.
- Remaining activation blocker: authoritative positional formation plus exact round-objective settlement comparison.

## 2026-10-04 — Dungeon leave HUD recovery — PASS

### Root cause
- DVT UIMain "Leave Dungeon" button calls `Dungeon.LeaveSinglePlayDungeon()` -> C:013-004, not C:047-010.
- Server already intercepted C:013-004 only when an in-memory `ActiveDungeonRun` existed.
- `activeRuns` is volatile; after server restart/relog, or any dungeon entry path that did not mark the run, the HUD button still appeared on a dungeon scene but C:013-004 fell through to ordinary party leave and effectively did nothing.

### Fix
- Added `DungeonSystem.exitDungeonFromHud(session)`.
- C:013-004 is intercepted only while the player is physically inside a scene referenced by `DungeonData.sceneId`, so ordinary party leave outside dungeons is preserved.
- If a matching active run exists, return to the exact captured pre-entry map/x/y/instance.
- If the run was lost, fallback uses DVT Eve `sceneInfo.backgroundNo + playerAppearX/Y` through `SpecialSceneTeleportService.exitSpecialScene()`; no hard-coded return map.
- PartyHandler now routes C:013-004 through this robust HUD exit path first.

### Validation
- Extended `DungeonRewardLeaveRegressionTest`:
  - active-run leave still returns to captured origin;
  - lost-run/restart fallback exits through Eve sceneInfo;
  - C:013-004 inside dungeon prefers dungeon exit over party leave;
  - C:013-004 outside dungeon still falls through to PartySystem.
- Targeted regression: BUILD SUCCESSFUL, 18/18 tasks.
- Full local `gradlew test build --rerun-tasks`: BUILD SUCCESSFUL, 21/21 tasks.
- `git diff --check`: PASS.

## 2026-10-04 — Dungeon HUD exit follows SceneSet.kind=2 — PASS

### Root cause correction
- DVT `UIMain.UpdateDungeon()` shows the HUD dungeon panel when `sceneDatas[SceneManager.sceneId].kind == 2`.
- The HUD "Leave Dungeon" button calls `Dungeon.LeaveSinglePlayDungeon()` -> C:013-004.
- Previous fix `1ccaa00` incorrectly gated fallback leave on whether the current map appeared in `IOS_Dungeon_C.dat`.
- Dynamic dungeon scenes can be `SceneSet.kind == 2` without being listed as a `DungeonData.sceneId`, so C:013-004 could still fall through and do nothing.

### Fix
- `DungeonSystem` now receives the authoritative `GameDataManager.sceneSetDefs`.
- `exitDungeonFromHud()` gates on `sceneSetDefs[currentMap].kind == 2`, matching Lua DVT exactly.
- If an `ActiveDungeonRun` still exists, it returns to the exact captured pre-entry map/x/y/instance.
- If the run is missing after restart/relog, it exits through DVT Eve `sceneInfo.backgroundNo + playerAppearX/Y`.
- Outside scene kind2, C:013-004 still falls through to normal PartySystem leave.

### Validation
- Regression now explicitly selects a real `SceneSet_C.dat` kind2 scene that is NOT present in `IOS_Dungeon_C.dat` and verifies HUD exit succeeds through Eve fallback.
- Targeted `DungeonRewardLeaveRegressionTest`: BUILD SUCCESSFUL, 18/18 tasks.
- Full local `gradlew test build --rerun-tasks`: BUILD SUCCESSFUL, 21/21 tasks.
- `git diff --check`: PASS.

## 2026-10-04 — Dungeon lifecycle timer + durable leave origin — PASS local

### Root cause confirmed
- DVT UIMain shows dungeon HUD for SceneSet.kind=2 and displays time from Dungeon.nowDungeonId + S:047-018 startTime.
- Lua Dungeon.UpdateTime only updates/stops the client timer; it does NOT eject the player at zero. Timeout eject is backend responsibility.
- S:047-018 reconnect contract: Double startTime; when client nowDungeonId==0 it additionally reads UInt16 dungeonId.
- Scene 62003 / dungeon 6 has one Eve Door but zero SceneInfo and zero resultType=2 exit. Therefore a backend restart cannot reconstruct the exact return map from Eve; server must persist the pre-entry origin.

### Implementation
- Added durable CharacterDungeonRuns / DungeonRunRepository keyed by characterId.
- Persisted state: dungeonId, OLE startTime, Unix startedAtMillis, instanceId, origin map/x/y/instance, settled.
- Single and multiplayer runs persist the exact origin before teleport.
- C:012-001 scene-ready restores ActiveDungeonRun from DB after reconnect/server restart, preserving original startTime/deadline and re-sending S:047-018 + dungeonId.
- Server-side watchdog uses DungeonData.time and auto-exits at deadline; if battle is active it waits until battle cleanup is safe, then uses the same exit path.
- Leaving a dungeon clears both runtime and durable run state. Loading a non-dungeon scene also clears stale durable state.
- Legacy rescue for players already stranded before this schema existed:
  - first prefer authoritative Eve exit;
  - if the scene has no Eve exit (62003), use that character's own saved record point;
  - never hard-code a return map and never use a record point that is the same/dynamic dungeon scene.
- SpecialSceneTeleportService recovery SceneInfo coordinates now follow EveDoorWarpService's tile->pixel *20 contract.

### Validation
- Targeted DungeonActiveRun / DungeonRewardLeaveRegression / DungeonRoomPacket / DungeonEntryRegression: BUILD SUCCESSFUL, 18/18 tasks.
- Added restart regression: run saved by runtime A -> runtime B restores exact startTime/origin -> HUD exit returns to exact origin -> durable row cleared.
- Added DVT-data regression proving 62001/62002 have Eve exits but 62003 does not.
- Added legacy 62003 record-point rescue regression.
- Full local gradlew test build --rerun-tasks: BUILD SUCCESSFUL, 21/21 tasks.
- git diff --check: PASS (only CRLF normalization warnings).
- Production deployment/runtime verification is recorded immediately below.

## Production deploy c3504fe — VERIFIED
- Production source is exactly `c3504fe fix: persist dungeon lifecycle and timeout`.
- Production full Gradle test/build passed 21/21 tasks.
- Runtime restarted by the existing WMI launcher; Java game PID 8980 was created at 2026-10-04 20:17:35 +07 and runs `tw.tsm.app.MainKt`.
- Fresh Git Bash SSH verification: `0.0.0.0:6613` and `[::]:6613` LISTEN on PID 8980; recurring localhost health checks continue.
- Startup log at 20:17:36-20:17:40 confirms Hikari/MySQL connection, DatabaseFactory schema initialization, Dungeon/DungeonCombo/DungeonCriteria/Eve data load, Netty bind, then `Máy chủ đã sẵn sàng, đang chờ kết nối...`.
- No ERROR/Exception/FATAL was observed in this startup window or current runtime tail.
- Production-local `application.conf`, WMI launchers and deployment bundles remain intentionally untracked/preserved.

### Runtime client check next
- Relog/enter a timed DungeonData scene and confirm the HUD now shows numeric `Phút/Giây` from S:047-018.
- Press `Rời khỏi` -> `Có`; C:013-004 must return to the exact persisted pre-entry origin for new runs.
- For a character already stranded in legacy scene 62003 before c3504fe, recovery may use the character's saved record point because DVT Eve has no SceneInfo exit there.
- Timeout must auto-eject through the same exit path when DungeonData.time expires; production log should record the dungeon timeout/exit line.

## 2026-10-04 — Dungeon Eve GroupData weighted member selection — PASS local

### Root cause
- DVT `Eve_GroupData.lua` parses `MemberNoAy[]`, `ProbabilityRateAy[]` and `PickMember`; client does not execute GroupData, so selection is backend-side.
- Server incorrectly treated `PickMember=1` as literal `resultNo=1` before consulting weights.
- Full tracked Eve.emg audit: 9,289 groups; 2,794 groups have positive PickMember; every positive value is 1; 374 of those groups do not even contain member/resultNo 1. Therefore PickMember cannot mean a literal result number.
- Production scene 62007 log confirmed the symptom: door event advanced mission 12358 from step1->2, but no grouped class3 show-NPC result was dispatched.

### DVT dungeon evidence
- Treasure search dungeon id6 / scene62003 / mission12330 contains six hidden chest placements (npcId20001). Door event1 uses weighted groups 20:[2,3], 21:[4,5], 22:[6,7], 11:[8..13], all PickMember=1, to send class3/pStyle4 show results. Wrong filtering explains entering with no treasure chest visible.
- Find BaDauYeu dungeon id10 / scene62007 / mission12358 contains six hidden positions of npcId22087. After entry, mission step1->2; each successful find/battle advances 2->3->4->5. While step<5, class3 pStyle3 hides the current position and weighted pStyle4 group selection reveals one valid next position. At step5 it completes mission12359.
- Higher BaDauYeu tiers use the same contract in scenes62008..62010 with mission flags12362/12366/12370.

### Fix
- `EveConditionResolver.selectMember()` no longer returns `pickMember` as a result number.
- Weighted groups now choose one actual member from `MemberNoAy` using `ProbabilityRateAy`.
- A single-member zero-weight group selects its only member; unknown multi-member zero-weight groups remain fail-open/preserve-all rather than guessing.
- Updated `EveGroupData.pickMember` documentation to record that DVT uses it as a pick-count flag (0/1), not resultNo.

### Validation
- Added `EveGroupDataRegressionTest` against real tracked Eve.emg:
  - scene62003 entry retains exactly one show result from each weighted treasure group;
  - scene62007 relocation retains exactly one valid next-location result;
  - positive PickMember corpus is {1} and includes groups where resultNo1 is absent.
- Targeted regression: BUILD SUCCESSFUL, 18/18 tasks.
- Full local `gradlew test build --rerun-tasks`: BUILD SUCCESSFUL, 21/21 tasks.
- Fresh production trace on old runtime confirms scene62007 symptom exactly: Door event at mission12358 step1 sends show param1, then hide param1 and advances 1->2, but emits no grouped pStyle4 next-position result; subsequent ClickNpc trigger6 cannot progress the scripted search.
- User also confirmed scene62003 enters with no visible treasure chest; this matches the same lost weighted show-results path.
- Production deployment pending below.

## 2026-10-05 — Treasure progression follow-up after c0dd255

- User runtime test on scene62003: four chests now appear and are clickable, but HUD/dialog remained at “found 1” after opening additional chests.
- Fresh production trace proves entry flow is correct: mission12330 starts at 1, auto-entry Eve1 shows chests and advances 1->2.
- First opened chest (clickId4 / Eve7) correctly executes condition1, hides the chest and advances mission12330 2->3.
- At mission12330=3, later chest clicks (for example clickId6 / Eve9) incorrectly selected condition2 (step==3 talk) instead of condition1 (hide + mission +1), so mission stayed at 3 and the UI count stayed at 1.
- Root cause: ClickNpc only used Eve data-order for battle/class8 state machines. Treasure events 5..10 are also progressive multi-phase state machines but contain no battle.
- Fix: detect ClickNpc events whose early result increments a mission (class2/pStyle1/value>0) and later conditions read higher steps of the same mission; resolve these events in Eve.emg data order and do not apply completed-condition blocking to their reusable progression head.
- Regression now locks the three treasure transitions: step2->3 then count branch, step3->4 then count branch, step4->5 then completion branch; final branch must complete mission12330 and advance mission12331.
- Targeted DungeonMiniGameFlowRegressionTest: BUILD SUCCESSFUL, 18/18 tasks.
- Full local gradlew test build --rerun-tasks: BUILD SUCCESSFUL, 21/21 tasks.

## 2026-10-04 — Production deploy Eve GroupData fix 2ef4aed — VERIFIED

- Commit: `2ef4aed fix: honor DVT Eve group member selection`; pushed to `origin/main`.
- Production source fast-forwarded to `2ef4aed` via Git bundle/Git Bash SSH.
- Targeted `EveGroupDataRegressionTest + DungeonEntryRegressionTest`: BUILD SUCCESSFUL, 18/18 tasks.
- Full local `gradlew test build --rerun-tasks`: BUILD SUCCESSFUL, 21/21 tasks.
- Full production `gradlew test build --rerun-tasks`: BUILD SUCCESSFUL, 21/21 tasks (4m39s).
- Runtime restarted after production build. New game process PID 6392 was created at 2026-10-04 22:21:33 +07.
- Fresh verification: production HEAD `2ef4aed`; port 6613 LISTENING on PID 6392.
- Startup 22:21:34-22:21:39: MySQL/Hikari + schema init PASS; DVT data including IOS_Dungeon / DungeonCombo / DungeonCriteria / Eve loaded; Netty bound 0.0.0.0:6613; server READY.
- Runtime verification pending user gameplay:
  - scene62007 / dungeon10 Tìm Ba Đậu Yêu: entry must reveal a valid first/next hidden NPC position, then each successful find must hide current, reveal one weighted next position, progress mission12358 2->3->4->5, and complete after the third find.
  - scene62003 / dungeon6 Tìm bảo rương: entry weighted GroupData must emit real class3/pStyle4 show results so treasure objects become visible; no longer lose all group members through PickMember=1 misinterpretation.
- Do not hard-code these scenes separately: fix is generic Eve GroupData semantics and applies to the same DVT group mechanism elsewhere.

## 2026-10-04 — Dungeon mini-game lifecycle phase 2 — PASS local, production pending

### User retest invalidated previous gameplay PASS assumption
- User retested production commit `2ef4aed` and confirmed both scene62007 Tìm Ba Đậu Yêu and scene62003 Tìm bảo rương were still not scripted correctly.
- Treat `2ef4aed` as a necessary GroupData fix only, NOT a complete mini-game gameplay PASS.

### Fresh production trace root causes
- Scene62007 at 23:47-23:48 proves GroupData selection now works: entry showed clickId2; clicking it hid clickId2 and selected weighted next clickId6.
- However event2 skipped its Talk/Battle branch because generic Eve resolver chose condition8 (mission12358 step<5, higher stepScore) over condition1 (mission12358 step>1). Mission stayed at step2.
- Real Eve event2 is a multi-phase state machine: cond1 Talk/Battle -> cond2 class8 WIN + mission increment -> cond5/6 step talk -> cond8 hide/current + weighted show next; step5 uses cond7 terminal completion.
- Scene62003 at 23:50-23:52 loaded with mission12330 step1 but no Door entry event ran. Thus no weighted class3/pStyle4 chest show results were sent; only guard ServerBump battles ran.

### Fix design
- Keep global `EveChainResolver.resolve()` heuristic unchanged for ordinary quests.
- Add `resolveInDataOrder()` / `resolveEventInDataOrder()` for multi-phase state-machine continuation.
- ClickNpc events containing both a Battle result and class8 post-battle conditions use data-order resolution; ordinary Eve events keep existing resolver.
- Same-Eve auto-chain continues after the completed condition in data order and bypasses completed-event blocking for that continuation.
- Same-Eve continuation stops after any resultClass2/pStyle3 COMPLETE_MISSION so terminal mini-game chains cannot reopen after mission step is removed to 0.
- Add generic DungeonData scene-ready entry hook: after map init, trigger only a Door event whose WhenHappen[1] condition and mission-add result both match the active DungeonData.missionFlag. No scene ID is hard-coded; reconnect at later mission steps naturally fails the entry condition.

### DVT-data regressions
- Added `DungeonMiniGameFlowRegressionTest` against tracked Eve.emg.
- scene62007/event2 locks sequence cond1 battle -> cond2 win/increment -> cond5 step3 talk -> cond8 hide + exactly one weighted next position.
- scene62003/event1 locks exactly four visible chest show results plus mission12330 add1.
- Relocate regression verifies scene-ready calls dungeon entry trigger using DungeonDef.missionFlag.
- Auto-chain regression verifies pStyle3 mission completion stops same-Eve continuation.
- Targeted mini-game/Eve suite: BUILD SUCCESSFUL, 18/18 tasks.
- Full local `gradlew test build --rerun-tasks`: BUILD SUCCESSFUL, 21/21 tasks.
- `git diff --check`: PASS (CRLF normalization warning only).
- Production deployment is pending; do not mark client gameplay PASS until user retests after this phase-2 commit.

## 2026-10-05 — Production deploy c0dd255 — VERIFIED, client retest pending

- Gameplay commit: `c0dd255 fix: restore DVT dungeon mini-game flow`; pushed to `origin/main` and fast-forwarded to production.
- Full production `gradlew test build --rerun-tasks`: BUILD SUCCESSFUL, 21/21 tasks.
- First restart attempt killed game child PID 6392 while the old Gradle `app:run` wrapper was still alive; the new WMI launch did not remain running. Port 6613 was temporarily offline. This was an operational launcher/wrapper issue, not a c0dd255 application crash.
- Verified no Java game/wrapper remained, then relaunched cleanly with existing `_launch_prod_wmi.cmd`.
- Current production runtime:
  - source HEAD `c0dd255`;
  - Gradle wrapper `app:run` PID 12044;
  - game `tw.tsm.app.MainKt` PID 10920;
  - `0.0.0.0:6613` and `[::]:6613` LISTEN on PID 10920.
- Fresh startup 00:25:15-00:25:19 +07 confirms Hikari/MySQL, schema init, all DVT data including Dungeon/DungeonCombo/DungeonCriteria/Eve, Netty bind and server READY.
- Client gameplay still requires user validation; do NOT mark the two mini-games PASS until tested:
  - scene62007 Tìm Ba Đậu Yêu should auto-run entry script on scene-ready, then each found NPC must Talk/Battle, win increments mission12358, step-specific talk runs, current NPC hides and one weighted next NPC shows; third win terminates without a fourth spawn.
  - scene62003 Tìm bảo rương should auto-run Door entry script on scene-ready, show four weighted treasure chest placements and advance mission12330 1->2; subsequent chest clicks should advance the same EveNo phases.

## 2026-10-05 — Treasure reward settlement follow-up

- User runtime test confirmed treasure finding/count progression is now correct, but no completion reward was granted.
- Fresh production trace of the successful run proves the terminal Eve path:
  - mission12330 4->5;
  - pStyle3 completes/removes mission12330;
  - Eve advances mission12321 +1 and mission12331 0->1;
  - no item/reward settlement occurred before leaving the dungeon.
- DVT DungeonData id6: kind2, scene62003, missionFlag=12330, dayilyFlag=12344, dayilyCount=1, time=10 minutes, lootTable=7, extraLoot=0.
- DVT DungeonCombo lootTable7 has three remaining-time rows: kindValue 200/400/600 seconds. For turn0 the rewards are 45661 x1, 45662 x1, 45664 x1 respectively; turn1/2/3 use their configured rebirth bags via DungeonComboData:GetReward fallback semantics.
- Existing DungeonSystem.resolveRewards already matched DVT; existing settleAfterBattle already performed exact bag mutation and S:047 result construction.
- Root cause: settleAfterBattle was invoked only from BattleManager.onBattleEnd. Treasure dungeon completes through a ClickNpc/Eve chain with no terminal battle, so it never entered settlement.
- Fix in progress: EveFlowController exposes an app-layer completion hook only after the full Eve chain/auto-chain is terminal. ServerModule routes that hook to DungeonSystem.settleAfterEvent(), which reuses the existing settlement/reward path. Mission still active => no-op, so intermediate chest events cannot award.
- Successful settlement also increments DungeonData.dayilyFlag (12344 for dungeon6) exactly once and syncs the mission step, because UIDungeon reads this Mark to derive remaining daily attempts while the Eve script itself never mutates it.
- Idempotence remains protected by ActiveDungeonRun.settled, so the later BattleManager settlement callback cannot double-award.
- Added regression for dungeon6 non-battle settlement: with 500 seconds remaining and turn0 it awards item45664 x1, increments dailyFlag12344, marks the run settled, and a second settlement attempt returns null.
- Added EveFlowController regression proving a terminal non-battle event invokes the completion hook exactly once.
- Targeted reward/mini-game/callback suite: BUILD SUCCESSFUL, 18/18 tasks.
- Full local `gradlew test build --rerun-tasks`: BUILD SUCCESSFUL, 21/21 tasks.
- Production deployment pending below.

## 2026-10-05 — Production deploy treasure progression e5f5f4c — VERIFIED

- Commit: `e5f5f4c fix: keep treasure dungeon progression in Eve order`; pushed to `origin/main`.
- Production source fast-forwarded to `e5f5f4c` via Git bundle/Git Bash SSH.
- Targeted `DungeonMiniGameFlowRegressionTest`: BUILD SUCCESSFUL, 18/18 tasks.
- Full local `gradlew test build --rerun-tasks`: BUILD SUCCESSFUL, 21/21 tasks.
- Full production `gradlew test build --rerun-tasks`: BUILD SUCCESSFUL, 21/21 tasks (3m16s).
- First restart attempt happened while the previous Gradle `app:run` was still tearing down after game PID termination, so it produced no live Java process; no gameplay validation was claimed from that attempt.
- After the old Gradle run fully exited, the existing WMI launcher was invoked again from a clean state.
- Fresh runtime: game Java PID 3280, created at 2026-10-05 01:07:27 +07; port 6613 LISTENING on IPv4 and IPv6.
- Startup 01:07:27-01:07:31 confirms Hikari/MySQL, schema init, DVT data/Eve load, Netty bind 0.0.0.0:6613, then `Máy chủ đã sẵn sàng, đang chờ kết nối...`.
- Runtime gameplay verification still pending user retest of scene62003:
  - entry must set mission12330 1->2 and show treasure chests;
  - first distinct chest: 2->3 and “found 1”;
  - second distinct chest: 3->4 and “found 2”;
  - third distinct chest: 4->5 then completion branch must complete 12330 and advance 12331.

## 2026-10-05 — Production deploy non-battle dungeon settlement d4387a9 — VERIFIED

- Commit: `d4387a9 fix: settle non-battle dungeon completion`; pushed to `origin/main`.
- Production source fast-forwarded to `d4387a9` via Git bundle/Git Bash SSH.
- Targeted reward + mini-game + Eve completion callback suite: BUILD SUCCESSFUL, 18/18 tasks.
- Full local `gradlew test build --rerun-tasks`: BUILD SUCCESSFUL, 21/21 tasks.
- Full production `gradlew test build --rerun-tasks`: BUILD SUCCESSFUL, 21/21 tasks (2m54s).
- Restart was performed safely: game PID 3280 stopped first; old Gradle wrapper PID 12424 was verified fully exited and port 6613 released before invoking the WMI launcher.
- Fresh runtime: Gradle app:run wrapper PID 6484, game `tw.tsm.app.MainKt` PID 6152, created at 2026-10-05 01:42:38 +07.
- Fresh verification: production HEAD `d4387a9`; `0.0.0.0:6613` and `[::]:6613` LISTEN on PID 6152.
- Startup 01:42:38-01:42:43 confirms Hikari/MySQL, schema init, Dungeon/DungeonCombo/DungeonCriteria/Eve data load, Netty bind, then `Máy chủ đã sẵn sàng, đang chờ kết nối...`; no ERROR/Exception/FATAL observed in this startup window.
- Runtime gameplay validation is still pending user retest; do NOT mark reward PASS yet.
- Expected scene62003 success path after retest:
  - complete three treasure chests and terminal Eve mission12330;
  - completion hook calls DungeonSystem.settleAfterEvent;
  - reward selected from lootTable7 by remaining time and player turn;
  - for turn0 with >=400 seconds remaining: item45664 `PhóBản lễ túi 4` x1;
  - dailyFlag12344 increments by 1 so remaining attempts becomes 0/1 for the day;
  - S:047-012 result packet carries the reward list;
  - server log should contain `Dungeon 6 settled: remaining=... rewards=... dailyFlag=12344`.

## 2026-10-05 — Treasure reward production retest — SERVER RUNTIME PASS

- Read the entire checkpoint before continuing. Verified that gameplay fix d4387a9 was built and restarted before this retest; c13f469 only adds documentation.
- The previous missing-reward trace predates that restart. A fresh production run now confirms three distinct chest clicks advance mission12330 2->3->4->5, terminal Eve completes12330 and increments12321/12331, then the non-battle completion hook settles the reward.
- Actual settlement: remaining457s, turn0, item45664 x1; dailyFlag12344 advances0->1. Reward settlement is SERVER RUNTIME PASS; user confirmation of client dialog/bag visibility remains separate.
- Normal dungeon6 uses DungeonData lootTable7 / DungeonComboData, including Lua rebirth fallback. EternalDungeonPrizeData is for the separate Eternal subsystem. Kotlin S47-12 item UInt16/quantity UInt32 matches Lua. No reward or formula was changed or invented.
- Dungeon/Combo production data match regression input. Packed Eve files differ between environments; do not claim whole-file identity. The actual production trace independently confirms the tested treasure terminal behavior.
- Fresh targeted regression: BridgeEventRelease3 + DungeonF3BData4 + DungeonMiniGameFlowRegression3 + DungeonRewardLeaveRegression12 =22 tests, zero failures/errors; BUILD SUCCESSFUL18/18 tasks; diff check PASS. Existing full build/startup PASS remains valid; no new gameplay patch or restart is needed.
- Existing untracked scratch/bundles/staging helper were preserved and excluded. Restored only the tracked compiler session file removed by this test run.
- Next: record user confirmation of client reward display; preserve 3-chest/progression PASS and d4387a9 gameplay baseline.

## 2026-10-05 — Ba Dau Yeu hidden path interaction fix — BUILD PASS, runtime retest pending

- Symptom: dungeon entry selects roaming clickId=6 and mission advances to step 2, but repeated clicks do not start the event.
- DVT contract: Role.MapNpcAppear starts motionType=4 paths locally; hidden RoleController.UpdateMove pauses. S:022-002 reveals the NPC and replaces movement, so server patrol conflicts with scripted hide/show and client path position.
- Kotlin fix: exclude initially hidden path NPCs without trace encounters from server patrol; accept clicks within 200 pixels of the Eve path segments (including initial spawn), while retaining ordinary distance checks and rejecting positions away from the route. No reward/data/Lua changes.
- Regression: all Ba Dau level scenes 62007..62010 retain client ownership, path vertices/interiors are clickable, remote/standing/encounter cases stay bounded. Targeted suite 20/20 PASS; app build PASS.
- Runtime verification and player completion remain pending deployment/retest. GitHub push remains blocked by automatic approval review pending confirmation of the remote destination.

### Ba Dau deployment verification

- Production source is gameplay commit `48c7aec`; app build and the same 20 targeted tests passed on production.
- Restart completed through the established detached WMI launcher after old game and app wrapper exited. Fresh connections confirm the new game owns the configured listener, the new NpcClientPath class was compiled, and startup is ready without runtime errors.
- Player was asked to enter Ba Dau again after restart. No post-deploy gameplay trace yet; do not mark the three finds or client visibility PASS until observed.
- Dev tracked working tree is clean; pre-existing untracked bundles/scratch/tools are preserved. GitHub push still awaits trusted-remote confirmation after automatic approval rejection.

### Ba Dau post-deploy live retest — SERVER RUNTIME PASS

- New player run after restart selected clickIds 2, 6 (roaming), then 4. Each click opened the dialogue and battle; mission12358 advanced 2→3→4→5.
- The final branch completed mission12358, incremented12359 from0→1, and reward settlement logged `Dungeon 10 settled: remaining=539s turn=0 rewards=[(45664, 1)] dailyFlag=12360`.
- This reproduces and clears the previously blocked roaming clickId6. All three finds, terminal completion and server reward issuance now have post-deploy evidence. Client bag/dialog receipt has not been explicitly confirmed by the player.
- Gameplay runtime remains commit48c7aec; later commits only synchronize checkpoint. The focused20/20 tests and production build passed before restart.

## 2026-10-05 — Weekly and Soul dungeon continuation — entry blocker confirmed, evidence required

- User explicitly confirms Ba Dau PASS, then requests Bách chiến thiên quan and Khiêu chiến Linh Hồn; symptom: clicking a stage does not enter.
- Verified production source df7b234 and live listener from the last successful deployment; no new runtime patch/restart during this audit.
- Exact blocker: DungeonSystem.validateNormalRoomEntry accepts only kind1. kind3/5 requests return RESULT_NO_SPACE before room creation. This is the deliberate missing WeekDungeon battle implementation described in earlier checkpoints, not a new dungeon entry regression.
- Real DungeonData: Bách Chiến stage1 is id18/kind3/scene62015/missionFlag0, maxPlayer5, level1..450, dailyFlag12722, lootTable82. First Soul stage id63/kind5 requires level50..450 plus needPar(8,1),(5,10004). The last observed character was level36; level requirements must remain data-driven.
- All found WeekDungeon data copies are header-only (4 bytes); available sources do not supply battle formation records. Membership helper remains reconstructed and unwired, not authoritative placement.
- Additional direct gameplay reference: https://www.youtube.com/watch?v=_9Gf1vtFBS8 (2019-05-22, original recording). Visible stage1 has Wei Bing/An Lu Na-style enemies around level50; candidate40258..40267 in current DVT data instead contains Hac Manh/Lu Bu/Chen Gong etc at110..120. This may be a version difference; it cannot establish current DVT formation or validate the candidate stage mapping. Do not promote this video or candidate mapping to authoritative current data.
- Rechecked both luaDVT and active Lua Dungeon.CheckForQualification: `kind ~= 3 or kind ~= 5` returns true for every kind, leaving later checks unreachable. This client bug does NOT prove server qualification should be bypassed; needPar and the server-side contract still need correct handling. Do not change Lua to fit Kotlin.
- Audit-only scratch parsed real Npc/Dungeon data successfully and was removed. No gameplay/reward/qualification behavior changed. Existing regression files and prior PASS runtime are preserved.
- Asked user for original DVT backend path, original battle log, or matching-version video to recover per-stage formation. Next: resolve exact NPC IDs/slots, dedicated warStyle24 multiplayer battle, round limits, progress/reward, then Soul120 state/qualification. Do not enable entry to an empty unsupported scene.

## 2026-10-05 — SESSION HANDOFF — resume weekly/Soul implementation

### User decisions and scope
- User confirms Tìm Ba Đậu Yêu PASS and wants to continue Bách chiến thiên quan + Khiêu chiến Linh Hồn; both fail to enter when clicking a stage.
- User has NO original DVT server, original battle logs, or matching-version video to provide. Do not repeat that question as if unanswered.
- User explicitly waived the exclusive-machine-access rule earlier (`ko cần quy định đó nữa`). Local PowerShell/Git Bash SSH has been used with authorized production access. This waiver does not authorize invented rewards or fabricated DVT formations.
- User now asks to save the entire progress for the next chat. Next chat MUST read the complete checkpoint before continuing.

### Verified PASS and production baseline
- Treasure dungeon6/scene62003: three chests and progression PASS; fresh trace mission12330 2→3→4→5, complete12330, increase12321/12331, settlement remaining457s/item45664×1/dailyFlag12344. Reward gameplay fix d4387a9 was built/restarted before that trace.
- Ba Dau dungeon10/scene62007: gameplay48c7aec preserves hidden client-owned motionType4 paths and bounds clicks to Eve route segments. Patched files: NpcClientPath.kt, NpcPatrolSystem.kt, EveTriggerRouter.kt; regression BaDauNpcMovementRegressionTest.kt.
- Live retest clickIds2→6(roaming)→4; mission12358 2→3→4→5, complete12358,12359 0→1; settlement remaining539s/item45664×1/dailyFlag12360. User explicitly says PASS. Do not reopen this issue without new evidence.
- Dev and production app builds PASS; targeted20/20 tests PASS (movement3, mini-game3, reward12, state-builder2).
- Last verified production source df7b234, gameplay48c7aec; gamePID7088 owns port6613, startup02:13:34+07. Later docs do not require restart. Production path C:\Server-Game\tsm_dodo on192.168.1.69; dev D:\Games\Ts-Offline\TSMobileOnLine\tsm_dodo_chatgpt_main / feature/9-pet.

### Current blockers and next work
- Weekly create is explicitly rejected by validateNormalRoomEntry kind1-only guard; removing that guard alone would leave unsupported combat.
- Bách Chiến stages id18..37 kind3, scene62015; first stage has no missionFlag and needs a dedicated warStyle24 multiplayer battle, per-stage formation, round objective enforcement, success/failure settlement and data-driven loot/progress.
- Soul stages start id63 kind5, require level50 and Soul/skill qualifications; latest observed character level36. Soul protocol120 equipped state is not implemented sufficiently. Retain real level restrictions.
- DvtBachChienMembership / DvtWeeklyRoundObjective / DvtWeeklyQualification are evidence helpers, not complete wired gameplay. Existing inferred stage memberships must not be promoted to verified slot/order data. Historical video stage1 differs from inferred candidate; version applicability remains unresolved.
- Lua qualification OR guard always returns true in both current source copies; this is a client bug, not evidence for bypassing server restrictions. No Lua/data/reward edits made.
- Further local/public searches did not recover a matching original formation table. Original WeekDungeon data copies are4-byte headers. No new weekly/Soul gameplay patch has been deployed; do not claim these modes PASS.

### Git, runtime operations and clean-up
- Latest pre-handoff dev commit1b4bb59 is audit documentation; it was NOT deployed. Production remainsdf7b234. Tracked dev tree clean; pre-existing untracked scratch/bundles/staging helper preserved.
- GitHub push was rejected twice by automatic approval review because the remote destination was not confirmed trusted. Human confirmation of BauTroiDo/tsm-dodo-server ownership/trust and main push is still absent. Do not bypass the rejection via another upload path.
- Use Android Studio JBR E:\Soft\AndroidStudio\jbr\bin on dev and explicit GRADLE_USER_HOME=C:\Users\QT\.gradle for builds. Build can remove tracked .kotlin/sessions/kotlin-compiler-10369008480978718564.salive; restore only that exact unrelated cache file afterward.
- Production restart only after build/test PASS: terminate old game and app:run wrapper, verify both exited, then existing _launch_prod_wmi.cmd → _start_prod_detached.cmd, reconnect SSH and check newPID/listener/log. Preserve production application.conf.
- Temporary BaDauProductionAuditScratchTest and WeeklyAuditScratchTest created in this session were removed. Read-only production Eve snapshot remains in this chat workspace work/production-eve.emg. Production packed Eve hash differs from test fixture; do not assert whole-file identity.


## 2026-10-05 — Soul Foundation A1 — DATA CONTRACT / LOADERS PASS

- Scope follows user-approved order: establish the Linh Hồn foundation before enabling Soul Challenge dungeon. Lua DVT remains authoritative; no Lua/data/reward edits.
- Authoritative client/data files locked:
  - `Logic/Soul.lua`, `Data/SoulData.lua`, `Data/SoulLevelData.lua`, `Data/NpcSoulData.lua`, `UI/UISoul.lua`, `UI/UISoulInfo.lua`, `Common/protocal.lua`.
  - `IOS_Soul_C.dat`, `IOS_SoulLevel_C.dat`, `IOS_NpcSoul_C.dat`.
- Real DVT data parsed byte-exact:
  - IOS_Soul_C: 4 records × 28 bytes, qualities 0..3 (N/R/SR/SSR), no trailing bytes.
  - IOS_SoulLevel_C: 300 records × 26 bytes, levels 1..300, no trailing bytes.
  - IOS_NpcSoul_C: 94 records × 5 bytes, item59501..59594 -> npc46415..46508; quality counts N41/R20/SR14/SSR19.
- Lua Soul formulas locked:
  - max level = basicLvLimit + starLv×20;
  - max breakthrough/star = 5 from all four rows;
  - final caps at star5: N200, R220, SR250, SSR300;
  - conversion percent = convertRate[starLv] / 10000;
  - converted six-stat value = floor((startAtt+nowAtt) × conversion percent).
- Protocol120 SoulThing wire locked: sn Int64, Id U16, element U8, equipId U16, lv U16, starLv U8, startAtt[6] U16, nowAtt[6] U16, 4×(skillId U16 + skillLv U8), lock U8. Quality is derived from item data, not serialized.
- Important separation: existing Kotlin `FollowNpcData.soulId` / `character_pets.soul_id` is DVT 魂玉ID (Hồn Ngọc / original ID before transformation) from protocol15, NOT protocol120 equipped Linh Hồn. Never reuse this field for Soul120 state.
- Soul Challenge qualification contract locked from `Dungeon.CheckForQualification`:
  - kind4 required skill, kind5 forbidden skill;
  - kind8 equipped Soul element equals parameter;
  - kind9 equipped Soul level >= parameter;
  - kind10 equipped Soul level <= parameter.
  - Existing client Lua OR guard is buggy/unreachable, but server must preserve the intended checks instead of bypassing them.
- Combat/client dependency confirmed:
  - RoleController includes equipped Soul skills; duplicate/base skills use the higher level where requested.
  - Battle RoleAppear consumes trailing U16 `soulEquipId`; Kotlin currently always serializes 0, so Soul battle avatar is not yet wired.
  - Client exposes converted six-stat formulas, but no authoritative original backend trace was found for the server-side stat settlement/update packet. Do not guess this part.
- Backend gap before this patch: no Soul handler, no protocol120 persistence/repository, no Soul static loaders, no login S120-008; DvtWeeklyQualification correctly leaves Soul-dependent kinds unsupported.
- Implemented evidence-safe A1 only:
  - `domain/.../SoulDefs.kt`;
  - `data/.../SoulDatLoaders.kt`;
  - GameDataManager wiring for all three IOS Soul tables;
  - `SoulFoundationDataRegressionTest.kt` against real DVT files.
- Validation:
  - targeted `SoulFoundationDataRegressionTest`: BUILD SUCCESSFUL;
  - `git diff --check`: PASS;
  - full `gradlew test build --rerun-tasks`: BUILD SUCCESSFUL, 21/21 tasks.
- No production deploy/restart for A1: loaders alone do not expose gameplay and protocol120 remains unwired.
- Next safe slice A2: separate protocol120 Soul persistence + login sync S120-008, then C120-001 equip/takeoff. Do not implement compound/intensify/breakthrough/affination or Soul dungeon rewards until their mutation semantics are separately locked.


## 2026-10-05 — Soul Foundation A2 — PERSISTENCE + LOGIN SYNC PASS

- A2 scope stays read-only from gameplay: persistent protocol120 SoulThing state, online cache and login init only. No C120 mutation handler, no stat bonus, no Soul Challenge entry change.
- Confirmed Lua `RoleCount.Get(index)` returns 0 for missing counters, so Soul bag use of RoleCount823 is safe without inventing a default DB row.
- Added dedicated protocol120 model/repository/table; it is deliberately separate from pet `soul_id` (protocol15 Hồn Ngọc/original NPC id).
- `character_souls` persists the exact S120 SoulThing fields: SN, itemId, element, equipId, level/star, 6 start stats, 6 current stats, 4 skill id/levels, lock flags.
- Added `SoulSystem` authoritative online snapshot with load/find/equippedForNpc/unload. A2 does not mutate or save gameplay state.
- Added opcode120 and `SoulResponseSender.sendSnapshot` for S:120-008:
  - header 120/8/0;
  - count UInt16LE;
  - exact 53-byte SoulThing rows;
  - trailing result UInt8; result=1 for login.
- LoginService now loads/sends Soul snapshot after RoleCount init, matching the client dependency on RoleCount823 for Soul bag capacity.
- DatabaseFactory creates/migrates CharacterSouls; ServerModule wires SoulRepository/SoulSystem and unloads the cache on disconnect.
- Regression:
  - one populated Soul snapshot is asserted byte-for-byte at 59 bytes;
  - empty login still emits [120,8,0,0,0,1];
  - SoulSystem load/find/equipped lookup/unload verified.
- Validation:
  - A1+A2 targeted suite BUILD SUCCESSFUL, 18 actionable tasks;
  - `git diff --check` PASS;
  - full `gradlew test build --rerun-tasks` BUILD SUCCESSFUL, 21/21 tasks.
- Gradle removed the tracked Kotlin compiler session file during build; restored only `.kotlin/sessions/kotlin-compiler-10369008480978718564.salive`.
- No production deploy/restart yet. A2 only makes login snapshot capable; gameplay still cannot equip/take off Souls.
- Next A3: lock exact C:120-001 / S:120-001 equip/takeoff semantics and validation from Lua DVT, then implement atomic persistence/cache mutation. Do not mix in compound/intensify/breakthrough/affination.


## 2026-10-05 — Soul Foundation A3 — EQUIP / TAKEOFF PASS

- Scope: implement only DVT C:120-001 / S:120-001 equip/takeoff on top of A1+A2. No compound/intensify/breakthrough/affination, no Soul stat settlement, no Soul Challenge entry enablement.
- Locked Lua DVT wire:
  - C:120-001 = kind U8 + npcId U16 + followIndex U8 + sn I64 = exactly 12 payload bytes.
  - kind1=equip, kind2=takeoff.
  - S:120-001 = kind U8 + npcId U16 + sn I64; followIndex is request-only.
  - S:120-255 = errorCode U8.
- Target validation follows UISoul/Soul.lua:
  - followIndex must resolve to a carried warrior;
  - packet npcId must match that exact follow slot;
  - eligible warrior requires canGrow != 0 and != 2;
  - Soul element must equal the target NPC element;
  - only one Soul may be equipped per npcId.
- State flags:
  - lockFlags bit0 = locked; DVT UISoul equip path does NOT pass checkLock, so locked Souls remain equippable.
  - lockFlags bit1 = mining; equip/takeoff rejects with protocol120 error4.
  - battle rejects before mutation with error26.
- Implemented:
  - SoulThingData helpers isLocked/isMining.
  - SoulSystem per-character mutex and atomic equip/takeoff mutation.
  - Persistence is written before cache publication; DB failure returns error255 and leaves cache unchanged.
  - Error mapping used from Lua comments: no player1, no Soul2, mining4, warrior ID7, target already has Soul8, equip/takeoff error9, element mismatch10, no eligible warrior12, battle26, generic255.
  - SoulHandler registered in PacketDispatcher/Koin.
  - SoulResponseSender/ResponseSender support S120-001 and S120-255.
- Switching behavior intentionally matches client flow: client sends takeoff, waits for S120-001, then sends a second equip request; server does not invent an atomic replace packet.
- Regression SoulA3EquipRegressionTest covers:
  - byte-exact S120-001 and S120-255;
  - followIndex/npcId/canGrow validation;
  - locked Soul may equip;
  - mining and element mismatch reject without DB write;
  - one-Soul-per-NPC rule;
  - takeoff ownership check;
  - persistence failure leaves cache unchanged;
  - handler parses exact 12-byte C120-001 and rejects battle.
- Validation:
  - targeted A1+A2+A3 Soul suite: BUILD SUCCESSFUL, 18 actionable tasks.
  - git diff --check: PASS.
  - full gradlew test build --rerun-tasks: BUILD SUCCESSFUL, 21/21 tasks.
- No production deploy/restart for A3. Production still does not have a verified DVT source for Soul acquisition, and stat settlement remains evidence-blocked.
- Next safe slice A4: wire equipped Soul state into the already-identified read-only consumers (qualification kind8/9/10 and battle appearance/soul skill lookup) only where the Lua contract is explicit; keep six-stat settlement blocked until authoritative evidence.


## 2026-10-05 — Soul Foundation A4 — READ-ONLY CONSUMERS PASS

- Scope: consume already-persisted/equipped protocol120 Soul state in explicit Lua DVT read paths only. No Soul acquisition, no six-stat settlement, no weekly/Soul dungeon battle enablement.
- Qualification evidence from Dungeon.CheckForQualification:
  - kind8 requires equipped Soul element == parameter.
  - kind9 requires equipped Soul level >= parameter.
  - kind10 requires equipped Soul level <= parameter.
  - kinds4/5 remain UNSUPPORTED in DvtWeeklyQualification because their full RoleController:GetSkillLv contract also includes special/upgrade/exclusive-weapon/passive skills; do not pretend partial state is complete.
- DvtWeeklyQualification now accepts optional equippedSoul and evaluates kinds1/2/3/8/9/10. It requires the Soul equipId to match the deployed warrior npcId.
- This qualification helper is still NOT wired to weekly room creation/runtime. Existing kind3/5 combat/formation blockers remain; Soul Challenge entry is NOT enabled by A4.
- Battle appearance evidence from FightField.RoleAppear:
  - every fighter has trailing soulEquipId U16;
  - when >0, client indexes npcSoulDatas[soulEquipId], therefore the wire value is the Soul itemId (595xx), not equipId and not the mapped NPC avatar id.
- Battle integration:
  - BattleFighter/BattleRoleData now carry soulEquipId.
  - BattleRoleSerializer writes the authoritative value instead of unconditional zero.
  - player-owned FollowNpc fighters resolve SoulSystem.equippedForNpc and use equippedSoul.itemId on the battle wire.
- Soul skill evidence from RoleController:GetSkillLv(showHigher=true):
  - Soul-only skills expose their Soul level.
  - duplicate warrior/Soul skills use the higher level.
- SoulSkillLevelMerge implements exactly that rule and is applied to follower battle skill snapshots.
- A4 intentionally DOES NOT add Soul startAtt/nowAtt conversion to ATK/DEF/INT/AGI/HPX/SPX. Client formulas are known, but authoritative server settlement/order is still evidence-blocked.
- Regression:
  - DungeonWeeklyQualificationSafeTest covers kind8 element, kind9 minimum level, kind10 maximum level, missing Soul, wrong element/level, and equipId mismatch.
  - DvtBattleRoleWireTest proves follower trailing soulEquipId serializes Soul item59576.
  - SoulA4ReadOnlyIntegrationTest proves duplicate skill higher-level semantics and Soul-only skill insertion.
  - Existing A3 equip tests remain PASS.
- Validation:
  - targeted A3/A4 + weekly qualification/battle-wire suite: BUILD SUCCESSFUL, 18 actionable tasks.
  - git diff --check: PASS.
  - full gradlew test build --rerun-tasks: BUILD SUCCESSFUL, 21/21 tasks.
- Gradle compiler-session deletion was restored exactly; pre-existing scratch/bundle files remain untouched.
- No production deploy/restart for A4. Next safe work: separately audit a verified Soul acquisition path (gacha/mail/GM/web) or complete the full skill qualification contract before wiring Soul dungeon entry. Do not remove the weekly kind guard until dedicated kind3/5 combat is complete.


## 2026-10-05 — Soul Foundation A5 — ACQUISITION AUDIT + FULL SKILL QUALIFICATION PASS

- Scope followed A4 checkpoint: first audit a verified Soul acquisition path; because creation semantics remain incomplete, do NOT fabricate Soul generation. Then complete the safe alternative: full `RoleController:GetSkillLv` qualification contract for dungeon needPar kinds 4/5.
- Soul acquisition evidence locked:
  - `S:120-008` result values from Lua DVT: 1=login, 2=gacha, 3=mail, 4=GM, 5=web.
  - Doro/Mall uses `ECurrency.Soul=10` and `ColorSoul=11`.
  - Doro Soul pools resolve through BlissBag tiers: 32994(N), 32995(R), 34073(SR), 34074(SSR), then to actual Soul item IDs 595xx.
  - Example tier weights from current DVT data: normal Soul draw 6000/2900/1000/100; ColorSoul 7000/3000 for SR/SSR.
  - TextData 23837 confirms Soul quality changes base stats / level cap / skill count; each strengthen level adds 2 random points across six stats.
- Acquisition remains EVIDENCE-BLOCKED:
  - DVT client/data still do not expose the authoritative algorithm that distributes `SoulDef.startPoint` into the six `startAtt` fields at creation.
  - DVT client/data still do not expose the authoritative rule choosing initial Soul skill IDs for each probabilistic slot.
  - Therefore no gacha/mail/GM/web Soul-creation mutation was added. Do not invent equal/random distribution or NPC-derived skills.
- Full FollowNpc skill lookup implemented as `DvtFollowNpcSkillLookup`, preserving Lua order:
  1. normal 3 slots (including immediate return of level 0 on ID match),
  2. learned special skill,
  3. upgrade skill,
  4. exclusive-weapon skill,
  5. passive skill,
  6. Soul-only skill.
- `DvtWeeklyQualification` kinds 4/5 now mirror exact `Dungeon.CheckForQualification` semantics:
  - kind4: `GetSkillLv(skill)>0`, and when a Soul is equipped that Soul must also contain the required skill;
  - kind5: reject when either `GetSkillLv(skill)>0` OR the equipped Soul contains the forbidden skill.
- Exclusive weapon data is no longer missing in this workspace:
  - `IOS_ExclusiveWeapon_C.dat`: 692 rows × 29 bytes; 686 unique (itemId, enhanceLv) keys because item 64524 duplicates lv0..5. Lua uses last-row-wins table assignment, and Kotlin loader now matches it.
  - `IOS_CollectWarriorSort_C.dat`: 567 rows × 9 bytes, used for `ExclusiveWeapon.CheckExclusiveWarrior` ownership.
  - Added domain models/loaders and GameDataManager loading for both tables.
- Corrected NpcUpgrade source to the authoritative DVT `IOS_NpcUpgrade_C.dat` when present, with fallback to legacy `NpcUpgrade_C.dat`. Current DVT table has 15 rows; its `skillLv` values are all 0, which is preserved rather than guessed.
- Regression:
  - `DvtFullSkillQualificationTest` covers real DAT sizes/duplicate semantics plus normal/special/upgrade/passive/Soul/exclusive-weapon lookup behavior.
  - `DungeonWeeklyQualificationSafeTest` now covers required/forbidden skill gates including the exact Soul interaction.
  - Existing `SoulA4ReadOnlyIntegrationTest` remains green.
- Validation:
  - targeted A5/A4 qualification suite: PASS, 10 tests.
  - full `gradlew test build --rerun-tasks`: BUILD SUCCESSFUL, 21/21 tasks.
  - `git diff --check`: PASS.
  - Gradle-deleted tracked `.kotlin/sessions/kotlin-compiler-10369008480978718564.salive` restored exactly.
- Runtime boundary:
  - `DvtWeeklyQualification.checkEntry` is still only exercised by tests; it is NOT wired into room creation/runtime yet.
  - Weekly kind3 / Soul kind5 battle lifecycle remains blocked by dedicated combat/formation/objective implementation.
  - No production deploy/restart for A5.
- Next safe work: either continue evidence search for authoritative Soul creation stat/skill initialization, or wire qualification into the dedicated kind3/kind5 room path only after those battle lifecycles are implemented. Do not remove the existing kind guard merely because kinds4/5 are now evaluable.

## 2026-10-05 — Soul A1–A5 production deployment PASS

- GitHub `main` fast-forwarded from `c13f469` to `227ed91` (`feat: complete DVT soul skill qualification`).
- Production `C:\Server-Game\tsm_dodo` was previously at `df7b234`; direct GitHub fetch over non-interactive SSH could not use the Windows credential store, so deployment used a verified git bundle requiring exactly `df7b234`.
- Production fast-forwarded cleanly `df7b234 -> 227ed91`; local `app/src/main/resources/application.conf` was preserved and no scratch/bundle files were committed.
- Production Java 21 full gate after update: `gradlew.bat test build --rerun-tasks` => BUILD SUCCESSFUL, 21/21 tasks executed.
- Runtime restart:
  - old game JVM PID `7088` was stopped;
  - first WMI launch occurred while the old `app:run` wrapper was still unwinding and did not bind 6613;
  - after the old wrapper exited, second `_launch_prod_wmi.cmd` start succeeded;
  - new game JVM PID `2376` owns `0.0.0.0:6613` and `[::]:6613`.
- Startup verification on production:
  - database pool connected successfully;
  - `IOS_Soul`: 4 qualities;
  - `IOS_SoulLevel`: 300 levels;
  - `IOS_NpcSoul`: 94 Souls;
  - `IOS_ExclusiveWeapon`: 686 effective rows / 106 weapon IDs;
  - `IOS_CollectWarriorSort`: 567 groups;
  - `IOS_NpcUpgrade`: 15 rows from the authoritative IOS table;
  - Netty server reported ready on port 6613 and local health-check connections continued.
- Scope reminder:
  - A1–A4 protocol120 foundation/login/equip/read-only battle consumers are now live on production.
  - A5 qualification code is present in the deployed binary but `DvtWeeklyQualification.checkEntry` is still not wired into weekly/Soul room creation, so it does not change live dungeon entry yet.
  - Soul acquisition/gacha creation is still evidence-blocked at initial six-stat distribution and initial skill-ID selection; no speculative creation algorithm was deployed.

## 2026-10-05 — BẮT BUỘC dùng Lua ĐVT + native dump ĐVT 1.15 làm nguồn client chuẩn

- Từ phiên sau, khi audit/fix subsystem client-facing, PHẢI đọc Lua ĐVT cùng với native dump chính thức ĐVT 1.15 trước khi suy từ Kotlin hoặc PC source.
- Native dump chuẩn:
  - root: `D:\Games\Ts-Offline\TSMobileOnLine\_native_dump_dvt115`
  - APK gốc: `dvt115_base.apk`
  - ARM64 IL2CPP: `lib\arm64-v8a\libil2cpp.so`
  - ARMv7 IL2CPP: `lib\armeabi-v7a\libil2cpp.so`
  - metadata: `assets\bin\Data\Managed\Metadata\global-metadata.dat`
  - dump outputs: `dump.cs`, `il2cpp.h`, `script.json`
- Danh tính APK đã xác minh trực tiếp từ AndroidManifest/signing certificate:
  - package: `net.chinesegamer.tsn`
  - app name: `吞食天地 多元宇宙`
  - versionName: `1.15`
  - versionCode: `26`
  - signer subject/issuer: `Organization=chinesegamer, OU=cgtsn, CN=cgtsn`
  - certificate SHA-256: `1711d2110d87514290d03b9ceb1c85ef0dc3220aea5e220afce67bd4871bc447`
- `1.15` là Android app version của base APK; `2.4.76` là data/CDN version từng dùng trong client, không được coi là mâu thuẫn.
- Native network trace đã khóa từ binary ARM64:
  - `SocketClient.WriteMessage()` đóng gói header `0x91C0`, length, payload và XOR `0xAD` trước khi gửi.
  - `SocketClient.OnReceive()` giải XOR, tách packet rồi gọi `NetworkManager.AddEvent(0x68, payload)`.
  - `NetworkManager.Update()` lấy Lua callback, push `eventId + byte buffer`, rồi `PCall()`.
  - relocation `0x2ed4f70 -> 0x2fcb6f0`; `script.json` xác nhận string tại đó là `Network.OnSocket`.
  - Lua `Logic/Network.lua::Network.OnSocket()` nhận `csProtocal.Message` rồi gọi `Network.OnMessage(data)`.
  - Kết luận: IL2CPP không tự giải nghĩa protocol gameplay 120; gameplay packet được chuyển vào Lua.
- Quy tắc sử dụng nguồn từ nay:
  1. Lua ĐVT vẫn là nguồn sự thật gameplay/protocol/semantics cao nhất.
  2. Native dump ĐVT 1.15 + client DAT/assets là nguồn client chính thức song hành để xác minh C#/IL2CPP bridge, serialization, packet transport, field layout, Unity/client behavior và những gì Lua không biểu diễn.
  3. Khi Lua và native/client data có vẻ mâu thuẫn, phải trace cả hai và xác định đúng layer trước khi sửa server; KHÔNG lấy Kotlin hiện tại làm chuẩn.
  4. Kotlin server chỉ được sửa sau khi contract đã khóa từ Lua + native/client evidence.
  5. PC source `bboycs/tsonline-server-tl` chỉ là fallback khi Lua + native dump + client data đều không đủ; không được lấn át ĐVT.
- Soul/gacha note:
  - gacha production đã xác nhận trả hộp Soul `32994/32995/34073/34074`, nhưng backend hiện còn đưa chúng vào túi item thường.
  - Lua protocol xác nhận nhận Soul từ gacha dùng `S:120-008 result=2`.
  - Native IL2CPP không chứa logic tạo Soul; nó chỉ chuyển packet sang Lua.
  - thuật toán server gốc phân bố `startPoint` vào 6 stat và chọn initial skill IDs vẫn chưa có bằng chứng; tiếp tục tìm trong toàn bộ APK/assets/Lua binary/server evidence, không được bịa.

## 2026-10-05 — Soul acquisition flow corrected + item-use 240 safety guard

- IMPORTANT correction to the earlier gacha diagnosis: DVT Soul gacha does NOT create a SoulThing directly.
- Authoritative flow locked from official DVT 1.15 Lua + real IOS DAT:
  1. Doro gacha rewards a normal-bag Soul box.
  2. Soul box uses normal item-use specialAbility 48 and opens through BlissBag data.
  3. Box reward is a real Soul item 595xx with specialAbility 240 (`EItemUseKind.Soul`).
  4. Using the 595xx Soul item through C:023-015 is the server-side Soul creation path.
  5. Server must create/persist a fully populated SoulThing and then send protocol120 receive/bag data; client does not generate it.
- Exact four Doro Soul boxes from `IOS_Item_C.dat`:
  - 32994 `Bộ Linh Hồn Trắng`: kind38, specialAbility48.
  - 32995 `Bộ Linh Hồn Lục`: kind38, specialAbility48.
  - 34073 `Túi Linh Hồn - Lam`: kind38, specialAbility48.
  - 34074 `Túi Linh Hồn - Tím`: kind38, specialAbility48.
- `IOS_BlissBag_C.dat` maps those boxes to 595xx Soul items. Regression now asserts every reward has specialAbility240 and the expected quality N/R/SR/SSR, with total pool weight 10000.
- Therefore Soul bag `0/100` immediately after gacha is expected DVT behavior if the player has only received the box and has not opened/used the resulting Soul item yet.
- `59501..59594` are kind94 + specialAbility240. Their item metadata does NOT provide generation data: element/elementValue/skillLink/spare2/spare3/attr1/attr2 are all zero; only quality varies.
- Official DVT 1.15 `SoulThing.New(data)` only deserializes server data: sn, itemId, element, equipId, lv, starLv, six startAtt, six nowAtt, four skill(id+lv), lock. There is no client generator.
- `IOS_Soul_C.dat` quality rows lock skill-slot probabilities / startPoint:
  - N: startPoint150, skillGetProb 10000/0/0/0.
  - R: startPoint250, skillGetProb 10000/2000/0/0.
  - SR: startPoint400, skillGetProb 10000/10000/1500/0.
  - SSR: startPoint550, skillGetProb 10000/10000/10000/1000.
- Still evidence-blocked: authoritative initial six-stat distribution, skill-ID pool/selection, and exact initial element derivation. Do not invent them.
- `IOS_NpcSoul_C.dat` avatar NPCs cannot provide initial skills: their six stats are all 1 and skills are zero.
- Client-side candidate tables were eliminated:
  - `IOS_NpcSkillProb_C.dat` APK asset is only a 4-byte count/stub and has no usable payload.
  - `IOS_WeapBody.dat` is loaded by `OnLoadNpcWeaponData` (appearance data), not a Soul skill pool.
  - `IOS_Skill2.dat` is not loaded by DVT 1.15 Lua and is not authoritative for Soul generation.
- MoonDev/GOC decompile contains opcode120/SoulSub and pet SoulId/SoulEquipId fields, but no standalone Soul model/table and no startPoint/skillGetProb/generator implementation. It is insufficient as an authoritative generator source.
- Actual Kotlin bug found: `ItemUseProcessor` had no EItemUseKind.SOUL=240 case, so a 595xx item could fall into generic item use and be consumed without creating a Soul.
- Safety fix implemented (not a generator):
  - added `EItemUseKind.SOUL = 240` to Kotlin constants;
  - ItemUseProcessor now rejects 240 with `success=false, consumed=false` until the authoritative generator is implemented;
  - this prevents loss of real Soul items during continued reverse-engineering.
- Regression `SoulItemUseSafetyTest`:
  - locks box->Soul-item quality semantics from real DAT;
  - locks item59501 specialAbility240;
  - asserts 240 is never consumed as generic/no-op.
- Validation: targeted Soul safety tests PASS; full `gradlew test build --rerun-tasks` PASS, 21/21 tasks; `git diff --check` PASS.
- Next safe work: commit/push/deploy this loss-prevention guard to production, then continue server-side evidence search for the missing Soul generator. Do NOT change Doro to issue SoulThing directly.

## 2026-10-05 — Soul guard production PASS + MoonDev generator ruled out

- Commit `a749513` (`fix: preserve Soul items until generator is verified`) pushed to GitHub main and deployed to production `.69`.
- Production validation:
  - source HEAD `a749513108b77a6c12ec7089aa0eafe8d3d8dc95`;
  - full production `gradlew test build --rerun-tasks` PASS, 21/21 tasks;
  - runtime restarted cleanly via `_launch_prod_wmi.cmd`;
  - production listener `0.0.0.0:6613` is active on Java PID 11592;
  - startup log confirms server ready and accepting local health-check connections.
- Runtime guard behavior now active: item Soul 595xx / `specialAbility=240` is NOT consumed until authoritative SoulThing generation is implemented.
- Reverse-engineering update:
  - all seven workspace copies of `NpcSkillProb_C.dat` are byte-identical 4-byte stubs: `02 00 00 00` (count=2, no payload); this includes server-data and CDN 2.4.69/2.4.76/2.7 copies.
  - the previous `probs=[...]` strings recovered from MoonDev were traced to Eve GroupData selection logs, not Soul skill probabilities.
  - `UpdateSoulPoints` recovered from MoonDev is `RequiemSub.UpdateSoulPoints`, unrelated to Soul item generation.
  - IL-level xref over extracted `TSM.GameServer.dll` against `TSM.Shared.Protocol.SoulSub` returned `HITS=0`.
  - therefore the MoonDev package has Soul protocol enum scaffolding in `TSM.Shared`, but its actual GameServer does not implement SoulSub handlers and cannot provide an authoritative initial Soul generator.
  - exact public-web searches for `NpcSkillProb_C.dat`, `NpcSoul_C.dat`, `Soul_C.dat`, and `TSM.Shared.Protocol.SoulSub` produced no usable external source.
- Current evidence-block remains unchanged and explicit: no authoritative source for initial 6-stat distribution, initial skill-ID pool/selection, or exact initial element derivation.
- Do NOT fabricate official DVT semantics. If an offline fallback is later desired, implement it behind an explicitly custom/offline policy and keep the DVT evidence status documented.

## 2026-10-05 — Official TSN website pinned as authoritative DVT evidence source

- Official website pinned for all future DVT audits: `https://tsn.chinesegamer.net/index.php`.
- Official Soul system page: `https://tsn.chinesegamer.net/sys.php`.
- Official Requiem/Soul-point page: `https://tsn.chinesegamer.net/sys1.php`.
- Source priority for future work is now:
  1. Lua DVT gameplay/protocol semantics.
  2. Official TSN website `tsn.chinesegamer.net` for published game rules/system behavior.
  3. Official DVT 1.15 native dump + client DAT/assets for C#/IL2CPP bridge, transport, field layout and client data.
  4. Kotlin server implementation.
  5. PC source fallback only when all DVT sources above are insufficient.
- Official Soul rules confirmed from `sys.php`:
  - Soul grants both stat bonuses and Soul skills when equipped.
  - A warrior can equip only one Soul.
  - Soul must match the warrior element to be equippable.
  - qualities are white/green/blue/purple and quality changes conversion rate, total initial stat points, max level, and number of acquired skills.
  - each strengthen level grants exactly 2 points randomly distributed among the 6 Soul stats.
  - refinement only changes strengthened/added stats; initial Soul stats cannot be changed by refinement.
  - Soul skill sets are independent/flexible; official examples include Zhou Yu with Freeze/Revive, Guan Yu with Stealth/Stun, Ma Chao with Barrier/Tree-Spirit. Therefore do NOT derive Soul skill IDs from the base warrior's own normal skill set.
  - breakthrough requires same quality + same Soul name and raises level cap; each Soul can break through at most 5 times.
  - skill upgrade consumes Souls with matching skills; all matching skills can level when multiple overlap.
- Official `sys1.php` confirms Requiem/Soul-point behavior:
  - equipped Soul on an active warrior enables Soul-point drops from outdoor battle victories;
  - Soul quality affects Soul-point drop amount;
  - up to 4 bosses can accumulate; Soul points stop accumulating while slots are full;
  - refresh consumes 500 Soul points.
- Official launch/event page confirms Soul gacha currencies and event terminology (`靈魂幣`, `幻彩靈魂幣`) but does not expose the missing generator internals.
- Critical evidence status after official-site audit:
  - authoritative: element source, quality, startPoint totals, skill-count probabilities, packet/storage structure, strengthen semantics, and the fact that Soul skills come from an independent/flexible pool.
  - still NOT published/locked: exact initial distribution algorithm of startPoint across six stats; complete Soul skill-ID pool and exact skill-ID selection algorithm.
  - therefore DO NOT implement an 'official DVT' Soul generator yet. Keep production guard for item-use 240 until those two pieces are evidenced, or explicitly choose a separate custom/offline fallback policy.

### Clarification — Lua DVT remains the supreme source of truth

- **Lua Đa Vũ Trụ is the highest-priority and most important source.**
- Never let the official website, native dump, Kotlin server, or PC source override behavior that is clearly defined by Lua DVT.
- Use the official website only to supplement published rules/descriptions that Lua does not fully explain.
- Use native dump/client DAT/assets to verify transport, serialization, field layout, client-side behavior, and hidden implementation details not expressed in Lua.
- If sources appear to conflict, resolve in this order: **Lua DVT > official TSN rules > DVT 1.15 native/client DAT/assets > Kotlin > PC fallback**.
- For every future Soul/Dungeon/Item/Protocol audit, inspect the relevant Lua DVT files first before modifying server code.

## 2026-10-05 — Soul acquisition made usable end-to-end with explicit CUSTOM OFFLINE generator

- Goal of this step: make item Soul 595xx usable end-to-end without pretending the unknown original server generator is recovered.
- Lua DVT remains supreme source. Official TSN website and DVT 1.15 client/native data are supporting evidence only.
- Authoritative DVT flow now implemented:
  1. Doro gives Soul boxes 32994/32995/34073/34074 (specialAbility48).
  2. Opening those boxes via BlissBag yields Soul items 595xx (specialAbility240).
  3. C:023-015 using a 595xx item invokes the Soul creation path.
  4. `NpcSoul_C.dat` supplies itemId -> Soul avatar NPC + quality.
  5. `Npc_C.dat` Soul avatar supplies the authoritative element.
  6. `Soul_C.dat` supplies authoritative startPoint total and skill-count probabilities by quality.
  7. Soul is persisted in `character_souls`, then S:120-008 result=2 sends the newly created Soul.
  8. Only after Soul creation persists successfully is the 595xx item consumed.
- Lua DVT `protocolTable[120][8]` was re-read and locked:
  - result=1 (login) populates the full equipped/Soul bags from the received list.
  - result!=1 appends each received Soul into the next free Soul-bag slot and shows a receive message.
  - therefore result=2 MUST send only the newly created Soul(s), never the full Soul snapshot.
- `SoulSystem` now has atomic `create()` and `delete()` support:
  - enforces max Soul bag size 100;
  - allocates a unique positive SN under the per-character mutex;
  - repository save happens before publishing to cache;
  - rollback delete is available if item consumption unexpectedly fails.
- `SoulResponseSender` now defines `RESULT_GACHA = 2`.
- `ItemHandler` now handles `EItemUseKind.SOUL=240` as a direct-use special case before generic item processing:
  - generate template -> `SoulSystem.create()` -> consume exactly 1 bag item -> S:023-015 success -> S:120-008 count=1/result=2.
  - if generate/persist fails or Soul bag is full, the item is NOT consumed.
  - if item removal fails after create, the created Soul is deleted as rollback.
- `OfflineSoulGenerationPolicy` is intentionally marked CUSTOM OFFLINE for only the two original-server details still unpublished:
  - initial six-stat distribution: each of `startPoint` points is distributed uniformly at random among the 6 Soul stats; sum is always exactly authoritative `startPoint`.
  - Soul skill-ID pool: independent Soul-skill whitelist derived only from unambiguous official DVT examples and exact IOS_Skill_C.dat IDs:
    - 10004 Cây tinh (樹精)
    - 10010 kết giới (結界)
    - 11013 Thuật hồi sinh (復活)
    - 11014 Băng phong (冰封)
    - 13005 Thuật ẩn mình (隱身)
  - 暈擊 is deliberately NOT mapped because the localized DAT contains multiple stun-producing skills and no authoritative one-to-one ID was found.
  - selected Soul skill IDs are unique, initial skill level=1, and the number of selected slots follows authoritative `skillGetProb` from `Soul_C.dat`.
- Everything outside those two CUSTOM choices remains DVT-authoritative: item mapping, quality, element, startPoint totals, skill-count probability, storage, packet wire, equip rules and battle consumers.
- New regression `SoulOfflineGenerationRegressionTest`:
  - loads real IOS_Soul_C.dat / IOS_NpcSoul_C.dat / IOS_Npc_C.dat / IOS_Skill_C.dat;
  - verifies all four qualities preserve authoritative element/startPoint/skill-count bounds;
  - verifies skill IDs are unique, level1 and in the explicit CUSTOM whitelist;
  - verifies `SoulSystem.create/delete` persist-first cache behavior.
- `SoulA2LoginSyncRegressionTest` now also locks S:120-008 result=2 as count=1 + exactly one Soul + final byte 2.
- Validation:
  - targeted Soul suite PASS;
  - full `gradlew test build --rerun-tasks` PASS, 21/21 tasks;
  - `git diff --check` PASS.
- Deployment status at this checkpoint: local code complete and validated; not yet committed/deployed in this paragraph. Next action is commit/push, deploy to production `.69`, restart, then client test using 595xx item.


## 2026-10-05 — QUÀ / HOUSE BOARD Q1+Q2 LOCAL PASS
- Source of truth: Lua DVT protocolTable[105], HouseBoard.lua, UISouvenirShop.lua, SouvenirShop.lua, SouvenirData.lua, IOS_Souvenir_C.dat.
- Xác định nút/hệ “Quà” chính là House Board / Gift Wall, mainKind=105. Backend trước mốc này không có handler 105.
- Chia mốc:
  - Q1 PASS local: profile + friend list + gift wall read-only + gift history (105-001/008/009), profile update 105-002 cũng đã có.
  - Q2 PASS local: gửi quà C/S105-003, nhận realtime S105-010, shop/bag source, persist history/charm, friendly, role counts và currency sync.
  - Q3 CHƯA LÀM: leaderboard 105-004, charm feedback reward 105-005/006.
- IOS_Souvenir_C.dat authoritative:
  - 19 records, mỗi record 18 bytes.
  - fields: itemId U16, quality U8, payment U8, price I32, addCharm U16, addFriendly U16, addExp U16, naming U8, broadcast U8, roleCount U16.
  - payment chỉ có 1=ECurrency.Gold/KNB và 3=ECurrency.Money.
  - per-item RoleCount 711..729.
  - RoleCount 696 = SouvenirFeedbackExp; 987 = BuyGiftMaxMoney.
  - quà từ BAG chỉ chấp nhận itemId thuộc souvenirDatas; Lua SetListOfGiftsInBag xác nhận điều này.
- C105-003 wire authoritative: targetId Int64 + itemId UInt16 + quantity U8 + sourceKind U8 = 12 bytes.
- S105-003 result authoritative: 1 success, 8 no data, 9 daily spend cap, 14 chưa nhất chuyển.
- S105-001 friend friendly là 4 bytes Int32 (đã có regression để tránh lệch packet).
- S105-009/010 time dùng Double OADate; server dùng OleDate.nowLocal().
- Persistence mới, không nhét dữ liệu vào Characters:
  - character_house_profiles
  - character_gift_records
- Q2 mutation:
  - người nhận: charm += addCharm * qty + gift history.
  - nếu là bạn: friendly hai chiều += addFriendly * qty, clamp FriendConstants.MAX_FRIENDLY.
  - người gửi: RoleCount 696 += addExp * qty; RoleCount item 711..729 += qty; money-shop dùng 987.
  - shop source trừ KNB qua PointSystem hoặc Money qua MoneySystem; bag source tự tìm stack itemId vì C105-003 không gửi slot.
  - transaction DB gift record + charm atomic; system có rollback cho currency/item/friendly/RoleCount nếu persist lỗi.
- Realtime người nhận online nhận S105-010; client tự append gift wall/history/red-dot, không tự cộng charm/friendly.
- Regression: HouseBoardGiftQ12Test PASS: real DAT 19x18 bytes, S105-001 Int32 friendly, S105-008/009/010 byte contract, C105-003 parse 12 bytes/result relay.
- Validation local:
  - :app:compileKotlin PASS.
  - targeted HouseBoardGiftQ12Test PASS.
  - git diff --check PASS.
  - full gradlew test build --rerun-tasks PASS 21/21 tasks in 31s.
- Chưa deploy production tại thời điểm ghi checkpoint này. Bước kế: commit/push Q1+Q2 -> kiểm production HEAD/status -> deploy production 192.168.1.69:6613 -> runtime smoke -> client test -> sau đó Q3.

### 2026-10-05 — QUÀ Q1+Q2 PRODUCTION PASS
- Commit gameplay: 960b369 Implement DVT House Board gifts Q1 Q2.
- GitHub main đã fast-forward tới 960b369.
- Production C:\Server-Game\tsm_dodo fast-forward từ 5360cd4 -> 960b369 bằng verified Git bundle vì Git Credential Manager trên production không fetch HTTPS được; không force/reset và giữ nguyên application.conf local.
- Production full gradlew test build --rerun-tasks PASS 21/21 tasks trong 2m53s.
- Runtime production restart bằng _launch_prod_wmi.cmd; HEAD 960b369; port 6613 LISTEN PID 12156.
- Runtime log xác nhận DatabaseFactory init thành công và GameDataManager nạp IOS_Souvenir = 19 quà.
- TCP smoke từ máy client tới 192.168.1.69:6613 = True.
- Refined runtime scan: không có ERROR, FATAL, Exception: hoặc HouseBoard rollback sau restart.
- Bundle tạm houseboard-q12-960b369.bundle đã xóa ở local và production.
- Trạng thái: Q1+Q2 deployed PASS; chờ client test UI/gửi quà. Q3 (105-004/005/006) vẫn pending.

## 2026-10-05 — Judgment Tower A1 Activity 77 schedule
- Lua DVT `EActivity.JudgmentTower=30` = 搶救二喬 (試煉之塔) / Giải cứu Nhị Kiều.
- `IOS_ActivityInfo_C.dat`: activityId=1, lv=15, awards are luckyBag IDs 10016..10020, bitFunction=1.
- `IOS_ActivityTime_C.dat`: id=1, switch=1, lifetime 2022-01-01..2032-12-31, openDayMask=64 (Sunday only), 08:00..23:00.
- Lua protocol 77 locked: S:077-001 = activityId U16 + start OADate Double + close OADate Double; S:077-002 = activityId U16 + state U8. C:077-003 = enter activity + activityId U16.
- Added `ActivityScheduleSystem` to send runtime S77-1/2 for activity 1 during character login, deriving state from authoritative DAT schedule. Monday/non-open day => state0; Sunday 08:00-23:00 => state1; after close => state2.
- Added `DvtJudgmentTowerActivityTest` using real IOS_ActivityTime_C.dat and byte-level S77 regression.
- Targeted test PASS; full `gradlew test build --rerun-tasks` PASS 21/21.
- A2 pending: authoritative C77-003 destination/entry semantics; do not guess map/coords.

## 2026-10-05 — Central Admin/GM bypass policy + Judgment Tower live test
- Reused existing authoritative account permission: `accounts.gm_level` -> `session.gmLevel`; no hard-coded account/character names.
- Added central `AdminBypassPolicy` with flags:
  - bypassSchedule=true for GM
  - bypassLevel=true for GM
  - bypassEntryLimit=true for GM
  - bypassQuestGate=true for GM
  - freeCost=false by default
  - rewardRepeatTest=false by default
- Normal players always receive all-false flags and remain 100% on DVT rules.
- Destructive/economy/reward-repeat bypasses intentionally remain disabled by default.
- Integrated policy into `ActivityScheduleSystem` first:
  - normal account: Judgment Tower activityId=1 still Sunday 08:00-23:00 from IOS_ActivityTime_C.dat.
  - GM/Admin (`gmLevel>0`): schedule state is forced STARTED for testing, with an admin-only runtime window around current time.
  - static DVT DAT/Lua are untouched.
- Regression added: same Monday timestamp yields STATE_NONE for normal session and STATE_STARTED for GM session; existing S:077 byte-contract tests remain PASS.
- Targeted test PASS; full `gradlew test build --rerun-tasks` PASS 21/21; git diff --check pending commit step.
- Framework is now the single policy point for future subsystem integrations; A2 C:077-003 entry handler should consult the same policy for level/entry-limit checks.

## 2026-10-05 — GM gameplay-wide bypass + Judgment Tower A2 entry
- User requirement: any account with `gmLevel > 0` must be able to test locked content.
- Existing authority remains authoritative: `accounts.gm_level` -> `session.gmLevel`; no account-name hardcode.
- AdminBypassPolicy defaults for GM are now all testing flags ON: schedule, level, entry-limit, quest-gate, free-cost, reward-repeat. Normal accounts still receive all-false flags.
- Integrated shared GM bypass into `DungeonSystem`: level gates, daily entry limits/counters, weekly/all-pass quest gate, reward-repeat guard, and dungeon timeout enforcement are skipped for GM only.
- Integrated shared GM bypass into `LimitTimeDungeonSystem`: GM is not auto-expelled / time-counted by limit-time dungeon quota.
- Activity protocol A2 implemented: `Opcodes.ACTIVITY = 77`; new `ActivityHandler` handles C:077-003 (UInt16 activityId).
- Judgment Tower authoritative Eve evidence: scene 12921 is the tower entrance; external scene 12061 has SceneInfo target 12921 spawn raw (31,60) => world (620,1200). Scenes 12921..12939 form the linked trial-tower chain in Eve.emg.
- C77-003 activityId=1 validates current schedule for normal players; GM uses schedule+level bypass, then teleports to scene 12921 at the Eve-derived (620,1200). No guessed coordinates.
- Added dispatcher registration for ActivityHandler and regression `AdminBypassAndActivityEntryTest`: normal flags all false, gmLevel=99 flags all true, Eve-derived tower entry exactly 12921/(620,1200).
- Targeted tests PASS; full `gradlew test build --rerun-tasks` PASS 21/21 locally.

## 2026-10-05 — Judgment Tower A3 GM battle-gate fix
- Runtime trace after user test: entered activity correctly, then reached scene 12955; Nhân Hùng (NPC 38382, clickId=1) only owns Eve6 Talk 53182. Battle is on the Door directly under/near him, not on the NPC talk.
- Scene12955 Door2/Eve2 authoritative chain requires mission 10690 progress >=81, mission10691 gate, Lv15+, then emits Talk 53181 + Battle resultType3/FightData key1. User's mission10690 was absent (step=0), so Door2 correctly failed under normal Eve evaluation and no battle started.
- Added optional conditionOverride to EveConditionResolver without changing normal-player semantics.
- EveTriggerRouter now applies a tightly-scoped GM override only on battle-bearing Door events in the authoritative Trial Tower scene chain 12921..12959: mission/class2 and player-level gate are bypassed, while class8 battle-result and class10 choice conditions are NOT bypassed. This lets gmLevel>0 test any tower battle without corrupting normal event branching.
- AdminBypassPolicy injected into EveTriggerRouter.
- Regression uses real Eve.emg scene12955 Eve2: normal empty state resolves null; GM override resolves the exact Talk53181 + Battle FightData key1 branch.
- Targeted tests PASS; full local test/build PASS 21/21.

## 2026-10-06 — GM mở toàn bộ lịch S.Kiện
- User chuyển sang audit các sự kiện khác sau khi Teleport GM PASS.
- `IOS_ActivityInfo_C.dat` có 25 activity records; nhóm đầu:
  - id1 kind30 JudgmentTower / Giải cứu Nhị Kiều, Lv15, Chủ nhật 08:00-23:00.
  - id2 kind31 MixVS / Đấu tổ đội, Lv15, openDayMask=8, 19:00-21:00.
  - id3 kind32 ChaosVS / Loạn đấu lôi đài, Lv15, openDayMask=2, 19:00-21:00.
  - id4 kind33 DareNpc / Khiêu chiến lôi đài NPC, Lv15, openDayMask=21, 19:00-21:00.
  - id5 kind34 KingOfTheWorld / World Boss, Lv15, openDayMask=127, 10:00-23:00.
- Lua `UIActivity.OnClick_Go()` xác nhận mọi activity thường đều gửi C:077-003 + activityId(U16), ngoại trừ ArmyBattle dùng 171-007.
- Trước patch LoginService chỉ gửi S:077-001/002 cho activityId=1, nên các event khác không có runtime state từ server.
- `ActivityScheduleSystem` thêm `sendAllActivityStates()` và LoginService giờ gửi runtime schedule/state cho toàn bộ activityTimeDefs khi đăng nhập.
- GM `gmLevel>0` được `STATE_STARTED` trước cả static lifetime/day/time checks, đúng yêu cầu test mọi event; account thường vẫn giữ nguyên DVT lifetime/day/time.
- Regression `allActivitySchedulesAreSentAndGmSeesThemStarted` dùng real IOS_ActivityTime_C.dat: mọi enabled activity có đủ S77-1/S77-2 và GM đều STARTED.
- Targeted test PASS; full local `gradlew test build --rerun-tasks` PASS 21/21.
- Entry backend C77-003 hiện mới authoritative cho id1; id2..id5 sẽ audit/implement lần lượt, không đoán scene đích.


## 2026-10-06 — Activity 77 entry id2–id5 fixed from authoritative DVT Eve/Talk

- Root cause after GM-open-all-schedules commit 007e1f0:
  - Login/UI state was correct for all enabled activities, but ActivityHandler still hard-returned every C:077-003 activityId != 1.
  - Therefore GM saw events as STARTED but pressing "Đến ngay" for other events did nothing.
- Re-read Lua DVT protocol 77:
  - S:077-003 result=1 payload = sceneId U16 + X U16 + Y U16.
  - Client calls MarkManager.StartNavigation(0, sceneId, 1, position); this is navigation to the event entry NPC, not direct teleport.
- Locked public entry NPCs from IOS_Npc_C.dat + Talk_C.dat + Eve.emg:
  - activityId2 MixVS / Đoàn P -> scene10991, npc15163 "Chủ Lôi Đài", (1070,120). Talk12334 explicitly describes 5v5.
  - activityId3 ChaosVS / Loạn đấu -> scene10991, npc15226 "Chủ LoạnĐấuĐài", (990,260). Talk12345/12346 explicitly describe solo chaos arena registration.
  - activityId4 DareNpc / Khiêu chiến lôi đài NPC -> scene10991, npc15244 "Chủđài Kchiến", (990,260). Eve chain + Talk20217 identify challenge-arena flow/reward.
  - activityId5 KingOfTheWorld / World Boss -> scene12001, npc38177 "BOSSKhiêuChiến", (1870,1180). Talk52373/52376/52377/52378 matches no-team, five-attempt World Boss rules.
  - scene10991 also contains Bảng Công Bố npc24009 / Talk20220 naming the three arena schedules, strengthening the mapping.
- ActivityHandler now:
  - keeps activityId1 JudgmentTower direct teleport behavior unchanged;
  - supports id2..5 with the authoritative S77-003 result=1 navigation contract;
  - derives arena scene/coordinates from Eve at runtime by requiring the three authoritative entry NPCs in one public scene, avoiding stale hard-coded coordinates;
  - derives World Boss entry from the unique Eve placement of npc38177;
  - keeps normal-player schedule + Lv15 checks; GM continues to bypass schedule/level via AdminBypassPolicy.
  - unsupported later activity IDs remain explicitly unimplemented rather than guessed.
- Regression AdminBypassAndActivityEntryTest:
  - locks exact Eve-derived mappings id2..5;
  - byte-locks S77-003 result=1 packet for scene10991/(1070,120);
  - existing Judgment Tower/GM battle-gate regressions remain green.
- Validation:
  - targeted AdminBypassAndActivityEntryTest + DvtJudgmentTowerActivityTest: BUILD SUCCESSFUL.
  - full gradlew test build --rerun-tasks: BUILD SUCCESSFUL, 21/21 tasks executed.
  - git diff --check: PASS before full build; final check performed again before commit.
- Next: deploy production 192.168.1.69:6613 and runtime-test GM "Đến ngay" for id2/3/4/5. Arrival should be navigation to the entry NPC; deeper matchmaking/WorldBoss protocol73 behavior is a separate backend slice if runtime exposes it.


## 2026-10-06 — World Boss live PASS + Activity id37 Tiên Đấu entry
- World Boss id5 runtime root cause confirmed on production before deploy: logs showed `C77-003 activityId=5 chưa hỗ trợ` while production HEAD was 007e1f0.
- Deployed commit f20e7b9 to production 192.168.1.69 via Git Bash SSH + verified bundle, preserving local application.conf.
- Production full `gradlew test build --rerun-tasks` PASS 21/21, restart recovered correctly, port 6613 and login runtime healthy; user confirmed World Boss works.
- Continued activity audit from real IOS_ActivityInfo_C.dat / TextData / Talk_C / Eve.emg.
- Next authoritative entry locked:
  - activityId37 kind48, subTag 99596 = Tiên Đấu, required level 65.
  - Eve scene49909 contains unique npc27141 `T.Đấu Trọngtài` at (1010,560).
  - Talk33567 from that NPC explicitly states this is the Tiên Đấu referee, solo-only, no charms, pet loyalty >=40.
  - Therefore C77-003 id37 uses the same Lua S77-003 result=1 navigation contract to scene49909/(1010,560), not direct teleport.
- ActivityHandler updated:
  - adds IMMORTAL_ARENA_ACTIVITY_ID=37 / entry NPC 27141;
  - derives entry from the unique Eve placement at runtime;
  - normal players require Lv65 for id37; GM level bypass remains centralized in AdminBypassPolicy.
- Regression AdminBypassAndActivityEntryTest locks exact id37 mapping and Lv65.
- Validation local: targeted PASS; git diff --check PASS; full `gradlew test build --rerun-tasks` PASS 21/21.
- Vô Giới id89/90/92 and Tử chiến TS id21 remain under evidence audit; do not guess their entry NPC. Evidence found: scene12003 has LôiĐàiTruyềnSư, NVthụlýtrậnđấu, VGTruyềnTốngSư, Quản Lý LôiĐài, but these roles must be distinguished before wiring C77-003.


## 2026-10-06 — Single-server Activity Server bridge (S172-001)
- Lua UIActivity: activities whose bitFunction includes flag2 attempt activity-server switching before C77-003.
- Root blocker: client Network.activityServerId defaults 0; current Kotlin never sent S172-001, so bitFunction=3 activities could return before sending C77-003.
- Lua protocol locked: S172-001 = ServerID(Int32)+ServerPort(Int32)+ServerIP(raw); Network.ConnectToActivityServer uses C1-100 only when activityServerId != current serverId.
- Offline authoritative solution: advertise current GS itself as the activity server during character-enter sequence.
  - S172-001 serverId=session.serverId
  - port=DEFAULT_PORT 6613
  - empty raw IP; Lua replaces IP from Network.servers for the matching server id.
  - Result: activityServerId == serverId, so client proceeds directly to C77-003 without fake shard reconnect.
- Added byte regression: AC 01 00 01 00 00 00 D5 19 00 00 for serverId1/port6613.
- Targeted AdminBypassAndActivityEntryTest PASS.
- git diff --check PASS; full local gradlew test build --rerun-tasks PASS 21/21.
- DeathMatch id21 evidence: map49952 is dynamic (0 NPC, no normal inbound reverse-link), uses EWar.DeathMatch=22 plus S77-012/013 for life/kill/rank; lifecycle still pending and must not be implemented as normal navigation.


## 2026-10-06 — Activity batch tiếp theo: Tiên Đấu + Vô Giới + Trung Thu
- Production trước batch này vẫn ở f20e7b9 (id2..5); local HEAD đã có:
  - 1410c68 Add DVT Immortal Arena activity entry.
  - f301713 Bridge DVT activity server to production GS.
- Tiên Đấu activityId37 / kind48:
  - IOS_ActivityInfo: Lv65, bitFunction=3.
  - Eve authoritative entry: scene49909, npc27141 "T.Đấu Trọngtài", (1010,560).
  - Talk33567 xác nhận trọng tài Tiên Đấu, solo-only, Lv65.
  - f301713 gửi S172-001 trỏ activityServerId về chính GS hiện tại để Lua không reconnect sang shard hoạt động giả khi bitFunction có flag2.
- Lôi Đài Vô Giới:
  - activityId89 kind101 Vô giới tổ đội PK.
  - activityId90 kind102 Vô Giới loạn đấu.
  - activityId92 kind103 LV100 loạn đấu.
  - Cả ba dùng public entry scene12003, npc15280 "VGTruyềnTốngSư", (650,880).
  - Eve chain public được phân biệt với placement cũ scene10993 bằng Talk20458/20459 + reborn gate conditionClass7/param8.
  - Server derive từ Eve runtime và map cả id89/90/92 về cùng entry.
- Trung Thu activityId94 / kind116 "Nghiễm Hàn Dật Sự":
  - IOS_ActivityInfo Lv30.
  - Description24563 yêu cầu đến hồ Quảng Hàn.
  - Eve scene49998 chứa cụm Hằng Nga38264 / Ngọc Thố22095 / Ngô Cương38465 / Hậu Nghệ38462.
  - Entry khóa tại Hằng Nga npc38264 (910,340), derive bằng cụm NPC để tránh hard-code scene mù.
- ActivityHandler minimumLevel:
  - id37 = 65; id94 = 30; các id89/90/92 giữ Lv15.
- Regression AdminBypassAndActivityEntryTest khóa exact entries:
  - id37 -> 49909/27141/(1010,560)
  - id89/90/92 -> 12003/15280/(650,880)
  - id94 -> 49998/38264/(910,340)
  - S172-001 activity-server bridge byte contract vẫn PASS.
- Targeted activity tests: PASS.
- Full local gradlew test build --rerun-tasks: BUILD SUCCESSFUL, 21/21 tasks.
- git diff --check: PASS.
- id21 Tử chiến TS vẫn evidence-blocked: chưa khóa được public entry NPC/scene nên chưa đoán.


## 2026-10-06 — Activity id124 Vô giới Nhất Kỵ Thảo + audit blocker 48/171
- activityId124 / kind136 "Vô giới Nhất Kỵ Thảo":
  - IOS_ActivityInfo lv=1, bitFunction=1.
  - Description24476: reborn player, personal registration, click arena master, random 1v1 with up to 4 warriors.
  - Public Vô Giới entry remains scene12003 npc15280 "VGTruyềnTốngSư" at (650,880).
  - Eve public entry flow has a fourth choice style33 only in the newer event branch, consistent with the additional Vô Giới duel mode.
  - ActivityHandler maps id124 to the same authoritative Vô Giới entry as id89/90/92.
  - minimumLevel(id124)=1 to match DAT; rebirth qualification remains an event/backend rule, not faked as a level gate.
- id96 Thần Binh Bảo Khố:
  - Lua UIEternalDungeon uses protocol 48: C48-1 battle layer, C48-2 request layer info; S48-2/3 opens/updates UI.
  - DAT authoritative exists: IOS_EternalDungeonLayer_C.dat + IOS_EternalDungeonPrize_C.dat.
  - Kotlin server currently has no EternalDungeon/protocol48 backend. Treat as a dedicated subsystem; do not fake navigation-only PASS.
- id121 Cứ điểm trận chiến / ArmyBattle:
  - UIActivity does NOT use C77-003; it sends mainKind171 subKind7.
  - Kotlin server currently has no mainKind171 handler. Treat as separate organization-war backend; do not fake with ActivityHandler.
- id21 Tử chiến TS remains evidence-blocked for public entry.
- Validation id124:
  - targeted AdminBypassAndActivityEntryTest PASS.
  - full gradlew test build --rerun-tasks PASS 21/21.
  - git diff --check PASS.


## 2026-10-06 — Activity id96 Thần Binh Bảo Khố / Eternal Dungeon A1+A2

- ActivityInfo authoritative:
  - activityId=96, kind=119, Lv15, bitFunction=1.
  - Vietnamese UI: mainTag 23316 = "Địa Tầng Thâm Uyên", subTag 25087 = "Thần Binh Bảo Khố".
- Lua DVT protocol 48 locked:
  - C:048-001: UI sends selected layer Int32 when pressing battle.
  - C:048-002: UI sends requested layer Int32 from RequestInfo().
  - S:048-001: result U8; result=3 shows message 21971, otherwise closes UI.
  - S:048-002: layer Int32 + result U8; client calls OverBattle(result) then reopens UI.
  - S:048-003: no payload; client opens UIEternalDungeon.
  - S:048-004 (Lua comment stale as 006): layer Int32 + count Int32 + record payload; client updates layer record list.
- Entry from S.Kiện:
  - No authoritative public NPC/navigation target could be locked from Eve/Talk/data for id96.
  - Therefore C:077-003 id96 now bridges to authoritative S:048-003, which is the client registration/open-UI packet, rather than inventing a map/NPC.
  - Normal Lv15/schedule validation remains in ActivityHandler; GM uses existing AdminBypassPolicy.
- RoleCount:
  - ERoleCount.EternalDungeon = 707.
  - Lua UIEternalDungeon.OnOpen uses currentLayer = RoleCount.Get(707) + 1, so 707 is number of cleared layers, not current layer.
- Authoritative DAT loaders added:
  - IOS_EternalDungeonLayer_C.dat: 19 records, 13 bytes each:
    id U16 + effects[3] U16 + compare U8 + needs[2] U16.
  - IOS_EternalDungeonPrize_C.dat: 30 records, 19 bytes each:
    id Int32 + 5 × (itemId U16 + count U8).
  - Real data examples locked:
    layer1 effects=[0,0,0], compare=2, needs=[8,10].
    layer30 effects=[113,0,0], compare=2, needs=[8,10].
    prize1 = 35290x1 + 33989x3.
    prize30 = 35344x1 + 32708x1 + 65301x200.
- Criteria evidence:
  - DungeonCriteria 8: follow NPCs, element, equality, values 3/7.
  - DungeonCriteria 10: follow NPCs, element, equality, values 7/3.
  - Lua follow-NPC evaluator accepts primary OR secondary value for each follow NPC.
- Backend A2:
  - added Opcodes.ETERNAL_DUNGEON=48.
  - EternalDungeonHandler registered in dispatcher/Koin.
  - C48-002 returns S48-004 with requested normalized layer and count=0 offline record list.
  - helper packet contracts for S48-001/002/003/004 are byte-regressed.
  - GameDataManager loads both Eternal Dungeon DAT tables.
- A3 battle/progression is deliberately evidence-blocked:
  - MoonDev fallback proves a server-side design using war style39, RoleCount707 progression and prize table.
  - Its battle generator requires three server-side datasets not present in DVT client/workspace: enriched layer NPC configs, EternalDungeonNpcs, EternalDungeonBaseAttrDefs.
  - MoonDev parser shapes were recovered (NPC records 7 bytes; BaseAttr records 13 bytes; enriched layer includes five NPC configs), but exhaustive workspace scan found no matching authoritative binary tables.
  - C48-001 therefore logs the blocker and does NOT create guessed monsters/rewards.
- Validation:
  - targeted EternalDungeonDataTest PASS.
  - real DAT parser regression PASS.
  - protocol 48 byte regression PASS.
  - full gradlew test build --rerun-tasks PASS 21/21.
  - git diff --check PASS.
- Next:
  - commit/push/deploy A1+A2 to production .69.
  - client test: S.Kiện -> Thần Binh Bảo Khố must open UI and show layer/reward data.
  - Resume A3 only when authoritative server-side EternalDungeon monster tables are recovered; do not invent them.


### 2026-10-06 — Eternal Dungeon A1+A2 production deployed
- Gameplay commit: d51b0d9 Add DVT Eternal Dungeon UI backend.
- GitHub feature/9-pet + main pushed to d51b0d9.
- Production C:\Server-Game\tsm_dodo fast-forward fab7d01 -> d51b0d9 bằng verified Git bundle; giữ nguyên application.conf local.
- Production full gradlew test build --rerun-tasks PASS 21/21.
- Runtime restart sạch; TCP 192.168.1.69:6613 = True.
- Runtime log xác nhận:
  - IOS_EternalDungeonLayer = 19 mốc tầng.
  - IOS_EternalDungeonPrize = 30 tầng thưởng.
- Client test cần xác nhận: S.Kiện -> Thần Binh Bảo Khố mở UI và hiển thị tầng/phần thưởng.
- Battle C48-001 vẫn evidence-blocked vì thiếu server-side NPC/BaseAttr/enriched-layer tables; tuyệt đối không phát quái/reward giả.


## 2026-10-06 — Xếp Hạng / Leaderboard protocol 88 core fixed to current DVT

- User confirmed previous Activity/Eternal Dungeon slice PASS, then moved to Xếp Hạng.
- Authoritative Lua DVT protocol:
  - C:088-001 = leaderboardId(U8) + page(U8).
  - S:088-001 = leaderboardId/page/maxPage/myRank U8 + self score Int32[] + ranked rows.
  - Ranked row = rank/state U8 + roleId Int64 + name + level/element/turn3Element/turn/career U8 + score Int32[].
  - UILeaderboard.maxInfo = 5, and UI page is 1-based.
- Fixed legacy backend bugs:
  - PAGE_SIZE 10 -> 5.
  - page 0-based -> 1-based.
  - empty board maxPage now 1 so client page guard stays valid.
  - leaderboard refresh starts immediately on server startup instead of waiting 5 seconds.
  - career now comes from Characters.career instead of hard-coded 0.
  - rank list capped at 255 because rank/page/maxPage/myRank are byte fields on S88-001.
- Current DVT IOS_LeaderbordInfo_C.dat:
  - exactly 19 boards (not legacy 26).
  - IDs = 1,2,3,4,5,6,7,20,44,29,30,31,8,9,19,10,11,17,16.
  - ID1 = level / Tất cả.
  - ID2 = level / Hệ Địa.
  - ID3 = level / Hệ Thủy.
  - ID4 = level / Hệ Hỏa.
  - ID5 = level / Hệ Phong.
  - ID6 = Võ tướng.
  - ID7 = Bảng vàng.
  - ID20 = Bảng thành tựu.
  - 44/29/30/31 = Lôi Đài Vô Giới variants.
  - 8/9/19 = lôi đài loạn đấu / tổ đội / Tiên Đấu.
  - 10/11 = BOSS Thế Giới sát thương 1 lần / tích lũy.
  - 17 = BOSS Quân đoàn sát thương tuần.
  - 16 = Tử đấu bảng / số lần giết.
- Element enum locked from Lua DVT:
  - Earth=1, Water=2, Fire=3, Wind=4.
  - Old backend mapping (Kim/Mộc/Thủy/Hỏa/Thổ and legacy IDs 32..36) removed.
- Level score:
  - Lua Role.playerMaxLv=200.
  - cumulative level score = level + turn*200.
  - old Kotlin +150 logic removed.
- Warrior leaderboard ID6:
  - CharacterPets has npcId/level/exp/turnKind.
  - best warrior selected by turn -> level -> exp -> row id.
  - NpcDef.element supplies warrior element.
  - scoreInfo wire = [element, turn, level], matching DVT scoreFormat.
- Gold ID7:
  - uses CharacterMoney.gold.
- Achievement ID20:
  - uses authoritative AchievementSystem.calculateTotalScore() from DVT BitFlags/achievement defs.
- Event/BOSS boards:
  - no authoritative persisted score source was found in current Lua/RoleCount/server data for IDs 8,9,10,11,16,17,19,29,30,31,44.
  - They intentionally return empty rather than reusing unrelated gold/pet/level values.
  - Must be connected later to each event subsystem when its score lifecycle is implemented/persisted.
- Regression added: DvtLeaderboardRegressionTest.
  - real IOS_LeaderbordInfo_C.dat count/ID mapping.
  - cumulative level 100 + 2*200 = 500.
  - 11 rows -> 3 pages, 5 rows/page, 1-based/clamping.
  - runtime page2 ranks 6..10.
  - Earth board filters element=1.
  - warrior turn/element/level score and career wire.
  - byte-level S:088-001 serialization regression.
- Validation:
  - targeted DvtLeaderboardRegressionTest PASS.
  - full gradlew test build --rerun-tasks PASS 21/21.
  - git diff --check PASS.
- Next:
  - commit/push/deploy production .69.
  - client test core boards first: cấp độ tabs, Võ tướng, Bảng vàng, Bảng thành tựu, and page switching.
  - event/BOSS leaderboard persistence is a later slice; do not invent scores.


### 2026-10-06 — Leaderboard production source correction before client PASS
- First runtime after commit 79a6631 exposed source mismatch: GameDataManager still loaded server-data/Data/LeaderbordInfo_C.dat legacy, producing 23 boards and warnings for legacy IDs 27,32..36,45.
- Production authoritative client DVT file exists at File.dat/Data-ios/IOS_LeaderbordInfo_C.dat, size 752 bytes, exact 19 records.
- Root fix: GameDataManager leaderboard loader changed from Data/LeaderbordInfo_C.dat to Data-ios/IOS_LeaderbordInfo_C.dat, matching Lua DVT DataManager.OnLoadLeaderboardData.
- Targeted DvtLeaderboardRegressionTest PASS after source fix.
- Full local gradlew test build --rerun-tasks PASS 21/21 after source fix.
- Do not mark ranking runtime PASS until redeployed and log confirms IOS_LeaderbordInfo=19 with no legacy-ID warnings.


## 2026-10-06 — Xếp Hạng / Leaderboard runtime PASS
- User runtime confirmed phần Xếp Hạng PASS.
- DVT protocol 88 / UILeaderboard flow validated in client.
- Core semantics locked during audit:
  - C88-001 = leaderboardId(U8) + page(U8).
  - UI page is 1-based.
  - UILeaderboard.maxInfo = 5 rows/page.
  - IOS_LeaderbordInfo_C.dat current DVT has 19 boards.
  - Current DVT element mapping: Earth=1, Water=2, Fire=3, Wind=4.
  - ID1 = Tất cả; ID2=Địa; ID3=Thủy; ID4=Hỏa; ID5=Phong.
  - ID6 = Võ tướng; ID7 = Bảng vàng; ID20 = Bảng thành tựu.
  - Lua Role.playerMaxLv = 200; cumulative player level = level + turn*200.
- Do not restore legacy leaderboard mappings (e.g. IDs 32..36 or ID10/11 as gold/auction gold) because they do not match current DVT DAT.
- Runtime status: user says PASS; next subsystem can proceed.


## 2026-10-06 — Weekly dungeon + Soul weekly style24 backend implemented locally

- Scope: the two remaining dungeon tabs shown by user:
  - Phó bản tuần -> Bách chiến thiên quân: DungeonData ids18..37, kind3, 20 stages.
  - PB Tuần Cường -> Khiêu chiến Linh Hồn: ids63..82, kind5, 20 stages.
- Lua/client authoritative contracts:
  - both use protocol47 room flow and EWarStyle.WeekDungeon=24.
  - FightField index = col*5+row; server grid enemy front col0 / enemy back col1.
  - Bách all-pass uses C47-22; anchor id18 has allPassPrize34366 and claim flag/costPoint12762.
  - Soul has no all-pass reward in DungeonData.
  - Soul stage N>1 requires prior stage dailyFlag completion.
- Formation membership locked from DVT IOS_Npc_C.dat:
  - Bách ids18..36 -> contiguous ten-NPC blocks from40258; id37 -> explicit 10 Lữ Bố set 40448..40455 +40480..40481.
  - Soul ids63..82 -> exact contiguous 200-NPC block40687..40886, ten NPCs per stage.
- Original backend-only WeekDungeon placement file is absent (WeekDungeon_C.dat is only 4-byte header).
  - Offline reconstructed placement is therefore explicitly documented, not claimed official:
    first5 roster IDs -> enemy col0 rows0..4; next5 -> enemy col1 rows0..4.
    This follows DVT client fightIndex grouping and server BattleConstants.
- Added DvtWeeklyDungeonFormation for both kind3/kind5.
- BattleInstance/BattleManager:
  - style24 battle metadata dungeonId/maxRound/minRound/suppressGenericRewards.
  - maxRound kind6 = failure if enemies remain at limit.
  - minRound kind7 = success after surviving required rounds.
  - up to five room members; one singular fightNpc per player, matching Lua Role.fightNpc qualification.
  - no catch, no generic EXP/gold/drop/KillNum/follower EXP for weekly/Soul.
  - dedicated weekly completion callback bypasses Eve continuation.
- DungeonSystem:
  - validateNormalRoomEntry now permits kind3/kind5 with formation present.
  - normal Lv/room/battle/team/daily gates preserved.
  - Soul stages server-side sequential gate; GM bypassQuestGate can test directly.
  - validateWeeklyRoomStart uses DvtWeeklyQualification with deployed pet, equipped Soul and full skill lookup data.
  - settleWeeklyBattle first-clear writes dailyFlag and grants exact DungeonCombo reward; repeat clear does not duplicate reward; failures do not progress.
  - active weekly run is cleared after settlement.
- Rewards verified:
  - Bách loot tables82..101 are DungeonCombo kind1, 1800s, reward29845x1.
  - Soul tables72..81 and102..111 are kind1, 1800s, reward29846 quantities1..20.
- DungeonRoomRegistry completeForPlayer releases all members after battle so next stage can be created.
- Regression DungeonWeeklyBattleV2Test:
  - 20 Bách rosters real/unique;
  - exact Soul 200-NPC block;
  - round objectives;
  - Bách vs Soul all-pass metadata;
  - room release.
- Existing qualification/needPar/formation/round tests also PASS.
- Validation:
  - targeted weekly suite BUILD SUCCESSFUL 18/18.
  - full local gradlew test build --rerun-tasks BUILD SUCCESSFUL 21/21.
  - git diff --check PASS (line-ending warnings only).
- Next: commit/push, deploy production .69, full production build/restart, then user runtime tests Bách ải1 and Soul ải1 before declaring PASS.


## 2026-10-06 — Weekly dungeon ghost-room fix
- Runtime finding: Bách Chiến create room PASS after production received weekly battle chain.
- Quick Join semantics remain DVT-native: C47-13 joins an existing unlocked room; with no other room it may return result=7 ("Không có phó bản nào để tham gia"). No auto-create invented.
- Real lifecycle bug after weekly battle defeat:
  - Server sent S47-12 result then called DungeonRoomRegistry.completeForPlayer(), deleting room/playerRoom mappings.
  - Lua Dungeon.ReciveDungeonResult() does NOT clear dungeon-room state; it opens UIResult and later invokes Dungeon.LeaveSinglePlayDungeon via C13-4.
  - Therefore client still showed room UI while server had already removed the room; later C47-10 leave became a no-op (ghost room).
- Fix:
  - add DungeonRoomRegistry.resetAfterBattleForPlayer().
  - weekly/Soul-weekly sendDungeonResult now resets starting=false, started=false and prepared=false for all members while preserving room membership.
  - Client can now retry or leave through normal C47 room lifecycle after win/loss.
- Regression:
  - DungeonRoomRegistryTest reproduces started weekly room -> reset after battle -> owner can leave and ownership migrates.
  - DungeonRoomRegistryTest + DungeonWeeklyBattleV2Test targeted PASS.
  - full gradlew test build --rerun-tasks PASS 21/21.


### 2026-10-06 — Soul weekly room UI close compatibility
- Runtime screenshot: Soul weekly / "Cuồng ải 1" starts battle but UIDungeon remains visible behind battle/result.
- Root cause is Lua DVT protocolTable[47][20]:
  - S47-20 reads a dungeonId and only calls UI.Close(UIDungeon) when resolved dungeonData.kind == 3.
  - Soul weekly nodes are kind=5, so sending their own dungeonId is ignored by client.
- Server-only compatibility fix:
  - kind3 weekly keeps sending its own dungeonId in S47-20.
  - kind5 Soul weekly sends current DVT first kind3 weekly node id=18 only as the S47-20 close-UI anchor.
  - Actual battle/progress/settlement dungeonId remains the real kind5 id unchanged.
- Regression: DungeonRoomPacketTest locks kind5 id63 -> close anchor18, kind3 ids stay unchanged.
- Targeted DungeonRoomPacketTest + DungeonWeeklyBattleV2Test PASS.
- Full local gradlew test build --rerun-tasks PASS 21/21.


## 2026-10-06 — Quest 10143 Đại Phá Quân Ô Tản / class4 skill comparator fix
- Runtime screenshot route identified:
  - current map scene12705 = Lều Hương Dũng quân.
  - NPC click1 = Tốt Bá npc38048; it belongs to separate Hương Dũng mission12292.
  - current character runtime has mission10143=1 and no mission12292, so Tốt Bá generic greeting is expected and is not quest10143 target.
- IOS_Mark_C.dat:
  - 10143 = marker mission "Đại Phá Quân Ô Tản", bitId0.
  - 10144 = completion mission same name, bitId91.
- IOS_MarkStep_C.dat:
  - 10143 step1: "Đến Lư Long khẩu đánh bại Khâu Lực Cư, học được thuật đào tẩu".
  - destination scene12820, pos(1390,310), eventKind1/eventId1, checkTeam=true.
- Eve scene12820 click1 = Âu Lực Cư npc14249, event6:
  - mission10143 step>=1 + 10144 absent + skill14001 gate.
  - Talk10735 -> Talk10737 -> fightDataKey11.
  - win: Talk10739 + item46285 + skill14002 + complete10143 + add10144=1.
- IOS_Skill_C.dat:
  - 14001 Thuật trinh thám, maxLv1.
  - 14002 Thuật đào tẩu, maxLv1, preSkill14001.
- Root cause:
  - Eve class4 previously reused compareStep().
  - scene12820/eve6 uses skill14001 ops4/value1.
  - compareStep ops4 means >, making required condition level>1 impossible because skill14001 maxLv=1.
- Full production-Eve class4 inventory: 36 rows, shapes only:
  - ops5/value0 = 12
  - ops5/value1 = 8
  - ops4/value1 = 4
  - ops2/value0 = 8
  - ops2/value9 = 4
- Data forces class4 to use Eve numeric comparator family:
  - ops5 exact equality (unlearned Lv0 / exact Lv1).
  - ops4 >= threshold.
  - ops2 > threshold (e.g. maxLv10 skills use ops2/value9 for Lv10).
- Fix:
  - EveConditionEvaluator conditionClass4 now uses compareEveNumeric(), not compareStep().
  - permanent regression locks mission10143 skill14001 Lv1 ops4/value1 and other real class4 shapes.
- Temporary Lulong/EveSkill probe tests removed before commit.
- Targeted EveConditionEvaluatorTest PASS.
- Full local gradlew test build --rerun-tasks PASS 21/21.


## 2026-10-06 — DVT Eve battle LocationPos / reinforcement fix
- Viễn Chinh Ô Hoàn / Bố Lỗ blocker: scene12820 click13 event17, mission10376 step3 reached FightData20, then BattleManager crashed with BattleGrid row=-1.
- FightData20 has 13 left records: first 10 occupy LocationPos 1..10; final 3 repeat 7,8,9 with npc17071. Repeated LocationPos is handled as sequential reinforcement, not simultaneous fighters.
- Lua DVT keeps NO + NPCID + LocationPos + AI in Eve_FightData; S11-250 is initial battle snapshot and S11-005 calls FightField.RoleAppear for a role joining an existing battle.
- Generic fix: DvtEveBattlePlacement maps positions 1..5 to front rows0..4, 6..10 to back rows0..4; first record per slot is initial, duplicates queue. Dead/vacant queued slots exit old role with S11-001 then spawn next role with S11-005. Pending queues clear with battle cleanup and battle-end checks occur after reinforcement opportunity.
- LocationPos=0 remains evidence-blocked; legacy placement is retained but capped to 10 cells so it cannot crash BattleGrid.
- Whole Eve audit: 497 sides have duplicate valid LocationPos; 523 sides have >10 records, so this is systemic.
- DvtEveBattlePlacementTest locks scene12820 FightData20 = 10 initial + 3 pending; targeted PASS; full local test build PASS 21/21.


## 2026-10-06 — DVT mission class2 comparator root fix / mission11120
- Runtime blocker: quest 'Nguyên liệu trang bị (2)' had mission11120=1 and item44042 VD Thiết Thạch 1/1, but clicking Tốt Bá scene12705/click1 failed to enter the hand-in branch.
- Production log proved the evaluator rejected `class2 mission11120 step=1 ops=2 value=0` as false.
- Real DVT Eve scene12705 event6 hand-in branch requires that condition, then consumes item44042 x1, grants item45605 x1, completes11120, adds11121=1 and updates related guide missions.
- Whole Eve class2 audit proves mission comparator semantics differ from legacy compareStep:
  - ops2/value0 appears 856 times for pStyle1 and represents active step > 0.
  - ops5 is exact equality: many events contain multiple mutually-exclusive ops5 step values for the same mission (e.g. mission11568 values1..6).
  - mission comparator is 0==,1<,2>,3<=,4>=,5==,6!=.
- pStyle3 is completion-flag only (MarkData.bitId), not max(activeStep,flag). Full-suite regression scene12421/eve2 proved active step6 with no completion flag must satisfy pStyle3 ops5 value0 for post-battle door continuation.
- EveConditionEvaluator class2 now uses compareMissionStep and selects flag for pStyle3, active step otherwise.
- Permanent DvtMissionComparatorRegressionTest loads real Eve scene12705/event6 and locks the full mission11120 item hand-in branch.
- Targeted EveConditionEvaluator + EveChainResolver + DvtMissionComparatorRegression PASS 18/18.
- Full local gradlew test build --rerun-tasks PASS 21/21.


## 2026-10-06 — DVT marker mission data-driven root fix / Bình Trưởng scene12992
- Runtime symptom after mission11120 fix: map12992 NPC38050 Bình Trưởng only showed default Lv16-20 training talk; no challenge progression.
- Runtime trace: player had mission11121=1 but flag for Mark11121 bitId588 was absent. Event10 requires class2 mission11121 pStyle3 ops5 value1, so branch was blocked.
- DVT data evidence: Mark11121 has bitId=588 and has no record in IOS_MarkStep_C.dat. Existing server already hardcoded four similar no-MarkStep marker missions (11804,12283,12285,12396) to set completion flags on ADD_STEP.
- Root fix removes production dependence on marker-ID hardcoding: added MarkStepDatLoader + GameDataManager.markStepMissionIds. A marker mission is recognized from data as MarkDef.bitId>0 and missionId absent from IOS_MarkStep_C.dat; legacy list remains fallback only if MarkStep data is unavailable.
- EveResultExecutor ADD_STEP now sets the Mark completion flag for any data-driven marker mission, while preserving the mission step write/client packet.
- EveStateBuilder now auto-reconciles stale data: if a marker mission already has step>0 but its flag is missing, it persists flag=1 and exposes it immediately to the same Eve evaluation. This repairs existing characters such as 11121=1/flag588=0 without reset.
- Permanent DvtMarkerMissionRegressionTest locks: 11121 bitId588 + no MarkStep, ADD_STEP sets flag588, real-step mission11120 does not auto-flag, and stale marker step is backfilled before evaluation.
- Targeted marker/comparator/door-chain tests PASS; full local gradlew test build --rerun-tasks PASS 21/21.


## 2026-10-06 — Danh hiệu / Title backend DVT
- User xác nhận Bình Trưởng / marker mission fix PASS, chuyển sang Danh hiệu.
- Lua DVT authoritative contract:
  - `UITitle.OnClick_Active()` gửi C:005-012 + titleId UInt16; gửi 0 để tháo.
  - Client chỉ cho đeo khi `TitleData.flagId == 0 || BitFlag.Get(flagId)` và không cho đổi trong battle.
  - S:005-012 = roleId Int64 + titleId UInt16; client gọi `Role:SetTitle`.
  - DataManager hiện tại đọc `IOS_TitleData_C.dat`.
- `IOS_TitleData_C.dat` hiện có 33 record, fixed 43 bytes/record. Server thêm `TitleDataDatLoader` và `TitleDef`; chỉ dùng id/nameStringId/flagId, presentation giữ phía client.
- Root gap cũ: PlayerStatusHandler đã persist+broadcast C5-12 nhưng tin titleId do client gửi, không validate tồn tại/sở hữu. Có thể forged client đeo title chưa mở.
- Fix generic:
  - GameDataManager load `IOS_TitleData_C.dat` thành `titleDefs`.
  - `DvtTitlePolicy` mirror đúng ownership gate của UITitle.
  - PlayerStatusHandler chỉ cho titleId=0 hoặc title tồn tại và flag sở hữu; malformed packet/battle/invalid title bị từ chối; valid title vẫn persist Characters.titleId + broadcast S5-12 map-wide.
  - LoginService restore `session.titleId = record.titleId`; trước đây DB/S3-0 có title nhưng session vẫn 0 sau relog, làm other-player/chat có thể mất title.
- Data audit nguồn mở khóa:
  - 18/33 TitleData.flagId trùng Achievement.completeFlag; title mở khi achievement hoàn thành, không phải lúc claim reward.
  - 15 flag còn lại là event/special flags; không tự cấp hoặc đoán nguồn.
- Regression `DvtTitleRegressionTest`:
  - khóa current DVT count=33 và các flag mẫu 1=1558, 4=7151, 101=425, 124=7300;
  - unknown/unowned bị chặn, owned/flag0/remove0 được phép.
- Targeted DvtTitleRegressionTest: PASS 18 tasks.
- Full local `gradlew test build --rerun-tasks`: PASS 21/21.
- `git diff --check`: không có whitespace error; chỉ warning CRLF hiện hữu.
- Next: commit/push/deploy production .69; runtime test đeo/tháo + chat/overhead + relog persistence.

### 2026-10-06 — Danh hiệu production deployed
- Commit gameplay `0c05141 Fix DVT title ownership and persistence` pushed to feature/9-pet + main.
- Production `C:\Server-Game\tsm_dodo` fast-forward 1651df4 -> 0c05141 bằng verified bundle, không đụng application.conf/local production files.
- Production full `gradlew test build --rerun-tasks`: PASS 21/21.
- Runtime restart sạch; production HEAD=0c05141, TCP 6613 LISTEN PID 6516.
- Startup log xác nhận `IOS_TitleData` nạp đúng 33 danh hiệu và server ready trên 0.0.0.0:6613; không thấy ERROR/Exception/FATAL/Koin creation error trong đoạn verify.
- Runtime client test cần xác nhận: danh hiệu đã mở có thể đeo; title chưa mở không thể forged; bấm lại để tháo; overhead/chat cập nhật; relog giữ đúng title.


## 2026-10-06 — Fix cảnh báo "máy chủ sự kiện" / logical Activity GS handoff
- User xác nhận Danh hiệu PASS nhưng mọi trận chiến hiện string 24880 ("chức năng bị hạn chế trong máy chủ sự kiện").
- Root cause khóa từ Lua DVT: `FightField.Create()` hiển thị 24880 khi `Network.activityServerId == Network.serverId`; bridge cũ f301713 cố tình gửi S172-001 với activityServerId=current GS để né reconnect nên production bị client coi là Activity Server ở mọi trận.
- Kiến trúc gốc ĐVT xác nhận có activity/sub-server: UIActivity bitFunction flag2 gọi `Network.ConnectToActivityServer()` -> C:001-100; S:001-100 success gọi `UIServerArea.DoConnect`; sau S:001-002 client tự gọi `UIActivity.TeleportToActivityScene()`.
- Fix backend-only, không sửa Lua/client:
  - S172-001 giờ quảng bá logical activity server ID 90, khác physical production serverId 1; raw IP dùng marker `same-gs` chỉ để vượt guard non-empty, không dùng làm socket destination.
  - LoginHandler hỗ trợ C:001-100: cấp transfer ticket ngẫu nhiên 30s và trả S:001-100 success với GSID vật lý hiện tại, khiến client reconnect lại cùng production host/port.
  - Thêm LoginKind CHANGE_AREA=254; reconnect xác thực bằng one-time transfer ticket thay vì plaintext password.
  - Existing-character session được `loadFromRecord()` trước S:001-002 để tránh race C77-003 bị drop khi Lua auto-teleport ngay sau handoff.
  - Production vẫn chỉ một process/port; activity server chỉ là logical identity, không tạo staging/dev server mới.
- Regression AdminBypassAndActivityEntryTest khóa: logical ID90, ELogin254, S172 bytes với marker same-gs, S1-100 result bytes.
- Targeted test PASS; full local `gradlew test build --rerun-tasks` PASS 21/21.
- Next: commit/push/deploy production, verify login normal + normal battle không còn 24880 + activity bitFunction2 vẫn handoff/entry được.

- Deploy production hoàn tất: production fast-forward 0c05141 -> 1e4c501; production `gradlew test build --rerun-tasks` PASS 21/21 (4m55s).
- Restart lần đầu vướng Gradle `app:run` cha cũ còn sống sau khi kill Java child; đợi lifecycle cũ kết thúc rồi chạy lại `_launch_prod_wmi.cmd` sạch.
- Runtime mới PASS: HEAD=1e4c501, TCP 6613 LISTENING PID 14108; startup 15:28:38 nạp data đầy đủ và `NettyGameServer` ready, không có startup ERROR/Koin/Exception trong log mới.
- Client phải relog để nhận S172 mới. Runtime validation cần user: trận thường không còn string24880; sau đó thử activity bitFunction2 để kiểm tra C1-100 ticket -> CHANGE_AREA -> C77-003 tự động.


## 2026-10-06 — BattlePass / Thông Hành evidenced core — LOCAL BUILD PASS
- Read complete checkpoint before auditing/patching. Verified canonical dev HEAD 8603573 and production HEAD 1e4c501. Canonical EveFlowController/EveResultClassPolicy/EveResultExecutor and QuestItemDeliveryTest changes plus all untracked files were preserved; implementation/build used an isolated clone under the Codex chat work directory from 8603573.
- Lua DVT Logic/BattlePass.lua, UI/UIBattlePass.lua, Data/PassAwardData.lua, RoleCount.lua and protocolTable[77][11] are authoritative. Inspected DVT 1.15 native dump SocketClient/NetworkManager bridge and decrypted DVT client Lua; no native BattlePass gameplay generator exists in dump.cs.
- Wire: C77-11 payload [128,0] query or [128,1] claim-all; S77-11 payload [128,result,request] with result0 failure,1 success,255 unavailable. Malformed requests cannot mutate; battle blocks claims.
- IOS_PassAward_C.dat exactly100 rows x22 bytes: seasons117/118, 50 levels each, cumulative EXP180..9000. Each row preserves one free + two paid slots. Empty itemId0 slots are ignored exactly as Lua; season117/118 level44 paid slot3 has dirty quantity5 but itemId0 and must not create an item.
- Real ActivityTime includes only season117: 2026-09-16 00:00 through2026-10-13 23:59, switch1, all days. Season118 has rewards but NO schedule and stays disabled. S77-1 now sends the complete static season window for PassAward season IDs, rather than today's daily window; other activities preserve existing scheduling behavior. GM never fabricates BattlePass dates/paid access. Uses existing server local/OleDate calendar consistently.
- EXP791, free progress792, paid progress793; paid entitlement BitFlag6060. levelForExp mirrors cumulative Lua thresholds, cap50. Existing item65447 is specialAbility218 RoleCount item, roleCountIndex791/delta1; existing ItemHandler persists EXP and sends S85 updates. No invented combat/mission EXP formula.
- Claims plan the whole eligible free/paid bundle before mutation. Per-character mutex prevents duplicate concurrent claims. InventoryRepositoryImpl commits bag changes, both progress rows, and character_battle_pass_seasons identity in one Exposed transaction; caches publish after commit. Persisted progress must match expected before committing; DB failure changes no cache. Relog uses existing RoleCount/inventory loads. Later paid entitlement claims unpaid levels without repeating free rewards. Insufficient bag rejects the entire bundle without progress consumption.
- Season binding is first-claim adoption preserving existing counters. A different bound season fails closed: automatic EXP/progress/paid reset is NOT guessed.
- DvtBattlePassRegressionTest:5 passing tests cover real DAT/empty slots/EXP item, every threshold, concurrent replay, free+paid catch-up, simulated relog, DB failure, full bag, expiration, mismatched season, full-season packets, request parsing/battle guard/client sync order.
- Full isolated gradlew test build --rerun-tasks PASS21/21 tasks. git diff --check PASS; generated/compiler files excluded.
- Production PassAward and ActivityTime hashes match regression inputs exactly. Production application.conf SHA25625A3A41691F3800F093001F34B98962AE818A0EF098CAA0BBBBD5FADF77F4742 preserved.
- Explicit remaining blockers / incomplete branches:
  - paid purchase via StoreValue saleCredit133: no verified payment/cost/entitlement settlement backend; consumes no invented currency and never auto-grants6060;
  - BattlePass task button sends C77-11 kind129, while Lua receive dispatcher only documents/handles122/128. MissionAward season117 IDs17..22 use flags59..64, all conditions RoleCount>= and item65447x150. Daily/season flag lifecycle and authoritative kind129 response mapping are unresolved; task awards not activated;
  - season118 schedule absent, rollover/reset semantics unresolved. Core deployment is PARTIAL subsystem completion and must not be called full BattlePass gameplay PASS.
- Next: commit only intentional BattlePass source/tests/docs, push, fast-forward production, full production build/restart; verify exact HEAD/listener/log. Client must relog then test open/EXP item/free claim/paid only if6060 already legitimately owned/relog persistence. Do not mark client gameplay PASS from startup alone.

### BattlePass claim persistence hardening before runtime deployment
- Production full test/build of d442561 PASS21/21 in3m28s, after fixing PowerShell stderr redirection (first attempt stopped on a harmless JVM warning). Existing production runtime remains1e4c501/PID14108; no restart yet.
- Final persistence review identified generic RoleCount saveAll can restore an older snapshot. Added durable free_level/paid_level to character_battle_pass_seasons; this ledger is outside generic RoleCount autosave.
- Queries/claims restore cache progress to at least the durable ledger and sync counters before S77-11 success. The atomic transaction locks/checks the season ledger and stores reward/progress/ledger together. Even if RoleCount792/793 are rolled back to0 by a stale save, relog cannot receive those levels twice. Unknown first-season migration preserves legacy values; no reset invented.
- Regression now explicitly simulates persisted RoleCount792/793 rollback to0 after both tracks claimed, constructs a fresh runtime, and confirms no reward write and counters restored to3. Targeted BattlePass suite PASS. Final full local validation and updated production deploy follow.

## 2026-10-06 BattlePass / Thông Hành — claim + mission rewards production PASS
- Production runtime: commit `ad59f5b` on `C:\Server-Game\tsm_dodo`; full `test build --rerun-tasks` PASS 21/21; port 6613 LISTEN PID 11124; `IOS_PassAward_C.dat` loads 100 rows (seasons 117/118), `IOS_MissionAward_C.dat` loads 28 missions.
- Base claim pipeline retained from `d442561` + stale-RoleCount duplicate guard `5ce60ce`: RoleCount EXP/FREE/PAID = 791/792/793, paid entitlement BitFlag 6060, C77-011 kind128, free/paid reward claim atomic and relog-safe.
- Corrected previous assumption: BattlePass mission reward protocol is **C77-011 kind122**, not kind129. Request payload = kind122 + missionId UInt16. Response result: 0 unknown, 1 success, 2 unqualified, 3 received, 4 bag full, 255 function closed.
- `IOS_MissionAward_C.dat` authoritative loader added. 28 rows total; season 117 missions IDs 17-22, season 118 IDs 23-28. Conditions use CheckCondition kind14=RoleCount with operator semantics from Lua; rewards/getFlag/activityId are read directly from DAT.
- Mission claim now validates active BattlePass season, mission activityId, getFlag, RoleCount condition, bag capacity; success gives DAT item/qty and sets getFlag, sends bag + BitFlag sync. Failure does not mark received. Target regression + full local build PASS; production full build PASS.
- Item 65447 remains the DVT BattlePass EXP consumable path already wired to RoleCount/BattlePass EXP.
- Paid purchase audit: client product index 133, StoreValue cashIndex 6005, DAT internal price 670; platform display/billing varies. Lua calls external StoreValue/IAP/MyCard. No backend payment callback evidence exists in this repo, so normal-user paid purchase is NOT guessed/emulated with KNB.
- GM paid test requires no new code: existing `/setflag 6060` persists/syncs the exact DVT paid entitlement and refreshes BattlePass client state.
- Season rollover/reset remains evidence-blocked: Lua only reads RoleCount 791/792/793 and does not reset them; season 118 has no authoritative ActivityTime window in current DAT. Do not invent rollover/reset until DVT/billing/server evidence is found.

### 2026-10-06 BattlePass mission receive hotfix
- Root cause from Lua UI: UIBattlePass.SendCompleteMission sends C:077-011 kind129 + missionId UInt16. Generic MissionAward.SendCompleteMission uses kind122. Server previously only routed 122, so BattlePass button Nhận was ignored.
- Fix commit 7dd5840: ActivityHandler routes both kind122 and kind129 into the same validated mission claim path; response remains kind122 because protocal.lua only has S77-11 kind122 receiver MissionAward.ReceiveMissionAward.
- Local targeted BattlePassMissionAwardTest PASS; full local build PASS 21/21; production full build PASS 21/21. Runtime production HEAD 7dd5840, port 6613 PID 5152.



## 2026-10-06 — Trạc / Poke (戳戳樂) backend production
- Xác định đúng feature: EFunction.Poke=54, UI UIPoke, protocol mainKind 92; không phải Affix/Tẩy luyện.
- Lua DVT: C92-003 gridIndex U8; S92-001 coin U16; S92-002 reward rows [id U8, kind U8, itemId U16, qty U32, gridIndex U8, prWeight U16]; S92-003 success/fail.
- Coin item29507, max999; fallback mua 9 real Point/xu qua UISell44. Cost mở ô = openCount*2+1. UISell46 đổi bàn; IOS_UISell_Formula_C.dat xác nhận 50 Point.
- DAT: IOS_PokeData_C.dat=93 rewards (38/18/16/21 theo kind1..4); IOS_PokeRate_C.dat={1:18,2:4,3:2,4:1}; IOS_PokeBonusReward_C.dat=53 rows.
- Backend: PokeSystem/PokeHandler/PokeRepository; persist coin + rewardId->gridIndex qua relog; mutex; weighted draw chỉ reward chưa trúng; chống mở trùng; rollback state khi grant fail.
- Direct-use specialAbility216 dùng mapping đã chứng minh ở SlotMachine/Welfare: currencyKind=attr1Kind-8, qty=attr1Value*rewardCount. Current Poke currencies kinds 6,10,11,13,14.
- Login đẩy S92-1 + S92-2 vì current UIPoke.OnOpen không gửi query riêng.
- DvtPokeTest target PASS; full local build PASS21/21; production full build PASS21/21.
- Gameplay commit 8f490e3; production HEAD=8f490e3, TCP6613 PID13740; startup log nạp PokeData93/PokeRate/PokeBonusReward53 và server ready.
- Blocker: current UIPoke.lua không trực tiếp dùng IOS_PokeBonusReward_C.dat; chưa đoán bonus/double semantics. changeItemId29508 cũng chỉ khai báo nhưng không dùng, nên chưa tự tiêu.


### 2026-10-06 Trạc/Poke UISell44 alert race hotfix
- User symptom: nhập số lượng mua xu Trạc rồi bấm Đồng ý nhưng không thấy phản hồi.
- Root cause locked from Lua DVT: UIAlert.OnClick_Submit invokes currentClickEvent(...) BEFORE UI.Close(UIAlert). UIPoke callback immediately calls UISell.Launch(44,nil,0,text). Server LAN/offline returned S84-1 fast enough that UISell.ReceiveQuery opened the second ShowCheckMessage on the same UIAlert, then the old submit handler closed it immediately. This made the query look like no response.
- Client IOS_UISell_C.dat was verified to contain sellId44 (moneyString22462/itemString20327), so this was not missing client data.
- Fix commit 0912034: defer only C84-1 sellId44 response by 50ms before quote/send; Lua remains untouched.
- DvtPokeTest targeted PASS; full local build PASS21/21; production full build PASS21/21.
- Production runtime HEAD=0912034, TCP6613 PID6304, startup ready and PokeData93/PokeRate/PokeBonusReward53 loaded.
- Retest: relog, Trạc -> Thêm xu -> nhập quantity -> Đồng ý; second confirmation must now remain visible, then final confirm should deduct Point and increase Trạc coin.


## 2026-10-06 — Linh Xu + Nhận thưởng online
- User xác nhận Trạc/Poke PASS.
- Linh Xu authoritative:
  - IOS_Item_C.dat item65344 = Tiểu Tinh Linh Tệ, icon6082, directUse=1, specialAbility=216, attr1Kind=17, attr1Value=1.
  - Lua define.lua ECurrency.Elf=9; ItemData.lua ItemUse_216=216 (加數值).
  - Mapping DVT: currencyKind = attr1Kind - 8 => 9. Root cause: ItemHandler thiếu ItemUse216, item bị tiêu nhưng PlayerCurrency không tăng.
  - Fix: generic direct-currency item handler persist currency -> consume item -> S35-50 currency sync; rollback currency nếu consume fail. Regression locks item65344 -> currency9.
- Online reward authoritative:
  - UILoginAward EAward.Online=3; click -> SendGetAward -> C87-2 [kind U8, day Int32, count U8].
  - IOS_LoginAwardData group3 exact rows: 10/20/30/60/90/120 phút, flags 2/3/4/5/6/7; first reward 46038x20.
  - Lua GetOnlineTime = elapsed current session + RoleCount.OnlineTime(index10), then /60.
  - Root cause: WelfareHandler chỉ xử lý kind1(Login) và kind4(OpenServer), kind3 bị WrongKind.
  - Fix: handle kind3, validate elapsed minutes + persisted RoleCount10, grant reward, set DVT row flag, idempotent duplicate ACK; persist current-session online seconds into RoleCount10 on disconnect.
- Tests: DvtItemWireTest + WelfareLoginAwardTest targeted PASS; full local build PASS21/21; production full build PASS21/21.
- Commit 13bcd4d pushed main. Production HEAD=13bcd4d, TCP6613 PID12756, startup ready.
- Production application.conf preserved.
- TODO evidence/robustness: daily reset lifecycle for RoleCount.OnlineTime(10) + online reward flags2..7 is not yet separately persisted by a welfare-day marker; do not invent reset semantics until audited.

## 2026-10-06 — Mission11900 auto-navigation data repair
- User reported [Chính] Cứu viện Trương Bảo / Giúp Trương Giác đi does not auto-path.
- Exact mission: Mark 11900. Root cause is client data, not server pathfinder: IOS_MarkStep_C.dat step1-4 and step12 had endSceneId=0, endPosition=(0,0), endEventKind=0, endEventId=0. Lua MarkManager.Navigation refuses navigation when scene=0 or position=(0,0).
- Eve.emg authoritative mapping from exact mission-step gates:
  - step1 -> scene12861, door5, pos(1220,1720), eventKind=2.
  - step2 -> scene12581, door3, pos(2880,1020), eventKind=2.
  - step3 -> scene12584, door4, pos(560,360), eventKind=2.
  - step4 -> scene12589, NPC10269 Tr.Bảo bị hại, clickId17, pos(1490,1020), eventKind=1.
  - step12 -> scene12242, NPC10002 Trương Bảo, clickId7, pos(130,500), eventKind=1.
- Supporting Eve identity: scene12012 click48 NPC10001=Trương Giác pos(510,260), event2 is quest start/accept path; step5-11 existing MarkStep condition already points to injured Trương Bảo NPC10269 in scene12589.
- Patched only navigation metadata in IOS_MarkStep_C.dat; conditions, descriptions, mission semantics and Lua untouched.
- Patched source File.dat/Data-ios/IOS_MarkStep_C.dat and active client C:\Users\QT\Downloads\TSNPCVN_9PET_STAGING\AssetBundles\Data\IOS_MarkStep_C.dat.
- Old C:\Users\QT\Downloads\TSNPCVN client not present.
- Server restart not required; client must fully close/reopen so DataManager reloads IOS_MarkStep_C.dat.


## 2026-10-07 PvP bot VS / Chaos / Mix — production

- Client/wire contract already audited: player-shaped bot uses `EHuman.AutomanualPlayer=28`; bot companion uses `AutomanualNpc=29`. Client does not choose bot skill/target; backend does.
- Implemented custom offline-server matchmaking policy (not claimed to be original DVT AI/rules):
  - real players are preferred for 10 seconds;
  - VS: 1v1, missing opponent becomes bot;
  - Chaos: solo registrations are split across two 5-slot sides, remaining slots become bots;
  - Mix: current party snapshot stays on one side, missing slots become bots.
- Eve registration hooks:
  - VS: authoritative scene 12003 / NPC click 10 / result type2 class7 param8;
  - Mix mission marker 10007;
  - Chaos mission marker 10578.
- Battle safety:
  - kind28 counts as player-shaped for PvP damage/capture immunity but is not client-controlled;
  - kind29 companion is server-controlled and treated as PvP pet;
  - arena battles use `EWar.VS`, `suppressGenericRewards=true`, no PvE EXP/drop/KillNum/capture settlement;
  - standalone arena lifecycle does not resume unrelated Eve after battle end;
  - queue cancels on disconnect or invalid party/player state.
- Bot AI v1 is explicitly custom:
  - revive first when a learned fightWay=8 skill and dead ally exist;
  - HP heal priority below 55%;
  - live `IOS_Skill_C.dat` proved Trị Liệu rows use `fightWay=7`; this classifier is isolated to PvP-bot AI so existing battle-core semantics are unchanged;
  - otherwise use legal learned offensive skill, fallback HandFight.
- Regression:
  - new `DvtPvpBotTest`;
  - fixed two integration regressions found by full gate by restoring the legacy `createFollowNpcFighter(...8 args)` JVM signature and keeping DAT heal discovery out of global `BattleDamageCalc.isHealSkill`.
- Validation:
  - local targeted tests PASS;
  - local full `test build --rerun-tasks` PASS 21/21;
  - merged GitHub PR #4 as squash commit `111877b`;
  - production fast-forward `13bcd4d -> 111877b`;
  - production full `test build --rerun-tasks` PASS 21/21 in 4m02s;
  - production runtime restarted; TCP `192.168.1.69:6613` reachable, listener PID 268.
- Runtime test requested next: enter VS / Chaos / Mix through their normal NPC/event entry and verify after 10 seconds that bots appear, act, can heal/revive, battle closes cleanly, and no PvE reward/capture is granted.


## 2026-10-07 — WorldSimBot mounts production

- User confirmed world simbot movement from 9d9c612 feels more natural; mount rendering was the next blocker.
- Authoritative DVT contract:
  - Lua Role.PlayerAppear / S:003-000 reads instanceId UInt16, horseNpcId UInt16, then MountsLv Byte.
  - RoleController:Ride(npcId) renders a mount only when both mountsDatas[npcId] and npcDatas[npcId] exist.
  - IOS_Mounts_C.dat contains 224 mount records; server production loads the same DVT data.
  - DEFAULT_MOUNT_NPC_ID=18005 is the existing tutorial/default white horse on server.
- Root cause: WorldSimBotSystem buildAppearPacket hard-coded mountNpcId=0 and mountLevel=0 for every simbot.
- Fix:
  - WorldSimBotSystem now receives mountDefs + npcDefs and builds a valid pool from their intersection.
  - Some bots remain on foot; FARMER mounts more often than SOCIAL/IDLER.
  - Mounted bots get deterministic varied DVT-valid mounts, with the default tutorial mount kept common.
  - S:003-000 simbot appearance now sends mountNpcId + mountLevel=1.
  - Existing A*/Ground.mmg path safety and movement smoothing were not changed.
- Validation:
  - compile: BUILD SUCCESSFUL, 13/13 tasks executed.
  - full production test build: BUILD SUCCESSFUL, 21/21 tasks executed.
  - gameplay commit: b9d4470 Add DVT mounts to world simbots.
  - production restart complete; server ready 2026-10-07 16:34:10.
  - 6613 LISTEN PID 2272.
- GitHub note: HTTPS push/fetch from the production SSH session is blocked by Windows Git Credential Manager because the session has no TTY. Do not force-update GitHub history from a connector without reconciling remote main first.
- Visual runtime status: pending user confirmation that mounts render naturally in the client.

## 2026-10-07 — WorldSimBot PvE + real-player party production

- User visually confirmed WorldSimBot mounts PASS, then requested autonomous monster encounters and partying with real players.
- DVT evidence / reused server contracts:
  - world encounter area comes from Eve.emg MineData through MineEncounterSystem.selectMine;
  - party stays on existing mainKind 13 PartySystem/PartyHandler lifecycle;
  - real party combat uses existing BattleManager fighter kinds AutomanualPlayer=28 and AutomanualNpc=29;
  - BattleInstance already drives kind 28/29 through PvpBotAiPolicy, including server-side skill/target/support decisions.
- WorldSimBot changes:
  - FARMER bots accumulate movement only inside valid MineData and periodically enter a 7-15s solo encounter lifecycle, disappear from world, then return after cooldown;
  - solo simulated encounters do not mint EXP/gold/drop or mutate economy;
  - a real player can invite a same-map WorldSimBot; the bot auto-accepts through the existing PartySystem and follows the real leader using Ground.mmg A*;
  - party bots are protected from normal simbot retire/shrink lifecycle while in a team;
  - when the real player starts normal PvE, same-map party bots are injected into the actual BattleManager grid as kind 28, with optional kind 29 companion, and act through existing server AI;
  - only real GameSessions are in battle.playerSessions, so rewards remain attached to real players and bots cannot receive/persist economic rewards.
- Validation:
  - compile gate PASS: BUILD SUCCESSFUL, 13/13 executed;
  - full production gate PASS: BUILD SUCCESSFUL, 21/21 executed;
  - gameplay commit: 6c4d207 Enable world simbot PvE and player parties;
  - production restarted cleanly; HEAD 6c4d207; 6613 LISTEN PID 10548;
  - runtime ready 2026-10-07 17:03:31, Eve 3797 scenes / IOS_Mounts 224 loaded, WorldSimBot service started.
- Runtime/client validation pending: invite visible world bot -> party UI -> follow -> trigger normal PvE -> verify bot kind28/optional pet kind29 appears and auto-acts; verify kick/leave cleanup.

### 2026-10-07 — WorldSimBot party cross-map follow
- Follow-up hardening after 6c4d207: party simbots now migrate with the real leader across normal-world map changes instead of becoming stale in the old population.
- Old-map bot/pet disappear packets are emitted, the bot is placed on the nearest Ground.mmg walkable point around the leader, and normal A* following resumes on the destination map.
- Deliberately limited to instanceId=0 so dungeon-specific bot systems remain isolated.
- If a party bot cannot be migrated and its old scope is destroyed, PartySystem removes it to avoid a stale member entry.
- Targeted gamelogic compile PASS; final full production gate PASS 21/21.
- Gameplay commit: 5ef1e45 Keep party simbots across map changes.
- Production ready 2026-10-07 17:15:08; 6613 LISTEN PID 10928.


### 2026-10-07 — WorldSimBot party consecutive-battle lifecycle fix
- User runtime symptom: bot joined the first PvE battle, then left/disappeared from party before the next battle.
- Root cause: tick() built active scopes only from real sessions with battleId==0. If the party leader was the only real player on the map, entering battle made the scope look empty and called partySystem.leaveParty(bot.id).
- Fix: logged-in normal-world sessions retain their map scope even while battleId!=0; only battleId==0 sessions receive world visibility/movement processing.
- Disconnect/true normal-world scope exit still performs cleanup.
- Compile PASS; full production test build PASS 21/21.
- Gameplay commit: adc021a Keep simbot parties through battles.
- Runtime validation from user: bot follows and enters subsequent battles OK.


### 2026-10-07 — WorldSimBot visual variety / pet / mount
- User confirmed party/combat lifecycle PASS and reported outfits too repetitive plus some bots had no pet and walked.
- Root cause: only 4 outfit profiles; pet assignment was probabilistic; mount assignment intentionally left FARMER/SOCIAL/IDLER subsets on foot.
- Fix:
  - build 32 deterministic equipment combinations from DVT item pools fitType 1..5, only openUsed=0 items with both male/female pictures;
  - keep original conservative sex/hair profile range; only outfit combinations are expanded;
  - every WorldSimBot receives one of the already-proven world pet IDs 40428..40431;
  - every WorldSimBot receives a DVT-valid mount from mountDefs∩npcDefs; default 18005 remains common, other valid mounts remain deterministic/varied.
- Movement, Ground.mmg pathing, PvE AI and party lifecycle unchanged.
- Compile PASS; full production test build PASS 21/21.
- Gameplay commit: 6cbba63 Diversify simbot outfits pets and mounts.
- Production ready 2026-10-07 18:30:31; Netty 0.0.0.0:6613 ready and TCP check PASS.
- Visual runtime validation pending user confirmation after relog/map refresh.


### 2026-10-07 — WorldSimBot expanded pet pool
- User requested more pet variety after confirming outfit/mount/party behavior acceptable.
- Audited IOS_Npc_C.dat (8427 records) using the same NpcDatLoader contract as the server.
- Existing proven pets 40428..40431 are kind=31, canBeCatch=true, valid models, combat-ready.
- Selected 29 high-level pet candidates from the same kind=31/catchable family, mostly level 150-200 and rarity 3-5.
- Runtime validates every configured pet against npcDefs: kind=31, canBeCatch, picId>0, level>0, hpBase>0. Missing/invalid IDs are excluded safely.
- Weighted distribution: 70% common high-grade pool, 22% rare pool, 8% legendary pool.
- Common pool includes Hạ Hầu Đôn/Uyên, Trương Cáp, Bàng Đức, Tuân Úc, Hứa Chử, Điển Vi, Quách Gia, Triệu Vân, Trương Phi, Quan Vũ, Hoàng Trung, Mã Siêu, Lữ Mông, Chu Thái, Cam Ninh, Thái Sử Từ, Chu Du.
- Rare/legendary pools include Lục Tốn, Tả Từ, Vu Cát, Gia Cát Lượng, Tư Mã Ý and higher-tier level-200 variants.
- Compile PASS; full production test build PASS 21/21.
- Gameplay commit: 6e61a95 Expand world simbot pet variety.
- Production restarted and TCP 6613 reachable.
- Runtime visual validation pending.


### 2026-10-07 — WorldSimBot real headless solo PvE
- User requested autonomous world bots to actually fight random encounter monsters instead of only disappearing for a fake 7-15s timer.
- Authoritative encounter source remains Eve.emg MineData: MineData.events -> NpcEventData -> unconditional conditionClass=0 -> resultType=3 -> SceneEveData.fightDatas[resultMeanNo]. Bots skip mines without an unconditional fight result rather than guessing quest-specific combat.
- Replaced simulated solo timer with BattleManager.createWorldSimBotPveBattle().
- Headless battle uses the normal BattleInstance / BattleSkillResolver / resolveNpcActions / DVT Eve_FightData enemy placement pipeline.
- World bot enters as EHuman.AUTOMANUAL_PLAYER kind 28; its pet enters as EHuman.AUTOMANUAL_NPC kind 29. Both act entirely through server AI.
- A same-map real session is used only as the stat/learned-skill template for the bot fighter; it is never registered in battle.playerSessions, never receives battle packets, and its session.battleId is not changed.
- Headless battle has suppressGenericRewards=true, capture disabled, no playerSessions, no EXP/gold/drop/KillNum/daily-progress/persistence/economy mutation.
- On real battle completion, callback records win/loss + rounds, applies the normal 27s encounter cooldown, waits 1.2s, then allows the bot/pet to reappear in world. Failed battle creation retries after 5s; BattleManager exception path also releases the bot via fail-safe callback.
- Existing party PvE, dungeon bots, player battles, movement and Ground.mmg pathing are unchanged.
- Targeted gamelogic compile PASS: BUILD SUCCESSFUL, 5/5 tasks.
- Full production gate PASS: BUILD SUCCESSFUL, 21/21 tasks.
- Gameplay commit: 9ea2ec3 Run real headless PvE for world simbots.
- Production restart clean: WorldSimBot started 2026-10-07 20:23:32; Netty 0.0.0.0:6613 ready; TCP 6613 PASS; HEAD 9ea2ec3.
- Runtime encounter validation pending: while a real player stays on a MineData map, watch for `WorldSimBot solo PvE started`, `WorldSimBot headless PvE start/end`, and `WorldSimBot solo PvE finished` with fight id, win/loss and round count.


### 2026-10-07 — WorldSimBot native battle-cloud visual
- User provided a screenshot of the normal DVT world battle/loading cloud and requested WorldSimBots to look the same when they encounter monsters.
- Authoritative client evidence:
  - S:011-004 parses kind + roleId + npcIndex + warKind and calls RoleController:SetWar(warKind).
  - RoleController:UpdateViewTexture() uses UniversalJmp.GetFight() whenever Player/Players/ForeignNpc war != EWar.None; this is the native world battle cloud from the screenshot.
  - SetWar propagates the war state to followers for non-party players, and FollowNpc UpdateViewVisible hides the pet while war != None.
- WorldSimBot solo encounter now keeps the world entity present and sends S:011-004 with EHuman.Players + EWar.EVENT instead of despawning bot/pet.
- A 650ms lead-in is kept before starting the real headless BattleInstance; the native cloud remains visible for the entire server-side fight.
- After battle completion, the bot stays in war state for 1.2s, then receives S:011-004 EWar.NONE and returns to its normal appearance/pet.
- Failed battle creation clears war state immediately. In-battle bots are protected from retire/population-shrink cleanup so the cloud cannot vanish mid-fight.
- Targeted gamelogic compile PASS: BUILD SUCCESSFUL, 5/5 tasks.
- Full production gate PASS: BUILD SUCCESSFUL, 21/21 tasks.
- Gameplay commit: 5773573 Show native battle cloud for world simbots.
- Production restarted; HEAD 5773573; TCP 6613 PASS.
- Runtime visual confirmation pending user observation on a MineData map.


### 2026-10-07 — WorldSimBot cloud retry + smooth movement
- User runtime feedback after 5773573: bot still disappeared instead of showing native battle cloud; world movement looked jerky/stuttered.
- Movement root cause confirmed in code: WorldSimBot tick was 900ms while each update could skip 2-4 A* cells, so clients received large position jumps roughly once per second.
- Movement fix:
  - TICK_MS 900 -> 180ms;
  - FARMER/SOCIAL/IDLER max path advance 4/3/2 cells -> 2/1/1;
  - per-behavior move cadence tightened to 180-280 / 240-380 / 360-560ms.
- Client contract rechecked: EHuman.Players=2 is correct; S:011-004 calls RoleController:SetWar(); UniversalJmp.GetFight() is the native battle-cloud texture; random triggered battle semantics use EWar.DoingFight=23.
- Cloud hardening:
  - solo encounter world war state now uses DoingFight=23 instead of Event=1;
  - S:011-004 war-state is sent twice, 120ms apart, before starting headless BattleInstance;
  - bot remains a world entity and is protected from retire/shrink while in battle.
- Targeted gamelogic compile PASS: 5/5 tasks.
- Full production gate PASS: 21/21 tasks.
- Gameplay commit: 7cbaf48 Smooth world simbots and retry battle cloud.
- Production restarted; HEAD 7cbaf48; TCP 6613 PASS.
- Live runtime evidence after deploy: autonomous bot on map 63000 repeatedly ran real headless fights 14/24/32/36, winning in 1-3 rounds; server-side encounter lifecycle is healthy.
- Client visual/movement confirmation pending user retest.

### 2026-10-07 — WorldSimBot battle-cloud runtime PASS
- User runtime confirmed battle cloud now appears correctly when autonomous bot encounters monsters.
- Movement smoothness also confirmed improved.
- Effective gameplay baseline: movement smoothing 7cbaf48 + role re-assert cloud fix 0bcc815.
- Headless PvE remains real server-side BattleInstance using Eve.mmg/Eve.emg MineData fight resolution, with no bot economy rewards.


### 2026-10-07 — Production SSH access — tsm-prod alias
- Standard production SSH alias on the user PC: `tsm-prod`.
- Local SSH config file: `C:\Users\QT\.ssh\config`.
- Config:
  - `Host tsm-prod`
  - `HostName 192.168.1.69`
  - `User Administrator`
  - `ServerAliveInterval 30`
  - `ServerAliveCountMax 3`
- Verified manually with Windows OpenSSH 9.5p1: `ssh tsm-prod "whoami"` -> `win-33r6op5go1m\administrator`.
- Preferred user-side commands: `ssh tsm-prod`, `ssh tsm-prod "git -C C:\Server-Game\tsm_dodo status"`, and `ssh tsm-prod "git -C C:\Server-Game\tsm_dodo rev-parse --short HEAD"`.
- User-side PowerShell / Windows OpenSSH is healthy and should be preferred over typing the full `Administrator@192.168.1.69` target.
- ChatGPT automation caveat: Windows `ssh.exe` launched from the Remote Desktop Commander non-interactive process exited code 1 with no output even though the same command works manually. For automated remote work, keep using the proven Git Bash SSH transport or upload/run Python scripts on production when quoting becomes complex.
- Do not interpret the OpenSSH post-quantum key-exchange warning as a connection failure; it is only a warning.
