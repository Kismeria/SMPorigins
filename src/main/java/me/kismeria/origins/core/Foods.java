package me.kismeria.origins.core;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.FoodProperties;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.EnumSet;
import java.util.Set;

import static org.bukkit.Material.*;

public final class Foods {
    public static final Set<Material> PLANT = EnumSet.of(
            APPLE, GOLDEN_APPLE, ENCHANTED_GOLDEN_APPLE, BREAD, POTATO, BAKED_POTATO, POISONOUS_POTATO,
            CARROT, GOLDEN_CARROT, MELON_SLICE, SWEET_BERRIES, GLOW_BERRIES, COOKIE, PUMPKIN_PIE,
            DRIED_KELP, CHORUS_FRUIT, BEETROOT, BEETROOT_SOUP);
    public static final Set<Material> MEAT = EnumSet.of(
            BEEF, COOKED_BEEF, PORKCHOP, COOKED_PORKCHOP, CHICKEN, COOKED_CHICKEN,
            MUTTON, COOKED_MUTTON, RABBIT, COOKED_RABBIT, RABBIT_STEW);
    public static final Set<Material> FISH = EnumSet.of(
            COD, COOKED_COD, SALMON, COOKED_SALMON, TROPICAL_FISH, PUFFERFISH);
    public static final Set<Material> RAW = EnumSet.of(
            BEEF, PORKCHOP, CHICKEN, MUTTON, RABBIT, ROTTEN_FLESH, COD, SALMON, TROPICAL_FISH, PUFFERFISH);

    private Foods() {
    }

    /**
     * Adds (mult - 1) of the food's nutrition and saturation plus a flat bonus.
     * Runs one tick later, after vanilla applied the normal values.
     */
    public static void bonus(Plugin plugin, Player p, ItemStack item, double mult, int flatFood) {
        FoodProperties food = item.getData(DataComponentTypes.FOOD);
        if (food == null) return;
        int extraFood = (int) Math.round(food.nutrition() * (mult - 1)) + flatFood;
        float extraSat = (float) (food.saturation() * (mult - 1));
        if (extraFood <= 0 && extraSat <= 0) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            p.setFoodLevel(Math.min(20, p.getFoodLevel() + extraFood));
            p.setSaturation(Math.min(p.getFoodLevel(), p.getSaturation() + extraSat));
        });
    }
}
