package pl.commercelink.inventory;

public final class CodeNotation {

    private static final int MIN_AFFIX_LENGTH = 4;

    private CodeNotation() {
    }

    public static String normalize(String code) {
        if (code == null) {
            return "";
        }
        return code.replaceAll("[^\\p{L}\\p{N}]", "").toUpperCase();
    }

    public static boolean sameNotation(String a, String b) {
        String left = normalize(a);
        String right = normalize(b);
        if (left.isEmpty() || right.isEmpty()) {
            return false;
        }
        if (left.equals(right)) {
            return true;
        }
        String shorter = left.length() <= right.length() ? left : right;
        String longer = shorter == left ? right : left;
        return shorter.length() >= MIN_AFFIX_LENGTH && (longer.startsWith(shorter) || longer.endsWith(shorter));
    }
}
