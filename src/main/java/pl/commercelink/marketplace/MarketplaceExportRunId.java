package pl.commercelink.marketplace;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MarketplaceExportRunId {

    private static final long COUNTDOWN_ORIGIN = 9_999_999_999L;
    private static final DateTimeFormatter RUN_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter RUN_TIMESTAMP_UTC = RUN_TIMESTAMP.withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter READABLE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern COUNTDOWN_RUN_ID = Pattern.compile("^(\\d{10})_(.+)$");
    private static final Pattern TIMESTAMP_PART = Pattern.compile("\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}");

    private MarketplaceExportRunId() {
    }

    public static String of(Instant instant) {
        return String.format("%010d_%s", COUNTDOWN_ORIGIN - instant.getEpochSecond(), RUN_TIMESTAMP_UTC.format(instant));
    }

    public static Optional<Instant> instantOf(String runId) {
        Matcher countdown = COUNTDOWN_RUN_ID.matcher(runId);
        if (countdown.matches()) {
            return Optional.of(Instant.ofEpochSecond(COUNTDOWN_ORIGIN - Long.parseLong(countdown.group(1))));
        }
        return legacyInstantOf(runId);
    }

    public static String readable(String runId) {
        Matcher timestamp = TIMESTAMP_PART.matcher(runId);
        if (!timestamp.find()) {
            return runId;
        }
        return LocalDateTime.parse(timestamp.group(), RUN_TIMESTAMP).format(READABLE_TIMESTAMP);
    }

    private static Optional<Instant> legacyInstantOf(String runId) {
        try {
            return Optional.of(LocalDateTime.parse(runId, RUN_TIMESTAMP).toInstant(ZoneOffset.UTC));
        } catch (DateTimeParseException exception) {
            return Optional.empty();
        }
    }
}
