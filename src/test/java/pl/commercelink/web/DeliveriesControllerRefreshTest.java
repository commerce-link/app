package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.inventory.deliveries.OrderIdRefreshService;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.ConnectionMode;

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
    private static final String PROVIDER = "IncomGroup";

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

    @Test
    void refreshOrderIdConfirmedAddsFlashSuccessMessageAndRedirectsToDetails() {
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
            verify(redirectAttributes).addFlashAttribute("successMessage", "Fetched the final order number from the supplier");
            verify(redirectAttributes, never()).addFlashAttribute(eq("errorMessage"), any());
        }
    }

    @Test
    void refreshOrderIdStillPendingAddsFlashErrorMessage() {
        // given
        when(orderIdRefreshService.refreshManually(STORE_ID, DELIVERY_ID))
                .thenReturn(OrderIdRefreshService.ManualRefreshOutcome.STILL_PENDING);
        when(messageSource.getMessage(eq("deliveries.orderId.refresh.stillPending"), eq(null), eq(Locale.ENGLISH)))
                .thenReturn("The supplier has not confirmed the order number yet");

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(true);

            // when
            String view = deliveriesController.refreshOrderIdForSuperAdmin(STORE_ID, DELIVERY_ID, redirectAttributes, Locale.ENGLISH);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/store/store-1/deliveries/details?deliveryId=delivery-1");
            verify(redirectAttributes).addFlashAttribute("errorMessage", "The supplier has not confirmed the order number yet");
            verify(redirectAttributes, never()).addFlashAttribute(eq("successMessage"), any());
        }
    }

    @Test
    void refreshOrderIdUnavailableAddsFlashErrorMessage() {
        // given
        when(orderIdRefreshService.refreshManually(STORE_ID, DELIVERY_ID))
                .thenReturn(OrderIdRefreshService.ManualRefreshOutcome.UNAVAILABLE);
        when(messageSource.getMessage(eq("deliveries.orderId.refresh.unavailable"), eq(null), eq(Locale.ENGLISH)))
                .thenReturn("Order number refresh is not available for this delivery");

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(true);

            // when
            String view = deliveriesController.refreshOrderIdForSuperAdmin(STORE_ID, DELIVERY_ID, redirectAttributes, Locale.ENGLISH);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/store/store-1/deliveries/details?deliveryId=delivery-1");
            verify(redirectAttributes).addFlashAttribute("errorMessage", "Order number refresh is not available for this delivery");
            verify(redirectAttributes, never()).addFlashAttribute(eq("successMessage"), any());
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
            verify(redirectAttributes).addFlashAttribute("successMessage", "Fetched the final order number from the supplier");
        }
    }

    @Test
    void refreshOrderIdRefusesGlobalDeliveriesForStoreAdmin() {
        // given
        Delivery delivery = new Delivery(STORE_ID, null, PROVIDER);
        delivery.setDeliveryId(DELIVERY_ID);
        delivery.setConnectionMode(ConnectionMode.GLOBAL);
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(messageSource.getMessage(eq("deliveries.purchase.retry.error.global"), eq(null), eq(Locale.ENGLISH)))
                .thenReturn("A global delivery can only be retried by the platform administrator.");

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);

            // when
            String view = deliveriesController.refreshOrderId(DELIVERY_ID, redirectAttributes, Locale.ENGLISH);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/deliveries/details?deliveryId=delivery-1");
            verify(orderIdRefreshService, never()).refreshManually(any(), any());
            verify(redirectAttributes).addFlashAttribute("errorMessage", "A global delivery can only be retried by the platform administrator.");
        }
    }
}
