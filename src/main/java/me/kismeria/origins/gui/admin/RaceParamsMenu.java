package me.kismeria.origins.gui.admin;

import me.kismeria.origins.OriginsPlugin;
import me.kismeria.origins.core.Lang;
import me.kismeria.origins.core.Origin;
import me.kismeria.origins.core.Txt;
import me.kismeria.smpcore.gui.Menu;
import me.kismeria.smpcore.util.Icon;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.List;

/** Every number of one race. ЛКМ/ПКМ ± шаг, Shift — крупный шаг; booleans toggle. */
public final class RaceParamsMenu extends Menu {
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private final OriginsPlugin origins;
    private final String originId;

    public RaceParamsMenu(OriginsPlugin origins, Player viewer, String originId) {
        super(origins.core(), viewer);
        this.origins = origins;
        this.originId = originId;
    }

    @Override
    protected String title() {
        Origin o = origins.anyOrigin(originId);
        return "<dark_gray>SMP · Расы · " + (o == null ? originId : o.name(Lang.localeOf(viewer)));
    }

    @Override
    protected int rows() {
        return 5;
    }

    @Override
    protected Menu reopen() {
        return new RaceParamsMenu(origins, viewer, originId);
    }

    @Override
    protected void draw() {
        Origin o = origins.anyOrigin(originId);
        if (o == null) return;
        String locale = Lang.localeOf(viewer);
        List<Origin.Param> params = o.params();
        for (int i = 0; i < params.size() && i < SLOTS.length; i++) {
            Origin.Param p = params.get(i);
            String path = o.path(p.key());
            String name = origins.lang().raw(locale, "param." + p.key());
            if (p.def() instanceof Boolean def) {
                toggle(SLOTS[i], Material.LEVER, name, List.of("<dark_gray>" + p.key()),
                        origins.getConfig().getBoolean(path, def), v -> change(path, v));
                continue;
            }
            boolean integer = p.def() instanceof Integer;
            double def = ((Number) p.def()).doubleValue();
            double value = origins.getConfig().getDouble(path, def);
            double step = integer ? 1 : Math.abs(def) < 0.1 ? 0.005 : Math.abs(def) < 3 ? 0.05 : 1;
            double big = integer ? 10 : step * 5;
            button(SLOTS[i], Icon.of(integer ? Material.REDSTONE : Material.GLOWSTONE_DUST).name("<aqua>" + name)
                    .lore("<dark_gray>" + p.key(), "",
                            "Значение: <white>" + Txt.num(value) + (value != def ? " <gray>(стандарт " + Txt.num(def) + ")" : ""), "",
                            "<yellow>ЛКМ</yellow> +" + Txt.num(step) + "  <yellow>ПКМ</yellow> −" + Txt.num(step),
                            "<yellow>Shift</yellow> — шаг " + Txt.num(big))
                    .build(), t -> {
                double delta = switch (t) {
                    case LEFT -> step;
                    case RIGHT -> -step;
                    case SHIFT_LEFT -> big;
                    case SHIFT_RIGHT -> -big;
                    default -> 0;
                };
                if (delta == 0) return;
                double next = Math.round((value + delta) * 10000) / 10000.0;
                if (def >= 0 && next < 0) next = 0;
                change(path, integer ? (Object) (int) Math.round(next) : (Object) next);
            });
        }
        button(36, Icon.of(Material.WATER_BUCKET).name("<red><bold>Стандартные значения")
                .lore("Вернуть все числа этой расы", "к значениям по умолчанию.").build(),
                t -> confirm("Сбросить числа расы?", () -> {
                    for (Origin.Param p : o.params()) origins.getConfig().set(o.path(p.key()), null);
                    origins.saveConfig();
                    origins.loadOrigins();
                }));
        set(44, Icon.of(o.icon()).name(o.coloredName(locale)).lore("<gray>Изменения сразу применяются", "<gray>и описание расы обновляется.").build());
        back(() -> new RacesMenu(origins, viewer));
    }

    private void change(String path, Object value) {
        origins.getConfig().set(path, value);
        origins.saveConfig();
        origins.loadOrigins();
        redraw();
    }
}
