package pl.commercelink.pricelist;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.commercelink.scheduling.EventBridgeSchedules;
import pl.commercelink.scheduling.PollingSchedule;
import pl.commercelink.starter.util.ConversionUtil;

import java.util.Optional;

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
                PollingSchedule.storedOrRandomNightly(pricelistSchedule).withRandomStart().awsExpression(),
                pricelistQueueArn,
                ConversionUtil.toJson(new PricelistEventPayload(storeId, catalogId)));
    }

    public void deleteSchedule(String storeId, String catalogId) {
        schedules.delete(scheduleName(storeId, catalogId));
    }

    public Optional<String> snapshot(String storeId, String catalogId) {
        return schedules.expressionOf(scheduleName(storeId, catalogId));
    }

    public void restore(String storeId, String catalogId, Optional<String> snapshot) {
        String name = scheduleName(storeId, catalogId);
        if (snapshot.isPresent()) {
            schedules.put(name, snapshot.get(), pricelistQueueArn,
                    ConversionUtil.toJson(new PricelistEventPayload(storeId, catalogId)));
        } else {
            schedules.delete(name);
        }
    }

    private String scheduleName(String storeId, String catalogId) {
        return "pricelist-" + storeId + "-" + catalogId;
    }
}
