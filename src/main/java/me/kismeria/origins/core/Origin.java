package me.kismeria.origins.core;

import me.kismeria.origins.OriginsPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Base of every origin. An origin is a listener; its handlers check {@link #has(Player)}.
 * Numbers come from config.yml (origins.&lt;id&gt;.*), texts from lang/&lt;locale&gt;.yml (origins.&lt;id&gt;.*).
 */
public abstract class Origin implements Listener {
    /** Editable config value, listed in the admin menu. */
    public record Param(String key, Object def) {
    }

    protected final OriginsPlugin plugin;
    private final String id;
    private final List<Ability> abilities = new ArrayList<>();
    private final List<Param> params = new ArrayList<>();
    private final Map<String, NamespacedKey> keys = new HashMap<>();

    protected Origin(OriginsPlugin plugin, String id) {
        this.plugin = plugin;
        this.id = id;
        plugin.getConfig().addDefault(path("enabled"), true);
    }

    public final String id() {
        return id;
    }

    /** Hex colour, e.g. "#9aa3b5". */
    public abstract String color();

    public abstract Material icon();

    /** 1 = easy, 2 = medium, 3 = hard. */
    public abstract int impact();

    public List<Ability> abilities() {
        return abilities;
    }

    public List<Param> params() {
        return params;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean(path("enabled"), true);
    }

    // --- texts ---

    public String name(String locale) {
        return plugin.lang().raw(locale, "origins." + id + ".name");
    }

    public String description(String locale) {
        return plugin.lang().raw(locale, "origins." + id + ".description");
    }

    public String coloredName(String locale) {
        return "<" + color() + ">" + name(locale) + "</" + color() + ">";
    }

    public Component title(String locale) {
        return Lang.mm("<" + color() + "><b>" + name(locale) + "</b>");
    }

    public String abilityTitle(String locale, Ability a) {
        return plugin.lang().raw(locale, "origins." + id + ".abilities." + a.id() + ".title");
    }

    public Component abilityText(String locale, Ability a) {
        Lang lang = plugin.lang();
        return Lang.mm(lang.raw(locale, "origins." + id + ".abilities." + a.id() + ".text"), lang.args(locale, a.args()));
    }

    // --- hooks ---

    /** Permanent stats (attribute modifiers). Called on assign and every second. */
    public void apply(Player p) {
    }

    /** Called every 10 ticks for players of this origin. {@code tick} counts these calls. */
    public void tick(Player p, int tick) {
    }

    /** Drop per-player state: origin removed, quit or death. */
    public void reset(Player p) {
    }

    /** Plugin disable or reload. */
    public void shutdown() {
    }

    /** Meter above the hunger bar (SmpCore action bar HUD), null = none. Called every 2 ticks. */
    public Component hudBar(Player p) {
        return null;
    }

    /** Text above the hearts when no message is shown, null = none. */
    public Component hudHint(Player p) {
        return null;
    }

    public boolean forbidsArmor(Material m) {
        return false;
    }

    /** Lang key of the "you can't wear this" message. */
    public String armorMessage() {
        return "origins." + id + ".armor";
    }

    // --- helpers for subclasses ---

    /** Pairs: placeholder, value, placeholder, value... */
    protected void plus(String abilityId, Object... args) {
        abilities.add(new Ability(abilityId, true, map(args)));
    }

    protected void minus(String abilityId, Object... args) {
        abilities.add(new Ability(abilityId, false, map(args)));
    }

    private static Map<String, Object> map(Object[] args) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) m.put(String.valueOf(args[i]), args[i + 1]);
        return m;
    }

    protected static Lang.Ref ref(String key) {
        return new Lang.Ref(key);
    }

    protected boolean has(Player p) {
        return plugin.manager().is(p, this) && OriginManager.active(p);
    }

    protected boolean has(Entity e) {
        return e instanceof Player p && has(p);
    }

    public String path(String key) {
        return "origins." + id + "." + key;
    }

    protected double num(String key, double def) {
        FileConfiguration c = plugin.getConfig();
        c.addDefault(path(key), def);
        params.add(new Param(key, def));
        return c.getDouble(path(key), def);
    }

    protected int integer(String key, int def) {
        FileConfiguration c = plugin.getConfig();
        c.addDefault(path(key), def);
        params.add(new Param(key, def));
        return c.getInt(path(key), def);
    }

    protected boolean flag(String key, boolean def) {
        FileConfiguration c = plugin.getConfig();
        c.addDefault(path(key), def);
        params.add(new Param(key, def));
        return c.getBoolean(path(key), def);
    }

    protected NamespacedKey key(String name) {
        return keys.computeIfAbsent(name, n -> new NamespacedKey(plugin, id + "_" + n));
    }

    protected void modifier(Player p, Attribute a, String name, double amount, AttributeModifier.Operation op) {
        if (amount == 0) return;
        Attrs.set(p, a, key(name), amount, op);
    }

    /** Short racial effect, refreshed by the 10-tick ticker. */
    protected void effect(Player p, PotionEffectType type, int amplifier) {
        Fx.effect(p, type, amplifier, 30);
    }

    protected Lang lang() {
        return plugin.lang();
    }
}
