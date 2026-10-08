package me.kismeria.origins.gui;

import me.kismeria.origins.OriginsPlugin;
import me.kismeria.origins.core.Ability;
import me.kismeria.origins.core.Lang;
import me.kismeria.origins.core.Origin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** /origin list: every race in the viewer's language; click opens its window. */
public final class RaceListMenu extends Menu {
    private static final int CLOSE_SLOT = 40;

    private final OriginsPlugin plugin;
    private final String locale;
    private final Map<Integer, Origin> bySlot = new HashMap<>();

    public RaceListMenu(OriginsPlugin plugin, Player viewer) {
        super(5, plugin.lang().get(Lang.localeOf(viewer), "list.title"));
        this.plugin = plugin;
        this.locale = Lang.localeOf(viewer);
        Lang lang = plugin.lang();
        fill(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, pane(Material.BLACK_STAINED_GLASS_PANE));
            inventory.setItem(36 + i, pane(Material.BLACK_STAINED_GLASS_PANE));
        }
        Origin own = plugin.manager().get(viewer);
        List<Origin> list = new ArrayList<>(plugin.manager().all());
        place(list.subList(0, Math.min(7, list.size())), 1, own);
        if (list.size() > 7) place(list.subList(7, Math.min(14, list.size())), 2, own);
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER, lang.get(locale, "list.close"), List.of()));
    }

    private void place(List<Origin> row, int rowIndex, Origin own) {
        List<Integer> slots = rowSlots(rowIndex, row.size());
        for (int i = 0; i < row.size(); i++) {
            Origin o = row.get(i);
            bySlot.put(slots.get(i), o);
            List<Component> lore = new ArrayList<>();
            lore.add(line("<#b0b0b0><i>" + o.description(locale)));
            lore.add(Component.empty());
            for (Ability a : o.abilities()) {
                lore.add(line((a.positive() ? "<#7CFC00>✚ " : "<#ff5555>✖ ") + "<white>" + o.abilityTitle(locale, a)));
            }
            lore.add(Component.empty());
            if (o == own) lore.add(plugin.lang().get(locale, "list.yours"));
            lore.add(plugin.lang().get(locale, "list.click"));
            var it = item(o.icon(), o.title(locale).colorIfAbsent(NamedTextColor.WHITE), lore);
            if (o == own) it.editMeta(m -> m.setEnchantmentGlintOverride(true));
            inventory.setItem(slots.get(i), it);
        }
    }

    @Override
    public void onClick(Player p, int slot, InventoryClickEvent e) {
        if (slot == CLOSE_SLOT) {
            p.closeInventory();
            return;
        }
        Origin o = bySlot.get(slot);
        if (o == null) return;
        p.closeInventory();
        p.showDialog(plugin.dialogs().info(locale, o, o == plugin.manager().get(p)));
    }
}
