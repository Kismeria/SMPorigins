package me.kismeria.origins.origin;

import me.kismeria.origins.OriginsPlugin;
import me.kismeria.origins.core.Env;
import me.kismeria.origins.core.Fx;
import me.kismeria.origins.core.Origin;
import me.kismeria.origins.core.Txt;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.concurrent.ThreadLocalRandom;

public final class Miner extends Origin {
    private final int haste1Y;
    private final int haste2Y;
    private final double health;
    private final double sneezeChance;

    public Miner(OriginsPlugin plugin) {
        super(plugin, "miner");
        haste1Y = integer("haste-1-below-y", 40);
        haste2Y = integer("haste-2-below-y", 0);
        health = num("max-health-delta", -4);
        sneezeChance = num("sneeze-chance", 0.15);
        plus("miner_underground", "y1", haste1Y, "y2", haste2Y);
        plus("miner_thick_skull");
        minus("miner_photophobia");
        minus("miner_heavy_lungs", "hearts", Txt.hearts(health));
    }

    @Override public String color() { return "#9aa3b5"; }
    @Override public Material icon() { return Material.IRON_PICKAXE; }
    @Override public int impact() { return 1; }

    @Override
    public void apply(Player p) {
        modifier(p, Attribute.MAX_HEALTH, "health", health, Operation.ADD_NUMBER);
    }

    @Override
    public void tick(Player p, int tick) {
        int y = p.getLocation().getBlockY();
        if (y <= haste2Y) effect(p, PotionEffectType.HASTE, 1);
        else if (y <= haste1Y) effect(p, PotionEffectType.HASTE, 0);

        if (Env.inSun(p)) {
            effect(p, PotionEffectType.WEAKNESS, 0);
            effect(p, PotionEffectType.SLOWNESS, 0);
        }

        // every 10 s: dust in tight tunnels makes the miner sneeze
        if (tick % 20 == 0 && p.getLocation().add(0, 2, 0).getBlock().getType().isSolid()
                && ThreadLocalRandom.current().nextDouble() < sneezeChance) {
            Location nose = p.getEyeLocation().add(p.getLocation().getDirection().multiply(0.6));
            Fx.particle(nose, Particle.SNEEZE, 15, 0.1, 0.1, 0.1, 0.02);
            Fx.sound(p.getLocation(), "minecraft:entity.panda.sneeze", 1f, 1f);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION && has(e.getEntity())) e.setCancelled(true);
    }
}
