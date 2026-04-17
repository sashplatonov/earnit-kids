-- Ensure the script runs against the target schema
SET search_path TO earnit_kids, public;

SELECT current_database() AS database_name,
       current_schema() AS schema_name,
       now() AS checked_at;

SELECT installed_rank,
       version,
       description,
       success,
       installed_on
FROM flyway_schema_history
WHERE version IN ('5', '6')
ORDER BY installed_rank;

WITH candidates AS (
    SELECT 'history' AS table_name,
           id,
           external_id,
           created_at,
           type,
           child_id,
           related_id,
           CASE
               WHEN external_id BETWEEN 946684800000 AND 4102444800000 THEN to_timestamp(external_id / 1000.0)
               WHEN external_id BETWEEN 946684800 AND 4102444800 THEN to_timestamp(external_id)
               ELSE NULL
           END AS repaired_created_at
    FROM history
    WHERE external_id IS NOT NULL

    UNION ALL

    SELECT 'requests' AS table_name,
           id,
           external_id,
           created_at,
           request_type AS type,
           child_id,
           COALESCE(task_id, item_id) AS related_id,
           CASE
               WHEN external_id BETWEEN 946684800000 AND 4102444800000 THEN to_timestamp(external_id / 1000.0)
               WHEN external_id BETWEEN 946684800 AND 4102444800 THEN to_timestamp(external_id)
               ELSE NULL
           END AS repaired_created_at
    FROM requests
    WHERE external_id IS NOT NULL
)
SELECT table_name,
       count(*) FILTER (WHERE repaired_created_at IS NOT NULL) AS timestamp_like_rows,
       count(*) FILTER (WHERE repaired_created_at IS NOT NULL AND created_at IS NULL) AS null_created_at_rows,
       count(*) FILTER (
           WHERE repaired_created_at IS NOT NULL
             AND created_at IS NOT NULL
             AND ABS(EXTRACT(EPOCH FROM (created_at - repaired_created_at))) > 60
       ) AS mismatched_rows,
       count(*) FILTER (WHERE repaired_created_at IS NULL) AS non_timestamp_external_id_rows
FROM candidates
GROUP BY table_name
ORDER BY table_name;

WITH candidates AS (
    SELECT 'history' AS table_name,
           id,
           external_id,
           created_at,
           type,
           child_id,
           related_id,
           CASE
               WHEN external_id BETWEEN 946684800000 AND 4102444800000 THEN to_timestamp(external_id / 1000.0)
               WHEN external_id BETWEEN 946684800 AND 4102444800 THEN to_timestamp(external_id)
               ELSE NULL
           END AS repaired_created_at
    FROM history
    WHERE external_id IS NOT NULL

    UNION ALL

    SELECT 'requests' AS table_name,
           id,
           external_id,
           created_at,
           request_type AS type,
           child_id,
           COALESCE(task_id, item_id) AS related_id,
           CASE
               WHEN external_id BETWEEN 946684800000 AND 4102444800000 THEN to_timestamp(external_id / 1000.0)
               WHEN external_id BETWEEN 946684800 AND 4102444800 THEN to_timestamp(external_id)
               ELSE NULL
           END AS repaired_created_at
    FROM requests
    WHERE external_id IS NOT NULL
)
SELECT table_name,
       id,
       external_id,
       type,
       child_id,
       related_id,
       created_at,
       repaired_created_at,
       CASE
           WHEN created_at IS NULL OR repaired_created_at IS NULL THEN NULL
           ELSE ROUND(EXTRACT(EPOCH FROM (created_at - repaired_created_at)))
       END AS diff_seconds
FROM candidates
WHERE repaired_created_at IS NOT NULL
  AND (
      created_at IS NULL
      OR ABS(EXTRACT(EPOCH FROM (created_at - repaired_created_at))) > 60
  )
ORDER BY table_name, id
LIMIT 200;

SELECT 'history' AS table_name,
       created_at,
       count(*) AS row_count
FROM history
GROUP BY created_at
HAVING count(*) >= 10
ORDER BY row_count DESC, created_at DESC
LIMIT 20;

SELECT 'requests' AS table_name,
       created_at,
       count(*) AS row_count
FROM requests
GROUP BY created_at
HAVING count(*) >= 10
ORDER BY row_count DESC, created_at DESC
LIMIT 20;

SELECT 'history' AS table_name,
       id,
       external_id,
       type,
       child_id,
       related_id,
       created_at
FROM history
WHERE created_at IS NULL

UNION ALL

SELECT 'requests' AS table_name,
       id,
       external_id,
    request_type AS type,
       child_id,
    COALESCE(task_id, item_id) AS related_id,
       created_at
FROM requests
WHERE created_at IS NULL
ORDER BY table_name, id
LIMIT 200;