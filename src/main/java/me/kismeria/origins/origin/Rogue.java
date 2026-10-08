package me.kismeria.origins.origin;

import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import me.kismeria.origins.OriginsPlugin;
import me.kismeria.origins.core.Env;
import me.kismeria.origins.core.Fx;
import me.kismeria.origins.core.Meters;
import net.kyori.adventure.text.Component;
import me.kismeria.origins.core.Origin;
import me.kismeria.origins.core.Txt;
import org.bukkit.Bukkit;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Stealth no longer moves armour out of the slots (the Skript could lose or dupe it).
 * Armour and held items stay equipped; other players just receive empty equipment packets.
 */
public final class Rogue extends Origin {
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.HAND, EquipmentSlot.OFF_HAND};

    private final int stealthDelayTicks;
    private final int cooldownSeconds;
    private final double backstabBonus;
    private final double backstabCos;
    private final double health;
    private final double sneakSpeed;
    private final boolean attackBreaks;

    private static final int EFFECT_TICKS = 40;

    private final Map<UUID, Integer> sneakTicks = new HashMap<>();
    private final Set<UUID> stealthed = new HashSet<>();
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();

    public Rogue(OriginsPlugin plugin) {
        super(plugin, "rogue");
        stealthDelayTicks = integer("stealth-sneak-ticks", 30);
        cooldownSeconds = integer("stealth-cooldown-seconds", 20);
        backstabBonus = num("backstab-damage-bonus", 0.2);
        backstabCos = Math.cos(Math.toRadians(num("backstab-angle", 50)));
        health = num("max-health-delta", -4);
        sneakSpeed = num("sneak-speed-bonus", 0.4);
        attackBreaks = flag("attack-breaks-stealth", false);
        plus("rogue_night_stalker");
        plus("rogue_stealth", "seconds", Txt.num(stealthDelayTicks / 20.0));
        plus("rogue_backstab", "pct", Txt.pct(backstabBonus));
        minus("rogue_frail", "hearts", Txt.hearts(health), "cd", cooldownSeconds, "attack", attackBreaks ? ref("origins.rogue.attack-note") : "");
        minus("rogue_sun_weakness");
    }

    @Override public String color() { return "#8e7cc3"; }
    @Override public Material icon() { return Material.FEATHER; }
    @Override public int impact() { return 2; }

    @Override
    public void apply(Player p) {
        modifier(p, Attribute.MAX_HEALTH, "health", health, Operation.ADD_NUMBER);
        modifier(p, Attribute.SNEAKING_SPEED, "sneak", sneakSpeed, Operation.ADD_NUMBER);
    }

    @Override
    public void tick(Player p, int tick) {
        if (Env.night(p)) effect(p, PotionEffectType.SPEED, 0);
        if (Env.inSun(p)) effect(p, PotionEffectType.WEAKNESS, 0);

        UUID id = p.getUniqueId();
        if (!p.isSneaking()) {
            sneakTicks.remove(id);
            if (stealthed.contains(id)) exit(p, false);
            return;
        }
        if (stealthed.contains(id)) {
            enter(p); // refresh the 2 s effects and the fake empty equipment
            return;
        }
        Long cd = cooldownUntil.get(id);
        long now = System.currentTimeMillis();
        if (cd != null && cd > now) return;
        if (sneakTicks.merge(id, 10, Integer::sum) >= stealthDelayTicks) enter(p);
    }

    /** Stealth: filling while sneaking, shimmering while hidden, refilling on cooldown. */
    @Override
    public Component hudBar(Player p) {
        UUID id = p.getUniqueId();
        if (stealthed.contains(id)) return Meters.shimmer("#3b2a63", "#a28fe0", Bukkit.getCurrentTick() / 2);
        if (!p.isSneaking()) return null;
        Long cd = cooldownUntil.get(id);
        long now = System.currentTimeMillis();
        if (cd != null && cd > now) {
            return Meters.bar(1 - (cd - now) / (cooldownSeconds * 1000.0), "#4a2030", "#8e5c8a", "#1f1a24", null);
        }
        return Meters.bar(sneakTicks.getOrDefault(id, 0) / (double) stealthDelayTicks, "#4b3a7a", "#b9a6ff", "#1f1a2a", "#ffffff");
    }

    @Override
    public Component hudHint(Player p) {
        UUID id = p.getUniqueId();
        if (stealthed.contains(id)) return lang().get(p, "origins.rogue.hint.hidden");
        Long cd = cooldownUntil.get(id);
        long now = System.currentTimeMillis();
        if (p.isSneaking() && cd != null && cd > now) {
            return lang().get(p, "origins.rogue.msg.cooldown", "seconds", (cd - now) / 1000 + 1);
        }
        return null;
    }

    private void enter(Player p) {
        boolean fresh = stealthed.add(p.getUniqueId());
        // like the Skript: 2 s effects refreshed while sneaking; darkness fades out by itself after leaving
        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, EFFECT_TICKS, 0, true, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, EFFECT_TICKS, 0, true, false, false));
        stashArmor(p);
        hide(p);
        if (fresh) {
            Fx.particle(p.getLocation().add(0, 1, 0), Particle.LARGE_SMOKE, 12, 0.3, 0.5, 0.3, 0.01);
            Fx.sound(p, "minecraft:entity.illusioner.mirror_move", 0.6f, 1.6f);
            lang().bar(p, "origins.rogue.msg.vanished");
        }
    }

    private void exit(Player p, boolean broken) {
        UUID id = p.getUniqueId();
        sneakTicks.remove(id);
        if (!stealthed.remove(id)) {
            restoreArmor(p, null);
            return;
        }
        removeOurs(p, PotionEffectType.INVISIBILITY);
        restoreArmor(p, null);
        reveal(p);
        if (broken) {
            cooldownUntil.put(id, System.currentTimeMillis() + cooldownSeconds * 1000L);
            lang().bar(p, "origins.rogue.msg.broken", "seconds", cooldownSeconds);
            Fx.sound(p, "minecraft:entity.illusioner.prepare_blindness", 0.6f, 1.4f);
        }
    }

    // --- armour buffer (like the Skript: armour comes off in stealth, goes back on after) ---

    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** Kept in the player's own data file, next to the inventory: a crash can't lose or dupe it. */
    private NamespacedKey bufferKey(EquipmentSlot slot) {
        return key("armor_" + slot.name().toLowerCase(java.util.Locale.ROOT));
    }

    private void stashArmor(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        for (EquipmentSlot slot : ARMOR) {
            ItemStack item = p.getEquipment().getItem(slot);
            // a piece put on during stealth stays on (hidden by packets) if the buffer slot is taken
            if (item.isEmpty() || pdc.has(bufferKey(slot))) continue;
            pdc.set(bufferKey(slot), PersistentDataType.BYTE_ARRAY, item.serializeAsBytes());
            p.getEquipment().setItem(slot, null);
        }
    }

    /**
     * Puts the buffered armour back. An occupied slot sends the piece to the inventory (or the ground).
     * {@code deathDrops} != null: the player is dying without keepInventory, so the pieces are also
     * added to the drops (the inventory is cleared after the event; SmpCore souls read the inventory).
     */
    private void restoreArmor(Player p, java.util.List<ItemStack> deathDrops) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        for (EquipmentSlot slot : ARMOR) {
            byte[] bytes = pdc.get(bufferKey(slot), PersistentDataType.BYTE_ARRAY);
            if (bytes == null) continue;
            pdc.remove(bufferKey(slot));
            ItemStack item;
            try {
                item = ItemStack.deserializeBytes(bytes);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Не удалось вернуть броню разбойника " + p.getName() + ": " + ex);
                continue;
            }
            if (deathDrops != null) deathDrops.add(item.clone());
            if (p.getEquipment().getItem(slot).isEmpty()) {
                p.getEquipment().setItem(slot, item);
            } else if (deathDrops == null) {
                for (ItemStack left : p.getInventory().addItem(item).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
            } else {
                p.getInventory().addItem(item);
            }
        }
    }

    /** Before SmpCore souls (HIGHEST) and vanilla drops: the armour must die with the player, not vanish. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(org.bukkit.event.entity.PlayerDeathEvent e) {
        Player p = e.getPlayer();
        stealthed.remove(p.getUniqueId());
        plugin.equipmentMask().unmask(p);
        restoreArmor(p, e.getKeepInventory() ? null : e.getDrops());
    }

    /** Leftover buffer (race changed while offline, plugin was removed mid-stealth...). */
    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
        restoreArmor(e.getPlayer(), null);
    }

    /** Only our short ambient effect, never a real invisibility potion. */
    private static void removeOurs(Player p, PotionEffectType type) {
        PotionEffect cur = p.getPotionEffect(type);
        if (cur != null && cur.isAmbient() && !cur.isInfinite() && cur.getDuration() <= EFFECT_TICKS) p.removePotionEffect(type);
    }

    private static Map<EquipmentSlot, ItemStack> empty() {
        Map<EquipmentSlot, ItemStack> m = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot s : SLOTS) m.put(s, ItemStack.empty());
        return m;
    }

    private void hide(Player p) {
        plugin.equipmentMask().mask(p);
        Map<EquipmentSlot, ItemStack> air = empty();
        for (Player viewer : p.getTrackedBy()) viewer.sendEquipmentChange(p, air);
    }

    private void reveal(Player p) {
        plugin.equipmentMask().unmask(p);
        Map<EquipmentSlot, ItemStack> real = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot s : SLOTS) real.put(s, p.getEquipment().getItem(s));
        for (Player viewer : p.getTrackedBy()) viewer.sendEquipmentChange(p, real);
    }

    @Override
    public void reset(Player p) {
        exit(p, false);
        cooldownUntil.remove(p.getUniqueId());
    }

    @Override
    public void shutdown() {
        for (UUID id : Set.copyOf(stealthed)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) exit(p, false);
        }
    }

    @EventHandler
    public void onTrack(PlayerTrackEntityEvent e) {
        if (!(e.getEntity() instanceof Player rogue) || !stealthed.contains(rogue.getUniqueId())) return;
        Player viewer = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (viewer.isOnline() && stealthed.contains(rogue.getUniqueId())) viewer.sendEquipmentChange(rogue, empty());
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && stealthed.contains(p.getUniqueId())) exit(p, true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent e) {
        Player attacker = null;
        if (e.getDamager() instanceof Player p) attacker = p;
        else if (e.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) attacker = p;
        if (attacker == null || !has(attacker)) return;

        if (e.getDamager() == attacker && e.getCause() == DamageCause.ENTITY_ATTACK
                && e.getEntity() instanceof LivingEntity victim && behind(attacker, victim)) {
            e.setDamage(e.getDamage() * (1 + backstabBonus));
            lang().bar(attacker, "origins.rogue.msg.backstab", "pct", Txt.pct(backstabBonus));
            Fx.particle(victim.getLocation().add(0, victim.getHeight() * 0.6, 0), Particle.CRIT, 15, 0.3, 0.3, 0.3, 0.2);
            Fx.sound(victim.getLocation(), "minecraft:entity.player.attack.crit", 1f, 0.8f);
        }
        if (attackBreaks && stealthed.contains(attacker.getUniqueId())) {
            Player a = attacker;
            Bukkit.getScheduler().runTask(plugin, () -> exit(a, true));
        }
    }

    /** Victim looks away from the attacker (within the configured angle). */
    private boolean behind(Player attacker, LivingEntity victim) {
        Vector facing = victim.getLocation().getDirection().setY(0);
        Vector toVictim = victim.getLocation().toVector().subtract(attacker.getLocation().toVector()).setY(0);
        if (facing.lengthSquared() < 1e-6 || toVictim.lengthSquared() < 1e-6) return false;
        return facing.normalize().dot(toVictim.normalize()) >= backstabCos;
    }
}
