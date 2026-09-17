package pl.commercelink.marketplace;

import pl.commercelink.scheduling.EventBridgeSchedules;
import pl.commercelink.scheduling.PollingSchedule;
import pl.commercelink.starter.util.ConversionUtil;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public abstract class MarketplaceImportScheduler {

    private final String namePrefix;
    private final String queueArn;
    private final int defaultIntervalMinutes;
    private final EventBridgeSchedules schedules;

    protected MarketplaceImportScheduler(String namePrefix, String queueArn, int defaultIntervalMinutes,
                                         EventBridgeSchedules schedules) {
        this.namePrefix = namePrefix;
        this.queueArn = queueArn;
        this.defaultIntervalMinutes = defaultIntervalMinutes;
        this.schedules = schedules;
    }

    public int defaultIntervalMinutes() {
        return defaultIntervalMinutes;
    }

    public void apply(String storeId, String marketplace, String schedule) {
        schedules.put(
                scheduleName(storeId, marketplace),
                PollingSchedule.storedOrEveryMinutes(schedule, defaultIntervalMinutes).withRandomStart().awsExpression(),
                queueArn,
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
            schedules.put(name, snapshot.get(), queueArn, ConversionUtil.toJson(importRequest(storeId, marketplace)));
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
        return namePrefix + storeId + "-" + marketplace.toLowerCase().replaceAll("[^a-z0-9]", "-");
    }
}
