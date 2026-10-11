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
