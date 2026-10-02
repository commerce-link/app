package pl.commercelink.web.itemhistory;

import java.util.List;

public record ItemHistoryPage(String serialNo, boolean searched, boolean found, String warningCounts, Product product,
                              Now now, String eventsNote, List<Event> events, Integer timelineLimit, String moreText,
                              String shownText, String hiddenText, String emptyEventsText) {
    public static final int VISIBLE_EVENTS = 10;

    public record Product(String title, String serialNo, String ean, String mfn) {}
    public record Now(String label, String tone, String text, String linkText, String href) {}
    public record Event(String at, String icon, String title, String recordText, String recordHref, String recordId,
                        String pillText, String pillTone, String facts) {}

    public static ItemHistoryPage empty() {
        return new ItemHistoryPage(null, false, false, null, null, null, null, List.of(), null, null, null, null, null);
    }
}
