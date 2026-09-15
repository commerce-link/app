package pl.commercelink.marketplace;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.commercelink.scheduling.EventBridgeSchedules;
import pl.commercelink.scheduling.PollingSchedule;
import pl.commercelink.starter.util.ConversionUtil;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class MarketplaceOrdersImportScheduler {

    private final String ordersImportQueueArn;
    private final int defaultIntervalMinutes;
    private final EventBridgeSchedules schedules;

    public MarketplaceOrdersImportScheduler(@Value("${sqs.orders-import.queue.arn}") String ordersImportQueueArn,
                                            @Value("${marketplace.orders-import.default-interval-minutes}") int defaultIntervalMinutes,
                                            EventBridgeSchedules schedules) {
        this.ordersImportQueueArn = ordersImportQueueArn;
        this.defaultIntervalMinutes = defaultIntervalMinutes;
        this.schedules = schedules;
    }

    public int defaultIntervalMinutes() {
        return defaultIntervalMinutes;
    }

    public void apply(String storeId, String marketplace, String ordersImportSchedule) {
        schedules.put(
                scheduleName(storeId, marketplace),
                PollingSchedule.storedOrRandomEveryMinutes(ordersImportSchedule, defaultIntervalMinutes).awsExpression(),
                ordersImportQueueArn,
                ConversionUtil.toJson(importRequest(storeId, marketplace)));
    }

    public void delete(String storeId, String marketplace) {
        schedules.delete(scheduleName(storeId, marketplace));
    }

    public Optional<String> snapshot(String storeId, String marketplace) {
        return schedules.expressionOf(scheduleName(storeId, marketplace));
    }

    public void restore(String storeId, String marketplace, Optional<String> snapshot) {
        String name = scheduleName(storeId, marketplace);
        if (snapshot.isPresent()) {
            schedules.put(name, snapshot.get(), ordersImportQueueArn,
                    ConversionUtil.toJson(importRequest(storeId, marketplace)));
        } else {
            schedules.delete(name);
        }
    }

    private Map<String, String> importRequest(String storeId, String marketplace) {
        Map<String, String> request = new LinkedHashMap<>();
        request.put("marketplace", marketplace);
        request.put("storeId", storeId);
        return request;
    }

    private String scheduleName(String storeId, String marketplace) {
        return "orders-import-" + storeId + "-" + marketplace.toLowerCase().replaceAll("[^a-z0-9]", "-");
    }
}
