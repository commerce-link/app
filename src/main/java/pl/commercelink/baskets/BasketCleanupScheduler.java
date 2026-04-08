package pl.commercelink.baskets;

import io.awspring.cloud.sqs.annotation.SqsListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@ConditionalOnProperty(name = "application.env", havingValue = "prod", matchIfMissing = false)
public class BasketCleanupScheduler {

    private final BasketsRepository basketsRepository;

    @Value("${basket.cleanup.days:14}")
    private int cleanupDays;

    @Autowired
    public BasketCleanupScheduler(BasketsRepository basketsRepository) {
        this.basketsRepository = basketsRepository;
    }

    @SqsListener(
            value = "basket-cleanup-queue",
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    public void cleanUpOldBaskets(String message) {
        LocalDateTime thresholdDate = LocalDateTime.now().minusDays(cleanupDays);
        basketsRepository.deleteAllBasketsOlderThan(thresholdDate);
    }
}
   