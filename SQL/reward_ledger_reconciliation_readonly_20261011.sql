-- Read-only payout ledger inspection for MySQL. NO mutations.
-- This file does not authorize automatically clearing a pending claim.
-- Compare bags, currency, EXP, pet records and battle/dungeon logs first.

-- 1. Oldest unresolved payouts across all dungeon and battle types.
SELECT
    character_id, reward_kind, source_key, attempt_token,
    FROM_UNIXTIME(reserved_at_epoch_ms / 1000) AS reserved_at,
    TIMESTAMPDIFF(SECOND, FROM_UNIXTIME(reserved_at_epoch_ms / 1000), NOW()) AS pending_seconds
FROM character_reward_ledger
WHERE settled_at_epoch_ms IS NULL
ORDER BY reserved_at_epoch_ms ASC;

-- 2. Use the real character ID being investigated.
SET @audit_char_id = 0;
SELECT
    character_id, reward_kind, source_key, attempt_token,
    FROM_UNIXTIME(reserved_at_epoch_ms / 1000) AS reserved_at,
    FROM_UNIXTIME(settled_at_epoch_ms / 1000) AS settled_at
FROM character_reward_ledger
WHERE character_id = @audit_char_id
ORDER BY reserved_at_epoch_ms DESC;

-- 3. Scope guards: which pending run is blocking a new run of the same kind?
SELECT g.character_id, g.reward_kind, g.source_key, g.attempt_token,
       l.reserved_at_epoch_ms, l.settled_at_epoch_ms
FROM character_reward_pending_scopes AS g
LEFT JOIN character_reward_ledger AS l
    ON l.character_id = g.character_id
    AND l.reward_kind = g.reward_kind
    AND l.source_key = g.source_key
    AND l.attempt_token = g.attempt_token
ORDER BY g.character_id, g.reward_kind;

-- 4. A pending receipt lacking its corresponding guard suggests data
-- corruption or a partially applied manual maintenance action. READ ONLY.
SELECT l.character_id, l.reward_kind, l.source_key, l.attempt_token,
       l.reserved_at_epoch_ms
FROM character_reward_ledger AS l
LEFT JOIN character_reward_pending_scopes AS g
    ON g.character_id = l.character_id
    AND g.reward_kind = l.reward_kind
    AND g.source_key = l.source_key
    AND g.attempt_token = l.attempt_token
WHERE l.settled_at_epoch_ms IS NULL
  AND g.character_id IS NULL;
