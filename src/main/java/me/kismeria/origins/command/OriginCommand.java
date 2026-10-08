package me.kismeria.origins.command;

import me.kismeria.origins.OriginsPlugin;
import me.kismeria.origins.core.Lang;
import me.kismeria.origins.core.Origin;
import me.kismeria.origins.gui.RaceListMenu;
import me.kismeria.origins.gui.admin.OriginsAdminMenu;
import me.kismeria.origins.origin.Alchemist;
import me.kismeria.origins.origin.Runesmith;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class OriginCommand implements TabExecutor {
    private static final List<String> USER = List.of("info", "list");
    private static final List<String> ADMIN = List.of("set", "reset", "resetall", "cdreset", "runeghost", "altarstop", "reload", "menu");

    private final OriginsPlugin plugin;

    public OriginCommand(OriginsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Lang lang = plugin.lang();
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (ADMIN.contains(sub) && !sender.hasPermission("origins.admin")) {
            lang.send(sender, "command.no-permission");
            return true;
        }
        switch (sub) {
            case "", "info" -> {
                if (!(sender instanceof Player p)) return usage(sender);
                Origin own = plugin.manager().get(p);
                Origin o = args.length > 1 ? plugin.manager().byId(args[1]) : own;
                if (o == null) new RaceListMenu(plugin, p).open(p);
                else p.showDialog(plugin.dialogs().info(Lang.localeOf(p), o, o == own));
            }
            case "list" -> {
                if (sender instanceof Player p) {
                    new RaceListMenu(plugin, p).open(p);
                } else {
                    for (Origin o : plugin.manager().all()) sender.sendMessage(Lang.mm(o.id() + " — " + o.coloredName("ru_ru")));
                }
            }
            case "menu" -> {
                if (sender instanceof Player p) new OriginsAdminMenu(plugin, p).open();
            }
            case "set" -> {
                if (args.length < 3) return usage(sender);
                Player t = Bukkit.getPlayerExact(args[1]);
                Origin o = plugin.manager().byId(args[2]);
                if (t == null) lang.send(sender, "command.not-online");
                else if (o == null) lang.send(sender, "command.unknown-race");
                else {
                    plugin.manager().set(t, o, false);
                    plugin.manager().greet(t);
                    lang.send(sender, "command.set", "player", t.getName(), "race", o.coloredName(Lang.localeOf(sender)));
                }
            }
            case "reset" -> {
                if (args.length < 2) return usage(sender);
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null) {
                    lang.send(sender, "command.not-online");
                    return true;
                }
                plugin.manager().reroll(t);
                Origin o = plugin.manager().get(t);
                lang.send(sender, "command.set", "player", t.getName(), "race", o == null ? "-" : o.coloredName(Lang.localeOf(sender)));
            }
            case "resetall" -> {
                plugin.manager().resetAll();
                lang.send(sender, "command.reset-all");
            }
            case "cdreset" -> {
                if (args.length < 2) return usage(sender);
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null) {
                    lang.send(sender, "command.not-online");
                    return true;
                }
                for (Origin o : plugin.origins()) if (o instanceof Alchemist a) a.resetCooldown(t);
                lang.send(sender, "command.cd-reset", "player", t.getName());
            }
            case "runeghost" -> {
                Player t = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player self ? self : null;
                if (t == null) {
                    lang.send(sender, "command.not-online");
                    return true;
                }
                for (Origin o : plugin.origins()) if (o instanceof Runesmith r) r.resetAncestor(t);
                lang.send(sender, "command.rune-ghost", "player", t.getName());
            }
            case "altarstop" -> lang.send(sender, "command.altars-stopped", "count", plugin.altars().stopAll());
            case "reload" -> {
                plugin.loadOrigins();
                lang.send(sender, "command.reloaded", "count", plugin.origins().size());
            }
            default -> {
                return usage(sender);
            }
        }
        return true;
    }

    private boolean usage(CommandSender s) {
        plugin.lang().send(s, "command.usage");
        if (s.hasPermission("origins.admin")) plugin.lang().send(s, "command.usage-admin");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        boolean admin = sender.hasPermission("origins.admin");
        if (args.length == 1) {
            out.addAll(USER);
            if (admin) out.addAll(ADMIN);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("info")) {
            for (Origin o : plugin.manager().all()) out.add(o.id());
        } else if (args.length == 2 && admin && List.of("set", "reset", "cdreset", "runeghost").contains(args[0].toLowerCase(Locale.ROOT))) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        } else if (args.length == 3 && admin && args[0].equalsIgnoreCase("set")) {
            for (Origin o : plugin.manager().all()) out.add(o.id());
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
