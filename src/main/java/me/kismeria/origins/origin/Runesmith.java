package me.kismeria.origins.origin;

import io.papermc.paper.entity.TeleportFlag;
import me.kismeria.origins.OriginsPlugin;
import me.kismeria.origins.core.Fx;
import me.kismeria.origins.core.Lang;
import me.kismeria.origins.core.Meters;
import net.kyori.adventure.text.Component;
import me.kismeria.origins.core.Origin;
import me.kismeria.origins.core.Txt;
import me.kismeria.origins.gui.Menu;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Port of runesmith.sk.
 * Charge: book in the main hand + sneaking fills a rune bar (0..120) for health and XP; the bar keeps its value.
 * Full bar: Shift+RMB with an enchanted item opens a menu, the chosen enchantment goes into a book.
 * Fusion: two equal one-enchantment books in both hands + LMB = one level higher.
 * While the rune is full the Runesmith coughs blood. Once a day an extraction summons the ancestor's echo.
 */
public final class Runesmith extends Origin {
    private static final double FULL = 120;
    private static final Particle.DustOptions BLOOD_DRIP = new Particle.DustOptions(Color.fromRGB(115, 5, 5), 1.1f);
    private static final Particle.DustOptions BLOOD_SPRAY = new Particle.DustOptions(Color.fromRGB(115, 0, 0), 1.0f);
    private static final Particle.DustOptions BLOOD_DROP = new Particle.DustOptions(Color.fromRGB(128, 0, 0), 1.4f);
    private static final Particle.DustOptions BLOOD_POOL = new Particle.DustOptions(Color.fromRGB(77, 0, 0), 2.5f);

    private final double fullHp;
    private final double fullXp;
    private final int chargeSeconds;
    private final double altarSpeed;
    private final double altarCost;
    private final double minHealth;
    private final int coughInterval;
    private final double coughDamage;
    private final int fusionMax;
    private final boolean ancestorEcho;
    private final boolean noTable;

    private final NamespacedKey chargeKey;
    private final NamespacedKey debtKey;
    private final NamespacedKey ancestorKey;
    private final Map<UUID, Double> angle = new HashMap<>();
    private final Set<UUID> fullAnnounced = new HashSet<>();
    private final Map<UUID, Integer> coughTimer = new HashMap<>();
    private final Set<UUID> coughing = new HashSet<>();
    private final Set<Allay> ghosts = new HashSet<>();
    /** Why charging stopped (lang key), shown above the hearts. */
    private final Map<UUID, String> blocked = new HashMap<>();
    private final BukkitTask chargeTask;
    private final BukkitTask secondTask;

    public Runesmith(OriginsPlugin plugin) {
        super(plugin, "runesmith");
        fullHp = num("full-charge-health", 24);
        fullXp = num("full-charge-xp-levels", 14);
        chargeSeconds = Math.max(1, integer("charge-seconds", 6));
        altarSpeed = num("altar-speed-multiplier", 2);
        altarCost = num("altar-cost-multiplier", 0.45);
        minHealth = num("min-health-left", 1);
        coughInterval = Math.max(1, integer("cough-interval-seconds", 10));
        coughDamage = num("cough-damage", 1);
        fusionMax = integer("fusion-max-level", 5);
        ancestorEcho = flag("ancestor-echo", true);
        noTable = flag("no-enchanting-table", true);
        chargeKey = new NamespacedKey(plugin, "rune_charge");
        debtKey = new NamespacedKey(plugin, "rune_xpdebt");
        ancestorKey = new NamespacedKey(plugin, "rune_ancestor_day");

        plus("rune_charge", "hp", Txt.num(fullHp), "xp", Txt.num(fullXp), "seconds", chargeSeconds);
        plus("rune_extract");
        plus("rune_fusion", "max", fusionMax);
        plus("rune_altar", "speed", Txt.num(altarSpeed), "pct", Txt.pct(1 - altarCost / altarSpeed));
        minus("rune_cough", "seconds", coughInterval);
        if (noTable) minus("rune_no_table");

        chargeTask = Bukkit.getScheduler().runTaskTimer(plugin, this::chargeStep, 2L, 2L);
        secondTask = Bukkit.getScheduler().runTaskTimer(plugin, this::everySecond, 20L, 20L);
    }

