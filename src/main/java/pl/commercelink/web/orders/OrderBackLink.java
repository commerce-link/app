package pl.commercelink.web.orders;

/** "‹ Zamówienia" on the details returns to the list as the operator left it, and only ever to the list. */
public final class OrderBackLink {

    public static final String LIST = "/dashboard/orders";

    private OrderBackLink() {
    }

    public static String sanitize(String back) {
        if (back == null || back.length() > 300) {
            return LIST;
        }
        boolean ownPath = back.equals(LIST) || back.startsWith(LIST + "?");
        return ownPath && !back.contains("//") ? back : LIST;
    }
}
