package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedParseStatsTest {

    @Test
    void logsNothingWhenAllCountersAreZero() {
        // given
        FeedParseStats stats = new FeedParseStats("Acme");

        // when
        String output = captureLog(stats);

        // then
        assertEquals("", output);
    }

    @Test
    void logsAggregatedCountersLine() {
        // given
        FeedParseStats stats = new FeedParseStats("Acme");
        stats.markAdopted();
        stats.markPendingAdded();
        stats.markPendingAdded();
        stats.markDropped();

        // when
        String output = captureLog(stats);

        // then
        assertTrue(output.contains("Feed Acme: adoptedCategories=1 pendingAdded=2 droppedUnprocessable=1"));
    }

    private static String captureLog(FeedParseStats stats) {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured));
        try {
            stats.log();
        } finally {
            System.setOut(originalOut);
        }
        return captured.toString();
    }
}
