package pl.commercelink.marketplace;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.commercelink.scheduling.EventBridgeSchedules;

@Component
public class MarketplaceOrdersImportScheduler extends MarketplaceImportScheduler {

    public MarketplaceOrdersImportScheduler(@Value("${sqs.orders-import.queue.arn}") String ordersImportQueueArn,
                                            @Value("${marketplace.orders-import.default-interval-minutes}") int defaultIntervalMinutes,
                                            EventBridgeSchedules schedules) {
        super("orders-import-", ordersImportQueueArn, defaultIntervalMinutes, schedules);
    }
}
