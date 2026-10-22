package pl.commercelink.orders.rma;

import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import pl.commercelink.web.rma.RmaDetailPage;
import pl.commercelink.web.rma.RmaDetailPageFactory;
import pl.commercelink.web.rma.RmaFlash;
import pl.commercelink.web.rma.RmaNotice;
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
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentCreationState;
import pl.commercelink.orders.ShipmentPickup;
import pl.commercelink.orders.ShipmentTrackingStatus;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.event.Event;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.starter.storage.FileStorage;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.warehouse.api.ItemCondition;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    @Mock
    private pl.commercelink.orders.OrdersRepository ordersRepository;

    @Mock
    private RmaShipmentsViewFactory rmaShipmentsViewFactory;

    @Spy
    private RmaDetailPageFactory rmaDetailPageFactory = new RmaDetailPageFactory(realMessages());

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
        verify(marketplaceReturnDecisions, never()).publishAcceptance(any(), any(), anyBoolean());
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
        when(marketplaceReturnDecisions.coversEveryReturnableItem(rma, rmaItems)).thenReturn(true);

        // when
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            controller.acceptReturn(RMA_ID, ItemCondition.Sealed, true, new RMAItemsForm(),
                    redirectAttributes, Locale.ENGLISH);
        }

        // then
        ArgumentCaptor<Boolean> refundDeliveryCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(marketplaceReturnDecisions).publishAcceptance(eq(rma), eq(rmaItems), refundDeliveryCaptor.capture());
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
        when(marketplaceReturnDecisions.coversEveryReturnableItem(rma, rmaItems)).thenReturn(false);

        // when
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            controller.acceptReturn(RMA_ID, ItemCondition.Sealed, true, new RMAItemsForm(),
                    redirectAttributes, Locale.ENGLISH);
        }

        // then
        ArgumentCaptor<Boolean> refundDeliveryCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(marketplaceReturnDecisions).publishAcceptance(eq(rma), eq(rmaItems), refundDeliveryCaptor.capture());
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
        when(marketplaceReturnDecisions.coversEveryReturnableItem(rma, rmaItems)).thenReturn(true);

        // when
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            controller.acceptReturn(RMA_ID, ItemCondition.Sealed, true, new RMAItemsForm(),
                    redirectAttributes, Locale.ENGLISH);
        }

        // then
        InOrder order = inOrder(marketplaceReturnDecisions, ordersRMAManager);
        order.verify(marketplaceReturnDecisions).coversEveryReturnableItem(rma, rmaItems);
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
        verify(marketplaceReturnDecisions).publishAcceptance(eq(rma), eq(rmaItems), refundDeliveryCaptor.capture());
        assertThat(refundDeliveryCaptor.getValue()).isFalse();
        // refundDelivery && coversEveryReturnableItem(...) must short-circuit: an unchecked box never even asks.
        verify(marketplaceReturnDecisions, never()).coversEveryReturnableItem(any(), any());
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
        verify(marketplaceReturnDecisions, never()).publishRejection(any());
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
        verify(marketplaceReturnDecisions, never()).publishRejection(any());
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

    @Test
    void updateRmaWithTheFullFieldSetChangesOnlyWhatTheFormEdits() {
        // given: the next-status dialog posts the new status and every other field as the page's current values
        RMA existingRma = rmaWithStatus(RMAStatus.ItemsReceived);
        existingRma.setRmaId(RMA_ID);
        existingRma.setEmail("buyer@example.com");
        existingRma.setRejectionReason("Kept as it was");
        existingRma.setEmailNotificationsEnabled(true);
        existingRma.setShippingInsurance(4247.5);
        existingRma.setMedia(new ArrayList<>(List.of("store-1/rma/rma-1/a.jpg", "store-1/rma/rma-1/b.pdf")));
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(existingRma);
        RmaDetailPage.CurrentFields current = rmaDetailPageFactory.page(STORE_ID, existingRma, List.of(), List.of(), null,
                false, null, null, null, Locale.ENGLISH).current();
        RMA posted = new RMA(STORE_ID);
        WebDataBinder binder = new WebDataBinder(posted);
        controller.restrictBindableRmaFields(binder);
        binder.bind(new MutablePropertyValues(Map.of(
                "status", "Processing",
                "email", current.email(),
                "rejectionReason", current.rejectionReason(),
                "emailNotificationsEnabled", String.valueOf(current.notifications()),
                "shippingInsurance", current.insurance())));

        // when
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            controller.updateRma(RMA_ID, posted, current.media(), emptyMedia, redirectAttributes, Locale.ENGLISH);
        }

        // then
        verify(rmaLifecycle).update(existingRma);
        assertThat(existingRma.getStatus()).isEqualTo(RMAStatus.Processing);
        assertThat(existingRma.getEmail()).isEqualTo("buyer@example.com");
        assertThat(existingRma.getRejectionReason()).isEqualTo("Kept as it was");
        assertThat(existingRma.isEmailNotificationsEnabled()).isTrue();
        assertThat(existingRma.getShippingInsurance()).isEqualTo(4247.5);
        assertThat(existingRma.getMedia()).containsExactly("store-1/rma/rma-1/a.jpg", "store-1/rma/rma-1/b.pdf");
        verifyNoInteractions(fileStorage);
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
        verify(marketplaceReturnDecisions).publishRejection(existingRma);
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
        verify(marketplaceReturnDecisions, never()).publishRejection(any());
    }

    // ------------------------------------------------------------------
    // addRmaItemFromOrder: the Task 10 validation block, which now feeds
    // a real Allegro refund's lineItems[].quantity, so it must never let
    // a bad quantity through and must never mutate on rejection.
    // ------------------------------------------------------------------

    static Stream<Arguments> invalidOrderItemQuantities() {
        return Stream.of(
                Arguments.of(Named.of("order item is missing", null), "item-missing", 1),
                Arguments.of(Named.of("order item already returned",
                        orderItemWithQtyAndStatus("item-1", 3, FulfilmentStatus.Returned)), "item-1", 1),
                Arguments.of(Named.of("non-positive quantity",
                        orderItemWithQtyAndStatus("item-1", 3, FulfilmentStatus.Delivered)), "item-1", 0),
                Arguments.of(Named.of("quantity exceeds the order item qty",
                        orderItemWithQtyAndStatus("item-1", 2, FulfilmentStatus.Delivered)), "item-1", 3));
    }

    @ParameterizedTest
    @MethodSource("invalidOrderItemQuantities")
    void addRmaItemFromOrderRejectsAnInvalidOrderItemOrQuantity(OrderItem orderItem, String itemId, int qty) {
        // given
        RMA rma = rmaWithStatus(RMAStatus.New);
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(rma);
        when(orderItemsRepository.findById(ORDER_ID, itemId)).thenReturn(orderItem);
        when(messageSource.getMessage(eq("rma.item.invalid.quantity"), any(), any())).thenReturn("invalid");

        // when
        String view;
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            view = controller.addRmaItemFromOrder(RMA_ID, itemId, qty, "Return", null,
                    redirectAttributes, Locale.ENGLISH);
        }

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/rma/" + RMA_ID);
        assertThat(flashedNotice()).isEqualTo(itemsRefusal("invalid"));
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
        assertThat(flashedNotice()).isEqualTo(itemsRefusal("service"));
        verify(rmaItemsRepository, never()).save(any());
    }

    @Test
    void addRmaItemFromOrderRejectsAnItemAlreadyClaimedByAnotherOpenRma() {
        // given
        RMA rma = rmaWithStatus(RMAStatus.New);
        OrderItem item = orderItemWithQtyAndStatus("item-1", 2, FulfilmentStatus.Delivered);
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(rma);
        when(orderItemsRepository.findById(ORDER_ID, "item-1")).thenReturn(item);
        when(openRmaCoverage.coveredByAnotherOpenRma(STORE_ID, "item-1", RMA_ID)).thenReturn(true);
        when(messageSource.getMessage(eq("rma.item.already.in.open.rma"), any(), any())).thenReturn("claimed");

        // when
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            controller.addRmaItemFromOrder(RMA_ID, "item-1", 1, "Return", null, redirectAttributes, Locale.ENGLISH);
        }

        // then
        assertThat(flashedNotice()).isEqualTo(itemsRefusal("claimed"));
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
        when(marketplaceReturnDecisions.publishAcceptance(rma, rmaItems, false)).thenReturn(false);
        when(messageSource.getMessage(eq("rma.marketplace.decision.not.sent"), any(), any())).thenReturn("not sent");

        // when
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            controller.acceptReturn(RMA_ID, ItemCondition.Sealed, false, new RMAItemsForm(),
                    redirectAttributes, Locale.ENGLISH);
        }

        // then
        assertThat(flashedNotice()).isEqualTo(itemsRefusal("not sent"));
    }

    @FunctionalInterface
    private interface ClosedRmaEndpoint {
        String call(RMAController controller, RedirectAttributes redirectAttributes);
    }

    static Stream<Arguments> endpointsGuardedAgainstAClosedRma() {
        return Stream.of(
                endpoint("addRmaItemFromOrder", (c, ra) ->
                        c.addRmaItemFromOrder(RMA_ID, "item-1", 1, "Return", null, ra, Locale.ENGLISH)),
                endpoint("markItemsAsReceived", (c, ra) ->
                        c.markItemsAsReceived(RMA_ID, new RMAItemsForm(), ra, Locale.ENGLISH)),
                endpoint("splitRmaItem", (c, ra) ->
                        c.splitRmaItem(RMA_ID, "rma-item-1", 1, 1, ra, Locale.ENGLISH)),
                endpoint("editRmaShipping", (c, ra) ->
                        c.editRmaShipping(RMA_ID, null, new org.springframework.ui.ExtendedModelMap(), ra, Locale.ENGLISH)),
                endpoint("updateShippingDetails", (c, ra) -> {
                    RMA postedRma = new RMA(STORE_ID);
                    postedRma.setShippingDetails(new ShippingDetails());
                    return c.updateShippingDetails(RMA_ID, postedRma, false, new org.springframework.ui.ExtendedModelMap(), ra, Locale.ENGLISH);
                }),
                endpoint("updateShipments", (c, ra) -> {
                    RMA postedRma = new RMA(STORE_ID);
                    postedRma.setShipments(List.of(new Shipment(ShipmentType.Courier)));
                    return c.updateShipments(RMA_ID, postedRma, List.of(), ra, Locale.ENGLISH);
                }),
                endpoint("markItemsAsReturnedToClient", (c, ra) ->
                        c.markItemsAsReturnedToClient(RMA_ID, new RMAItemsForm(), ra, Locale.ENGLISH)),
                endpoint("markItemsAsSentToDistributor", (c, ra) ->
                        c.markItemsAsSentToDistributor(RMA_ID, new RMAItemsForm(), ra, Locale.ENGLISH)),
                endpoint("checkSerialNumbers", (c, ra) ->
                        c.checkSerialNumbers(RMA_ID, new RMAItemsForm(), ra, Locale.ENGLISH)),
                endpoint("deleteItems", (c, ra) ->
                        c.deleteRMAItems(RMA_ID, new RMAItemsForm(), ra, Locale.ENGLISH)));
    }

    private static Arguments endpoint(String name, ClosedRmaEndpoint endpoint) {
        return Arguments.of(Named.of(name, endpoint));
    }

    @ParameterizedTest
    @MethodSource("endpointsGuardedAgainstAClosedRma")
    void mutatingEndpointsOnAClosedRmaAreBlocked(ClosedRmaEndpoint endpoint) {
        // given
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(rmaWithStatus(RMAStatus.Completed));
        when(messageSource.getMessage(eq("rma.already.closed"), any(), any())).thenReturn("already closed");

        // when
        String view;
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            view = endpoint.call(controller, redirectAttributes);
        }

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/rma/" + RMA_ID);
        verify(redirectAttributes).addFlashAttribute("errorMessage", "already closed");
        verifyNoInteractions(rmaManager);
        verify(rmaItemsRepository, never()).delete(anyList());
        verify(rmaItemsRepository, never()).delete(any(RMAItem.class));
        verify(orderItemsRepository, never()).findById(any(), any());
        verify(rmaItemsRepository, never()).findById(any(), any());
        verify(rmaItemsRepository, never()).save(any());
        verify(rmaItemsRepository, never()).batchSave(any());
        verify(rmaManager, never()).markItemsAsReceived(any(), any(), any());
        verify(rmaRepository, never()).save(any());
    }

    @Test
    void splitWithInvalidQuantitiesUsesTheLocalizedMessage() {
        // given
        RMAItem item = new RMAItem();
        item.setQty(3);
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(rmaWithStatus(RMAStatus.WaitingForItems));
        when(rmaItemsRepository.findById(RMA_ID, "rma-item-1")).thenReturn(item);
        when(messageSource.getMessage(eq("rma.detail.error.split.quantities"), any(), any())).thenReturn("bad split");

        // when
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            controller.splitRmaItem(RMA_ID, "rma-item-1", 1, 1, redirectAttributes, Locale.ENGLISH);
        }

        // then
        assertThat(flashedNotice()).isEqualTo(itemsRefusal("bad split"));
        verify(rmaItemsRepository, never()).save(any());
    }

    @Test
    void deletingOnlyShippedItemsUsesTheLocalizedMessage() {
        // given
        RMAItem shipped = new RMAItem();
        shipped.setStatus(RMAItemStatus.SentForRepair);
        shipped.setRmaItemId("rma-item-1");
        shipped.setSelected(true);
        RMAItemsForm form = new RMAItemsForm(List.of(shipped));
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(rmaWithStatus(RMAStatus.WaitingForItems));
        when(rmaItemsRepository.findByRmaId(RMA_ID)).thenReturn(List.of(shipped));
        when(messageSource.getMessage(eq("rma.detail.error.delete.status"), any(), any())).thenReturn("only new");

        // when
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            controller.deleteRMAItems(RMA_ID, form, redirectAttributes, Locale.ENGLISH);
        }

        // then
        assertThat(flashedNotice()).isEqualTo(itemsRefusal("only new"));
        verify(rmaItemsRepository, never()).delete(anyList());
        verify(rmaItemsRepository, never()).delete(any(RMAItem.class));
    }

    @Test
    void acceptingWithoutTheConditionOfTheItemsIsRefusedInTheItemsCard() {
        // given
        when(messageSource.getMessage(eq("rma.item.condition.required"), any(), any())).thenReturn("condition");

        // when
        controller.acceptReturn(RMA_ID, null, true, new RMAItemsForm(), redirectAttributes, Locale.ENGLISH);

        // then
        assertThat(flashedNotice()).isEqualTo(itemsRefusal("condition"));
        verify(redirectAttributes, never()).addFlashAttribute(eq("errorMessage"), any());
        verifyNoInteractions(rmaManager);
    }

    @Test
    void markingAsSentToDistributorWithNothingToMarkIsRefusedInTheItemsCard() {
        // given
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(rmaWithStatus(RMAStatus.Processing));
        when(rmaManager.markItemsAsSentToDistributor(any(), any(), any())).thenReturn(RMAManager.OperationResult.failure());
        when(messageSource.getMessage(eq("rma.cannot.mark.as.sent.to.distributor.invalid.status"), any(), any()))
                .thenReturn("nothing to send");

        // when
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            controller.markItemsAsSentToDistributor(RMA_ID, new RMAItemsForm(), redirectAttributes, Locale.ENGLISH);
        }

        // then
        assertThat(flashedNotice()).isEqualTo(itemsRefusal("nothing to send"));
        verify(redirectAttributes, never()).addFlashAttribute(eq("errorMessage"), any());
    }

    @Test
    void checkingSerialNumbersWithNothingSelectedIsRefusedInTheItemsCard() {
        // given
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(rmaWithStatus(RMAStatus.Processing));
        when(messageSource.getMessage(eq("rma.check.serial.no.items.selected"), any(), any())).thenReturn("select");

        // when
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            controller.checkSerialNumbers(RMA_ID, new RMAItemsForm(), redirectAttributes, Locale.ENGLISH);
        }

        // then
        assertThat(flashedNotice()).isEqualTo(itemsRefusal("select"));
    }

    @Test
    void splittingAnItemThatIsGoneIsRefusedInTheItemsCard() {
        // given
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(rmaWithStatus(RMAStatus.Processing));
        when(messageSource.getMessage(eq("rma.detail.error.item.not.found"), any(), any())).thenReturn("gone");

        // when
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            controller.splitRmaItem(RMA_ID, "rma-item-1", 1, 1, redirectAttributes, Locale.ENGLISH);
        }

        // then
        assertThat(flashedNotice()).isEqualTo(itemsRefusal("gone"));
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

    // ------------------------------------------------------------------
    // updateShipments: the form edits what it shows, the courier order stays
    // ------------------------------------------------------------------

    private static Shipment typed(String trackingNo, String externalId) {
        Shipment shipment = new Shipment(ShipmentType.Courier);
        shipment.setCarrier("DPD");
        shipment.setTrackingNo(trackingNo);
        shipment.setShippedAt(LocalDateTime.of(2026, 10, 6, 9, 0));
        shipment.setExternalId(externalId);
        return shipment;
    }

    private static Shipment courierOrder(String trackingNo, String externalId) {
        Shipment shipment = typed(trackingNo, externalId);
        shipment.setProvider("furgonetka");
        shipment.setPickUpAddressId("addr-1");
        shipment.setPickup(ShipmentPickup.awaiting());
        return shipment;
    }

    /** Posts the form as the page renders it: it shows every package of the RMA that is not being created. */
    private RMA saveShipments(RMA existing, Shipment... posted) {
        List<String> shown = existing.getShipments().stream()
                .filter(s -> s.getCreation() == null && s.getExternalId() != null)
                .map(Shipment::getExternalId).toList();
        return saveShipments(existing, shown, posted);
    }

    private RMA saveShipments(RMA existing, List<String> shownPackages, Shipment... posted) {
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(existing);
        RMA postedRma = new RMA(STORE_ID);
        postedRma.setShipments(new ArrayList<>(List.of(posted)));
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            controller.updateShipments(RMA_ID, postedRma, shownPackages, redirectAttributes, Locale.ENGLISH);
        }
        ArgumentCaptor<RMA> saved = ArgumentCaptor.forClass(RMA.class);
        verify(rmaRepository).save(saved.capture());
        return saved.getValue();
    }

    /** A row as the form posts it: no subscription, dates to the minute. */
    private static Shipment formRow(String trackingNo, LocalDateTime shippedAt) {
        Shipment shipment = typed(trackingNo, null);
        shipment.setShippedAt(shippedAt);
        return shipment;
    }

    private static Shipment tracked(String trackingNo, LocalDateTime shippedAt) {
        Shipment shipment = typed(trackingNo, null);
        shipment.setShippedAt(shippedAt);
        shipment.setTrackingSubscriptionStatus(ShipmentTrackingStatus.ACTIVE);
        shipment.setTrackingSubscriptionId("sub-" + trackingNo);
        shipment.setTrackingExternalId("ext-" + trackingNo);
        return shipment;
    }

    @Test
    void aSaveWithoutChangesKeepsTheTrackingSubscriptionAndSeconds() {
        // given: the form shows the date to the minute
        LocalDateTime stored = LocalDateTime.of(2026, 10, 6, 9, 31, 17);
        RMA existing = rmaWithStatus(RMAStatus.Processing);
        existing.setShipments(new ArrayList<>(List.of(tracked("T-1", stored))));

        // when
        RMA saved = saveShipments(existing, formRow("T-1", stored.truncatedTo(ChronoUnit.MINUTES)));

        // then
        Shipment shipment = saved.getShipments().get(0);
        assertThat(shipment.getTrackingSubscriptionStatus()).isEqualTo(ShipmentTrackingStatus.ACTIVE);
        assertThat(shipment.getTrackingSubscriptionId()).isEqualTo("sub-T-1");
        assertThat(shipment.getTrackingExternalId()).isEqualTo("ext-T-1");
        assertThat(shipment.getShippedAt()).isEqualTo(stored);
    }

    @Test
    void editingOneShipmentLeavesTheOthersTracking() {
        // given
        LocalDateTime at = LocalDateTime.of(2026, 10, 6, 9, 31, 17);
        RMA existing = rmaWithStatus(RMAStatus.Processing);
        existing.setShipments(new ArrayList<>(List.of(tracked("T-1", at), tracked("T-2", at))));

        // when: only the second number is corrected
        RMA saved = saveShipments(existing,
                formRow("T-1", at.truncatedTo(ChronoUnit.MINUTES)),
                formRow("T-2-fixed", at.truncatedTo(ChronoUnit.MINUTES)));

        // then
        assertThat(saved.getShipments().get(0).getTrackingSubscriptionStatus()).isEqualTo(ShipmentTrackingStatus.ACTIVE);
        assertThat(saved.getShipments().get(0).getShippedAt()).isEqualTo(at);
        assertThat(saved.getShipments().get(1).getTrackingNo()).isEqualTo("T-2-fixed");
        assertThat(saved.getShipments().get(1).getTrackingSubscriptionStatus()).isNull();
    }

    @Test
    void aChangedShippedAtIsTakenFromTheForm() {
        // given
        LocalDateTime stored = LocalDateTime.of(2026, 10, 6, 9, 31, 17);
        RMA existing = rmaWithStatus(RMAStatus.Processing);
        existing.setShipments(new ArrayList<>(List.of(tracked("T-1", stored))));

        // when: another minute, and a delivery date typed in
        Shipment posted = formRow("T-1", LocalDateTime.of(2026, 10, 6, 9, 45));
        posted.setDeliveredAt(LocalDateTime.of(2026, 10, 8, 12, 0));
        RMA saved = saveShipments(existing, posted);

        // then
        assertThat(saved.getShipments().get(0).getShippedAt()).isEqualTo(LocalDateTime.of(2026, 10, 6, 9, 45));
        assertThat(saved.getShipments().get(0).getDeliveredAt()).isEqualTo(LocalDateTime.of(2026, 10, 8, 12, 0));
    }

    @Test
    void aRowAfterAShipmentBeingCreatedKeepsItsOwnTrackingAndSeconds() {
        // given: the row being created holds index 0, so the form posts the typed shipment as shipments[1] and Spring
        // fills index 0 with an empty row
        LocalDateTime shipped = LocalDateTime.of(2026, 10, 6, 9, 31, 17);
        LocalDateTime delivered = LocalDateTime.of(2026, 10, 8, 12, 4, 51);
        Shipment inFlight = new Shipment(ShipmentType.Courier);
        inFlight.setCreation(ShipmentCreationState.pending("cmd-1", LocalDateTime.now()));
        Shipment typedByHand = tracked("T-1", shipped);
        typedByHand.setDeliveredAt(delivered);
        RMA existing = rmaWithStatus(RMAStatus.Processing);
        existing.setShipments(new ArrayList<>(List.of(inFlight, typedByHand)));
        Shipment row = formRow("T-1", shipped.truncatedTo(ChronoUnit.MINUTES));
        row.setDeliveredAt(delivered.truncatedTo(ChronoUnit.MINUTES));

        // when
        RMA saved = saveShipments(existing, new Shipment(ShipmentType.Courier), row);

        // then
        assertThat(saved.getShipments()).hasSize(2);
        Shipment kept = saved.getShipments().get(0);
        assertThat(kept.getTrackingNo()).isEqualTo("T-1");
        assertThat(kept.getTrackingSubscriptionStatus()).isEqualTo(ShipmentTrackingStatus.ACTIVE);
        assertThat(kept.getTrackingSubscriptionId()).isEqualTo("sub-T-1");
        assertThat(kept.getShippedAt()).isEqualTo(shipped);
        assertThat(kept.getDeliveredAt()).isEqualTo(delivered);
        assertThat(saved.getShipments().get(1)).isSameAs(inFlight);
    }

    @Test
    void aPackageMatchedByItsIdKeepsTrackingAndSecondsWhenItsDateIsEdited() {
        // given: a package of the shipping integration, followed by its tracking
        LocalDateTime shipped = LocalDateTime.of(2026, 10, 6, 9, 31, 17);
        LocalDateTime delivered = LocalDateTime.of(2026, 10, 8, 12, 4, 51);
        Shipment manual = tracked("T-0", shipped);
        Shipment fromIntegration = tracked("T-1", shipped);
        fromIntegration.setExternalId("EXT-1");
        fromIntegration.setDeliveredAt(delivered);
        RMA existing = rmaWithStatus(RMAStatus.Processing);
        existing.setShipments(new ArrayList<>(List.of(manual, fromIntegration)));

        // when: the shipping date of the package is moved, its delivery date shown as it was
        Shipment packageRow = typed("T-1", "EXT-1");
        packageRow.setShippedAt(LocalDateTime.of(2026, 10, 7, 8, 0));
        packageRow.setDeliveredAt(delivered.truncatedTo(ChronoUnit.MINUTES));
        RMA saved = saveShipments(existing, formRow("T-0", shipped.truncatedTo(ChronoUnit.MINUTES)), packageRow);

        // then
        Shipment kept = saved.getShipments().get(1);
        assertThat(kept.getExternalId()).isEqualTo("EXT-1");
        assertThat(kept.getTrackingSubscriptionStatus()).isEqualTo(ShipmentTrackingStatus.ACTIVE);
        assertThat(kept.getTrackingSubscriptionId()).isEqualTo("sub-T-1");
        assertThat(kept.getShippedAt()).isEqualTo(LocalDateTime.of(2026, 10, 7, 8, 0));
        assertThat(kept.getDeliveredAt()).isEqualTo(delivered);
        assertThat(saved.getShipments().get(0).getTrackingSubscriptionId()).isEqualTo("sub-T-0");
    }

    @Test
    void aShipmentBeingCreatedSurvivesAnEditOfTheOthers() {
        // given: it has no shipping data yet, and losing it would drop the label its command pays for
        Shipment inFlight = new Shipment(ShipmentType.Courier);
        inFlight.setCreation(ShipmentCreationState.pending("cmd-1", LocalDateTime.now()));
        RMA existing = rmaWithStatus(RMAStatus.Processing);
        existing.setShipments(new ArrayList<>(List.of(typed("T-1", null), inFlight)));

        // when
        RMA saved = saveShipments(existing, typed("T-1-fixed", null), new Shipment(ShipmentType.Courier));

        // then
        assertThat(saved.getShipments()).hasSize(2);
        assertThat(saved.getShipments().get(0).getTrackingNo()).isEqualTo("T-1-fixed");
        assertThat(saved.getShipments().get(1)).isSameAs(inFlight);
    }

    @Test
    void theCourierOrderAndItsPickupStayWithTheirPackage() {
        // given: the form carries the package id, not the courier order
        RMA existing = rmaWithStatus(RMAStatus.Processing);
        existing.setShipments(new ArrayList<>(List.of(courierOrder("T-1", "EXT-1"))));

        // when: the operator corrects the tracking number
        RMA saved = saveShipments(existing, typed("T-1-fixed", "EXT-1"));

        // then
        Shipment shipment = saved.getShipments().get(0);
        assertThat(shipment.getTrackingNo()).isEqualTo("T-1-fixed");
        assertThat(shipment.getProvider()).isEqualTo("furgonetka");
        assertThat(shipment.getPickUpAddressId()).isEqualTo("addr-1");
        assertThat(shipment.awaitsPickup()).isTrue();
    }

    @Test
    void aPackageIdTheRmaNeverHadIsNotTakenFromTheForm() {
        // given
        RMA existing = rmaWithStatus(RMAStatus.Processing);
        existing.setShipments(new ArrayList<>(List.of(typed("T-1", null))));

        // when
        RMA saved = saveShipments(existing, typed("T-1", "FORGED"));

        // then
        assertThat(saved.getShipments().get(0).getExternalId()).isNull();
    }

    @Test
    void aRemovedPackageLeavesThePickupList() {
        // given
        RMA existing = rmaWithStatus(RMAStatus.Processing);
        existing.setShipments(new ArrayList<>(List.of(courierOrder("T-1", "EXT-1"), courierOrder("T-2", "EXT-2"))));

        // when
        RMA saved = saveShipments(existing, typed("T-2", "EXT-2"));

        // then
        assertThat(saved.getShipments()).extracting(Shipment::getExternalId).containsExactly("EXT-2");
    }

    @Test
    void aPackageCreatedAfterTheFormWasOpenedSurvivesItsSave() {
        // given: the form was rendered while EXT-2 was still being created, so it neither shows nor posts it;
        // the creation settled in the background before the operator saved
        RMA existing = rmaWithStatus(RMAStatus.Processing);
        existing.setShipments(new ArrayList<>(List.of(courierOrder("T-1", "EXT-1"), courierOrder("T-2", "EXT-2"))));

        // when: the operator saves the form as it was, showing only EXT-1
        RMA saved = saveShipments(existing, List.of("EXT-1"), typed("T-1", "EXT-1"));

        // then: the paid package stays, still waiting for its pickup
        assertThat(saved.getShipments()).extracting(Shipment::getExternalId).containsExactly("EXT-1", "EXT-2");
        assertThat(saved.getShipments().get(1).awaitsPickup()).isTrue();
    }

    @Test
    void aPackageTheFormShowedCanStillBeRemoved() {
        // given
        RMA existing = rmaWithStatus(RMAStatus.Processing);
        existing.setShipments(new ArrayList<>(List.of(courierOrder("T-1", "EXT-1"), courierOrder("T-2", "EXT-2"))));

        // when: both were shown, the operator removed EXT-1
        RMA saved = saveShipments(existing, List.of("EXT-1", "EXT-2"), typed("T-2", "EXT-2"));

        // then
        assertThat(saved.getShipments()).extracting(Shipment::getExternalId).containsExactly("EXT-2");
    }

    /** A package of a dispatch with several parcels: they all share the dispatch's externalId. */
    private static Shipment parcelOfDispatch(String trackingNo, ShipmentPickup pickup, Boolean cancellable) {
        Shipment shipment = courierOrder(trackingNo, "EXT-1");
        shipment.setPickup(pickup);
        shipment.setCancellable(cancellable);
        shipment.setTrackingSubscriptionStatus(ShipmentTrackingStatus.ACTIVE);
        shipment.setTrackingSubscriptionId("sub-" + trackingNo);
        shipment.setTrackingExternalId("ext-" + trackingNo);
        return shipment;
    }

    @Test
    void editingAnotherShipmentKeepsEachParcelOfOneDispatchItsOwnTrackingAndPickup() {
        // given
        LocalDateTime at = LocalDateTime.of(2026, 10, 6, 9, 0);
        RMA existing = rmaWithStatus(RMAStatus.Processing);
        existing.setShipments(new ArrayList<>(List.of(
                tracked("T-0", at),
                parcelOfDispatch("P-1", ShipmentPickup.awaiting(), true),
                parcelOfDispatch("P-2", ShipmentPickup.bookedByCarrier("pickup-2"), false))));

        // when: the manual shipment is edited, both parcels are posted as hidden fields
        RMA saved = saveShipments(existing, formRow("T-0-fixed", at), typed("P-1", "EXT-1"), typed("P-2", "EXT-1"));

        // then
        assertThat(saved.getShipments()).hasSize(3);
        Shipment first = saved.getShipments().get(1);
        Shipment second = saved.getShipments().get(2);
        assertThat(first.getTrackingNo()).isEqualTo("P-1");
        assertThat(first.getTrackingSubscriptionId()).isEqualTo("sub-P-1");
        assertThat(first.getTrackingExternalId()).isEqualTo("ext-P-1");
        assertThat(first.getTrackingSubscriptionStatus()).isEqualTo(ShipmentTrackingStatus.ACTIVE);
        assertThat(first.awaitsPickup()).isTrue();
        assertThat(first.getCancellable()).isTrue();
        assertThat(second.getTrackingNo()).isEqualTo("P-2");
        assertThat(second.getTrackingSubscriptionId()).isEqualTo("sub-P-2");
        assertThat(second.getTrackingExternalId()).isEqualTo("ext-P-2");
        assertThat(second.getTrackingSubscriptionStatus()).isEqualTo(ShipmentTrackingStatus.ACTIVE);
        assertThat(second.getPickup().getPickupId()).isEqualTo("pickup-2");
        assertThat(second.getCancellable()).isFalse();
        assertThat(second.getExternalId()).isEqualTo("EXT-1");
    }

    @Test
    void aParcelWhoseNumberWasEditedKeepsItsOwnCourierOrderNotTheOtherParcels() {
        // given
        LocalDateTime at = LocalDateTime.of(2026, 10, 6, 9, 0);
        RMA existing = rmaWithStatus(RMAStatus.Processing);
        existing.setShipments(new ArrayList<>(List.of(
                parcelOfDispatch("P-1", ShipmentPickup.awaiting(), true),
                parcelOfDispatch("P-2", ShipmentPickup.bookedByCarrier("pickup-2"), false))));

        // when: the second parcel's number is corrected
        RMA saved = saveShipments(existing, typed("P-1", "EXT-1"), typed("P-2-fixed", "EXT-1"));

        // then
        Shipment first = saved.getShipments().get(0);
        Shipment second = saved.getShipments().get(1);
        assertThat(first.getTrackingSubscriptionId()).isEqualTo("sub-P-1");
        assertThat(first.getCancellable()).isTrue();
        assertThat(second.getTrackingNo()).isEqualTo("P-2-fixed");
        assertThat(second.getExternalId()).isEqualTo("EXT-1");
        assertThat(second.getPickup().getPickupId()).isEqualTo("pickup-2");
        assertThat(second.getCancellable()).isFalse();
    }

    /** A parcel of a marketplace return as the import stores it: number and carrier, no shipping date. */
    private static Shipment importedParcel(String trackingNo) {
        Shipment shipment = new Shipment(ShipmentType.Courier);
        shipment.setTrackingNo(trackingNo);
        shipment.setCarrier("ALLEGRO");
        return shipment;
    }

    @Test
    void addingAShipmentKeepsTheParcelsImportedWithAMarketplaceReturn() {
        // given
        RMA existing = rmaWithStatus(RMAStatus.WaitingForItems);
        existing.setShipments(new ArrayList<>(List.of(importedParcel("A-1"), importedParcel("A-2"))));

        // when: "add shipment" posts both parcels as hidden fields and the new one at the next index
        RMA saved = saveShipments(existing, importedParcel("A-1"), importedParcel("A-2"),
                formRow("T-new", LocalDateTime.of(2026, 10, 7, 10, 0)));

        // then
        assertThat(saved.getShipments()).extracting(Shipment::getTrackingNo).containsExactly("A-1", "A-2", "T-new");
        assertThat(saved.getShipments().get(0).getShippedAt()).isNull();
        assertThat(saved.getShipments().get(0).getCarrier()).isEqualTo("ALLEGRO");
    }

    @Test
    void editingAnotherShipmentLeavesTheImportedParcelsAsTheyWere() {
        // given
        LocalDateTime at = LocalDateTime.of(2026, 10, 6, 9, 0);
        RMA existing = rmaWithStatus(RMAStatus.WaitingForItems);
        existing.setShipments(new ArrayList<>(List.of(importedParcel("A-1"), tracked("T-0", at), importedParcel("A-2"))));

        // when
        RMA saved = saveShipments(existing, importedParcel("A-1"), formRow("T-0-fixed", at), importedParcel("A-2"));

        // then
        assertThat(saved.getShipments()).extracting(Shipment::getTrackingNo).containsExactly("A-1", "T-0-fixed", "A-2");
        assertThat(saved.getShipments().get(2).getCarrier()).isEqualTo("ALLEGRO");
        assertThat(saved.getShipments().get(2).getShippedAt()).isNull();
    }

    @Test
    void anEmptyNewRowIsStillDropped() {
        // given
        LocalDateTime at = LocalDateTime.of(2026, 10, 6, 9, 0);
        RMA existing = rmaWithStatus(RMAStatus.WaitingForItems);
        existing.setShipments(new ArrayList<>(List.of(tracked("T-0", at))));
        Shipment empty = new Shipment(ShipmentType.Courier);
        empty.setCarrier(" ");
        empty.setTrackingNo("");

        // when
        RMA saved = saveShipments(existing, formRow("T-0", at), empty);

        // then
        assertThat(saved.getShipments()).extracting(Shipment::getTrackingNo).containsExactly("T-0");
    }

    @Test
    void clearingTheShippedDateOfAManualShipmentRemovesIt() {
        // given
        LocalDateTime at = LocalDateTime.of(2026, 10, 6, 9, 0);
        RMA existing = rmaWithStatus(RMAStatus.WaitingForItems);
        existing.setShipments(new ArrayList<>(List.of(tracked("T-0", at), tracked("T-1", at))));

        // when: the dialog of the second shipment is saved with its shipped date cleared
        RMA saved = saveShipments(existing, formRow("T-0", at), formRow("T-1", null));

        // then
        assertThat(saved.getShipments()).extracting(Shipment::getTrackingNo).containsExactly("T-0");
    }

    @Test
    void aNewShipmentWithoutAShippedDateIsNotSaved() {
        // given
        LocalDateTime at = LocalDateTime.of(2026, 10, 6, 9, 0);
        RMA existing = rmaWithStatus(RMAStatus.WaitingForItems);
        existing.setShipments(new ArrayList<>(List.of(tracked("T-0", at))));

        // when: "add shipment" with a carrier and a number but no date
        RMA saved = saveShipments(existing, formRow("T-0", at), formRow("T-new", null));

        // then
        assertThat(saved.getShipments()).extracting(Shipment::getTrackingNo).containsExactly("T-0");
    }

    @Test
    void anImportedParcelEditedInItsDialogStaysWithoutAShippedDate() {
        // given
        RMA existing = rmaWithStatus(RMAStatus.WaitingForItems);
        existing.setShipments(new ArrayList<>(List.of(importedParcel("A-1"))));
        Shipment edited = importedParcel("A-1-fixed");

        // when
        RMA saved = saveShipments(existing, edited);

        // then
        assertThat(saved.getShipments()).extracting(Shipment::getTrackingNo).containsExactly("A-1-fixed");
        assertThat(saved.getShipments().get(0).getShippedAt()).isNull();
    }

    // ------------------------------------------------------------------
    // action outcomes are shown in the page, with the skipped items named
    // ------------------------------------------------------------------

    private static ResourceBundleMessageSource realMessages() {
        ResourceBundleMessageSource real = new ResourceBundleMessageSource();
        real.setBasename("messages");
        real.setDefaultEncoding("UTF-8");
        real.setFallbackToSystemLocale(false);
        return real;
    }

    private void answerMessagesFromTheRealBundles() {
        ResourceBundleMessageSource real = realMessages();
        when(messageSource.getMessage(any(String.class), any(), any(Locale.class)))
                .thenAnswer(inv -> real.getMessage(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));
    }

    private static RMAItem selectedItem(String id, String name, RMAItemStatus status) {
        RMAItem item = new RMAItem();
        item.setRmaItemId(id);
        item.setName(name);
        item.setStatus(status);
        item.setSelected(true);
        return item;
    }

    private static RmaNotice itemsRefusal(String text) {
        return new RmaNotice(RmaNotice.BAD, text, List.of(), RmaNotice.Scope.ITEMS);
    }

    private RmaNotice flashedNotice() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(redirectAttributes).addFlashAttribute(eq(RmaFlash.ATTRIBUTE), captor.capture());
        return (RmaNotice) captor.getValue();
    }

    // ------------------------------------------------------------------
    // checkSerialNumbers compares the stored numbers
    // ------------------------------------------------------------------

    private RmaNotice checkSerialNumbersOf(RMA rma, RMAItem... posted) {
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(rma);
        answerMessagesFromTheRealBundles();
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            controller.checkSerialNumbers(RMA_ID, new RMAItemsForm(List.of(posted)), redirectAttributes,
                    Locale.forLanguageTag("pl"));
        }
        return flashedNotice();
    }

    private static RMAItem storedItem(String id, String name, String serialNo) {
        RMAItem item = new RMAItem();
        item.setRmaItemId(id);
        item.setItemId("oi-" + id);
        item.setName(name);
        item.setSerialNo(serialNo);
        return item;
    }

    private static OrderItem orderItemWithSerial(String itemId, String serialNo) {
        OrderItem orderItem = new OrderItem();
        orderItem.setOrderId(ORDER_ID);
        orderItem.setItemId(itemId);
        orderItem.setSerialNo(serialNo);
        return orderItem;
    }

    @Test
    void checkSerialNumbersReadsTheSerialFromTheStoredItemNotTheForm() {
        // given
        RMAItem posted = selectedItem("a", "Laptop", RMAItemStatus.New);
        when(rmaItemsRepository.findByRmaId(RMA_ID)).thenReturn(List.of(storedItem("a", "Laptop", "X9")));
        when(orderItemsRepository.findById(ORDER_ID, "oi-a")).thenReturn(orderItemWithSerial("oi-a", "A1"));

        // when
        RmaNotice notice = checkSerialNumbersOf(rmaWithStatus(RMAStatus.WaitingForItems), posted);

        // then
        assertThat(notice.tone()).isEqualTo(RmaNotice.BAD);
        assertThat(notice.text()).isEqualTo("Numery seryjne nie zgadzają się z zamówieniem:");
        assertThat(notice.lines()).containsExactly("Laptop — X9");
        assertThat(notice.scope()).isEqualTo(RmaNotice.Scope.ITEMS);
    }

    @Test
    void checkSerialNumbersAcceptsANumberFromTheOrder() {
        // given
        RMAItem posted = selectedItem("a", "Laptop", RMAItemStatus.New);
        when(rmaItemsRepository.findByRmaId(RMA_ID)).thenReturn(List.of(storedItem("a", "Laptop", "A2")));
        when(orderItemsRepository.findById(ORDER_ID, "oi-a")).thenReturn(orderItemWithSerial("oi-a", "A1, A2"));

        // when
        RmaNotice notice = checkSerialNumbersOf(rmaWithStatus(RMAStatus.WaitingForItems), posted);

        // then
        assertThat(notice.tone()).isEqualTo(RmaNotice.OK);
        assertThat(notice.text()).isEqualTo("Wszystkie numery seryjne zgadzają się z zamówieniem.");
        assertThat(notice.lines()).isEmpty();
    }

    @Test
    void checkSerialNumbersSkipsAnItemWithoutASerialNumber() {
        // given
        RMAItem posted = selectedItem("a", "Laptop", RMAItemStatus.New);
        when(rmaItemsRepository.findByRmaId(RMA_ID)).thenReturn(List.of(storedItem("a", "Laptop", " ")));

        // when
        RmaNotice notice = checkSerialNumbersOf(rmaWithStatus(RMAStatus.WaitingForItems), posted);

        // then
        assertThat(notice.tone()).isEqualTo(RmaNotice.WARN);
        assertThat(notice.text()).isEqualTo("Nie było czego porównać.");
        assertThat(notice.lines()).containsExactly("Nie sprawdzono: Laptop — pozycja RMA nie ma numeru seryjnego.");
        verify(orderItemsRepository, never()).findById(any(), any());
    }

    @Test
    void checkSerialNumbersWithAnOrderItemWithoutSerialsIsNotAMismatchButNothingWasCompared() {
        // given
        RMAItem posted = selectedItem("a", "Laptop", RMAItemStatus.New);
        when(rmaItemsRepository.findByRmaId(RMA_ID)).thenReturn(List.of(storedItem("a", "Laptop", "X9")));
        when(orderItemsRepository.findById(ORDER_ID, "oi-a")).thenReturn(orderItemWithSerial("oi-a", null));

        // when
        RmaNotice notice = checkSerialNumbersOf(rmaWithStatus(RMAStatus.WaitingForItems), posted);

        // then
        assertThat(notice.tone()).isEqualTo(RmaNotice.WARN);
        assertThat(notice.text()).isEqualTo("Nie było czego porównać.");
        assertThat(notice.lines()).containsExactly(
                "Nie sprawdzono: Laptop — zamówienie nie ma zapisanych numerów seryjnych tej pozycji.");
    }

    @Test
    void checkSerialNumbersSaysAllMatchOnlyForTheComparedItemsAndNamesTheSkippedOnes() {
        // given
        RMAItem laptop = selectedItem("a", "Laptop", RMAItemStatus.New);
        RMAItem cable = selectedItem("b", "Kabel", RMAItemStatus.New);
        RMAItem mouse = selectedItem("c", "Mysz", RMAItemStatus.New);
        when(rmaItemsRepository.findByRmaId(RMA_ID)).thenReturn(List.of(
                storedItem("a", "Laptop", "A1"),
                storedItem("b", "Kabel", null),
                storedItem("c", "Mysz", "M1")));
        when(orderItemsRepository.findById(ORDER_ID, "oi-a")).thenReturn(orderItemWithSerial("oi-a", "A1"));
        when(orderItemsRepository.findById(ORDER_ID, "oi-c")).thenReturn(orderItemWithSerial("oi-c", " "));

        // when
        RmaNotice notice = checkSerialNumbersOf(rmaWithStatus(RMAStatus.WaitingForItems), laptop, cable, mouse);

        // then
        assertThat(notice.tone()).isEqualTo(RmaNotice.OK);
        assertThat(notice.text()).isEqualTo("Wszystkie numery seryjne zgadzają się z zamówieniem.");
        assertThat(notice.lines()).containsExactly(
                "Nie sprawdzono: Kabel — pozycja RMA nie ma numeru seryjnego.",
                "Nie sprawdzono: Mysz — zamówienie nie ma zapisanych numerów seryjnych tej pozycji.");
        verify(rmaItemsRepository).findByRmaId(RMA_ID);
        verify(rmaItemsRepository, never()).findById(any(), any());
    }

    @Test
    void checkSerialNumbersListsTheMismatchesBeforeTheSkippedItems() {
        // given
        RMAItem laptop = selectedItem("a", "Laptop", RMAItemStatus.New);
        RMAItem cable = selectedItem("b", "Kabel", RMAItemStatus.New);
        when(rmaItemsRepository.findByRmaId(RMA_ID)).thenReturn(List.of(
                storedItem("a", "Laptop", "X9"),
                storedItem("b", "Kabel", "")));
        when(orderItemsRepository.findById(ORDER_ID, "oi-a")).thenReturn(orderItemWithSerial("oi-a", "A1"));

        // when
        RmaNotice notice = checkSerialNumbersOf(rmaWithStatus(RMAStatus.WaitingForItems), cable, laptop);

        // then
        assertThat(notice.tone()).isEqualTo(RmaNotice.BAD);
        assertThat(notice.lines()).containsExactly("Laptop — X9",
                "Nie sprawdzono: Kabel — pozycja RMA nie ma numeru seryjnego.");
    }

    @Test
    void checkSerialNumbersListsADuplicatedWrongNumberOnce() {
        // given
        RMAItem posted = selectedItem("a", "Laptop", RMAItemStatus.New);
        when(rmaItemsRepository.findByRmaId(RMA_ID)).thenReturn(List.of(storedItem("a", "Laptop", "X9, X9")));
        when(orderItemsRepository.findById(ORDER_ID, "oi-a")).thenReturn(orderItemWithSerial("oi-a", "A1"));

        // when
        RmaNotice notice = checkSerialNumbersOf(rmaWithStatus(RMAStatus.WaitingForItems), posted);

        // then
        assertThat(notice.lines()).containsExactly("Laptop \u2014 X9");
    }

    @Test
    void checkSerialNumbersReportsOnlyTheNumbersOutsideTheOrder() {
        // given
        RMAItem posted = selectedItem("a", "Laptop", RMAItemStatus.New);
        when(rmaItemsRepository.findByRmaId(RMA_ID)).thenReturn(List.of(storedItem("a", "Laptop", "A1, X9")));
        when(orderItemsRepository.findById(ORDER_ID, "oi-a")).thenReturn(orderItemWithSerial("oi-a", "A1, A2"));

        // when
        RmaNotice notice = checkSerialNumbersOf(rmaWithStatus(RMAStatus.WaitingForItems), posted);

        // then
        assertThat(notice.lines()).containsExactly("Laptop \u2014 X9");
    }

    @Test
    void checkSerialNumbersSkipsAnItemWithANullSerialNumber() {
        // given
        RMAItem posted = selectedItem("a", "Laptop", RMAItemStatus.New);
        when(rmaItemsRepository.findByRmaId(RMA_ID)).thenReturn(List.of(storedItem("a", "Laptop", null)));

        // when
        RmaNotice notice = checkSerialNumbersOf(rmaWithStatus(RMAStatus.WaitingForItems), posted);

        // then
        assertThat(notice.tone()).isEqualTo(RmaNotice.WARN);
        assertThat(notice.lines()).containsExactly("Nie sprawdzono: Laptop — pozycja RMA nie ma numeru seryjnego.");
        verify(orderItemsRepository, never()).findById(any(), any());
    }

    @Test
    void checkSerialNumbersWithoutTheOrderItemReportsItInsteadOfFailing() {
        // given
        RMAItem posted = selectedItem("a", "Laptop", RMAItemStatus.New);
        when(rmaItemsRepository.findByRmaId(RMA_ID)).thenReturn(List.of(storedItem("a", "Laptop", "X9")));
        when(orderItemsRepository.findById(ORDER_ID, "oi-a")).thenReturn(null);

        // when
        RmaNotice notice = checkSerialNumbersOf(rmaWithStatus(RMAStatus.WaitingForItems), posted);

        // then
        assertThat(notice.tone()).isEqualTo(RmaNotice.BAD);
        assertThat(notice.lines()).containsExactly("Nie znaleziono pozycji zamówienia dla: Laptop");
    }

    @Test
    void checkSerialNumbersOnAnRmaWithoutAnOrderReportsEveryItem() {
        // given
        RMA rma = rmaWithStatus(RMAStatus.WaitingForItems);
        rma.setOrderId(null);
        RMAItem posted = selectedItem("a", "Laptop", RMAItemStatus.New);
        when(rmaItemsRepository.findByRmaId(RMA_ID)).thenReturn(List.of(storedItem("a", "Laptop", "X9")));

        // when
        RmaNotice notice = checkSerialNumbersOf(rma, posted);

        // then
        assertThat(notice.tone()).isEqualTo(RmaNotice.BAD);
        assertThat(notice.lines()).containsExactly("Nie znaleziono pozycji zamówienia dla: Laptop");
        verify(orderItemsRepository, never()).findById(any(), any());
    }

    @Test
    void markItemsAsSentToDistributorReportsTheSkippedItems() {
        // given
        RMAItem received = selectedItem("a", "A", RMAItemStatus.Received);
        RMAItem fresh = selectedItem("b", "B", RMAItemStatus.New);
        RMA rma = rmaWithStatus(RMAStatus.WaitingForItems);
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(rma);
        when(rmaItemsRepository.findByRmaId(RMA_ID)).thenReturn(List.of(received, fresh));
        when(rmaManager.markItemsAsSentToDistributor(any(), any(), any()))
                .thenReturn(RMAManager.OperationResult.success(rma, List.of(received)));
        answerMessagesFromTheRealBundles();

        // when
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            controller.markItemsAsSentToDistributor(RMA_ID, new RMAItemsForm(List.of(received, fresh)),
                    redirectAttributes, Locale.forLanguageTag("pl"));
        }

        // then
        RmaNotice notice = flashedNotice();
        assertThat(notice.lines()).containsExactly("Pominięto 1: B — jeszcze nie odebrana.");
        assertThat(notice.tone()).isEqualTo(RmaNotice.OK);
        verify(redirectAttributes, never()).addFlashAttribute(eq("errorMessage"), any());
    }

    @Test
    void deleteItemsRemovesTheDeletableOnesLowersTheInsuranceAndNamesTheSkippedOnes() {
        // given
        RMAItem received = selectedItem("a", "A", RMAItemStatus.Received);
        received.setPrice(100.0);
        received.setQty(2);
        RMAItem inRepair = selectedItem("b", "B", RMAItemStatus.SentForRepair);
        inRepair.setPrice(50.0);
        inRepair.setQty(1);
        RMA rma = rmaWithStatus(RMAStatus.ItemsReceived);
        rma.setShippingInsurance(250.0);
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(rma);
        when(rmaItemsRepository.findByRmaId(RMA_ID)).thenReturn(List.of(received, inRepair));
        answerMessagesFromTheRealBundles();

        // when
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            controller.deleteRMAItems(RMA_ID, new RMAItemsForm(List.of(received, inRepair)), redirectAttributes,
                    Locale.forLanguageTag("pl"));
        }

        // then
        verify(rmaItemsRepository).delete(List.of(received));
        verify(rmaRepository).save(rma);
        assertThat(rma.getShippingInsurance()).isEqualTo(50.0);
        RmaNotice notice = flashedNotice();
        assertThat(notice.tone()).isEqualTo(RmaNotice.OK);
        assertThat(notice.text()).isEqualTo("Pozycje usunięte: 1.");
        assertThat(notice.lines()).containsExactly("Pominięto 1: B — w naprawie.");
        assertThat(notice.scope()).isEqualTo(RmaNotice.Scope.ITEMS);
    }

    private RmaNotice acceptOnMarketplace(boolean refundDelivery, boolean coversWholeOrder) {
        RMA rma = rmaWithStatus(RMAStatus.WaitingForItems);
        rma.setExternalReturnId("ext-1");
        RMAItem item = selectedItem("a", "A", RMAItemStatus.Received);
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(rma);
        when(rmaItemsRepository.findByRmaId(RMA_ID)).thenReturn(List.of(item));
        when(rmaManager.returnSelectedItems(any(), any(), any()))
                .thenReturn(RMAManager.OperationResult.success(rma, List.of(item)));
        when(ordersRMAManager.acceptReturn(any(), any(), any(), any())).thenReturn(OperationResult.success());
        if (refundDelivery) {
            when(marketplaceReturnDecisions.coversEveryReturnableItem(any(), any())).thenReturn(coversWholeOrder);
        }
        when(marketplaceReturnDecisions.publishAcceptance(any(), any(), anyBoolean())).thenReturn(true);
        answerMessagesFromTheRealBundles();
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            controller.acceptReturn(RMA_ID, ItemCondition.Sealed, refundDelivery, new RMAItemsForm(List.of(item)),
                    redirectAttributes, Locale.forLanguageTag("pl"));
        }
        return flashedNotice();
    }

    @Test
    void acceptReturnOnAMarketplaceRmaSaysTheDeliveryRefundWasRequested() {
        // when
        RmaNotice notice = acceptOnMarketplace(true, true);

        // then
        assertThat(notice.lines()).containsExactly("Zlecono zwrot kosztu dostawy.");
    }

    @Test
    void acceptReturnOnAMarketplaceRmaSaysThereIsNoDeliveryRefundWhenTheOperatorUntickedIt() {
        // when
        RmaNotice notice = acceptOnMarketplace(false, true);

        // then
        assertThat(notice.lines()).containsExactly("Bez zwrotu kosztu dostawy.");
    }

    @Test
    void acceptReturnOnAMarketplaceRmaSaysTheDeliveryWasNotRefundedWhenTheReturnIsPartial() {
        // when
        RmaNotice notice = acceptOnMarketplace(true, false);

        // then
        assertThat(notice.lines()).containsExactly(
                "Koszt dostawy nie został zwrócony: zwrot nie obejmuje wszystkich pozycji zamówienia.");
    }

    @Test
    void aLegacyFlashMessageIsShownInThePageNotInTheLayout() {
        // given
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(rmaWithStatus(RMAStatus.WaitingForItems));
        Model model = new ExtendedModelMap();
        model.addAttribute("errorMessage", "X");

        // when
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            controller.showRmaDetail(RMA_ID, null, null, model, Locale.ENGLISH);
        }

        // then
        assertThat(model.asMap()).doesNotContainKeys("errorMessage", "successMessage");
        RmaNotice notice = (RmaNotice) model.getAttribute(RmaFlash.ATTRIBUTE);
        assertThat(notice.text()).isEqualTo("X");
        assertThat(notice.tone()).isEqualTo(RmaNotice.BAD);
    }

    @Test
    void showRmaDetailPassesTheOpenDialogOnlyWhenKnown() {
        // given
        RMA rma = rmaWithStatus(RMAStatus.New);
        rma.setRmaId(RMA_ID);
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(rma);
        Model known = new ExtendedModelMap();
        Model unknown = new ExtendedModelMap();

        // when
        String view;
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            view = controller.showRmaDetail(RMA_ID, "next", null, known, Locale.ENGLISH);
            controller.showRmaDetail(RMA_ID, "bogus", null, unknown, Locale.ENGLISH);
        }

        // then
        assertThat(view).isEqualTo("rma-detail");
        assertThat(((RmaDetailPage) known.getAttribute("page")).openDialog()).isEqualTo("next");
        assertThat(((RmaDetailPage) unknown.getAttribute("page")).openDialog()).isNull();
        assertThat(known.asMap()).containsKeys("rma", "rmaItemsForm", "rmaShipments")
                .doesNotContainKeys("isClosed", "backofficeDomain", "rmaStatusTypes");
    }
}
