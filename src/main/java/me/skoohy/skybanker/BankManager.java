package me.skoohy.skybanker;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BankManager {

    private static JavaPlugin plugin;

    public static void init(JavaPlugin pluginInstance) {
        plugin = pluginInstance;
    }

    public static String getMessage(String key) {
        if (plugin != null) {
            String msg = plugin.getConfig().getString(key);
            if (msg == null) {
                java.io.File file = new java.io.File(plugin.getDataFolder(), "messages.yml");
                if (file.exists()) {
                    org.bukkit.configuration.file.FileConfiguration messagesConfig =
                            org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
                    msg = messagesConfig.getString(key);
                }
            }
            if (msg != null) {
                return colorize(msg);
            }
        }
        return "§cMissing message: " + key;
    }

    public static String getPrefix() {
        return getMessage("prefix");
    }

    public static String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public static List<String> colorizeList(List<String> lines) {
        List<String> out = new ArrayList<>();
        if (lines == null) return out;
        for (String line : lines) {
            out.add(colorize(line));
        }
        return out;
    }

    private static String applyPlaceholders(String line, Map<String, String> placeholders) {
        if (placeholders == null) return line;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            line = line.replace(entry.getKey(), entry.getValue());
        }
        return line;
    }

    public static List<String> getLore(String path, Map<String, String> placeholders) {
        List<String> raw = plugin.getConfig().getStringList(path);
        List<String> result = new ArrayList<>();
        for (String line : raw) {
            result.add(colorize(applyPlaceholders(line, placeholders)));
        }
        return result;
    }

    public static String getName(String path, String def) {
        String name = plugin.getConfig().getString(path, def);
        return colorize(name);
    }

    public static Material getMaterial(String path, Material def) {
        String matName = plugin.getConfig().getString(path);
        if (matName == null) return def;
        try {
            return Material.valueOf(matName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[SkyBanker] Invalid material '" + matName + "' at config path '" + path + "', using default.");
            return def;
        }
    }

    public static int getSlot(String path, int def) {
        return plugin.getConfig().getInt(path, def);
    }

    public static double getBankBalance(Player player) {
        if (!SkyBankerDatabase.hasAccount(player)) {
            SkyBankerDatabase.createAccount(player);
        }
        return SkyBankerDatabase.getBalance(player);
    }

    public static void setBankBalance(Player player, double amount) {
        SkyBankerDatabase.setBalance(player, amount);
    }

    public static int getBankLevel(Player player) {
        if (!SkyBankerDatabase.hasAccount(player)) {
            SkyBankerDatabase.createAccount(player);
        }
        return SkyBankerDatabase.getLevel(player);
    }

    public static void setBankLevel(Player player, int level) {
        SkyBankerDatabase.setLevel(player, level);
    }

    public static double getBankLimit(Player player) {
        return getTierMaxBalance(getBankLevel(player));
    }

    public static String getAccountName(Player player) {
        return getTierName(getBankLevel(player));
    }

    public static String getTierName(int level) {
        return getName("bank-tiers.tier-" + level + ".name", "&aStarter Account");
    }

    public static double getTierMaxBalance(int level) {
        if (plugin != null) {
            return plugin.getConfig().getDouble("bank-tiers.tier-" + level + ".max-balance", 50_000_000.0);
        }
        return 50_000_000.0;
    }

    public static double getTierCost(int level) {
        if (plugin != null) {
            return plugin.getConfig().getDouble("bank-tiers.tier-" + level + ".cost", 0.0);
        }
        return 0.0;
    }

    public static double getMaxInterest(int level) {
        if (plugin != null) {
            return plugin.getConfig().getDouble("bank-tiers.tier-" + level + ".max-interest", 250000.0);
        }
        return 250000.0;
    }

    public static Material getTierMaterial(int level) {
        return getMaterial("bank-tiers.tier-" + level + ".material", Material.PAPER);
    }

    public static List<String> getTierLore(Player player, int tierLevel) {
        int currentLvl = getBankLevel(player);

        String status;
        if (tierLevel == currentLvl) {
            status = getMessage("tier-status.current");
        } else if (tierLevel < currentLvl) {
            status = getMessage("tier-status.unlocked");
        } else if (tierLevel == currentLvl + 1) {
            status = getMessage("tier-status.next-tier");
        } else {
            status = getMessage("tier-status.locked");
        }

        java.util.HashMap<String, String> placeholders = new java.util.HashMap<>();
        placeholders.put("%cost%", formatNum(getTierCost(tierLevel)));
        placeholders.put("%max_balance%", formatNum(getTierMaxBalance(tierLevel)));
        placeholders.put("%max_interest%", formatNum(getMaxInterest(tierLevel)));
        placeholders.put("%status%", status);

        return getLore("bank-tiers.tier-" + tierLevel + ".lore", placeholders);
    }

    public static void addTransaction(Player player, boolean isDeposit, double amount) {
        String type = isDeposit ? "DEPOSIT" : "WITHDRAW";
        SkyBankerDatabase.addTransaction(player, amount, type);
    }

    public static List<String> getTransactionLore(Player player) {
        List<String> lore = new ArrayList<>();
        lore.add("");

        try {
            List<Map<String, Object>> rawTransactions = SkyBankerDatabase.getTransactions(player);

            if (rawTransactions == null || rawTransactions.isEmpty()) {
                lore.add(colorize(plugin.getConfig().getString("gui.no-transactions-text", "&7No transactions yet")));
                return lore;
            }

            String format = plugin.getConfig().getString("gui.transaction-format",
                    "%sign%&6%amount%&7, %time% ago by %actor%");

            for (Map<String, Object> tx : rawTransactions) {
                String type = (String) tx.getOrDefault("type", "DEPOSIT");
                double amount = (double) tx.getOrDefault("amount", 0.0);
                long timestamp = (long) tx.getOrDefault("timestamp", System.currentTimeMillis());
                String actor = (String) tx.getOrDefault("actor", player.getName());

                boolean isDeposit = type.equals("DEPOSIT") || type.equals("INTEREST");
                String sign = isDeposit ? "&a+" : "&c-";
                String timeAgo = formatTimeAgo(System.currentTimeMillis() - timestamp);

                String line = format
                        .replace("%sign%", sign)
                        .replace("%amount%", formatNum(amount))
                        .replace("%time%", timeAgo)
                        .replace("%actor%", actor);

                lore.add(colorize(line));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[SkyBanker] Failed to build transaction lore: " + e.getMessage());
            e.printStackTrace();
            if (lore.size() <= 1) {
                lore.add(colorize(plugin.getConfig().getString("gui.no-transactions-text", "&7No transactions yet")));
            }
        }
        return lore;
    }

    private static String formatTimeAgo(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) return days + (days == 1 ? " day" : " days");
        if (hours > 0) return hours + (hours == 1 ? " hour" : " hours");
        if (minutes > 0) return minutes + (minutes == 1 ? " minute" : " minutes");
        return "a few seconds";
    }

    public static String getInterestTimer(Player player) {
        long last = SkyBankerDatabase.getLastInterestTime(player);
        if (last == 0) return "31h 0m";

        long intervalHours = plugin.getConfig().getLong("settings.interest-interval-hours", 31);
        long intervalMillis = intervalHours * 60L * 60L * 1000L;

        long elapsed = System.currentTimeMillis() - last;
        long remaining = intervalMillis - elapsed;
        if (remaining <= 0) return "Ready!";

        long hours = remaining / (1000 * 60 * 60);
        long minutes = (remaining / (1000 * 60)) % 60;
        return hours + "h " + minutes + "m";
    }

    // =====================================================================
    // Interest info (Last interest / Projected) - for the Information item
    // =====================================================================

    /** The coin amount of the player's most recent interest payout, or 0. */
    public static double getLastInterestAmount(Player player) {
        return SkyBankerDatabase.getLastInterestAmount(player);
    }

    /**
     * Previews what interest the player would earn RIGHT NOW using their
     * current balance/tier - reuses SkyInterest's exact real payout
     * formula, so this preview never drifts out of sync with reality.
     */
    public static double getProjectedInterest(Player player) {
        double balance = getBankBalance(player);
        int level = getBankLevel(player);
        return SkyInterest.calculateInterest(plugin, balance, level);
    }

    /** The projected interest expressed as a % of current balance. */
    public static double getProjectedInterestPercent(Player player) {
        double balance = getBankBalance(player);
        if (balance <= 0) return 0;
        double projected = getProjectedInterest(player);
        return (projected / balance) * 100.0;
    }

    public static double getSpaceLeft(Player player) {
        double limit = getBankLimit(player);
        double balance = getBankBalance(player);
        return Math.max(0, limit - balance);
    }

    public static double getProgressPercent(Player player) {
        double limit = getBankLimit(player);
        if (limit <= 0) return 0;
        double balance = getBankBalance(player);
        return Math.min(100.0, (balance / limit) * 100.0);
    }

    public static double getNextUpgradeCost(Player player) {
        int level = getBankLevel(player);
        if (level >= 7) return -1;
        return getTierCost(level + 1);
    }

    public static double getWithdrawPercent() {
        if (plugin != null) {
            return plugin.getConfig().getDouble("settings.withdraw-percent-option", 0.2);
        }
        return 0.2;
    }

    public static List<Map<String, Object>> getTopBalances(int limit) {
        return SkyBankerDatabase.getTopBalances(limit);
    }

    public static Map<String, Object> getRankByName(String playerName) {
        return SkyBankerDatabase.getRankByName(playerName);
    }

    public static int getTotalAccountCount() {
        return SkyBankerDatabase.getTotalAccountCount();
    }

    public static double parseAmount(String input) {
        input = input.toLowerCase().trim();
        try {
            if (input.endsWith("b")) {
                return Double.parseDouble(input.replace("b", "")) * 1_000_000_000;
            } else if (input.endsWith("m")) {
                return Double.parseDouble(input.replace("m", "")) * 1_000_000;
            } else if (input.endsWith("k")) {
                return Double.parseDouble(input.replace("k", "")) * 1_000;
            } else {
                return Double.parseDouble(input);
            }
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static String formatNum(double amount) {
        return String.format("%,d", (long) amount);
    }

    /** Formats with one decimal place, used for the "Projected" preview. */
    public static String formatDecimal(double amount) {
        return String.format("%,.1f", amount);
    }
}