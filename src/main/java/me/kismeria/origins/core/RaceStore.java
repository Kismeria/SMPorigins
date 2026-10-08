package me.kismeria.origins.core;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * players.yml: uuid -> origin, name, seen (welcome window shown).
 * A file instead of player data: races are assigned while the player is still loading
 * (configuration phase) and an SMP reset must also clear offline players.
 */
public final class RaceStore {
    public record Entry(String origin, String name, boolean seen) {
    }

    private final Plugin plugin;
    private final File file;
    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();

    public RaceStore(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
    }

    public void load() {
        entries.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            try {
                entries.put(UUID.fromString(key), new Entry(yaml.getString(key + ".origin"), yaml.getString(key + ".name", "?"),
                        yaml.getBoolean(key + ".seen")));
            } catch (IllegalArgumentException ignored) {
                // not a uuid
            }
        }
    }

    public Entry get(UUID id) {
        return entries.get(id);
    }

    public void put(UUID id, String origin, String name, boolean seen) {
        entries.put(id, new Entry(origin, name, seen));
        saveLater();
    }

    public void markSeen(UUID id) {
        Entry e = entries.get(id);
        if (e != null && !e.seen()) {
            entries.put(id, new Entry(e.origin(), e.name(), true));
            saveLater();
        }
    }

    public void remove(UUID id) {
        if (entries.remove(id) != null) saveLater();
    }

    public void clear() {
        entries.clear();
        saveLater();
    }

    public Map<String, Integer> counts() {
        Map<String, Integer> out = new HashMap<>();
        for (Entry e : entries.values()) if (e.origin() != null) out.merge(e.origin(), 1, Integer::sum);
        return out;
    }

    public int size() {
        return entries.size();
    }

    private void saveLater() {
        if (plugin.isEnabled()) Bukkit.getScheduler().runTaskAsynchronously(plugin, this::save);
        else save();
    }

    public synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Entry> e : Map.copyOf(entries).entrySet()) {
            String k = e.getKey().toString();
            yaml.set(k + ".origin", e.getValue().origin());
            yaml.set(k + ".name", e.getValue().name());
            yaml.set(k + ".seen", e.getValue().seen());
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Не удалось сохранить players.yml", ex);
        }
    }
}
