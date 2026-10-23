package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.inventory.deliveries.OrderIdRefreshService;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.web.orders.OrderFlash;
import pl.commercelink.web.orders.OrderLabels;
import pl.commercelink.web.orders.OrderNotice;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveriesControllerRefreshTest {

    private static final String STORE_ID = "store-1";
    private static final String DELIVERY_ID = "delivery-1";

    @Mock
    private OrderIdRefreshService orderIdRefreshService;

    @Mock
    private DeliveriesRepository deliveriesRepository;

    @Mock
    private MessageSource messageSource;

    @Mock
    private RedirectAttributes redirectAttributes;

    @InjectMocks
    private DeliveriesController deliveriesController;

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "STILL_PENDING, deliveries.orderId.refresh.stillPending, The supplier has not confirmed the order number yet",
            "UNAVAILABLE,   deliveries.orderId.refresh.unavailable,  Order number refresh is not available for this delivery"})
    void refreshOrderIdFlashesTheRefusalAndRedirectsToDetails(OrderIdRefreshService.ManualRefreshOutcome outcome,
                                                              String messageKey, String message) {
        // given
        when(orderIdRefreshService.refreshManually(STORE_ID, DELIVERY_ID)).thenReturn(outcome);
        when(messageSource.getMessage(eq(messageKey), eq(null), eq(Locale.ENGLISH))).thenReturn(message);

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(true);

            // when
            String view = deliveriesController.refreshOrderIdForSuperAdmin(STORE_ID, DELIVERY_ID, redirectAttributes, Locale.ENGLISH);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/store/store-1/deliveries/details?deliveryId=delivery-1");
            verify(redirectAttributes).addFlashAttribute("errorMessage", message);
            verify(redirectAttributes, never()).addFlashAttribute(eq(OrderFlash.ATTRIBUTE), any());
        }
    }

    @Test
    void refreshOrderIdConfirmedShowsTheNoticeOnTheDetailsPage() {
        // given
        when(orderIdRefreshService.refreshManually(STORE_ID, DELIVERY_ID))
                .thenReturn(OrderIdRefreshService.ManualRefreshOutcome.CONFIRMED);
        when(messageSource.getMessage(eq("deliveries.orderId.refresh.confirmed"), eq(null), eq(Locale.ENGLISH)))
                .thenReturn("Fetched the final order number from the supplier");

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(true);

            // when
            String view = deliveriesController.refreshOrderIdForSuperAdmin(STORE_ID, DELIVERY_ID, redirectAttributes, Locale.ENGLISH);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/store/store-1/deliveries/details?deliveryId=delivery-1");
            verify(redirectAttributes).addFlashAttribute(OrderFlash.ATTRIBUTE,
                    new OrderNotice(OrderLabels.OK, "Fetched the final order number from the supplier", null, null));
            verify(redirectAttributes, never()).addFlashAttribute(eq("errorMessage"), any());
        }
    }

    @Test
    void refreshOrderIdForAdminRedirectsToTheNonStoreScopedDeliveryDetails() {
        // given
        when(orderIdRefreshService.refreshManually(STORE_ID, DELIVERY_ID))
                .thenReturn(OrderIdRefreshService.ManualRefreshOutcome.CONFIRMED);
        when(messageSource.getMessage(eq("deliveries.orderId.refresh.confirmed"), eq(null), eq(Locale.ENGLISH)))
                .thenReturn("Fetched the final order number from the supplier");

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(false);

            // when
            String view = deliveriesController.refreshOrderId(DELIVERY_ID, redirectAttributes, Locale.ENGLISH);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/deliveries/details?deliveryId=delivery-1");
            verify(redirectAttributes).addFlashAttribute(OrderFlash.ATTRIBUTE,
                    new OrderNotice(OrderLabels.OK, "Fetched the final order number from the supplier", null, null));
        }
    }
}
