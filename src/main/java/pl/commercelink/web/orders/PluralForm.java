package pl.commercelink.web.orders;

/**
 * Picks the plural form of a counted noun as a message key suffix, following the Polish rules: "one" for 1, "few" for
 * 2-4, 22-24, 32-34... (but not 12-14), "many" for everything else. English bundles give "few" and "many" the same
 * text, so one rule serves both languages.
 */
public final class PluralForm {

    private PluralForm() {
    }

    public static String of(int count) {
        int n = Math.abs(count);
        if (n == 1) {
            return "one";
        }
        int lastDigit = n % 10;
        int lastTwoDigits = n % 100;
        if (lastDigit >= 2 && lastDigit <= 4 && (lastTwoDigits < 12 || lastTwoDigits > 14)) {
            return "few";
        }
        return "many";
    }
}
