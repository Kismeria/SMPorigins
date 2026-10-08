package me.kismeria.origins.origin;

import me.kismeria.origins.OriginsPlugin;
import me.kismeria.origins.core.Origin;
import me.kismeria.origins.core.Txt;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

public final class Survivor extends Origin {
    private final double adrenalineHp;
    private final double fallReduction;
    private final double sprintExhaustion;

    public Survivor(OriginsPlugin plugin) {
        super(plugin, "survivor");
        adrenalineHp = num("adrenaline-below-hp", 6);
        fallReduction = num("fall-damage-reduction", 0.3);
        sprintExhaustion = num("sprint-exhaustion-per-second", 0.1);
        plus("survivor_adrenaline", "hearts", Txt.num(adrenalineHp / 2));
        plus("survivor_light_steps", "pct", Txt.pct(fallReduction));
        minus("survivor_soft_skin");
        minus("survivor_fast_metabolism");
    }

    @Override public String color() { return "#e8c35a"; }
    @Override public Material icon() { return Material.TOTEM_OF_UNDYING; }
    @Override public int impact() { return 1; }

    @Override
    public void apply(Player p) {
        modifier(p, Attribute.FALL_DAMAGE_MULTIPLIER, "fall", -fallReduction, Operation.ADD_SCALAR);
    }

    @Override
    public void tick(Player p, int tick) {
        if (p.getHealth() < adrenalineHp) {
            effect(p, PotionEffectType.SPEED, 1);
            effect(p, PotionEffectType.RESISTANCE, 0);
        }
        if (p.isSprinting()) p.setExhaustion(Math.min(40f, p.getExhaustion() + (float) (sprintExhaustion / 2)));
    }

    @Override
    public boolean forbidsArmor(Material m) {
        return m.name().startsWith("NETHERITE_");
    }
}
