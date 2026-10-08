package me.kismeria.origins.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Race meters for the action bar: 27 bold "|" = 81 px, exactly the width of the hunger bar.
 * Vanilla font only, no resource pack.
 */
public final class Meters {
    private static final int CELLS = 27;

    private Meters() {
    }

    /** Gradient from {@code from} to {@code to} over the filled part, {@code off} for the rest. */
    public static Component bar(double frac, String from, String to, String off, String tip) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, frac)) * CELLS);
        TextColor a = TextColor.fromHexString(from), b = TextColor.fromHexString(to);
        Component line = Component.empty().decoration(TextDecoration.BOLD, true);
        for (int i = 0; i < filled; i++) {
            TextColor c = tip != null && i == filled - 1 && filled < CELLS
                    ? TextColor.fromHexString(tip)
                    : TextColor.lerp(i / (float) (CELLS - 1), a, b);
            line = line.append(Component.text("|", c));
        }
        if (filled < CELLS) line = line.append(Component.text("|".repeat(CELLS - filled), TextColor.fromHexString(off)));
        return line;
    }

    /** Full meter with a light wave running along it ({@code phase} grows every update). */
    public static Component shimmer(String base, String glow, int phase) {
        TextColor a = TextColor.fromHexString(base), b = TextColor.fromHexString(glow);
        Component line = Component.empty().decoration(TextDecoration.BOLD, true);
        for (int i = 0; i < CELLS; i++) {
            int d = Math.floorMod(i - phase, 12);
            float t = 1 - Math.min(d, 12 - d) / 6f;
            line = line.append(Component.text("|", TextColor.lerp(t, a, b)));
        }
        return line;
    }
}
