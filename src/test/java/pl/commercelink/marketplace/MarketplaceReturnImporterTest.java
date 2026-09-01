package pl.commercelink.marketplace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.marketplace.api.MarketplaceReturn;
import pl.commercelink.marketplace.api.MarketplaceReturnStatus;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.event.Event;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.orders.rma.RMAItem;
import pl.commercelink.orders.rma.RMAItemsRepository;
import pl.commercelink.orders.rma.RMARepository;
import pl.commercelink.orders.rma.RMAResolutionType;
import pl.commercelink.orders.rma.RMAStatus;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreNotification;
import pl.commercelink.stores.StoreNotificationType;
import pl.commercelink.stores.StoresRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketplaceReturnImporterTest {

    private static final String STORE_ID = "store-1";
    private static final String MARKETPLACE = "Allegro";
    private static final String ORDER_ID = "order-1";
    private static final String EXTERNAL_ORDER_ID = "cf-1";

    @Mock private RMARepository rmaRepository;
    @Mock private RMAItemsRepository rmaItemsRepository;
    @Mock private OrdersRepository ordersRepository;
    @Mock private OrderItemsRepository orderItemsRepository;
    @Mock private StoresRepository storesRepository;
    @Mock private OrderItemFamily orderItemFamily;
    @Mock private Order order;
    @Mock private OrderItem orderItem;

    @InjectMocks
    private MarketplaceReturnImporter importer;

    private Store store;

    @BeforeEach
    void setUp() {
        store = new Store();
        store.setStoreId(STORE_ID);
        when(ordersRepository.findByStoreIdAndExternalOrderId(STORE_ID, EXTERNAL_ORDER_ID)).thenReturn(order);
        when(order.getOrderId()).thenReturn(ORDER_ID);
        when(order.getStoreId()).thenReturn(STORE_ID);
        when(order.getStatus()).thenReturn(OrderStatus.Shipping);
        when(order.getEmail()).thenReturn("buyer@allegro.pl");
        when(order.getShippingDetails()).thenReturn(new ShippingDetails());
        when(orderItem.getItemId()).thenReturn("item-1");
        when(orderItem.getManufacturerCode()).thenReturn("SKU-1");
        when(orderItem.getQty()).thenReturn(3);
        when(orderItem.getPrice()).thenReturn(100.0);
        when(orderItem.hasOneOfTheStatuses(FulfilmentStatus.Returned, FulfilmentStatus.Replaced)).thenReturn(false);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(orderItem));
    }

    private static MarketplaceReturn marketplaceReturn(String id, MarketplaceReturnStatus status, MarketplaceReturn.Item... items) {
        return new MarketplaceReturn(id, EXTERNAL_ORDER_ID, "REF/" + id, status,
                LocalDateTime.of(2026, 8, 20, 12, 0), List.of(items),
                List.of(new MarketplaceReturn.Parcel("0000123456", "INPOST")));
    }

    private static MarketplaceReturn.Item item(String mfn, int qty) {
        return new MarketplaceReturn.Item(mfn, qty, new BigDecimal("100.00"), "NOT_AS_DESCRIBED: wrong colour");
    }

    private static MarketplaceReturn returnWithItem(String marketplaceKey, int qty) {
        return marketplaceReturn("r-1", MarketplaceReturnStatus.IN_TRANSIT, item(marketplaceKey, qty));
    }

    private static MarketplaceReturn returnWithItems(MarketplaceReturn.Item... items) {
        return marketplaceReturn("r-1", MarketplaceReturnStatus.IN_TRANSIT, items);
    }

    private static OrderItem orderItem(String itemId, String externalItemId, String manufacturerCode, int qty) {
        OrderItem item = mock(OrderItem.class);
        when(item.getItemId()).thenReturn(itemId);
        when(item.getExternalItemId()).thenReturn(externalItemId);
        when(item.getManufacturerCode()).thenReturn(manufacturerCode);
        when(item.getQty()).thenReturn(qty);
        when(item.hasOneOfTheStatuses(FulfilmentStatus.Returned, FulfilmentStatus.Replaced)).thenReturn(false);
        return item;
    }

    private static OrderItem orderItem(String itemId, String sku, int qty) {
        return orderItem(itemId, sku, sku, qty);
    }

    private static RMAItem rmaItem(String rmaId, String orderItemId) {
        RMAItem item = new RMAItem();
        item.setRmaId(rmaId);
        item.setItemId(orderItemId);
        return item;
    }

    @Test
    void createsWaitingForItemsRmaWithMatchedItemsAndParcel() {
        // given
        when(rmaRepository.findByExternalReturnId(STORE_ID, MARKETPLACE, "r-1")).thenReturn(null);

        // when
        importer.importReturn(store, MARKETPLACE, marketplaceReturn("r-1", MarketplaceReturnStatus.IN_TRANSIT, item("SKU-1", 2)));

        // then
        ArgumentCaptor<RMA> rmaCaptor = ArgumentCaptor.forClass(RMA.class);
        verify(rmaRepository).save(rmaCaptor.capture());
        RMA rma = rmaCaptor.getValue();
        assertEquals(RMAStatus.WaitingForItems, rma.getStatus());
        assertEquals(ORDER_ID, rma.getOrderId());
        assertEquals("buyer@allegro.pl", rma.getEmail());
        assertEquals(MARKETPLACE, rma.getMarketplace());
        assertEquals("r-1", rma.getExternalReturnId());
        assertEquals("REF/r-1", rma.getExternalReturnReference());
        assertEquals(MarketplaceReturnStatus.IN_TRANSIT, rma.getExternalReturnStatus());
        assertFalse(rma.isEmailNotificationsEnabled());
        assertEquals(LocalDateTime.of(2026, 8, 20, 12, 0), rma.getCreatedAt());
        assertEquals("0000123456", rma.getShipments().get(0).getTrackingNo());
        assertEquals("INPOST", rma.getShipments().get(0).getCarrier());
        assertEquals(200.0, rma.getShippingInsurance(), 0.001);

        ArgumentCaptor<List<RMAItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(rmaItemsRepository).batchSave(itemsCaptor.capture());
        RMAItem rmaItem = itemsCaptor.getValue().get(0);
        assertEquals(rma.getRmaId(), rmaItem.getRmaId());
        assertEquals("item-1", rmaItem.getItemId());
        assertEquals(2, rmaItem.getQty());
        assertEquals(RMAResolutionType.Return, rmaItem.getDesiredResolution());
        assertEquals("NOT_AS_DESCRIBED: wrong colour", rmaItem.getReason());
        assertEquals("SKU-1", rmaItem.getMfn());

        InOrder inOrder = inOrder(rmaItemsRepository, rmaRepository);
        inOrder.verify(rmaItemsRepository).batchSave(anyList());
        inOrder.verify(rmaRepository).save(any());
    }

    @Test
    void matchesByThePersistedExternalItemIdWhenManufacturerCodeWasOverwrittenBySupplierAssignment() {
        // given
        when(orderItem.getExternalItemId()).thenReturn("SKU-1");
        when(orderItem.getManufacturerCode()).thenReturn("SUPPLIER-CODE-1");
        when(rmaRepository.findByExternalReturnId(STORE_ID, MARKETPLACE, "r-1")).thenReturn(null);

        // when
        importer.importReturn(store, MARKETPLACE, marketplaceReturn("r-1", MarketplaceReturnStatus.IN_TRANSIT, item("SKU-1", 1)));

        // then
        ArgumentCaptor<List<RMAItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(rmaItemsRepository).batchSave(itemsCaptor.capture());
        assertEquals("item-1", itemsCaptor.getValue().get(0).getItemId());
    }

    @Test
    void matchesLegacyOrderItemsWhoseManufacturerCodeWasNormalisedAtImport() {
        // given: an order imported before externalItemId existed — mfn went through unifyMfn
        OrderItem legacy = orderItem("item-1", null, "K7M2XQ9PZ4", 1);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(legacy));
        MarketplaceReturn ret = returnWithItem("k7m2xq9pz4", 1);

        // when
        importer.importReturn(store, MARKETPLACE, ret);

        // then
        verify(rmaRepository).save(any(RMA.class));
    }

    @Test
    void matchesCurrentOrderItemsOnTheRawMarketplaceKey() {
        // given: externalItemId holds the raw key; manufacturerCode may have been overwritten by assignSupplier
        OrderItem current = orderItem("item-1", "k7m2xq9pz4", "SUPPLIER-CODE-9", 1);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(current));
        MarketplaceReturn ret = returnWithItem("k7m2xq9pz4", 1);

        // when
        importer.importReturn(store, MARKETPLACE, ret);

        // then
        verify(rmaRepository).save(any(RMA.class));
    }

    @Test
    void fallsBackToManufacturerCodeWhenExternalItemIdIsBlank() {
        // given
        when(orderItem.getExternalItemId()).thenReturn(null);
        when(rmaRepository.findByExternalReturnId(STORE_ID, MARKETPLACE, "r-1")).thenReturn(null);

        // when
        importer.importReturn(store, MARKETPLACE, marketplaceReturn("r-1", MarketplaceReturnStatus.IN_TRANSIT, item("SKU-1", 1)));

        // then
        ArgumentCaptor<List<RMAItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(rmaItemsRepository).batchSave(itemsCaptor.capture());
        assertEquals("item-1", itemsCaptor.getValue().get(0).getItemId());
    }

    @Test
    void clampsQuantityToOrderedQuantity() {
        // given
        when(rmaRepository.findByExternalReturnId(STORE_ID, MARKETPLACE, "r-1")).thenReturn(null);

        // when
        importer.importReturn(store, MARKETPLACE, marketplaceReturn("r-1", MarketplaceReturnStatus.DECLARED, item("SKU-1", 5)));

        // then
        ArgumentCaptor<List<RMAItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(rmaItemsRepository).batchSave(itemsCaptor.capture());
        assertEquals(3, itemsCaptor.getValue().get(0).getQty());
    }

    @Test
    void skipsWhenNoItemMatches() {
        // given
        when(rmaRepository.findByExternalReturnId(STORE_ID, MARKETPLACE, "r-1")).thenReturn(null);

        // when
        importer.importReturn(store, MARKETPLACE, marketplaceReturn("r-1", MarketplaceReturnStatus.DECLARED, item("OTHER", 1)));

        // then
        verify(rmaRepository, never()).save(any());
        verify(rmaItemsRepository, never()).batchSave(anyList());
        assertEquals(1, store.getNotifications().size());
        assertEquals(StoreNotificationType.MARKETPLACE_RETURN_UNMATCHED, store.getNotifications().get(0).getType());
        assertEquals("r-1", store.getNotifications().get(0).getObject());
        // No RMA was created, so the wording must send the operator to the marketplace panel directly
        assertTrue(store.getNotifications().get(0).getMessage().contains("could not be matched"));
        verify(storesRepository).save(store);
    }

    @Test
    void notifiesTheStoreWhenOnlySomeReturnItemsMatched() {
        // given: a two-item return where one item has no counterpart in the order
        when(rmaRepository.findByExternalReturnId(STORE_ID, MARKETPLACE, "r-1")).thenReturn(null);
        OrderItem matched = orderItem("item-1", "sku-a", 1);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(matched));
        MarketplaceReturn ret = returnWithItems(item("sku-a", 1), item("sku-missing", 1));

        // when
        importer.importReturn(store, MARKETPLACE, ret);

        // then: the RMA is still created, but the shortfall must not be silent
        verify(rmaRepository).save(any(RMA.class));
        StoreNotification notification = store.getNotifications().stream()
                .filter(n -> n.getType() == StoreNotificationType.MARKETPLACE_RETURN_UNMATCHED)
                .findFirst()
                .orElseThrow();
        // M1: an RMA WAS created for the matched items - the wording must not read as a total miss, which
        // would invite a manual marketplace refund on top of the app's own partial one (a double refund)
        assertTrue(notification.getMessage().contains("RMA was created"));
        assertFalse(notification.getMessage().contains("could not be matched to an order"));
    }

    @Test
    void skipsOrderItemsAlreadyCoveredByAnOpenRma() {
        // given: an open RMA already claims item-1
        when(rmaRepository.findByExternalReturnId(STORE_ID, MARKETPLACE, "r-1")).thenReturn(null);
        OrderItem matched = orderItem("item-1", "sku-a", 1);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(matched));
        when(rmaItemsRepository.findByOrderItemId("item-1")).thenReturn(List.of(rmaItem("open-rma-1", "item-1")));
        RMA openRma = new RMA(STORE_ID);
        openRma.setStatus(RMAStatus.WaitingForItems);
        when(rmaRepository.findById(STORE_ID, "open-rma-1")).thenReturn(openRma);
        MarketplaceReturn ret = returnWithItem("sku-a", 1);

        // when
        importer.importReturn(store, MARKETPLACE, ret);

        // then: no second RMA competing for the same order item
        verify(rmaRepository, never()).save(any(RMA.class));
    }

    @Test
    void matchesItemsMovedToASplitOrder() {
        // given: the returned item now lives on an order split off from the one the marketplace still tracks
        when(rmaRepository.findByExternalReturnId(STORE_ID, MARKETPLACE, "r-1")).thenReturn(null);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());
        OrderItem moved = orderItem("item-2", "sku-a", 1);
        when(orderItemFamily.siblingItems(order)).thenReturn(List.of(moved));
        MarketplaceReturn ret = returnWithItem("sku-a", 1);

        // when
        importer.importReturn(store, MARKETPLACE, ret);

        // then
        verify(rmaRepository).save(any(RMA.class));
    }

    @Test
    void doesNotConsultTheSplitFamilyWhenTheParentsOwnItemsAlreadyMatch() {
        // given: no split ever happened - the item matches straight away against the parent's own items
        when(rmaRepository.findByExternalReturnId(STORE_ID, MARKETPLACE, "r-1")).thenReturn(null);
        MarketplaceReturn ret = returnWithItem("SKU-1", 2);

        // when
        importer.importReturn(store, MARKETPLACE, ret);

        // then: the expensive whole-partition family read is skipped on the common path
        verifyNoInteractions(orderItemFamily);
    }

    @Test
    void matchesOrderItemsWhoseOnlyReferencingRmaWasRejected() {
        // given: the earlier RMA on this order item was rejected, so it must become matchable again
        when(rmaRepository.findByExternalReturnId(STORE_ID, MARKETPLACE, "r-1")).thenReturn(null);
        OrderItem matched = orderItem("item-1", "sku-a", 1);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(matched));
        when(rmaItemsRepository.findByOrderItemId("item-1")).thenReturn(List.of(rmaItem("rejected-rma-1", "item-1")));
        RMA rejectedRma = new RMA(STORE_ID);
        rejectedRma.setStatus(RMAStatus.Rejected);
        when(rmaRepository.findById(STORE_ID, "rejected-rma-1")).thenReturn(rejectedRma);
        MarketplaceReturn ret = returnWithItem("sku-a", 1);

        // when
        importer.importReturn(store, MARKETPLACE, ret);

        // then
        verify(rmaRepository).save(any(RMA.class));
    }

    @Test
    void notifiesStoreOnceWhenTheSameUnmatchedReturnIsPolledAgain() {
        // given
        when(rmaRepository.findByExternalReturnId(STORE_ID, MARKETPLACE, "r-1")).thenReturn(null);
        MarketplaceReturn unmatched = marketplaceReturn("r-1", MarketplaceReturnStatus.DECLARED, item("OTHER", 1));

        // when
        importer.importReturn(store, MARKETPLACE, unmatched);
        importer.importReturn(store, MARKETPLACE, unmatched);

        // then
        assertEquals(1, store.getNotifications().size());
        verify(storesRepository, times(1)).save(store);
    }

    @Test
    void skipsWhenOrderIsUnknownOrCancelled() {
        // given
        when(rmaRepository.findByExternalReturnId(eq(STORE_ID), eq(MARKETPLACE), any())).thenReturn(null);
        when(ordersRepository.findByStoreIdAndExternalOrderId(STORE_ID, EXTERNAL_ORDER_ID)).thenReturn(null);

        // when
        importer.importReturn(store, MARKETPLACE, marketplaceReturn("r-1", MarketplaceReturnStatus.DECLARED, item("SKU-1", 1)));

        // given
        when(ordersRepository.findByStoreIdAndExternalOrderId(STORE_ID, EXTERNAL_ORDER_ID)).thenReturn(order);
        when(order.getStatus()).thenReturn(OrderStatus.Cancelled);

        // when
        importer.importReturn(store, MARKETPLACE, marketplaceReturn("r-2", MarketplaceReturnStatus.DECLARED, item("SKU-1", 1)));

        // then
        verify(rmaRepository, never()).save(any());
    }

    @Test
    void skipsUnknownClosedReturns() {
        // given
        when(rmaRepository.findByExternalReturnId(STORE_ID, MARKETPLACE, "r-1")).thenReturn(null);

        // when
        importer.importReturn(store, MARKETPLACE, marketplaceReturn("r-1", MarketplaceReturnStatus.REFUNDED, item("SKU-1", 1)));

        // then
        verify(rmaRepository, never()).save(any());
        verify(ordersRepository, never()).findByStoreIdAndExternalOrderId(any(), any());
    }

    @Test
    void updatesExternalStatusOfExistingRma() {
        // given
        RMA existing = new RMA(STORE_ID);
        existing.setExternalReturnId("r-1");
        existing.setExternalReturnStatus(MarketplaceReturnStatus.DECLARED);
        when(rmaRepository.findByExternalReturnId(STORE_ID, MARKETPLACE, "r-1")).thenReturn(existing);

        // when
        importer.importReturn(store, MARKETPLACE, marketplaceReturn("r-1", MarketplaceReturnStatus.DELIVERED, item("SKU-1", 1)));

        // then
        assertEquals(MarketplaceReturnStatus.DELIVERED, existing.getExternalReturnStatus());
        verify(rmaRepository).save(existing);
        verify(rmaItemsRepository, never()).batchSave(anyList());
        assertTrue(store.getNotifications().isEmpty());
    }

    @Test
    void doesNotSaveWhenNothingChanged() {
        // given: status and shipments both already match what the marketplace reports
        RMA existing = new RMA(STORE_ID);
        existing.setExternalReturnId("r-1");
        existing.setExternalReturnStatus(MarketplaceReturnStatus.DELIVERED);
        Shipment shipment = new Shipment(ShipmentType.Courier);
        shipment.setTrackingNo("0000123456");
        shipment.setCarrier("INPOST");
        existing.setShipments(List.of(shipment));
        when(rmaRepository.findByExternalReturnId(STORE_ID, MARKETPLACE, "r-1")).thenReturn(existing);

        // when
        importer.importReturn(store, MARKETPLACE, marketplaceReturn("r-1", MarketplaceReturnStatus.DELIVERED, item("SKU-1", 1)));

        // then
        verify(rmaRepository, never()).save(any());
    }

    @Test
    void fillsInShipmentsOnALaterPollEvenWhenStatusIsUnchanged() {
        // given: the return was declared before the buyer generated a waybill, so it imported with no parcels
        RMA existing = new RMA(STORE_ID);
        existing.setExternalReturnId("r-1");
        existing.setExternalReturnStatus(MarketplaceReturnStatus.DECLARED);
        when(rmaRepository.findByExternalReturnId(STORE_ID, MARKETPLACE, "r-1")).thenReturn(existing);

        // when: the marketplace status is still DECLARED, but a waybill now exists
        importer.importReturn(store, MARKETPLACE, marketplaceReturn("r-1", MarketplaceReturnStatus.DECLARED, item("SKU-1", 1)));

        // then
        assertEquals(MarketplaceReturnStatus.DECLARED, existing.getExternalReturnStatus());
        assertEquals(1, existing.getShipments().size());
        assertEquals("0000123456", existing.getShipments().get(0).getTrackingNo());
        assertEquals("INPOST", existing.getShipments().get(0).getCarrier());
        verify(rmaRepository).save(existing);
    }

    @Test
    void notifiesStoreOnceWhenMarketplaceRefundedWithoutAppDecision() {
        // given
        RMA existing = new RMA(STORE_ID);
        existing.setExternalReturnId("r-1");
        existing.setExternalReturnReference("REF/r-1");
        existing.setExternalReturnStatus(MarketplaceReturnStatus.DELIVERED);
        when(rmaRepository.findByExternalReturnId(STORE_ID, MARKETPLACE, "r-1")).thenReturn(existing);
        MarketplaceReturn refunded = marketplaceReturn("r-1", MarketplaceReturnStatus.REFUNDED, item("SKU-1", 1));

        // when
        importer.importReturn(store, MARKETPLACE, refunded);
        importer.importReturn(store, MARKETPLACE, refunded);

        // then
        assertEquals(1, store.getNotifications().size());
        assertEquals(StoreNotificationType.MARKETPLACE_RETURN_REFUNDED, store.getNotifications().get(0).getType());
        assertEquals(existing.getRmaId(), store.getNotifications().get(0).getObject());
        assertTrue(existing.hasEvent(new Event(EventType.action, MarketplaceReturnImporter.EVENT_REFUNDED_BY_MARKETPLACE, null)));
        assertEquals(RMAStatus.New, existing.getStatus());
        verify(storesRepository, times(1)).save(store);
    }

    @Test
    void doesNotNotifyWhenAppRequestedTheRefund() {
        // given
        RMA existing = new RMA(STORE_ID);
        existing.setExternalReturnId("r-1");
        existing.setExternalReturnStatus(MarketplaceReturnStatus.DELIVERED);
        existing.addEvent(new Event(EventType.action, MarketplaceReturnImporter.EVENT_REFUND_REQUESTED, LocalDateTime.now()));
        when(rmaRepository.findByExternalReturnId(STORE_ID, MARKETPLACE, "r-1")).thenReturn(existing);

        // when
        importer.importReturn(store, MARKETPLACE, marketplaceReturn("r-1", MarketplaceReturnStatus.REFUNDED, item("SKU-1", 1)));

        // then
        assertTrue(store.getNotifications().isEmpty());
        verify(storesRepository, never()).save(any());
        assertEquals(MarketplaceReturnStatus.REFUNDED, existing.getExternalReturnStatus());
    }
}
