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
        ImportScheduleView orders,
        ImportScheduleView returns,
        boolean supportsReturns) {

    public boolean hasFetched() {
        return lastFetchedAt != null;
    }

    public record ImportScheduleView(String expression) {

        public boolean hasOwn() {
            return isNotBlank(expression);
        }

        public PollingScheduleDescription description() {
            return PollingScheduleDescription.of(expression);
        }
    }
}
