package me.skoohy.skybanker;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Personal Vault - a private item storage the player buys once.
 *
 * Flow:  Main menu -> Vault button
 *          not bought -> "Confirm purchase" menu -> pays the price -> opens vault
 *          bought     -> opens the vault
 *
 * Performance / safety design:
 *  - Contents are saved ONLY when the vault is closed (not on every click).
 *  - Saving goes through ONE background thread (FIFO), so the main thread never
 *    waits for the database and writes can never arrive out of order.
 *  - The newest saved contents are kept in memory (CACHE) while the player is
 *    online, so re-opening instantly never reads stale data from the database.
 *  - Owned/not-owned is cached in memory too.
 *  - On shutdown, any open vaults are saved and the writer is flushed.
 *
 * The button lives in the Settings menu (right of the profile head).
 * By default the vault is a plain chest (storage rows only). Optionally
 * (gui.vault-menu.close-button: true) one extra bottom row with a Close button is added.
 *
 * Config: vault.enabled / vault.price / vault.rows, plus the item and menu
 * settings under gui.settings-menu.items.vault, gui.vault-menu and gui.vault-confirm-menu.
 */
public class BankVault implements Listener {

    private static final String MAIN_ITEM = "gui.settings-menu.items.vault";
    private static final String VAULT_MENU = "gui.vault-menu";
    private static final String CONFIRM = "gui.vault-confirm-menu";

    /** Marks an inventory as somebody's vault (and remembers whose). */
    public static class VaultHolder implements InventoryHolder {
        private final UUID owner;
        private final int storageSize;
        private Inventory inventory;

        VaultHolder(UUID owner, int storageSize) {
            this.owner = owner;
            this.storageSize = storageSize;
        }

        /** Number of real storage slots (the control row below is not storage). */
        int getStorageSize() {
            return storageSize;
        }

        UUID getOwner() {
            return owner;
        }

