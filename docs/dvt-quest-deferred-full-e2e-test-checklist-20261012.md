# DVT quest-system deferred full E2E test register

**Requested by server owner 2026-10-12**: continue implementing source-verifiable quest fixes without stopping for per-fix in-game manual testing. **Preserve complete progression/history and conduct ONE consolidated in-game test pass later**. No entry below may be marked real gameplay PASS from Python/GitHub CI alone. This list should remain version-controlled and update as each fix is pushed/deployed.

Source of truth for each change: GitHub PR #41 and commit-specific docs, original `gamedata/Eve.emg`, original DVT Lua and `_native_dump_dvt115` for protocol/UI, client staging and DEV actual SHA verification. Latest known DEV stage source on this entry: commit `44be307`, plus upcoming stage10/14 patch under review.

## Deferred in-game checklist (all NOT TESTED E2E)

| Quest / stage | After source-backed patch, expected actual in-game interaction | Manual E2E |
|---|---|---|
| DVT beginner start/tutorial | Character creation must follow DVT starter story rather than classic; quest10821 tutorial skip class15 still needs verified semantics | Not tested; known unresolved |
| 10122/2 | Click quest → route to scene11002 genuine spy17018 placement5; interact/enter original scene11301; verify dialogue branch | Not tested |
| 10126/4 | Click quest → same genuine spy scene11002; enter palace; with follower10099 trigger NPC14216 dialogue/advance to stage5 | Not tested |
| 10290/1–2 | Click → scene14566 NPC35013; receive item29095 Đồng Tâm; advance → turn in to Vương Doãn scene14173 | Not tested |
| 10852/1 and return | Capture genuine moon rabbit from cave, turn in to Ngô Cương scene11021 NPC15153, verify no lost reward/progress; **class13/value40 reward remains unsupported/fail-closed** | Not tested, reward blocker unresolved |
| 12106/6–15 and 18–27 | For each of ten pairs, acquire 5 item29342, click mission → actual poisoned soldier17491 scene30892 or30891; hand in and advance each half-step; verify quest step16/17,28/29 unaffected | Not tested |
| 12158/1–5 | Click each Qin tomb stage to original 5 verified NPC/scene targets; item hand-in should not skip stages | Not tested |
| 12162 turn-in | Obtain actual 30 item32043 from real chance-based bag46285 via quest10120, click mission → scene12808 NPC15050; atomic item consumption and quest progress | Not tested |
| 12509/8 | Three original merchants: item26106×1,26013×5,26016×5; click mission after collection → true chef NPC15421 scene49944; verify consume/progress | Not tested |
| 12509/10 | Click with fewer than 10 rods → source NPC24005 scene49946; receive item29454 until 10; next click → receiver NPC15422 same scene, consumes10 and advances | Not tested; stage10 source navigation fixed (commit85d0ae2) |
| 12509/12 | Click → map49946 original source-opened cave door2→scene49947, confirm original scene-entry; door3→scene49948 remains possible; verify branch with/without follower38287 | Not tested; route fixed (commit d3b54c5) |
| 12509/14 | Click → original Khảm Bộc Từ question NPC22092 scene49946 placement11; correct answer advances, wrong does not; no fabricated rewards | Not tested; source navigation fixed (commit85d0ae2) |
| 12509/16 | Capture follower38136 in scene49946; click after capture → scene49945 **Door2 at (280,540) triggers Eve9**, not the Santa NPC; finish/removal of follower38136; alternative cave Door3→Eve3 preserved. Without follower the quest must not finish | Not tested; earlier Santa NPC waypoint corrected to real Eve9 Door2 |
| 12998/1–3 summer festival | At step1 click to inn scene12007 NPC38414; step2 click to teahouse scene12018 NPC38412 with secondary NPC38413, step3 returns to NPC38411 scene12003; verify dynamically hidden NPCs appear only at right quest steps, rewards, and the original Chinese stage text separately | Not tested; 1–2 navigation source-backed, native client/Kotlin E2E deferred |
| 12936/4,9,21 relationship | At 4 click → Quách Nữ Vương NPC11123 map12421 then letter stage5; at 9 → Chân Mật NPC11074 map12701 then follower stage10; at 21 → Mạnh Hoạch NPC14644 map12701 then Chúc Dung stage22; verify quest-gated hidden NPC visibility, step progress, relog consistency | Not tested; 3 navigation endpoints source-backed |
| 12938/4–6 Qixi | Step4 click → aunt NPC15133 scene12031 placement13; step5 click → **Door6 (740,680), Eve19** to advance and show original NPC27007 placement14; step6 NPC dialogue, verify dynamic hidden visibility and no premature success | Not tested; two source-backed endpoints and Door-vs-NPC regression in latest patch |
| 12936/16 scripted Door | At stage16 enter map12403, **interact with Door7 at (100,400)** to trigger Eve10 stage16→17; after that use stage17 original NPC12059 placement8; verify never finishes merely by entering map | Not tested; corrected former NPC8 target to original Door7 |
| 13221/2; 13306/1 | Kill NPC-specific monsters in map49991/map54974; auto-navigation remains pending until original battle victory advances quests | Not tested |
| Quest window mission-click / map unlock | UI mission click launches correct native path, scene/door auto path and gated map visibility; no classic-versus-DVT fallback; test across beginner/mainline/side quests | Not tested |
| Quest item resultClass1/pStyle3 | Hand in multi-stack item correct counts; inventory must be atomic, never delete beyond quest demands | Not tested |
| Reward resultClass13 (89 effects), 11 (35), 15 (tutorial skip), condition17 and active-time | Verify original semantics from DVT source and test only AFTER safe implementation; do not silently acknowledge or skip unsupported effects | Blocked/unimplemented where noted |

## Repeatable consolidated E2E procedure

1. Snapshot DEV player DB, relevant quest inventory and backup MarkStep. Record Git commit, server and both client exact SHA, game port and JVM process.
2. Use one or more disposable character saves on DEV with controlled quest-step position, inventory and follower state. Never reuse a valuable player's profile.
3. For each row: click the mission text, verify chosen map/door/NPC and any dynamic spawn; perform the original Eve interaction, confirm quest step progression, item consume/grant, stage flags and recovery after disconnect/relogin. Exercise incorrect answers, insufficient item count, duplicate-click and full inventory where meaningful.
4. Capture logs/protocol packets (especially navigation, scene-entry event, resultClass11/13/15, conditionClass17) and compare to original Lua/IL2CPP, not to classic TS PC shortcuts.
5. Record exact PASS / FAIL / SKIP and reproducible scene+mission step here, with a companion regression test/fix PR for failures; do not equate static audit with game E2E.

## Maintenance contract

For every next source-backed fix, extend this register with user scenario and link to commit-specific docs, record local tests, GitHub CI and actual DEV/client hash plus backup in PR#41. Continue implementation now as requested; postpone manual in-game interactions until the consolidated pass. **No unsupported reward semantics to be guessed or bypassed.**
