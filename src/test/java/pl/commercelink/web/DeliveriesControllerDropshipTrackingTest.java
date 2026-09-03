package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.inventory.deliveries.DropshipTrackingService;
import pl.commercelink.inventory.deliveries.ManualTrackingOutcome;
import pl.commercelink.starter.security.CustomSecurityContext;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeliveriesControllerDropshipTrackingTest {

    private static final String STORE_ID = "store-1";

    @Mock
    private DropshipTrackingService dropshipTrackingService;
    @Mock
    private MessageSource messageSource;
    @Mock
    private RedirectAttributes redirectAttributes;
    @InjectMocks
    private DeliveriesController controller;

    private MockedStatic<CustomSecurityContext> security;

    @BeforeEach
    void stubSecurity() {
        security = mockStatic(CustomSecurityContext.class);
        security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
        security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(false);
        when(messageSource.getMessage(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void closeSecurity() {
        security.close();
    }

    @Test
    void storeAdminRouteUsesSessionStoreAndFlashesSuccessWhenConfirmed() {
        // given
        when(dropshipTrackingService.checkManually(STORE_ID, "d1")).thenReturn(ManualTrackingOutcome.CONFIRMED);

        // when
        String view = controller.checkTracking("d1", redirectAttributes, Locale.forLanguageTag("pl"));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/deliveries/details?deliveryId=d1");
        verify(redirectAttributes).addFlashAttribute("successMessage", "deliveries.dropship.tracking.result.confirmed");
    }

    @Test
    void superAdminRouteUsesPathStoreAndRedirectsToStoreDetails() {
        // given
        security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(true);
        when(dropshipTrackingService.checkManually("other-store", "d1")).thenReturn(ManualTrackingOutcome.STILL_PROCESSING);

        // when
        String view = controller.checkTrackingForSuperAdmin("other-store", "d1", redirectAttributes, Locale.forLanguageTag("pl"));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/store/other-store/deliveries/details?deliveryId=d1");
        verify(redirectAttributes).addFlashAttribute("errorMessage", "deliveries.dropship.tracking.result.stillProcessing");
    }

    @Test
    void eachOutcomeMapsToItsMessage() {
        // given
        when(dropshipTrackingService.checkManually(STORE_ID, "d1"))
                .thenReturn(ManualTrackingOutcome.CANCELLED)
                .thenReturn(ManualTrackingOutcome.NO_DATA)
                .thenReturn(ManualTrackingOutcome.UNAVAILABLE);

        // when
        controller.checkTracking("d1", redirectAttributes, Locale.forLanguageTag("pl"));
        controller.checkTracking("d1", redirectAttributes, Locale.forLanguageTag("pl"));
        controller.checkTracking("d1", redirectAttributes, Locale.forLanguageTag("pl"));

        // then
        verify(redirectAttributes).addFlashAttribute("errorMessage", "deliveries.dropship.tracking.result.cancelled");
        verify(redirectAttributes).addFlashAttribute("errorMessage", "deliveries.dropship.tracking.result.noData");
        verify(redirectAttributes).addFlashAttribute("errorMessage", "deliveries.dropship.tracking.result.unavailable");
    }
}
