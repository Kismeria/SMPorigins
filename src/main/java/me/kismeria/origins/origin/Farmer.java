package me.kismeria.origins.origin;

import me.kismeria.origins.OriginsPlugin;
import me.kismeria.origins.core.Foods;
import me.kismeria.origins.core.Fx;
import me.kismeria.origins.core.Origin;
import me.kismeria.origins.core.Txt;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class Farmer extends Origin {
    private static final Set<Material> CROPS = EnumSet.of(Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS);
    private static final BlockFace[] SIDES = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

    private final double doubleChance;
    private final double plantMult;
    private final int darkLight;

    public Farmer(OriginsPlugin plugin) {
        super(plugin, "farmer");
        doubleChance = num("double-harvest-chance", 0.6);
        plantMult = num("plant-food-multiplier", 1.5);
        darkLight = integer("weak-below-light", 7);
        plus("farmer_green_thumb", "pct", Txt.pct(doubleChance));
        plus("farmer_hearty_harvest", "mult", Txt.num(plantMult));
        minus("farmer_vegetarian");
        minus("farmer_sun_lover", "light", darkLight);
    }

    @Override public String color() { return "#9bd35a"; }
    @Override public Material icon() { return Material.WHEAT; }
    @Override public int impact() { return 1; }

    @Override
    public void tick(Player p, int tick) {
        if (p.getLocation().getBlock().getLightLevel() < darkLight) effect(p, PotionEffectType.WEAKNESS, 0);
    }

    /** Ripe crop, ripe berry bush, or a melon/pumpkin grown from a stem (not player-placed). */
    private static boolean harvestable(Block b) {
        Material t = b.getType();
        if (CROPS.contains(t)) return b.getBlockData() instanceof Ageable a && a.getAge() >= a.getMaximumAge();
        if (t == Material.SWEET_BERRY_BUSH) return b.getBlockData() instanceof Ageable a && a.getAge() >= 2;
        if (t == Material.MELON || t == Material.PUMPKIN) {
            Material stem = t == Material.MELON ? Material.ATTACHED_MELON_STEM : Material.ATTACHED_PUMPKIN_STEM;
            for (BlockFace f : SIDES) {
                Block s = b.getRelative(f);
                if (s.getType() == stem && s.getBlockData() instanceof Directional d && d.getFacing() == f.getOppositeFace()) return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();
        if (!e.isDropItems() || !has(p) || !harvestable(b)) return;
        if (ThreadLocalRandom.current().nextDouble() >= doubleChance) return;
        ItemStack tool = p.getInventory().getItemInMainHand();
        for (ItemStack drop : b.getDrops(tool, p)) b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.3, 0.5), drop);
        Fx.particle(b.getLocation().add(0.5, 0.5, 0.5), Particle.HAPPY_VILLAGER, 5, 0.3, 0.3, 0.3, 0);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHoe(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        ItemStack hoe = p.getInventory().getItemInMainHand();
        Block center = e.getClickedBlock();
        if (center == null || !hoe.getType().name().endsWith("_HOE") || !has(p)) return;
        Material t = center.getType();
        if (!CROPS.contains(t) && t != Material.MELON && t != Material.PUMPKIN) return;
        e.setCancelled(true);
        int broken = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block b = center.getRelative(dx, 0, dz);
                if (b.getType() != Material.SWEET_BERRY_BUSH && harvestable(b) && p.breakBlock(b)) broken++;
            }
        }
        if (broken == 0) return;
        p.getInventory().getItemInMainHand().damage(broken, p);
        p.swingMainHand();
        Fx.particle(center.getLocation().add(0.5, 1, 0.5), Particle.HAPPY_VILLAGER, 20, 1.5, 0.5, 1.5, 0);
        Fx.sound(center.getLocation(), "minecraft:item.crop.plant", 1f, 1f);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent e) {
        Player p = e.getPlayer();
        Material m = e.getItem().getType();
        if (!has(p)) return;
        if (Foods.PLANT.contains(m) && m != Material.BREAD && m != Material.GOLDEN_CARROT) {
            Foods.bonus(plugin, p, e.getItem(), plantMult, 0);
        } else if (Foods.MEAT.contains(m)) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 0, false, false, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 100, 0, false, false, true));
            lang().bar(p, "origins.farmer.msg.meat");
        }
    }
}
