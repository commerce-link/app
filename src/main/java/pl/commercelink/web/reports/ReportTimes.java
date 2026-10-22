package pl.commercelink.web.reports;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Moments are shown the way the store's people read them: Warsaw time, "14.10.2026 10:05". */
final class ReportTimes {

    private static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("d.MM.yyyy HH:mm");

    private ReportTimes() {
    }

    static String format(Instant moment) {
        return FORMAT.format(moment.atZone(ZONE));
    }
}
