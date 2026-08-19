package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.warehouse.builtin.WarehouseAllocationsManager;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryCostSyncTest {

    @Mock
    private OrderAllocationsManager orderAllocationsManager;

    @Mock
    private WarehouseAllocationsManager warehouseAllocationsManager;

    @InjectMocks
    private DeliveryCostSync deliveryCostSync;

    @Test
    void applySumsOrderAndWarehouseDeltas() {
        // given
        Map<String, Double> costs = Map.of("MFN-1", 12.0);
        when(warehouseAllocationsManager.updateUnitCosts("store-1", "delivery-1", costs)).thenReturn(7.5);
        when(orderAllocationsManager.updateUnitCosts("delivery-1", costs)).thenReturn(20.0);

        // when
        double delta = deliveryCostSync.apply("store-1", "delivery-1", costs);

        // then
        assertThat(delta).isEqualTo(27.5);
    }

    @Test
    void applyDoesNothingForEmptyCosts() {
        // when
        double delta = deliveryCostSync.apply("store-1", "delivery-1", Map.of());

        // then
        assertThat(delta).isZero();
        verify(warehouseAllocationsManager, never()).updateUnitCosts(anyString(), anyString(), any());
        verify(orderAllocationsManager, never()).updateUnitCosts(anyString(), any());
    }
}
