package pl.commercelink.marketplace;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.commercelink.scheduling.EventBridgeSchedules;

@Component
public class MarketplaceReturnsImportScheduler extends MarketplaceImportScheduler {

    public MarketplaceReturnsImportScheduler(@Value("${sqs.returns-import.queue.arn}") String returnsImportQueueArn,
                                             @Value("${marketplace.returns-import.default-interval-minutes}") int defaultIntervalMinutes,
                                             EventBridgeSchedules schedules) {
        super("returns-import-", returnsImportQueueArn, defaultIntervalMinutes, schedules);
    }
}
