package me.skoohy.skybanker;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Registers all %skybanker_...% placeholders with PlaceholderAPI.
 * Test any of these in-game with: /papi parse <player> %skybanker_balance%
 *
 * IMPORTANT: the identifier must NOT contain an underscore - PAPI splits
 * a placeholder at the FIRST underscore to separate identifier from
 * parameter. "sky_banker" (with an underscore) broke that matching,
 * which is why placeholders were printing back unresolved with no error.
 * "skybanker" (no underscore) fixes it.
 *
 * Available placeholders:
 *   %skybanker_balance%             - bank balance, formatted (e.g. 1,000,000)
 *   %skybanker_purse%                - Vault purse/wallet balance, formatted
 *   %skybanker_limit%                - current tier's max bank balance
 *   %skybanker_level%                - tier number (1-7)
 *   %skybanker_account%              - tier display name (e.g. "Gold Account")
 *   %skybanker_interest_timer%       - time left until next interest payout
 *   %skybanker_next_upgrade_cost%    - cost of the next tier (or "MAX" at tier 7)
 *   %skybanker_space_left%           - coins of room left before hitting the limit
 *   %skybanker_progress%             - % of bank limit filled (e.g. "73%")
 */
public class SkyBankerPlaceholders extends PlaceholderExpansion {

    private final SkyBanker plugin;

    public SkyBankerPlaceholders(SkyBanker plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "skybanker";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Skoohy";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        if (offlinePlayer == null || !offlinePlayer.isOnline()) {
            return "";
        }
        Player player = (Player) offlinePlayer;

        return switch (params.toLowerCase()) {
            case "balance" -> BankManager.formatNum(BankManager.getBankBalance(player));
            case "purse" -> BankManager.formatNum(SkyBanker.getEconomy().getBalance(player));
            case "limit" -> BankManager.formatNum(BankManager.getBankLimit(player));
            case "level" -> String.valueOf(BankManager.getBankLevel(player));
            case "account" -> BankManager.getAccountName(player);
            case "interest_timer" -> BankManager.getInterestTimer(player);
            case "space_left" -> BankManager.formatNum(BankManager.getSpaceLeft(player));
            case "progress" -> String.format("%.0f%%", BankManager.getProgressPercent(player));
            case "next_upgrade_cost" -> {
                double cost = BankManager.getNextUpgradeCost(player);
                yield cost < 0 ? "MAX" : BankManager.formatNum(cost);
            }
            default -> null;
        };
    }
}