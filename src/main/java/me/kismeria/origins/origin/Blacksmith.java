package me.kismeria.origins.origin;

import me.kismeria.origins.OriginsPlugin;
import me.kismeria.origins.core.Fx;
import me.kismeria.origins.core.Origin;
import me.kismeria.origins.core.Txt;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class Blacksmith extends Origin {
    private final double enchantChance;
    private final double axeBonus;
    private final Map<UUID, Long> lastWarn = new HashMap<>();

    public Blacksmith(OriginsPlugin plugin) {
        super(plugin, "blacksmith");
        enchantChance = num("craft-enchant-chance", 0.2);
        axeBonus = num("axe-damage-bonus", 0.1);
        plus("blacksmith_enchanter", "pct", Txt.pct(enchantChance));
        plus("blacksmith_heavy_strike", "pct", Txt.pct(axeBonus));
        minus("blacksmith_iron_giant");
        minus("blacksmith_clumsy_hands");
    }

    @Override public String color() { return "#d9773a"; }
    @Override public Material icon() { return Material.ANVIL; }
    @Override public int impact() { return 2; }

    @Override
    public void tick(Player p, int tick) {
        if (p.isInWater()) effect(p, PotionEffectType.SLOWNESS, 1);
    }

    @Override
    public void reset(Player p) {
        lastWarn.remove(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !has(p)) return;
        ItemStack result = e.getCurrentItem();
        if (result == null || result.isEmpty() || result.getMaxStackSize() > 1) return;
        if (!e.isShiftClick() && !e.getCursor().isEmpty()) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (r.nextDouble() >= enchantChance) return;
        ItemStack copy = result.clone();
        if (!ForgeEnchanter.enchant(copy, r)) return;
        e.setCurrentItem(copy);
        lang().send(p, "origins.blacksmith.msg.enchanted");
        Fx.particle(p.getLocation().add(0, 1, 0), Particle.ENCHANT, 300, 1, 1, 1, 1);
        Fx.particle(p.getLocation().add(0, 1, 0), Particle.LAVA, 6, 0.4, 0.3, 0.4, 0);
        Fx.sound(p, "minecraft:block.enchantment_table.use", 1f, 1f);
        Fx.sound(p, "minecraft:block.anvil.use", 0.5f, 1.4f);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (e.getCause() != DamageCause.ENTITY_ATTACK && e.getCause() != DamageCause.ENTITY_SWEEP_ATTACK) return;
        if (!(e.getDamager() instanceof Player p) || !has(p)) return;
        if (p.getInventory().getItemInMainHand().getType().name().endsWith("_AXE")) e.setDamage(e.getDamage() * (1 + axeBonus));
    }

    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player p) || !has(p)) return;
        e.setCancelled(true);
        warn(p);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDraw(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = e.getItem();
        if (item == null || (item.getType() != Material.BOW && item.getType() != Material.CROSSBOW)) return;
        if (!has(e.getPlayer())) return;
        e.setUseItemInHand(Event.Result.DENY);
        warn(e.getPlayer());
    }

    private void warn(Player p) {
        long now = System.currentTimeMillis();
        Long last = lastWarn.get(p.getUniqueId());
        if (last != null && now - last < 1500) return;
        lastWarn.put(p.getUniqueId(), now);
        lang().bar(p, "origins.blacksmith.msg.clumsy");
    }
}
