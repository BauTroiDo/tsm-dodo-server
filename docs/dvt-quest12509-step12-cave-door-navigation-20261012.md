# DVT quest12509 stage12 — original cave door source-backed auto-navigation

Date 2026-10-12 (VN). **Manual in-game validation deliberately deferred as requested.**

**SOURCE CLARIFICATION (2026-10-12):** `when_happen=[False,True,False,False]` at scene49946 Eve2 refers to the actual **Door2 event**, not automatic map-entry firing. The step12509/12 END already correctly points to Door2 and is **unchanged**. See `docs/dvt-door-trigger-parity-correction-12936-12509-12938-20261012.md` for the revised trigger classification.

## Root cause and verified DVT event

Quest12509 step12 "Đến sơn động của Khảm Bộc Từ tìm con trai cô hàng rong" originally has no acquisition condition and **END=(0,0,0,0,0)**. Mission click in original `lua/Logic/MarkManager.lua` uses END but has no destination.

Original `gamedata/Eve.emg` scene49946, **scene-entry Eve2** (`when_happen=[False,True,False,False]`) class2 mission12509/pStyle1/ops5/value12 **opens door2 and door3** by original Eve resultType2/class7/param2 and param3. The real doors lead to original cave **scene49947 and scene49948**, where scene-entry Eve2/4/5 check mission12509/step12 and follower NPC38287 branches. **Step13 already uses the exact real door2 waypoint** `(49946,2370,150,2,2)`. Therefore step12 may use the *same first entry* without guessing coordinates. The alternate door3 and scene49948 remain unaltered. Step11 vendor38286, step13 original door2, step14 quiz NPC22092, and step16 Santa completion are unchanged.

## Strict data-only patch

`tools/patch_dvt_12509_cave_stage12_door_20261012.py` checks Eve2 original step12 scene-entry and both door unlock effects, both real source destinations and their cave return entrances, 12509/12 original no conditions, matching previous stages, known source edition SHA, precise eight-byte END field and idempotency. Backup/atomic save/sha readback. **Only 7 actual bytes differ within 8-byte END field**:

- Repo + DEV MarkStep (892087 bytes) offset **768003–768010**. SHA old `2edf6533584247e20f9d18e19753595b59d9c47fde6960d0630a38f8d1f0532f` → **`4acb01edebaf1f2fee8240bd4bb61d949c6146515da792a1869409cd66f20939`**.
- Both client MarkStep editions (928425 bytes) offset **769823–769830**, SHA old `78c2f74b0b4ad82932ad385b8f92cb68315a4ce803d405293f0472c7b4dca364` → **`de917171a4ae9acdab65566a93d146e70f5fca691724cbee9a6d03f848b3d2e2`**.
- Static navigable targets **5755→5756**, missing END count **240→239**, record count still **4505**. No other quest field, script, reward or account data changed.
- `tools/test_dvt_12509_cave_step12_door_20261012.py` protects real door2+door3, scene entry, adjacent steps, exact SHA, idempotency and zero scene/NPC/door invalid references.

**Deferred consolidated E2E:** Use test quest12509 stage12, click: enter scene49946 through map49944/door2 and then the opened cave entry scene49947; verify alternate scene49948 can still be entered; confirm original scene entry condition and stage transition with/without follower38287; do not assume completion merely from the waypoint. Test stage13 door and stage14 question NPC independently.

## Kotlin evaluator audit (read-only)

Kotlin `EveConditionEvaluator.kt` supports class10 dialogue surface/choice matching and `EveFlowController.chooseEvent` snapshots the current surface/choice code and resolves branches; no ungrounded Kotlin adjustment made. Missing `conditionClass17` appears in only original scenes12721 and12722 (16 branches): semantics vary (params0/2/3, ops0/5/6), and no source-confirmed definition was recovered in this pass. **Leave fail-closed**; add to deferred E2E/source-reverse-engineering backlog. Do not equate static source regression with manual quest completion.
