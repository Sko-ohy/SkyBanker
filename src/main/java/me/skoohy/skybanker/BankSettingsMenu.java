package me.skoohy.skybanker;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The bank "Settings" menu (opened from the Settings button in the main
 * bank menu). Kept in its own class so BankMenu stays small.
 *
 * Layout (36 slots by default, all slots are configurable):
 *   center (13) - Player head   (username, status, created date, balance...)
 *   bottom (31) - Go Back
 *
 * Admins can turn the whole feature off with
 * gui.settings-menu.enabled: false in config.yml.
 *
 * Click events are routed here from BankMenu (MenuType.SETTINGS).
 */
public class BankSettingsMenu {

    private static final String PATH = "gui.settings-menu";

    private BankSettingsMenu() {
    }

    private static FileConfiguration cfg() {
        return SkyBanker.getInstance().getConfig();
    }

    /** True unless the admin set gui.settings-menu.enabled to false. */
    public static boolean isEnabled() {
        return cfg().getBoolean(PATH + ".enabled", true);
    }

    // =====================================================================
    // Open the menu
    // =====================================================================

    public static void open(Player player) {
        if (!isEnabled()) {
            // Message lives in messages.yml (key: settings-disabled). If an older
            // messages.yml on the server doesn't have the key yet, use the default.
            String msg = BankManager.getMessage("settings-disabled");
            if (msg.contains("Missing message")) {
                msg = BankManager.colorize("&cThe settings menu is disabled on this server.");
            }
            player.sendMessage(BankManager.getPrefix() + msg);
            BankerSound.playError(player);
            BankMenu.openMainBank(player);
            return;
        }

        // --- gather the data (getBankBalance also creates the account if needed) ---
        double balance = BankManager.getBankBalance(player);
        double limit = BankManager.getBankLimit(player);
        double purse = SkyBanker.getEconomy().getBalance(player);

        Map<String, Object> stats = SkyBankerDatabase.getAccountStats(player.getUniqueId());
        long createdAt = System.currentTimeMillis();
        if (stats != null) {
            createdAt = (Long) stats.get("created_at");
        }

        String rank = "N/A";
        Map<String, Object> rankInfo = BankManager.getRankByName(player.getName());
        if (rankInfo != null) {
            rank = String.valueOf(rankInfo.get("rank"));
        }

        boolean full = limit > 0 && balance >= limit;
        String status = full
                ? cfg().getString(PATH + ".status-full", "&6Bank Full")
                : cfg().getString(PATH + ".status-active", "&aActive");

        double progress = BankManager.getProgressPercent(player);

        // --- placeholders shared by the items ---
        Map<String, String> ph = new HashMap<>();
        ph.put("%player%", player.getName());
        ph.put("%status%", status);
        ph.put("%account%", BankManager.getAccountName(player));
        ph.put("%created%", formatDate(createdAt));
        ph.put("%created_ago%", formatAge(System.currentTimeMillis() - createdAt));
        ph.put("%balance%", BankManager.formatNum(balance));
        ph.put("%purse%", BankManager.formatNum(purse));
        ph.put("%limit%", BankManager.formatNum(limit));
        ph.put("%space_left%", BankManager.formatNum(BankManager.getSpaceLeft(player)));
        ph.put("%progress%", String.format("%.0f", progress));
        ph.put("%progress_bar%", progressBar(progress));
        ph.put("%rank%", rank);
        ph.put("%total_accounts%", String.valueOf(BankManager.getTotalAccountCount()));
        ph.put("%interest_timer%", BankManager.getInterestTimer(player));
        ph.put("%last_interest%", BankManager.formatNum(BankManager.getLastInterestAmount(player)));
        ph.put("%projected_interest%", BankManager.formatDecimal(BankManager.getProjectedInterest(player)));
        ph.put("%projected_percent%", String.format("%.0f", BankManager.getProjectedInterestPercent(player)));

        // --- build the inventory ---
        BankHolder holder = new BankHolder(BankHolder.MenuType.SETTINGS);
        String title = BankManager.getName(PATH + ".title", "Bank Settings");
        int configSize = cfg().getInt(PATH + ".size", 36);
        int size = Math.min(54, Math.max(9, (int) Math.ceil(configSize / 9.0) * 9));

        Inventory gui = Bukkit.createInventory(holder, size, title);
        holder.setInventory(gui);

        ItemStack filler = fillerItem();
        for (int i = 0; i < size; i++) {
            gui.setItem(i, filler);
        }

        // center: the player's own head with all the account info
        setItem(gui, slot("profile", 13), buildProfileItem(player, ph));

        // bottom: go back
        setItem(gui, slot("go-back", 31), buildItem(PATH + ".items.go-back", Material.ARROW, ph));

        player.openInventory(gui);
    }

    // =====================================================================
    // Clicks (called from BankMenu for MenuType.SETTINGS)
    // =====================================================================

    public static void handleClick(Player player, int slot) {
        if (slot == slot("go-back", 31)) {
            BankerSound.playClick(player);
            BankMenu.openMainBank(player);
        }
        // The other items are information only, so clicking them does nothing.
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private static int slot(String item, int def) {
        return cfg().getInt(PATH + ".items." + item + ".slot", def);
    }

    private static void setItem(Inventory gui, int slot, ItemStack item) {
        if (slot >= 0 && slot < gui.getSize()) {
            gui.setItem(slot, item);
        }
    }

    private static ItemStack fillerItem() {
        Material mat = BankManager.getMaterial(PATH + ".filler-material", Material.GRAY_STAINED_GLASS_PANE);
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String applyPlaceholders(String text, Map<String, String> ph) {
        for (Map.Entry<String, String> e : ph.entrySet()) {
            text = text.replace(e.getKey(), e.getValue());
        }
        return text;
    }

    /** Sets the display name + lore of an item from the config path. */
    private static void decorate(ItemMeta meta, String itemPath, Map<String, String> ph) {
        String rawName = cfg().getString(itemPath + ".name", "&7Item");
        meta.setDisplayName(BankManager.colorize(applyPlaceholders(rawName, ph)));
        meta.setLore(BankManager.getLore(itemPath + ".lore", ph));
    }

    private static ItemStack buildItem(String itemPath, Material defMat, Map<String, String> ph) {
        Material mat = BankManager.getMaterial(itemPath + ".material", defMat);
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            decorate(meta, itemPath, ph);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** The player's own skin as a head item. */
    private static ItemStack buildProfileItem(Player player, Map<String, String> ph) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta rawMeta = head.getItemMeta();
        if (rawMeta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(player);
            decorate(skullMeta, PATH + ".items.profile", ph);
            head.setItemMeta(skullMeta);
        }
        return head;
    }

    private static String progressBar(double percent) {
        int total = 20;
        int filled = (int) Math.round(Math.max(0, Math.min(100, percent)) / 100.0 * total);
        return "&a" + "|".repeat(filled) + "&7" + "|".repeat(total - filled);
    }

    private static String formatDate(long millis) {
        String pattern = cfg().getString(PATH + ".date-format", "dd MMM yyyy");
        DateTimeFormatter formatter;
        try {
            formatter = DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH);
        } catch (IllegalArgumentException e) {
            formatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
        }
        return formatter.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
    }

    private static String formatAge(long millis) {
        long seconds = Math.max(0, millis / 1000);
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) return days + (days == 1 ? " day ago" : " days ago");
        if (hours > 0) return hours + (hours == 1 ? " hour ago" : " hours ago");
        if (minutes > 0) return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
        return "just now";
    }
}