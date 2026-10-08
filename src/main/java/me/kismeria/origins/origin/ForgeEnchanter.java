package me.kismeria.origins.origin;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

import static org.bukkit.enchantments.Enchantment.*;

/** Blacksmith "forge sparks": random enchant tables per item kind (ported from the Skript). */
final class ForgeEnchanter {
    private ForgeEnchanter() {
    }

    /** @return true when at least one enchantment was added */
    static boolean enchant(ItemStack it, Random r) {
        String n = it.getType().name();
        if (n.endsWith("_SWORD") || n.endsWith("_SPEAR")) {
            int r1 = roll(r);
            add(it, r1 <= 60 ? SHARPNESS : r1 <= 80 ? SMITE : BANE_OF_ARTHROPODS, lvl(r));
            if (chance(r)) {
                int r2 = roll(r);
                if (r2 <= 30) add(it, FIRE_ASPECT, 1 + r.nextInt(2));
                else if (r2 <= 60) add(it, KNOCKBACK, 1 + r.nextInt(2));
                else if (r2 <= 85) add(it, n.endsWith("_SPEAR") ? LUNGE : SWEEPING_EDGE, lvl(r));
                else add(it, LOOTING, lvl(r));
            }
        } else if (n.endsWith("_AXE")) {
            add(it, roll(r) <= 50 ? SHARPNESS : EFFICIENCY, lvl(r));
            if (chance(r)) {
                if (roll(r) <= 50) add(it, FORTUNE, lvl(r));
                else add(it, SILK_TOUCH, 1);
            }
        } else if (n.endsWith("_PICKAXE") || n.endsWith("_SHOVEL") || n.endsWith("_HOE")) {
            add(it, EFFICIENCY, lvl(r));
            if (chance(r)) {
                if (roll(r) <= 60) add(it, FORTUNE, lvl(r));
                else add(it, SILK_TOUCH, 1);
            }
        } else if (n.equals("BOW")) {
            add(it, POWER, lvl(r));
            if (chance(r)) {
                int r2 = roll(r);
                if (r2 <= 40) add(it, PUNCH, 1 + r.nextInt(2));
                else if (r2 <= 80) add(it, FLAME, 1);
                else add(it, INFINITY, 1);
            }
        } else if (n.equals("CROSSBOW")) {
            if (roll(r) <= 60) add(it, PIERCING, lvl(r));
            else add(it, MULTISHOT, 1);
            if (chance(r)) add(it, QUICK_CHARGE, lvl(r));
        } else if (n.equals("MACE")) {
            add(it, roll(r) <= 50 ? DENSITY : BREACH, lvl(r));
            if (chance(r)) add(it, WIND_BURST, lvl(r));
        } else if (n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS")) {
            int r1 = roll(r);
            add(it, r1 <= 50 ? PROTECTION : r1 <= 70 ? FIRE_PROTECTION : r1 <= 85 ? BLAST_PROTECTION : PROJECTILE_PROTECTION, lvl(r));
            if (chance(r)) armorExtra(it, n, r);
        } else if (n.equals("SHIELD")) {
            add(it, UNBREAKING, lvl(r));
        } else {
            return false;
        }

        if (r.nextDouble() < 0.25) {
            if (roll(r) <= 80) add(it, UNBREAKING, lvl(r));
            else add(it, MENDING, 1);
        }
        return !it.getEnchantments().isEmpty();
    }

    private static void armorExtra(ItemStack it, String n, Random r) {
        int r2 = roll(r);
        if (n.endsWith("_HELMET")) {
            if (r2 <= 40) add(it, RESPIRATION, lvl(r));
            else if (r2 <= 80) add(it, AQUA_AFFINITY, 1);
            else add(it, THORNS, 1 + r.nextInt(2));
        } else if (n.endsWith("_CHESTPLATE")) {
            add(it, THORNS, 1 + r.nextInt(2));
        } else if (n.endsWith("_LEGGINGS")) {
            if (r2 <= 50) add(it, SWIFT_SNEAK, lvl(r));
            else add(it, THORNS, 1 + r.nextInt(2));
        } else {
            if (r2 <= 30) add(it, FEATHER_FALLING, lvl(r));
            else if (r2 <= 60) add(it, DEPTH_STRIDER, lvl(r));
            else if (r2 <= 80) add(it, SOUL_SPEED, lvl(r));
            else add(it, FROST_WALKER, 1 + r.nextInt(2));
        }
    }

    private static int roll(Random r) {
        return 1 + r.nextInt(100);
    }

    private static int lvl(Random r) {
        return 1 + r.nextInt(3);
    }

    private static boolean chance(Random r) {
        return r.nextDouble() < 0.2;
    }

    /** Respects max level and vanilla conflicts (e.g. infinity + mending, fortune + silk touch). */
    private static void add(ItemStack it, Enchantment e, int level) {
        for (Enchantment ex : it.getEnchantments().keySet()) {
            if (ex.equals(e) || ex.conflictsWith(e)) return;
        }
        it.addUnsafeEnchantment(e, Math.min(level, e.getMaxLevel()));
    }
}
