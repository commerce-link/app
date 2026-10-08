package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderIndexEntry;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.fulfilment.FulfilmentAllocation;
import pl.commercelink.orders.fulfilment.FulfilmentForm;
import pl.commercelink.orders.fulfilment.FulfilmentGroup;
import pl.commercelink.orders.fulfilment.FulfilmentSource;
import pl.commercelink.orders.fulfilment.FulfilmentQueue;
import pl.commercelink.orders.fulfilment.ManualOrderFulfilment;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.web.fulfilment.FulfilmentQueuePage;
import pl.commercelink.web.fulfilment.FulfilmentQueuePageFactory;
import pl.commercelink.web.fulfilment.FulfilmentSelectPage;
import pl.commercelink.web.fulfilment.FulfilmentSelectPageFactory;
import pl.commercelink.web.orders.OrderNotice;
import pl.commercelink.web.fulfilment.SkippedGroups;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FulfilmentControllerTest {

    private static final Locale PL = Locale.forLanguageTag("pl");

    @Mock
    private OrderItemsRepository orderItemsRepository;
    @Mock
    private FulfilmentQueue fulfilmentQueue;
    @Mock
    private ManualOrderFulfilment manualOrderFulfilment;
    @Mock
    private SupplierLabels supplierLabels;
    @Mock
    private FulfilmentQueuePageFactory fulfilmentQueuePageFactory;
    @Mock
    private FulfilmentSelectPageFactory fulfilmentSelectPageFactory;
    @Mock
    private MessageSource messageSource;
    @InjectMocks
    private FulfilmentController controller;

    private MockedStatic<CustomSecurityContext> security;

    @BeforeEach
    void setUp() {
        security = mockStatic(CustomSecurityContext.class);
        security.when(CustomSecurityContext::getStoreId).thenReturn("store-1");
        security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(false);
    }

    @AfterEach
    void closeSecurity() {
        security.close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void queuePassesTheLoadedOrdersAndItemCountsToThePageFactory() {
        // given
        Order order = new Order("store-1");
        order.setOrderId("o-1");
        when(orderItemsRepository.findByOrderIdAndStatus("o-1", FulfilmentStatus.New)).thenReturn(List.of(mock(OrderItem.class), mock(OrderItem.class)));
        when(fulfilmentQueue.pickFulfilmentGroup(eq("store-1"), eq(List.of("skipped-1")), any())).thenAnswer(call -> {
            Predicate<Order> criteria = call.getArgument(2);
            assertThat(criteria.test(order)).isTrue();
            return List.of(OrderIndexEntry.fromOrder(order));
        });
        FulfilmentQueuePage page = mock(FulfilmentQueuePage.class);
        when(fulfilmentQueuePageFactory.build(eq(false), eq(SkippedGroups.from(List.of("skipped-1"), "1")), anyList(), anyMap(), anyMap(), any(), eq(PL)))
                .thenReturn(page);
        Model model = new ConcurrentModel();

        // when
        String view = controller.fulfilmentQueue(List.of("skipped-1"), "1", model, PL);

        // then
        assertThat(view).isEqualTo("fulfilment-queue");
        assertThat(model.getAttribute("page")).isSameAs(page);
        ArgumentCaptor<Map<String, Order>> orders = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map<String, Integer>> items = ArgumentCaptor.forClass(Map.class);
        verify(fulfilmentQueuePageFactory).build(anyBoolean(), any(), anyList(), orders.capture(), items.capture(), any(), any());
        assertThat(orders.getValue()).containsEntry("o-1", order);
        assertThat(items.getValue()).containsEntry("o-1", 2);
    }

    @Test
    void submittingWithoutSelectedOrdersGoesBackToTheQueue() {
        // when
        String view = controller.initiateMultiOrderManualFulfilment(List.of(), "default", false, false, false, false, null, null, new ConcurrentModel(), PL);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/fulfilment/queue");
        verifyNoInteractions(manualOrderFulfilment);
    }

    @Test
    void superAdminSubmittingWithoutSelectedOrdersGoesBackToTheQueue() {
        // when
        String view = controller.initiateMultiOrderManualFulfilmentForSuperAdmin("store-9", List.of(), "suggest", false, false, false, false, null, null, new ConcurrentModel(), PL);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/fulfilment/queue");
        verifyNoInteractions(manualOrderFulfilment);
    }

    @Test
    void submittingSelectedOrdersPassesTheChosenStrategyAndOptionsToTheSelectionPage() {
        // given
        FulfilmentForm form = new FulfilmentForm();
        when(manualOrderFulfilment.init("store-1", List.of("o-1", "o-2"), "suggest-exact", true, false, true)).thenReturn(form);
        when(fulfilmentSelectPageFactory.forOrders(any(), any(), anyBoolean(), any(), any(), any())).thenReturn(mock(FulfilmentSelectPage.class));
        Model model = new ConcurrentModel();

        // when
        String view = controller.initiateMultiOrderManualFulfilment(List.of("o-1", "o-2"), "suggest-exact", true, false, true, false, null, null, model, PL);

        // then
        assertThat(view).isEqualTo("fulfilment");
        verify(manualOrderFulfilment).init("store-1", List.of("o-1", "o-2"), "suggest-exact", true, false, true);
        assertThat(model.getAttribute("form")).isSameAs(form);
        assertThat(form.getPathSelector()).isEqualTo("suggest-exact");
        assertThat(form.getSelectedOrders()).containsExactly("o-1", "o-2");
        assertThat(model.getAttribute("page")).isNotNull();
    }

    @Test
    void superAdminSubmittingSelectedOrdersUsesTheStoreFromThePath() {
        // given
        FulfilmentForm form = new FulfilmentForm();
        when(manualOrderFulfilment.init("store-9", List.of("o-1"), "suggest", false, true, false)).thenReturn(form);

        // when
        String view = controller.initiateMultiOrderManualFulfilmentForSuperAdmin("store-9", List.of("o-1"), "suggest", false, true, false, false, null, null, new ConcurrentModel(), PL);

        // then
        assertThat(view).isEqualTo("fulfilment");
        verify(manualOrderFulfilment).init("store-9", List.of("o-1"), "suggest", false, true, false);
    }

    private static FulfilmentForm postedForm(String provider) {
        FulfilmentSource source = new FulfilmentSource();
        source.setProvider(provider);
        FulfilmentAllocation first = new FulfilmentAllocation();
        first.setOrderId("o-1");
        first.setOrderItemId("i-1");
        FulfilmentAllocation second = new FulfilmentAllocation();
        second.setOrderId("o-1");
        second.setOrderItemId("i-2");
        FulfilmentForm form = new FulfilmentForm();
        form.setEntries(new java.util.ArrayList<>(List.of(new FulfilmentGroup(source, List.of(first, second), true))));
        form.setSelectedOrders(List.of("o-1"));
        form.setSkippedOrderIds(List.of("s1"));
        form.setSkippedGroups("1");
        return form;
    }

    @Test
    void anEmptySelectionGoesBackToTheQueueWithItsSkipState() {
        // when
        String view = controller.initiateMultiOrderManualFulfilment(List.of(), "default", false, false, false, false,
                List.of("s1", "s2"), "2", new ConcurrentModel(), PL);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/fulfilment/queue?orderIds=s1&orderIds=s2&skippedGroups=2");
    }

    @Test
    void theSkipStateFromTheQueueTravelsOnTheForm() {
        // given
        FulfilmentForm form = new FulfilmentForm();
        when(manualOrderFulfilment.init("store-1", List.of("o-1"), "default", false, false, false)).thenReturn(form);

        // when
        controller.initiateMultiOrderManualFulfilment(List.of("o-1"), "default", false, false, false, false,
                List.of("s1"), "1", new ConcurrentModel(), PL);

        // then
        assertThat(form.getSkippedOrderIds()).containsExactly("s1");
        assertThat(form.getSkippedGroups()).isEqualTo("1");
        assertThat(form.getOrderCountAtStart()).isEqualTo(1);
    }

    @Test
    void savingReturnsToTheQueueWithTheSkipStateAndANoticeLinkingPendingDeliveries() {
        // given
        FulfilmentForm form = postedForm("Elko-k1");
        form.setRedirectUrl("redirect:https://evil.example");
        when(messageSource.getMessage(eq("fulfilment.select.saved"), any(), eq(PL))).thenReturn("Zapisano dobór 2/0");
        when(messageSource.getMessage(eq("fulfilment.select.saved.link"), any(), eq(PL))).thenReturn("Oczekujące dostawy ›");
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = controller.commitFulfilmentForm(form, new ConcurrentModel(), PL, redirect);

        // then
        verify(manualOrderFulfilment).commit("store-1", form);
        assertThat(view).isEqualTo("redirect:/dashboard/fulfilment/queue?orderIds=s1&skippedGroups=1");
        OrderNotice notice = (OrderNotice) redirect.getFlashAttributes().get("orderNotice");
        assertThat(notice.text()).isEqualTo("Zapisano dobór 2/0");
        assertThat(notice.linkHref()).isEqualTo("/dashboard/deliveries/preview");
        verify(messageSource).getMessage(eq("fulfilment.select.saved"), eq(new Object[]{2L, 0L}), eq(PL));
    }

    @Test
    void theSuperAdminsNoticeLinksTheStoresPendingDeliveries() {
        // given
        security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(true);
        when(messageSource.getMessage(any(String.class), any(), eq(PL))).thenReturn("x");
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = controller.commitFulfilmentFormForSuperAdmin("store-9", postedForm("Warehouse"), new ConcurrentModel(), PL, redirect);

        // then
        assertThat(view).startsWith("redirect:/dashboard/fulfilment/queue");
        assertThat(((OrderNotice) redirect.getFlashAttributes().get("orderNotice")).linkHref()).isEqualTo("/dashboard/store/store-9/deliveries/preview");
    }

    @Test
    void skippingTheLastOrderReturnsToTheQueueWithoutANotice() {
        // given
        FulfilmentForm form = postedForm("Elko-k1");
        form.setOrderByOrder(true);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = controller.skipFulfilmentOrder(form, new ConcurrentModel(), PL, redirect);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/fulfilment/queue?orderIds=s1&skippedGroups=1");
        assertThat(redirect.getFlashAttributes()).isEmpty();
        verify(manualOrderFulfilment, org.mockito.Mockito.never()).commit(any(), any());
    }

    @Test
    void savingOneOfSeveralOrdersShowsTheNextOneAndAddsItsValueToTheEarlierSteps() {
        // given
        FulfilmentForm form = postedForm("Elko-k1");
        form.getEntries().get(0).getSource().setPriceNet(10);
        form.getEntries().get(0).getAllocations().forEach(a -> a.setOrderItemQty(1));
        form.setOrderByOrder(true);
        form.setSelectedOrders(List.of("o-1", "o-2"));
        form.setOrderCountAtStart(2);
        FulfilmentForm next = new FulfilmentForm();
        when(orderItemsRepository.findByOrderIdAndStatus(any(), eq(FulfilmentStatus.New))).thenReturn(List.of());
        when(manualOrderFulfilment.init(eq("store-1"), eq(List.of("o-2")), any(), anyBoolean(), anyBoolean(), anyBoolean())).thenReturn(next);
        Model model = new ConcurrentModel();

        // when
        String view = controller.commitFulfilmentForm(form, model, PL, new RedirectAttributesModelMap());

        // then
        assertThat(view).isEqualTo("fulfilment");
        assertThat(next.getCommittedSuppliers()).containsEntry("Elko-k1", 20.0);
        assertThat(next.getSkippedOrderIds()).containsExactly("s1");
        assertThat(next.getOrderCountAtStart()).isEqualTo(2);
    }
}
