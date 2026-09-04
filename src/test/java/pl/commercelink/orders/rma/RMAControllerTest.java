package pl.commercelink.orders.rma;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.context.MessageSource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrdersRMAManager;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.event.Event;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.starter.storage.FileStorage;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.warehouse.api.ItemCondition;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RMAControllerTest {

    private static final String STORE_ID = "store-1";
    private static final String RMA_ID = "rma-1";
    private static final String ORDER_ID = "order-1";

    private static final MultiValueMap<String, MultipartFile> emptyMedia = new LinkedMultiValueMap<>();

    @Mock
    private RMARepository rmaRepository;
    @Mock
    private MarketplaceReturnDecisions marketplaceReturnDecisions;
    @Mock
    private RMAItemsRepository rmaItemsRepository;
    @Mock
    private RMALifecycle rmaLifecycle;
    @Mock
    private RMAManager rmaManager;
    @Mock
    private OrdersRepository orderRepository;
    @Mock
    private OrderItemsRepository orderItemsRepository;
    @Mock
    private OrdersRMAManager ordersRMAManager;
    @Mock
    private FileStorage fileStorage;
    @Mock
    private MessageSource messageSource;
    @Mock
    private RedirectAttributes redirectAttributes;
    @Mock
    private OpenRmaCoverage openRmaCoverage;

    @InjectMocks
    private RMAController controller;

    private static RMA rmaWithStatus(RMAStatus status) {
        RMA rma = new RMA(STORE_ID);
        rma.setOrderId(ORDER_ID);
        rma.setStatus(status);
        return rma;
    }

    private static OrderItem orderItemWithQtyAndStatus(String itemId, int qty, FulfilmentStatus status) {
        OrderItem orderItem = new OrderItem();
        orderItem.setOrderId(ORDER_ID);
        orderItem.setItemId(itemId);
        orderItem.setQty(qty);
        orderItem.setStatus(status);
        return orderItem;
    }

    private static RMAItem rmaItemWithQty(String itemId, int qty) {
        RMAItem rmaItem = new RMAItem();
        rmaItem.setItemId(itemId);
        rmaItem.setQty(qty);
        return rmaItem;
    }

    // ------------------------------------------------------------------
    // acceptReturn: publish only after the warehouse operation succeeded
    // ------------------------------------------------------------------

    @Test
    void acceptReturnPublishesOnlyAfterTheWarehouseSucceeded() {
        // given
        RMA rma = rmaWithStatus(RMAStatus.WaitingForItems);
        List<RMAItem> rmaItems = List.of(rmaItemWithQty("item-1", 1));
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(rma);
        when(rmaManager.returnSelectedItems(any(), any(), any()))
                .thenReturn(RMAManager.OperationResult.success(rma, rmaItems));
        when(ordersRMAManager.acceptReturn(any(), any(), any(), any()))
                .thenReturn(OperationResult.failure("warehouse.error"));

        // when
        String view;
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            view = controller.acceptReturn(RMA_ID, ItemCondition.Sealed, true, new RMAItemsForm(),
                    redirectAttributes, Locale.ENGLISH);
        }

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/rma/" + RMA_ID);
        verify(marketplaceReturnDecisions, never()).returnAccepted(any(), any(), anyBoolean());
    }

    // ------------------------------------------------------------------
    // acceptReturn: refundDelivery is re-derived from the accepted items,
    // never trusted from the submitted checkbox (Task 9 follow-up)
    // ------------------------------------------------------------------

    @Test
    void acceptReturnPassesRefundDeliveryTrueWhenAcceptedItemsCoverTheWholeOrder() {
        // given
        RMA rma = rmaWithStatus(RMAStatus.WaitingForItems);
        List<RMAItem> rmaItems = List.of(rmaItemWithQty("item-1", 2));
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(rma);
        when(rmaManager.returnSelectedItems(any(), any(), any()))
                .thenReturn(RMAManager.OperationResult.success(rma, rmaItems));
        when(ordersRMAManager.acceptReturn(any(), any(), any(), any())).thenReturn(OperationResult.success());
        when(marketplaceReturnDecisions.coversWholeOrder(rma, rmaItems)).thenReturn(true);

        // when
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            controller.acceptReturn(RMA_ID, ItemCondition.Sealed, true, new RMAItemsForm(),
                    redirectAttributes, Locale.ENGLISH);
        }

        // then
        ArgumentCaptor<Boolean> refundDeliveryCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(marketplaceReturnDecisions).returnAccepted(eq(rma), eq(rmaItems), refundDeliveryCaptor.capture());
        assertThat(refundDeliveryCaptor.getValue()).isTrue();
    }

    @Test
    void acceptReturnForcesRefundDeliveryFalseForAPartialAcceptance() {
        // given
        RMA rma = rmaWithStatus(RMAStatus.WaitingForItems);
        List<RMAItem> rmaItems = List.of(rmaItemWithQty("item-1", 1));
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(rma);
        when(rmaManager.returnSelectedItems(any(), any(), any()))
                .thenReturn(RMAManager.OperationResult.success(rma, rmaItems));
        when(ordersRMAManager.acceptReturn(any(), any(), any(), any())).thenReturn(OperationResult.success());
        // The operator checked "refund delivery", but the warehouse only accepted part of the order,
        // so the controller must re-derive coverage instead of trusting the checkbox.
        when(marketplaceReturnDecisions.coversWholeOrder(rma, rmaItems)).thenReturn(false);

        // when
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            controller.acceptReturn(RMA_ID, ItemCondition.Sealed, true, new RMAItemsForm(),
                    redirectAttributes, Locale.ENGLISH);
        }

        // then
        ArgumentCaptor<Boolean> refundDeliveryCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(marketplaceReturnDecisions).returnAccepted(eq(rma), eq(rmaItems), refundDeliveryCaptor.capture());
        assertThat(refundDeliveryCaptor.getValue()).isFalse();
    }

    @Test
    void acceptReturnEvaluatesWholeOrderCoverageBeforeMutatingOrderItems() {
        // given: accepting can split an OrderItem (one fragment marked Returned, the remainder left
        // open) - coversWholeOrder must run on the pre-acceptance state, or a just-split-off fragment
        // gets mistaken for a prior, separate decision and the leftover fragment coincidentally
        // absorbs this batch's quantity, wrongly reporting full coverage (see bug found in S10).
        RMA rma = rmaWithStatus(RMAStatus.WaitingForItems);
        List<RMAItem> rmaItems = List.of(rmaItemWithQty("item-1", 1));
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(rma);
        when(rmaManager.returnSelectedItems(any(), any(), any()))
                .thenReturn(RMAManager.OperationResult.success(rma, rmaItems));
        when(ordersRMAManager.acceptReturn(any(), any(), any(), any())).thenReturn(OperationResult.success());
        when(marketplaceReturnDecisions.coversWholeOrder(rma, rmaItems)).thenReturn(true);

        // when
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            controller.acceptReturn(RMA_ID, ItemCondition.Sealed, true, new RMAItemsForm(),
                    redirectAttributes, Locale.ENGLISH);
        }

        // then
        InOrder order = inOrder(marketplaceReturnDecisions, ordersRMAManager);
        order.verify(marketplaceReturnDecisions).coversWholeOrder(rma, rmaItems);
        order.verify(ordersRMAManager).acceptReturn(any(), any(), any(), any());
    }

    @Test
    void acceptReturnKeepsRefundDeliveryFalseWhenTheOperatorDidNotRequestIt() {
        // given
        RMA rma = rmaWithStatus(RMAStatus.WaitingForItems);
        List<RMAItem> rmaItems = List.of(rmaItemWithQty("item-1", 2));
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(rma);
        when(rmaManager.returnSelectedItems(any(), any(), any()))
                .thenReturn(RMAManager.OperationResult.success(rma, rmaItems));
        when(ordersRMAManager.acceptReturn(any(), any(), any(), any())).thenReturn(OperationResult.success());

        // when
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            controller.acceptReturn(RMA_ID, ItemCondition.Sealed, false, new RMAItemsForm(),
                    redirectAttributes, Locale.ENGLISH);
        }

        // then
        ArgumentCaptor<Boolean> refundDeliveryCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(marketplaceReturnDecisions).returnAccepted(eq(rma), eq(rmaItems), refundDeliveryCaptor.capture());
        assertThat(refundDeliveryCaptor.getValue()).isFalse();
        // refundDelivery && coversWholeOrder(...) must short-circuit: an unchecked box never even asks.
        verify(marketplaceReturnDecisions, never()).coversWholeOrder(any(), any());
    }

    // ------------------------------------------------------------------
    // updateRma: rejection gates run before any mutation
    // ------------------------------------------------------------------

    @Test
    void updateRmaOnAnAlreadyClosedRmaIsBlockedBeforeAnyMutation() {
        // given: rma-detail.html disables status/email/shippingInsurance/rejectionReason once the RMA
        // is closed, so a resubmission (double-click, back-button) posts those fields as null/default.
        RMA existingRma = rmaWithStatus(RMAStatus.Rejected);
        existingRma.setEmail("buyer@example.com");
        existingRma.setRejectionReason("Damaged on arrival");
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(existingRma);
        when(messageSource.getMessage(eq("rma.already.closed"), any(), any())).thenReturn("already closed");
        RMA postedRma = new RMA(STORE_ID);
        postedRma.setOrderId(ORDER_ID);

        // when
        String view;
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            view = controller.updateRma(RMA_ID, postedRma, null, emptyMedia, redirectAttributes, Locale.ENGLISH);
        }

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/rma/" + RMA_ID);
        verify(redirectAttributes).addFlashAttribute("errorMessage", "already closed");
        verify(rmaLifecycle, never()).update(any());
        verify(marketplaceReturnDecisions, never()).returnRejected(any());
        verify(rmaRepository, never()).save(any());
        assertThat(existingRma.getStatus()).isEqualTo(RMAStatus.Rejected);
        assertThat(existingRma.getEmail()).isEqualTo("buyer@example.com");
        assertThat(existingRma.getRejectionReason()).isEqualTo("Damaged on arrival");
    }

    @Test
    void rejectionWithoutAReasonIsBlockedBeforeAnyMutation() {
        // given
        RMA existingRma = rmaWithStatus(RMAStatus.New);
        existingRma.setExternalReturnId("r-1");
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(existingRma);
        when(messageSource.getMessage(eq("rma.rejection.reason.required"), any(), any())).thenReturn("reason required");

        // when
        String view;
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            view = controller.updateRma(RMA_ID, rmaWithStatus(RMAStatus.Rejected), null, emptyMedia,
                    redirectAttributes, Locale.ENGLISH);
        }

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/rma/" + RMA_ID);
        verify(redirectAttributes).addFlashAttribute("errorMessage", "reason required");
        verify(rmaLifecycle, never()).update(any());
        verify(marketplaceReturnDecisions, never()).returnRejected(any());
        assertThat(existingRma.getStatus()).isEqualTo(RMAStatus.New);
    }

    @Test
    void rejectionAfterARefundIsBlocked() {
        // given
        RMA existingRma = rmaWithStatus(RMAStatus.New);
        existingRma.setExternalReturnId("r-1");
        existingRma.addActionEvent(RMA.EVENT_REFUND_REQUESTED);
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(existingRma);
        when(messageSource.getMessage(eq("rma.rejection.after.refund"), any(), any())).thenReturn("blocked");
        RMA postedRma = rmaWithStatus(RMAStatus.Rejected);
        postedRma.setRejectionReason("Damaged on arrival");

        // when
        String view;
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            view = controller.updateRma(RMA_ID, postedRma, null, emptyMedia,
                    redirectAttributes, Locale.ENGLISH);
        }

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/rma/" + RMA_ID);
        verify(redirectAttributes).addFlashAttribute("errorMessage", "blocked");
        verify(rmaLifecycle, never()).update(any());
        assertThat(existingRma.getStatus()).isEqualTo(RMAStatus.New);
    }

    // ------------------------------------------------------------------
    // updateRma: the rejectionPending publish gate (Task 21 bug fix)
    // ------------------------------------------------------------------

    @Test
    void updateRmaRetriesTheRejectionPublishWhenAPreviousPublishFailed() {
        // given: the RMA is already Rejected (a prior save moved it there) but no RejectionSent
        // event was ever recorded, meaning the previous publish attempt failed.
        RMA existingRma = rmaWithStatus(RMAStatus.WaitingForItems);
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(existingRma);
        RMA postedRma = rmaWithStatus(RMAStatus.Rejected);
        postedRma.setRejectionReason("Damaged on arrival");

        // when
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            controller.updateRma(RMA_ID, postedRma, null, emptyMedia, redirectAttributes, Locale.ENGLISH);
        }

        // then
        verify(marketplaceReturnDecisions).returnRejected(existingRma);
    }

    @Test
    void updateRmaDoesNotRepublishTheRejectionWhenItWasAlreadySent() {
        // given
        RMA existingRma = rmaWithStatus(RMAStatus.WaitingForItems);
        existingRma.addEvent(new Event(EventType.action, RMA.EVENT_REJECTION_SENT, LocalDateTime.now()));
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(existingRma);
        RMA postedRma = rmaWithStatus(RMAStatus.Rejected);
        postedRma.setRejectionReason("Damaged on arrival");

        // when
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            controller.updateRma(RMA_ID, postedRma, null, emptyMedia, redirectAttributes, Locale.ENGLISH);
        }

        // then
        verify(marketplaceReturnDecisions, never()).returnRejected(any());
    }

    // ------------------------------------------------------------------
    // addRmaItemFromOrder: the Task 10 validation block, which now feeds
    // a real Allegro refund's lineItems[].quantity, so it must never let
    // a bad quantity through and must never mutate on rejection.
    // ------------------------------------------------------------------

    @Test
    void addRmaItemFromOrderRejectsWhenTheOrderItemIsMissing() {
        // given
        RMA rma = rmaWithStatus(RMAStatus.New);
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(rma);
        when(orderItemsRepository.findById(ORDER_ID, "item-missing")).thenReturn(null);
        when(messageSource.getMessage(eq("rma.item.invalid.quantity"), any(), any())).thenReturn("invalid");

        // when
        String view;
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            view = controller.addRmaItemFromOrder(RMA_ID, "item-missing", 1, "Return", null,
                    redirectAttributes, Locale.ENGLISH);
        }

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/rma/" + RMA_ID);
        verify(redirectAttributes).addFlashAttribute("errorMessage", "invalid");
        verify(rmaItemsRepository, never()).save(any());
    }

    @Test
    void addRmaItemFromOrderRejectsAnOrderItemAlreadyReturned() {
        // given
        RMA rma = rmaWithStatus(RMAStatus.New);
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(rma);
        OrderItem orderItem = orderItemWithQtyAndStatus("item-1", 3, FulfilmentStatus.Returned);
        when(orderItemsRepository.findById(ORDER_ID, "item-1")).thenReturn(orderItem);
        when(messageSource.getMessage(eq("rma.item.invalid.quantity"), any(), any())).thenReturn("invalid");

        // when
        String view;
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            view = controller.addRmaItemFromOrder(RMA_ID, "item-1", 1, "Return", null,
                    redirectAttributes, Locale.ENGLISH);
        }

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/rma/" + RMA_ID);
        verify(redirectAttributes).addFlashAttribute("errorMessage", "invalid");
        verify(rmaItemsRepository, never()).save(any());
    }

    @Test
    void addRmaItemFromOrderRejectsNonPositiveQuantity() {
        // given
        RMA rma = rmaWithStatus(RMAStatus.New);
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(rma);
        OrderItem orderItem = orderItemWithQtyAndStatus("item-1", 3, FulfilmentStatus.Delivered);
        when(orderItemsRepository.findById(ORDER_ID, "item-1")).thenReturn(orderItem);
        when(messageSource.getMessage(eq("rma.item.invalid.quantity"), any(), any())).thenReturn("invalid");

        // when
        String view;
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            view = controller.addRmaItemFromOrder(RMA_ID, "item-1", 0, "Return", null,
                    redirectAttributes, Locale.ENGLISH);
        }

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/rma/" + RMA_ID);
        verify(redirectAttributes).addFlashAttribute("errorMessage", "invalid");
        verify(rmaItemsRepository, never()).save(any());
    }

    @Test
    void addRmaItemFromOrderRejectsQuantityExceedingTheOrderItemQty() {
        // given
        RMA rma = rmaWithStatus(RMAStatus.New);
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(rma);
        OrderItem orderItem = orderItemWithQtyAndStatus("item-1", 2, FulfilmentStatus.Delivered);
        when(orderItemsRepository.findById(ORDER_ID, "item-1")).thenReturn(orderItem);
        when(messageSource.getMessage(eq("rma.item.invalid.quantity"), any(), any())).thenReturn("invalid");

        // when
        String view;
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            view = controller.addRmaItemFromOrder(RMA_ID, "item-1", 3, "Return", null,
                    redirectAttributes, Locale.ENGLISH);
        }

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/rma/" + RMA_ID);
        verify(redirectAttributes).addFlashAttribute("errorMessage", "invalid");
        verify(rmaItemsRepository, never()).save(any());
    }

    @Test
    void addRmaItemFromOrderRejectsAServiceLineOnAMarketplaceRma() {
        // given: the Allegro delivery line has no marketplace key, so it can never be refunded per line item
        RMA rma = rmaWithStatus(RMAStatus.WaitingForItems);
        rma.setExternalReturnId("r-1");
        OrderItem shipping = orderItemWithQtyAndStatus("item-ship", 1, FulfilmentStatus.Delivered);
        shipping.setService(true);
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(rma);
        when(orderItemsRepository.findById(ORDER_ID, "item-ship")).thenReturn(shipping);
        when(messageSource.getMessage(eq("rma.item.service.not.returnable"), any(), any())).thenReturn("service");

        // when
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            controller.addRmaItemFromOrder(RMA_ID, "item-ship", 1, "Return", null, redirectAttributes, Locale.ENGLISH);
        }

        // then
        verify(redirectAttributes).addFlashAttribute("errorMessage", "service");
        verify(rmaItemsRepository, never()).save(any());
    }

    @Test
    void addRmaItemFromOrderRejectsAnItemAlreadyClaimedByAnotherOpenRma() {
        // given
        RMA rma = rmaWithStatus(RMAStatus.New);
        OrderItem item = orderItemWithQtyAndStatus("item-1", 2, FulfilmentStatus.Delivered);
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(rma);
        when(orderItemsRepository.findById(ORDER_ID, "item-1")).thenReturn(item);
        when(openRmaCoverage.coversOrderItem(STORE_ID, "item-1", RMA_ID)).thenReturn(true);
        when(messageSource.getMessage(eq("rma.item.already.in.open.rma"), any(), any())).thenReturn("claimed");

        // when
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            controller.addRmaItemFromOrder(RMA_ID, "item-1", 1, "Return", null, redirectAttributes, Locale.ENGLISH);
        }

        // then
        verify(redirectAttributes).addFlashAttribute("errorMessage", "claimed");
        verify(rmaItemsRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // closed-RMA guard on the mutating endpoints (Task 7)
    // ------------------------------------------------------------------

    @Test
    void acceptReturnOnAClosedRmaIsBlockedBeforeTouchingTheWarehouse() {
        // given: tab 2 rejected the return while tab 1 still shows an enabled accept form
        RMA rejected = rmaWithStatus(RMAStatus.Rejected);
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(rejected);
        when(messageSource.getMessage(eq("rma.already.closed"), any(), any())).thenReturn("already closed");

        // when
        String view;
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            view = controller.acceptReturn(RMA_ID, ItemCondition.Sealed, true, new RMAItemsForm(),
                    redirectAttributes, Locale.ENGLISH);
        }

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/rma/" + RMA_ID);
        verify(redirectAttributes).addFlashAttribute("errorMessage", "already closed");
        verify(rmaManager, never()).returnSelectedItems(any(), any(), any());
        verify(ordersRMAManager, never()).acceptReturn(any(), any(), any(), any());
    }

    @Test
    void acceptReturnFlashesAnErrorWhenTheMarketplaceDecisionWasRefused() {
        // given
        RMA rma = rmaWithStatus(RMAStatus.WaitingForItems);
        List<RMAItem> rmaItems = List.of(rmaItemWithQty("item-1", 1));
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(rma);
        when(rmaManager.returnSelectedItems(any(), any(), any()))
                .thenReturn(RMAManager.OperationResult.success(rma, rmaItems));
        when(ordersRMAManager.acceptReturn(any(), any(), any(), any())).thenReturn(OperationResult.success());
        when(marketplaceReturnDecisions.returnAccepted(rma, rmaItems, false)).thenReturn(false);
        when(messageSource.getMessage(eq("rma.marketplace.decision.not.sent"), any(), any())).thenReturn("not sent");

        // when
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            controller.acceptReturn(RMA_ID, ItemCondition.Sealed, false, new RMAItemsForm(),
                    redirectAttributes, Locale.ENGLISH);
        }

        // then
        verify(redirectAttributes).addFlashAttribute("errorMessage", "not sent");
    }

    @Test
    void addRmaItemFromOrderOnAClosedRmaIsBlocked() {
        // given
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(rmaWithStatus(RMAStatus.Completed));
        when(messageSource.getMessage(eq("rma.already.closed"), any(), any())).thenReturn("already closed");

        // when
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            controller.addRmaItemFromOrder(RMA_ID, "item-1", 1, "Return", null, redirectAttributes, Locale.ENGLISH);
        }

        // then
        verify(redirectAttributes).addFlashAttribute("errorMessage", "already closed");
        verify(orderItemsRepository, never()).findById(any(), any());
        verify(rmaItemsRepository, never()).save(any());
    }

    @Test
    void resendMarketplaceDecisionOnAnUnknownRmaRedirectsToTheListInsteadOfFailing() {
        // given
        when(rmaRepository.findById(STORE_ID, "nope")).thenReturn(null);
        when(messageSource.getMessage(eq("rma.not.found"), any(), any())).thenReturn("not found");

        // when
        String view;
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            view = controller.resendMarketplaceDecision("nope", redirectAttributes, Locale.ENGLISH);
        }

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/rma");
        verify(redirectAttributes).addFlashAttribute("errorMessage", "not found");
        verify(marketplaceReturnDecisions, never()).resendDecisions(any());
    }

    // ------------------------------------------------------------------
    // Form binding: marketplace-decision fields and storeId never come
    // from the request
    // ------------------------------------------------------------------

    @Test
    void marketplaceDecisionFieldsAndStoreIdAreNotBindableFromTheRequest() {
        // given
        RMA target = new RMA(STORE_ID);
        WebDataBinder binder = new WebDataBinder(target);
        controller.restrictBindableRmaFields(binder);
        MutablePropertyValues posted = new MutablePropertyValues(Map.of(
                "email", "buyer@example.com",
                "storeId", "victim-store",
                "externalReturnId", "forged",
                "marketplaceDecisions[0].commandId", "x"));

        // when
        binder.bind(posted);

        // then
        assertThat(target.getEmail()).isEqualTo("buyer@example.com");
        assertThat(target.getStoreId()).isEqualTo(STORE_ID);
        assertThat(target.getExternalReturnId()).isNull();
        assertThat(target.getMarketplaceDecisions()).isEmpty();
    }

    @Test
    void createRmaStoresTheRmaUnderTheSessionStoreRegardlessOfTheSubmittedOne() {
        // given
        RMA posted = new RMA("victim-store");
        posted.setOrderId(ORDER_ID);
        RMAItem draft = rmaItemWithQty("item-1", 1);
        draft.setDesiredResolution(RMAResolutionType.Return);
        posted.setDraftRmaItems(List.of(draft));
        Order order = mock(Order.class);
        when(order.isEligibleForRMACreation()).thenReturn(true);
        when(orderRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findById(ORDER_ID, "item-1"))
                .thenReturn(orderItemWithQtyAndStatus("item-1", 1, FulfilmentStatus.Delivered));

        // when
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            controller.createRma(posted, redirectAttributes, Locale.ENGLISH);
        }

        // then
        ArgumentCaptor<RMA> saved = ArgumentCaptor.forClass(RMA.class);
        verify(rmaRepository).save(saved.capture());
        assertThat(saved.getValue().getStoreId()).isEqualTo(STORE_ID);
    }
}
