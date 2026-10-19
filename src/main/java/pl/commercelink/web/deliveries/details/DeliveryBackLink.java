package pl.commercelink.web.deliveries.details;

/**
 * "‹ Dostawy" on the delivery details returns to the list as the operator left it, and only ever to the list. The
 * super admin's "Kolejka dostaw" is the same address (the list spans the stores), so one rule serves both.
 */
public final class DeliveryBackLink {

    public static final String LIST = "/dashboard/deliveries";
    static final int MAX_LENGTH = 300;

    private DeliveryBackLink() {
    }

    public static String sanitize(String back) {
        if (back == null || back.length() > MAX_LENGTH) {
            return LIST;
        }
        boolean ownPath = back.equals(LIST) || back.startsWith(LIST + "?");
        return ownPath && !back.contains("//") ? back : LIST;
    }

    public static String labelKey(boolean superAdmin) {
        return superAdmin ? "nav.deliveries.queue" : "nav.deliveries";
    }
}
