package me.skoohy.skybanker;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

public class SkyInterest {

    public static void startInterestTask(Plugin plugin) {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long currentTime = System.currentTimeMillis();

            long intervalHours = plugin.getConfig().getLong("settings.interest-interval-hours", 31);
            long intervalMillis = intervalHours * 60L * 60L * 1000L;

            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();

                long lastInterest = SkyBankerDatabase.getLastInterestTime(uuid);

                if (lastInterest == 0) {
                    SkyBankerDatabase.setLastInterestTime(uuid, currentTime);
                    continue;
                }

                if (currentTime - lastInterest >= intervalMillis) {

                    SkyBankerDatabase.setLastInterestTime(uuid, currentTime);

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) return;

                        double currentBalance = BankManager.getBankBalance(player);
                        int level = BankManager.getBankLevel(player);
                        double interest = calculateInterest(plugin, currentBalance, level);

                        if (interest > 0) {
                            double limit = getLimitForLevel(plugin, level);
                            double newBalance = Math.min(limit, currentBalance + interest);

                            BankManager.setBankBalance(player, newBalance);
                            SkyBankerDatabase.addTransaction(player, interest, "INTEREST");

                            String rawMsg = BankManager.getMessage("interest-received");
                            String formattedMsg = rawMsg
                                    .replace("%amount%", BankManager.formatNum(interest))
                                    .replace("%balance%", BankManager.formatNum(newBalance));

                            player.sendMessage("");
                            player.sendMessage(formattedMsg);
                            player.sendMessage("");

                            BankerSound.playUpgrade(player);
                        }
                    });
                }
            }
        }, 0L, 20L * 60L);
    }

    public static double getLimitForLevel(Plugin plugin, int level) {
        String path = "bank-tiers.tier-" + level + ".max-balance";
        return plugin.getConfig().getDouble(path, 50_000_000.0);
    }

    /**
     * Calculates interest for a given balance/tier using Hypixel-style
     * tranches. Made public so BankManager can reuse this EXACT same
     * formula for the "Projected interest" preview shown in the bank
     * menu's Information item - keeping the preview and the real payout
     * always in sync instead of risking two separate formulas drifting
     * apart over time.
     */
    public static double calculateInterest(Plugin plugin, double balance, int level) {
        double interest = 0;

        if (balance > 0) {
            double tier1 = Math.min(balance, 10_000_000);
            interest += tier1 * 0.02;
        }
        if (balance > 10_000_000 && level >= 2) {
            double tier2 = Math.min(balance - 10_000_000, level == 2 ? 10_000_000 : (level == 3 ? 20_000_000 : 10_000_000));
            interest += tier2 * 0.01;
        }
        if (balance > 20_000_000 && level >= 3) {
            double tier3 = Math.min(balance - 20_000_000, 10_000_000);
            interest += tier3 * 0.005;
        }
        if (balance > 30_000_000 && level >= 4) {
            double tier4 = Math.min(balance - 30_000_000, 20_000_000);
            interest += tier4 * 0.002;
        }
        if (balance > 50_000_000 && level >= 5) {
            double tier5 = Math.min(balance - 50_000_000, 50_000_000);
            interest += tier5 * 0.001;
        }
        if (balance > 100_000_000 && level >= 6) {
            double tier6 = Math.min(balance - 100_000_000, 5_000_000_000.0);
            interest += tier6 * 0.0001;
        }
        if (balance > 5_100_000_000.0 && level >= 7) {
            double tier7 = Math.min(balance - 5_100_000_000.0, 60_000_000_000.0);
            interest += tier7 * 0.00001;
        }

        String path = "bank-tiers.tier-" + level + ".max-interest";
        double maxCap = plugin.getConfig().getDouble(path, 250_000.0);

        return Math.min(interest, maxCap);
    }
}