package me.kismeria.origins.gui.admin;

import me.kismeria.origins.OriginsPlugin;
import me.kismeria.smpcore.gui.Menu;
import me.kismeria.smpcore.util.Icon;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

public final class AltarMenu extends Menu {
    private final OriginsPlugin origins;

    public AltarMenu(OriginsPlugin origins, Player viewer) {
        super(origins.core(), viewer);
        this.origins = origins;
    }

    @Override
    protected String title() {
        return "<dark_gray>SMP · Расы · Алтарь рун";
    }

    @Override
    protected int rows() {
        return 4;
    }

    @Override
    protected Menu reopen() {
        return new AltarMenu(origins, viewer);
    }

    @Override
    protected void draw() {
        var c = origins.getConfig();
        set(4, Icon.of(Material.CALIBRATED_SCULK_SENSOR).name("<light_purple><bold>Алтарь рун")
                .lore("Калиброванный скалк-сенсор в центре,", "8 аметистовых блоков: 4 на осях в 3 блоках,",
                        "4 по диагоналям в 2 блоках (высота ±3).", "Выбросить аметистовый осколок на сенсор.").build());
        number(19, Material.CLOCK, "Сколько горит", List.of("Пока горит, Руномант в центре", "колдует быстрее и дешевле."),
                c.getInt("altar.duration-seconds", 180), 10, 60, 30, 1800, s -> s + " сек", v -> save("altar.duration-seconds", v));
        number(20, Material.AMETHYST_SHARD, "Шанс зажечь", List.of("Шанс, что осколок зажжёт алтарь."),
                (int) Math.round(c.getDouble("altar.success-chance", 0.3) * 100), 5, 25, 1, 100, s -> s + "%",
                v -> save("altar.success-chance", v / 100.0));
        number(21, Material.BARRIER, "КД после неудачи", List.of("Защита от спама осколками."),
                c.getInt("altar.fail-cooldown-seconds", 5), 1, 10, 0, 120, s -> s + " сек", v -> save("altar.fail-cooldown-seconds", v));
        number(23, Material.AMETHYST_BLOCK, "Рассыпается минимум", List.of("Сколько аметистовых блоков", "пропадает, когда алтарь гаснет."),
                c.getInt("altar.burnout-breaks-min", 2), 1, 2, 0, 8, String::valueOf, v -> save("altar.burnout-breaks-min", v));
        number(24, Material.AMETHYST_BLOCK, "Рассыпается максимум", List.of("Не меньше минимума."),
                c.getInt("altar.burnout-breaks-max", 3), 1, 2, 0, 8, String::valueOf, v -> save("altar.burnout-breaks-max", v));
        button(25, Icon.of(Material.WATER_BUCKET).name("<red><bold>Погасить все алтари").build(),
                t -> confirm("Погасить все горящие алтари?", () -> origins.altars().stopAll()));
        back(() -> new OriginsAdminMenu(origins, viewer));
    }

    private void save(String path, Object value) {
        origins.getConfig().set(path, value);
        origins.saveConfig();
        origins.altars().reload();
    }
}
