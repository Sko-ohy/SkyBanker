package me.skoohy.skybanker;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Shared executor for both /skybanker and /sb - registered twice in
 * SkyBanker.java under two different command names, but they run the
 * exact same code so behaviour always stays in sync between the two.
 *
 * Subcommands:
 *   reload <config.yml|message.yml|all>  - admin only
 *   top                                    - top 10 richest bank accounts
 *   user [player]                          - your rank, or another player's
 *   placeholder                            - admin only - lists all placeholders
 *   help                                   - lists all subcommands
 */
public class SkyBankerCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private static final String BORDER = "&6&m                                        &r";

    public SkyBankerCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String prefix = BankManager.getPrefix();

        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "reload" -> handleReload(sender, args, prefix);
            case "top" -> handleTop(sender);
            case "user" -> handleUser(sender, args);
            case "placeholder", "placeholders" -> handlePlaceholders(sender);
            case "help" -> sendHelp(sender, label);
            default -> sender.sendMessage(prefix + ChatColor.RED + "Unknown subcommand! Use /" + label + " help for a list.");
        }

        return true;
    }

    // =====================================================================
    // reload (admin only)
    // =====================================================================

    private void handleReload(CommandSender sender, String[] args, String prefix) {
        if (!sender.hasPermission("skybanker.admin")) {
            sender.sendMessage(prefix + ChatColor.RED + "You do not have permission to use this command!");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(prefix + ChatColor.RED + "Usage: /skybanker reload <config.yml|message.yml|all>");
            return;
        }

        String target = args[1].toLowerCase();

        if (target.equals("config.yml") || target.equals("config")) {
            plugin.reloadConfig();
            BankManager.init(plugin);
            sender.sendMessage(prefix + ChatColor.GREEN + "Successfully reloaded config.yml!");
        } else if (target.equals("message.yml") || target.equals("messages.yml") || target.equals("messages")) {
            sender.sendMessage(prefix + ChatColor.GREEN + "messages.yml is loaded live - no reload needed, your changes are already active!");
        } else if (target.equals("all")) {
            plugin.reloadConfig();
            BankManager.init(plugin);
            sender.sendMessage(prefix + ChatColor.GREEN + "Successfully reloaded all configuration files!");
        } else {
            sender.sendMessage(prefix + ChatColor.RED + "Unknown file! Use: config.yml, message.yml, or all");
        }
    }

    // =====================================================================
    // top - top 10 richest bank accounts
    // =====================================================================

    private void handleTop(CommandSender sender) {
        List<Map<String, Object>> top = BankManager.getTopBalances(10);

        sender.sendMessage(BankManager.colorize(BORDER));
        sender.sendMessage(BankManager.colorize("&6&lBANK LEADERBOARD &7- Top " + top.size()));
        sender.sendMessage("");

        if (top.isEmpty()) {
            sender.sendMessage(BankManager.colorize("&7No bank accounts exist yet."));
        } else {
            int rank = 1;
            for (Map<String, Object> row : top) {
                String name = (String) row.get("player_name");
                double balance = (double) row.get("balance");

                String rankColor = switch (rank) {
                    case 1 -> "&6";
                    case 2 -> "&7";
                    case 3 -> "&c";
                    default -> "&f";
                };

                sender.sendMessage(BankManager.colorize(
                        rankColor + "#" + rank + " &f" + name + " &7- &6$" + BankManager.formatNum(balance)
                ));
                rank++;
            }
        }

        sender.sendMessage("");
        sender.sendMessage(BankManager.colorize(BORDER));
    }

    // =====================================================================
    // user [player] - your rank, or another player's rank
    // =====================================================================

    private void handleUser(CommandSender sender, String[] args) {
        String prefix = BankManager.getPrefix();
        String targetName;

        if (args.length >= 2) {
            targetName = args[1];
        } else if (sender instanceof Player player) {
            targetName = player.getName();
        } else {
            sender.sendMessage(prefix + ChatColor.RED + "Console must specify a player name: /skybanker user <player>");
            return;
        }

        Map<String, Object> result = BankManager.getRankByName(targetName);

        if (result == null) {
            sender.sendMessage(prefix + ChatColor.RED + targetName + " has no bank account yet.");
            return;
        }

        double balance = (double) result.get("balance");
        int rank = (int) result.get("rank");
        int total = BankManager.getTotalAccountCount();

        sender.sendMessage(BankManager.colorize(BORDER));
        sender.sendMessage(BankManager.colorize("&6&l" + targetName + "'s Bank Rank"));
        sender.sendMessage("");
        sender.sendMessage(BankManager.colorize("&7Balance: &6$" + BankManager.formatNum(balance)));
        sender.sendMessage(BankManager.colorize("&7Rank: &e#" + rank + " &7of &e" + total));
        sender.sendMessage(BankManager.colorize(BORDER));
    }

    // =====================================================================
    // placeholder - ADMIN ONLY - lists all PlaceholderAPI placeholders
    // =====================================================================

    private void handlePlaceholders(CommandSender sender) {
        if (!sender.hasPermission("skybanker.admin")) {
            sender.sendMessage(BankManager.getPrefix() + ChatColor.RED + "You do not have permission to use this command!");
            return;
        }

        sender.sendMessage(BankManager.colorize(BORDER));
        sender.sendMessage(BankManager.colorize("&6&lSKYBANKER PLACEHOLDERS"));
        sender.sendMessage(BankManager.colorize("&7Requires PlaceholderAPI. Test with /papi parse <player> <placeholder>"));
        sender.sendMessage("");

        String[][] placeholders = {
                {"%skybanker_balance%", "Bank balance"},
                {"%skybanker_purse%", "Vault purse/wallet balance"},
                {"%skybanker_limit%", "Current tier's max bank balance"},
                {"%skybanker_level%", "Tier number (1-7)"},
                {"%skybanker_account%", "Tier display name"},
                {"%skybanker_interest_timer%", "Time left until next interest"},
                {"%skybanker_next_upgrade_cost%", "Cost of next tier (or MAX)"},
                {"%skybanker_space_left%", "Coins of room left in bank"},
                {"%skybanker_progress%", "% of bank limit filled"}
        };

        for (String[] p : placeholders) {
            sender.sendMessage(BankManager.colorize("&e" + p[0] + " &8- &7" + p[1]));
        }

        sender.sendMessage("");
        sender.sendMessage(BankManager.colorize(BORDER));
    }

    // =====================================================================
    // help
    // =====================================================================

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(BankManager.colorize(BORDER));
        sender.sendMessage(BankManager.colorize("&6&lSKYBANKER COMMANDS"));
        sender.sendMessage("");
        sender.sendMessage(BankManager.colorize("&e/" + label + " top &8- &7View the top 10 richest accounts"));
        sender.sendMessage(BankManager.colorize("&e/" + label + " user [player] &8- &7View a bank rank"));

        // placeholder and reload are both admin-only, shown together
        if (sender.hasPermission("skybanker.admin")) {
            sender.sendMessage(BankManager.colorize("&e/" + label + " placeholder &8- &7List available placeholders"));
            sender.sendMessage(BankManager.colorize("&e/" + label + " reload <config.yml|message.yml|all> &8- &7Reload files"));
        }

        sender.sendMessage("");
        sender.sendMessage(BankManager.colorize("&7Use &e/bank &7to open your bank account."));
        sender.sendMessage(BankManager.colorize(BORDER));
    }

    // =====================================================================
    // Tab completion
    // =====================================================================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("top", "user", "help"));
            if (sender.hasPermission("skybanker.admin")) {
                subs.add("reload");
                subs.add("placeholder");
            }
            String partial = args[0].toLowerCase();
            completions = subs.stream()
                    .filter(s -> s.startsWith(partial))
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("reload") && sender.hasPermission("skybanker.admin")) {
                completions = List.of("config.yml", "message.yml", "all").stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (args[0].equalsIgnoreCase("user")) {
                String partial = args[1].toLowerCase();
                completions = Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(partial))
                        .collect(Collectors.toList());
            }
        }

        return completions;
    }
}