package me.kismeria.origins.origin;

import me.kismeria.origins.OriginsPlugin;
import me.kismeria.origins.core.Origin;
import me.kismeria.origins.core.Txt;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.entity.Player;

public final class Builder extends Origin {
    private final double reach;
    private final double fallReduction;
    private final double slow;
    private final double attackSlow;

    public Builder(OriginsPlugin plugin) {
        super(plugin, "builder");
        reach = num("extra-block-reach", 1.5);
        fallReduction = num("fall-damage-reduction", 0.75);
        slow = num("movement-slow", 0.15);
        attackSlow = num("attack-speed-penalty", 0.15);
        plus("builder_reach", "blocks", Txt.num(reach));
        plus("builder_climber", "pct", Txt.pct(fallReduction));
        minus("builder_heavy_belt", "pct", Txt.pct(slow));
        minus("builder_clumsy_fighter", "pct", Txt.pct(attackSlow));
    }

    @Override public String color() { return "#e0a040"; }
    @Override public Material icon() { return Material.SCAFFOLDING; }
    @Override public int impact() { return 1; }

    @Override
    public void apply(Player p) {
        modifier(p, Attribute.BLOCK_INTERACTION_RANGE, "reach", reach, Operation.ADD_NUMBER);
        modifier(p, Attribute.FALL_DAMAGE_MULTIPLIER, "fall", -fallReduction, Operation.ADD_SCALAR);
        modifier(p, Attribute.MOVEMENT_SPEED, "belt", -slow, Operation.MULTIPLY_SCALAR_1);
        modifier(p, Attribute.ATTACK_SPEED, "clumsy", -attackSlow, Operation.MULTIPLY_SCALAR_1);
    }
}
