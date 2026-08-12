package pl.commercelink.inventory.supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class FeedParseStats {

    private static final Logger logger = LoggerFactory.getLogger(FeedParseStats.class);

    private final String supplierName;
    private final long startNanos = System.nanoTime();
    private int imported;
    private int importedCategorized;
    private int categorizationScheduled;
    private int categorizationPostponed;
    private int incomplete;
    private int invalid;

    FeedParseStats(String supplierName) {
        this.supplierName = supplierName;
    }

    String supplierName() {
        return supplierName;
    }

    void markImported() {
        imported++;
    }

    void markImportedCategorized() {
        importedCategorized++;
    }

    void markCategorizationScheduled() {
        categorizationScheduled++;
    }

    void markCategorizationPostponed() {
        categorizationPostponed++;
    }

    void markIncomplete() {
        incomplete++;
    }

    void markInvalid() {
        invalid++;
    }

    void log() {
        if (imported + importedCategorized + categorizationScheduled
                + categorizationPostponed + incomplete + invalid > 0) {
            logger.info(summary());
        }
    }

    String summary() {
        long importDurationInMs = (System.nanoTime() - startNanos) / 1_000_000;
        return "Feed " + supplierName + ": imported=" + imported
                + " importedCategorized=" + importedCategorized
                + " categorizationScheduled=" + categorizationScheduled
                + " categorizationPostponed=" + categorizationPostponed
                + " incomplete=" + incomplete
                + " invalid=" + invalid
                + " importDurationInMs=" + importDurationInMs;
    }
}
