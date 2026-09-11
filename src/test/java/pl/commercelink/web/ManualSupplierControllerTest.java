package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.inventory.supplier.manual.ManualSupplierService;
import pl.commercelink.starter.security.CustomSecurityContext;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManualSupplierControllerTest {

    private static final String STORE_ID = "store-1";
    private static final String IDENTITY = "manual:Hurtownia X";

    @Mock
    private ManualSupplierService manualSupplierService;
    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private ManualSupplierController controller;

    @Test
    void savingOneManualSupplierPassesASingleSelectionAndRedirects() {
        // given
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("ok");
        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

        try (MockedStatic<CustomSecurityContext> context = mockStatic(CustomSecurityContext.class)) {
            context.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            context.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(false);

            // when
            String view = controller.saveSelection(IDENTITY, true, true, false, Locale.ENGLISH, attributes);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/store/fulfilment");
            verify(manualSupplierService).applySelections(eq(STORE_ID),
                    eq(List.of(new ManualSupplierService.ManualSelection(IDENTITY, true, true, false))));
        }
    }

    @Test
    void theSuperAdminVariantRedirectsToTheStoreScopedPath() {
        // given
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("ok");
        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

        try (MockedStatic<CustomSecurityContext> context = mockStatic(CustomSecurityContext.class)) {
            context.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(true);

            // when
            String view = controller.saveSelectionForStore(STORE_ID, IDENTITY, false, true, true,
                    Locale.ENGLISH, attributes);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/store/store-1/fulfilment");
            verify(manualSupplierService).applySelections(eq(STORE_ID),
                    eq(List.of(new ManualSupplierService.ManualSelection(IDENTITY, false, true, true))));
        }
    }
}
