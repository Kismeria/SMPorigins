package me.kismeria.origins.core;

import me.kismeria.origins.OriginsPlugin;
import me.kismeria.smpcore.util.Bedrock;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/** Origin of each player: stored in players.yml, cached while online. */
public final class OriginManager {
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private final OriginsPlugin plugin;
    private final RaceStore store;
    private final NamespacedKey keyHealth;
    /** Enabled origins; replaced as a whole on reload (read from the async configuration thread). */
    private volatile Map<String, Origin> origins = Map.of();
    private final Map<UUID, Origin> cache = new ConcurrentHashMap<>();

    public OriginManager(OriginsPlugin plugin, RaceStore store) {
        this.plugin = plugin;
        this.store = store;
        this.keyHealth = new NamespacedKey(plugin, "health");
    }

    public void setOrigins(List<Origin> list) {
        Map<String, Origin> m = new java.util.LinkedHashMap<>();
        for (Origin o : list) m.put(o.id(), o);
        origins = m;
    }

    public Collection<Origin> all() {
        return origins.values();
    }

    public Origin byId(String id) {
        return id == null ? null : origins.get(id.toLowerCase());
    }

    public RaceStore store() {
        return store;
    }

    public Origin get(Player p) {
        return cache.get(p.getUniqueId());
    }

    /** Races work only in survival and adventure: admins in creative/spectator are left alone. */
    public static boolean active(Player p) {
        GameMode m = p.getGameMode();
        return m == GameMode.SURVIVAL || m == GameMode.ADVENTURE;
    }

    public boolean is(Player p, Origin o) {
        return cache.get(p.getUniqueId()) == o;
    }

    public Origin random() {
        List<Origin> list = new ArrayList<>(origins.values());
        return list.isEmpty() ? null : list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    /** Thread-safe: may run on the configuration thread before the Player exists. */
    public Origin ensureAssigned(UUID id, String name) {
        RaceStore.Entry e = store.get(id);
        Origin o = e == null ? null : byId(e.origin());
        if (o != null) return o;
        o = random();
        if (o != null) store.put(id, o.id(), name, false);
        return o;
    }

    public void load(Player p) {
        Origin o = ensureAssigned(p.getUniqueId(), p.getName());
        if (o != null) cache.put(p.getUniqueId(), o);
        else cache.remove(p.getUniqueId());
    }

    public void unload(Player p) {
        cache.remove(p.getUniqueId());
    }

    /** Assign at once: old stats removed, new ones applied. seen=false shows the welcome window next time. */
    public void set(Player p, Origin o, boolean seen) {
        Origin old = get(p);
        if (old != null) old.reset(p);
        Attrs.clearAll(p, plugin);
        if (o == null) {
            store.remove(p.getUniqueId());
            cache.remove(p.getUniqueId());
            return;
        }
        store.put(p.getUniqueId(), o.id(), p.getName(), seen);
        cache.put(p.getUniqueId(), o);
        if (!active(p)) return;
        o.apply(p);
        checkArmor(p, o);
    }

    /** New random race and the welcome window, for a player in game. */
    public void reroll(Player p) {
        Origin o = random();
        if (o == null) return;
        set(p, o, false);
        greet(p);
    }

    /** Everyone loses their race; online players get a new one at once, offline ones on login. */
    public void resetAll() {
        store.clear();
        for (Player p : Bukkit.getOnlinePlayers()) reroll(p);
    }

    /** Welcome window in game (Java) or chat + title (Bedrock). Marks the race as seen. */
    public void greet(Player p) {
        Origin o = get(p);
        if (o == null) return;
        String locale = Lang.localeOf(p);
        if (Bedrock.is(p.getUniqueId())) {
            Lang lang = plugin.lang();
            lang.send(p, "welcome.chat", "race", o.coloredName(locale));
            for (Ability a : o.abilities()) {
                p.sendMessage(Lang.mm((a.positive() ? "<#7CFC00>+ " : "<#ff5555>- ") + "<white>" + o.abilityTitle(locale, a) + "<gray>: ")
                        .append(o.abilityText(locale, a)));
            }
            p.showTitle(Title.title(o.title(locale), lang.get(locale, "welcome.subtitle"),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofSeconds(1))));
        } else {
            p.showDialog(plugin.dialogs().welcome(locale, o, false));
        }
        Fx.sound(p, "minecraft:block.note_block.bell", 1f, 1f);
        Fx.sound(p, "minecraft:ui.toast.challenge_complete", 0.6f, 1.2f);
        store.markSeen(p.getUniqueId());
    }

    public void saveHealth(Player p) {
        p.getPersistentDataContainer().set(keyHealth, PersistentDataType.DOUBLE, p.getHealth());
    }

    /** Vanilla clamps health to 20 on login before our +HP modifier exists; give it back. */
    public void restoreHealth(Player p) {
        Double saved = p.getPersistentDataContainer().get(keyHealth, PersistentDataType.DOUBLE);
        if (saved != null && saved > p.getHealth() && !p.isDead()) {
            p.setHealth(Math.min(saved, Attrs.maxHealth(p)));
        }
    }

    public void checkArmor(Player p, Origin o) {
        EntityEquipment eq = p.getEquipment();
        for (EquipmentSlot slot : ARMOR) {
            ItemStack item = eq.getItem(slot);
            if (item.isEmpty() || !o.forbidsArmor(item.getType())) continue;
            eq.setItem(slot, null);
            for (ItemStack left : p.getInventory().addItem(item).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), left);
            }
            plugin.lang().bar(p, o.armorMessage());
            Fx.sound(p, "minecraft:item.armor.equip_generic", 1f, 0.6f);
        }
    }
}
