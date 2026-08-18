-- Update existing family_id to a random hash to prevent email leakage.
-- We use md5 of the existing family_id + a random salt to ensure consistency if needed, 
-- but for a clean break, a completely random value is better.
-- Since we are in PostgreSQL, we can use gen_random_uuid() or md5(random()::text).

UPDATE families 
SET family_id = 'fam_' || encode(digest(family_id || 'salt_2026', 'sha256'), 'hex')
WHERE family_id IS NOT NULL;

-- Note: The above uses pgcrypto's digest. If pgcrypto is not installed, we use md5.
-- To be safe and avoid dependencies, let's use a simple md5 with a salt.

UPDATE families 
SET family_id = 'fam_' || md5(family_id || 'random_salt_2026')
WHERE family_id IS NOT NULL;
