package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.carriers.Carrier;
import pl.commercelink.carriers.CarrierDictionary;
import pl.commercelink.carriers.CarrierDictionaryWriter;
import pl.commercelink.carriers.CarrierMapping;
import pl.commercelink.carriers.CarriersRepository;
import pl.commercelink.carriers.InMemoryCarrierDictionaries;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.stores.StoresRepository;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static pl.commercelink.testsupport.SecurityContextLogin.logInAs;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreSupplierCarriersControllerTest {

    private static final Locale POLISH = Locale.forLanguageTag("pl");
    private static final String SHOPER_A = "Shoper-a1";
    private static final String SHOPER_B = "Shoper-b2";

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private SupplierConnections suppliers;
    @Mock
    private CarriersRepository carriersRepository;
    @Mock
    private MessageSource messageSource;

    private final InMemoryCarrierDictionaries dictionaries = new InMemoryCarrierDictionaries();
    private StoreSupplierCarriersController controller;

    @BeforeEach
    void setUp() {
        controller = new StoreSupplierCarriersController(storesRepository, suppliers, carriersRepository, dictionaries,
                new CarrierDictionaryWriter(dictionaries), messageSource);
        when(messageSource.getMessage(anyString(), any(), eq(POLISH))).thenAnswer(invocation -> invocation.getArgument(0));
        when(carriersRepository.findAll()).thenReturn(List.of(new Carrier("InPost", null), new Carrier("DPD", null)));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void storeWith(String storeId, StoreSupplierConnection... connections) {
        Store store = new Store();
        store.setStoreId(storeId);
        when(storesRepository.findById(storeId)).thenReturn(store);
        for (StoreSupplierConnection connection : connections) {
            when(suppliers.connection(store, connection.getSupplierName())).thenReturn(connection);
        }
    }

    @Test
    void eachShoperConnectionOfAStoreHasItsOwnDictionary() {
        // given
        logInAs("ADMIN", "store-1");
        storeWith("store-1", new StoreSupplierConnection(SHOPER_A, ConnectionMode.OWN),
                new StoreSupplierConnection(SHOPER_B, ConnectionMode.OWN));

        // when
        String view = controller.save(SHOPER_A, null, List.of("Kurier DPD"), List.of("DPD"), POLISH,
                new ExtendedModelMap(), new RedirectAttributesModelMap());
        controller.save(SHOPER_B, null, List.of("Paczkomat"), List.of("InPost"), POLISH,
                new ExtendedModelMap(), new RedirectAttributesModelMap());

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/store/suppliers/Shoper-a1/carriers");
        assertThat(dictionaries.translator().carrierFor(
                CarrierDictionary.supplierDeliveryMethods("store-1", SHOPER_A), "Kurier DPD")).isEqualTo("DPD");
        assertThat(dictionaries.get(CarrierDictionary.supplierDeliveryMethods("store-1", SHOPER_A)).getMappings())
                .extracting(CarrierMapping::getExternalText).containsExactly("Kurier DPD");
        assertThat(dictionaries.get(CarrierDictionary.supplierDeliveryMethods("store-1", SHOPER_B)).getMappings())
                .extracting(CarrierMapping::getExternalText).containsExactly("Paczkomat");
    }

    @Test
    void aCarrierThatIsNotOursIsRefused() {
        // given
        logInAs("ADMIN", "store-1");
        storeWith("store-1", new StoreSupplierConnection(SHOPER_A, ConnectionMode.OWN));
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.save(SHOPER_A, null, List.of("Kurier"), List.of("Mój Kurier"), POLISH, model,
                new RedirectAttributesModelMap());

        // then
        assertThat(view).isEqualTo(CarrierDictionaryForm.VIEW);
        assertThat(model.get("settingsErrorMessage")).isEqualTo("store.supplier.carriers.unknown");
        assertThat(dictionaries.find(CarrierDictionary.supplierDeliveryMethods("store-1", SHOPER_A))).isEmpty();
    }

    @Test
    void aMethodNamingTwoCarriersIsRefused() {
        // given
        logInAs("ADMIN", "store-1");
        storeWith("store-1", new StoreSupplierConnection(SHOPER_A, ConnectionMode.OWN));
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        controller.save(SHOPER_A, null, List.of("Kurier", "Kurier"), List.of("DPD", "InPost"), POLISH, model,
                new RedirectAttributesModelMap());

        // then
        assertThat(model.get("settingsErrorMessage")).isEqualTo("carriers.dictionary.refused.AMBIGUOUS_SUPPLIER_TEXT");
        assertThat(dictionaries.find(CarrierDictionary.supplierDeliveryMethods("store-1", SHOPER_A))).isEmpty();
    }

    @Test
    void aPriceListUploadedByHandHasNoCarriersPage() {
        // given
        logInAs("ADMIN", "store-1");
        storeWith("store-1", new StoreSupplierConnection("manual:Cennik", ConnectionMode.MANUAL));

        // when / then
        assertThatThrownBy(() -> controller.carriers("manual:Cennik", new ExtendedModelMap(), POLISH))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void anAdminEditsOnlyTheConnectionsOfTheStoreInItsSession() {
        // given
        logInAs("ADMIN", "store-1");
        storeWith("store-1");
        storeWith("store-2", new StoreSupplierConnection(SHOPER_A, ConnectionMode.OWN));

        // when / then
        assertThatThrownBy(() -> controller.carriers(SHOPER_A, new ExtendedModelMap(), POLISH))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void superAdminEditsTheConnectionOfTheStoreInThePath() {
        // given
        logInAs("SUPER_ADMIN", "own-store");
        storeWith("store-7", new StoreSupplierConnection(SHOPER_A, ConnectionMode.OWN));
        dictionaries.with(CarrierDictionary.supplierDeliveryMethods("store-7", SHOPER_A), "Paczkomat=InPost");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.superAdminCarriers("store-7", SHOPER_A, model, POLISH);

        // then
        assertThat(view).isEqualTo(CarrierDictionaryForm.VIEW);
        assertThat(model.get("formAction")).isEqualTo("/dashboard/store/store-7/suppliers/Shoper-a1/carriers");
        assertThat(model.get("backHref")).isEqualTo("/dashboard/store/store-7/suppliers");
        assertThat(model.get("dictionaryKey")).isNull();
        assertThat(model.get("pairs")).asList().hasSize(2);
    }
}
