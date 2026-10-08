package me.kismeria.origins.core;

import org.bukkit.World;
import org.bukkit.entity.Player;

public final class Env {
    private Env() {
    }

    public static boolean overworld(Player p) {
        return p.getWorld().getEnvironment() == World.Environment.NORMAL;
    }

    public static boolean day(Player p) {
        return overworld(p) && p.getWorld().isDayTime();
    }

    public static boolean night(Player p) {
        return overworld(p) && !p.getWorld().isDayTime();
    }

    /** Open sky, daytime, no rain. */
    public static boolean inSun(Player p) {
        return day(p) && !p.getWorld().hasStorm() && p.getEyeLocation().getBlock().getLightFromSky() >= 15;
    }

    public static String biome(Player p) {
        return p.getLocation().getBlock().getBiome().getKey().getKey();
    }
}
