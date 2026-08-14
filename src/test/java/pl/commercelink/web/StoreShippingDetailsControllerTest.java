package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreForm;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.stores.Printer;
import pl.commercelink.stores.WarehouseConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreShippingDetailsControllerTest {

    private static final String STORE_ID = "store-1";

    @Mock
    private StoresRepository storesRepository;

    @Mock
    private MessageSource messageSource;

    @Mock
    private RedirectAttributes redirectAttributes;

    @InjectMocks
    private StoreController storeController;

    private ShippingDetails address(String companyName, String street) {
        ShippingDetails details = new ShippingDetails();
        details.setCompanyName(companyName);
        details.setStreetAndNumber(street);
        details.setPostalCode("31-140");
        details.setCity("Kraków");
        details.setCountry("PL");
        details.setEmail("sklep@example.com");
        details.setPhone("500600700");
        return details;
    }

    private StoreForm formWith(List<ShippingDetails> shippingDetails, int defaultIndex) {
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setShippingDetails(new ArrayList<>(shippingDetails));
        store.setWarehouseConfiguration(new WarehouseConfiguration());
        StoreForm form = new StoreForm(store);
        form.setDefaultShippingDetailIndex(defaultIndex);
        return form;
    }

    private Store existingStore() {
        Store store = new Store();
        store.setStoreId(STORE_ID);
        WarehouseConfiguration configuration = new WarehouseConfiguration();
        configuration.addPrinter(new Printer());
        store.setWarehouseConfiguration(configuration);
        return store;
    }

    @Test
    void warehouseSavePersistsShippingDetailsWithoutWipingPrinters() {
        // given
        Store existing = existingStore();
        when(storesRepository.findById(STORE_ID)).thenReturn(existing);
        StoreForm form = formWith(List.of(address("Sklep", "ul. Testowa 1")), 0);

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(false);

            // when
            storeController.updateStoreWarehouse(form, Locale.ENGLISH, redirectAttributes);

            // then
            ArgumentCaptor<Store> saved = ArgumentCaptor.forClass(Store.class);
            verify(storesRepository).save(saved.capture());
            assertThat(saved.getValue().getShippingDetails()).hasSize(1);
            assertThat(saved.getValue().getWarehouseConfiguration().getPrinters()).hasSize(1);
        }
    }

    @Test
    void defaultFlagSurvivesDroppingAnIncompleteRow() {
        // given
        Store existing = existingStore();
        when(storesRepository.findById(STORE_ID)).thenReturn(existing);
        ShippingDetails blank = new ShippingDetails();
        StoreForm form = formWith(List.of(blank, address("Sklep", "ul. Testowa 1")), 0);

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(false);

            // when
            storeController.updateStoreWarehouse(form, Locale.ENGLISH, redirectAttributes);

            // then
            ArgumentCaptor<Store> saved = ArgumentCaptor.forClass(Store.class);
            verify(storesRepository).save(saved.capture());
            assertThat(saved.getValue().getShippingDetails()).hasSize(1);
            assertThat(saved.getValue().getDefaultShippingDetails().getCompanyName()).isEqualTo("Sklep");
        }
    }

    @Test
    void defaultFlagFollowsTheChosenIndexAmongSeveralValidAddresses() {
        // given
        Store existing = existingStore();
        when(storesRepository.findById(STORE_ID)).thenReturn(existing);
        StoreForm form = formWith(
                List.of(address("Sklep A", "ul. Pierwsza 1"), address("Sklep B", "ul. Druga 2")), 1);

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(false);

            // when
            storeController.updateStoreWarehouse(form, Locale.ENGLISH, redirectAttributes);

            // then
            ArgumentCaptor<Store> saved = ArgumentCaptor.forClass(Store.class);
            verify(storesRepository).save(saved.capture());
            assertThat(saved.getValue().getDefaultShippingDetails().getStreetAndNumber()).isEqualTo("ul. Druga 2");
            assertThat(saved.getValue().getShippingDetails().get(0).is_default()).isFalse();
        }
    }

    @Test
    void billingSaveDoesNotClearShippingDetails() {
        // given
        Store existing = existingStore();
        existing.setShippingDetails(new ArrayList<>(List.of(address("Sklep", "ul. Testowa 1"))));
        when(storesRepository.findById(STORE_ID)).thenReturn(existing);
        Store submitted = new Store();
        submitted.setStoreId(STORE_ID);
        submitted.setBillingDetails(new BillingDetails());
        StoreForm form = new StoreForm(submitted);

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(false);

            // when
            storeController.updateBillingShippingConfiguration(form, Locale.ENGLISH, redirectAttributes);

            // then
            ArgumentCaptor<Store> saved = ArgumentCaptor.forClass(Store.class);
            verify(storesRepository).save(saved.capture());
            assertThat(saved.getValue().getShippingDetails()).hasSize(1);
        }
    }
}
