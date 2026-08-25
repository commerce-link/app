package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.deliveries.DropshipTrackingService.TrackingOutcome;
import pl.commercelink.inventory.supplier.SupplierProviderResolver;
import pl.commercelink.inventory.supplier.api.SupplierOrderLine;
import pl.commercelink.inventory.supplier.api.SupplierOrderState;
import pl.commercelink.inventory.supplier.api.SupplierOrderTracking;
import pl.commercelink.inventory.supplier.api.SupplierParcel;
import pl.commercelink.inventory.supplier.api.SupplierProvider;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentType;
import org.mockito.stubbing.Answer;
import pl.commercelink.starter.util.OperationResult;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DropshipTrackingServiceParcelsTest {

    private static final String STORE_ID = "store-1";
    private static final String ORDER_ID = "order-1";
    private static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 25, 12, 0);
    private static final LocalDateTime SUPPLIER_SHIPPED_AT = LocalDateTime.of(2026, 8, 25, 9, 15);

    @Mock
    private DeliveriesQueryService deliveriesQueryService;
    @Mock
    private DeliveriesRepository deliveriesRepository;
    @Mock
    private OrdersRepository ordersRepository;
    @Mock
    private SupplierProviderResolver providerResolver;
    @Mock
    private DropshipDeliveryCompletion completion;
    @Mock
    private SupplierProvider provider;

    private DropshipTrackingService service;
    private Delivery delivery;
    private Order order;
    private Allocation first;
    private Allocation second;

    @BeforeEach
    void setUp() {
        service = new DropshipTrackingService(deliveriesQueryService, deliveriesRepository, ordersRepository,
                providerResolver, completion, DropshipTrackingProperties.defaults(),
                Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE));
        order = new Order(STORE_ID);
        order.setOrderId(ORDER_ID);
        BillingDetails billingDetails = new BillingDetails();
        billingDetails.setEmail("customer@example.com");
        order.setBillingDetails(billingDetails);
        delivery = new Delivery(STORE_ID, "ACME-DS-ref-1", "Acme");
        delivery.setType(DeliveryType.DROPSHIP);
        delivery.setPurchaseRef("ref-1");
        delivery.setOrderedAt(NOW.minusHours(2));
        first = allocation("1", "5900000000001", "MFN-1");
        second = allocation("2", "5900000000002", "MFN-2");
        delivery.setAllocations(List.of(first, second));
        when(deliveriesQueryService.fetchDeliveryWithAllocations(STORE_ID, delivery.getDeliveryId())).thenReturn(delivery);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(providerResolver.resolve(STORE_ID, "Acme")).thenReturn(provider);
        when(provider.supportsOrderTracking()).thenReturn(true);
    }

    private Allocation allocation(String itemId, String ean, String mfn) {
        OrderItem item = new OrderItem(ORDER_ID, "Category", "Product " + itemId, 1, 100.0, null, false);
        item.setItemId(itemId);
        item.setDeliveryId(delivery.getDeliveryId());
        item.setStatus(FulfilmentStatus.Ordered);
        item.setEan(ean);
        item.setManufacturerCode(mfn);
        Allocation allocation = Allocation.fromOrderItem(order, item);
        allocation.setInAllocation(true);
        return allocation;
    }

    private static SupplierParcel parcel(String trackingNo, SupplierOrderLine... lines) {
        return new SupplierParcel("DPD", trackingNo, "https://t/" + trackingNo, SUPPLIER_SHIPPED_AT, List.of(lines));
    }

    private void supplierReports(SupplierOrderState state, SupplierParcel... parcels) {
        when(provider.trackOrder(any())).thenReturn(Optional.of(new SupplierOrderTracking(state, List.of(parcels))));
    }

    private static Answer<OperationResult<DropshipShipmentResult>> confirmedAs(DropshipShipmentResult... results) {
        Iterator<DropshipShipmentResult> sequence = List.of(results).iterator();
        return invocation -> {
            DropshipShipmentResult result = sequence.next();
            BiConsumer<Delivery, DropshipShipmentResult> beforeSave = invocation.getArgument(5);
            beforeSave.accept(invocation.getArgument(1), result);
            return OperationResult.success(result);
        };
    }

    @Test
    void singleParcelWithoutLinesConfirmsEverythingAndCompletes() {
        // given
        supplierReports(SupplierOrderState.SHIPPED, parcel("PKG-1"));
        when(completion.confirmShipped(eq(STORE_ID), same(delivery), anyList(), anyList(), any(), any()))
                .thenAnswer(confirmedAs(DropshipShipmentResult.COMPLETED));

        // when
        TrackingOutcome outcome = service.check(STORE_ID, delivery.getDeliveryId(), false);

        // then
        assertThat(outcome).isEqualTo(TrackingOutcome.APPLIED);
        ArgumentCaptor<List<Allocation>> selected = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<Allocation>> remaining = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<DropshipShipment> shipment = ArgumentCaptor.forClass(DropshipShipment.class);
        verify(completion).confirmShipped(eq(STORE_ID), same(delivery), selected.capture(), remaining.capture(), shipment.capture(), any());
        assertThat(selected.getValue()).containsExactly(first, second);
        assertThat(remaining.getValue()).isEmpty();
        assertThat(shipment.getValue().type()).isEqualTo(ShipmentType.Courier);
        assertThat(shipment.getValue().carrier()).isEqualTo("DPD");
        assertThat(shipment.getValue().trackingNo()).isEqualTo("PKG-1");
        assertThat(shipment.getValue().trackingUrl()).isEqualTo("https://t/PKG-1");
        assertThat(shipment.getValue().shippedAt()).isEqualTo(SUPPLIER_SHIPPED_AT);
        assertThat(shipment.getValue().collectionPointCode()).isNull();
        assertThat(delivery.getTrackingState()).isEqualTo(DeliveryTrackingState.COMPLETED);
        assertThat(delivery.getTrackingNextCheckAt()).isNull();
        assertThat(delivery.getEvents()).extracting(e -> e.getName()).contains("DROPSHIP_TRACKING_APPLIED");
        verify(deliveriesRepository, never()).save(any());
    }

    @Test
    void missingShippedAtFallsBackToDetectionTime() {
        // given
        supplierReports(SupplierOrderState.SHIPPED, new SupplierParcel("DPD", "PKG-1", null, null, null));
        when(completion.confirmShipped(any(), any(), anyList(), anyList(), any(), any()))
                .thenAnswer(confirmedAs(DropshipShipmentResult.COMPLETED));

        // when
        service.check(STORE_ID, delivery.getDeliveryId(), false);

        // then
        ArgumentCaptor<DropshipShipment> shipment = ArgumentCaptor.forClass(DropshipShipment.class);
        verify(completion).confirmShipped(any(), any(), anyList(), anyList(), shipment.capture(), any());
        assertThat(shipment.getValue().shippedAt()).isEqualTo(NOW);
    }

    @Test
    void partialShipmentWithLinesConfirmsOnlyMatchingItemsAndKeepsPolling() {
        // given
        supplierReports(SupplierOrderState.PARTIALLY_SHIPPED,
                parcel("PKG-1", new SupplierOrderLine(null, "5900000000001", null, 1)));
        when(completion.confirmShipped(any(), any(), anyList(), anyList(), any(), any()))
                .thenAnswer(confirmedAs(DropshipShipmentResult.PARTIAL));

        // when
        TrackingOutcome outcome = service.check(STORE_ID, delivery.getDeliveryId(), false);

        // then
        assertThat(outcome).isEqualTo(TrackingOutcome.APPLIED);
        ArgumentCaptor<List<Allocation>> selected = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<Allocation>> remaining = ArgumentCaptor.forClass(List.class);
        verify(completion).confirmShipped(any(), any(), selected.capture(), remaining.capture(), any(), any());
        assertThat(selected.getValue()).containsExactly(first);
        assertThat(remaining.getValue()).containsExactly(second);
        assertThat(delivery.getTrackingState()).isEqualTo(DeliveryTrackingState.PENDING);
        assertThat(delivery.getTrackingNextCheckAt()).isEqualTo(NOW.plusMinutes(30));
    }

    @Test
    void twoParcelsInOneResponseAreAppliedInOrder() {
        // given
        supplierReports(SupplierOrderState.SHIPPED,
                parcel("PKG-1", new SupplierOrderLine(null, "5900000000001", null, 1)),
                parcel("PKG-2", new SupplierOrderLine(null, "5900000000002", null, 1)));
        when(completion.confirmShipped(any(), any(), anyList(), anyList(), any(), any()))
                .thenAnswer(confirmedAs(DropshipShipmentResult.PARTIAL, DropshipShipmentResult.COMPLETED));

        // when
        TrackingOutcome outcome = service.check(STORE_ID, delivery.getDeliveryId(), false);

        // then
        assertThat(outcome).isEqualTo(TrackingOutcome.APPLIED);
        ArgumentCaptor<DropshipShipment> shipments = ArgumentCaptor.forClass(DropshipShipment.class);
        verify(completion, times(2)).confirmShipped(any(), any(), anyList(), anyList(), shipments.capture(), any());
        assertThat(shipments.getAllValues()).extracting(DropshipShipment::trackingNo).containsExactly("PKG-1", "PKG-2");
        assertThat(delivery.getTrackingState()).isEqualTo(DeliveryTrackingState.COMPLETED);
    }

    @Test
    void shippedStateAbsorbsItemsNotCoveredByLinesIntoLastParcel() {
        // given
        supplierReports(SupplierOrderState.SHIPPED,
                parcel("PKG-1", new SupplierOrderLine(null, "5900000000001", null, 1)));
        when(completion.confirmShipped(any(), any(), anyList(), anyList(), any(), any()))
                .thenAnswer(confirmedAs(DropshipShipmentResult.COMPLETED));

        // when
        service.check(STORE_ID, delivery.getDeliveryId(), false);

        // then
        ArgumentCaptor<List<Allocation>> selected = ArgumentCaptor.forClass(List.class);
        verify(completion).confirmShipped(any(), any(), selected.capture(), anyList(), any(), any());
        assertThat(selected.getValue()).containsExactly(first, second);
    }

    @Test
    void parcelAlreadyOnTheOrderIsSkipped() {
        // given
        Shipment manual = new Shipment();
        manual.setTrackingNo("PKG-1");
        order.addShipment(manual);
        supplierReports(SupplierOrderState.SHIPPED, parcel("PKG-1"));

        // when
        TrackingOutcome outcome = service.check(STORE_ID, delivery.getDeliveryId(), false);

        // then
        assertThat(outcome).isEqualTo(TrackingOutcome.PROCESSING);
        verify(completion, never()).confirmShipped(any(), any(), anyList(), anyList(), any(), any());
        assertThat(delivery.getTrackingNextCheckAt()).isEqualTo(NOW.plusMinutes(30));
    }

    @Test
    void nothingToConfirmSkipsParcelWithoutFailing() {
        // given
        supplierReports(SupplierOrderState.SHIPPED, parcel("PKG-1"));
        when(completion.confirmShipped(any(), any(), anyList(), anyList(), any(), any()))
                .thenReturn(OperationResult.failure("deliveries.dropship.shipment.error.nothingToConfirm"));

        // when
        TrackingOutcome outcome = service.check(STORE_ID, delivery.getDeliveryId(), false);

        // then
        assertThat(outcome).isEqualTo(TrackingOutcome.PROCESSING);
        assertThat(delivery.getTrackingState()).isEqualTo(DeliveryTrackingState.PENDING);
        assertThat(delivery.getEvents()).extracting(e -> e.getName()).doesNotContain("DROPSHIP_TRACKING_APPLIED");
    }

    @Test
    void cancelledOrderGivesUp() {
        // given
        supplierReports(SupplierOrderState.SHIPPED, parcel("PKG-1"));
        when(completion.confirmShipped(any(), any(), anyList(), anyList(), any(), any()))
                .thenReturn(OperationResult.failure("deliveries.dropship.shipment.error.orderCancelled"));

        // when
        TrackingOutcome outcome = service.check(STORE_ID, delivery.getDeliveryId(), false);

        // then
        assertThat(outcome).isEqualTo(TrackingOutcome.GIVEN_UP);
        assertThat(delivery.getTrackingState()).isEqualTo(DeliveryTrackingState.GIVEN_UP);
        assertThat(delivery.getEvents()).extracting(e -> e.getName()).contains("DROPSHIP_TRACKING_GIVEN_UP");
    }

    @Test
    void noOpenAllocationsMeansNothingAppliedAndPollingContinues() {
        // given
        first.setInAllocation(false);
        second.setInAllocation(false);
        supplierReports(SupplierOrderState.SHIPPED, parcel("PKG-1"));

        // when
        TrackingOutcome outcome = service.check(STORE_ID, delivery.getDeliveryId(), false);

        // then
        assertThat(outcome).isEqualTo(TrackingOutcome.PROCESSING);
        verify(completion, never()).confirmShipped(any(), any(), anyList(), anyList(), any(), any());
    }

    @Test
    void twoAllocationsOfTheSameProductAreConfirmedIndependently() {
        // given
        Allocation firstTwin = allocation("3", "5900000000009", "MFN-9");
        Allocation secondTwin = allocation("4", "5900000000009", "MFN-9");
        delivery.setAllocations(List.of(firstTwin, secondTwin));
        supplierReports(SupplierOrderState.PARTIALLY_SHIPPED,
                parcel("PKG-1", new SupplierOrderLine(null, "5900000000009", null, 1)));
        when(completion.confirmShipped(any(), any(), anyList(), anyList(), any(), any()))
                .thenAnswer(confirmedAs(DropshipShipmentResult.COMPLETED));

        // when
        TrackingOutcome outcome = service.check(STORE_ID, delivery.getDeliveryId(), false);

        // then
        assertThat(outcome).isEqualTo(TrackingOutcome.APPLIED);
        ArgumentCaptor<List<Allocation>> selected = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<Allocation>> remaining = ArgumentCaptor.forClass(List.class);
        verify(completion).confirmShipped(any(), any(), selected.capture(), remaining.capture(), any(), any());
        assertThat(selected.getValue()).containsExactlyInAnyOrder(firstTwin, secondTwin);
        assertThat(remaining.getValue()).isEmpty();
    }

    @Test
    void absorbRuleAppliesToLastParcelNotAlreadyOnTheOrder() {
        // given
        Shipment already = new Shipment();
        already.setTrackingNo("PKG-2");
        order.addShipment(already);
        supplierReports(SupplierOrderState.SHIPPED,
                parcel("PKG-1", new SupplierOrderLine(null, "5900000000001", null, 1)),
                parcel("PKG-2", new SupplierOrderLine(null, "5900000000002", null, 1)));
        when(completion.confirmShipped(any(), any(), anyList(), anyList(), any(), any()))
                .thenAnswer(confirmedAs(DropshipShipmentResult.COMPLETED));

        // when
        TrackingOutcome outcome = service.check(STORE_ID, delivery.getDeliveryId(), false);

        // then
        assertThat(outcome).isEqualTo(TrackingOutcome.APPLIED);
        ArgumentCaptor<List<Allocation>> selected = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<DropshipShipment> shipment = ArgumentCaptor.forClass(DropshipShipment.class);
        verify(completion, times(1)).confirmShipped(any(), any(), selected.capture(), anyList(), shipment.capture(), any());
        assertThat(shipment.getValue().trackingNo()).isEqualTo("PKG-1");
        assertThat(selected.getValue()).containsExactlyInAnyOrder(first, second);
        assertThat(delivery.getTrackingState()).isEqualTo(DeliveryTrackingState.COMPLETED);
    }
}
