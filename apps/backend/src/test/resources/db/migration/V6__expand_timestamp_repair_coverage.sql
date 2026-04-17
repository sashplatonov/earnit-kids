UPDATE history
SET created_at = CASE
    WHEN external_id BETWEEN 946684800000 AND 4102444800000
        THEN DATEADD('MILLISECOND', external_id, TIMESTAMP WITH TIME ZONE '1970-01-01 00:00:00+00')
    WHEN external_id BETWEEN 946684800 AND 4102444800
        THEN DATEADD('SECOND', external_id, TIMESTAMP WITH TIME ZONE '1970-01-01 00:00:00+00')
    ELSE created_at
END
WHERE external_id IS NOT NULL
  AND (
      created_at IS NULL
      OR created_at < DATEADD('MINUTE', -1,
          CASE
              WHEN external_id BETWEEN 946684800000 AND 4102444800000
                  THEN DATEADD('MILLISECOND', external_id, TIMESTAMP WITH TIME ZONE '1970-01-01 00:00:00+00')
              WHEN external_id BETWEEN 946684800 AND 4102444800
                  THEN DATEADD('SECOND', external_id, TIMESTAMP WITH TIME ZONE '1970-01-01 00:00:00+00')
              ELSE created_at
          END)
      OR created_at > DATEADD('MINUTE', 1,
          CASE
              WHEN external_id BETWEEN 946684800000 AND 4102444800000
                  THEN DATEADD('MILLISECOND', external_id, TIMESTAMP WITH TIME ZONE '1970-01-01 00:00:00+00')
              WHEN external_id BETWEEN 946684800 AND 4102444800
                  THEN DATEADD('SECOND', external_id, TIMESTAMP WITH TIME ZONE '1970-01-01 00:00:00+00')
              ELSE created_at
          END)
  );

UPDATE requests
SET created_at = CASE
    WHEN external_id BETWEEN 946684800000 AND 4102444800000
        THEN DATEADD('MILLISECOND', external_id, TIMESTAMP WITH TIME ZONE '1970-01-01 00:00:00+00')
    WHEN external_id BETWEEN 946684800 AND 4102444800
        THEN DATEADD('SECOND', external_id, TIMESTAMP WITH TIME ZONE '1970-01-01 00:00:00+00')
    ELSE created_at
END
WHERE external_id IS NOT NULL
  AND (
      created_at IS NULL
      OR created_at < DATEADD('MINUTE', -1,
          CASE
              WHEN external_id BETWEEN 946684800000 AND 4102444800000
                  THEN DATEADD('MILLISECOND', external_id, TIMESTAMP WITH TIME ZONE '1970-01-01 00:00:00+00')
              WHEN external_id BETWEEN 946684800 AND 4102444800
                  THEN DATEADD('SECOND', external_id, TIMESTAMP WITH TIME ZONE '1970-01-01 00:00:00+00')
              ELSE created_at
          END)
      OR created_at > DATEADD('MINUTE', 1,
          CASE
              WHEN external_id BETWEEN 946684800000 AND 4102444800000
                  THEN DATEADD('MILLISECOND', external_id, TIMESTAMP WITH TIME ZONE '1970-01-01 00:00:00+00')
              WHEN external_id BETWEEN 946684800 AND 4102444800
                  THEN DATEADD('SECOND', external_id, TIMESTAMP WITH TIME ZONE '1970-01-01 00:00:00+00')
              ELSE created_at
          END)
  );