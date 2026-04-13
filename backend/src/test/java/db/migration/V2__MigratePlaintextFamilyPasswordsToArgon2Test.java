package db.migration;

import de.mkammerer.argon2.Argon2Factory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class V2__MigratePlaintextFamilyPasswordsToArgon2Test {

    @Test
    void collectRowsToMigrate_hashesOnlyPlaintextPasswords() {
        var migration = new V2__MigratePlaintextFamilyPasswordsToArgon2();
        var argon2 = Argon2Factory.create();
        var argonPassword = argon2.hash(3, 65536, 1, "secret123".toCharArray());

        var rows = List.of(
            new FamilyPasswordMigrationRow("fam_plain", "plain123"),
            new FamilyPasswordMigrationRow(
                "fam_sha",
                "9b8769a4a742959a2d0298db454132dedb57a0a778655bc47c3fab172f7f8d63"),
            new FamilyPasswordMigrationRow("fam_argon", argonPassword));

        var migrated = migration.collectRowsToMigrate(argon2, rows);

        assertThat(migrated).hasSize(1);
        assertThat(migrated.getFirst().familyId()).isEqualTo("fam_plain");
        assertThat(migrated.getFirst().password()).startsWith("$argon2");
        assertThat(argon2.verify(migrated.getFirst().password(), "plain123".toCharArray())).isTrue();
    }
}
