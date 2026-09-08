package pl.commercelink.pricelist;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.commercelink.scheduling.EventBridgeSchedules;
import pl.commercelink.scheduling.PollingSchedule;
import pl.commercelink.starter.util.ConversionUtil;

import static org.apache.commons.lang3.StringUtils.isBlank;

@Component
public class PricelistEventScheduler {

    @Value("${sqs.pricelist.queue.arn}")
    private String pricelistQueueArn;

    @Autowired
    private EventBridgeSchedules schedules;

    public void createRecurringSchedule(String storeId, String catalogId, String pricelistSchedule) {
        putSchedule(storeId, catalogId, pricelistSchedule);
    }

    public void updateSchedule(String storeId, String catalogId, String pricelistSchedule) {
        putSchedule(storeId, catalogId, pricelistSchedule);
    }

    public void deleteSchedule(String storeId, String catalogId) {
        schedules.delete(scheduleName(storeId, catalogId));
    }

    private void putSchedule(String storeId, String catalogId, String pricelistSchedule) {
        schedules.put(
                scheduleName(storeId, catalogId),
                scheduleExpression(pricelistSchedule),
                pricelistQueueArn,
                ConversionUtil.toJson(new PricelistEventPayload(storeId, catalogId)));
    }

    private String scheduleExpression(String pricelistSchedule) {
        if (isBlank(pricelistSchedule)) {
            return PollingSchedule.randomNightly().awsExpression();
        }
        return "cron(" + pricelistSchedule.trim() + ")";
    }

    private String scheduleName(String storeId, String catalogId) {
        return "pricelist-" + storeId + "-" + catalogId;
    }
}
