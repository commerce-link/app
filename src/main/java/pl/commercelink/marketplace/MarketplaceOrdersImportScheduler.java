package pl.commercelink.marketplace;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.commercelink.scheduling.EventBridgeSchedules;
import pl.commercelink.starter.util.ConversionUtil;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.isBlank;

@Component
public class MarketplaceOrdersImportScheduler {

    @Value("${sqs.orders-import.queue.arn}")
    private String ordersImportQueueArn;

    @Autowired
    private EventBridgeSchedules schedules;

    public void apply(String storeId, String marketplace, String ordersImportSchedule) {
        if (isBlank(ordersImportSchedule)) {
            delete(storeId, marketplace);
            return;
        }
        schedules.put(
                scheduleName(storeId, marketplace),
                "cron(" + ordersImportSchedule.trim() + ")",
                ordersImportQueueArn,
                ConversionUtil.toJson(importRequest(storeId, marketplace)));
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
