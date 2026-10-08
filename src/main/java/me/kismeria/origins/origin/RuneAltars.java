package me.kismeria.origins.origin;

import me.kismeria.origins.OriginsPlugin;
import me.kismeria.origins.core.Fx;
import me.kismeria.origins.core.Txt;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Rune altar of the Runesmith (port of runesmith_altar.sk).
 * Pattern, top view (C = calibrated sculk sensor, A = amethyst block, any height within ±3):
 * <pre>
 *         A   A   A
 *     A . . C . . A
 *         A   A   A
 * </pre>
 * 4 blocks 3 away on the axes, 4 blocks 2 away on the diagonals. Drop an amethyst shard on the sensor.
 */
public final class RuneAltars implements Listener {
    private static final int[][] OFFSETS = {{3, 0}, {-3, 0}, {0, 3}, {0, -3}, {2, 2}, {2, -2}, {-2, 2}, {-2, -2}};
    private static final Particle.DustOptions DUST_LIGHT = new Particle.DustOptions(Color.fromRGB(166, 102, 230), 1.4f);
    private static final Particle.DustOptions DUST_DARK = new Particle.DustOptions(Color.fromRGB(115, 64, 178), 1.2f);

    private final OriginsPlugin plugin;
    private final Map<Block, Altar> altars = new HashMap<>();
    private final Map<Block, Long> failCooldown = new HashMap<>();

    private int durationSeconds;
    private double successChance;
    private long failCooldownMs;
    private int breakMin;
    private int breakMax;

    private static final class Altar {
        final Block center;
        final List<Block> crystals;
        int elapsedTicks;
        int angle;
        boolean lit;
        boolean burning;
        BukkitTask task;

        Altar(Block center, List<Block> crystals) {
            this.center = center;
            this.crystals = crystals;
        }

        double cx() { return center.getX() + 0.5; }
        double cy() { return center.getY() + 1; }
        double cz() { return center.getZ() + 0.5; }
    }

    public RuneAltars(OriginsPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        c.addDefault("altar.duration-seconds", 180);
        c.addDefault("altar.success-chance", 0.3);
        c.addDefault("altar.fail-cooldown-seconds", 5);
        c.addDefault("altar.burnout-breaks-min", 2);
        c.addDefault("altar.burnout-breaks-max", 3);
        durationSeconds = c.getInt("altar.duration-seconds");
        successChance = c.getDouble("altar.success-chance");
        failCooldownMs = c.getLong("altar.fail-cooldown-seconds") * 1000L;
        breakMin = c.getInt("altar.burnout-breaks-min");
        breakMax = Math.max(breakMin, c.getInt("altar.burnout-breaks-max"));
    }

    public void shutdown() {
        for (Altar a : altars.values()) if (a.task != null) a.task.cancel();
        altars.clear();
    }

    /** Runesmith stands on the heart of a lit altar. */
    public boolean isOnAltar(Player p) {
        for (Altar a : altars.values()) {
            if (!a.lit || a.burning || a.center.getWorld() != p.getWorld()) continue;
            double dy = p.getLocation().getY() - a.center.getY();
            if (Math.abs(p.getLocation().getX() - a.cx()) <= 1.2 && Math.abs(p.getLocation().getZ() - a.cz()) <= 1.2 && dy >= -0.5 && dy <= 2.5) {
                return true;
            }
        }
        return false;
    }

    public int stopAll() {
        int n = 0;
        for (Altar a : List.copyOf(altars.values())) {
            if (!a.burning) {
                burnout(a);
                n++;
            }
        }
        return n;
    }

