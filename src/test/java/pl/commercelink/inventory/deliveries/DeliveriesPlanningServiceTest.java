package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.warehouse.builtin.WarehouseAllocationsManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveriesPlanningServiceTest {

    private static final String STORE_ID = "store-1";

    @Mock
    private OrderAllocationsManager orderAllocationsManager;
    @Mock
    private WarehouseAllocationsManager warehouseAllocationsManager;
    @Mock
    private SupplierPurchaseService supplierPurchaseService;

    @InjectMocks
    private DeliveriesPlanningService service;

    private static Allocation allocation(String orderId, String itemId, String provider,
                                         boolean directToConsumer) {
        Allocation allocation = new Allocation();
        allocation.setKey(new AllocationKey(orderId, itemId, "customer@example.com"));
        allocation.setType(AllocationType.Order);
        allocation.setName("Product " + itemId);
        allocation.setQty(1);
        allocation.setDeliveryId(provider);
        allocation.setEan("59000000000" + itemId);
        allocation.setMfn("MFN-" + itemId);
        allocation.setUnitCost(100.0);
        allocation.setInAllocation(true);
        allocation.setDirectToConsumer(directToConsumer);
        return allocation;
    }

    @Test
    void dropshipCandidatesAreKeptOutOfSupplierBatches() {
        // given
        when(supplierPurchaseService.isDropshipAvailable(STORE_ID, "Acme")).thenReturn(true);
        when(orderAllocationsManager.fetchAll(STORE_ID)).thenReturn(List.of(
                allocation("order-1", "1", "Acme", false),
                allocation("order-2", "2", "Acme", true)));
        when(warehouseAllocationsManager.fetchAll(STORE_ID)).thenReturn(List.of());

        // when
        List<Delivery> deliveries = service.run(STORE_ID);

        // then
        assertThat(deliveries).hasSize(1);
        assertThat(deliveries.getFirst().getAllocations())
                .allMatch(allocation -> !allocation.isDirectToConsumer());
    }

    @Test
    void dropshipCandidatesGroupDirectToConsumerAllocationsPerOrder() {
        // given
        when(supplierPurchaseService.isDropshipAvailable(STORE_ID, "Acme")).thenReturn(true);
        when(orderAllocationsManager.fetchAll(STORE_ID)).thenReturn(List.of(
                allocation("order-1", "1", "Acme", false),
                allocation("order-2", "2", "Acme", true),
                allocation("order-2", "3", "Acme", true),
                allocation("order-3", "4", "Acme", true)));

        // when
        List<DropshipCandidate> candidates = service.dropshipCandidates(STORE_ID);

        // then
        assertThat(candidates).hasSize(2);
        DropshipCandidate first = candidates.stream()
                .filter(c -> c.orderId().equals("order-2")).findFirst().orElseThrow();
        assertThat(first.provider()).isEqualTo("Acme");
        assertThat(first.customer()).isEqualTo("customer");
        assertThat(first.items()).hasSize(2);
    }

    @Test
    void directToConsumerOrderAtASupplierWithoutDropshipFallsBackToTheBatch() {
        // given
        when(supplierPurchaseService.isDropshipAvailable(STORE_ID, "AcmeB")).thenReturn(false);
        when(orderAllocationsManager.fetchAll(STORE_ID)).thenReturn(List.of(
                allocation("order-2", "2", "AcmeB", true)));
        when(warehouseAllocationsManager.fetchAll(STORE_ID)).thenReturn(List.of());

        // when
        List<DropshipCandidate> candidates = service.dropshipCandidates(STORE_ID);
        List<Delivery> deliveries = service.run(STORE_ID);

        // then
        assertThat(candidates).isEmpty();
        assertThat(deliveries).hasSize(1);
        assertThat(deliveries.getFirst().getProvider()).isEqualTo("AcmeB");
        assertThat(deliveries.getFirst().getAllocations()).hasSize(1);
    }

    @Test
    void directToConsumerOrderSplitAcrossSuppliersFallsBackToTheBatches() {
        // given
        lenient().when(supplierPurchaseService.isDropshipAvailable(STORE_ID, "Acme")).thenReturn(true);
        when(orderAllocationsManager.fetchAll(STORE_ID)).thenReturn(List.of(
                allocation("order-2", "2", "Acme", true),
                allocation("order-2", "3", "Elko", true)));
        when(warehouseAllocationsManager.fetchAll(STORE_ID)).thenReturn(List.of());

        // when
        List<DropshipCandidate> candidates = service.dropshipCandidates(STORE_ID);
        List<Delivery> deliveries = service.run(STORE_ID);

        // then
        assertThat(candidates).isEmpty();
        assertThat(deliveries).hasSize(2);
    }

    @Test
    void warehouseFulfilmentOrdersProduceNoDropshipCandidates() {
        // given
        when(orderAllocationsManager.fetchAll(STORE_ID)).thenReturn(List.of(
                allocation("order-1", "1", "Acme", false)));

        // when / then
        assertThat(service.dropshipCandidates(STORE_ID)).isEmpty();
    }
}
