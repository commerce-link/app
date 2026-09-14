package pl.commercelink.marketplace;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.commercelink.scheduling.EventBridgeSchedules;
import pl.commercelink.scheduling.PollingSchedule;
import pl.commercelink.starter.util.ConversionUtil;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.isBlank;

@Component
public class MarketplaceOrdersImportScheduler {

    private static final int FLEXIBLE_WINDOW_MINUTES = 1;

    private final String ordersImportQueueArn;
    private final EventBridgeSchedules schedules;

    public MarketplaceOrdersImportScheduler(@Value("${sqs.orders-import.queue.arn}") String ordersImportQueueArn,
                                            EventBridgeSchedules schedules) {
        this.ordersImportQueueArn = ordersImportQueueArn;
        this.schedules = schedules;
    }

    public void apply(String storeId, String marketplace, String ordersImportSchedule) {
        if (isBlank(ordersImportSchedule)) {
            delete(storeId, marketplace);
            return;
        }
        schedules.put(
                scheduleName(storeId, marketplace),
                PollingSchedule.stored(ordersImportSchedule).awsExpression(),
                ordersImportQueueArn,
                ConversionUtil.toJson(importRequest(storeId, marketplace)),
                FLEXIBLE_WINDOW_MINUTES);
    }

    public void delete(String storeId, String marketplace) {
        schedules.delete(scheduleName(storeId, marketplace));
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
