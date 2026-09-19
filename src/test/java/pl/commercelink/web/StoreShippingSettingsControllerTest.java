package pl.commercelink.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ui.ExtendedModelMap;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.stores.ShippingConfiguration;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.testsupport.OptimisticLockingExecutorMocks;
import pl.commercelink.web.settings.IntegrationStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreShippingSettingsControllerTest {

    private static final Locale POLISH = Locale.forLanguageTag("pl");

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private ShippingAccounts shippingAccounts;
    @Mock
    private OptimisticLockingExecutor optimisticLockingExecutor;

    @InjectMocks
    private StoreShippingSettingsController controller;

    @BeforeEach
    void retriesPassThrough() {
        when(optimisticLockingExecutor.modifyAndSave(any(), any(), any()))
                .thenAnswer(OptimisticLockingExecutorMocks.passThroughModifyAndSave());
        when(shippingAccounts.status(any())).thenReturn(IntegrationStatus.none());
    }

    private Store storeWithAddress(String id) {
        ShippingDetails address = new ShippingDetails();
        address.setId(id);
        address.setName("Magazyn");
        ShippingConfiguration configuration = new ShippingConfiguration();
        configuration.setPickUpAddresses(new ArrayList<>(List.of(address)));
        Store store = new Store();
        store.setStoreId("store-1");
        store.setShippingConfiguration(configuration);
        when(storesRepository.findById("store-1")).thenReturn(store);
        return store;
    }

    /** Another save of the store may land between reading and writing, so ids are assigned through the retrying executor. */
    @Test
    void anAddressWithoutAnIdGetsOneThroughTheRetryingSave() {
        // given
        Store store = storeWithAddress(null);

        // when
        controller.superAdminShipping("store-1", new ExtendedModelMap(), POLISH);

        // then
        verify(optimisticLockingExecutor).modifyAndSave(any(), any(), any());
        verify(storesRepository).save(store);
        assertThat(store.getShippingConfiguration().getPickUpAddresses().get(0).getId()).isNotBlank();
    }

    @Test
    void viewingAPageWhoseRecordsHaveIdsWritesNothing() {
        // given
        storeWithAddress("a-1");

        // when
        controller.superAdminShipping("store-1", new ExtendedModelMap(), POLISH);

        // then
        verify(storesRepository, never()).save(any(Store.class));
    }
}
