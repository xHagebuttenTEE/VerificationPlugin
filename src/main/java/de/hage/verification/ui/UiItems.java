package de.hage.verification.ui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class UiItems {

    private UiItems() {}

    public static ItemStack filler() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        item.editMeta(meta -> {
            meta.displayName(Component.text(" "));
            meta.setHideTooltip(true);
        });
        return item;
    }

    public static void fillEmpty(Inventory inventory) {
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler());
            }
        }
    }
}