package pl.commercelink.stores;

public record BrandPalette(String fill, String on, String hover, String ink, String inkHover, String soft) {

    public static final String SYSTEM_ACCENT = "#1b4db1";
    public static final String INK = "#0e1721";
    private static final String WHITE = "#ffffff";
    private static final String BLACK = "#000000";
    private static final double MIN_CONTRAST = 4.5;

    /** @param safeHex result of {@code Branding.getSafePrimaryColor()}: {@code #rrggbb} or null */
    public static BrandPalette of(String safeHex) {
        String fill = safeHex != null ? safeHex : SYSTEM_ACCENT;
        String on = onColour(fill);

        // The brand colour stays untouched as the button background; links and focus use a darkened copy that passes AA
        // on white and on soft, the selection background that also carries ink text (the step badge).
        String soft = mix(fill, WHITE, 0.90);
        String ink = fill;
        double t = 0;
        while (contrast(ink, WHITE) < MIN_CONTRAST || contrast(ink, soft) < MIN_CONTRAST) {
            t += 0.05;
            ink = mix(fill, BLACK, t);
        }

        String hover = mix(fill, BLACK, 0.12);
        if (contrast(hover, on) < MIN_CONTRAST) {
            hover = mix(fill, WHITE, 0.25);
        }
        return new BrandPalette(fill, on, hover, ink, mix(ink, BLACK, 0.15), soft);
    }

    public String style() {
        return "--cl-brand:%s;--cl-brand-on:%s;--cl-brand-hover:%s;--cl-brand-ink:%s;--cl-brand-ink-hover:%s;--cl-brand-soft:%s"
                .formatted(fill, on, hover, ink, inkHover, soft);
    }

    private static String onColour(String fill) {
        if (contrast(fill, WHITE) >= MIN_CONTRAST) {
            return WHITE;
        }
        return contrast(fill, INK) >= MIN_CONTRAST ? INK : BLACK;
    }

    static double contrast(String hexA, String hexB) {
        double a = luminance(hexA);
        double b = luminance(hexB);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    private static double luminance(String hex) {
        int[] c = rgb(hex);
        return 0.2126 * channel(c[0]) + 0.7152 * channel(c[1]) + 0.0722 * channel(c[2]);
    }

    private static double channel(int value) {
        double v = value / 255.0;
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    private static String mix(String from, String to, double t) {
        int[] a = rgb(from);
        int[] b = rgb(to);
        StringBuilder out = new StringBuilder("#");
        for (int i = 0; i < 3; i++) {
            // rint rounds half to even, like the reference implementation
            long v = Math.round(Math.rint(a[i] + (b[i] - a[i]) * t));
            out.append("%02x".formatted(Math.max(0, Math.min(255, v))));
        }
        return out.toString();
    }

    private static int[] rgb(String hex) {
        return new int[]{
                Integer.parseInt(hex.substring(1, 3), 16),
                Integer.parseInt(hex.substring(3, 5), 16),
                Integer.parseInt(hex.substring(5, 7), 16)};
    }
}
