package pl.commercelink.web.inventory;

/**
 * The page opened to pick a source for something else, not just to look a price up. Encoded in the URL as
 * {@code ?for=order:{orderId}:{itemId}} so the mode survives a reload, a bookmark and the browser's back
 * button, which a session attribute would not.
 */
public record OfferSelection(String orderId, String itemId) {

    private static final String ORDER_PREFIX = "order:";
    private static final int PARTS = 2;

    /** Returns null for anything unparseable: a stray parameter must not break an ordinary search. */
    public static OfferSelection parse(String value) {
        if (value == null || !value.startsWith(ORDER_PREFIX)) {
            return null;
        }
        String[] parts = value.substring(ORDER_PREFIX.length()).split(":", PARTS);
        if (parts.length != PARTS || !isId(parts[0]) || !isId(parts[1])) {
            return null;
        }
        return new OfferSelection(parts[0], parts[1]);
    }

    // the ids go straight into a URL the page renders, so refuse anything that could steer it elsewhere
    private static boolean isId(String value) {
        return !value.isBlank() && value.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '-' || c == '_');
    }

    public String token() {
        return ORDER_PREFIX + orderId + ":" + itemId;
    }

    public String assignUrl() {
        return "/dashboard/orders/" + orderId + "/assign-supplier";
    }

    public String cancelUrl() {
        return "/dashboard/orders/" + orderId;
    }
}
