package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import pl.commercelink.payments.PaymentProviderFactory;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreControllerPaymentsTest {

    @Mock
    private StoresRepository storesRepository;

    @Mock
    private PaymentProviderFactory paymentProviderFactory;

    @InjectMocks
    private StoreController controller;

    @Test
    void rendersThePaymentsPageForAStoreThatHasNeverSavedCheckoutSettings() {
        // given
        Store store = new Store();
        store.setStoreId("store-1");
        when(storesRepository.findById("store-1")).thenReturn(store);
        Model model = new ExtendedModelMap();

        // when
        String view = controller.superAdminStorePayments("store-1", model);

        // then
        assertThat(view).isEqualTo("store-payments");
        assertThat(store.getCheckoutConfiguration()).isNotNull();
        assertThat(store.getCheckoutConfiguration().getDeliveryOptions()).hasSize(2);
    }
}
