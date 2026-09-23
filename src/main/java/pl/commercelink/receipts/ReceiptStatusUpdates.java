package pl.commercelink.receipts;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.receipts.api.Receipt;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Receipt states pushed by providers. The result only moves an attempt forward (the merger's rules) and wakes it,
 * so the effects run in the processor under the lease. Never throws: a webhook answered with an error is retried by
 * Fakturownia 25 times and then switched off for the whole account, and polling covers anything missed.
 */
@Slf4j
@Component
public class ReceiptStatusUpdates {

    private final ReceiptAttemptStore attempts;
    private final ReceiptWorkPublisher publisher;
    private final Clock clock;

    @Autowired
    public ReceiptStatusUpdates(ReceiptAttemptStore attempts, ReceiptWorkPublisher publisher) {
        this(attempts, publisher, Clock.systemUTC());
    }

    ReceiptStatusUpdates(ReceiptAttemptStore attempts, ReceiptWorkPublisher publisher, Clock clock) {
        this.attempts = attempts;
        this.publisher = publisher;
        this.clock = clock;
    }

    public void applyPushed(String storeId, String providerName, Receipt receipt) {
        try {
            if (receipt == null || receipt.receiptKey() == null) {
                log.info("Receipt webhook for store {} without a receipt key ignored; polling covers it", storeId);
                return;
            }
            String key = receipt.receiptKey();
            Optional<ReceiptAttempt> attempt = attempts.find(storeId, key);
            if (attempt.isEmpty() || !providerName.equals(attempt.get().getProvider())) {
                log.info("Receipt webhook for unknown attempt {} of store {} ignored", key, storeId);
                return;
            }
            Instant now = clock.instant();
            // update() re-runs this predicate after a version conflict (someone else wrote the attempt meanwhile);
            // changed[0] must reflect only the run that actually gets saved, so it is reset on every run and set
            // only on the run that returns true — never carried over from an earlier, discarded run.
            boolean[] changed = new boolean[1];
            attempts.update(storeId, key, a -> {
                changed[0] = false;
                if (!ReceiptStatusMerger.merge(a, receipt, ReceiptStatusMerger.Source.PUSH, now)) {
                    return false;
                }
                a.schedule(now);
                changed[0] = true;
                return true;
            });
            if (changed[0]) {
                publisher.publishNow(storeId, key);
            }
        } catch (RuntimeException e) {
            log.error("Receipt webhook for store {} could not be applied", storeId, e);
        }
    }
}
