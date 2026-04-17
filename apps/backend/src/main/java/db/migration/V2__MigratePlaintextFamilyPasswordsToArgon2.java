package db.migration;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class V2__MigratePlaintextFamilyPasswordsToArgon2 extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        var argon2 = Argon2Factory.create();
        var rows = readRows(context);
        var migratedRows = collectRowsToMigrate(argon2, rows);
        updatePasswords(context, migratedRows);
    }

    List<FamilyPasswordMigrationRow> collectRowsToMigrate(
        Argon2 argon2,
        List<FamilyPasswordMigrationRow> rows
    ) {
        var migratedRows = new ArrayList<FamilyPasswordMigrationRow>();
        for (var row : rows) {
            var password = row.password();
            if (password == null || password.startsWith("$argon2") || isSha256Hex(password)) {
                continue;
            }
            migratedRows.add(new FamilyPasswordMigrationRow(
                row.familyId(),
                argon2.hash(3, 65536, 1, password.toCharArray())));
        }
        return migratedRows;
    }

    private List<FamilyPasswordMigrationRow> readRows(Context context) throws Exception {
        var rows = new ArrayList<FamilyPasswordMigrationRow>();
        try (PreparedStatement statement = context.getConnection().prepareStatement(
            "SELECT family_id, admin_password FROM families")) {
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new FamilyPasswordMigrationRow(
                        resultSet.getString("family_id"),
                        resultSet.getString("admin_password")));
                }
            }
        }
        return rows;
    }

    private void updatePasswords(Context context, List<FamilyPasswordMigrationRow> rows) throws Exception {
        if (rows.isEmpty()) {
            return;
        }
        try (PreparedStatement statement = context.getConnection().prepareStatement(
            "UPDATE families SET admin_password = ? WHERE family_id = ?")) {
            for (var row : rows) {
                statement.setString(1, row.password());
                statement.setString(2, row.familyId());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private boolean isSha256Hex(String value) {
        return value.length() == 64 && value.matches("[0-9a-fA-F]{64}");
    }
}
