package pl.commercelink.marketplace;

import pl.commercelink.starter.storage.TimeOrderedFileName;

import java.time.Instant;
import java.util.Optional;

public final class MarketplaceExportRunId {

    private MarketplaceExportRunId() {
    }

    public static String of(Instant instant) {
        return TimeOrderedFileName.of(instant);
    }

    public static Optional<Instant> instantOf(String runId) {
        return TimeOrderedFileName.instantOf(runId);
    }

    public static String readable(String runId) {
        return TimeOrderedFileName.readableTimestampOf(runId).orElse(runId);
    }
}
