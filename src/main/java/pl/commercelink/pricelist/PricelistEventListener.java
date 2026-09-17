package pl.commercelink.pricelist;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.scheduling.ScheduledExecutionCounter;
import pl.commercelink.scheduling.ScheduledExecution;
import pl.commercelink.stores.SupplierScope;

import java.io.IOException;
import java.util.List;

@Service
@ConditionalOnProperty(name = "application.env", havingValue = "prod", matchIfMissing = false)
@RequiredArgsConstructor
class PricelistEventListener {

    private final Inventory inventory;
    private final PricelistRepository pricelistRepository;
    private final PricelistEventPublisher pricelistEventPublisher;
    private final AvailabilityAndPriceListFactory availabilityAndPriceListFactory;
    private final SellingPriceHistoryService sellingPriceHistoryService;
    private final ScheduledExecutionCounter scheduledExecutionCounter;

    @SqsListener(
            value = "catalog-pricelist-queue",
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    void handlePricelistEvent(PricelistEventPayload payload) throws IOException {
        InventoryView enrichedInventory = inventory.withEnabledSuppliersAndWarehouseData(payload.getStoreId(), SupplierScope.PRICING);

        List<AvailabilityAndPrice> pricelist = availabilityAndPriceListFactory
                .create(enrichedInventory)
                .generate(payload.getStoreId(), payload.getCatalogId());

        String pricelistId = pricelistRepository.save(payload.getStoreId(), payload.getCatalogId(), pricelist);

        sellingPriceHistoryService.update(payload.getStoreId(), payload.getCatalogId(), pricelist);

        pricelistEventPublisher.publish(payload.getStoreId(), payload.getCatalogId(), pricelistId);

        scheduledExecutionCounter.countCompleted(payload.getStoreId(), ScheduledExecution.PRICELIST, payload.getCatalogId());
    }

}
