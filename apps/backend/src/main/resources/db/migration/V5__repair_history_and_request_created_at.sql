-- V5: Repair history and request timestamps that were flattened to the last
-- save time when generic sync payloads dropped their original createdAt field.
--
-- Safe repair strategy:
-- 1. Only touch rows whose external_id looks like a client-generated Date.now()
--    timestamp in milliseconds.
-- 2. Use that external_id as the original event/request creation time.
-- 3. Leave legacy rows with non timestamp-like external ids unchanged.

UPDATE history
SET created_at = to_timestamp(external_id / 1000.0)
WHERE external_id BETWEEN 946684800000 AND 4102444800000
  AND (
      created_at < to_timestamp(external_id / 1000.0) - INTERVAL '1 minute'
      OR created_at > to_timestamp(external_id / 1000.0) + INTERVAL '1 minute'
  );

UPDATE requests
SET created_at = to_timestamp(external_id / 1000.0)
WHERE external_id BETWEEN 946684800000 AND 4102444800000
  AND (
      created_at < to_timestamp(external_id / 1000.0) - INTERVAL '1 minute'
      OR created_at > to_timestamp(external_id / 1000.0) + INTERVAL '1 minute'
  );