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

    private static RMAItem rmaItem(String mfn, int qty) {
        RMAItem item = new RMAItem();
        item.setMfn(mfn);
        item.setQty(qty);
        return item;
    }

    private static OrderItem orderItem(String mfn, int qty, FulfilmentStatus status) {
        OrderItem item = mock(OrderItem.class);
        when(item.getManufacturerCode()).thenReturn(mfn);
        when(item.getQty()).thenReturn(qty);
        when(item.hasOneOfTheStatuses(FulfilmentStatus.Returned, FulfilmentStatus.Replaced))
                .thenReturn(status == FulfilmentStatus.Returned || status == FulfilmentStatus.Replaced);
        return item;
    }

    @Test
    void returnAcceptedPublishesRefundActionAndRecordsEvent() {
        // when
        decisions.returnAccepted(marketplaceRma, List.of(rmaItem("SKU-1", 2), rmaItem("SKU-2", 1)), true);

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
    void eachAcceptanceRoundGetsItsOwnCommandId() {
        // when
        decisions.returnAccepted(marketplaceRma, List.of(rmaItem("SKU-1", 1)), false);
        decisions.returnAccepted(marketplaceRma, List.of(rmaItem("SKU-2", 1)), false);

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
        decisions.returnAccepted(manual, List.of(rmaItem("SKU-1", 1)), false);
        decisions.returnRejected(manual);

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
    void coversWholeOrderWhenRmaQuantitiesMatchOpenOrderItems() {
        // given
        List<OrderItem> orderItems = List.of(
                orderItem("SKU-1", 2, FulfilmentStatus.Delivered),
                orderItem("SKU-2", 1, FulfilmentStatus.Delivered),
                orderItem("SKU-3", 1, FulfilmentStatus.Returned));
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(orderItems);

        // when / then
        assertTrue(decisions.coversWholeOrder(marketplaceRma, List.of(rmaItem("SKU-1", 2), rmaItem("SKU-2", 1))));
        assertFalse(decisions.coversWholeOrder(marketplaceRma, List.of(rmaItem("SKU-1", 1), rmaItem("SKU-2", 1))));
        assertFalse(decisions.coversWholeOrder(marketplaceRma, List.of(rmaItem("SKU-1", 2))));
    }
}
