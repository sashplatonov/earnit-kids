-- Migration: move app tables to dedicated project schema
-- Created: 2026-03-11

CREATE SCHEMA IF NOT EXISTS __DB_SCHEMA__;

ALTER TABLE IF EXISTS public.families SET SCHEMA __DB_SCHEMA__;
ALTER TABLE IF EXISTS public.children SET SCHEMA __DB_SCHEMA__;
ALTER TABLE IF EXISTS public.tasks SET SCHEMA __DB_SCHEMA__;
ALTER TABLE IF EXISTS public.shop_items SET SCHEMA __DB_SCHEMA__;
ALTER TABLE IF EXISTS public.history SET SCHEMA __DB_SCHEMA__;
ALTER TABLE IF EXISTS public.requests SET SCHEMA __DB_SCHEMA__;
ALTER TABLE IF EXISTS public.friends SET SCHEMA __DB_SCHEMA__;
ALTER TABLE IF EXISTS public.super_admin SET SCHEMA __DB_SCHEMA__;
ALTER TABLE IF EXISTS public.device_push_tokens SET SCHEMA __DB_SCHEMA__;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_proc p
        JOIN pg_namespace n ON n.oid = p.pronamespace
        WHERE p.proname = 'update_updated_at_column'
          AND n.nspname = 'public'
    ) THEN
        EXECUTE 'ALTER FUNCTION public.update_updated_at_column() SET SCHEMA __DB_SCHEMA__';
    END IF;
END $$;
