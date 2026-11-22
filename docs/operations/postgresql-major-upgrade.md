# PostgreSQL major-version upgrade

The Compose files use `postgres:19-alpine` and store the cluster under
`/var/lib/postgresql/19/docker`. PostgreSQL 18 cluster files cannot be opened
by PostgreSQL 19. Do not point the PostgreSQL 19 service at an 18 data volume.

For an existing database, migrate with a logical dump/restore or a separately
planned and tested `pg_upgrade` procedure:

1. Schedule downtime and stop application writers.
2. Keep the PostgreSQL 18 volume intact. Create and verify a full backup before
   changing any volume or service configuration.
3. Start PostgreSQL 19 with a new, empty volume. Restore the backup into that
   cluster, then verify roles, extensions, row counts, application reads and
   writes, and Flyway state.
4. Switch the application to the verified PostgreSQL 19 cluster. Retain the
   PostgreSQL 18 volume and backup until the migration has been accepted.

For a logical migration, use `pg_dumpall` against the old server and restore
the resulting SQL with `psql` against the new server. Run both client tools at
version 19 or newer, use an administrative database role, and protect the dump
because it contains role and database definitions. Do not remove the old
volume as part of the migration until rollback is no longer needed.

The default Compose volume name is unchanged to avoid silently creating a
second database for fresh installs. That means an existing volume must be
migrated explicitly before starting the updated Compose stack.