        void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    // Latest saved contents per online player (empty array = empty vault)
    private static final Map<UUID, byte[]> CACHE = new ConcurrentHashMap<>();
    private static final Set<UUID> DIRTY = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Boolean> OWNED = new ConcurrentHashMap<>();

    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SkyBanker-Vault-Writer");
        t.setDaemon(true);
        return t;
    });

    private static FileConfiguration cfg() {
        return SkyBanker.getInstance().getConfig();
    }

    // =====================================================================
    // Config helpers
    // =====================================================================

    public static boolean isEnabled() {
        return cfg().getBoolean("vault.enabled", true);
    }

    public static double getPrice() {
        return Math.max(0, cfg().getDouble("vault.price", 5_000_000.0));
    }

    private static int getRows() {
        return Math.max(1, Math.min(closeButtonEnabled() ? 5 : 6, cfg().getInt("vault.rows", 3)));
    }

    /** Optional extra bottom row with a Close button (off by default). */
    private static boolean closeButtonEnabled() {
        return cfg().getBoolean(VAULT_MENU + ".close-button", false);
    }

    public static int buttonSlot() {
        return cfg().getInt(MAIN_ITEM + ".slot", 15);
    }

    private static boolean isOwned(UUID uuid) {
        Boolean owned = OWNED.get(uuid);
        if (owned == null) {
            owned = SkyBankerDatabase.hasVault(uuid);
            OWNED.put(uuid, owned);
        }
        return owned;
    }

    private static String msg(String key, String def) {
        return BankSoundMenu.msg(key, def);
    }

    // =====================================================================
    // Main menu button
    // =====================================================================

    /** Adds the Vault button to the Settings menu (called from BankSettingsMenu.open). */
    public static void setButton(Inventory gui, Player player) {
        if (!isEnabled()) return;
        int slot = buttonSlot();
        if (slot < 0 || slot >= gui.getSize()) return;

        boolean owned = isOwned(player.getUniqueId());
        Map<String, String> ph = new HashMap<>();
        ph.put("%price%", BankManager.formatNum(getPrice()));
        ph.put("%slots%", String.valueOf(getRows() * 9));

        gui.setItem(slot, build(MAIN_ITEM, owned ? ".lore-unlocked" : ".lore-locked", Material.ENDER_CHEST, ph));
    }

    /** Clicking the Vault button in the main menu. */
    public static void onButtonClick(Player player) {
        if (!isEnabled()) return;
        if (isOwned(player.getUniqueId())) {
            openVault(player);
        } else {
            openConfirm(player);
        }
    }

    // =====================================================================
    // Confirm purchase menu
    // =====================================================================

    private static void openConfirm(Player player) {
        BankHolder holder = new BankHolder(BankHolder.MenuType.VAULT_BUY);
        String title = BankManager.getName(CONFIRM + ".title", "Buy Personal Vault");
        int configSize = cfg().getInt(CONFIRM + ".size", 27);
        int size = Math.min(54, Math.max(9, (int) Math.ceil(configSize / 9.0) * 9));

        Inventory gui = Bukkit.createInventory(holder, size, title);
        holder.setInventory(gui);

        ItemStack filler = filler(CONFIRM + ".filler-material");
        for (int i = 0; i < size; i++) {
            gui.setItem(i, filler);
        }

        Map<String, String> ph = new HashMap<>();
        ph.put("%price%", BankManager.formatNum(getPrice()));
        ph.put("%slots%", String.valueOf(getRows() * 9));
        ph.put("%purse%", BankManager.formatNum(SkyBanker.getEconomy().getBalance(player)));

        put(gui, cfg().getInt(CONFIRM + ".items.confirm.slot", 11),
                build(CONFIRM + ".items.confirm", ".lore", Material.LIME_STAINED_GLASS_PANE, ph));
        put(gui, cfg().getInt(CONFIRM + ".items.info.slot", 13),
                build(CONFIRM + ".items.info", ".lore", Material.ENDER_CHEST, ph));
        put(gui, cfg().getInt(CONFIRM + ".items.cancel.slot", 15),
                build(CONFIRM + ".items.cancel", ".lore", Material.RED_STAINED_GLASS_PANE, ph));

        player.openInventory(gui);
    }

    /** Clicks in the confirm menu (routed from BankMenu, MenuType.VAULT_BUY). */
    public static void handleConfirmClick(Player player, int slot) {
        if (slot == cfg().getInt(CONFIRM + ".items.confirm.slot", 11)) {
            buy(player);
        } else if (slot == cfg().getInt(CONFIRM + ".items.cancel.slot", 15)) {
            BankerSound.playClick(player);
            BankSettingsMenu.open(player);
        }
    }

    private static void buy(Player player) {
        UUID uuid = player.getUniqueId();

        if (!isEnabled()) {
            BankSettingsMenu.open(player);
            return;
        }

        if (isOwned(uuid)) {
            player.sendMessage(BankManager.getPrefix() + msg("vault-already-owned", "&cYou already own the vault!"));
            openVault(player);
            return;
        }

        double price = getPrice();
        Economy economy = SkyBanker.getEconomy();
        double purse = economy.getBalance(player);

        if (purse < price) {
            player.sendMessage(BankManager.getPrefix() + msg("vault-not-enough",
                    "&cYou need &6$%price% &cto unlock the vault! You only have &6$%amount%&c.")
                    .replace("%price%", BankManager.formatNum(price))
                    .replace("%amount%", BankManager.formatNum(purse)));
            BankerSound.playError(player);
            BankSettingsMenu.open(player);
            return;
        }

        EconomyResponse response = economy.withdrawPlayer(player, price);
        if (!response.transactionSuccess()) {
            player.sendMessage(BankManager.getPrefix() + msg("invalid-amount", "&cInvalid amount!"));
            BankerSound.playError(player);
            BankSettingsMenu.open(player);
            return;
        }

        SkyBankerDatabase.setVaultOwned(uuid);
        OWNED.put(uuid, true);

        BankerSound.playUpgrade(player);
        player.sendMessage(BankManager.getPrefix() + msg("vault-purchased",
                "&aYou unlocked your &5Personal Vault &afor &6$%price%&a!")
                .replace("%price%", BankManager.formatNum(price)));

        openVault(player);
    }

    // =====================================================================
    // The vault itself
    // =====================================================================

    public static void openVault(Player player) {
        UUID uuid = player.getUniqueId();
        if (!isEnabled() || !isOwned(uuid)) {
            BankSettingsMenu.open(player);
            return;
        }

        // Newest contents: memory first, then database (only on the first open)
        byte[] bytes = CACHE.get(uuid);
        if (bytes == null) {
            bytes = SkyBankerDatabase.getVaultContents(uuid);
            if (bytes == null) bytes = new byte[0];
            CACHE.put(uuid, bytes);
        }
        ItemStack[] stored = decode(bytes);

        boolean closeButton = closeButtonEnabled();

        // Never shrink below what is already stored, even if rows was lowered in config
        int storage = Math.max(getRows() * 9, (int) Math.ceil(stored.length / 9.0) * 9);
        storage = Math.min(closeButton ? 45 : 54, storage);

        // Plain chest by default. With close-button: true one extra bottom row is added.
        VaultHolder holder = new VaultHolder(uuid, storage);
        String title = BankManager.getName(VAULT_MENU + ".title", "Personal Vault");
        Inventory inv = Bukkit.createInventory(holder, closeButton ? storage + 9 : storage, title);
        holder.setInventory(inv);

        for (int i = 0; i < stored.length && i < storage; i++) {
            if (stored[i] != null) inv.setItem(i, stored[i]);
        }

        if (closeButton) {
            ItemStack filler = filler(VAULT_MENU + ".filler-material");
            for (int i = storage; i < storage + 9; i++) {
                inv.setItem(i, filler);
            }
            inv.setItem(closeSlot(storage), build(VAULT_MENU + ".items.close", ".lore", Material.BARRIER, new HashMap<>()));
        }

        player.openInventory(inv);
    }

    /** Absolute slot of the Close button (config: gui.vault-menu.close-position, 1-9 in the bottom row). */
    private static int closeSlot(int storage) {
        int pos = Math.max(1, Math.min(9, cfg().getInt(VAULT_MENU + ".close-position", 5)));
        return storage + pos - 1;
    }

    // =====================================================================
    // Click protection: the bottom control row can never be moved or filled
    // =====================================================================

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof VaultHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // No control row (close button off) -> normal vanilla chest behaviour
        if (top.getSize() <= holder.getStorageSize()) return;

        // Double-click "collect to cursor" could pull items out of the control row
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }

        int storage = holder.getStorageSize();
        int raw = event.getRawSlot();
        boolean inTop = raw >= 0 && raw < top.getSize();

        // Control row: filler + Close button
        if (inTop && raw >= storage) {
            event.setCancelled(true);
            if (raw == closeSlot(storage)) {
                BankerSound.playClick(player);
                Bukkit.getScheduler().runTask(SkyBanker.getInstance(), () -> player.closeInventory());
            }
            return;
        }

        // Shift-click from the player's inventory: only fill the real storage slots
        if (!inTop && raw >= 0 && event.isShiftClick()) {
            event.setCancelled(true);
            ItemStack moving = event.getCurrentItem();
            if (moving == null || moving.getType().isAir()) return;
            ItemStack left = addToStorage(top, storage, moving.clone());
            event.setCurrentItem(left.getAmount() <= 0 ? null : left);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof VaultHolder holder)) return;
        for (int raw : event.getRawSlots()) {
            if (raw < top.getSize() && raw >= holder.getStorageSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /** Adds an item to slots 0..storage-1 only. Returns what did not fit. */
    private static ItemStack addToStorage(Inventory inv, int storage, ItemStack item) {
        int max = item.getMaxStackSize();
        for (int i = 0; i < storage && item.getAmount() > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (s != null && !s.getType().isAir() && s.isSimilar(item) && s.getAmount() < max) {
                int move = Math.min(max - s.getAmount(), item.getAmount());
                s.setAmount(s.getAmount() + move);
                inv.setItem(i, s);
                item.setAmount(item.getAmount() - move);
            }
        }
        for (int i = 0; i < storage && item.getAmount() > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (s == null || s.getType().isAir()) {
                int move = Math.min(max, item.getAmount());
                ItemStack put = item.clone();
                put.setAmount(move);
                inv.setItem(i, put);
                item.setAmount(item.getAmount() - move);
            }
        }
        return item;
    }

    // =====================================================================
    // Saving
    // =====================================================================

    /** Takes the current contents of an open vault and queues them for saving. */
    private static void snapshot(UUID owner, Inventory inv) {
        int storage = inv.getHolder(false) instanceof VaultHolder h ? h.getStorageSize() : inv.getSize();
        ItemStack[] all = inv.getContents();
        CACHE.put(owner, encode(Arrays.copyOf(all, Math.min(storage, all.length))));
        DIRTY.add(owner);
        queueWrite(owner);
    }

    private static void queueWrite(UUID owner) {
        try {
            WRITER.execute(() -> writeNow(owner));
        } catch (RejectedExecutionException e) {
            writeNow(owner); // writer already shut down (plugin disabling)
        }
    }

    private static void writeNow(UUID owner) {
        if (!DIRTY.remove(owner)) return;       // an earlier task already saved the newest data
        byte[] bytes = CACHE.get(owner);
        if (bytes != null) {
            SkyBankerDatabase.setVaultContents(owner, bytes);
        }
    }

    /** Called from SkyBanker.onDisable BEFORE the database closes. */
    public static void shutdown() {
        // Players who still have the vault open when the server stops
        for (Player p : Bukkit.getOnlinePlayers()) {
            Inventory top = p.getOpenInventory().getTopInventory();
            if (top.getHolder(false) instanceof VaultHolder h) {
                snapshot(h.getOwner(), top);
            }
        }
        WRITER.shutdown();
        try {
            WRITER.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        for (UUID uuid : new ArrayList<>(DIRTY)) {
            writeNow(uuid);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Inventory inv = event.getInventory();
        if (inv.getHolder(false) instanceof VaultHolder holder) {
            snapshot(holder.getOwner(), inv);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        OWNED.remove(uuid);
        // Queued AFTER any pending save (FIFO), so the data is on disk before it leaves memory
        try {
            WRITER.execute(() -> {
                if (!DIRTY.contains(uuid)) CACHE.remove(uuid);
            });
        } catch (RejectedExecutionException ignored) {
        }
    }

    // =====================================================================
    // Item <-> bytes (own tiny format, so empty slots are always handled safely)
    // =====================================================================

    private static byte[] encode(ItemStack[] items) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(bos)) {
            out.writeInt(items.length);
            for (ItemStack item : items) {
                if (item == null || item.getType().isAir()) {
                    out.writeInt(0);
                } else {
                    byte[] b = item.serializeAsBytes();
                    out.writeInt(b.length);
                    out.write(b);
                }
            }
            out.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ItemStack[] decode(byte[] data) {
        if (data == null || data.length == 0) return new ItemStack[0];
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            int count = in.readInt();
            if (count < 0 || count > 54) return new ItemStack[0];
            ItemStack[] out = new ItemStack[count];
            for (int i = 0; i < count; i++) {
                int len = in.readInt();
                if (len <= 0) continue;
                byte[] b = new byte[len];
                in.readFully(b);
                try {
                    out[i] = ItemStack.deserializeBytes(b);
                } catch (Exception ex) {
                    SkyBanker.getInstance().getLogger().warning("[Vault] Could not load one item (slot " + i + "): " + ex.getMessage());
                }
            }
            return out;
        } catch (IOException e) {
            SkyBanker.getInstance().getLogger().warning("[Vault] Could not read vault data: " + e.getMessage());
            return new ItemStack[0];
        }
    }

    // =====================================================================
    // Item building
    // =====================================================================

    private static void put(Inventory gui, int slot, ItemStack item) {
        if (slot >= 0 && slot < gui.getSize()) gui.setItem(slot, item);
    }

    private static ItemStack filler(String path) {
        ItemStack item = new ItemStack(BankManager.getMaterial(path, Material.GRAY_STAINED_GLASS_PANE));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Builds an item: material/name from itemPath, lore from itemPath + lorePath. */
    private static ItemStack build(String itemPath, String lorePath, Material def, Map<String, String> ph) {
        ItemStack item = new ItemStack(BankManager.getMaterial(itemPath + ".material", def));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = cfg().getString(itemPath + ".name", "&7Item");
            for (Map.Entry<String, String> e : ph.entrySet()) {
                name = name.replace(e.getKey(), e.getValue());
            }
            meta.setDisplayName(BankManager.colorize(name));
            List<String> lore = BankManager.getLore(itemPath + lorePath, ph);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}