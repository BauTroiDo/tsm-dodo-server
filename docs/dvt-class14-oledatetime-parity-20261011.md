# DVT class14 time condition — forensic source parity (2026-10-11)

## Source and verified shape
Authoritative Eve binary: currently active staging client and DEV `gamedata/Eve.emg`,
SHA256 `03c84cb565d4bdd10b7506a4d04a918ae590737f80a3e7e4fd37782b9c40b910`.
Client DVT source `Data/Eve/Eve_NpcEventData.lua` reads `CTimeValue` via
`reader:ReadDouble()`. Unlike the original client, `EveDataLoader.kt`
previously **discarded** this 8-byte Double. `EveConditionEvaluator.kt` then
misused a hardcoded `activityOpen=true` to evaluate class14, blocking real date
ranges and opening other branches irrespective of their expired period.

Independent binary scan of all 3,823 scenes:
- 279 class14 conditions, **all** with a finite nonzero `CTimeValue` and `subItem=0`.
- 274 records `param=0, pStyle=0, value=0`:
  - `ops=1` (now < time) 72 records;
  - `ops=2` (now > time) 178 records;
  - `ops=3` (now <= time) 13 records;
  - `ops=4` (now >= time) 11 records.
- 5 anomalous class14 `param=2, pStyle=1..4, ops=0, value=0`,
  all `CTimeValue=37931.6033664236`, only scene12001/eve64.
  Semantics **unverified**; they remain rejected, not forced true.

## Evidence for time comparisons
DVT stores OLE Automation local dates as 8-byte Double with 1899-12-30 epoch;
the integer portion is days, fractional portion is time-of-day. Actual
10852/eve8 groups `ops=2,time=43320.00000840278` (start)
AND `ops=1,time=43324.9999968287` (end). 11411/eve4
uses `ops=4,time=43376.000005671296` (inclusive start)
AND `ops=3,time=43382.99999409722` (inclusive end).
These shape pairs explain all four operators without assuming that they are
boolean "activity open" flags. The actual server clock is read as
`LocalDateTime.now()`, converted to a day-and-fraction OLE date;
this depends on the host's configured local timezone.

## Fix
- Preserve `conditionTimeValue` in `EveCondition` with default 0.0 for backward
  source/binary fixture compatibility and wire through `EveDataLoader`.
- Build `PlayerEventState.eveNowOaDate` from the server-local clock.
- Implement class14 `param0/pStyle0/ops1-4` comparisons with exact boundaries.
- Unknown time, unsupported shape, NaN and absent clock **fail closed**.
- Remove the hardcoded always-open interpretation; do not alter Event archives or
  the original game client.
- Regression tests use both real Eve archive records and fixed simulated OLE dates.

## Operational limit
Historical events from 2018-2026 will correctly be recognized as expired
according to the client-authored timestamps. This patch does **not**
reopen ended activities or invent a live event scheduler. A separate verified
event calendar / administrative policy is required to run these events again.
Do not treat a green unit test as live in-game E2E navigation or reward redemption.
