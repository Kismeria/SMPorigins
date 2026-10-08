package me.kismeria.origins.gui;

import me.kismeria.origins.core.Lang;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Player-facing chest menu (admin menus use SmpCore's Menu instead). */
public abstract class Menu implements InventoryHolder {
    protected final Inventory inventory;

    protected Menu(int rows, Component title) {
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open(Player p) {
        p.openInventory(inventory);
    }

    public abstract void onClick(Player p, int slot, InventoryClickEvent e);

    public static ItemStack item(Material m, Component name, List<Component> lore) {
        ItemStack it = new ItemStack(m);
        it.editMeta(meta -> {
            meta.displayName(name);
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.values());
        });
        return it;
    }

    public static ItemStack pane(Material m) {
        return item(m, Component.text(" "), List.of());
    }

    protected void fill(Material m) {
        ItemStack p = pane(m);
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, p);
    }

    /** Symmetric slots for k items in one row: odd counts contiguous, even counts skip the centre. */
    public static List<Integer> rowSlots(int row, int k) {
        List<Integer> out = new ArrayList<>();
        if (k % 2 == 1) {
            for (int c = 4 - k / 2; c < 4 - k / 2 + k; c++) out.add(row * 9 + c);
        } else {
            for (int c = 4 - k / 2; c < 4; c++) out.add(row * 9 + c);
            for (int c = 5; c <= 4 + k / 2; c++) out.add(row * 9 + c);
        }
        return out;
    }

    static Component line(String mini) {
        return Lang.mm(mini);
    }
}
