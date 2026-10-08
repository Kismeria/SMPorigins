package me.kismeria.origins.gui.admin;

import me.kismeria.origins.OriginsPlugin;
import me.kismeria.origins.core.Lang;
import me.kismeria.origins.core.Origin;
import me.kismeria.smpcore.gui.Menu;
import me.kismeria.smpcore.util.Icon;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;

/** Online players with their race. */
public final class PlayersMenu extends Menu {
    private static final int[] SLOTS = new int[28];

    static {
        for (int i = 0; i < SLOTS.length; i++) SLOTS[i] = 10 + (i / 7) * 9 + i % 7;
    }

    private final OriginsPlugin origins;
    private final int page;

    public PlayersMenu(OriginsPlugin origins, Player viewer, int page) {
        super(origins.core(), viewer);
        this.origins = origins;
        this.page = page;
    }

    @Override
    protected String title() {
        return "<dark_gray>SMP · Расы · Игроки";
    }

    @Override
    protected Menu reopen() {
        return new PlayersMenu(origins, viewer, page);
    }

    @Override
    protected void draw() {
        String locale = Lang.localeOf(viewer);
        List<Player> players = Bukkit.getOnlinePlayers().stream()
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .map(p -> (Player) p).toList();
        int pages = Math.max(1, (players.size() + SLOTS.length - 1) / SLOTS.length);
        int current = Math.min(page, pages - 1);
        for (int i = 0; i < SLOTS.length; i++) {
            int index = current * SLOTS.length + i;
            if (index >= players.size()) break;
            Player target = players.get(index);
            Origin o = origins.manager().get(target);
            button(SLOTS[i], Icon.of(Material.PLAYER_HEAD).head(target).name("<yellow><bold>" + target.getName())
                    .lore("Раса: " + (o == null ? "<gray>нет" : o.coloredName(locale)), "", "<yellow>Клик</yellow> — управление").build(),
                    t -> {
                        if (target.isOnline()) new PlayerRaceMenu(origins, viewer, target).open();
                        else redraw();
                    });
        }
        if (current > 0) {
            button(45, Icon.of(Material.ARROW).name("<yellow>← Страница " + current).build(),
                    t -> new PlayersMenu(origins, viewer, current - 1).open());
        }
        if (current < pages - 1) {
            button(53, Icon.of(Material.ARROW).name("<yellow>Страница " + (current + 2) + " →").build(),
                    t -> new PlayersMenu(origins, viewer, current + 1).open());
        }
        back(() -> new OriginsAdminMenu(origins, viewer));
    }
}
