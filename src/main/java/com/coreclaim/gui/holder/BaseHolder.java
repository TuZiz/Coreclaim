package com.coreclaim.gui.holder;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public abstract class BaseHolder implements InventoryHolder {
    public Inventory inventory;

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
