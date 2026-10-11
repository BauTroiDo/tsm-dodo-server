-- Read-only queries for resolving potentially paid but uncommitted Eve rewards.
-- MySQL 8.0+. NO UPDATE, DELETE, INSERT, DDL or automatic expiration.
-- Run against the intended database with permission to SELECT.
--
-- A pending claim is NOT proof that an item/gold/mission reward was delivered.
-- Compare inventory, money, mission flags, event counts and server logs
-- before deciding whether any action can safely be retried.

-- 1. All unresolved claims, oldest first, with current EveCount.
SELECT
    p.char_id,
    p.map_id,
    p.eve_no,
    p.run_token,
    FROM_UNIXTIME(p.reserved_at_epoch_ms / 1000) AS reserved_at,
    TIMESTAMPDIFF(SECOND, FROM_UNIXTIME(p.reserved_at_epoch_ms / 1000), NOW()) AS pending_seconds,
    COALESCE(c.completion_count, 0) AS current_eve_count
FROM character_eve_effect_claims AS p
LEFT JOIN character_completed_events AS c
    ON c.char_id = p.char_id
    AND c.map_id = p.map_id
    AND c.eve_no = p.eve_no
ORDER BY p.reserved_at_epoch_ms ASC;

-- 2. Replace 0 below with the actual character ID under investigation.
SET @audit_char_id = 0;

SELECT p.char_id, p.map_id, p.eve_no, p.run_token,
       FROM_UNIXTIME(p.reserved_at_epoch_ms / 1000) AS reserved_at
FROM character_eve_effect_claims AS p
WHERE p.char_id = @audit_char_id
ORDER BY p.reserved_at_epoch_ms;

SELECT s.char_id, s.map_id, s.eve_no, s.run_token,
       s.completion_count,
       FROM_UNIXTIME(s.settled_at_epoch_ms / 1000) AS settled_at
FROM character_eve_effect_settlements AS s
WHERE s.char_id = @audit_char_id
ORDER BY s.settled_at_epoch_ms DESC
LIMIT 100;

SELECT m.char_id, m.mission_id, m.step
FROM character_missions AS m
WHERE m.char_id = @audit_char_id
ORDER BY m.mission_id;

SELECT f.char_id, f.bit_index, f.bit_value
FROM character_mission_flags AS f
WHERE f.char_id = @audit_char_id
ORDER BY f.bit_index;

SELECT c.char_id, c.map_id, c.eve_no, c.completion_count
FROM character_completed_events AS c
WHERE c.char_id = @audit_char_id
ORDER BY c.map_id, c.eve_no;

-- Inventory/money/pet tables should be checked against their current schema,
-- and game logs correlated by runToken/mapId/eveNo before any manual release.
