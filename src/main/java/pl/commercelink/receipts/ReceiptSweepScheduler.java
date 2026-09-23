package pl.commercelink.receipts;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Local trigger: outside AWS there is no scheduler, so a fixed delay takes over (as for the dropship sweep).
 */
@Component
@ConditionalOnProperty(name = "application.env", havingValue = "localhost", matchIfMissing = true)
@RequiredArgsConstructor
class ReceiptSweepScheduler {

    private final ReceiptSweep sweep;

    // Spring @Scheduled expects milliseconds, not ISO-8601 durations.
    // Property value is in milliseconds; defaults to 60000 ms (1 minute).
    @Scheduled(fixedDelayString = "${receipts.sweep.fixed-delay:60000}", initialDelayString = "30000")
    void trigger() {
        sweep.sweep();
    }
}
