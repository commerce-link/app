package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.warehouse.builtin.WarehouseAllocationsManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveriesPlanningServiceTest {

    private static final String STORE_ID = "store-1";

    @Mock
    private OrderAllocationsManager orderAllocationsManager;
    @Mock
    private WarehouseAllocationsManager warehouseAllocationsManager;
    @Mock
    private OrdersRepository ordersRepository;
    @Mock
    private OrderItemsRepository orderItemsRepository;
    @Mock
    private DropshipEligibility dropshipEligibility;

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
        Order order = new Order();
        when(orderAllocationsManager.fetchAll(STORE_ID)).thenReturn(List.of(
                allocation("order-1", "1", "Acme", false),
                allocation("order-2", "2", "Acme", true)));
        when(warehouseAllocationsManager.fetchAll(STORE_ID)).thenReturn(List.of());
        when(ordersRepository.findById(STORE_ID, "order-2")).thenReturn(order);
        when(orderItemsRepository.findByOrderId("order-2")).thenReturn(List.of());
        when(dropshipEligibility.eligibleProvider(order, List.of())).thenReturn(Optional.of("Acme"));

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
        Order order2 = new Order();
        Order order3 = new Order();
        when(orderAllocationsManager.fetchAll(STORE_ID)).thenReturn(List.of(
                allocation("order-1", "1", "Acme", false),
                allocation("order-2", "2", "Acme", true),
                allocation("order-2", "3", "Acme", true),
                allocation("order-3", "4", "Acme", true)));
        when(ordersRepository.findById(STORE_ID, "order-2")).thenReturn(order2);
        when(orderItemsRepository.findByOrderId("order-2")).thenReturn(List.of());
        when(dropshipEligibility.eligibleProvider(order2, List.of())).thenReturn(Optional.of("Acme"));
        when(ordersRepository.findById(STORE_ID, "order-3")).thenReturn(order3);
        when(orderItemsRepository.findByOrderId("order-3")).thenReturn(List.of());
        when(dropshipEligibility.eligibleProvider(order3, List.of())).thenReturn(Optional.of("Acme"));

        // when
        List<DropshipCandidate> candidates = service.plan(STORE_ID).dropshipCandidates();

        // then
        assertThat(candidates).hasSize(2);
        DropshipCandidate first = candidates.stream()
                .filter(c -> c.orderId().equals("order-2")).findFirst().orElseThrow();
        assertThat(first.provider()).isEqualTo("Acme");
        assertThat(first.items()).hasSize(2);
    }

    @Test
    void directToConsumerOrderAtASupplierWithoutDropshipFallsBackToTheBatch() {
        // given
        Order order = new Order();
        when(orderAllocationsManager.fetchAll(STORE_ID)).thenReturn(List.of(
                allocation("order-2", "2", "AcmeB", true)));
        when(warehouseAllocationsManager.fetchAll(STORE_ID)).thenReturn(List.of());
        when(ordersRepository.findById(STORE_ID, "order-2")).thenReturn(order);
        when(orderItemsRepository.findByOrderId("order-2")).thenReturn(List.of());
        when(dropshipEligibility.eligibleProvider(order, List.of())).thenReturn(Optional.empty());

        // when
        List<DropshipCandidate> candidates = service.plan(STORE_ID).dropshipCandidates();
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
        Order order = new Order();
        when(orderAllocationsManager.fetchAll(STORE_ID)).thenReturn(List.of(
                allocation("order-2", "2", "Acme", true),
                allocation("order-2", "3", "Elko", true)));
        when(warehouseAllocationsManager.fetchAll(STORE_ID)).thenReturn(List.of());
        when(ordersRepository.findById(STORE_ID, "order-2")).thenReturn(order);
        when(orderItemsRepository.findByOrderId("order-2")).thenReturn(List.of());
        when(dropshipEligibility.eligibleProvider(order, List.of())).thenReturn(Optional.empty());

        // when
        List<DropshipCandidate> candidates = service.plan(STORE_ID).dropshipCandidates();
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
        assertThat(service.plan(STORE_ID).dropshipCandidates()).isEmpty();
    }

    @Test
    void ineligibleDirectToConsumerOrderStaysInBatchAndYieldsNoCandidate() {
        // given
        Allocation dtc = allocation("order-1", "1", "Acme", true);
        when(orderAllocationsManager.fetchAll(STORE_ID)).thenReturn(List.of(dtc));
        when(warehouseAllocationsManager.fetchAll(STORE_ID)).thenReturn(List.of());
        Order order = new Order();
        when(ordersRepository.findById(STORE_ID, "order-1")).thenReturn(order);
        when(orderItemsRepository.findByOrderId("order-1")).thenReturn(List.of());
        when(dropshipEligibility.eligibleProvider(order, List.of())).thenReturn(Optional.empty());

        // when
        DeliveriesPlanningService.Planning planning = service.plan(STORE_ID);

        // then
        assertThat(planning.dropshipCandidates()).isEmpty();
        assertThat(planning.deliveries()).hasSize(1);
    }

    @Test
    void planFetchesOrderAllocationsOnlyOnce() {
        // given
        when(orderAllocationsManager.fetchAll(STORE_ID)).thenReturn(List.of());
        when(warehouseAllocationsManager.fetchAll(STORE_ID)).thenReturn(List.of());

        // when
        DeliveriesPlanningService.Planning planning = service.plan(STORE_ID);

        // then
        assertThat(planning.deliveries()).isEmpty();
        assertThat(planning.dropshipCandidates()).isEmpty();
        verify(orderAllocationsManager, times(1)).fetchAll(STORE_ID);
    }
}
