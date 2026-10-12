# DVT quest12509 — two original Christmas socks step routes

Date 2026-10-12, branch PR #41. This follow-up modifies ONLY the MarkStep END for mission12509 steps 5 and 6. No player states, Eve archives, script effects, items or Kotlin quest progression are changed.

## Source evidence
Original DVT Eve archive `gamedata/Eve.emg`, scene49944, NPC placement3, NPC type38286, original position (250,720), visible and click-trigger Eve8.

* At mission12509 stage5, Eve8 has exact condition (class2,param12509,pStyle1,ops5,value5); its source result consumes item29451 by 2 and increments quest12509 step +2. This is the original socks turn-in mechanism, not an arrival trigger.
* At stage6, Eve8 condition (class2,param12509,pStyle1,ops5,value6); its result consumes item29466 by 2 and increments quest12509 step +3.
* Nearby source MarkStep stage4 AND stage11 already guide to the same authentic NPC placement3 at map49944 (370,730), distinct from the raw NPC's (250,720). Reuse the established MarkStep approach waypoint, not a guessed Door, scene entry or unrelated NPC.
* Stage7 remains map49944 (410,390) NPC5; all original stage effects are untouched. The two paths deliberately navigate *to* NPC3 and do not auto-consume socks, auto-advance, bypass a choice or grant items.

## Changes and integrity
* MarkStep mission12509 steps5,6: END (0,0,0,0,0) → (49944,370,730,1,3).
* Server/Git binary 892087 bytes, SHA256 e0d5135b01ea5331c69384174a6ab0a0db05a7aa1189dfd1b3d7aa94cdbeca35 → ab39587a5dacbd65ca60490bf58c5c3da680e826b72b195fc69f80c388f4ec07.
* Both localized client staging binary editions 928425 bytes, SHA256 3521cd4e21059ecac3f2fe9b82de87f6fb892e221997f088673c2f2d21835a37 → 8b7cbe97e8ec96a982adb2ed2011d7d816bd9b3648a56ae1e742ab8d55ad8b68.
* Exactly 16 changed bytes per edition, inside two 8-byte END slots; text translations unchanged. Guarded CLI `tools/patch_dvt_12509_socks_20261012.py` validates original Eve, mission layout, original NPC and exact stage results, input version SHA, packed byte locations, backup and idempotence; rejects unknown full SHA even if new END fields already match.
* End-target raw gap 69 → 67; 8 condition-guided (only while unmet); remainder 61 → 59 need source-specific classification; source-located targets 5926 → 5928, 4505 MarkStep records, 0 missing static NPC/door/scene references.
* Other 61 earlier raw blank routes are mostly geographic/check-in riddles, next-day Christmas tree reward, scene observation or multi-option maze/battles. Do NOT assign fabricated NPC/door destinations. 87 unlocated objective-target fields (70 steps) remain separate; most are crafting/collection sources, so do not misroute to the final hand-in NPC while requirements are unmet.

## Regression & deferred gameplay checklist
Version-locked tests in `tools/test_dvt_12509_socks_source_routes_20261012.py` assert exact Eve stage/result provenance, original neighbor targets, 4505 source rows, no invalid references, 67/8/59 split and full-SHA fail-closed patch behavior.

Manual one-pass owner-approved E2E is *not yet performed*: stage5 with and without required Noel socks; stage6 with and without red-white socks; click quest → scene49944 NPC3 auto-walk; correct rejection dialogue for lacking items; on valid interaction allow original Eve to consume exact required socks and increment source-defined +2/+3; no double rewards, no progression on arrival; test relog and cross-map route.
