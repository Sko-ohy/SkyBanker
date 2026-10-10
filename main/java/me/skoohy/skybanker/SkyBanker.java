package me.skoohy.skybanker;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public final class SkyBanker extends JavaPlugin implements CommandExecutor {

    private static SkyBanker instance;
    private static Economy economy = null;
    public static String PREFIX = "§e[NPC] Banker: §f";

    @Override
    public void onEnable() {
        instance = this;
        this.saveDefaultConfig();

        if (!new File(getDataFolder(), "messages.yml").exists()) {
            saveResource("messages.yml", false);
        }

        SkyBankerDatabase.init(this);
        BankManager.init(this);

        SkyInterest.startInterestTask(this);

        if (!setupEconomy()) {
            getLogger().severe("Disabled due to no Vault economy provider found!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(new BankMenu(), this);

        // /bank - opens the bank menu only, unchanged
        if (getCommand("bank") != null) {
            getCommand("bank").setExecutor(this);
        }

        // /skybanker and /sb share the exact same executor/tab-completer,
        // registered under two separate command names. /sb does NOT open
        // the bank menu (that's /bank's job only) - it's the same admin/
        // utility command (reload, top, user, placeholder, help) just
        // under a shorter alias. Note: if another installed plugin already
        // owns the plain "/sb" alias, Bukkit may route it to that plugin
        // instead depending on load order - ours is still reachable as
        // "/skybanker:sb" in that case.
        SkyBankerCommand bankerCommand = new SkyBankerCommand(this);
        if (getCommand("skybanker") != null) {
            getCommand("skybanker").setExecutor(bankerCommand);
            getCommand("skybanker").setTabCompleter(bankerCommand);
        }
        if (getCommand("sb") != null) {
            getCommand("sb").setExecutor(bankerCommand);
            getCommand("sb").setTabCompleter(bankerCommand);
        }

        // Register PlaceholderAPI expansion, if PlaceholderAPI is installed
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new SkyBankerPlaceholders(this).register();
            getLogger().info("PlaceholderAPI hook enabled! Use /skybanker placeholder to see the list.");
        } else {
            getLogger().info("PlaceholderAPI not found - placeholders disabled (this is optional, not an error).");
        }

        getLogger().info("SkyBanker loaded successfully!");
    }

    @Override
    public void onDisable() {
        SkyBankerDatabase.closeConnection();
        getLogger().info("SkyBanker has been disabled.");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }

    public static Economy getEconomy() {
        return economy;
    }

    public static SkyBanker getInstance() {
        return instance;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }

        if (command.getName().equalsIgnoreCase("bank")) {
            if (BankMenu.canUseBankCommand(player)) {
                BankMenu.openMainBank(player);
            }
            return true;
        }
        return false;
    }
}