package pl.commercelink.orders.rma;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.util.ReflectionTestUtils;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.MarketplaceReturnAction;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemFamily;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrderSource;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.event.Event;
import pl.commercelink.orders.event.EventType;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketplaceReturnDecisionsTest {

    private static final String STORE_ID = "store-1";
    private static final String ORDER_ID = "order-1";
    private static final String EXTERNAL_ORDER_ID = "ext-1";

    @Mock private OrdersRepository ordersRepository;
    @Mock private OrderItemsRepository orderItemsRepository;
    @Mock private RMARepository rmaRepository;
    @Mock private ReturnLifecycleEventPublisher publisher;
    @Mock private OrderItemFamily orderItemFamily;
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

        OrderSource source = mock(OrderSource.class);
        when(source.getName()).thenReturn("Allegro");
        when(order.getStoreId()).thenReturn(STORE_ID);
        when(order.getOrderId()).thenReturn(ORDER_ID);
        when(order.getExternalOrderId()).thenReturn(EXTERNAL_ORDER_ID);
        when(order.getSource()).thenReturn(source);
        when(order.isMarketplaceOrder()).thenReturn(true);
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

    private MarketplaceReturnAction capturePublishedAction() {
        ArgumentCaptor<ReturnLifecycleEvent> captor = ArgumentCaptor.forClass(ReturnLifecycleEvent.class);
        verify(publisher, atLeastOnce()).publish(captor.capture());
        return captor.getValue().action();
    }

    private ReturnLifecycleEventType capturePublishedType() {
        ArgumentCaptor<ReturnLifecycleEvent> captor = ArgumentCaptor.forClass(ReturnLifecycleEvent.class);
        verify(publisher, atLeastOnce()).publish(captor.capture());
        return captor.getValue().type();
    }

    private static OrderItem shippingOrderItem(String itemId) {
        OrderItem item = new OrderItem(ORDER_ID, "Other", "shipping", 1, 0.0, BasketItem.SHIPPING_MFN_CODE, false);
        item.setItemId(itemId);
        item.setService(true);
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
        decisions.publishAcceptance(marketplaceRma,
                List.of(rmaItem("item-1", "SKU-1", 2), rmaItem("item-2", "SKU-2", 1)), true);

        // then
        ArgumentCaptor<ReturnLifecycleEvent> captor = ArgumentCaptor.forClass(ReturnLifecycleEvent.class);
        verify(publisher).publish(captor.capture());
        assertEquals(ReturnLifecycleEventType.ReturnAccepted, captor.getValue().type());
        // Four String fields in a row on the ReturnLifecycleEvent constructor - pin each to its source so an
        // accidental swap (e.g. sending the internal orderId where externalOrderId belongs) fails loudly here.
        assertEquals(STORE_ID, captor.getValue().storeId());
        assertEquals(ORDER_ID, captor.getValue().orderId());
        assertEquals("Allegro", captor.getValue().marketplace());
        MarketplaceReturnAction action = captor.getValue().action();
        assertEquals(marketplaceRma.getRmaId(), action.rmaId());
        assertEquals("r-1", action.externalReturnId());
        assertNotNull(action.commandId());
        assertTrue(action.refundDelivery());
        assertEquals(2, action.items().size());
        assertEquals("SKU-1", action.items().get(0).marketplaceKey());
        assertEquals(2, action.items().get(0).quantity());
        assertTrue(marketplaceRma.hasEvent(new Event(EventType.action, RMA.EVENT_REFUND_REQUESTED, null)));
        verify(rmaRepository).save(marketplaceRma);
    }

    @Test
    void mergesAcceptedItemsThatResolveToTheSameMarketplaceKey() {
        // given: an RMA item split in two - both halves point at the same OrderItem
        OrderItem orderItem = orderItem("item-1", "sku-a", 2, FulfilmentStatus.Delivered);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(orderItem));
        List<RMAItem> accepted = List.of(rmaItem("item-1", "sku-a", 1), rmaItem("item-1", "sku-a", 1));

        // when
        decisions.publishAcceptance(marketplaceRma, accepted, false);

        // then: one entry with the summed quantity, never two entries with the same key
        ArgumentCaptor<ReturnLifecycleEvent> captor = ArgumentCaptor.forClass(ReturnLifecycleEvent.class);
        verify(publisher).publish(captor.capture());
        MarketplaceReturnAction action = captor.getValue().action();
        assertEquals(1, action.items().size());
        assertEquals("sku-a", action.items().get(0).marketplaceKey());
        assertEquals(2, action.items().get(0).quantity());
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
        decisions.publishAcceptance(marketplaceRma, List.of(rmaItem("item-1", "SUPPLIER-CODE-1", 1)), false);

        // then
        ArgumentCaptor<ReturnLifecycleEvent> captor = ArgumentCaptor.forClass(ReturnLifecycleEvent.class);
        verify(publisher).publish(captor.capture());
        assertEquals("SKU-1", captor.getValue().action().items().get(0).marketplaceKey());
    }

    @Test
    void refundItemsFallBackToTheRmaItemsStoredMfnWhenTheOrderItemIsGone() {
        // given
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());

        // when
        decisions.publishAcceptance(marketplaceRma, List.of(rmaItem("item-1", "SKU-1", 1)), false);

        // then
        ArgumentCaptor<ReturnLifecycleEvent> captor = ArgumentCaptor.forClass(ReturnLifecycleEvent.class);
        verify(publisher).publish(captor.capture());
        assertEquals("SKU-1", captor.getValue().action().items().get(0).marketplaceKey());
    }

    @Test
    void eachAcceptanceRoundGetsItsOwnCommandId() {
        // given
        List<OrderItem> orderItems = List.of(
                orderItem("item-1", "SKU-1", 1, FulfilmentStatus.Delivered),
                orderItem("item-2", "SKU-2", 1, FulfilmentStatus.Delivered));
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(orderItems);

        // when
        decisions.publishAcceptance(marketplaceRma, List.of(rmaItem("item-1", "SKU-1", 1)), false);
        decisions.publishAcceptance(marketplaceRma, List.of(rmaItem("item-2", "SKU-2", 1)), false);

        // then
        ArgumentCaptor<ReturnLifecycleEvent> captor = ArgumentCaptor.forClass(ReturnLifecycleEvent.class);
        verify(publisher, times(2)).publish(captor.capture());
        assertNotEquals(captor.getAllValues().get(0).action().commandId(), captor.getAllValues().get(1).action().commandId());
    }

    @Test
    void aSecondAcceptRoundIsRecordedNextToTheFirstNotOverIt() {
        // given
        List<OrderItem> orderItems = List.of(
                orderItem("item-1", "SKU-1", 1, FulfilmentStatus.Delivered),
                orderItem("item-2", "SKU-2", 1, FulfilmentStatus.Delivered));
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(orderItems);

        // when
        decisions.publishAcceptance(marketplaceRma, List.of(rmaItem("item-1", "SKU-1", 1)), false);
        decisions.publishAcceptance(marketplaceRma, List.of(rmaItem("item-2", "SKU-2", 1)), false);

        // then
        assertEquals(2, marketplaceRma.getMarketplaceDecisions().size());
        assertNotEquals(marketplaceRma.getMarketplaceDecisions().get(0).getCommandId(),
                marketplaceRma.getMarketplaceDecisions().get(1).getCommandId());
    }

    @Test
    void resendRepublishesEveryRecordedRoundWithItsOriginalCommandId() {
        // given: round 1 went to the DLQ, round 2 succeeded - resend must reach round 1 too. The payload is
        // the whole ReturnLifecycleEvent envelope, not just the action, since that is what is now persisted.
        marketplaceRma.addMarketplaceDecision(new MarketplaceDecision("ReturnAccepted", "cmd-1",
                "{\"storeId\":\"store-1\",\"orderId\":\"order-1\",\"externalOrderId\":\"ext-1\",\"marketplace\":\"Allegro\","
                        + "\"type\":\"ReturnAccepted\",\"action\":{\"rmaId\":\"r\",\"externalReturnId\":\"r-1\","
                        + "\"items\":[{\"marketplaceKey\":\"SKU-1\",\"quantity\":1}],\"refundDelivery\":false,\"commandId\":\"cmd-1\"}}",
                LocalDateTime.now()));
        marketplaceRma.addMarketplaceDecision(new MarketplaceDecision("ReturnAccepted", "cmd-2",
                "{\"storeId\":\"store-1\",\"orderId\":\"order-1\",\"externalOrderId\":\"ext-1\",\"marketplace\":\"Allegro\","
                        + "\"type\":\"ReturnAccepted\",\"action\":{\"rmaId\":\"r\",\"externalReturnId\":\"r-1\","
                        + "\"items\":[{\"marketplaceKey\":\"SKU-2\",\"quantity\":1}],\"refundDelivery\":false,\"commandId\":\"cmd-2\"}}",
                LocalDateTime.now()));

        // when
        boolean resent = decisions.resendDecisions(marketplaceRma);

        // then
        assertTrue(resent);
        ArgumentCaptor<ReturnLifecycleEvent> captor = ArgumentCaptor.forClass(ReturnLifecycleEvent.class);
        verify(publisher, times(2)).publish(captor.capture());
        assertTrue(captor.getAllValues().stream().allMatch(e -> e.type() == ReturnLifecycleEventType.ReturnAccepted));
        assertEquals(List.of("cmd-1", "cmd-2"),
                captor.getAllValues().stream().map(e -> e.action().commandId()).toList());
        assertEquals(List.of("SKU-1", "SKU-2"),
                captor.getAllValues().stream().map(e -> e.action().items().get(0).marketplaceKey()).toList());
    }

    // --- Task 5: the whole queue message is persisted and resent verbatim ---

    @Test
    void theStoredPayloadIsTheWholeQueueMessage() throws Exception {
        // given
        OrderItem orderItem = orderItem("item-1", "SKU-1", 2, FulfilmentStatus.Delivered);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(orderItem));

        // when
        decisions.publishAcceptance(marketplaceRma, List.of(rmaItem("item-1", "SKU-1", 2)), false);

        // then: what is persisted is exactly what was published, so a resend needs nothing else
        ArgumentCaptor<ReturnLifecycleEvent> captor = ArgumentCaptor.forClass(ReturnLifecycleEvent.class);
        verify(publisher).publish(captor.capture());
        ReturnLifecycleEvent stored = new ObjectMapper().readValue(
                marketplaceRma.getMarketplaceDecisions().get(0).getPayload(), ReturnLifecycleEvent.class);
        assertEquals(captor.getValue(), stored);
        assertEquals(EXTERNAL_ORDER_ID, stored.externalOrderId());
    }

    @Test
    void aResendRepublishesTheSameMessageWithoutLoadingTheOrder() {
        // given: a decision was recorded, then the order was hard-deleted before the resend
        OrderItem orderItem = orderItem("item-1", "SKU-1", 2, FulfilmentStatus.Delivered);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(orderItem));
        decisions.publishAcceptance(marketplaceRma, List.of(rmaItem("item-1", "SKU-1", 2)), false);
        String originalCommandId = marketplaceRma.getMarketplaceDecisions().get(0).getCommandId();
        clearInvocations(publisher);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(null);

        // when
        boolean resent = decisions.resendDecisions(marketplaceRma);

        // then
        assertTrue(resent);
        ArgumentCaptor<ReturnLifecycleEvent> captor = ArgumentCaptor.forClass(ReturnLifecycleEvent.class);
        verify(publisher).publish(captor.capture());
        assertEquals(originalCommandId, captor.getValue().action().commandId());
        assertEquals(ReturnLifecycleEventType.ReturnAccepted, captor.getValue().type());
    }

    @Test
    void aDecisionOnANonMarketplaceOrderIsRecordedButReportedAsNotSent() {
        // given: previously the publisher skipped silently while the caller still returned true
        when(order.isMarketplaceOrder()).thenReturn(false);
        OrderItem orderItem = orderItem("item-1", "SKU-1", 1, FulfilmentStatus.Delivered);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(orderItem));

        // when
        boolean published = decisions.publishAcceptance(marketplaceRma, List.of(rmaItem("item-1", "SKU-1", 1)), false);

        // then
        assertFalse(published);
        verify(publisher, never()).publish(any());
        verify(rmaRepository).save(marketplaceRma);
        assertEquals(1, marketplaceRma.getMarketplaceDecisions().size());
    }

    @Test
    void manualRmaDecisionsAreIgnored() {
        // given
        RMA manual = new RMA(STORE_ID);
        manual.setOrderId(ORDER_ID);

        // when
        decisions.publishAcceptance(manual, List.of(rmaItem("item-1", "SKU-1", 1)), false);
        decisions.publishRejection(manual);

        // then
        verifyNoInteractions(publisher);
        verify(rmaRepository, never()).save(any());
    }

    @Test
    void returnAcceptedDoesNothingWhenOrderIsMissing() {
        // given
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(null);

        // when
        decisions.publishAcceptance(marketplaceRma, List.of(rmaItem("item-1", "SKU-1", 1)), false);

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
        decisions.publishRejection(marketplaceRma);

        // then
        verifyNoInteractions(publisher);
        verify(rmaRepository, never()).save(any());
    }

    @Test
    void returnRejectedPublishesReasonAndRecordsEvent() {
        // given
        marketplaceRma.setRejectionReason("Damaged by buyer");

        // when
        decisions.publishRejection(marketplaceRma);

        // then
        ArgumentCaptor<ReturnLifecycleEvent> captor = ArgumentCaptor.forClass(ReturnLifecycleEvent.class);
        verify(publisher).publish(captor.capture());
        assertEquals(ReturnLifecycleEventType.ReturnRejected, captor.getValue().type());
        assertEquals("Damaged by buyer", captor.getValue().action().rejectionReason());
        assertTrue(marketplaceRma.hasEvent(new Event(EventType.action, RMA.EVENT_REJECTION_SENT, null)));
        verify(rmaRepository).save(marketplaceRma);
    }

    @Test
    void rejectionIsSentOnlyOnce() {
        // given
        marketplaceRma.setRejectionReason("Damaged");

        // when
        decisions.publishRejection(marketplaceRma);
        decisions.publishRejection(marketplaceRma);

        // then
        ArgumentCaptor<ReturnLifecycleEvent> captor = ArgumentCaptor.forClass(ReturnLifecycleEvent.class);
        verify(publisher, times(1)).publish(captor.capture());
        assertEquals(ReturnLifecycleEventType.ReturnRejected, captor.getValue().type());
    }

    @Test
    void doesNotPublishAcceptanceAfterARejectionWasSent() {
        // given
        marketplaceRma.addEvent(new Event(EventType.action, RMA.EVENT_REJECTION_SENT, LocalDateTime.now()));

        // when
        decisions.publishAcceptance(marketplaceRma, List.of(rmaItem("item-1", "SKU-1", 1)), false);

        // then
        verify(publisher, never()).publish(any());
        verify(rmaRepository, never()).save(any());
    }

    @Test
    void resendRepublishesTheStoredActionWithTheSameCommandId() {
        // given
        List<OrderItem> orderItems = List.of(orderItem("item-1", "SKU-1", 1, FulfilmentStatus.Delivered));
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(orderItems);
        decisions.publishAcceptance(marketplaceRma, List.of(rmaItem("item-1", "SKU-1", 1)), true);
        MarketplaceReturnAction first = capturePublishedAction();
        reset(publisher);

        // when
        boolean resent = decisions.resendDecisions(marketplaceRma);

        // then
        assertTrue(resent);
        MarketplaceReturnAction second = capturePublishedAction();
        assertEquals(first.commandId(), second.commandId());
        assertEquals(ReturnLifecycleEventType.ReturnAccepted, capturePublishedType());
    }

    @Test
    void resendReturnsFalseWhenNoDecisionWasEverPublished() {
        // when / then
        assertFalse(decisions.resendDecisions(marketplaceRma));
        verifyNoInteractions(publisher);
    }

    @Test
    void resendReturnsFalseForManualRmaEvenWithAStoredPayload() {
        // given
        RMA manual = new RMA(STORE_ID);
        manual.setOrderId(ORDER_ID);
        manual.addMarketplaceDecision(new MarketplaceDecision(ReturnLifecycleEventType.ReturnAccepted.name(), "cmd-1",
                "{}", LocalDateTime.now()));

        // when / then
        assertFalse(decisions.resendDecisions(manual));
        verifyNoInteractions(publisher);
    }

    // resendReturnsFalseWhenOrderIsMissing was removed: resendDecisions no longer loads the order at all
    // (that is the point of this task, see §1.3 of the spec), so "the order is missing" can no longer make
    // a resend fail. aResendRepublishesTheSameMessageWithoutLoadingTheOrder below proves the new contract:
    // a resend succeeds even after the order was hard-deleted.

    @Test
    void aCorruptSingleDecisionDoesNotAbortTheRestOfTheResend() {
        // given: MarketplaceDecision is a @NoArgsConstructor DynamoDB document, so a row saved without its
        // payload attribute deserializes with payload == null; ObjectMapper.readValue(null, ...) throws
        // IllegalArgumentException, not JsonProcessingException - that single bad row must not sink the loop
        marketplaceRma.addMarketplaceDecision(new MarketplaceDecision("ReturnAccepted", "cmd-bad", null, LocalDateTime.now()));
        marketplaceRma.addMarketplaceDecision(new MarketplaceDecision("ReturnAccepted", "cmd-good",
                "{\"storeId\":\"store-1\",\"orderId\":\"order-1\",\"externalOrderId\":\"ext-1\",\"marketplace\":\"Allegro\","
                        + "\"type\":\"ReturnAccepted\",\"action\":{\"rmaId\":\"r\",\"externalReturnId\":\"r-1\","
                        + "\"items\":[],\"refundDelivery\":false,\"commandId\":\"cmd-good\"}}",
                LocalDateTime.now()));

        // when
        boolean resent = decisions.resendDecisions(marketplaceRma);

        // then: the corrupt row is skipped, not fatal - the other decision on this RMA still gets resent
        assertTrue(resent);
        ArgumentCaptor<ReturnLifecycleEvent> captor = ArgumentCaptor.forClass(ReturnLifecycleEvent.class);
        verify(publisher).publish(captor.capture());
        assertEquals("cmd-good", captor.getValue().action().commandId());
    }

    @Test
    void whenReturnsAreDisabledResendPublishesNothing() {
        // given
        ReflectionTestUtils.setField(decisions, "returnsEnabled", false);
        marketplaceRma.addMarketplaceDecision(new MarketplaceDecision("ReturnAccepted", "cmd-1",
                "{\"storeId\":\"store-1\",\"orderId\":\"order-1\",\"externalOrderId\":\"ext-1\",\"marketplace\":\"Allegro\","
                        + "\"type\":\"ReturnAccepted\",\"action\":{\"rmaId\":\"r\",\"externalReturnId\":\"r-1\","
                        + "\"items\":[],\"refundDelivery\":false,\"commandId\":\"cmd-1\"}}",
                LocalDateTime.now()));

        // when
        boolean resent = decisions.resendDecisions(marketplaceRma);

        // then
        assertFalse(resent);
        verifyNoInteractions(publisher);
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
        assertTrue(decisions.coversEveryReturnableItem(marketplaceRma,
                List.of(rmaItem("item-1", "SKU-1", 2), rmaItem("item-2", "SKU-2", 1))));
        assertFalse(decisions.coversEveryReturnableItem(marketplaceRma,
                List.of(rmaItem("item-1", "SKU-1", 1), rmaItem("item-2", "SKU-2", 1))));
        assertFalse(decisions.coversEveryReturnableItem(marketplaceRma, List.of(rmaItem("item-1", "SKU-1", 2))));
    }

    @Test
    void doesNotCoverWholeOrderWhenOnlySomeItemsAreSelected() {
        // given: an order with two open items, but only one is being accepted
        List<OrderItem> orderItems = List.of(
                orderItem("item-1", "sku-a", 1, FulfilmentStatus.Delivered),
                orderItem("item-2", "sku-b", 1, FulfilmentStatus.Delivered));
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(orderItems);

        // when
        boolean covers = decisions.coversEveryReturnableItem(marketplaceRma, List.of(rmaItem("item-1", "sku-a", 1)));

        // then
        assertFalse(covers);
    }

    @Test
    void coversWholeOrderReturnsFalseWhenOrderIsMissing() {
        // given
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(null);

        // when / then
        assertFalse(decisions.coversEveryReturnableItem(marketplaceRma, List.of(rmaItem("item-1", "SKU-1", 1))));
    }

    // --- Finding 1: split-family awareness ---

    @Test
    void returnAcceptedResolvesAnItemThatMovedToASplitOffOrder() {
        // given: item-1 no longer lives on the parent order - it moved to a split-off child
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());
        OrderItem movedItem = orderItem("item-1", "SKU-1", 1, FulfilmentStatus.Delivered);
        when(orderItemFamily.itemsMovedToSplitOffOrders(order)).thenReturn(List.of(movedItem));

        // when
        decisions.publishAcceptance(marketplaceRma, List.of(rmaItem("item-1", "SKU-1", 1)), false);

        // then: the split family was consulted and gave the real key, not the RMA item's stored (and
        // possibly stale) mfn
        assertEquals("SKU-1", capturePublishedAction().items().get(0).marketplaceKey());
    }

    @Test
    void returnAcceptedDoesNotConsultTheSplitFamilyWhenTheParentsOwnItemsAlreadyResolveEverything() {
        // given: no split ever happened, every accepted item resolves against the parent's own items
        List<OrderItem> orderItems = List.of(orderItem("item-1", "SKU-1", 1, FulfilmentStatus.Delivered));
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(orderItems);

        // when
        decisions.publishAcceptance(marketplaceRma, List.of(rmaItem("item-1", "SKU-1", 1)), false);

        // then: the expensive family lookup is skipped on the common path
        verifyNoInteractions(orderItemFamily);
    }

    @Test
    void coversWholeOrderAcrossASplitFamily() {
        // given: the order was split - one item stayed on the parent, one moved to a child order
        OrderItem remaining = orderItem("item-1", "SKU-1", 1, FulfilmentStatus.Delivered);
        OrderItem moved = orderItem("item-2", "SKU-2", 1, FulfilmentStatus.Delivered);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(remaining));
        when(orderItemFamily.itemsMovedToSplitOffOrders(order)).thenReturn(List.of(moved));

        // when / then: only the parent's item is being returned - the moved item is still outstanding, so
        // this must NOT count as a whole-order return (a split family can never trivially satisfy this)
        assertFalse(decisions.coversEveryReturnableItem(marketplaceRma, List.of(rmaItem("item-1", "SKU-1", 1))));

        // when / then: once both the parent's and the moved item are returned, the whole family is covered
        assertTrue(decisions.coversEveryReturnableItem(marketplaceRma,
                List.of(rmaItem("item-1", "SKU-1", 1), rmaItem("item-2", "SKU-2", 1))));
    }

    @Test
    void coversWholeOrderNeedsEveryBatchOfAKeySharedByTwoOrderItems() {
        // given: multi-batch fulfilment left two order items with the same marketplace key
        List<OrderItem> orderItems = List.of(
                orderItem("item-a", "SKU-1", 1, FulfilmentStatus.Delivered),
                orderItem("item-b", "SKU-1", 1, FulfilmentStatus.Delivered));
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(orderItems);
        when(orderItemFamily.itemsMovedToSplitOffOrders(order)).thenReturn(List.of());

        // when / then
        assertFalse(decisions.coversEveryReturnableItem(marketplaceRma, List.of(rmaItem("item-a", "SKU-1", 1))));
        assertTrue(decisions.coversEveryReturnableItem(marketplaceRma,
                List.of(rmaItem("item-a", "SKU-1", 1), rmaItem("item-b", "SKU-1", 1))));
    }

    // --- Finding 2: refund/rejection mutual exclusion is symmetric ---

    @Test
    void returnRejectedIsRefusedOnceARefundWasAlreadyRequested() {
        // given
        marketplaceRma.addEvent(new Event(EventType.action, RMA.EVENT_REFUND_REQUESTED, LocalDateTime.now()));
        marketplaceRma.setRejectionReason("Damaged by buyer");

        // when
        decisions.publishRejection(marketplaceRma);

        // then
        verifyNoInteractions(publisher);
        verify(rmaRepository, never()).save(any());
    }

    // --- Finding 3: persist before publish ---

    @Test
    void returnAcceptedPersistsBeforePublishing() {
        // given
        List<OrderItem> orderItems = List.of(orderItem("item-1", "SKU-1", 1, FulfilmentStatus.Delivered));
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(orderItems);

        // when
        decisions.publishAcceptance(marketplaceRma, List.of(rmaItem("item-1", "SKU-1", 1)), false);

        // then
        InOrder inOrder = inOrder(rmaRepository, publisher);
        inOrder.verify(rmaRepository).save(marketplaceRma);
        inOrder.verify(publisher).publish(any());
    }

    @Test
    void returnAcceptedNeverPublishesWhenTheSaveFails() {
        // given
        List<OrderItem> orderItems = List.of(orderItem("item-1", "SKU-1", 1, FulfilmentStatus.Delivered));
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(orderItems);
        doThrow(new RuntimeException("version conflict")).when(rmaRepository).save(marketplaceRma);

        // when / then
        assertThrows(RuntimeException.class, () ->
                decisions.publishAcceptance(marketplaceRma, List.of(rmaItem("item-1", "SKU-1", 1)), false));
        verifyNoInteractions(publisher);
    }

    @Test
    void returnRejectedPersistsBeforePublishing() {
        // given
        marketplaceRma.setRejectionReason("Damaged");

        // when
        decisions.publishRejection(marketplaceRma);

        // then
        InOrder inOrder = inOrder(rmaRepository, publisher);
        inOrder.verify(rmaRepository).save(marketplaceRma);
        inOrder.verify(publisher).publish(any());
    }

    @Test
    void returnRejectedNeverPublishesWhenTheSaveFails() {
        // given
        marketplaceRma.setRejectionReason("Damaged");
        doThrow(new RuntimeException("version conflict")).when(rmaRepository).save(marketplaceRma);

        // when / then
        assertThrows(RuntimeException.class, () -> decisions.publishRejection(marketplaceRma));
        verifyNoInteractions(publisher);
    }

    @Test
    void returnAcceptedFailsLoudlyWhenAnItemHasNoOrderItemAndNoStoredMfn() {
        // given: no order item anywhere in the family, and the RMA item's own stored mfn is also blank
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());
        RMAItem itemWithoutMfn = rmaItem("item-1", null, 1);

        // when / then
        assertThrows(IllegalStateException.class, () ->
                decisions.publishAcceptance(marketplaceRma, List.of(itemWithoutMfn), false));
        verifyNoInteractions(publisher);
        verify(rmaRepository, never()).save(any());
    }

    @Test
    void returnAcceptedUsesTheNormalisedSkuAsRefundKeyForALegacyOrderItem() {
        // given
        OrderItem legacy = orderItem("item-1", "SUPPLIER-MPN-77", 1, FulfilmentStatus.Delivered);
        when(legacy.getSku()).thenReturn("LOCAL-SEED-0051");
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(legacy));

        // when
        decisions.publishAcceptance(marketplaceRma, List.of(rmaItem("item-1", "SUPPLIER-MPN-77", 1)), false);

        // then
        assertEquals("LOCAL-SEED-0051", capturePublishedAction().items().get(0).marketplaceKey());
    }

    // --- Task 7: decisions report refusal instead of silently swallowing it ---

    @Test
    void returnAcceptedReportsRefusalWhenARejectionWasAlreadySent() {
        // given
        marketplaceRma.addActionEvent(RMA.EVENT_REJECTION_SENT);

        // when
        boolean sent = decisions.publishAcceptance(marketplaceRma, List.of(rmaItem("item-1", "SKU-1", 1)), false);

        // then
        assertFalse(sent);
        verifyNoInteractions(publisher);
    }

    @Test
    void returnAcceptedIsANoOpThatSucceedsForAManualRma() {
        // when / then
        assertTrue(decisions.publishAcceptance(new RMA(STORE_ID), List.of(), false));
        verifyNoInteractions(publisher);
    }

    @Test
    void returnRejectedReportsRefusalWhenARefundWasAlreadyRequested() {
        // given
        marketplaceRma.addActionEvent(RMA.EVENT_REFUND_REQUESTED);

        // when / then
        assertFalse(decisions.publishRejection(marketplaceRma));
        verifyNoInteractions(publisher);
    }

    @Test
    void whenReturnsAreDisabledTheDecisionIsRecordedButNotPublished() {
        // given
        ReflectionTestUtils.setField(decisions, "returnsEnabled", false);
        OrderItem item = orderItem("item-1", "SKU-1", 1, FulfilmentStatus.Delivered);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(item));

        // when
        boolean sent = decisions.publishAcceptance(marketplaceRma, List.of(rmaItem("item-1", "SKU-1", 1)), false);

        // then
        assertFalse(sent);
        assertTrue(marketplaceRma.hasActionEvent(RMA.EVENT_REFUND_REQUESTED));
        assertEquals(1, marketplaceRma.getMarketplaceDecisions().size());
        verify(rmaRepository).save(marketplaceRma);
        verifyNoInteractions(publisher);
    }
}
