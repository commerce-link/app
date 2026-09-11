package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.inventory.supplier.ErrorMessage;
import pl.commercelink.inventory.supplier.StoreSupplierConnectionService;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.SupplierConnectionForm;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreFulfilmentSupplierControllerTest {

    private static final String STORE_ID = "store-1";

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private StoreSupplierConnectionService storeSupplierConnectionService;
    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private StoreFulfilmentSupplierController controller;

    private Store store() {
        Store store = new Store();
        store.setStoreId(STORE_ID);
        return store;
    }

    private SupplierConnectionForm form() {
        SupplierConnectionForm form = new SupplierConnectionForm();
        form.setSupplierName("Elko");
        form.setMode(ConnectionMode.OWN);
        form.setIncludeInPricing(true);
        form.setIncludeInFulfilment(true);
        form.setConfiguration(Map.of("login", "u"));
        return form;
    }

    @Test
    void savingASupplierDelegatesToTheServiceAndRedirectsToTheStoreScreen() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(store());
        when(storeSupplierConnectionService.connectOrUpdate(any(), any(), anyMap()))
                .thenReturn(new StoreSupplierConnectionService.ConnectionUpdateResult(List.of(), Set.of("Elko"), Set.of()));
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("ok");
        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

        try (MockedStatic<CustomSecurityContext> context = mockStatic(CustomSecurityContext.class)) {
            context.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            context.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(false);

            // when
            String view = controller.save(form(), Locale.ENGLISH, attributes);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/store/fulfilment");
            verify(storeSupplierConnectionService).connectOrUpdate(any(), any(), eq(Map.of("login", "u")));
        }
    }

    @Test
    void aFailedValidationRedirectsBackWithTheEditParameterSoTheModalReopens() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(store());
        when(storeSupplierConnectionService.connectOrUpdate(any(), any(), anyMap()))
                .thenReturn(new StoreSupplierConnectionService.ConnectionUpdateResult(
                        List.of(ErrorMessage.of("store.supplier.connection.error.requires.field", "Elko", "Login")),
                        Set.of(), Set.of()));
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("missing field");
        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

        try (MockedStatic<CustomSecurityContext> context = mockStatic(CustomSecurityContext.class)) {
            context.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            context.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(false);

            // when
            String view = controller.save(form(), Locale.ENGLISH, attributes);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/store/fulfilment?edit=Elko");
            assertThat(attributes.getFlashAttributes()).containsKey("errorMessage");
            assertThat(attributes.getFlashAttributes()).containsKey("submittedSupplierConfiguration");
        }
    }

    @Test
    void theSuperAdminVariantRedirectsToTheStoreScopedPath() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(store());
        when(storeSupplierConnectionService.disconnect(any(), eq("Elko")))
                .thenReturn(new StoreSupplierConnectionService.ConnectionUpdateResult(List.of(), Set.of(), Set.of("Elko")));
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("ok");
        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

        try (MockedStatic<CustomSecurityContext> context = mockStatic(CustomSecurityContext.class)) {
            context.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(true);

            // when
            String view = controller.disconnectForStore(STORE_ID, "Elko", Locale.ENGLISH, attributes);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/store/store-1/fulfilment");
            verify(storeSupplierConnectionService).disconnect(any(), eq("Elko"));
        }
    }

    @Test
    void aMissingStoreRedirectsBackWithAnError() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(null);
        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

        try (MockedStatic<CustomSecurityContext> context = mockStatic(CustomSecurityContext.class)) {
            context.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            context.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(false);

            // when
            String view = controller.save(form(), Locale.ENGLISH, attributes);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/store/fulfilment");
            assertThat(attributes.getFlashAttributes()).containsKey("errorMessage");
        }
    }
}
