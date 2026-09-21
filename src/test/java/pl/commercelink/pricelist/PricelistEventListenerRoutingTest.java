package pl.commercelink.pricelist;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.scheduling.ScheduledExecutionCounter;
import pl.commercelink.scheduling.ScheduledExecution;
import pl.commercelink.stores.SupplierScope;

import java.io.IOException;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PricelistEventListenerRoutingTest {

    private static final String STORE_ID = "store-1";
    private static final String CATALOG_ID = "catalog-1";

    @Mock
    private Inventory inventory;
    @Mock
    private PricelistRepository pricelistRepository;
    @Mock
    private PricelistEventPublisher pricelistEventPublisher;
    @Mock
    private AvailabilityAndPriceListFactory availabilityAndPriceListFactory;
    @Mock
    private SellingPriceHistoryService sellingPriceHistoryService;
    @Mock
    private InventoryView inventoryView;
    @Mock
    private AvailabilityAndPriceList availabilityAndPriceList;
    @Mock
    private ScheduledExecutionCounter scheduledExecutionCounter;

    @InjectMocks
    private PricelistEventListener listener;

    @Test
    void handlePricelistEventCallsInventoryWithPricingScope() throws IOException {
        // given
        when(inventory.withEnabledSuppliersAndWarehouseData(STORE_ID, SupplierScope.PRICING)).thenReturn(inventoryView);
        when(availabilityAndPriceListFactory.create(inventoryView)).thenReturn(availabilityAndPriceList);
        when(availabilityAndPriceList.generate(STORE_ID, CATALOG_ID)).thenReturn(List.of());

        PricelistEventPayload payload = new PricelistEventPayload(STORE_ID, CATALOG_ID);

        // when
        listener.handlePricelistEvent(payload);

        // then
        verify(inventory).withEnabledSuppliersAndWarehouseData(eq(STORE_ID), eq(SupplierScope.PRICING));
    }

    @Test
    void completedPricelistCalculationIsRecordedAfterPublishing() throws IOException {
        // given
        when(inventory.withEnabledSuppliersAndWarehouseData(STORE_ID, SupplierScope.PRICING)).thenReturn(inventoryView);
        when(availabilityAndPriceListFactory.create(inventoryView)).thenReturn(availabilityAndPriceList);
        when(availabilityAndPriceList.generate(STORE_ID, CATALOG_ID)).thenReturn(List.of());
        when(pricelistRepository.save(STORE_ID, CATALOG_ID, List.of())).thenReturn("pricelist-1");

        // when
        listener.handlePricelistEvent(new PricelistEventPayload(STORE_ID, CATALOG_ID));

        // then
        InOrder inOrder = inOrder(pricelistEventPublisher, scheduledExecutionCounter);
        inOrder.verify(pricelistEventPublisher).publish(STORE_ID, CATALOG_ID, "pricelist-1");
        inOrder.verify(scheduledExecutionCounter).countCompleted(STORE_ID, ScheduledExecution.PRICELIST, CATALOG_ID);
    }

    @Test
    void failedPricelistCalculationIsNotRecorded() {
        // given
        when(inventory.withEnabledSuppliersAndWarehouseData(STORE_ID, SupplierScope.PRICING)).thenReturn(inventoryView);
        when(availabilityAndPriceListFactory.create(inventoryView)).thenReturn(availabilityAndPriceList);
        when(availabilityAndPriceList.generate(STORE_ID, CATALOG_ID)).thenThrow(new IllegalStateException("inventory unavailable"));

        // when / then
        assertThrows(IllegalStateException.class, () -> listener.handlePricelistEvent(new PricelistEventPayload(STORE_ID, CATALOG_ID)));
        verifyNoInteractions(scheduledExecutionCounter);
    }
}
