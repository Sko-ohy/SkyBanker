package me.skoohy.skybanker;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SkyBankerDatabase {

    private static final Object LOCK = new Object();
    private static Connection connection;
    private static JavaPlugin plugin;

    public static void init(JavaPlugin pluginInstance) {
        plugin = pluginInstance;
        connect();
    }

    private static void connect() {
        synchronized (LOCK) {
            try {
                File dataFolder = plugin.getDataFolder();
                if (!dataFolder.exists()) {
                    dataFolder.mkdirs();
                }

                File dbFile = new File(dataFolder, "sky_banker.db");
                String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
                connection = DriverManager.getConnection(url);

                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("PRAGMA journal_mode = WAL");
                    stmt.execute("PRAGMA synchronous = NORMAL");
                    stmt.execute("PRAGMA cache_size = -64000");
                    stmt.execute("PRAGMA temp_store = MEMORY");
                    stmt.execute("PRAGMA foreign_keys = ON");
                    stmt.execute("PRAGMA busy_timeout = 5000");
                }

                createTables();
                Bukkit.getLogger().info("[Sky Banker] Database connected successfully!");
            } catch (SQLException e) {
                Bukkit.getLogger().severe("[Sky Banker] Failed to connect to database!");
                e.printStackTrace();
            }
        }
    }

    private static void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS bank_accounts (" +
                    "uuid TEXT PRIMARY KEY," +
                    "player_name TEXT NOT NULL," +
                    "balance REAL NOT NULL DEFAULT 0," +
                    "level INTEGER NOT NULL DEFAULT 1," +
                    "last_interest_time LONG NOT NULL DEFAULT 0," +
                    "created_at LONG NOT NULL," +
                    "updated_at LONG NOT NULL" +
                    ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS transactions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "uuid TEXT NOT NULL," +
                    "amount REAL NOT NULL," +
                    "transaction_type TEXT NOT NULL," +
                    "timestamp LONG NOT NULL," +
                    "FOREIGN KEY (uuid) REFERENCES bank_accounts(uuid) ON DELETE CASCADE" +
                    ")");

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_uuid ON transactions(uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_timestamp ON transactions(timestamp)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_uuid_timestamp ON transactions(uuid, timestamp)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_balance ON bank_accounts(balance)");
        }
    }

    public static double getBalance(Player player) {
        return getBalance(player.getUniqueId());
    }

    public static double getBalance(UUID uuid) {
        synchronized (LOCK) {
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "SELECT balance FROM bank_accounts WHERE uuid = ?")) {
                pstmt.setString(1, uuid.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) return rs.getDouble("balance");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return 0;
        }
    }

    public static void setBalance(Player player, double amount) {
        setBalance(player.getUniqueId(), player.getName(), amount);
    }

    public static void setBalance(UUID uuid, String playerName, double amount) {
        createAccount(uuid, playerName);
        synchronized (LOCK) {
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "UPDATE bank_accounts SET balance = ?, player_name = ?, updated_at = ? WHERE uuid = ?")) {
                pstmt.setDouble(1, amount);
                pstmt.setString(2, playerName);
                pstmt.setLong(3, System.currentTimeMillis());
                pstmt.setString(4, uuid.toString());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public static int getLevel(Player player) {
        return getLevel(player.getUniqueId());
    }

    public static int getLevel(UUID uuid) {
        synchronized (LOCK) {
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "SELECT level FROM bank_accounts WHERE uuid = ?")) {
                pstmt.setString(1, uuid.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) return rs.getInt("level");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return 1;
        }
    }

    public static void setLevel(Player player, int level) {
        setLevel(player.getUniqueId(), player.getName(), level);
    }

    public static void setLevel(UUID uuid, String playerName, int level) {
        createAccount(uuid, playerName);
        synchronized (LOCK) {
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "UPDATE bank_accounts SET level = ?, player_name = ?, updated_at = ? WHERE uuid = ?")) {
                pstmt.setInt(1, level);
                pstmt.setString(2, playerName);
                pstmt.setLong(3, System.currentTimeMillis());
                pstmt.setString(4, uuid.toString());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public static void addTransaction(Player player, double amount, String type) {
        addTransaction(player.getUniqueId(), amount, type);
    }

    public static void addTransaction(UUID uuid, double amount, String type) {
        synchronized (LOCK) {
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "INSERT INTO transactions (uuid, amount, transaction_type, timestamp) VALUES (?, ?, ?, ?)")) {
                pstmt.setString(1, uuid.toString());
                pstmt.setDouble(2, amount);
                pstmt.setString(3, type);
                pstmt.setLong(4, System.currentTimeMillis());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }

            int maxStored = plugin != null ? plugin.getConfig().getInt("settings.max-stored-transactions", 20) : 20;
            String pruneQuery = "DELETE FROM transactions WHERE uuid = ? AND id NOT IN " +
                    "(SELECT id FROM transactions WHERE uuid = ? ORDER BY id DESC LIMIT ?)";
            try (PreparedStatement pruneStmt = connection.prepareStatement(pruneQuery)) {
                pruneStmt.setString(1, uuid.toString());
                pruneStmt.setString(2, uuid.toString());
                pruneStmt.setInt(3, maxStored);
                pruneStmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public static List<Map<String, Object>> getTransactions(Player player) {
        synchronized (LOCK) {
            List<Map<String, Object>> transactionList = new ArrayList<>();
            int maxStored = plugin != null ? plugin.getConfig().getInt("settings.max-stored-transactions", 20) : 20;
            String query = "SELECT amount, transaction_type, timestamp FROM transactions WHERE uuid = ? ORDER BY id DESC LIMIT ?";

            try (PreparedStatement pstmt = connection.prepareStatement(query)) {
                pstmt.setString(1, player.getUniqueId().toString());
                pstmt.setInt(2, maxStored);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> tx = new HashMap<>();
                        double amount = rs.getDouble("amount");
                        String type = rs.getString("transaction_type");
                        long timestamp = rs.getLong("timestamp");

                        String actor = type.equalsIgnoreCase("INTEREST") ? "Bank Interest" : player.getName();

                        tx.put("amount", amount);
                        tx.put("type", type);
                        tx.put("timestamp", timestamp);
                        tx.put("actor", actor);

                        transactionList.add(tx);
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return transactionList;
        }
    }

    public static long getLastInterestTime(Player player) {
        return getLastInterestTime(player.getUniqueId());
    }

    public static long getLastInterestTime(UUID uuid) {
        synchronized (LOCK) {
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "SELECT last_interest_time FROM bank_accounts WHERE uuid = ?")) {
                pstmt.setString(1, uuid.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) return rs.getLong("last_interest_time");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return 0;
        }
    }

    public static void setLastInterestTime(Player player, long time) {
        setLastInterestTime(player.getUniqueId(), time);
    }

    public static void setLastInterestTime(UUID uuid, long time) {
        synchronized (LOCK) {
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "UPDATE bank_accounts SET last_interest_time = ?, updated_at = ? WHERE uuid = ?")) {
                pstmt.setLong(1, time);
                pstmt.setLong(2, System.currentTimeMillis());
                pstmt.setString(3, uuid.toString());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * The coin amount of the player's most recent INTEREST-type
     * transaction, or 0 if they've never received interest yet.
     */
    public static double getLastInterestAmount(Player player) {
        synchronized (LOCK) {
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "SELECT amount FROM transactions WHERE uuid = ? AND transaction_type = 'INTEREST' " +
                            "ORDER BY timestamp DESC LIMIT 1")) {
                pstmt.setString(1, player.getUniqueId().toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) return rs.getDouble("amount");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return 0;
        }
    }

    /**
     * Account info used by the Settings menu: "created_at" (Long, millis).
     * Returns null if the player has no account yet.
     */
    public static Map<String, Object> getAccountStats(UUID uuid) {
        synchronized (LOCK) {
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "SELECT created_at FROM bank_accounts WHERE uuid = ?")) {
                pstmt.setString(1, uuid.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        Map<String, Object> stats = new HashMap<>();
                        stats.put("created_at", rs.getLong("created_at"));
                        return stats;
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    public static boolean hasAccount(Player player) {
        return hasAccount(player.getUniqueId());
    }

    public static boolean hasAccount(UUID uuid) {
        synchronized (LOCK) {
            return hasAccountNoLock(uuid);
        }
    }

    public static void createAccount(Player player) {
        createAccount(player.getUniqueId(), player.getName());
    }

    public static void createAccount(UUID uuid, String playerName) {
        synchronized (LOCK) {
            if (hasAccountNoLock(uuid)) return;
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "INSERT INTO bank_accounts (uuid, player_name, balance, level, last_interest_time, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                pstmt.setString(1, uuid.toString());
                pstmt.setString(2, playerName);
                pstmt.setDouble(3, 0);
                pstmt.setInt(4, 1);
                pstmt.setLong(5, System.currentTimeMillis());
                pstmt.setLong(6, System.currentTimeMillis());
                pstmt.setLong(7, System.currentTimeMillis());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private static boolean hasAccountNoLock(UUID uuid) {
        try (PreparedStatement pstmt = connection.prepareStatement(
                "SELECT 1 FROM bank_accounts WHERE uuid = ? LIMIT 1")) {
            pstmt.setString(1, uuid.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static List<Map<String, Object>> getTopBalances(int limit) {
        synchronized (LOCK) {
            List<Map<String, Object>> list = new ArrayList<>();
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "SELECT player_name, balance FROM bank_accounts ORDER BY balance DESC LIMIT ?")) {
                pstmt.setInt(1, limit);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("player_name", rs.getString("player_name"));
                        row.put("balance", rs.getDouble("balance"));
                        list.add(row);
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return list;
        }
    }

    public static Map<String, Object> getRankByName(String playerName) {
        synchronized (LOCK) {
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "SELECT balance, " +
                            "(SELECT COUNT(*) + 1 FROM bank_accounts b2 WHERE b2.balance > b1.balance) AS rank " +
                            "FROM bank_accounts b1 WHERE LOWER(player_name) = LOWER(?)")) {
                pstmt.setString(1, playerName);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        Map<String, Object> result = new LinkedHashMap<>();
                        result.put("balance", rs.getDouble("balance"));
                        result.put("rank", rs.getInt("rank"));
                        return result;
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    public static int getTotalAccountCount() {
        synchronized (LOCK) {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS total FROM bank_accounts")) {
                if (rs.next()) return rs.getInt("total");
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return 0;
        }
    }

    public static void closeConnection() {
        synchronized (LOCK) {
            try {
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                    Bukkit.getLogger().info("[Sky Banker] Database disconnected!");
                }
            } catch (SQLException e) {
                Bukkit.getLogger().severe("[Sky Banker] Error disconnecting database!");
                e.printStackTrace();
            }
        }
    }

    public static boolean isConnected() {
        synchronized (LOCK) {
            try {
                return connection != null && !connection.isClosed();
            } catch (SQLException e) {
                return false;
            }
        }
    }
}