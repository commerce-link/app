package pl.commercelink.receipts;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Wakes every attempt whose time has come. The schedule lives on the attempts, so a lost message, a crash or a deploy
 * only delays work until the next sweep; re-publishing an attempt still in the queue is absorbed by the FIFO
 * deduplication and, beyond that, by the lease.
 */
@Slf4j
@Component
public class ReceiptSweep {

    static final int BATCH = 500;

    private final ReceiptAttemptStore attempts;
    private final ReceiptWorkPublisher publisher;
    private final Clock clock;

    @Autowired
    public ReceiptSweep(ReceiptAttemptStore attempts, ReceiptWorkPublisher publisher) {
        this(attempts, publisher, Clock.systemUTC());
    }

    ReceiptSweep(ReceiptAttemptStore attempts, ReceiptWorkPublisher publisher, Clock clock) {
        this.attempts = attempts;
        this.publisher = publisher;
        this.clock = clock;
    }

    public int sweep() {
        int published = 0;
        for (ReceiptAttempt attempt : attempts.findDue(clock.instant(), BATCH)) {
            try {
                publisher.publishDue(attempt);
                published++;
            } catch (RuntimeException e) {
                log.error("Receipt attempt {} could not be queued", attempt.getReceiptKey(), e);
            }
        }
        if (published > 0) {
            log.info("Receipt sweep queued {} attempts", published);
        }
        return published;
    }
}
