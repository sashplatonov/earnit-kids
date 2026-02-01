package com.coinsshop.service;

import com.coinsshop.model.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DatabaseService {

    private final String url;

    public DatabaseService(String dbPath) {
        this.url = "jdbc:sqlite:" + dbPath;
        initSchema();
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(url);
    }

    private void initSchema() {
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS families (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "parent_chat_id TEXT UNIQUE NOT NULL, " +
                    "pin TEXT" +
                    ");");

            stmt.execute("CREATE TABLE IF NOT EXISTS children (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "family_id INTEGER REFERENCES families(id), " +
                    "chat_id TEXT UNIQUE, " +
                    "name TEXT NOT NULL, " +
                    "balance INTEGER DEFAULT 0" +
                    ");");

            stmt.execute("CREATE TABLE IF NOT EXISTS tasks (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "family_id INTEGER REFERENCES families(id), " +
                    "title TEXT NOT NULL, " +
                    "reward INTEGER NOT NULL, " +
                    "icon TEXT" +
                    ");");

            stmt.execute("CREATE TABLE IF NOT EXISTS shop_items (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "family_id INTEGER REFERENCES families(id), " +
                    "title TEXT NOT NULL, " +
                    "price INTEGER NOT NULL, " +
                    "icon TEXT" +
                    ");");

            stmt.execute("CREATE TABLE IF NOT EXISTS invite_codes (" +
                    "code TEXT PRIMARY KEY, " +
                    "family_id INTEGER REFERENCES families(id), " +
                    "created_at DATETIME DEFAULT CURRENT_TIMESTAMP" +
                    ");");

            // System.out.println("Database initialized.");

        } catch (SQLException e) {
            System.err.println("Error initializing database: " + e.getMessage());
        }
    }

    // --- FAMILY ---

    public int getOrCreateFamily(String parentChatId, String pin) {
        try (Connection conn = connect()) {
            // Check exist
            PreparedStatement check = conn.prepareStatement("SELECT id FROM families WHERE parent_chat_id = ?");
            check.setString(1, parentChatId);
            ResultSet rs = check.executeQuery();
            if (rs.next())
                return rs.getInt("id");

            // Create
            PreparedStatement insert = conn.prepareStatement("INSERT INTO families (parent_chat_id, pin) VALUES (?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            insert.setString(1, parentChatId);
            insert.setString(2, pin);
            insert.executeUpdate();
            ResultSet keys = insert.getGeneratedKeys();
            if (keys.next())
                return keys.getInt(1);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public Integer getFamilyIdByParent(String parentChatId) {
        try (Connection conn = connect();
                PreparedStatement stmt = conn.prepareStatement("SELECT id FROM families WHERE parent_chat_id = ?")) {
            stmt.setString(1, parentChatId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next())
                return rs.getInt("id");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean validatePin(int familyId, String pin) {
        try (Connection conn = connect();
                PreparedStatement stmt = conn.prepareStatement("SELECT id FROM families WHERE id = ? AND pin = ?")) {
            stmt.setInt(1, familyId);
            stmt.setString(2, pin);
            return stmt.executeQuery().next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // --- CHILDREN ---

    public ChildData getChildByChatId(String chatId) {
        try (Connection conn = connect();
                PreparedStatement stmt = conn.prepareStatement("SELECT * FROM children WHERE chat_id = ?")) {
            stmt.setString(1, chatId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new ChildData(rs.getInt("id"), rs.getInt("family_id"), rs.getString("name"),
                        rs.getInt("balance"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void createChild(int familyId, String chatId, String name) {
        try (Connection conn = connect();
                PreparedStatement stmt = conn
                        .prepareStatement("INSERT INTO children (family_id, chat_id, name) VALUES (?, ?, ?)")) {
            stmt.setInt(1, familyId);
            stmt.setString(2, chatId);
            stmt.setString(3, name);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<ChildData> getChildren(int familyId) {
        List<ChildData> list = new ArrayList<>();
        try (Connection conn = connect();
                PreparedStatement stmt = conn.prepareStatement("SELECT * FROM children WHERE family_id = ?")) {
            stmt.setInt(1, familyId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                list.add(new ChildData(rs.getInt("id"), rs.getInt("family_id"), rs.getString("name"),
                        rs.getInt("balance")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    // --- TASKS & SHOP ---

    public List<Task> getTasks(int familyId) {
        List<Task> list = new ArrayList<>();
        try (Connection conn = connect();
                PreparedStatement stmt = conn.prepareStatement("SELECT * FROM tasks WHERE family_id = ?")) {
            stmt.setInt(1, familyId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                list.add(new Task(String.valueOf(rs.getInt("id")), rs.getString("title"), rs.getInt("reward"),
                        rs.getString("icon")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public void addTask(int familyId, String title, int reward, String icon) {
        try (Connection conn = connect();
                PreparedStatement stmt = conn
                        .prepareStatement("INSERT INTO tasks (family_id, title, reward, icon) VALUES (?, ?, ?, ?)")) {
            stmt.setInt(1, familyId);
            stmt.setString(2, title);
            stmt.setInt(3, reward);
            stmt.setString(4, icon);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<ShopItem> getShopItems(int familyId) {
        List<ShopItem> list = new ArrayList<>();
        try (Connection conn = connect();
                PreparedStatement stmt = conn.prepareStatement("SELECT * FROM shop_items WHERE family_id = ?")) {
            stmt.setInt(1, familyId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                list.add(new ShopItem(String.valueOf(rs.getInt("id")), rs.getString("title"), rs.getInt("price"),
                        rs.getString("icon")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    // --- INVITES ---

    public String createInviteCode(int familyId) {
        String code = String.valueOf(100000 + (int) (Math.random() * 900000));
        try (Connection conn = connect();
                PreparedStatement stmt = conn
                        .prepareStatement("INSERT INTO invite_codes (code, family_id) VALUES (?, ?)")) {
            stmt.setString(1, code);
            stmt.setInt(2, familyId);
            stmt.executeUpdate();
            return code;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Integer getFamilyIdByInviteCode(String code) {
        try (Connection conn = connect();
                PreparedStatement stmt = conn.prepareStatement("SELECT family_id FROM invite_codes WHERE code = ?")) {
            stmt.setString(1, code);
            ResultSet rs = stmt.executeQuery();
            if (rs.next())
                return rs.getInt("family_id");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void consumeInviteCode(String code) {
        try (Connection conn = connect();
                PreparedStatement stmt = conn.prepareStatement("DELETE FROM invite_codes WHERE code = ?")) {
            stmt.setString(1, code);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
