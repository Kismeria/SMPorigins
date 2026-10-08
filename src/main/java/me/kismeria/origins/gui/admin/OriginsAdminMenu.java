package me.kismeria.origins.gui.admin;

import me.kismeria.origins.OriginsPlugin;
import me.kismeria.origins.core.Lang;
import me.kismeria.origins.core.Origin;
import me.kismeria.smpcore.gui.Menu;
import me.kismeria.smpcore.gui.menu.MainMenu;
import me.kismeria.smpcore.util.Icon;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** /smp → «Расы». Built on SmpCore's menu, same look and admin check. */
public final class OriginsAdminMenu extends Menu {
    private final OriginsPlugin origins;

    public OriginsAdminMenu(OriginsPlugin origins, Player viewer) {
        super(origins.core(), viewer);
        this.origins = origins;
    }

    @Override
    protected String title() {
        return "<dark_gray>SMP · Расы";
    }

    @Override
    protected int rows() {
        return 5;
    }

    @Override
    protected Menu reopen() {
        return new OriginsAdminMenu(origins, viewer);
    }

    @Override
    protected void draw() {
        String locale = Lang.localeOf(viewer);
        Map<String, Integer> counts = origins.manager().store().counts();
        List<String> stats = new ArrayList<>();
        stats.add("Игроков с расой: <white>" + origins.manager().store().size());
        stats.add("");
        for (Origin o : origins.allOrigins()) {
            stats.add((o.enabled() ? "" : "<dark_gray><st>") + o.coloredName(locale) + "<gray>: <white>" + counts.getOrDefault(o.id(), 0));
        }
        set(4, Icon.of(Material.KNOWLEDGE_BOOK).name("<#a970ff><bold>Статистика рас").lore(stats).build());

        button(20, Icon.of(Material.ENCHANTED_BOOK).name("<#a970ff><bold>Расы и их числа")
                .lore("Включить/выключить расу,", "менять шансы, бонусы, штрафы.").build(),
                t -> new RacesMenu(origins, viewer).open());
        button(22, Icon.of(Material.PLAYER_HEAD).name("<yellow><bold>Игроки")
                .lore("Посмотреть расу игрока,", "перевыдать, выбрать вручную,", "сбросить КД алхимика.").build(),
                t -> new PlayersMenu(origins, viewer, 0).open());
        button(24, Icon.of(Material.AMETHYST_CLUSTER).name("<light_purple><bold>Алтарь рун")
                .lore("Длительность, шанс зажечь,", "сколько кристаллов рассыпается.").build(),
                t -> new AltarMenu(origins, viewer).open());

        toggle(29, Material.TNT_MINECART, "Сброс рас вместе со сбросом SMP",
                List.of("«Сбросить SMP» в /smp → Старт", "заново раздаёт всем случайные расы."),
                origins.getConfig().getBoolean("reset-with-smp", true), v -> save("reset-with-smp", v));
        toggle(30, Material.OAK_SIGN, "Окно расы при каждом входе",
                List.of("Выключено: окно с расой показывается", "только когда раса новая."),
                origins.getConfig().getBoolean("welcome.every-join", false), v -> save("welcome.every-join", v));
        number(31, Material.CLOCK, "Задержка окна после входа",
                List.of("Через сколько секунд после входа", "в мир показать окно расы."),
                origins.getConfig().getInt("welcome.delay-seconds", 2), 1, 5, 1, 30, s -> s + " сек",
                v -> save("welcome.delay-seconds", v));
        button(32, Icon.of(Material.TNT).name("<red><bold>Сбросить расы всем")
                .lore("Все теряют расу. Онлайн сразу получают", "новую случайную, офлайн — при входе.").build(),
                t -> confirm("Перевыдать расы всем игрокам?", () -> origins.manager().resetAll()));
        button(33, Icon.of(Material.REPEATER).name("<aqua><bold>Перезагрузить")
                .lore("config.yml и языки lang/*.yml").build(), t -> {
            origins.loadOrigins();
            redraw();
        });

        back(() -> new MainMenu(origins.core(), viewer));
    }

    private void save(String path, Object value) {
        origins.getConfig().set(path, value);
        origins.saveConfig();
    }
}
