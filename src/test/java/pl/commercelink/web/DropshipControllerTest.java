package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.inventory.deliveries.DropshipAssessment;
import pl.commercelink.inventory.deliveries.DropshipEligibility;
import pl.commercelink.inventory.deliveries.DropshipRejection;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.starter.security.CustomSecurityContext;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DropshipControllerTest {

    private static final String STORE_ID = "store-1";
    private static final String ORDER_ID = "order-1";
    private static final String PROVIDER = "Acme";

    @Mock
    private OrdersRepository ordersRepository;
    @Mock
    private OrderItemsRepository orderItemsRepository;
    @Mock
    private DropshipEligibility dropshipEligibility;
    @Mock
    private MessageSource messageSource;
    @Mock
    private RedirectAttributes redirectAttributes;

    @InjectMocks
    private DropshipController controller;

    private static Order order() {
        Order order = new Order();
        order.setStoreId(STORE_ID);
        order.setOrderId(ORDER_ID);
        return order;
    }

    private <T> T asStoreAdmin(Supplier<T> call) {
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(false);
            return call.get();
        }
    }

    private <T> T asSuperAdmin(Supplier<T> call) {
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(true);
            return call.get();
        }
    }

    @Test
    void oldDropshipAddressRedirectsToTheNewDeliveryPage() {
        // given
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order());

        // when
        String view = asStoreAdmin(() -> controller.dropshipCreate(ORDER_ID, PROVIDER, redirectAttributes, Locale.ENGLISH));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/deliveries/create/Acme?order=" + ORDER_ID + "&from=order");
    }

    @Test
    void oldAddressWithoutASupplierTakesTheOnlyOne() {
        // given
        Order order = order();
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());
        when(dropshipEligibility.assess(same(order), any())).thenReturn(DropshipAssessment.of(List.of(PROVIDER)));

        // when
        String view = asStoreAdmin(() -> controller.dropshipCreate(ORDER_ID, null, redirectAttributes, Locale.ENGLISH));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/deliveries/create/Acme?order=" + ORDER_ID + "&from=order");
    }

    @Test
    void oldAddressWithoutASupplierAsksOnThePreviewWhenTheOrderHasSeveral() {
        // given
        Order order = order();
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());
        when(dropshipEligibility.assess(same(order), any())).thenReturn(DropshipAssessment.of(List.of("Acme", "Elko")));
        when(messageSource.getMessage(eq("orders.dropship.chooseProvider"), eq(null), any())).thenReturn("pick one");

        // when
        String view = asStoreAdmin(() -> controller.dropshipCreate(ORDER_ID, null, redirectAttributes, Locale.ENGLISH));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/deliveries/preview");
        verify(redirectAttributes).addFlashAttribute("errorMessage", "pick one");
    }

    @Test
    void oldAddressOfAnOrderThatCannotBeDropshippedShowsTheReasonOnTheOrder() {
        // given
        Order order = order();
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());
        when(dropshipEligibility.assess(same(order), any()))
                .thenReturn(DropshipAssessment.rejected(DropshipRejection.NO_SHIPPING_DETAILS));
        when(messageSource.getMessage(eq("orders.dropship.rejected.noShippingDetails"), eq(null), any())).thenReturn("no address");

        // when
        String view = asStoreAdmin(() -> controller.dropshipCreate(ORDER_ID, null, redirectAttributes, Locale.ENGLISH));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
        verify(redirectAttributes).addFlashAttribute("errorMessage", "no address");
    }

    @Test
    void superAdminIsRedirectedWithinTheStore() {
        // given
        when(ordersRepository.findById("store-9", ORDER_ID)).thenReturn(order());

        // when
        String view = asSuperAdmin(() -> controller.dropshipCreateForSuperAdmin("store-9", ORDER_ID, PROVIDER,
                redirectAttributes, Locale.ENGLISH));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/store/store-9/deliveries/create/Acme?order=" + ORDER_ID + "&from=order");
    }
}
