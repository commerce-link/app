package pl.commercelink.web.orders;

/** "‹ Zamówienia" on the details returns to the list as the operator left it — the open orders or the archive — and only there. */
public final class OrderBackLink {

    public static final String LIST = "/dashboard/orders";
    public static final String ARCHIVE = OrderArchiveQuery.PATH;

    // an archive address carries a search text of up to 100 characters (encoded) and a cursor
    private static final int MAX_LENGTH = 1000;

    private OrderBackLink() {
    }

    public static String sanitize(String back) {
        if (back == null || back.length() > MAX_LENGTH || back.contains("//")) {
            return LIST;
        }
        return own(back, LIST) || own(back, ARCHIVE) ? back : LIST;
    }

    public static boolean isArchive(String back) {
        return back != null && own(back, ARCHIVE);
    }

    private static boolean own(String back, String path) {
        return back.equals(path) || back.startsWith(path + "?");
    }
}
