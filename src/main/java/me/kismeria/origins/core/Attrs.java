package me.kismeria.origins.core;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Attribute modifiers are transient (not saved to player data), so removing the plugin
 * never leaves players with broken stats. The ticker re-applies them every second.
 */
public final class Attrs {
    private static final Attribute[] USED = {
            Attribute.MAX_HEALTH, Attribute.ATTACK_SPEED, Attribute.SNEAKING_SPEED,
            Attribute.BLOCK_INTERACTION_RANGE, Attribute.FALL_DAMAGE_MULTIPLIER, Attribute.MOVEMENT_SPEED
    };
    /** Vanilla player base values the old Skript overwrote with "/attribute ... base set". */
    private static final Map<Attribute, Double> SKRIPT_BASES = Map.of(
            Attribute.MAX_HEALTH, 20.0, Attribute.ATTACK_SPEED, 4.0, Attribute.SNEAKING_SPEED, 0.3);

    private Attrs() {
    }

    public static void set(Player p, Attribute a, NamespacedKey key, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = p.getAttribute(a);
        if (inst == null) return;
        AttributeModifier cur = inst.getModifier(key);
        if (cur != null) {
            if (cur.getAmount() == amount && cur.getOperation() == op) return;
            inst.removeModifier(key);
        }
        inst.addTransientModifier(new AttributeModifier(key, amount, op, EquipmentSlotGroup.ANY));
    }

    public static void clearAll(Player p, Plugin plugin) {
        String ns = plugin.getName().toLowerCase(Locale.ROOT);
        for (Attribute a : USED) {
            AttributeInstance inst = p.getAttribute(a);
            if (inst == null) continue;
            for (AttributeModifier m : List.copyOf(inst.getModifiers())) {
                if (m.getKey().getNamespace().equals(ns)) inst.removeModifier(m);
            }
        }
    }

    public static void resetSkriptBases(Player p) {
        SKRIPT_BASES.forEach((a, base) -> {
            AttributeInstance inst = p.getAttribute(a);
            if (inst != null && inst.getBaseValue() != base) inst.setBaseValue(base);
        });
    }

    public static double maxHealth(Player p) {
        AttributeInstance inst = p.getAttribute(Attribute.MAX_HEALTH);
        return inst == null ? 20 : inst.getValue();
    }
}
