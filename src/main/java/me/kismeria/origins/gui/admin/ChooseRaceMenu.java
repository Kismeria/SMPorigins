package me.kismeria.origins.gui.admin;

import me.kismeria.origins.OriginsPlugin;
import me.kismeria.origins.core.Lang;
import me.kismeria.origins.core.Origin;
import me.kismeria.smpcore.gui.Menu;
import me.kismeria.smpcore.util.Icon;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class ChooseRaceMenu extends Menu {
    private final OriginsPlugin origins;
    private final Player target;

    public ChooseRaceMenu(OriginsPlugin origins, Player viewer, Player target) {
        super(origins.core(), viewer);
        this.origins = origins;
        this.target = target;
    }

    @Override
    protected String title() {
        return "<dark_gray>Раса для " + target.getName();
    }

    @Override
    protected int rows() {
        return 4;
    }

    @Override
    protected void draw() {
        String locale = Lang.localeOf(viewer);
        Origin current = origins.manager().get(target);
        List<Origin> list = new ArrayList<>(origins.manager().all());
        for (int i = 0; i < list.size() && i < RacesMenu.SLOTS.length; i++) {
            Origin o = list.get(i);
            button(RacesMenu.SLOTS[i], Icon.of(o.icon()).name(o.coloredName(locale))
                    .lore("<gray>" + o.description(locale), "", o == current ? "<green>Текущая раса" : "<yellow>Клик</yellow> — назначить")
                    .glow(o == current).build(), t -> {
                if (!target.isOnline()) return;
                origins.manager().set(target, o, false);
                origins.manager().greet(target);
                new PlayerRaceMenu(origins, viewer, target).open();
            });
        }
        back(() -> new PlayerRaceMenu(origins, viewer, target));
    }
}
