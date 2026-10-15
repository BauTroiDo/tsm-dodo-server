# DEV deployment checkpoint — gold writer fence + C031 inn fee (2026-10-13, Asia/Ho_Chi_Minh)

## Scope
This is an **actual DEV source deployment, compilation and Java restart**, not activation of the dormant SQL two-character trade coordinator. Source commit `955333c1b3d4bab70a299e2b4542ce988585e458` on branch `fix/dvt-quest-nav-parity-20261011` / draft PR #41, preceded by `ee3bd9f` and followed by documentation-only checkpoint `f9a15f9`. The Python/Kotlin tests remain those of source commit 955333c.

## Verified GitHub gates
- [Server CI run 38312363431](https://github.com/BauTroiDo/tsm-dodo-server/actions/runs/38312363431): completed/success, head `955333c1b3d4bab70a299e2b4542ce988585e458`.
- [DVT data audit run 38312363403](https://github.com/BauTroiDo/tsm-dodo-server/actions/runs/38312363403): completed/success, same head.

## Remote and preservation
- Desktop Commander device `DESKTOP-Q96IFHO`, ID `d11d42f7-d646-4c3d-80f5-334bcfdf5d00`: **ping responded**. Git for Windows OpenSSH `C:\Program Files\Git\usr\bin\ssh.exe` successfully used alias `tsm-prod`; destination `WIN-33R6OP5GO1M`.
- Windows source checkout `D:\Games\Ts-Offline\_dvt_nav_batch_20261012` on `fix/dvt-quest-nav-parity-20261011` had local HEAD `ee3bd9f` and a **dirty MoneySystem.kt** corresponding to a noncommitted variant of the cancelled-waiter patch. Executed `git fetch origin fix/dvt-quest-nav-parity-20261011`, resulting remote-tracking ref `f9a15f9a331e6066ba97c788ed04dd0c1f80cd1a`. **No checkout/reset/force push or local dirty work overwrite.**
- DEV source repo `C:\Server-Game\tsm_dodo` remains on local branch `main` at `f6c228b`, with numerous independent tracked/untracked DVT hotfixes. **No full checkout, pull or bulk source overwrite**. Exact source staged from GitHub's fetched branch was verified by matching `git hash-object` to `git rev-parse origin/branch:path` for both selected files; a read-only byte comparison of each DEV file versus staged source confirmed the differences were the desired MoneySystem writer fence/staged trade methods and C031 rest debit alone, preserving existing child-follower pet handler behavior.

## Selective source deployment and SHA-256
Backed up original DEV files under:
`C:\Server-Game\tsm_dodo\backups\dvt_gold_fence_955333c_20261013_083624\before\`

The same backup directory also has `after\` containing exact staged GitHub files and `manifest.txt`.

| Source | DEV SHA-256 before | SHA-256 after (verified on DEV) |
|---|---|---|
| `gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/MoneySystem.kt` | `A8CBC455C146788BCC1B21D4BF1A497A3D19D26FA21E51259F71A9276D9DEB69` | `F15FAE5A082EB6852D78D5422F9B926360CD8AFA0EBD1E85AFF63EF433280D02` |
| `app/src/main/kotlin/tw/tsm/app/handlers/InnHandler.kt` | `C85A26E4A3D48E5341C2C435263C8BC604918A4B1351C762333F6B403B6669A9` | `2322AA902C89C8C7F6D7DB4567E80A60A2FCC131F6A3C508DE2F7A3723BE91AF` |

Temporary SCP copies placed into the Administrator profile were removed; local verified staging copies were left at `D:\Games\Ts-Offline\_dvt_gold_20261013_stage`. The server's git worktree remains dirty by design to preserve other hotfixes.

## Build and restart
- Ran `.\gradlew.bat :app:classes --no-daemon --console=plain` **on DEV**. `GRADLE_EXIT=0`, **BUILD SUCCESSFUL in 46s**, 3 tasks executed / 11 up-to-date; `MoneySystem.class` and `InnHandler.class` timestamps advanced. Log: `logs\dvt_gold_fence_955333c_gradle_20261013.log`. Two unrelated existing Kotlin warnings from `GmCommandProcessor.kt` and `WelfareHandler.kt`.
- Before restart: **0 established game connections**, PID `5564` listening on TCP `6613`.
- Stopped **only** PID `5564`, then invoked the pre-existing DEV launcher `_start_dvt_trade_dupguard_baf3c59.cmd`. A subsequent independent TCP inspection confirmed **new PID `12184` listening on port `6613`**; process start was observed on 2026-10-13 around 08:38 local time.
- **Limit:** Complete application readiness (MySQL initialization, loaded missions/MarkSteps, and successful client login) was **not** proven after this restart. An additional remote diagnostic call was blocked by tooling; do not claim full game-ready or player E2E. Revisit logs and smoke tests when command access permits.

## Correctness and remaining gates
- Newly deployed: `MoneySystem.addGold/subtractGold` synchronous writer fence, cancellation-safe `withTradeGoldMutationFence` **as a dormant API**, cache helpers for a staged coordinator, and Inn C031 atomic debit via `subtractGold` guarded with `CharacterPersistenceGuard`; client response now uses `sendSubtractMoney` rather than invalid `addGold(-cost)`.
- **NOT ENABLED:** `TradeCacheSettlementCoordinator` and SQL two-character transaction are **not wired into TradeHandler/TradeSystem C025**. Previous duplicate-slot anti-dupe guard remains the live C025 protection. This deployment does **not** prove complete player-trade atomicity, network/logout race safety, or C025 success-after-commit semantics.
- No new DB migrations, quest data, DVT client edits, or unrelated hotfixes were applied.
- User requested a single consolidated direct-gameplay test later; do not claim it occurred.
- Next: (1) read post-restart runtime log for MySQL initialization and application-ready signal, verify listener PID stability; (2) continue static/concurrent testing for C025 final acknowledgment and balance writer interleavings; (3) only then consider staging an isolated opt-in SQL trade integration; (4) preserve before/after backup and avoid resetting DEV or Windows dirty worktree.
