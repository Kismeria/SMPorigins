package me.kismeria.origins.origin;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.OminousBottleAmplifier;
import me.kismeria.origins.OriginsPlugin;
import me.kismeria.origins.core.Fx;
import me.kismeria.origins.core.Lang;
import me.kismeria.origins.core.Meters;
import net.kyori.adventure.text.Component;
import me.kismeria.origins.core.Origin;
import me.kismeria.origins.core.Txt;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class Alchemist extends Origin {
    private static final List<PotionType> RANDOM = List.of(
            PotionType.SWIFTNESS, PotionType.STRENGTH, PotionType.HEALING,
            PotionType.FIRE_RESISTANCE, PotionType.REGENERATION, PotionType.LEAPING);
    /** No vanilla LONG_ variant: extended through a custom effect. */
    private static final Set<PotionType> CUSTOM_LONG = EnumSet.of(
            PotionType.OOZING, PotionType.WEAVING, PotionType.WIND_CHARGED, PotionType.INFESTED);
    private static final Set<EntityPotionEffectEvent.Cause> POTION_CAUSES = EnumSet.of(
            EntityPotionEffectEvent.Cause.POTION_DRINK, EntityPotionEffectEvent.Cause.POTION_SPLASH,
            EntityPotionEffectEvent.Cause.AREA_EFFECT_CLOUD, EntityPotionEffectEvent.Cause.ARROW);

    private final long cooldownMs;
    private final double goodMult;
    private final double badMult;
    private final double health;
    private final NamespacedKey cdKey;
    private final NamespacedKey extendedKey;

    public Alchemist(OriginsPlugin plugin) {
        super(plugin, "alchemist");
        cooldownMs = integer("transmutation-cooldown-minutes", 30) * 60_000L;
        goodMult = num("beneficial-duration-multiplier", 1.5);
        badMult = num("harmful-duration-multiplier", 0.5);
        health = num("max-health-delta", -4);
        cdKey = new NamespacedKey(plugin, "alchemy_cd");
        extendedKey = new NamespacedKey(plugin, "alchemy_extended");
        plus("alchemist_extraction", "good", Txt.num(goodMult), "bad", Txt.num(1 / badMult));
        plus("alchemist_transmutation", "cd", cooldownMs / 60_000);
        minus("alchemist_dependency");
        minus("alchemist_frail", "hearts", Txt.hearts(health));
    }

    @Override public String color() { return "#c27ba0"; }
    @Override public Material icon() { return Material.BREWING_STAND; }
    @Override public int impact() { return 3; }

    @Override
    public void apply(Player p) {
        modifier(p, Attribute.MAX_HEALTH, "health", health, Operation.ADD_NUMBER);
    }

    // --- cooldown ---

    private long left(Player p) {
        Long until = p.getPersistentDataContainer().get(cdKey, PersistentDataType.LONG);
        return until == null ? 0 : Math.max(0, until - System.currentTimeMillis());
    }

    public void resetCooldown(Player p) {
        p.getPersistentDataContainer().remove(cdKey);
    }

    private boolean ready(Player p, boolean chat) {
        long l = left(p);
        if (l <= 0) return true;
        if (chat) lang().send(p, "origins.alchemist.msg.cooldown", "time", lang().duration(Lang.localeOf(p), l));
        return false;
    }

    private void used(Player p, String msgKey) {
        p.getPersistentDataContainer().set(cdKey, PersistentDataType.LONG, System.currentTimeMillis() + cooldownMs);
        if (msgKey != null) lang().send(p, msgKey);
        Fx.particle(p.getLocation().add(0, 1, 0), Particle.WITCH, 70, 1, 1, 1, 0.5);
        Fx.particle(p.getLocation().add(0, 1.2, 0), Particle.ENCHANT, 60, 0.6, 0.6, 0.6, 1);
        Fx.sound(p.getLocation(), "minecraft:block.brewing_stand.brew", 1f, 1f);
        Fx.sound(p.getLocation(), "minecraft:entity.illusioner.cast_spell", 0.5f, 1.4f);
    }

    // --- hints ---

    private static boolean holdsAlchemy(Player p) {
        Material t = p.getInventory().getItemInMainHand().getType();
        return t == Material.GLASS_BOTTLE || t == Material.OMINOUS_BOTTLE || isPotion(t);
    }

    /** Transmutation cooldown refilling; shimmering when ready. */
    @Override
    public Component hudBar(Player p) {
        if (!holdsAlchemy(p)) return null;
        long l = left(p);
        if (l > 0) return Meters.bar(1 - l / (double) cooldownMs, "#5a2a48", "#c27ba0", "#2a1f2a", null);
        return Meters.shimmer("#b0608f", "#ffd6f2", Bukkit.getCurrentTick() / 2);
    }

    @Override
    public Component hudHint(Player p) {
        if (!holdsAlchemy(p)) return null;
        String locale = Lang.localeOf(p);
        long l = left(p);
        if (l > 0) return lang().get(locale, "origins.alchemist.msg.cooldown-bar", "time", lang().duration(locale, l));
        ItemStack hand = p.getInventory().getItemInMainHand();
        Material t = hand.getType();
        String key;
        if (t == Material.GLASS_BOTTLE) key = "bottle";
        else if (t == Material.OMINOUS_BOTTLE) key = "ominous";
        else {
            boolean up = upgraded(hand, "STRONG_") != null;
            boolean ext = canExtend(hand);
            key = up && ext ? "both" : up ? "level" : ext ? "time" : "max";
        }
        return lang().get(locale, "origins.alchemist.hint." + key);
    }

    private static boolean isPotion(Material m) {
        return m == Material.POTION || m == Material.SPLASH_POTION || m == Material.LINGERING_POTION;
    }

    private static PotionType base(ItemStack item) {
        return item.getItemMeta() instanceof PotionMeta meta && meta.hasBasePotionType() ? meta.getBasePotionType() : null;
    }

    /** STRONG_x / LONG_x for a plain potion, or null. */
    private static PotionType upgraded(ItemStack item, String prefix) {
        PotionType type = base(item);
        if (type == null || type.name().startsWith("STRONG_") || type.name().startsWith("LONG_")) return null;
        try {
            return PotionType.valueOf(prefix + type.name());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private boolean canExtend(ItemStack item) {
        if (upgraded(item, "LONG_") != null) return true;
        PotionType type = base(item);
        return type != null && CUSTOM_LONG.contains(type);
    }

    // --- transmutation ---

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || e.getAction() == Action.PHYSICAL) return;
        Player p = e.getPlayer();
        if (!has(p)) return;
        ItemStack item = p.getInventory().getItemInMainHand();
        Material t = item.getType();
        boolean left = e.getAction() == Action.LEFT_CLICK_AIR || e.getAction() == Action.LEFT_CLICK_BLOCK;
        boolean shiftRight = !left && p.isSneaking();

        if (t == Material.GLASS_BOTTLE && left) {
            e.setCancelled(true);
            if (!ready(p, true)) return;
            item.subtract();
            PotionType pick = RANDOM.get(ThreadLocalRandom.current().nextInt(RANDOM.size()));
            ItemStack potion = new ItemStack(Material.POTION);
            potion.editMeta(PotionMeta.class, m -> m.setBasePotionType(pick));
            give(p, potion);
            used(p, "origins.alchemist.msg.random-potion");
        } else if (t == Material.GLASS_BOTTLE && shiftRight) {
            e.setCancelled(true);
            if (!ready(p, true)) return;
            item.subtract();
            ItemStack bottle = new ItemStack(Material.OMINOUS_BOTTLE);
            bottle.setData(DataComponentTypes.OMINOUS_BOTTLE_AMPLIFIER, OminousBottleAmplifier.amplifier(0));
            give(p, bottle);
            used(p, "origins.alchemist.msg.ominous");
        } else if (t == Material.OMINOUS_BOTTLE && shiftRight) {
            e.setCancelled(true);
            if (!ready(p, true)) return;
            OminousBottleAmplifier cur = item.getData(DataComponentTypes.OMINOUS_BOTTLE_AMPLIFIER);
            int amp = cur == null ? 0 : cur.amplifier();
            if (amp >= 4) {
                lang().send(p, "origins.alchemist.msg.ominous-max");
                return;
            }
            item.setData(DataComponentTypes.OMINOUS_BOTTLE_AMPLIFIER, OminousBottleAmplifier.amplifier(amp + 1));
            lang().send(p, "origins.alchemist.msg.ominous-up", "level", amp + 2);
            used(p, null);
        } else if (isPotion(t) && left) {
            e.setCancelled(true);
            if (!ready(p, true)) return;
            PotionType strong = upgraded(item, "STRONG_");
            if (strong == null) {
                lang().send(p, "origins.alchemist.msg.cant-level");
                return;
            }
            item.editMeta(PotionMeta.class, m -> m.setBasePotionType(strong));
            used(p, "origins.alchemist.msg.leveled");
        } else if (isPotion(t) && shiftRight) {
            e.setCancelled(true);
            if (!ready(p, true)) return;
            if (!extend(item)) {
                lang().send(p, "origins.alchemist.msg.cant-extend");
                return;
            }
            used(p, "origins.alchemist.msg.extended");
        }
    }

    private boolean extend(ItemStack item) {
        PotionType longType = upgraded(item, "LONG_");
        if (longType != null) {
            item.editMeta(PotionMeta.class, m -> m.setBasePotionType(longType));
            return true;
        }
        PotionType type = base(item);
        if (type == null || !CUSTOM_LONG.contains(type)) return false;
        // 3:00 -> 8:00 like vanilla long potions; keeps the vanilla name through custom_name
        item.editMeta(PotionMeta.class, m -> {
            Color color = m.computeEffectiveColor();
            List<PotionEffect> effects = type.getPotionEffects();
            m.setBasePotionType(null);
            for (PotionEffect fx : effects) m.addCustomEffect(fx.withDuration(fx.getDuration() * 8 / 3), true);
            m.setColor(color);
            m.setCustomPotionName(type.getKey().getKey().toLowerCase(Locale.ROOT));
            m.getPersistentDataContainer().set(extendedKey, PersistentDataType.BOOLEAN, true);
        });
        return true;
    }

    private static void give(Player p, ItemStack item) {
        for (ItemStack left : p.getInventory().addItem(item).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
    }

    // --- extraction ---

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEffect(EntityPotionEffectEvent e) {
        if (!(e.getEntity() instanceof Player p) || !POTION_CAUSES.contains(e.getCause())) return;
        if (e.getAction() != EntityPotionEffectEvent.Action.ADDED && e.getAction() != EntityPotionEffectEvent.Action.CHANGED) return;
        PotionEffect fx = e.getNewEffect();
        if (fx == null || fx.isInfinite() || fx.getType().isInstant() || fx.getDuration() < 20 || !has(p)) return;
        PotionEffectType.Category cat = fx.getType().getEffectCategory();
        double mult = cat == PotionEffectType.Category.BENEFICIAL ? goodMult : cat == PotionEffectType.Category.HARMFUL ? badMult : 1;
        if (mult == 1) return;
        e.setCancelled(true);
        PotionEffect changed = fx.withDuration(Math.max(1, (int) (fx.getDuration() * mult)));
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline() && !p.isDead()) p.addPotionEffect(changed);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onMilk(PlayerItemConsumeEvent e) {
        if (e.getItem().getType() == Material.MILK_BUCKET && has(e.getPlayer())) {
            e.setCancelled(true);
            lang().bar(e.getPlayer(), "origins.alchemist.msg.milk");
        }
    }
}
