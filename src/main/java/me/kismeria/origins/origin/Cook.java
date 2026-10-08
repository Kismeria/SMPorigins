package me.kismeria.origins.origin;

import me.kismeria.origins.OriginsPlugin;
import me.kismeria.origins.core.Foods;
import me.kismeria.origins.core.Origin;
import me.kismeria.origins.core.Txt;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class Cook extends Origin {
    private final int bonusFood;
    private final double swordBonus;
    private final int crashFood;

    public Cook(OriginsPlugin plugin) {
        super(plugin, "cook");
        bonusFood = integer("bonus-food", 2);
        swordBonus = num("sword-damage-bonus", 0.1);
        crashFood = integer("weak-at-food-or-below", 5);
        plus("cook_professional", "food", bonusFood);
        plus("cook_sharp_knife", "pct", Txt.pct(swordBonus));
        minus("cook_gourmet");
        minus("cook_sugar_crash", "food", crashFood);
    }

    @Override public String color() { return "#f08a5d"; }
    @Override public Material icon() { return Material.CAMPFIRE; }
    @Override public int impact() { return 1; }

    @Override
    public void tick(Player p, int tick) {
        if (p.getFoodLevel() <= crashFood) effect(p, PotionEffectType.WEAKNESS, 0);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent e) {
        Player p = e.getPlayer();
        if (!has(p)) return;
        Foods.bonus(plugin, p, e.getItem(), 1, bonusFood);
        if (Foods.RAW.contains(e.getItem().getType())) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 0, false, false, true));
            lang().bar(p, "origins.cook.msg.raw");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (e.getCause() != DamageCause.ENTITY_ATTACK && e.getCause() != DamageCause.ENTITY_SWEEP_ATTACK) return;
        if (!(e.getDamager() instanceof Player p) || !has(p)) return;
        if (p.getInventory().getItemInMainHand().getType().name().endsWith("_SWORD")) e.setDamage(e.getDamage() * (1 + swordBonus));
    }
}
