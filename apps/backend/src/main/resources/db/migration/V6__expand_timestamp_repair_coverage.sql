-- V6: Extend timestamp repair coverage for rows whose client-generated
-- external_id encodes the original event time in either milliseconds or
-- seconds since the Unix epoch.

WITH repaired_history AS (
    SELECT id,
           CASE
               WHEN external_id BETWEEN 946684800000 AND 4102444800000 THEN to_timestamp(external_id / 1000.0)
               WHEN external_id BETWEEN 946684800 AND 4102444800 THEN to_timestamp(external_id)
               ELSE NULL
           END AS repaired_created_at
    FROM history
    WHERE external_id IS NOT NULL
)
UPDATE history h
SET created_at = repaired_history.repaired_created_at
FROM repaired_history
WHERE repaired_history.id = h.id
  AND repaired_history.repaired_created_at IS NOT NULL
  AND (
      h.created_at IS NULL
      OR h.created_at < repaired_history.repaired_created_at - INTERVAL '1 minute'
      OR h.created_at > repaired_history.repaired_created_at + INTERVAL '1 minute'
  );

WITH repaired_requests AS (
    SELECT id,
           CASE
               WHEN external_id BETWEEN 946684800000 AND 4102444800000 THEN to_timestamp(external_id / 1000.0)
               WHEN external_id BETWEEN 946684800 AND 4102444800 THEN to_timestamp(external_id)
               ELSE NULL
           END AS repaired_created_at
    FROM requests
    WHERE external_id IS NOT NULL
)
UPDATE requests r
SET created_at = repaired_requests.repaired_created_at
FROM repaired_requests
WHERE repaired_requests.id = r.id
  AND repaired_requests.repaired_created_at IS NOT NULL
  AND (
      r.created_at IS NULL
      OR r.created_at < repaired_requests.repaired_created_at - INTERVAL '1 minute'
      OR r.created_at > repaired_requests.repaired_created_at + INTERVAL '1 minute'
  );