    @Override public String color() { return "#a970ff"; }
    @Override public Material icon() { return Material.BOOK; }
    @Override public int impact() { return 2; }

    // --- charge storage ---

    private double charge(Player p) {
        return p.getPersistentDataContainer().getOrDefault(chargeKey, PersistentDataType.DOUBLE, 0.0);
    }

    private void setCharge(Player p, double v) {
        p.getPersistentDataContainer().set(chargeKey, PersistentDataType.DOUBLE, v);
    }

    private boolean full(Player p) {
        return charge(p) >= FULL;
    }

    // --- bar (every 10 ticks) ---

    private boolean charging(Player p) {
        return p.isSneaking() && p.getInventory().getItemInMainHand().getType() == Material.BOOK
                && !blocked.containsKey(p.getUniqueId());
    }

    /** Rune charge above the hunger bar while a book or an enchanted item is in hand. */
    @Override
    public Component hudBar(Player p) {
        ItemStack tool = p.getInventory().getItemInMainHand();
        if (tool.getType() != Material.BOOK && tool.getEnchantments().isEmpty()) return null;
        double c = charge(p);
        if (c >= FULL) return Meters.shimmer("#8a3df0", "#ffffff", Bukkit.getCurrentTick() / 2);
        return Meters.bar(c / FULL, "#6a2fc0", "#e0a8ff", "#2a1f38", charging(p) ? "#ffffff" : null);
    }

    @Override
    public Component hudHint(Player p) {
        ItemStack tool = p.getInventory().getItemInMainHand();
        boolean book = tool.getType() == Material.BOOK;
        boolean enchanted = !tool.getEnchantments().isEmpty();
        if (!book && !enchanted) return null;
        String locale = Lang.localeOf(p);
        if (full(p)) return lang().get(locale, enchanted ? "origins.runesmith.bar.full-item" : "origins.runesmith.bar.full-book");
        String reason = blocked.get(p.getUniqueId());
        if (reason != null && p.isSneaking() && book) return lang().get(locale, reason);
        if (!book) return null;
        if (!p.isSneaking()) return lang().get(locale, "origins.runesmith.bar.idle");
        return lang().get(locale, plugin.altars().isOnAltar(p) ? "origins.runesmith.bar.charging-altar" : "origins.runesmith.bar.charging");
    }

    // --- charging (every 2 ticks) ---

