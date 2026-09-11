package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.inventory.supplier.StoreSupplierConnectionService;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreForm;
import pl.commercelink.stores.StoresRepository;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreControllerTest {

    private static final String STORE_ID = "store-1";

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private StoreSupplierConnectionService storeSupplierConnectionService;
    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private StoreController controller;

    private Store store() {
        Store store = new Store();
        store.setStoreId(STORE_ID);
        return store;
    }

    private StoreForm form() {
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setFulfilmentConfiguration(new FulfilmentConfiguration());
        return new StoreForm(store);
    }

    @Test
    void savingFulfilmentSettingsOnlyAppliesStoreSettingsAndNeverTouchesConnections() {
        // given: this endpoint used to also apply supplier and manual supplier selections; it
        // must now save store-level settings only and leave the connection list untouched --
        // connections have their own per-supplier endpoints
        when(storesRepository.findById(STORE_ID)).thenReturn(store());
        when(storeSupplierConnectionService.applyStoreSettings(any(), any(), anyBoolean()))
                .thenReturn(new StoreSupplierConnectionService.ConnectionUpdateResult(List.of(), Set.of(), Set.of()));
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("ok");
        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

        try (MockedStatic<CustomSecurityContext> context = mockStatic(CustomSecurityContext.class)) {
            context.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(false);

            // when
            String view = controller.updateStoreFulfilmentConfiguration(form(), Locale.ENGLISH, attributes);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/store/fulfilment");
            verify(storeSupplierConnectionService).applyStoreSettings(any(), any(), anyBoolean());
            verify(storeSupplierConnectionService, never()).connectOrUpdate(any(), any(), any());
        }
    }
}
