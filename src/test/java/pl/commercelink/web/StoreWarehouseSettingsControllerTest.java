package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.printing.PrintProviderRegistry;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.testsupport.OptimisticLockingExecutorMocks;
import pl.commercelink.stores.Printer;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.stores.WarehouseConfiguration;
import pl.commercelink.web.dtos.WarehouseDocumentsForm;
import pl.commercelink.web.settings.WarehouseAddressView;

import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static pl.commercelink.testsupport.SecurityContextLogin.logInAs;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreWarehouseSettingsControllerTest {

    private static final Locale POLISH = Locale.forLanguageTag("pl");

    @Mock
    private StoresRepository storesRepository;

    @Mock
    private MessageSource messageSource;

    @Mock
    private PrintProviderRegistry printProviderRegistry;

    @Mock
    private OptimisticLockingExecutor optimisticLockingExecutor;

    @InjectMocks
    private StoreWarehouseSettingsController controller;

    @BeforeEach
    void retriesPassThrough() {
        when(optimisticLockingExecutor.modifyAndSave(any(), any(), any()))
                .thenAnswer(OptimisticLockingExecutorMocks.passThroughModifyAndSave());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Store store(String storeId) {
        Store store = new Store();
        store.setStoreId(storeId);
        when(storesRepository.findById(storeId)).thenReturn(store);
        return store;
    }

    private ShippingDetails address(String id, String company) {
        ShippingDetails details = new ShippingDetails();
        details.setId(id);
        details.setCompanyName(company);
        details.setStreetAndNumber("ul. Magazynowa 12");
        details.setPostalCode("02-495");
        details.setCity("Warszawa");
        details.setCountry("PL");
        details.setEmail("magazyn@sklep.pl");
        details.setPhone("600100200");
        return details;
    }

    @SuppressWarnings("unchecked")
    private List<WarehouseAddressView> addresses(ExtendedModelMap model) {
        return (List<WarehouseAddressView>) model.get("addresses");
    }

    private WarehouseDocumentsForm documents(boolean enabled, String warehouseId, String costCenterId) {
        WarehouseDocumentsForm form = new WarehouseDocumentsForm();
        form.setDocumentsEnabled(enabled);
        form.setWarehouseId(warehouseId);
        form.setCostCenterId(costCenterId);
        return form;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> errors(ExtendedModelMap model) {
        return (Map<String, String>) model.get("errors");
    }

    @Test
    void showsTheDocumentsFormAndTheListsOfAddressesAndPrinters() {
        // given
        logInAs("ADMIN", "store-1");
        Store store = store("store-1");
        store.setShippingDetails(new LinkedList<>(List.of(address("a-1", "Magazyn Warszawa"))));
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.warehouse(model, POLISH);

        // then
        assertThat(view).isEqualTo("store-warehouse");
        assertThat(model.get("formAction")).isEqualTo("/dashboard/store/warehouse");
        assertThat(addresses(model)).extracting(WarehouseAddressView::editHref)
                .containsExactly("/dashboard/store/warehouse/addresses/a-1");
        assertThat(model.get("newPrinterHref")).isEqualTo("/dashboard/store/warehouse/printers/new");
        assertThat(model.get("invoicingConnected")).isEqualTo(false);
        verify(storesRepository, never()).save(any());
    }

    @Test
    void addressesAndPrintersWithoutAnIdGetOneWhenThePageOpens() {
        // given
        logInAs("ADMIN", "store-1");
        Store store = store("store-1");
        store.setShippingDetails(new LinkedList<>(List.of(address(null, "Legacy"))));
        WarehouseConfiguration configuration = new WarehouseConfiguration();
        Printer printer = new Printer();
        printer.setName("Zebra");
        configuration.setPrinters(new LinkedList<>(List.of(printer)));
        store.setWarehouseConfiguration(configuration);

        // when
        controller.warehouse(new ExtendedModelMap(), POLISH);

        // then
        verify(storesRepository).save(store);
        assertThat(store.getShippingDetails().getFirst().getId()).isNotBlank();
        assertThat(printer.getId()).isNotBlank();
    }

    @Test
    void storeAdminAlwaysSavesTheDocumentsOfTheStoreFromTheirSession() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1");
        store("other-store");

        // when
        String view = controller.saveDocuments(documents(true, "MAG-01", "KC-01"), null, new ExtendedModelMap(), POLISH,
                new RedirectAttributesModelMap(), new MockHttpServletResponse());

        // then
        ArgumentCaptor<Store> saved = ArgumentCaptor.forClass(Store.class);
        verify(storesRepository).save(saved.capture());
        assertThat(saved.getValue().getStoreId()).isEqualTo("store-1");
        assertThat(saved.getValue().getWarehouseConfiguration().getWarehouseId()).isEqualTo("MAG-01");
        verify(storesRepository, never()).findById("other-store");
        assertThat(view).isEqualTo("redirect:/dashboard/store/warehouse");
    }

    @Test
    void superAdminSavesTheDocumentsOfTheStoreFromThePath() {
        // given
        logInAs("SUPER_ADMIN", "none");
        Store store = store("store-9");

        // when
        String view = controller.superAdminSaveDocuments("store-9", documents(false, null, null), null, new ExtendedModelMap(),
                POLISH, new RedirectAttributesModelMap(), new MockHttpServletResponse());

        // then
        verify(storesRepository).save(store);
        assertThat(view).isEqualTo("redirect:/dashboard/store/store-9/warehouse");
    }

    @Test
    void documentsCannotBeSwitchedOnWithoutTheIds() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1");
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = controller.saveDocuments(documents(true, "MAG-01", ""), "fetch", model, POLISH,
                new RedirectAttributesModelMap(), response);

        // then
        verify(storesRepository, never()).save(any());
        assertThat(view).isEqualTo("store-warehouse :: documentsForm");
        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(errors(model)).containsOnlyKeys("costCenterId");
    }

    @Test
    void asyncSaveAnswersWithTheDocumentsFormAndTheSuccessMessage() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1");
        when(messageSource.getMessage("store.warehouse.documents.update.success", null, POLISH)).thenReturn("Zapisano");
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = controller.saveDocuments(documents(true, " MAG-01 ", "KC-01"), "fetch", model, POLISH,
                new RedirectAttributesModelMap(), response);

        // then
        assertThat(view).isEqualTo("store-warehouse :: documentsForm");
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(model.get("savedMessage")).isEqualTo("Zapisano");
        assertThat(((WarehouseDocumentsForm) model.get("form")).getWarehouseId()).isEqualTo("MAG-01");
    }

    @Test
    void anUnknownStoreIsNotFound() {
        // when / then
        assertThatThrownBy(() -> controller.superAdminWarehouse("missing", new ExtendedModelMap(), Locale.forLanguageTag("pl")))
                .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND));
    }
}
