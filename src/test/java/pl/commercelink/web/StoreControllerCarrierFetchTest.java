package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ConcurrentModel;
import pl.commercelink.shipping.ShippingProviderFactory;
import pl.commercelink.shipping.api.ShippingProvider;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.settings.SettingsPage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreControllerCarrierFetchTest {

    private static final String STORE_ID = "store-1";

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private ShippingProviderFactory shippingProviderFactory;

    @InjectMocks
    private StoreController controller;

    @Test
    void carrierFetchKeepsTheSharedHeaderPointingAtTheShippingTile() {
        // given: this endpoint renders store-shipping under a non-tile URL, so the advice cannot
        // recognise it and the controller must set settingsPage explicitly
        Store store = new Store();
        store.setStoreId(STORE_ID);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        ShippingProvider provider = mock(ShippingProvider.class);
        when(shippingProviderFactory.get(store)).thenReturn(provider);
        when(provider.getAvailableCarriers()).thenReturn(List.of());
        ConcurrentModel model = new ConcurrentModel();

        try (MockedStatic<CustomSecurityContext> context = mockStatic(CustomSecurityContext.class)) {
            context.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(false);

            // when
            controller.fetchAvailableCarriers(STORE_ID, model);

            // then
            SettingsPage settingsPage = (SettingsPage) model.getAttribute("settingsPage");
            assertThat(settingsPage).isNotNull();
            assertThat(settingsPage.tile().key()).isEqualTo("shipping");
            assertThat(settingsPage.homeHref()).isEqualTo("/dashboard/store");
        }
    }
}
