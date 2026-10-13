# DVT 12511 – four original Christmas tree next-day NPC routes

Checkpoint 2026-10-12 on PR #41, following commit f2246a5. Manual in-game E2E remains deferred until owner's consolidated sweep.

## Exact source provenance

Original `gamedata/Eve.emg` scene **49944** has unique visible NPC placement **1**, NPC type **38001**, at **(850,700)**. ClickNpc **Eve1** (`when_happen=[true,false,false,false]`) has four independent source conditions: condition class2, param12511, pStyle1, ops5, stage **3/7/12/18** at original numbers 24/25/26/27. Each stage branch includes original effects class2 param12547 style1 value1; class2 param12511 style3 value0; class2 param12512 style1 value1. An original class14 source time condition also exists in Eve1. No time check or result changes were made.

Original MarkStep has **(0,0,0,1,1)** on each of these steps but its descriptions explicitly instruct `Làm rất tốt, cách ngày đến cây thông Noel nhận thưởng`. Neighbor stages guide to map49945 at Santa NPC1. Mapping the four **blank scene and coordinates** to source Christmas tree NPC38001 is more faithful than guessing an arbitrary destination or silently completing a reward. New END for each: **(49944,850,700,1,1)**, representing real NPC placement1. Navigating to the NPC does not itself trigger Eve reward; game still requires the original qualifying source event/time logic.

The 12517 observation stages 2/4/6/8 remain blank: map49949 has Door1 an EXIT trigger and merely pointing to it can skip the observation experience. Other blank-END cases include riddles/check-in, choices and maze paths; do not invent destinations.

## Exact binary and metric changes

- Git/DEV `File.dat/Data-ios/IOS_MarkStep_C.dat`, 892087 bytes, SHA256 **ab39587a5dacbd65ca60490bf58c5c3da680e826b72b195fc69f80c388f4ec07 → e48a4b7f50f6dfc4d3b902279bdda5efea0c783c3eb46f7c300a80db1bec13ad**.
- Two independently localized staging client binaries, 928425 bytes each, SHA256 **8b7cbe97e8ec96a982adb2ed2011d7d816bd9b3648a56ae1e742ab8d55ad8b68 → 93afa327618e1632b80bb0ddcd1fb1da73e3a6f0584da2046048d0be1ac35acc**.
- Exactly **24 byte values differ** per edition, exclusively in four specified 8-byte END fields. All other MarkStep and client localized text unchanged. Binary hash-locked patcher creates backups and supports idempotence after exact known full sha.
- **4505** steps, **5932** source-located targets (was5928), **63** raw blank-END (was67); **8** conditional-guided while unmet; **55** without condition guidance (was59). Raw all-zero END count remains **63**, because these four old fields were nonzero event-kind1/event-id1.
- **87** unlocated condition targets remain a separate audit bucket; many are item crafting or bespoke hunt sources rather than automatic travel.
- No changes to Kotlin, Eve, Lua, NPC flags, mission state, inventory or reward logic.
- Dedicated test `tools/test_dvt_12511_tree_return_navigation_20261012.py` verifies all four original Eve reward branches, unique map/NPC ownership, neighbors, SHA, idempotence, fail-closed unknown file, zero invalid destination refs and full audit counts.

## Deferred consolidated manual E2E

For quest12511 stages 3,7,12,18, click active mission before daily reset and after next-day eligibility. Expected navigation enters scene49944 and targets NPC38001; talking too early should not grant unauthorized reward, and valid qualified interaction should follow original Eve effects exactly once. Verify following Santa quest stage, relog and cross-map path. No manual character testing executed in this stage.
