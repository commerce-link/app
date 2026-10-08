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
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderIndexEntry;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.fulfilment.FulfilmentQueue;
import pl.commercelink.orders.fulfilment.ManualOrderFulfilment;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.web.fulfilment.FulfilmentQueuePage;
import pl.commercelink.web.fulfilment.FulfilmentQueuePageFactory;

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
        when(fulfilmentQueuePageFactory.build(eq(false), eq(List.of("skipped-1")), anyList(), anyMap(), anyMap(), any(), eq(PL)))
                .thenReturn(page);
        Model model = new ConcurrentModel();

        // when
        String view = controller.fulfilmentQueue(List.of("skipped-1"), model, PL);

        // then
        assertThat(view).isEqualTo("fulfilment-queue");
        assertThat(model.getAttribute("page")).isSameAs(page);
        ArgumentCaptor<Map<String, Order>> orders = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map<String, Integer>> items = ArgumentCaptor.forClass(Map.class);
        verify(fulfilmentQueuePageFactory).build(anyBoolean(), anyList(), anyList(), orders.capture(), items.capture(), any(), any());
        assertThat(orders.getValue()).containsEntry("o-1", order);
        assertThat(items.getValue()).containsEntry("o-1", 2);
    }

    @Test
    void submittingWithoutSelectedOrdersGoesBackToTheQueue() {
        // when
        String view = controller.initiateMultiOrderManualFulfilment(List.of(), "default", false, false, false, false, new ConcurrentModel());

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/fulfilment/queue");
        verifyNoInteractions(manualOrderFulfilment);
    }

    @Test
    void superAdminSubmittingWithoutSelectedOrdersGoesBackToTheQueue() {
        // when
        String view = controller.initiateMultiOrderManualFulfilmentForSuperAdmin("store-9", List.of(), "suggest", false, false, false, false, new ConcurrentModel());

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/fulfilment/queue");
        verifyNoInteractions(manualOrderFulfilment);
    }
}
