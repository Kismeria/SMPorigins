package me.kismeria.origins;

import me.kismeria.origins.command.OriginCommand;
import me.kismeria.origins.core.Attrs;
import me.kismeria.origins.core.CommonListener;
import me.kismeria.origins.core.Dialogs;
import me.kismeria.origins.core.EquipmentMask;
import me.kismeria.origins.core.Lang;
import me.kismeria.origins.core.Origin;
import me.kismeria.origins.core.OriginManager;
import me.kismeria.origins.core.RaceStore;
import me.kismeria.origins.gui.MenuListener;
import me.kismeria.origins.gui.admin.OriginsAdminMenu;
import me.kismeria.origins.origin.Alchemist;
import me.kismeria.origins.origin.Blacksmith;
import me.kismeria.origins.origin.Builder;
import me.kismeria.origins.origin.Cook;
import me.kismeria.origins.origin.Farmer;
import me.kismeria.origins.origin.Fisherman;
import me.kismeria.origins.origin.Hunter;
import me.kismeria.origins.origin.Miner;
import me.kismeria.origins.origin.Rogue;
import me.kismeria.origins.origin.RuneAltars;
import me.kismeria.origins.origin.Runesmith;
import me.kismeria.origins.origin.Survivor;
import me.kismeria.smpcore.SmpCore;
import me.kismeria.smpcore.api.HudProvider;
import me.kismeria.smpcore.api.SmpAddon;
import net.kyori.adventure.text.Component;
import me.kismeria.smpcore.util.Icon;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class OriginsPlugin extends JavaPlugin {
    private Lang lang;
    private RaceStore store;
    private OriginManager manager;
    private Dialogs dialogs;
    private RuneAltars altars;
    private EquipmentMask equipmentMask;
    /** Every origin, enabled or not (admin menu). */
    private final List<Origin> allOrigins = new ArrayList<>();
    private final List<Origin> origins = new ArrayList<>();
    private int tick;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        lang = new Lang(this);
        lang.load();
        store = new RaceStore(this);
        store.load();
        manager = new OriginManager(this, store);
        dialogs = new Dialogs(this);
        equipmentMask = new EquipmentMask(this);
        equipmentMask.enable(Bukkit.getOnlinePlayers());
        altars = new RuneAltars(this);
        getServer().getPluginManager().registerEvents(altars, this);
        getServer().getPluginManager().registerEvents(new CommonListener(this), this);
        getServer().getPluginManager().registerEvents(new MenuListener(), this);

        loadOrigins();

        OriginCommand cmd = new OriginCommand(this);
        PluginCommand pc = getCommand("origin");
        if (pc != null) {
            pc.setExecutor(cmd);
            pc.setTabCompleter(cmd);
        }

        getServer().getServicesManager().register(SmpAddon.class, new SmpAddon() {
            @Override
            public ItemStack icon(Player viewer) {
                return Icon.of(Material.ENCHANTED_BOOK).name("<#a970ff><bold>Расы")
                        .lore("Включение рас, их числа,", "игроки, сброс рас, алтарь.", "",
                                "Рас: <white>" + origins.size() + "/" + allOrigins.size() + "</white>  Игроков с расой: <white>" + store.size())
                        .build();
            }

            @Override
            public void open(Player viewer) {
                new OriginsAdminMenu(OriginsPlugin.this, viewer).open();
            }
        }, this, ServicePriority.Normal);

        // race meters above the hunger bar, hints above the hearts (SmpCore action bar HUD)
        core().hud().register(this, new HudProvider() {
            @Override
            public Component bar(Player p) {
                Origin o = manager.get(p);
                return o == null || !OriginManager.active(p) ? null : o.hudBar(p);
            }

            @Override
            public Component hint(Player p) {
                Origin o = manager.get(p);
                return o == null || !OriginManager.active(p) ? null : o.hudHint(p);
            }
        });

        Bukkit.getScheduler().runTaskTimer(this, this::tickAll, 10L, 10L);
        getLogger().info("Рас включено: " + origins.size());
    }

    @Override
    public void onDisable() {
        for (Origin o : origins) o.shutdown();
        if (altars != null) altars.shutdown();
        if (equipmentMask != null) equipmentMask.disable(Bukkit.getOnlinePlayers());
        for (Player p : Bukkit.getOnlinePlayers()) {
            manager.saveHealth(p);
            Attrs.clearAll(p, this);
        }
        if (store != null) store.save();
    }

    /** (Re)builds all origins from config and languages. Used on enable, /origin reload and the admin menu. */
    public void loadOrigins() {
        reloadConfig();
        lang.load();
        for (Origin o : origins) {
            o.shutdown();
            HandlerList.unregisterAll(o);
        }
        origins.clear();
        allOrigins.clear();
        altars.reload();

        allOrigins.addAll(List.of(
                new Miner(this), new Survivor(this), new Farmer(this), new Blacksmith(this),
                new Fisherman(this), new Hunter(this), new Builder(this), new Cook(this),
                new Alchemist(this), new Rogue(this), new Runesmith(this)));
        for (Origin o : allOrigins) {
            if (!o.enabled()) continue;
            origins.add(o);
            getServer().getPluginManager().registerEvents(o, this);
        }
        getConfig().options().copyDefaults(true);
        saveConfig();

        manager.setOrigins(origins);
        for (Player p : Bukkit.getOnlinePlayers()) {
            Attrs.clearAll(p, this);
            manager.load(p);
            Origin o = manager.get(p);
            if (o == null) continue;
            if (OriginManager.active(p)) o.apply(p);
            // race was disabled -> a new random one was given: show it
            var entry = store.get(p.getUniqueId());
            if (entry != null && !entry.seen()) manager.greet(p);
        }
    }

    private void tickAll() {
        tick++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            Origin o = manager.get(p);
            if (o == null || p.isDead() || !OriginManager.active(p)) continue;
            if (tick % 2 == 0) {
                o.apply(p);
                manager.checkArmor(p, o);
            }
            o.tick(p, tick);
        }
    }

    public SmpCore core() {
        return JavaPlugin.getPlugin(SmpCore.class);
    }

    public Lang lang() {
        return lang;
    }

    public OriginManager manager() {
        return manager;
    }

    public Dialogs dialogs() {
        return dialogs;
    }

    public EquipmentMask equipmentMask() {
        return equipmentMask;
    }

    public RuneAltars altars() {
        return altars;
    }

    public List<Origin> origins() {
        return origins;
    }

    public List<Origin> allOrigins() {
        return allOrigins;
    }

    public Origin anyOrigin(String id) {
        for (Origin o : allOrigins) if (o.id().equalsIgnoreCase(id)) return o;
        return null;
    }
}
