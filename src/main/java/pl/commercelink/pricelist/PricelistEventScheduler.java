package pl.commercelink.pricelist;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.commercelink.scheduling.EventBridgeSchedules;
import pl.commercelink.scheduling.PollingSchedule;
import pl.commercelink.starter.util.ConversionUtil;

@Component
public class PricelistEventScheduler {

    private final String pricelistQueueArn;
    private final EventBridgeSchedules schedules;

    public PricelistEventScheduler(@Value("${sqs.pricelist.queue.arn}") String pricelistQueueArn,
                                   EventBridgeSchedules schedules) {
        this.pricelistQueueArn = pricelistQueueArn;
        this.schedules = schedules;
    }

    public void schedule(String storeId, String catalogId, String pricelistSchedule) {
        schedules.put(
                scheduleName(storeId, catalogId),
                PollingSchedule.storedOrRandomNightly(pricelistSchedule).awsExpression(),
                pricelistQueueArn,
                ConversionUtil.toJson(new PricelistEventPayload(storeId, catalogId)));
    }

    public void deleteSchedule(String storeId, String catalogId) {
        schedules.delete(scheduleName(storeId, catalogId));
    }

    private String scheduleName(String storeId, String catalogId) {
        return "pricelist-" + storeId + "-" + catalogId;
    }
}
