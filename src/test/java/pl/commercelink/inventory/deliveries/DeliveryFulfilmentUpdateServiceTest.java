package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.warehouse.builtin.WarehouseAllocationsManager;
import pl.commercelink.web.dtos.DeliveryFulfilmentUpdateForm;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeliveryFulfilmentUpdateServiceTest {

    private static final String STORE_ID = "store-1";
    private static final String PROVIDER = "TechData";

    @Mock
    private OrderAllocationsManager orderAllocationsManager;
    @Mock
    private WarehouseAllocationsManager warehouseAllocationsManager;

    @InjectMocks
    private DeliveryFulfilmentUpdateService deliveryFulfilmentUpdateService;

    @Test
    @DisplayName("run delegates normalized fulfilment data to order and warehouse managers")
    void runDelegatesNormalizedDataToManagers() {
        // given
        when(orderAllocationsManager.updateFulfilment(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyDouble()))
                .thenReturn(true);
        when(warehouseAllocationsManager.updateFulfilment(anyString(), anyString(), anyString(), anyString(), anyString(), anyDouble()))
                .thenReturn(true);
        DeliveryFulfilmentUpdateForm form = form(" new-ean ", " new-mfn ", 99.99,
                List.of(allocationRef("order-1", "item-1"), allocationRef("order-2", "item-2")),
                List.of("warehouse-item-1"));

        // when
        OperationResult<Void> result = deliveryFulfilmentUpdateService.run(STORE_ID, PROVIDER, form);

        // then
        assertThat(result.isSuccess()).isTrue();
        verify(orderAllocationsManager).updateFulfilment(STORE_ID, PROVIDER, "order-1", "item-1", "new-ean", "NEW-MFN", 99.99);
        verify(orderAllocationsManager).updateFulfilment(STORE_ID, PROVIDER, "order-2", "item-2", "new-ean", "NEW-MFN", 99.99);
        verify(warehouseAllocationsManager).updateFulfilment(STORE_ID, PROVIDER, "warehouse-item-1", "new-ean", "NEW-MFN", 99.99);
    }

    @Test
    @DisplayName("run returns not-editable failure when no allocation was updated")
    void runReturnsFailureWhenNothingUpdated() {
        // given
        when(orderAllocationsManager.updateFulfilment(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyDouble()))
                .thenReturn(false);
        when(warehouseAllocationsManager.updateFulfilment(anyString(), anyString(), anyString(), anyString(), anyString(), anyDouble()))
                .thenReturn(false);
        DeliveryFulfilmentUpdateForm form = form("new-ean", "new-mfn", 99.99,
                List.of(allocationRef("order-1", "item-1")), List.of("warehouse-item-1"));

        // when
        OperationResult<Void> result = deliveryFulfilmentUpdateService.run(STORE_ID, PROVIDER, form);

        // then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo("error.message.delivery.fulfilment.not.editable");
    }

    @Test
    @DisplayName("run returns partial failure when only some allocations were updated")
    void runReturnsPartialFailureWhenOnlySomeAllocationsUpdated() {
        // given
        when(orderAllocationsManager.updateFulfilment(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyDouble()))
                .thenReturn(true);
        when(warehouseAllocationsManager.updateFulfilment(anyString(), anyString(), anyString(), anyString(), anyString(), anyDouble()))
                .thenReturn(false);
        DeliveryFulfilmentUpdateForm form = form("new-ean", "new-mfn", 99.99,
                List.of(allocationRef("order-1", "item-1")), List.of("warehouse-item-1"));

        // when
        OperationResult<Void> result = deliveryFulfilmentUpdateService.run(STORE_ID, PROVIDER, form);

        // then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo("error.message.delivery.fulfilment.partial");
    }

    @Test
    @DisplayName("run rejects blank manufacturer code without touching managers")
    void runRejectsBlankMfn() {
        // given
        DeliveryFulfilmentUpdateForm form = form("new-ean", " ", 99.99,
                List.of(allocationRef("order-1", "item-1")), List.of("warehouse-item-1"));

        // when
        OperationResult<Void> result = deliveryFulfilmentUpdateService.run(STORE_ID, PROVIDER, form);

        // then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo("error.message.delivery.fulfilment.invalid");
        verifyNoInteractions(orderAllocationsManager, warehouseAllocationsManager);
    }

    @Test
    @DisplayName("run rejects blank EAN without touching managers")
    void runRejectsBlankEan() {
        // given
        DeliveryFulfilmentUpdateForm form = form(" ", "new-mfn", 99.99,
                List.of(allocationRef("order-1", "item-1")), List.of());

        // when
        OperationResult<Void> result = deliveryFulfilmentUpdateService.run(STORE_ID, PROVIDER, form);

        // then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo("error.message.delivery.fulfilment.invalid");
        verifyNoInteractions(orderAllocationsManager, warehouseAllocationsManager);
    }

    @Test
    @DisplayName("run rejects negative cost without touching managers")
    void runRejectsNegativeCost() {
        // given
        DeliveryFulfilmentUpdateForm form = form("new-ean", "new-mfn", -1,
                List.of(allocationRef("order-1", "item-1")), List.of());

        // when
        OperationResult<Void> result = deliveryFulfilmentUpdateService.run(STORE_ID, PROVIDER, form);

        // then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo("error.message.delivery.fulfilment.invalid");
        verifyNoInteractions(orderAllocationsManager, warehouseAllocationsManager);
    }

    private DeliveryFulfilmentUpdateForm form(String ean, String mfn, double unitCost,
                                              List<DeliveryFulfilmentUpdateForm.AllocationRef> allocations,
                                              List<String> warehouseItemIds) {
        DeliveryFulfilmentUpdateForm form = new DeliveryFulfilmentUpdateForm();
        form.setEan(ean);
        form.setMfn(mfn);
        form.setUnitCost(unitCost);
        form.setAllocations(allocations);
        form.setWarehouseItemIds(warehouseItemIds);
        return form;
    }

    private DeliveryFulfilmentUpdateForm.AllocationRef allocationRef(String orderId, String itemId) {
        DeliveryFulfilmentUpdateForm.AllocationRef ref = new DeliveryFulfilmentUpdateForm.AllocationRef();
        ref.setOrderId(orderId);
        ref.setItemId(itemId);
        return ref;
    }
}
