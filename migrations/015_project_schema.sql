-- Migration: move app tables to dedicated project schema
-- Created: 2026-03-11

CREATE SCHEMA IF NOT EXISTS __DB_SCHEMA__;

DO $$
DECLARE
    app_table_name TEXT;
    tables_to_move TEXT[] := ARRAY[
        'families',
        'children',
        'tasks',
        'shop_items',
        'history',
        'requests',
        'friends',
        'super_admin',
        'device_push_tokens'
    ];
BEGIN
    FOREACH app_table_name IN ARRAY tables_to_move
    LOOP
        IF EXISTS (
            SELECT 1
            FROM information_schema.tables
            WHERE table_schema = 'public'
              AND information_schema.tables.table_name = app_table_name
        ) THEN
            IF EXISTS (
                SELECT 1
                FROM information_schema.tables
                WHERE table_schema = '__DB_SCHEMA__'
                  AND information_schema.tables.table_name = app_table_name
            ) THEN
                EXECUTE format('DROP TABLE __DB_SCHEMA__.%I CASCADE', app_table_name);
            END IF;

            EXECUTE format('ALTER TABLE public.%I SET SCHEMA __DB_SCHEMA__', app_table_name);
        END IF;
    END LOOP;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_proc p
        JOIN pg_namespace n ON n.oid = p.pronamespace
        WHERE p.proname = 'update_updated_at_column'
          AND n.nspname = '__DB_SCHEMA__'
    ) THEN
        EXECUTE 'DROP FUNCTION __DB_SCHEMA__.update_updated_at_column() CASCADE';
    END IF;

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
