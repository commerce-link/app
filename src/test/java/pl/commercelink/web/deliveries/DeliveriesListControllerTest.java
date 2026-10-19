package pl.commercelink.web.deliveries;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.util.LinkedMultiValueMap;
import pl.commercelink.starter.security.CustomSecurityContext;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveriesListControllerTest {

    @Mock DeliveryListService service;
    @InjectMocks DeliveriesListController controller;

    @Test
    void storeUserGetsHisOwnStoreAndTheWholePage() {
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            // given
            security.when(CustomSecurityContext::getStoreId).thenReturn("store-1");
            security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(false);
            security.when(() -> CustomSecurityContext.hasRole("ADMIN")).thenReturn(true);
            ExtendedModelMap model = new ExtendedModelMap();

            // when
            String view = controller.deliveries(new LinkedMultiValueMap<>(), new Locale("pl"), model);

            // then
            assertThat(view).isEqualTo("deliveries");
            verify(service).page(eq(new DeliveryListService.ListActor("store-1", false, true)), any(), any(), any());
        }
    }

    @Test
    void fragmentEndpointRendersOnlyTheResults() {
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn("store-1");
            assertThat(controller.deliveriesList(new LinkedMultiValueMap<>(), new Locale("pl"), new ExtendedModelMap()))
                    .isEqualTo("deliveries :: results");
        }
    }

    @Test
    void oldFilterFormRedirects() {
        // given
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("showArchived", "true");

        // when / then
        assertThat(controller.deliveries(params, new Locale("pl"), new ExtendedModelMap()))
                .isEqualTo("redirect:/dashboard/deliveries?scope=all&period=all");
        verifyNoInteractions(service);
    }

    @Test
    void superAdminIsNotTiedToAStore() {
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(null);
            security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(true);
            controller.deliveries(new LinkedMultiValueMap<>(), new Locale("pl"), new ExtendedModelMap());
            verify(service).page(eq(new DeliveryListService.ListActor(null, true, false)), any(), any(), any());
        }
    }
}
