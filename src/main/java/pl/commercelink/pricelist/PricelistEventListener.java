package pl.commercelink.pricelist;

import io.awspring.cloud.sqs.annotation.SqsListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryView;

import java.io.IOException;
import java.util.List;

@Service
@ConditionalOnProperty(name = "application.env", havingValue = "prod", matchIfMissing = false)
class PricelistEventListener {

    @Autowired
    private Inventory inventory;
    @Autowired
    private PricelistRepository pricelistRepository;
    @Autowired
    private PricelistEventPublisher pricelistEventPublisher;
    @Autowired
    private AvailabilityAndPriceListFactory availabilityAndPriceListFactory;
    @Autowired
    private SellingPriceHistoryService sellingPriceHistoryService;

    @SqsListener(
            value = "catalog-pricelist-queue",
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    void handlePricelistEvent(PricelistEventPayload payload) throws IOException {
        InventoryView enrichedInventory = inventory.withEnabledSuppliersAndWarehouseData(payload.getStoreId());

        List<AvailabilityAndPrice> pricelist = availabilityAndPriceListFactory
                .create(enrichedInventory)
                .generate(payload.getStoreId(), payload.getCatalogId());

        String pricelistId = pricelistRepository.save(payload.getCatalogId(), pricelist);

        sellingPriceHistoryService.update(payload.getCatalogId(), pricelist);

        pricelistEventPublisher.publish(payload.getStoreId(), payload.getCatalogId(), pricelistId);
    }

}
