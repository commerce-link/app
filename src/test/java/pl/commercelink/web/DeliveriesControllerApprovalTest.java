package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.inventory.deliveries.SupplierPurchaseService;
import pl.commercelink.starter.util.OperationResult;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveriesControllerApprovalTest {

    private static final String STORE_ID = "store-1";
    private static final String DELIVERY_ID = "delivery-1";

    @Mock
    private SupplierPurchaseService supplierPurchaseService;

    @Mock
    private MessageSource messageSource;

    @Mock
    private RedirectAttributes redirectAttributes;

    @InjectMocks
    private DeliveriesController deliveriesController;

    @Test
    void approvingRedirectsBackToTheStoreScopedDeliveryDetails() {
        // given
        when(supplierPurchaseService.approve(STORE_ID, DELIVERY_ID, "17200617"))
                .thenReturn(OperationResult.success(DELIVERY_ID));

        // when
        String view = deliveriesController.approvePurchase(STORE_ID, DELIVERY_ID, "17200617",
                redirectAttributes, Locale.forLanguageTag("pl"));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/store/store-1/deliveries/details?deliveryId=delivery-1");
        verify(supplierPurchaseService).approve(eq(STORE_ID), eq(DELIVERY_ID), eq("17200617"));
        verify(redirectAttributes, never()).addFlashAttribute(eq("errorMessage"), any());
    }

    @Test
    void approvalFailureAddsFlashErrorMessageAndStillRedirectsToDetails() {
        // given
        when(supplierPurchaseService.approve(STORE_ID, DELIVERY_ID, null))
                .thenReturn(OperationResult.failure("deliveries.approval.error.state"));
        when(messageSource.getMessage(eq("deliveries.approval.error.state"), eq(null), eq(Locale.ENGLISH)))
                .thenReturn("Delivery is no longer awaiting approval");

        // when
        String view = deliveriesController.approvePurchase(STORE_ID, DELIVERY_ID, null,
                redirectAttributes, Locale.ENGLISH);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/store/store-1/deliveries/details?deliveryId=delivery-1");
        verify(redirectAttributes).addFlashAttribute("errorMessage", "Delivery is no longer awaiting approval");
    }

    @Test
    void rejectingRedirectsBackToTheStoreScopedDeliveryDetails() {
        // given
        when(supplierPurchaseService.reject(STORE_ID, DELIVERY_ID, "out of stock"))
                .thenReturn(OperationResult.success(DELIVERY_ID));

        // when
        String view = deliveriesController.rejectPurchase(STORE_ID, DELIVERY_ID, "out of stock",
                redirectAttributes, Locale.forLanguageTag("pl"));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/store/store-1/deliveries/details?deliveryId=delivery-1");
        verify(supplierPurchaseService).reject(eq(STORE_ID), eq(DELIVERY_ID), eq("out of stock"));
        verify(redirectAttributes, never()).addFlashAttribute(eq("errorMessage"), any());
    }

    @Test
    void rejectionFailureAddsFlashErrorMessageAndStillRedirectsToDetails() {
        // given
        when(supplierPurchaseService.reject(STORE_ID, DELIVERY_ID, "reason"))
                .thenReturn(OperationResult.failure("deliveries.approval.error.state"));
        when(messageSource.getMessage(eq("deliveries.approval.error.state"), eq(null), eq(Locale.ENGLISH)))
                .thenReturn("Delivery is no longer awaiting approval");

        // when
        String view = deliveriesController.rejectPurchase(STORE_ID, DELIVERY_ID, "reason",
                redirectAttributes, Locale.ENGLISH);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/store/store-1/deliveries/details?deliveryId=delivery-1");
        verify(redirectAttributes).addFlashAttribute("errorMessage", "Delivery is no longer awaiting approval");
    }
}
