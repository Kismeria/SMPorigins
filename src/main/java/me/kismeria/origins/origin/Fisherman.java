package me.kismeria.origins.origin;

import me.kismeria.origins.OriginsPlugin;
import me.kismeria.origins.core.Env;
import me.kismeria.origins.core.Foods;
import me.kismeria.origins.core.Fx;
import me.kismeria.origins.core.Origin;
import me.kismeria.origins.core.Txt;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.bukkit.event.entity.EntityDamageEvent.DamageCause.*;

public final class Fisherman extends Origin {
    private static final Set<EntityDamageEvent.DamageCause> FIRE_CAUSES = EnumSet.of(FIRE, FIRE_TICK, LAVA, HOT_FLOOR, CAMPFIRE);

    private final int breathSeconds;
    private final double luckChancePerMinute;
    private final int luckMinMinutes;
    private final int luckMaxMinutes;
    private final double fishMult;
    private final double fireMult;
    private final Set<UUID> inWater = new HashSet<>();

    public Fisherman(OriginsPlugin plugin) {
        super(plugin, "fisherman");
        breathSeconds = integer("water-breathing-seconds", 6);
        luckChancePerMinute = num("luck-chance-per-minute", 1.0 / 60);
        luckMinMinutes = integer("luck-min-minutes", 2);
        luckMaxMinutes = integer("luck-max-minutes", 15);
        fishMult = num("fish-food-multiplier", 1.5);
        fireMult = num("fire-damage-multiplier", 1.2);
        plus("fisherman_sea_wolf", "seconds", breathSeconds);
        plus("fisherman_lucky_catch", "min", luckMinMinutes, "max", luckMaxMinutes);
        plus("fisherman_ichthyologist", "mult", Txt.num(fishMult));
        minus("fisherman_landlubber");
        minus("fisherman_flammable", "pct", Txt.pct(fireMult - 1));
    }

    @Override public String color() { return "#3fa7d6"; }
    @Override public Material icon() { return Material.FISHING_ROD; }
    @Override public int impact() { return 1; }

    @Override
    public void tick(Player p, int tick) {
        if (p.isInWater()) {
            if (inWater.add(p.getUniqueId())) {
                Fx.effect(p, PotionEffectType.WATER_BREATHING, 0, breathSeconds * 20);
                Fx.particle(p.getLocation().add(0, 1, 0), Particle.BUBBLE, 20, 0.4, 0.5, 0.4, 0.05);
            }
        } else {
            inWater.remove(p.getUniqueId());
        }

        String biome = Env.biome(p);
        if (biome.contains("desert") || biome.contains("badlands")) effect(p, PotionEffectType.WEAKNESS, 1);

        if (tick % 120 == 0 && !p.hasPotionEffect(PotionEffectType.LUCK)
                && ThreadLocalRandom.current().nextDouble() < luckChancePerMinute) {
            int minutes = ThreadLocalRandom.current().nextInt(luckMinMinutes, luckMaxMinutes + 1);
            p.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, minutes * 1200, 0, true, false, true));
            lang().send(p, "origins.fisherman.msg.luck", "minutes", minutes);
            Fx.sound(p, "minecraft:entity.dolphin.play", 1f, 1f);
            Fx.particle(p.getLocation().add(0, 1, 0), Particle.DOLPHIN, 40, 0.5, 0.6, 0.5, 0);
        }
    }

    @Override
    public void reset(Player p) {
        inWater.remove(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent e) {
        if (has(e.getPlayer()) && Foods.FISH.contains(e.getItem().getType())) {
            Foods.bonus(plugin, e.getPlayer(), e.getItem(), fishMult, 0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(EntityDamageEvent e) {
        if (FIRE_CAUSES.contains(e.getCause()) && has(e.getEntity())) e.setDamage(e.getDamage() * fireMult);
    }
}
