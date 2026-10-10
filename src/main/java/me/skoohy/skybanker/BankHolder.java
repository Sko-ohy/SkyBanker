package me.skoohy.skybanker;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Identifies which Bank GUI a given Inventory belongs to.
 * Used instead of matching inventory titles as strings, so menu
 * detection keeps working even if titles get translated/customized.
 *
 * IMPORTANT: after creating the Inventory with this holder, you must
 * call setInventory(inv) once, since Bukkit requires the holder to
 * exist before the inventory but InventoryHolder#getInventory() needs
 * to return that same inventory afterward.
 */
public class BankHolder implements InventoryHolder {

    private final MenuType type;
    private Inventory inventory;

    public BankHolder(MenuType type) {
        this.type = type;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public MenuType getMenuType() {
        return type;
    }

    @Override
    public @NotNull Inventory getInventory() {
        if (inventory == null) {
            throw new IllegalStateException("BankHolder inventory was never set via setInventory()");
        }
        return inventory;
    }

    public enum MenuType {
        MAIN, DEPOSIT, WITHDRAW, UPGRADES, SETTINGS
    }
}