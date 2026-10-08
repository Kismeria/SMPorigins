package me.kismeria.origins.gui.admin;

import me.kismeria.origins.OriginsPlugin;
import me.kismeria.origins.core.Lang;
import me.kismeria.origins.core.Origin;
import me.kismeria.origins.origin.Alchemist;
import me.kismeria.smpcore.gui.Menu;
import me.kismeria.smpcore.util.Icon;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** One player: reroll, pick a race by hand, show the window again, alchemist cooldown. */
public final class PlayerRaceMenu extends Menu {
    private final OriginsPlugin origins;
    private final Player target;

    public PlayerRaceMenu(OriginsPlugin origins, Player viewer, Player target) {
        super(origins.core(), viewer);
        this.origins = origins;
        this.target = target;
    }

    @Override
    protected String title() {
        return "<dark_gray>SMP · Расы · " + target.getName();
    }

    @Override
    protected int rows() {
        return 4;
    }

    @Override
    protected Menu reopen() {
        return new PlayerRaceMenu(origins, viewer, target);
    }

    @Override
    protected void draw() {
        if (!target.isOnline()) {
            new PlayersMenu(origins, viewer, 0).open();
            return;
        }
        String locale = Lang.localeOf(viewer);
        Origin o = origins.manager().get(target);
        set(4, Icon.of(Material.PLAYER_HEAD).head(target).name("<yellow><bold>" + target.getName())
                .lore("Раса: " + (o == null ? "<gray>нет" : o.coloredName(locale))).build());

        button(19, Icon.of(Material.ENDER_EYE).name("<light_purple><bold>Случайная раса")
                .lore("Выдать новую случайную расу", "и показать окно с её описанием.").build(),
                t -> confirm("Перевыдать расу " + target.getName() + "?", () -> origins.manager().reroll(target)));
        button(21, Icon.of(Material.ENCHANTED_BOOK).name("<#a970ff><bold>Выбрать расу")
                .lore("Назначить конкретную расу.").build(),
                t -> new ChooseRaceMenu(origins, viewer, target).open());
        button(23, Icon.of(Material.OAK_SIGN).name("<yellow><bold>Показать окно расы")
                .lore("Ещё раз открыть игроку", "окно с плюсами и минусами.").build(), t -> {
            origins.manager().greet(target);
            viewer.sendMessage(Lang.mm("<green>Окно показано."));
        });
        boolean alchemist = o instanceof Alchemist;
        button(25, Icon.of(Material.BREWING_STAND).name((alchemist ? "<aqua>" : "<dark_gray>") + "<bold>Сбросить КД трансмутации")
                .lore(alchemist ? "Алхимик сможет колдовать сразу." : "Только для Алхимика.").build(), t -> {
            if (o instanceof Alchemist a) {
                a.resetCooldown(target);
                viewer.sendMessage(Lang.mm("<green>КД сброшен."));
            }
        });
        back(() -> new PlayersMenu(origins, viewer, 0));
    }
}
