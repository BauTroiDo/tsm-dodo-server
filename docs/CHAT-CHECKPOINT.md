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
