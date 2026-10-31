package me.skoohy.skybanker;

import me.skoohy.skybanker.PlayerSettings.SoundType;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sound settings menu (right-click the Sounds button in Settings).
 *
 *   Left-click  = turn that sound ON/OFF
 *   Right-click = preview that sound
 *
 * Everything is read from memory (PlayerSettings) - opening or clicking
 * here never queries the database. Saving happens async.
 */
public class BankSoundMenu {

    private static final String PATH = "gui.sound-menu";

    private record Entry(SoundType type, String key, int defSlot, Material defMat, String defLabel) {
    }

    private static final List<Entry> ENTRIES = List.of(
            new Entry(SoundType.MASTER, "master", 4, Material.NOTE_BLOCK, "All Sounds"),
            new Entry(SoundType.INTEREST, "interest", 10, Material.EXPERIENCE_BOTTLE, "Interest"),
            new Entry(SoundType.DEPOSIT, "deposit", 12, Material.CHEST, "Deposit"),
            new Entry(SoundType.WITHDRAW, "withdraw", 14, Material.DROPPER, "Withdraw"),
            new Entry(SoundType.UPGRADE, "upgrade", 16, Material.GOLD_BLOCK, "Upgrade"),
            new Entry(SoundType.ERROR, "error", 21, Material.BARRIER, "Error"),
            new Entry(SoundType.CLICK, "click", 23, Material.STONE_BUTTON, "Menu Click")
    );

    private BankSoundMenu() {
    }

    private static FileConfiguration cfg() {
        return SkyBanker.getInstance().getConfig();
    }

    // =====================================================================
    // Open
    // =====================================================================

    public static void open(Player player) {
        BankHolder holder = new BankHolder(BankHolder.MenuType.SOUNDS);
        String title = BankManager.getName(PATH + ".title", "Sound Settings");
        int configSize = cfg().getInt(PATH + ".size", 36);
        int size = Math.min(54, Math.max(9, (int) Math.ceil(configSize / 9.0) * 9));

        Inventory gui = Bukkit.createInventory(holder, size, title);
        holder.setInventory(gui);

        ItemStack filler = fillerItem();
        for (int i = 0; i < size; i++) {
            gui.setItem(i, filler);
        }

        render(gui, player);
        player.openInventory(gui);
    }

    /** (Re)draws all buttons into an already-open sound menu. */
    private static void render(Inventory gui, Player player) {
        for (Entry e : ENTRIES) {
            setItem(gui, slot(e.key(), e.defSlot()), buildEntryItem(player, e));
        }
        setItem(gui, slot("go-back", 31), build(PATH + ".items.go-back", Material.ARROW, new HashMap<>(), false));
    }

    // =====================================================================
    // Clicks (called from BankMenu for MenuType.SOUNDS)
    // =====================================================================

    public static void handleClick(Player player, int slot, ClickType click) {
        if (slot == slot("go-back", 31)) {
            BankerSound.playClick(player);
            BankSettingsMenu.open(player);
            return;
        }

        for (Entry e : ENTRIES) {
            if (slot != slot(e.key(), e.defSlot())) continue;

            // Right-click = preview (always plays, even if muted)
            if (click.isRightClick()) {
                BankerSound.preview(player, e.type());
                return;
            }
            if (!click.isLeftClick()) return;

            boolean on = PlayerSettings.toggle(player, e.type());

            // Only redraw our own sound menu (the player could have closed it)
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof BankHolder h && h.getMenuType() == BankHolder.MenuType.SOUNDS) {
                render(top, player);
            }

            // Play a short preview when turning something ON
            boolean masterOn = PlayerSettings.isSoundOn(player.getUniqueId(), SoundType.MASTER);
            if (on && masterOn) {
                BankerSound.preview(player, e.type());
            }

            // Small action-bar notice (doesn't spam the chat)
            String text = msg("sound-toggled", "&7%sound%: %status%")
                    .replace("%sound%", label(e))
                    .replace("%status%", status(on));
            player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(text));
            return;
        }
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    /** "ON" / "OFF" text from config (colored). */
    public static String status(boolean on) {
        return BankManager.colorize(on
                ? cfg().getString(PATH + ".status-on", "&aON")
                : cfg().getString(PATH + ".status-off", "&cOFF"));
    }

    /** Message from messages.yml with a built-in fallback for old files. */
    public static String msg(String key, String def) {
        String m = BankManager.getMessage(key);
        return m.contains("Missing message") ? BankManager.colorize(def) : m;
    }

    private static String label(Entry e) {
        return BankManager.colorize(cfg().getString(PATH + ".items." + e.key() + ".label", e.defLabel()));
    }

    private static int slot(String item, int def) {
        return cfg().getInt(PATH + ".items." + item + ".slot", def);
    }

    private static void setItem(Inventory gui, int slot, ItemStack item) {
        if (slot >= 0 && slot < gui.getSize()) {
            gui.setItem(slot, item);
        }
    }

    private static ItemStack buildEntryItem(Player player, Entry e) {
        boolean on = PlayerSettings.isSoundOn(player.getUniqueId(), e.type());
        Map<String, String> ph = new HashMap<>();
        ph.put("%status%", status(on));
        return build(PATH + ".items." + e.key(), e.defMat(), ph, on);
    }

    private static ItemStack build(String itemPath, Material defMat, Map<String, String> ph, boolean glint) {
        Material mat = BankManager.getMaterial(itemPath + ".material", defMat);
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = cfg().getString(itemPath + ".name", "&7Item");
            for (Map.Entry<String, String> en : ph.entrySet()) {
                name = name.replace(en.getKey(), en.getValue());
            }
            meta.setDisplayName(BankManager.colorize(name));
            meta.setLore(BankManager.getLore(itemPath + ".lore", ph));
            if (glint) {
                meta.setEnchantmentGlintOverride(true);
            }
            item.setItemMeta(meta);
        }
        return item;
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
}