    private void chargeStep() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!has(p) || p.isDead()) continue;
            double c = charge(p);
            if (c >= FULL) {
                Fx.particle(p.getLocation().add(0, 1, 0), Particle.ENCHANT, 4, 0.5, 0.7, 0.5, 0.02);
                continue;
            }
            if (!p.isSneaking() || p.getInventory().getItemInMainHand().getType() != Material.BOOK) {
                blocked.remove(p.getUniqueId());
                continue;
            }

            int steps = chargeSeconds * 10;
            double step = FULL / steps;
            double hpCost = fullHp / steps;
            double xpCost = fullXp / steps;
            if (plugin.altars().isOnAltar(p)) {
                hpCost *= altarCost;
                xpCost *= altarCost;
                step *= altarSpeed;
            }
            boolean creative = p.getGameMode() == GameMode.CREATIVE;
            if (!creative && p.getHealth() - hpCost < minHealth) {
                blocked.put(p.getUniqueId(), "origins.runesmith.msg.no-health");
                continue;
            }
            PersistentDataContainer pdc = p.getPersistentDataContainer();
            double debt = pdc.getOrDefault(debtKey, PersistentDataType.DOUBLE, 0.0) + (creative ? 0 : xpCost);
            if (debt >= 1 && p.getLevel() < 1) {
                blocked.put(p.getUniqueId(), "origins.runesmith.msg.no-xp");
                continue;
            }
            blocked.remove(p.getUniqueId());
            if (!creative) p.setHealth(Math.max(0.5, p.getHealth() - hpCost));
            while (debt >= 1 && p.getLevel() >= 1) {
                p.setLevel(p.getLevel() - 1);
                debt -= 1;
            }
            pdc.set(debtKey, PersistentDataType.DOUBLE, debt);
            double next = Math.min(FULL, c + step);
            setCharge(p, next);
            chargeVisuals(p, next);
            if (next >= FULL && fullAnnounced.add(p.getUniqueId())) fullBurst(p);
        }
    }

    private void chargeVisuals(Player p, double c) {
        double a = (angle.getOrDefault(p.getUniqueId(), 0.0) + 22) % 360;
        angle.put(p.getUniqueId(), a);
        Location l = p.getLocation();
        double y = l.getY() + 1.1;
        double r1 = Math.toRadians(a), r2 = Math.toRadians(a + 180);
        Fx.particle(p.getWorld(), l.getX() + 1.2 * Math.cos(r1), y, l.getZ() + 1.2 * Math.sin(r1), Particle.WITCH, 1, 0, 0);
        Fx.particle(p.getWorld(), l.getX() + 1.2 * Math.cos(r2), y, l.getZ() + 1.2 * Math.sin(r2), Particle.SOUL_FIRE_FLAME, 1, 0, 0);
        Fx.particle(p.getWorld(), l.getX() + 1.2 * Math.cos(r1), y, l.getZ() + 1.2 * Math.sin(r1), Particle.END_ROD, 1, 0, 0);
        Fx.particle(l.clone().add(0, 1, 0), Particle.ENCHANT, 5, 0.3, 0.6, 0.3, 0.06);
        if ((int) c % 20 == 0) Fx.sound(p, "minecraft:block.amethyst_block.resonate", 0.6f, (float) (0.6 + c / FULL));
    }

    private void fullBurst(Player p) {
        Location c = p.getLocation().add(0, 1, 0);
        Fx.particle(c.clone().add(0, 0.2, 0), Particle.FIREWORK, 20, 0.2, 0.2, 0.2, 0.05);
        Fx.particle(c, Particle.WITCH, 100, 1.4, 1, 1.4, 0.15);
        Fx.particle(c, Particle.ENCHANT, 80, 1, 1, 1, 1);
        Fx.particle(c, Particle.END_ROD, 50, 0.5, 0.5, 0.5, 0.4);
        Fx.particle(c, Particle.SOUL_FIRE_FLAME, 30, 0.6, 0.7, 0.6, 0.1);
        Fx.sound(p, "minecraft:block.beacon.activate", 1f, 1.4f);
        Fx.sound(p, "minecraft:block.conduit.activate", 1f, 1.2f);
        Fx.sound(p, "minecraft:block.amethyst_block.resonate", 1f, 1.8f);
        Fx.sound(p, "minecraft:entity.illusioner.prepare_mirror", 0.7f, 1.4f);
        lang().bar(p, "origins.runesmith.bar.full");
    }

    // --- blood cough (every second while the rune is full) ---

    private void everySecond() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!has(p) || p.isDead()) continue;
            UUID id = p.getUniqueId();
            if (!full(p)) {
                coughTimer.remove(id);
                fullAnnounced.remove(id);
                continue;
            }
            if (coughing.contains(id)) continue;
            if (coughTimer.merge(id, 1, Integer::sum) >= coughInterval) {
                coughTimer.put(id, 0);
                cough(p);
            }
        }
    }

    private void cough(Player p) {
        UUID id = p.getUniqueId();
        coughing.add(id);
        if (p.getHealth() > coughDamage && p.getGameMode() != GameMode.CREATIVE) p.damage(coughDamage);
        Location at = p.getLocation();
        Fx.sound(at, "minecraft:entity.warden.heartbeat", 0.8f, 1.4f);
        Fx.sound(at, "minecraft:entity.frog.eat", 1f, 0.5f);
        Fx.sound(at, "minecraft:block.mud.break", 0.9f, 0.6f);
        lang().bar(p, "origins.runesmith.msg.cough");
        p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 60, 0, false, false, true));
        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                t++;
                if (!p.isOnline() || p.isDead()) {
                    coughing.remove(id);
                    cancel();
                    return;
                }
                if (t > 30) {
                    Fx.particle(p.getLocation().add(0, 0.05, 0), Particle.DUST, 25, 0.5, 0.02, 0.5, 0, BLOOD_POOL);
                    coughing.remove(id);
                    cancel();
                    return;
                }
                // head forced down, only the pitch changes
                Location look = p.getLocation();
                look.setPitch(70);
                p.teleport(look, TeleportFlag.Relative.X, TeleportFlag.Relative.Y, TeleportFlag.Relative.Z,
                        TeleportFlag.Relative.YAW, TeleportFlag.Relative.VELOCITY_X, TeleportFlag.Relative.VELOCITY_Y,
                        TeleportFlag.Relative.VELOCITY_Z);
                Fx.particle(mouth(p, 0.35, 1.35), Particle.DUST, 3, 0.05, 0.03, 0.05, 0, BLOOD_DRIP);
                if (t % 3 == 1) {
                    Fx.particle(mouth(p, 0.5, 1.4), Particle.DUST, 14, 0.12, 0.08, 0.14, 0.4, BLOOD_SPRAY);
                    Fx.particle(mouth(p, 0.5, 1.4), Particle.DUST, 8, 0.1, 0.06, 0.12, 0.05, BLOOD_DROP);
                }
                if (t == 12) {
                    Fx.sound(p.getLocation(), "minecraft:entity.frog.eat", 1f, 0.45f);
                    Fx.sound(p.getLocation(), "minecraft:block.mud.break", 0.8f, 0.5f);
                    Fx.particle(mouth(p, 0.6, 1.4), Particle.DUST, 24, 0.16, 0.1, 0.18, 0.5, BLOOD_SPRAY);
                    Fx.particle(mouth(p, 0.6, 1.4), Particle.DUST, 14, 0.14, 0.1, 0.16, 0.08, BLOOD_DROP);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Point in front of the face (yaw only), like ^ ^y ^forward. */
    private static Location mouth(Player p, double forward, double up) {
        Location l = p.getLocation();
        double yaw = Math.toRadians(l.getYaw());
        return l.add(-Math.sin(yaw) * forward, up, Math.cos(yaw) * forward);
    }

    // --- extraction ---

    @EventHandler(priority = EventPriority.HIGH)
    public void onRightClick(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action a = e.getAction();
        Player p = e.getPlayer();
        if (a == Action.RIGHT_CLICK_BLOCK && noTable && e.getClickedBlock() != null
                && e.getClickedBlock().getType() == Material.ENCHANTING_TABLE && has(p)) {
            e.setCancelled(true);
            lang().bar(p, "origins.runesmith.msg.table");
            Fx.sound(p, "minecraft:block.note_block.bass", 0.6f, 0.6f);
            return;
        }
        if (a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK) {
            if (has(p) && fuse(p)) e.setCancelled(true);
            return;
        }
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        if (!p.isSneaking() || !has(p) || !full(p)) return;
        ItemStack tool = p.getInventory().getItemInMainHand();
        if (tool.getEnchantments().isEmpty()) return;
        e.setCancelled(true);
        if (!p.getInventory().contains(Material.BOOK)) {
            lang().send(p, "origins.runesmith.msg.need-book");
            return;
        }
        ItemStack snapshot = tool.clone();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) new RuneMenu(p, snapshot).open(p);
        });
    }

    /** One-row menu: one book per enchantment of the held item. */
    private final class RuneMenu extends Menu {
        private final ItemStack snapshot;
        private final List<Map.Entry<Enchantment, Integer>> entries = new ArrayList<>();

        RuneMenu(Player p, ItemStack snapshot) {
            super(1, plugin.lang().get(Lang.localeOf(p), "origins.runesmith.menu-title"));
            this.snapshot = snapshot;
            entries.addAll(snapshot.getEnchantments().entrySet());
            for (int i = 0; i < entries.size() && i < 9; i++) {
                Map.Entry<Enchantment, Integer> en = entries.get(i);
                ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
                book.editMeta(EnchantmentStorageMeta.class, m -> m.addStoredEnchant(en.getKey(), en.getValue(), true));
                inventory.setItem(i, book);
            }
            Fx.sound(p, "minecraft:block.enchantment_table.use", 0.8f, 1.2f);
        }

        @Override
        public void onClick(Player p, int slot, InventoryClickEvent e) {
            if (slot < 0 || slot >= entries.size()) return;
            Map.Entry<Enchantment, Integer> chosen = entries.get(slot);
            // closing inside the click event is unsafe: next tick
            Bukkit.getScheduler().runTask(plugin, () -> {
                p.closeInventory();
                ItemStack tool = p.getInventory().getItemInMainHand();
                // the held item must still be the one the menu was opened for
                if (!tool.isSimilar(snapshot) || !full(p) || !p.getInventory().contains(Material.BOOK)) return;
                extract(p, tool, chosen.getKey(), chosen.getValue());
            });
        }
    }

    private void extract(Player p, ItemStack tool, Enchantment ench, int level) {
        tool.removeEnchantment(ench);
        p.getInventory().removeItem(new ItemStack(Material.BOOK, 1));
        ItemStack out = new ItemStack(Material.ENCHANTED_BOOK);
        out.editMeta(EnchantmentStorageMeta.class, m -> m.addStoredEnchant(ench, level, true));
        for (ItemStack left : p.getInventory().addItem(out).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);

        setCharge(p, 0);
        fullAnnounced.remove(p.getUniqueId());
        coughTimer.remove(p.getUniqueId());
        lang().send(p, "origins.runesmith.msg.extracted");
        extractVisuals(p);
        if (ancestorEcho) maybeAncestor(p);
    }

    private void extractVisuals(Player p) {
        Location l = p.getLocation();
        for (int i = 1; i <= 3; i++) Fx.particle(l.clone().add(0, i * 0.7, 0), Particle.ENCHANT, 25, 0.3, 0.1, 0.3, 0.3);
        for (int i = 1; i <= 16; i++) {
            double a = Math.toRadians(i * 22.5);
            Location ring = l.clone().add(1.6 * Math.cos(a), 1, 1.6 * Math.sin(a));
            Fx.particle(ring, Particle.WITCH, 4, 0, 0.1, 0, 0.05);
            Fx.particle(ring, Particle.END_ROD, 1, 0, 0.05, 0, 0.02);
        }
        Location c = l.clone().add(0, 1.2, 0);
        Fx.particle(c, Particle.FIREWORK, 20, 0.2, 0.2, 0.2, 0.05);
        Fx.particle(c, Particle.TOTEM_OF_UNDYING, 50, 0.5, 0.6, 0.5, 0.4);
        Fx.particle(c.clone().subtract(0, 0.2, 0), Particle.SOUL_FIRE_FLAME, 20, 0.4, 0.5, 0.4, 0.05);
        Fx.sound(p, "minecraft:block.beacon.deactivate", 1f, 1.5f);
        Fx.sound(p, "minecraft:block.enchantment_table.use", 1.2f, 0.8f);
        Fx.sound(p, "minecraft:entity.illusioner.cast_spell", 0.9f, 1.3f);
        Fx.sound(p, "minecraft:item.totem.use", 0.4f, 1.6f);
        Fx.sound(p, "minecraft:block.amethyst_block.resonate", 1f, 2f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            Fx.sound(p, "minecraft:entity.experience_orb.pickup", 1f, 0.7f);
            Fx.sound(p, "minecraft:block.amethyst_block.chime", 1f, 1.4f);
        }, 4L);
    }

    // --- ancestor's echo, once a day ---

    public void resetAncestor(Player p) {
        p.getPersistentDataContainer().remove(ancestorKey);
    }

    private void maybeAncestor(Player p) {
        long today = System.currentTimeMillis() / 86_400_000L;
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        Long last = pdc.get(ancestorKey, PersistentDataType.LONG);
        if (last != null && last == today) return;
        pdc.set(ancestorKey, PersistentDataType.LONG, today);

        String locale = Lang.localeOf(p);
        List<String> phrases = lang().rawList(locale, "origins.runesmith.ancestor.phrases");
        String phrase = phrases.isEmpty() ? "..." : phrases.get(ThreadLocalRandom.current().nextInt(phrases.size()));
        Location g = p.getLocation();

        Fx.sound(p, "minecraft:ambient.cave", 1f, 0.5f);
        Fx.sound(p, "minecraft:block.soul_sand.break", 1f, 0.6f);
        new BukkitRunnable() {
            int t;
            Allay ghost;
            double ang;
            double ex, ez;

            @Override
            public void run() {
                t++;
                if (!p.isOnline()) {
                    finish();
                    return;
                }
                if (t <= 10) {
                    // the soul rises from the ground
                    Location r = g.clone().add(0, t * 0.22, 0);
                    Fx.particle(r, Particle.SOUL, 8, 0.12, 0.05, 0.12, 0.01);
                    Fx.particle(r, Particle.SOUL_FIRE_FLAME, 2, 0.05, 0.05, 0.05, 0);
                    return;
                }
                if (t == 11) {
                    Fx.particle(g.clone().add(0, 1.6, 0), Particle.FIREWORK, 20, 0.2, 0.2, 0.2, 0.05);
                    Fx.sound(p, "minecraft:entity.vex.ambient", 1f, 0.5f);
                    Fx.sound(p, "minecraft:block.soul_soil.break", 1f, 0.7f);
                    lang().send(p, "origins.runesmith.ancestor.rise");
                    ghost = p.getWorld().spawn(p.getLocation().add(1.8, 1, 0), Allay.class, al -> {
                        al.setInvulnerable(true);
                        al.setAI(false);
                        al.setSilent(true);
                        al.setPersistent(false);
                        al.setCanPickupItems(false);
                        al.customName(lang().get(locale, "origins.runesmith.ancestor.name"));
                        al.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 0, false, false));
                        al.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 200, 0, false, false));
                    });
                    ghosts.add(ghost);
                    return;
                }
                if (t <= 83) {
                    // two slow circles around the player
                    ang += 10;
                    Location c = p.getLocation();
                    double rad = Math.toRadians(ang);
                    double y = c.getY() + 1 + 0.3 * Math.sin(rad * 2);
                    Location pos = new Location(c.getWorld(), c.getX() + 1.8 * Math.cos(rad), y, c.getZ() + 1.8 * Math.sin(rad));
                    if (ghost.isValid()) ghost.teleport(pos);
                    Fx.particle(pos, Particle.SOUL_FIRE_FLAME, 4, 0.1, 0.15, 0.1, 0.005);
                    Fx.particle(pos, Particle.ENCHANT, 4, 0.15, 0.2, 0.15, 0.02);
                    if (t == 41) {
                        p.sendMessage(Lang.mm("<gray><i>«<dark_gray><i>" + phrase + "<gray><i>»"));
                        Fx.sound(p, "minecraft:block.beacon.ambient", 0.6f, 0.5f);
                    }
                    if (t == 83) {
                        Fx.sound(p, "minecraft:entity.vex.death", 0.8f, 0.7f);
                        Fx.sound(p, "minecraft:block.amethyst_block.resonate", 0.7f, 0.5f);
                        ex = ghost.getLocation().getX();
                        ez = ghost.getLocation().getZ();
                    }
                    return;
                }
                if (t <= 91) {
                    // flies up and fades
                    Location up = new Location(p.getWorld(), ex, p.getLocation().getY() + 1 + (t - 83) * 0.35, ez);
                    if (ghost.isValid()) ghost.teleport(up);
                    Fx.particle(up, Particle.SOUL_FIRE_FLAME, 8, 0.2, 0.1, 0.2, 0.02);
                    Fx.particle(up, Particle.ENCHANT, 5, 0.15, 0.1, 0.15, 0.02);
                    if (t == 91) Fx.particle(up.clone().add(0, 0.3, 0), Particle.FIREWORK, 20, 0.2, 0.2, 0.2, 0.05);
                    return;
                }
                finish();
            }

            private void finish() {
                if (ghost != null) {
                    ghost.remove();
                    ghosts.remove(ghost);
                }
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // --- fusion ---

    /** Two equal single-enchantment books in both hands + LMB: one book a level higher. */
    private boolean fuse(Player p) {
        ItemStack main = p.getInventory().getItemInMainHand();
        ItemStack off = p.getInventory().getItemInOffHand();
        if (main.getType() != Material.ENCHANTED_BOOK || off.getType() != Material.ENCHANTED_BOOK) return false;
        if (!(main.getItemMeta() instanceof EnchantmentStorageMeta mm) || !(off.getItemMeta() instanceof EnchantmentStorageMeta om)) return false;
        if (mm.getStoredEnchants().size() != 1 || om.getStoredEnchants().size() != 1) return false;
        Map.Entry<Enchantment, Integer> a = mm.getStoredEnchants().entrySet().iterator().next();
        Map.Entry<Enchantment, Integer> b = om.getStoredEnchants().entrySet().iterator().next();
        if (a.getValue() >= fusionMax) {
            lang().send(p, "origins.runesmith.msg.fusion-max");
            return true;
        }
        if (!a.getKey().equals(b.getKey()) || !a.getValue().equals(b.getValue())) {
            lang().send(p, "origins.runesmith.msg.fusion-mismatch");
            return true;
        }
        int lvl = a.getValue() + 1;
        main.subtract();
        off.subtract();
        ItemStack result = new ItemStack(Material.ENCHANTED_BOOK);
        result.editMeta(EnchantmentStorageMeta.class, m -> m.addStoredEnchant(a.getKey(), lvl, true));
        for (ItemStack left : p.getInventory().addItem(result).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        lang().send(p, "origins.runesmith.msg.fused", "level", lvl);
        Location c = p.getLocation().add(0, 1, 0);
        Fx.particle(c, Particle.ENCHANT, 50, 0.5, 0.6, 0.5, 1);
        Fx.particle(c, Particle.WITCH, 25, 0.4, 0.5, 0.4, 0.1);
        Fx.sound(p, "minecraft:block.enchantment_table.use", 1f, 0.9f);
        Fx.sound(p, "minecraft:block.amethyst_block.resonate", 1f, 1.5f);
        return true;
    }

    // --- lifecycle ---

    @Override
    public void reset(Player p) {
        UUID id = p.getUniqueId();
        angle.remove(id);
        blocked.remove(id);
        fullAnnounced.remove(id);
        coughTimer.remove(id);
        coughing.remove(id);
        if (p.getOpenInventory().getTopInventory().getHolder(false) instanceof RuneMenu) p.closeInventory();
    }

    @Override
    public void shutdown() {
        chargeTask.cancel();
        secondTask.cancel();
        for (Allay g : ghosts) g.remove();
        ghosts.clear();
    }
}
