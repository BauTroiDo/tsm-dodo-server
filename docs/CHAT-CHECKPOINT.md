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
