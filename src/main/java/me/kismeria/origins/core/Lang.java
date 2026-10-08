package me.kismeria.origins.core;

import me.kismeria.smpcore.SmpCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Texts in the player's client language, same scheme as SmpCore:
 * plugins/SmpOrigins/lang/&lt;locale&gt;.yml, unknown language falls back to SmpCore's default-language.
 */
public final class Lang {
    private static final List<String> BUNDLED = List.of("ru_ru", "en_us");
    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** Placeholder value that is itself a lang key (resolved in the reader's language). */
    public record Ref(String key) {
    }

    private final JavaPlugin plugin;
    private final Map<String, YamlConfiguration> files = new HashMap<>();
    private volatile String fallback = "ru_ru";

    public Lang(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        Map<String, YamlConfiguration> loaded = new HashMap<>();
        File dir = new File(plugin.getDataFolder(), "lang");
        for (String name : BUNDLED) {
            File file = new File(dir, name + ".yml");
            if (!file.exists()) {
                plugin.saveResource("lang/" + name + ".yml", false);
                continue;
            }
            // texts changed in an update: replace the file, keep the old one as .old
            InputStream bundled = plugin.getResource("lang/" + name + ".yml");
            if (bundled == null) continue;
            int jarVersion = YamlConfiguration.loadConfiguration(new InputStreamReader(bundled, StandardCharsets.UTF_8)).getInt("version");
            if (YamlConfiguration.loadConfiguration(file).getInt("version") < jarVersion) {
                File old = new File(dir, name + ".yml.old");
                if (old.exists()) old.delete();
                file.renameTo(old);
                plugin.saveResource("lang/" + name + ".yml", true);
                plugin.getLogger().info("lang/" + name + ".yml обновлён до версии " + jarVersion + " (старый — " + old.getName() + ")");
            }
        }
        File[] list = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (list != null) {
            for (File file : list) {
                String name = file.getName().substring(0, file.getName().length() - 4).toLowerCase(Locale.ROOT);
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                // new keys from plugin updates come from the jar
                InputStream bundled = plugin.getResource("lang/" + name + ".yml");
                if (bundled != null) {
                    yaml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(bundled, StandardCharsets.UTF_8)));
                }
                loaded.put(name, yaml);
            }
        }
        synchronized (files) {
            files.clear();
            files.putAll(loaded);
        }
        String def = JavaPlugin.getPlugin(SmpCore.class).lang().fallback();
        fallback = loaded.containsKey(def) ? def : "ru_ru";
    }

    public static String localeOf(CommandSender sender) {
        return sender instanceof Player p ? p.locale().toString().toLowerCase(Locale.ROOT) : "ru_ru";
    }

    private YamlConfiguration file(String locale) {
        synchronized (files) {
            YamlConfiguration exact = files.get(locale);
            if (exact != null) return exact;
            String language = locale.contains("_") ? locale.substring(0, locale.indexOf('_')) : locale;
            for (Map.Entry<String, YamlConfiguration> e : files.entrySet()) {
                if (e.getKey().startsWith(language + "_")) return e.getValue();
            }
            return files.get(fallback);
        }
    }

    public String raw(String locale, String key) {
        YamlConfiguration yaml = file(locale);
        String value = yaml != null ? yaml.getString(key) : null;
        if (value == null) {
            YamlConfiguration def;
            synchronized (files) {
                def = files.get(fallback);
            }
            value = def != null ? def.getString(key) : null;
        }
        return value != null ? value : key;
    }

    public List<String> rawList(String locale, String key) {
        YamlConfiguration yaml = file(locale);
        List<String> list = yaml != null ? yaml.getStringList(key) : List.of();
        if (list.isEmpty()) {
            YamlConfiguration def;
            synchronized (files) {
                def = files.get(fallback);
            }
            if (def != null) list = def.getStringList(key);
        }
        return list;
    }

    /** Pairs: name, value, name, value... Values may be {@link Ref}. */
    public TagResolver args(String locale, Object... pairs) {
        List<TagResolver> list = new ArrayList<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            Object v = pairs[i + 1];
            String s = v instanceof Ref r ? raw(locale, r.key()) : v instanceof Double d ? Txt.num(d) : String.valueOf(v);
            list.add(Placeholder.parsed(String.valueOf(pairs[i]), s));
        }
        return TagResolver.resolver(list);
    }

    public TagResolver args(String locale, Map<String, Object> map) {
        List<Object> pairs = new ArrayList<>();
        map.forEach((k, v) -> {
            pairs.add(k);
            pairs.add(v);
        });
        return args(locale, pairs.toArray());
    }

    public static Component mm(String text, TagResolver... resolvers) {
        return MM.deserialize(text, resolvers).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public Component get(String locale, String key, Object... pairs) {
        return mm(raw(locale, key), args(locale, pairs));
    }

    public Component get(CommandSender s, String key, Object... pairs) {
        return get(localeOf(s), key, pairs);
    }

    public void send(CommandSender s, String key, Object... pairs) {
        String locale = localeOf(s);
        s.sendMessage(mm(raw(locale, "prefix") + raw(locale, key), args(locale, pairs)));
    }

    public void bar(Player p, String key, Object... pairs) {
        JavaPlugin.getPlugin(SmpCore.class).hud().message(p, get(p, key, pairs));
    }

    /** "1 мин 5 сек" in the reader's language. */
    public String duration(String locale, long ms) {
        long s = ms / 1000 + 1;
        return s >= 60 ? (s / 60) + raw(locale, "time.m") + " " + (s % 60) + raw(locale, "time.s") : s + raw(locale, "time.s");
    }
}
