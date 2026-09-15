package pl.commercelink.marketplace;

import pl.commercelink.scheduling.PollingScheduleDescription;

import java.time.LocalDateTime;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

public record MarketplaceIntegrationView(
        String name,
        String displayName,
        boolean connected,
        boolean deviceAuth,
        LocalDateTime lastFetchedAt,
        String ordersImportSchedule,
        String returnsImportSchedule) {

    public boolean hasOwnSchedule() {
        return isNotBlank(ordersImportSchedule);
    }

    public PollingScheduleDescription scheduleDescription() {
        return PollingScheduleDescription.of(ordersImportSchedule);
    }

    public boolean hasOwnReturnsSchedule() {
        return isNotBlank(returnsImportSchedule);
    }

    public PollingScheduleDescription returnsScheduleDescription() {
        return PollingScheduleDescription.of(returnsImportSchedule);
    }

    public boolean hasFetched() {
        return lastFetchedAt != null;
    }
}
