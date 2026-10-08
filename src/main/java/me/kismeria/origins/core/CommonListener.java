package me.kismeria.origins.core;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import me.kismeria.origins.OriginsPlugin;
import me.kismeria.smpcore.api.SmpResetEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public final class CommonListener implements Listener {
    private final OriginsPlugin plugin;

    public CommonListener(OriginsPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean everyJoin() {
        return plugin.getConfig().getBoolean("welcome.every-join", false);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        OriginManager m = plugin.manager();
        if (plugin.getConfig().getBoolean("migration.reset-skript-base-attributes", true)) Attrs.resetSkriptBases(p);
        m.load(p);
        Origin o = m.get(p);
        if (o == null) return;
        if (OriginManager.active(p)) {
            o.apply(p);
            m.restoreHealth(p);
        }
        RaceStore.Entry entry = m.store().get(p.getUniqueId());
        // Окно расы — только в игре, после входа: на загрузке его держит окно пароля SmpCore,
        // а два окна на загрузке вешают клиента. PlayerJoinEvent приходит уже после пароля.
        if (entry != null && (!entry.seen() || everyJoin())) {
            long delay = Math.max(1, plugin.getConfig().getInt("welcome.delay-seconds", 2)) * 20L;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) m.greet(p);
            }, delay);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        Origin o = plugin.manager().get(p);
        if (o != null) o.reset(p);
        plugin.manager().saveHealth(p);
        plugin.manager().unload(p);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Origin o = plugin.manager().get(e.getPlayer());
        if (o != null) o.reset(e.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Origin o = plugin.manager().get(p);
            if (o == null || !p.isOnline() || !OriginManager.active(p)) return;
            o.apply(p);
            p.setHealth(Attrs.maxHealth(p));
        });
    }

    @EventHandler
    public void onArmor(PlayerArmorChangeEvent e) {
        Player p = e.getPlayer();
        Origin o = plugin.manager().get(p);
        if (o == null || !OriginManager.active(p) || e.getNewItem().isEmpty() || !o.forbidsArmor(e.getNewItem().getType())) return;
        Bukkit.getScheduler().runTask(plugin, () -> plugin.manager().checkArmor(p, o));
    }

    /** Creative/spectator switches the race off (stats, stealth, rituals), survival switches it back on. */
    @EventHandler
    public void onGameMode(PlayerGameModeChangeEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Origin o = plugin.manager().get(p);
            if (o == null || !p.isOnline()) return;
            if (OriginManager.active(p)) {
                o.apply(p);
                plugin.manager().checkArmor(p, o);
            } else {
                o.reset(p);
                Attrs.clearAll(p, plugin);
            }
        });
    }

    /** "Сбросить SMP" in SmpCore. */
    @EventHandler
    public void onSmpReset(SmpResetEvent e) {
        if (!plugin.getConfig().getBoolean("reset-with-smp", true)) return;
        plugin.manager().resetAll();
        plugin.getLogger().info("SMP сброшен — расы выданы заново.");
    }
}
