package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedParseStatsTest {

    @Test
    void summaryContainsAllCounters() {
        // given
        FeedParseStats stats = new FeedParseStats("Acme");
        stats.markImported();
        stats.markImported();
        stats.markImportedCategorized();
        stats.markCategorizationScheduled();
        stats.markCategorizationScheduled();
        stats.markCategorizationPostponed();
        stats.markIncomplete();
        stats.markInvalid();

        // when
        String summary = stats.summary();

        // then
        assertTrue(summary.contains("Feed Acme: imported=2 importedCategorized=1"
                + " categorizationScheduled=2 categorizationPostponed=1 incomplete=1 invalid=1"));
    }

    @Test
    void summaryContainsDurationSinceCreation() throws InterruptedException {
        // given
        FeedParseStats stats = new FeedParseStats("Acme");
        Thread.sleep(5);

        // when
        String summary = stats.summary();

        // then
        long importDurationInMs = Long.parseLong(
                summary.substring(summary.indexOf("importDurationInMs=") + "importDurationInMs=".length()));
        assertTrue(importDurationInMs >= 5);
    }
}
