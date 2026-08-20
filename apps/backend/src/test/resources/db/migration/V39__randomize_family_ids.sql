-- H2-compatible counterpart to the immutable PostgreSQL V39 migration.
UPDATE families
SET family_id = 'fam_' || RAWTOHEX(HASH('MD5', STRINGTOUTF8(family_id || 'random_salt_2026')))
WHERE family_id IS NOT NULL
  AND family_id NOT LIKE 'fam\_%' ESCAPE '\';
