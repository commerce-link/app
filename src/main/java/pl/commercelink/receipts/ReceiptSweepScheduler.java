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

    @Scheduled(fixedDelayString = "${receipts.sweep.fixed-delay:PT1M}", initialDelayString = "PT30S")
    void trigger() {
        sweep.sweep();
    }
}
