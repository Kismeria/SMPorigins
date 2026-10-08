package me.kismeria.origins.core;

public final class Txt {
    private Txt() {
    }

    public static String num(double d) {
        double r = Math.round(d * 100) / 100.0;
        if (r == Math.rint(r)) return String.valueOf((long) r);
        return String.valueOf(r);
    }

    /** 0.3 -> "30%" */
    public static String pct(double fraction) {
        return num(fraction * 100) + "%";
    }

    /** Max health delta in HP -> resulting heart count, e.g. -4 -> "8". */
    public static String hearts(double healthDelta) {
        return num((20 + healthDelta) / 2);
    }

    public static String progress(double frac, int len, String fill, String empty) {
        int n = (int) Math.round(Math.max(0, Math.min(1, frac)) * len);
        return fill + "|".repeat(n) + empty + "|".repeat(len - n);
    }
}