    // --- activation ---

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (e.getItemDrop().getItemStack().getType() != Material.AMETHYST_SHARD) return;
        Item drop = e.getItemDrop();
        new BukkitRunnable() {
            int runs;

            @Override
            public void run() {
                runs++;
                if (!drop.isValid() || runs > 15) {
                    cancel();
                    return;
                }
                if (!drop.isOnGround() && runs < 3) return;
                Block b = drop.getLocation().getBlock();
                if (b.getType() != Material.CALIBRATED_SCULK_SENSOR) b = b.getRelative(BlockFace.DOWN);
                if (b.getType() != Material.CALIBRATED_SCULK_SENSOR) {
                    if (drop.isOnGround()) cancel();
                    return;
                }
                cancel();
                tryLight(b, drop);
            }
        }.runTaskTimer(plugin, 4L, 4L);
    }

    private void tryLight(Block center, Item shard) {
        if (altars.containsKey(center)) return;
        List<Block> crystals = findCrystals(center);
        if (crystals == null) return;
        Long cd = failCooldown.get(center);
        if (cd != null && cd > System.currentTimeMillis()) return;

        ItemStack stack = shard.getItemStack();
        if (stack.getAmount() > 1) {
            stack.setAmount(stack.getAmount() - 1);
            shard.setItemStack(stack);
        } else {
            shard.remove();
        }

        Altar a = new Altar(center, crystals);
        boolean success = ThreadLocalRandom.current().nextDouble() < successChance;
        if (success) altars.put(center, a);
        else failCooldown.put(center, System.currentTimeMillis() + failCooldownMs);
        ignite(a, success);
    }

    /** One amethyst block per pattern column (±3 Y), or null when the pattern is incomplete. */
    private static List<Block> findCrystals(Block c) {
        List<Block> out = new ArrayList<>();
        for (int[] o : OFFSETS) {
            Block found = null;
            for (int dy = -3; dy <= 3 && found == null; dy++) {
                Block b = c.getRelative(o[0], dy, o[1]);
                if (b.getType() == Material.AMETHYST_BLOCK) found = b;
            }
            if (found == null) return null;
            out.add(found);
        }
        return out;
    }

    // --- animations ---

    private void vortex(Altar a, int step) {
        World w = a.center.getWorld();
        double spin = 0;
        for (int i = 1; i <= step; i++) spin += 8 + i;
        double r = 3.5 - step * 0.08;
        double y = a.cy() + ((step * 8) % 100) / 100.0;
        for (int arm = 1; arm <= 3; arm++) {
            double ang = Math.toRadians(spin + arm * 120);
            double x = a.cx() + r * Math.cos(ang);
            double z = a.cz() + r * Math.sin(ang);
            Fx.particle(w, x, y, z, Particle.ENCHANT, 1, 0, 0);
            Fx.particle(w, x, y, z, Particle.SOUL_FIRE_FLAME, 1, 0, 0);
        }
        if (step % 5 == 0) sound(a, "minecraft:block.beacon.ambient", 0.6f, 0.5f + step * 0.04f);
    }

    /** Shared wind-up; success = column of light + lit altar, failure = fizzling smoke. */
    private void ignite(Altar a, boolean success) {
        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                t++;
                if (t <= 30) {
                    vortex(a, t);
                    return;
                }
                if (success) successStep(a, t - 30, this);
                else failStep(a, t - 30, this);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void successStep(Altar a, int t, BukkitRunnable self) {
        World w = a.center.getWorld();
        if (t == 1 || t == 5 || t == 9) {
            int ring = (t - 1) / 4 + 1;
            double rad = 4.0 - ring * 1.2;
            for (int seg = 1; seg <= 12; seg++) {
                double ang = Math.toRadians(seg * 30);
                Fx.particle(w, a.cx() + rad * Math.cos(ang), a.cy(), a.cz() + rad * Math.sin(ang), Particle.ENCHANT, 1, 0, 0);
                Fx.particle(w, a.cx() + rad * Math.cos(ang), a.cy(), a.cz() + rad * Math.sin(ang), Particle.SOUL_FIRE_FLAME, 1, 0, 0);
            }
            sound(a, "minecraft:block.beacon.power_select", 1f, 0.6f + ring * 0.3f);
        } else if (t == 13) {
            org.bukkit.Location c = a.center.getLocation().add(0.5, 1, 0.5);
            Fx.particle(c.clone().add(0, 1, 0), Particle.FIREWORK, 50, 0.3, 0.3, 0.3, 0.2);
            Fx.particle(c, Particle.END_ROD, 120, 0.15, 3, 0.15, 0.1);
            Fx.particle(c.clone().add(0, 1.5, 0), Particle.FIREWORK, 80, 0.4, 0.8, 0.4, 0.2);
            Fx.particle(c.clone().add(0, 1.5, 0), Particle.TOTEM_OF_UNDYING, 100, 1, 1.2, 1, 0.5);
            Fx.particle(c.clone().add(0, 1.5, 0), Particle.ENCHANT, 150, 2, 2, 2, 1.5);
            for (int i = 1; i <= 24; i++) {
                double ang = Math.toRadians(i * 15);
                w.spawnParticle(Particle.END_ROD, a.cx() + 3.5 * Math.cos(ang), a.cy(), a.cz() + 3.5 * Math.sin(ang), 2, 0, 0.1, 0, 0.02, null, true);
            }
            for (Block crystal : a.crystals) {
                double ax = crystal.getX() + 0.5, ay = crystal.getY() + 1.2, az = crystal.getZ() + 0.5;
                for (int i = 1; i <= 8; i++) {
                    double f = i / 8.0;
                    Fx.particle(w, a.cx() + (ax - a.cx()) * f, a.cy() + (ay - a.cy()) * f, a.cz() + (az - a.cz()) * f, Particle.WITCH, 1, 0, 0);
                }
                w.spawnParticle(Particle.SOUL_FIRE_FLAME, ax, ay, az, 30, 0.25, 0.4, 0.25, 0.03, null, true);
                w.spawnParticle(Particle.END_ROD, ax, ay, az, 15, 0.2, 0.3, 0.2, 0.05, null, true);
            }
            sound(a, "minecraft:entity.warden.sonic_boom", 1.2f, 1.2f);
            sound(a, "minecraft:block.beacon.activate", 2f, 0.8f);
            sound(a, "minecraft:block.conduit.activate", 2f, 1f);
            sound(a, "minecraft:block.amethyst_block.resonate", 2f, 0.5f);
            sound(a, "minecraft:item.totem.use", 1f, 1.4f);
            sound(a, "minecraft:entity.illusioner.cast_spell", 1.2f, 0.6f);
            for (Player p : nearby(a, 12)) {
                if (plugin.manager().get(p) instanceof Runesmith) plugin.lang().bar(p, "altar.lit");
            }
        } else if (t == 19) {
            self.cancel();
            if (a.burning) return;
            sound(a, "minecraft:block.amethyst_block.chime", 2f, 1.2f);
            a.lit = true;
            a.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> ambient(a), 0L, 4L);
        }
    }

    private void failStep(Altar a, int t, BukkitRunnable self) {
        World w = a.center.getWorld();
        if (t == 1) {
            sound(a, "minecraft:block.conduit.deactivate", 1.5f, 0.5f);
            sound(a, "minecraft:block.beacon.deactivate", 1.2f, 0.4f);
            sound(a, "minecraft:entity.elder_guardian.curse", 0.7f, 0.6f);
        }
        // chaotic outward burst, 8 waves 2 ticks apart
        if (t % 2 == 1 && t <= 15) {
            int burst = (t + 1) / 2;
            for (int seg = 1; seg <= 12; seg++) {
                double ang = Math.toRadians(burst * 37 + seg * 30);
                double r = burst * 0.4;
                double x = a.cx() + r * Math.cos(ang), z = a.cz() + r * Math.sin(ang);
                double y = a.cy() + ((burst * 15) % 60) / 60.0;
                w.spawnParticle(Particle.SMOKE, x, y, z, 2, 0.05, 0.05, 0.05, 0.02, null, true);
                Fx.particle(w, x, y, z, Particle.SOUL, 1, 0, 0);
            }
            if (burst % 2 == 0) sound(a, "minecraft:block.fire.extinguish", 0.8f, 0.7f);
        }
        if (t == 17) {
            org.bukkit.Location c = a.center.getLocation().add(0.5, 1, 0.5);
            Fx.particle(c.clone().add(0, 0.3, 0), Particle.LARGE_SMOKE, 30, 0.5, 0.4, 0.5, 0.04);
            Fx.particle(c.clone().add(0, 0.5, 0), Particle.CAMPFIRE_COSY_SMOKE, 12, 0.3, 0.5, 0.3, 0.03);
            sound(a, "minecraft:entity.blaze.death", 0.5f, 0.6f);
            sound(a, "minecraft:block.note_block.bass", 1f, 0.4f);
            for (Player p : nearby(a, 12)) {
                if (plugin.manager().get(p) instanceof Runesmith) plugin.lang().bar(p, "altar.failed");
            }
            self.cancel();
        }
    }

    /** Every 4 ticks while the altar burns. */
    private void ambient(Altar a) {
        if (a.burning) return;
        if (a.center.getType() != Material.CALIBRATED_SCULK_SENSOR) {
            burnout(a);
            return;
        }
        a.elapsedTicks += 4;
        if (a.elapsedTicks >= durationSeconds * 20) {
            burnout(a);
            return;
        }
        a.angle += 9;
        World w = a.center.getWorld();
        double cy = a.cy() + 0.1;

        for (int s = 1; s <= 3; s++) {
            double ang = Math.toRadians(a.angle + s * 120);
            double y = cy + (a.angle % 90) / 90.0;
            Fx.particle(w, a.cx() + 0.6 * Math.cos(ang), y, a.cz() + 0.6 * Math.sin(ang), Particle.SOUL_FIRE_FLAME, 1, 0, 0);
            Fx.particle(w, a.cx() + 1.8 * Math.cos(ang), cy, a.cz() + 1.8 * Math.sin(ang), Particle.END_ROD, 1, 0, 0);
            Fx.particle(w, a.cx() + 2.5 * Math.cos(-ang), cy, a.cz() + 2.5 * Math.sin(-ang), Particle.SOUL_FIRE_FLAME, 1, 0, 0);
        }
        if (a.elapsedTicks % 20 == 0) {
            w.spawnParticle(Particle.ENCHANT, a.cx(), cy + 0.6, a.cz(), 5, 0.25, 0.5, 0.25, 0.04, null, true);
            w.spawnParticle(Particle.END_ROD, a.cx(), cy + 0.3, a.cz(), 2, 0.1, 0.3, 0.1, 0.01, null, true);
        }

        double f = (a.angle % 360) / 360.0;
        for (Block crystal : a.crystals) {
            double ax = crystal.getX() + 0.5, ay = crystal.getY() + 1.2, az = crystal.getZ() + 0.5;
            double x = a.cx() + (ax - a.cx()) * f, y = cy + (ay - cy) * f, z = a.cz() + (az - a.cz()) * f;
            Fx.particle(w, x, y, z, Particle.WITCH, 1, 0, 0);
            Fx.particle(w, x, y, z, Particle.ENCHANT, 1, 0, 0);
            if (a.elapsedTicks % 20 == 0) w.spawnParticle(Particle.SOUL_FIRE_FLAME, ax, ay, az, 2, 0.1, 0.15, 0.1, 0.005, null, true);
        }

        if (a.elapsedTicks % 60 == 0) {
            sound(a, "minecraft:block.beacon.ambient", 0.5f, 0.4f);
            sound(a, "minecraft:block.amethyst_block.chime", 0.3f, 0.6f);
        }
        // last 15 s: the altar cracks
        if (a.elapsedTicks >= (durationSeconds - 15) * 20) {
            if (a.elapsedTicks % 20 == 0) w.spawnParticle(Particle.SMOKE, a.cx(), cy + 0.5, a.cz(), 8, 0.4, 0.4, 0.4, 0.03, null, true);
            if (a.elapsedTicks % 40 == 0) sound(a, "minecraft:block.amethyst_block.hit", 0.8f, 0.5f);
        }
    }

    private void burnout(Altar a) {
        if (a.burning) return;
        a.burning = true;
        if (a.task != null) a.task.cancel();
        sound(a, "minecraft:block.beacon.deactivate", 1.5f, 0.6f);
        sound(a, "minecraft:block.conduit.deactivate", 1.5f, 0.7f);
        sound(a, "minecraft:entity.elder_guardian.curse", 0.8f, 0.8f);

        List<Block> pool = new ArrayList<>(a.crystals);
        Collections.shuffle(pool);
        int count = ThreadLocalRandom.current().nextInt(breakMin, breakMax + 1);
        List<Block> victims = pool.subList(0, Math.min(count, pool.size()));
        World w = a.center.getWorld();

        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                t++;
                if (t <= 32) {
                    // collapse: energy spirals into the heart
                    if (t % 2 == 0) {
                        int ix = t / 2;
                        double r = 3.0 - ix * 0.18;
                        for (int seg = 1; seg <= 6; seg++) {
                            double ang = Math.toRadians(ix * 45 + seg * 60);
                            double x = a.cx() + r * Math.cos(ang), z = a.cz() + r * Math.sin(ang), y = a.cy() + (16 - ix) * 0.08;
                            Fx.particle(w, x, y, z, Particle.SOUL_FIRE_FLAME, 1, 0, 0);
                            w.spawnParticle(Particle.SMOKE, x, y, z, 1, 0, 0.02, 0, 0.01, null, true);
                        }
                        if (ix % 4 == 0) sound(a, "minecraft:block.amethyst_block.hit", 0.6f, 0.4f);
                    }
                    return;
                }
                if (t == 33) {
                    org.bukkit.Location c = a.center.getLocation().add(0.5, 1, 0.5);
                    Fx.particle(c.clone().add(0, 0.5, 0), Particle.LARGE_SMOKE, 50, 0.5, 1, 0.5, 0.08);
                    Fx.particle(c.clone().add(0, 1, 0), Particle.SMOKE, 60, 0.6, 1.5, 0.6, 0.15);
                    Fx.particle(c.clone().add(0, 1, 0), Particle.CAMPFIRE_COSY_SMOKE, 20, 0.3, 1, 0.3, 0.05);
                    sound(a, "minecraft:entity.blaze.death", 1f, 0.4f);
                    sound(a, "minecraft:entity.wither.death", 0.4f, 1.6f);
                }
                // crystals shatter one by one, 10 ticks apart
                int idx = (t - 33) / 10;
                int phase = (t - 33) % 10;
                if (idx < victims.size()) {
                    Block v = victims.get(idx);
                    org.bukkit.Location vc = v.getLocation().add(0.5, 0.5, 0.5);
                    if (phase == 0) {
                        Fx.particle(vc, Particle.END_ROD, 20, 0.1, 0.1, 0.1, 0.15);
                        Fx.sound(vc, "minecraft:block.amethyst_block.resonate", 1f, 1.4f);
                    } else if (phase == 4) {
                        if (v.getType() == Material.AMETHYST_BLOCK) v.setType(Material.AIR);
                        Fx.particle(vc, Particle.DUST, 40, 0.25, 0.25, 0.25, 0.15, DUST_LIGHT);
                        Fx.particle(vc, Particle.DUST, 20, 0.25, 0.25, 0.25, 0.1, DUST_DARK);
                        Fx.particle(vc, Particle.FIREWORK, 15, 0.2, 0.2, 0.2, 0.1);
                        Fx.particle(vc, Particle.ENCHANT, 12, 0.3, 0.3, 0.3, 0.05);
                        Fx.sound(vc, "minecraft:block.amethyst_block.break", 1.3f, 0.7f);
                        Fx.sound(vc, "minecraft:block.glass.break", 1f, 0.9f);
                    }
                    return;
                }
                for (Player p : nearby(a, 12)) plugin.lang().bar(p, "altar.burnout");
                altars.remove(a.center);
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Breaking the heart of a lit altar is refused and burns it out instead. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Altar a = altars.get(e.getBlock());
        if (a == null) return;
        e.setCancelled(true);
        burnout(a);
    }

    private List<Player> nearby(Altar a, double radius) {
        List<Player> out = new ArrayList<>();
        org.bukkit.Location c = a.center.getLocation().add(0.5, 0.5, 0.5);
        for (Player p : a.center.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(c) <= radius * radius) out.add(p);
        }
        return out;
    }

    private static void sound(Altar a, String key, float volume, float pitch) {
        Fx.sound(a.center.getLocation().add(0.5, 1, 0.5), key, volume, pitch);
    }
}
