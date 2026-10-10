package me.skoohy.skybanker;

import io.papermc.paper.event.packet.UncheckedSignChangeEvent;
import io.papermc.paper.math.Position;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BankMenu implements Listener {

    private static final Map<UUID, Long> clickCooldowns = new HashMap<>();
    private static final Map<UUID, Long> spamCooldowns = new HashMap<>();

    private static final Map<UUID, Long> commandCooldowns = new HashMap<>();
    private static final long COMMAND_COOLDOWN_MS = 1000;

    // Players who currently have the sign editor open, and which mode
    // (deposit/withdraw) + fake sign position it belongs to.
    private record PendingSign(String mode, Location location) {}
    private static final Map<UUID, PendingSign> pendingSigns = new ConcurrentHashMap<>();

    public static boolean canUseBankCommand(Player player) {
        long now = System.currentTimeMillis();
        long last = commandCooldowns.getOrDefault(player.getUniqueId(), 0L);

        if (now - last < COMMAND_COOLDOWN_MS) {
            player.sendMessage(BankManager.getPrefix() + "§cYou're using that command too fast!");
            BankerSound.playError(player);
            return false;
        }

        commandCooldowns.put(player.getUniqueId(), now);
        return true;
    }

    private static boolean checkRateLimit(Player player) {
        long now = System.currentTimeMillis();
        long lastAction = spamCooldowns.getOrDefault(player.getUniqueId(), 0L);

        if (now - lastAction < 600) {
            player.sendMessage(BankManager.getPrefix() + "§cYou are clicking too fast! Please slow down.");
            BankerSound.playError(player);
            return false;
        }

        spamCooldowns.put(player.getUniqueId(), now);
        return true;
    }

    private static String formatCoin(double amount) {
        return BankManager.formatNum(amount);
    }

    private static ItemStack buildItem(String basePath, Map<String, String> placeholders) {
        Material mat = BankManager.getMaterial(basePath + ".material", Material.STONE);
        String name = BankManager.getName(basePath + ".name", "&7Item");
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            name = name.replace(e.getKey(), e.getValue());
        }
        List<String> lore = BankManager.getLore(basePath + ".lore", placeholders);

        // Optional "amount" field (e.g. showing a chest as x64 vs x32 to
        // visually represent "whole purse" vs "half purse"). Defaults to
        // 1 if not set, and is clamped to Minecraft's valid stack range.
        int stackAmount = plugin_getConfig().getInt(basePath + ".amount", 1);
        stackAmount = Math.max(1, Math.min(64, stackAmount));

        ItemStack item = new ItemStack(mat, stackAmount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack fillerItem() {
        Material mat = BankManager.getMaterial("gui.main-menu.filler-material", Material.GRAY_STAINED_GLASS_PANE);
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        return item;
    }

    private static Inventory createMenu(BankHolder.MenuType type, String configPath, int defaultSize) {
        BankHolder holder = new BankHolder(type);
        String title = BankManager.getName(configPath + ".title", type.name());

        int configSize = plugin_getConfig().getInt(configPath + ".size", defaultSize);
        int size = Math.min(54, Math.max(9, (int) Math.ceil(configSize / 9.0) * 9));

        Inventory inv = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inv);

        ItemStack filler = fillerItem();
        for (int i = 0; i < size; i++) inv.setItem(i, filler);

        return inv;
    }

    public static void openMainBank(Player player) {
        int size = plugin_getConfig().getInt("gui.main-menu.size", 36);
        Inventory gui = createMenu(BankHolder.MenuType.MAIN, "gui.main-menu", size);

        double bal = BankManager.getBankBalance(player);
        double limit = BankManager.getBankLimit(player);
        String account = BankManager.getAccountName(player);
        String interestTimer = BankManager.getInterestTimer(player);

        // Interest preview values for the Information item
        double lastInterest = BankManager.getLastInterestAmount(player);
        double projectedInterest = BankManager.getProjectedInterest(player);
        double projectedPercent = BankManager.getProjectedInterestPercent(player);

        Map<String, String> ph = new HashMap<>();
        ph.put("%balance%", formatCoin(bal));
        ph.put("%limit%", formatCoin(limit));
        ph.put("%account%", account);
        ph.put("%interest_timer%", interestTimer);
        ph.put("%last_interest%", formatCoin(lastInterest));
        ph.put("%projected_interest%", BankManager.formatDecimal(projectedInterest));
        ph.put("%projected_percent%", String.format("%.0f", projectedPercent));

        gui.setItem(BankManager.getSlot("gui.main-menu.items.deposit.slot", 11), buildItem("gui.main-menu.items.deposit", ph));
        gui.setItem(BankManager.getSlot("gui.main-menu.items.withdraw.slot", 13), buildItem("gui.main-menu.items.withdraw", ph));
        gui.setItem(BankManager.getSlot("gui.main-menu.items.go-back.slot", 30), buildItem("gui.main-menu.items.go-back", ph));
        gui.setItem(BankManager.getSlot("gui.main-menu.items.close.slot", 31), buildItem("gui.main-menu.items.close", ph));
        gui.setItem(BankManager.getSlot("gui.main-menu.items.info.slot", 32), buildItem("gui.main-menu.items.info", ph));
        gui.setItem(BankManager.getSlot("gui.main-menu.items.upgrades.slot", 35), buildItem("gui.main-menu.items.upgrades", ph));

        // Settings button (bottom-left). Only shown when the admin has the
        // settings feature enabled in config.yml (gui.settings-menu.enabled).
        if (BankSettingsMenu.isEnabled()) {
            gui.setItem(BankManager.getSlot("gui.main-menu.items.settings.slot", 27), buildItem("gui.main-menu.items.settings", ph));
        }

        ItemStack txItem = buildItem("gui.main-menu.items.transactions", ph);
        ItemMeta txMeta = txItem.getItemMeta();
        if (txMeta != null) {
            txMeta.setLore(BankManager.getTransactionLore(player));
            txItem.setItemMeta(txMeta);
        }
        gui.setItem(BankManager.getSlot("gui.main-menu.items.transactions.slot", 15), txItem);

        player.openInventory(gui);
    }

    public static void openDepositMenu(Player player) {
        int size = plugin_getConfig().getInt("gui.deposit-menu.size", 36);
        Inventory gui = createMenu(BankHolder.MenuType.DEPOSIT, "gui.deposit-menu", size);

        double purse = SkyBanker.getEconomy().getBalance(player);
        double bankBalance = BankManager.getBankBalance(player);
        double bankLimit = BankManager.getBankLimit(player);
        double availableSpace = Math.max(0, bankLimit - bankBalance);

        double wholeDepositAmount = Math.min(purse, availableSpace);
        double halfDepositAmount = Math.min(purse / 2, availableSpace);

        Map<String, String> wholePh = new HashMap<>();
        wholePh.put("%balance%", formatCoin(bankBalance));
        wholePh.put("%amount%", formatCoin(wholeDepositAmount));
        gui.setItem(BankManager.getSlot("gui.deposit-menu.items.whole-purse.slot", 11), buildItem("gui.deposit-menu.items.whole-purse", wholePh));

        Map<String, String> halfPh = new HashMap<>();
        halfPh.put("%balance%", formatCoin(bankBalance));
        halfPh.put("%amount%", formatCoin(halfDepositAmount));
        gui.setItem(BankManager.getSlot("gui.deposit-menu.items.half-purse.slot", 13), buildItem("gui.deposit-menu.items.half-purse", halfPh));

        Map<String, String> specPh = new HashMap<>();
        specPh.put("%balance%", formatCoin(bankBalance));
        gui.setItem(BankManager.getSlot("gui.deposit-menu.items.specific-amount.slot", 15), buildItem("gui.deposit-menu.items.specific-amount", specPh));

        gui.setItem(BankManager.getSlot("gui.deposit-menu.items.go-back.slot", 31), buildItem("gui.deposit-menu.items.go-back", specPh));

        player.openInventory(gui);
    }

    public static void openWithdrawMenu(Player player) {
        int size = plugin_getConfig().getInt("gui.withdraw-menu.size", 36);
        Inventory gui = createMenu(BankHolder.MenuType.WITHDRAW, "gui.withdraw-menu", size);

        double bank = BankManager.getBankBalance(player);
        double percentValue = BankManager.getWithdrawPercent();

        Map<String, String> allPh = new HashMap<>();
        allPh.put("%balance%", formatCoin(bank));
        allPh.put("%amount%", formatCoin(bank));
        gui.setItem(BankManager.getSlot("gui.withdraw-menu.items.everything.slot", 10), buildItem("gui.withdraw-menu.items.everything", allPh));

        Map<String, String> halfPh = new HashMap<>();
        halfPh.put("%balance%", formatCoin(bank));
        halfPh.put("%amount%", formatCoin(bank / 2));
        gui.setItem(BankManager.getSlot("gui.withdraw-menu.items.half.slot", 12), buildItem("gui.withdraw-menu.items.half", halfPh));

        Map<String, String> percentPh = new HashMap<>();
        percentPh.put("%balance%", formatCoin(bank));
        percentPh.put("%amount%", formatCoin(bank * percentValue));
        gui.setItem(BankManager.getSlot("gui.withdraw-menu.items.percent.slot", 14), buildItem("gui.withdraw-menu.items.percent", percentPh));

        Map<String, String> specPh = new HashMap<>();
        specPh.put("%balance%", formatCoin(bank));
        gui.setItem(BankManager.getSlot("gui.withdraw-menu.items.specific-amount.slot", 16), buildItem("gui.withdraw-menu.items.specific-amount", specPh));

        gui.setItem(BankManager.getSlot("gui.withdraw-menu.items.go-back.slot", 31), buildItem("gui.withdraw-menu.items.go-back", specPh));

        player.openInventory(gui);
    }

    public static void openUpgradesMenu(Player player) {
        int size = plugin_getConfig().getInt("gui.upgrades-menu.size", 36);
        Inventory gui = createMenu(BankHolder.MenuType.UPGRADES, "gui.upgrades-menu", size);

        for (int level = 1; level <= 7; level++) {
            int slot = level + 9;
            Material mat = BankManager.getTierMaterial(level);
            String name = BankManager.getTierName(level);
            List<String> lore = BankManager.getTierLore(player, level);

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(name);
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            gui.setItem(slot, item);
        }

        int goBackSlot = plugin_getConfig().getInt("gui.upgrades-menu.go-back-slot", 31);
        Map<String, String> ph = new HashMap<>();
        gui.setItem(goBackSlot, buildItem("gui.main-menu.items.go-back", ph));

        player.openInventory(gui);
    }

    // =====================================================================
    // Sign input - uses Paper's built-in virtual sign API (no NMS, no
    // external library), so it keeps working across Minecraft updates.
    // =====================================================================

    private static void openSignGUI(Player player, String mode) {
        player.closeInventory();

        List<String> lines = plugin_getConfig().getStringList("gui.sign-input." + mode);
        final String[] text = {
                lines.size() > 0 ? BankManager.colorize(lines.get(0)) : "",
                lines.size() > 1 ? BankManager.colorize(lines.get(1)) : "     ^     ",
                lines.size() > 2 ? BankManager.colorize(lines.get(2)) : "Enter amount",
                lines.size() > 3 ? BankManager.colorize(lines.get(3)) : (mode.equals("deposit") ? "to deposit" : "to withdraw")
        };

        // Fake sign position: a few blocks above the player. It has to be
        // close to them, newer versions reject edits of far-away signs.
        Location base = player.getLocation().getBlock().getLocation();
        Location target = base.clone().add(0, 3, 0);
        if (target.getY() >= player.getWorld().getMaxHeight() - 1) {
            target = base.clone().add(0, -3, 0);
        }
        final Location signLoc = target;

        // Open the sign in the same tick as closing the menu, so the player
        // doesn't see the game screen flash between the two screens.
        if (!player.isOnline()) return;
        {
            try {
                BlockData data = Material.OAK_SIGN.createBlockData();
                player.sendBlockChange(signLoc, data); // fake sign, only this player sees it

                Sign sign = (Sign) data.createBlockState();
                for (int i = 0; i < 4; i++) {
                    sign.getSide(Side.FRONT).line(i,
                            LegacyComponentSerializer.legacySection().deserialize(text[i]));
                }
                player.sendBlockUpdate(signLoc, sign); // put the text on the fake sign

                pendingSigns.put(player.getUniqueId(), new PendingSign(mode, signLoc));
                player.openVirtualSign(
                        Position.block(signLoc.getBlockX(), signLoc.getBlockY(), signLoc.getBlockZ()),
                        Side.FRONT);
            } catch (Throwable t) {
                pendingSigns.remove(player.getUniqueId());
                restoreBlock(player, signLoc);
                SkyBanker.getInstance().getLogger().warning("[SkyBanker] Failed to open sign editor: " + t);
                player.sendMessage(BankManager.getPrefix() + "§cFailed to open sign editor!");
                openMainBank(player);
            }
        }
    }

    /** Removes the fake sign by re-sending the real block to the player. */
    private static void restoreBlock(Player player, Location loc) {
        player.sendBlockChange(loc, loc.getBlock().getBlockData());
    }

    /** Runs the deposit/withdraw for whatever the player typed on the sign. */
    private static void handleAmountInput(Player p, String mode, String input) {
        if (!p.isOnline()) return;

        if (input.isEmpty()) {
            p.sendMessage(BankManager.getPrefix() + BankManager.getMessage("no-amount-entered"));
            BankerSound.playError(p);
            openMainBank(p);
            return;
        }

        double amountValue = BankManager.parseAmount(input);

        if (amountValue <= 0) {
            p.sendMessage(BankManager.getPrefix() + BankManager.getMessage("invalid-amount"));
            BankerSound.playError(p);
            openMainBank(p);
            return;
        }

        boolean success = mode.equals("deposit")
                ? processDeposit(p, amountValue)
                : processWithdraw(p, amountValue);

        if (!success) {
            openMainBank(p);
        }
    }

    @EventHandler
    public void onSignInput(UncheckedSignChangeEvent event) {
        Player p = event.getPlayer();
        PendingSign pending = pendingSigns.get(p.getUniqueId());
        if (pending == null) return; // not our sign

        // Make sure it's our fake sign position
        var pos = event.getEditedBlockPosition();
        Location l = pending.location();
        if (pos.blockX() != l.getBlockX() || pos.blockY() != l.getBlockY() || pos.blockZ() != l.getBlockZ()) return;

        pendingSigns.remove(p.getUniqueId());
        event.setCancelled(true);
        restoreBlock(p, l); // remove the fake sign

        String input = event.lines().isEmpty()
                ? ""
                : PlainTextComponentSerializer.plainText().serialize(event.lines().get(0)).trim();

        // Back on the main thread for Vault + database access
        Bukkit.getScheduler().runTask(SkyBanker.getInstance(),
                () -> handleAmountInput(p, pending.mode(), input));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingSigns.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory topInventory = event.getView().getTopInventory();
        InventoryHolder rawHolder = topInventory.getHolder();
        if (!(rawHolder instanceof BankHolder holder)) return;

        if (event.getClickedInventory() == null || event.getClickedInventory() != topInventory) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        Material filler = BankManager.getMaterial("gui.main-menu.filler-material", Material.GRAY_STAINED_GLASS_PANE);

        if (clicked == null || clicked.getType() == Material.AIR) return;
        if (clicked.getType() == filler) return;

        long lastClick = clickCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (System.currentTimeMillis() - lastClick < 400) return;
        clickCooldowns.put(player.getUniqueId(), System.currentTimeMillis());

        if (!checkRateLimit(player)) return;

        int slot = event.getSlot();

        switch (holder.getMenuType()) {
            case MAIN -> handleMainMenuClick(player, slot);
            case DEPOSIT -> handleDepositMenuClick(player, slot);
            case WITHDRAW -> handleWithdrawMenuClick(player, slot);
            case UPGRADES -> handleUpgradesMenuClick(player, slot);
            case SETTINGS -> BankSettingsMenu.handleClick(player, slot);
        }
    }

    private void handleMainMenuClick(Player player, int slot) {
        if (slot == BankManager.getSlot("gui.main-menu.items.deposit.slot", 11)) {
            BankerSound.playClick(player);
            openDepositMenu(player);
        } else if (slot == BankManager.getSlot("gui.main-menu.items.withdraw.slot", 13)) {
            BankerSound.playClick(player);
            openWithdrawMenu(player);
        } else if (slot == BankManager.getSlot("gui.main-menu.items.go-back.slot", 30)
                || slot == BankManager.getSlot("gui.main-menu.items.close.slot", 31)) {
            BankerSound.playClick(player);
            player.closeInventory();
        } else if (slot == BankManager.getSlot("gui.main-menu.items.upgrades.slot", 35)) {
            BankerSound.playClick(player);
            openUpgradesMenu(player);
        } else if (BankSettingsMenu.isEnabled()
                && slot == BankManager.getSlot("gui.main-menu.items.settings.slot", 27)) {
            BankerSound.playClick(player);
            BankSettingsMenu.open(player);
        }
    }

    private void handleDepositMenuClick(Player player, int slot) {
        double purse = SkyBanker.getEconomy().getBalance(player);
        double bankBalance = BankManager.getBankBalance(player);
        double limit = BankManager.getBankLimit(player);
        double availableSpace = Math.max(0, limit - bankBalance);

        if (slot == BankManager.getSlot("gui.deposit-menu.items.whole-purse.slot", 11)) {
            double amount = Math.min(purse, availableSpace);
            if (processDeposit(player, amount)) {
                player.closeInventory();
            } else {
                openMainBank(player);
            }
        } else if (slot == BankManager.getSlot("gui.deposit-menu.items.half-purse.slot", 13)) {
            double amount = Math.min(purse / 2, availableSpace);
            if (processDeposit(player, amount)) {
                player.closeInventory();
            } else {
                openMainBank(player);
            }
        } else if (slot == BankManager.getSlot("gui.deposit-menu.items.specific-amount.slot", 15)) {
            BankerSound.playClick(player);
            openSignGUI(player, "deposit");
        } else if (slot == BankManager.getSlot("gui.deposit-menu.items.go-back.slot", 31)) {
            BankerSound.playClick(player);
            openMainBank(player);
        }
    }

    private void handleWithdrawMenuClick(Player player, int slot) {
        double bank = BankManager.getBankBalance(player);
        double percentValue = BankManager.getWithdrawPercent();

        if (slot == BankManager.getSlot("gui.withdraw-menu.items.everything.slot", 10)) {
            if (processWithdraw(player, bank)) {
                player.closeInventory();
            } else {
                openMainBank(player);
            }
        } else if (slot == BankManager.getSlot("gui.withdraw-menu.items.half.slot", 12)) {
            if (processWithdraw(player, bank / 2)) {
                player.closeInventory();
            } else {
                openMainBank(player);
            }
        } else if (slot == BankManager.getSlot("gui.withdraw-menu.items.percent.slot", 14)) {
            if (processWithdraw(player, bank * percentValue)) {
                player.closeInventory();
            } else {
                openMainBank(player);
            }
        } else if (slot == BankManager.getSlot("gui.withdraw-menu.items.specific-amount.slot", 16)) {
            BankerSound.playClick(player);
            openSignGUI(player, "withdraw");
        } else if (slot == BankManager.getSlot("gui.withdraw-menu.items.go-back.slot", 31)) {
            BankerSound.playClick(player);
            openMainBank(player);
        }
    }

    private void handleUpgradesMenuClick(Player player, int slot) {
        int goBackSlot = plugin_getConfig().getInt("gui.upgrades-menu.go-back-slot", 31);
        if (slot == goBackSlot) {
            BankerSound.playClick(player);
            openMainBank(player);
            return;
        }

        int targetLevel = slot - 9;
        if (targetLevel >= 1 && targetLevel <= 7) {
            handleUpgrade(player, targetLevel);
        }
    }

    private static boolean processDeposit(Player player, double amount) {
        amount = Math.floor(amount);
        double purse = SkyBanker.getEconomy().getBalance(player);
        double limit = BankManager.getBankLimit(player);
        double currentBank = BankManager.getBankBalance(player);
        double space = Math.max(0, limit - currentBank);

        if (amount <= 0) {
            player.sendMessage(BankManager.getPrefix() + BankManager.getMessage("invalid-amount"));
            BankerSound.playError(player);
            return false;
        }

        if (amount > purse) {
            String msg = BankManager.getPrefix() + BankManager.getMessage("not-enough-coins")
                    .replace("%amount%", formatCoin(purse));
            player.sendMessage(msg);
            BankerSound.playError(player);
            return false;
        }

        if (amount > space) {
            amount = space;
        }

        if (amount <= 0) {
            String msg = BankManager.getPrefix() + BankManager.getMessage("bank-full")
                    .replace("%amount%", formatCoin(limit));
            player.sendMessage(msg);
            BankerSound.playError(player);
            return false;
        }

        SkyBanker.getEconomy().withdrawPlayer(player, amount);
        BankManager.setBankBalance(player, currentBank + amount);

        BankManager.addTransaction(player, true, amount);
        BankerSound.playDeposit(player);

        String msg = BankManager.getPrefix() + BankManager.getMessage("deposit-success")
                .replace("%amount%", formatCoin(amount))
                .replace("%balance%", formatCoin(BankManager.getBankBalance(player)));
        player.sendMessage(msg);
        return true;
    }

    private static boolean processWithdraw(Player player, double amount) {
        amount = Math.floor(amount);
        double bank = BankManager.getBankBalance(player);

        if (amount > bank) {
            String msg = BankManager.getPrefix() + BankManager.getMessage("not-enough-coins")
                    .replace("%amount%", formatCoin(bank));
            player.sendMessage(msg);
            BankerSound.playError(player);
            return false;
        }

        if (amount <= 0) {
            player.sendMessage(BankManager.getPrefix() + BankManager.getMessage("invalid-amount"));
            BankerSound.playError(player);
            return false;
        }

        BankManager.setBankBalance(player, bank - amount);
        SkyBanker.getEconomy().depositPlayer(player, amount);

        BankManager.addTransaction(player, false, amount);
        BankerSound.playWithdraw(player);

        String msg = BankManager.getPrefix() + BankManager.getMessage("withdraw-success")
                .replace("%amount%", formatCoin(amount))
                .replace("%balance%", formatCoin(BankManager.getBankBalance(player)));
        player.sendMessage(msg);
        return true;
    }

    private static void handleUpgrade(Player player, int targetLvl) {
        int currentLvl = BankManager.getBankLevel(player);
        if (targetLvl <= currentLvl) {
            player.sendMessage(BankManager.getPrefix() + BankManager.getMessage("already-unlocked"));
            BankerSound.playError(player);
            return;
        }
        if (targetLvl != currentLvl + 1) {
            player.sendMessage(BankManager.getPrefix() + BankManager.getMessage("need-previous-upgrade"));
            BankerSound.playError(player);
            return;
        }

        double cost = BankManager.getTierCost(targetLvl);
        double playerBalance = SkyBanker.getEconomy().getBalance(player);

        if (playerBalance < cost) {
            String msg = BankManager.getPrefix() + BankManager.getMessage("not-enough-coins")
                    .replace("%amount%", formatCoin(playerBalance));
            player.sendMessage(msg);
            BankerSound.playError(player);
            return;
        }

        SkyBanker.getEconomy().withdrawPlayer(player, cost);
        BankManager.setBankLevel(player, targetLvl);

        BankerSound.playUpgrade(player);

        // No prefix here on purpose - upgrade-success is a multi-line
        // bordered banner (like /skybanker top), and a prefix would only
        // ever land on line 1 since it has no line-break of its own,
        // misaligning the border/text underneath it.
        String msg = BankManager.getMessage("upgrade-success")
                .replace("%account%", BankManager.getAccountName(player))
                .replace("%limit%", formatCoin(BankManager.getBankLimit(player)));

        for (String line : msg.split("\n")) {
            player.sendMessage(line);
        }

        openUpgradesMenu(player);
    }

    private static org.bukkit.configuration.file.FileConfiguration plugin_getConfig() {
        return SkyBanker.getInstance().getConfig();
    }
}