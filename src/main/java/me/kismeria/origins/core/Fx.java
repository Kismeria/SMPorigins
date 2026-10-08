package me.kismeria.origins.core;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Particles, sounds and short racial effects. */
public final class Fx {
    private Fx() {
    }

    /** Racial effect: ambient (blue icon border), no particles. */
    public static void effect(Player p, PotionEffectType type, int amplifier, int ticks) {
        p.addPotionEffect(new PotionEffect(type, ticks, amplifier, true, false, true));
    }

    public static void particle(Location l, Particle type, int count, double dx, double dy, double dz, double speed) {
        l.getWorld().spawnParticle(type, l, count, dx, dy, dz, speed, null, true);
    }

    public static <T> void particle(Location l, Particle type, int count, double dx, double dy, double dz, double speed, T data) {
        l.getWorld().spawnParticle(type, l, count, dx, dy, dz, speed, data, true);
    }

    public static void particle(World w, double x, double y, double z, Particle type, int count, double d, double speed) {
        w.spawnParticle(type, x, y, z, count, d, d, d, speed, null, true);
    }

    public static void sound(Location l, String key, float volume, float pitch) {
        l.getWorld().playSound(l, key, SoundCategory.MASTER, volume, pitch);
    }

    /** Sound only this player hears. */
    public static void sound(Player p, String key, float volume, float pitch) {
        p.playSound(p.getLocation(), key, SoundCategory.MASTER, volume, pitch);
    }
}
