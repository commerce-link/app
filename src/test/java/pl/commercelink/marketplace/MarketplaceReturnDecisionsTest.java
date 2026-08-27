package pl.commercelink.marketplace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.MarketplaceReturnAction;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrderLifecycleEventPublisher;
import pl.commercelink.orders.OrderLifecycleEventType;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.event.Event;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.orders.rma.RMAItem;
import pl.commercelink.orders.rma.RMARepository;
import pl.commercelink.orders.rma.RMAStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketplaceReturnDecisionsTest {

    private static final String STORE_ID = "store-1";
    private static final String ORDER_ID = "order-1";

    @Mock private OrdersRepository ordersRepository;
    @Mock private OrderItemsRepository orderItemsRepository;
    @Mock private RMARepository rmaRepository;
    @Mock private OrderLifecycleEventPublisher publisher;
    @Mock private Order order;

    @InjectMocks
    private MarketplaceReturnDecisions decisions;

    private RMA marketplaceRma;

    @BeforeEach
    void setUp() {
        marketplaceRma = new RMA(STORE_ID);
        marketplaceRma.setOrderId(ORDER_ID);
        marketplaceRma.setMarketplace("Allegro");
        marketplaceRma.setExternalReturnId("r-1");
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
    }

    private static RMAItem rmaItem(String itemId, String mfn, int qty) {
        RMAItem item = new RMAItem();
        item.setItemId(itemId);
        item.setMfn(mfn);
        item.setQty(qty);
        return item;
    }

    private static OrderItem orderItem(String itemId, String mfn, int qty, FulfilmentStatus status) {
        OrderItem item = mock(OrderItem.class);
        when(item.getItemId()).thenReturn(itemId);
        when(item.getManufacturerCode()).thenReturn(mfn);
        when(item.getQty()).thenReturn(qty);
        when(item.hasOneOfTheStatuses(FulfilmentStatus.Returned, FulfilmentStatus.Replaced))
                .thenReturn(status == FulfilmentStatus.Returned || status == FulfilmentStatus.Replaced);
        return item;
    }

    private static OrderItem shippingOrderItem(String itemId) {
        OrderItem item = mock(OrderItem.class);
        when(item.getItemId()).thenReturn(itemId);
        when(item.getManufacturerCode()).thenReturn(BasketItem.SHIPPING_MFN_CODE);
        when(item.getQty()).thenReturn(1);
        when(item.isService()).thenReturn(true);
        return item;
    }

    @Test
    void returnAcceptedPublishesRefundActionAndRecordsEvent() {
        // given
        List<OrderItem> orderItems = List.of(
                orderItem("item-1", "SKU-1", 2, FulfilmentStatus.Delivered),
                orderItem("item-2", "SKU-2", 1, FulfilmentStatus.Delivered));
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(orderItems);

        // when
        decisions.returnAccepted(marketplaceRma,
                List.of(rmaItem("item-1", "SKU-1", 2), rmaItem("item-2", "SKU-2", 1)), true);

        // then
        ArgumentCaptor<MarketplaceReturnAction> captor = ArgumentCaptor.forClass(MarketplaceReturnAction.class);
        verify(publisher).publishReturnAction(eq(order), eq(marketplaceRma), eq(OrderLifecycleEventType.ReturnAccepted), captor.capture());
        MarketplaceReturnAction action = captor.getValue();
        assertEquals(marketplaceRma.getRmaId(), action.getRmaId());
        assertEquals("r-1", action.getExternalReturnId());
        assertNotNull(action.getCommandId());
        assertTrue(action.isRefundDelivery());
        assertEquals(2, action.getItems().size());
        assertEquals("SKU-1", action.getItems().get(0).getManufacturerCode());
        assertEquals(2, action.getItems().get(0).getQuantity());
        assertTrue(marketplaceRma.hasEvent(new Event(EventType.action, MarketplaceReturnImporter.EVENT_REFUND_REQUESTED, null)));
        verify(rmaRepository).save(marketplaceRma);
    }

    @Test
    void refundItemsUseThePersistedOrderItemKeyInsteadOfTheRmaItemsStoredMfn() {
        // given
        OrderItem mutatedOrderItem = mock(OrderItem.class);
        when(mutatedOrderItem.getItemId()).thenReturn("item-1");
        when(mutatedOrderItem.getExternalItemId()).thenReturn("SKU-1");
        when(mutatedOrderItem.getManufacturerCode()).thenReturn("SUPPLIER-CODE-1");
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(mutatedOrderItem));

        // when
        decisions.returnAccepted(marketplaceRma, List.of(rmaItem("item-1", "SUPPLIER-CODE-1", 1)), false);

        // then
        ArgumentCaptor<MarketplaceReturnAction> captor = ArgumentCaptor.forClass(MarketplaceReturnAction.class);
        verify(publisher).publishReturnAction(any(), any(), any(), captor.capture());
        assertEquals("SKU-1", captor.getValue().getItems().get(0).getManufacturerCode());
    }

    @Test
    void refundItemsFallBackToTheRmaItemsStoredMfnWhenTheOrderItemIsGone() {
        // given
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());

        // when
        decisions.returnAccepted(marketplaceRma, List.of(rmaItem("item-1", "SKU-1", 1)), false);

        // then
        ArgumentCaptor<MarketplaceReturnAction> captor = ArgumentCaptor.forClass(MarketplaceReturnAction.class);
        verify(publisher).publishReturnAction(any(), any(), any(), captor.capture());
        assertEquals("SKU-1", captor.getValue().getItems().get(0).getManufacturerCode());
    }

    @Test
    void eachAcceptanceRoundGetsItsOwnCommandId() {
        // given
        List<OrderItem> orderItems = List.of(
                orderItem("item-1", "SKU-1", 1, FulfilmentStatus.Delivered),
                orderItem("item-2", "SKU-2", 1, FulfilmentStatus.Delivered));
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(orderItems);

        // when
        decisions.returnAccepted(marketplaceRma, List.of(rmaItem("item-1", "SKU-1", 1)), false);
        decisions.returnAccepted(marketplaceRma, List.of(rmaItem("item-2", "SKU-2", 1)), false);

        // then
        ArgumentCaptor<MarketplaceReturnAction> captor = ArgumentCaptor.forClass(MarketplaceReturnAction.class);
        verify(publisher, times(2)).publishReturnAction(any(), any(), any(), captor.capture());
        assertNotEquals(captor.getAllValues().get(0).getCommandId(), captor.getAllValues().get(1).getCommandId());
    }

    @Test
    void manualRmaDecisionsAreIgnored() {
        // given
        RMA manual = new RMA(STORE_ID);
        manual.setOrderId(ORDER_ID);

        // when
        decisions.returnAccepted(manual, List.of(rmaItem("item-1", "SKU-1", 1)), false);
        decisions.returnRejected(manual);

        // then
        verifyNoInteractions(publisher);
        verify(rmaRepository, never()).save(any());
    }

    @Test
    void returnAcceptedDoesNothingWhenOrderIsMissing() {
        // given
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(null);

        // when
        decisions.returnAccepted(marketplaceRma, List.of(rmaItem("item-1", "SKU-1", 1)), false);

        // then
        verifyNoInteractions(publisher);
        verify(rmaRepository, never()).save(any());
    }

    @Test
    void returnRejectedDoesNothingWhenOrderIsMissing() {
        // given
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(null);
        marketplaceRma.setRejectionReason("Damaged by buyer");

        // when
        decisions.returnRejected(marketplaceRma);

        // then
        verifyNoInteractions(publisher);
        verify(rmaRepository, never()).save(any());
    }

    @Test
    void returnRejectedPublishesReasonAndRecordsEvent() {
        // given
        marketplaceRma.setRejectionReason("Damaged by buyer");

        // when
        decisions.returnRejected(marketplaceRma);

        // then
        ArgumentCaptor<MarketplaceReturnAction> captor = ArgumentCaptor.forClass(MarketplaceReturnAction.class);
        verify(publisher).publishReturnAction(eq(order), eq(marketplaceRma), eq(OrderLifecycleEventType.ReturnRejected), captor.capture());
        assertEquals("Damaged by buyer", captor.getValue().getRejectionReason());
        assertTrue(marketplaceRma.hasEvent(new Event(EventType.action, MarketplaceReturnImporter.EVENT_REJECTION_SENT, null)));
        verify(rmaRepository).save(marketplaceRma);
    }

    @Test
    void rejectionIsSentOnlyOnce() {
        // given
        marketplaceRma.setRejectionReason("Damaged");

        // when
        decisions.returnRejected(marketplaceRma);
        decisions.returnRejected(marketplaceRma);

        // then
        verify(publisher, times(1)).publishReturnAction(any(), any(), eq(OrderLifecycleEventType.ReturnRejected), any());
    }

    @Test
    void rejectionReasonIsRequiredOnlyWhenMarketplaceRmaTurnsRejected() {
        // given
        RMA manual = new RMA(STORE_ID);

        // when / then
        assertTrue(decisions.requiresRejectionReason(marketplaceRma, RMAStatus.Rejected, " "));
        assertTrue(decisions.requiresRejectionReason(marketplaceRma, RMAStatus.Rejected, null));
        assertFalse(decisions.requiresRejectionReason(marketplaceRma, RMAStatus.Rejected, "Damaged"));
        assertFalse(decisions.requiresRejectionReason(marketplaceRma, RMAStatus.Processing, null));
        assertFalse(decisions.requiresRejectionReason(manual, RMAStatus.Rejected, null));
        marketplaceRma.setStatus(RMAStatus.Rejected);
        assertFalse(decisions.requiresRejectionReason(marketplaceRma, RMAStatus.Rejected, null));
    }

    @Test
    void rejectionReasonIsRequiredWhenLongerThan250Characters() {
        // given
        String tooLong = "x".repeat(251);
        String maxAllowed = "x".repeat(250);

        // when / then
        assertTrue(decisions.requiresRejectionReason(marketplaceRma, RMAStatus.Rejected, tooLong));
        assertFalse(decisions.requiresRejectionReason(marketplaceRma, RMAStatus.Rejected, maxAllowed));
    }

    @Test
    void coversWholeOrderWhenRmaQuantitiesMatchOpenOrderItems() {
        // given
        List<OrderItem> orderItems = List.of(
                orderItem("item-1", "SKU-1", 2, FulfilmentStatus.Delivered),
                orderItem("item-2", "SKU-2", 1, FulfilmentStatus.Delivered),
                orderItem("item-3", "SKU-3", 1, FulfilmentStatus.Returned),
                shippingOrderItem("item-shipping"));
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(orderItems);

        // when / then
        assertTrue(decisions.coversWholeOrder(marketplaceRma,
                List.of(rmaItem("item-1", "SKU-1", 2), rmaItem("item-2", "SKU-2", 1))));
        assertFalse(decisions.coversWholeOrder(marketplaceRma,
                List.of(rmaItem("item-1", "SKU-1", 1), rmaItem("item-2", "SKU-2", 1))));
        assertFalse(decisions.coversWholeOrder(marketplaceRma, List.of(rmaItem("item-1", "SKU-1", 2))));
    }
}
