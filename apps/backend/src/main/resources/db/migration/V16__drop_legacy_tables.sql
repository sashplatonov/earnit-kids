-- Drop legacy tables that are no longer needed.
--
-- migrations: legacy table from the old Node.js migration system.
--             Flyway manages migrations via its own flyway_schema_history table.
-- super_admin: legacy table that stored super admin credentials in the database.
--              Super admin email is now configured via the SUPER_ADMIN_EMAIL env var
--              and validated at runtime without database storage.

DROP TABLE IF EXISTS migrations;
DROP TABLE IF EXISTS super_admin;
