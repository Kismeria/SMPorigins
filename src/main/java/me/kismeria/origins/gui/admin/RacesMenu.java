package me.kismeria.origins.gui.admin;

import me.kismeria.origins.OriginsPlugin;
import me.kismeria.origins.core.Lang;
import me.kismeria.origins.core.Origin;
import me.kismeria.smpcore.gui.Menu;
import me.kismeria.smpcore.util.Icon;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.List;
import java.util.Map;

public final class RacesMenu extends Menu {
    static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    private final OriginsPlugin origins;

    public RacesMenu(OriginsPlugin origins, Player viewer) {
        super(origins.core(), viewer);
        this.origins = origins;
    }

    @Override
    protected String title() {
        return "<dark_gray>SMP · Расы · Список";
    }

    @Override
    protected int rows() {
        return 4;
    }

    @Override
    protected Menu reopen() {
        return new RacesMenu(origins, viewer);
    }

    @Override
    protected void draw() {
        String locale = Lang.localeOf(viewer);
        Map<String, Integer> counts = origins.manager().store().counts();
        List<Origin> all = origins.allOrigins();
        for (int i = 0; i < all.size() && i < SLOTS.length; i++) {
            Origin o = all.get(i);
            boolean on = o.enabled();
            button(SLOTS[i], Icon.of(o.icon()).name((on ? "" : "<dark_gray><st>") + o.coloredName(locale) + (on ? "" : " <red>(выкл)"))
                    .lore("<gray>" + o.description(locale), "",
                            "Игроков: <white>" + counts.getOrDefault(o.id(), 0),
                            on ? "<green>● Включена" : "<red>○ Выключена", "",
                            "<yellow>ЛКМ</yellow> — вкл/выкл",
                            "<yellow>ПКМ</yellow> — числа способностей")
                    .glow(on).build(), t -> click(o, t));
        }
        back(() -> new OriginsAdminMenu(origins, viewer));
    }

    private void click(Origin o, ClickType type) {
        if (type.isRightClick()) {
            new RaceParamsMenu(origins, viewer, o.id()).open();
            return;
        }
        boolean on = o.enabled();
        if (on && origins.origins().size() <= 1) {
            viewer.sendMessage(Lang.mm("<red>Нельзя выключить последнюю расу."));
            return;
        }
        // players of a disabled race get a new random one (and its window)
        origins.getConfig().set(o.path("enabled"), !on);
        origins.saveConfig();
        origins.loadOrigins();
        redraw();
    }
}
