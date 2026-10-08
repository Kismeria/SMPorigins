package me.kismeria.origins.origin;

import me.kismeria.origins.OriginsPlugin;
import me.kismeria.origins.core.Foods;
import me.kismeria.origins.core.Origin;
import me.kismeria.origins.core.Txt;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.potion.PotionEffectType;

public final class Hunter extends Origin {
    private final double arrowBonus;
    private final double health;
    private final boolean allowGoldenApples;

    public Hunter(OriginsPlugin plugin) {
        super(plugin, "hunter");
        arrowBonus = num("projectile-damage-bonus", 0.15);
        health = num("max-health-delta", 4);
        allowGoldenApples = flag("carnivore-allows-golden-apples", true);
        plus("hunter_eagle_eye", "pct", Txt.pct(arrowBonus));
        plus("hunter_tracker");
        plus("hunter_vitality", "hearts", Txt.hearts(health));
        minus("hunter_light_armor");
        minus("hunter_carnivore", "note", allowGoldenApples ? ref("origins.hunter.golden-apples-note") : "");
    }

    @Override public String color() { return "#6aa84f"; }
    @Override public Material icon() { return Material.BOW; }
    @Override public int impact() { return 1; }

    @Override
    public void apply(Player p) {
        modifier(p, Attribute.MAX_HEALTH, "health", health, Operation.ADD_NUMBER);
    }

    @Override
    public void tick(Player p, int tick) {
        if (p.isSprinting() && p.getLocation().subtract(0, 0.1, 0).getBlock().getType() == Material.GRASS_BLOCK) {
            effect(p, PotionEffectType.SPEED, 0);
        }
    }

    @Override
    public boolean forbidsArmor(Material m) {
        return m.name().startsWith("DIAMOND_") || m.name().startsWith("NETHERITE_");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShot(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p && has(p)) {
            e.setDamage(e.getDamage() * (1 + arrowBonus));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent e) {
        Material m = e.getItem().getType();
        if (!Foods.PLANT.contains(m) || !has(e.getPlayer())) return;
        if (allowGoldenApples && (m == Material.GOLDEN_APPLE || m == Material.ENCHANTED_GOLDEN_APPLE)) return;
        e.setCancelled(true);
        lang().bar(e.getPlayer(), "origins.hunter.msg.carnivore");
    }
}
