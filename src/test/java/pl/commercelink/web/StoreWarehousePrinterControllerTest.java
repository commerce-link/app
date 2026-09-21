package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.printing.PrintProviderRegistry;
import pl.commercelink.printing.api.PrintProviderDescriptor;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.provider.api.ProviderField.FieldType;
import pl.commercelink.stores.Printer;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.stores.WarehouseConfiguration;
import pl.commercelink.web.dtos.WarehousePrinterForm;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static pl.commercelink.testsupport.SecurityContextLogin.logInAs;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreWarehousePrinterControllerTest {

    private static final Locale POLISH = Locale.forLanguageTag("pl");

    @Mock
    private StoresRepository storesRepository;

    @Mock
    private MessageSource messageSource;

    @Mock
    private PrintProviderRegistry printProviderRegistry;

    @Mock
    private PrintProviderDescriptor zebra;

    @InjectMocks
    private StoreWarehousePrinterController controller;

    @BeforeEach
    void setUp() {
        when(messageSource.getMessage(anyString(), any(), eq(POLISH))).thenAnswer(invocation -> invocation.getArgument(0));
        when(zebra.name()).thenReturn("zebra");
        when(zebra.displayName()).thenReturn("Zebra ZPL");
        when(zebra.configurationFields()).thenReturn(List.of(
                new ProviderField("deviceId", "ID drukarki", FieldType.TEXT, true, ""),
                new ProviderField("apiKey", "Klucz API", FieldType.PASSWORD, false, "")));
        when(printProviderRegistry.availableProviders()).thenReturn(List.of(zebra));
        when(printProviderRegistry.getDescriptor("zebra")).thenReturn(zebra);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Store store(String storeId, Printer... printers) {
        Store store = new Store();
        store.setStoreId(storeId);
        WarehouseConfiguration configuration = new WarehouseConfiguration();
        configuration.setPrinters(new LinkedList<>(List.of(printers)));
        store.setWarehouseConfiguration(configuration);
        when(storesRepository.findById(storeId)).thenReturn(store);
        return store;
    }

    private Printer printer(String id, String name) {
        Printer printer = new Printer();
        printer.setId(id);
        printer.setName(name);
        printer.setProviderName("zebra");
        printer.setSettings(new HashMap<>(Map.of("deviceId", "ZD-1", "apiKey", "stored-secret")));
        return printer;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> errors(ExtendedModelMap model) {
        return (Map<String, String>) model.get("errors");
    }

    @SuppressWarnings("unchecked")
    private Set<String> storedSecretIds(ExtendedModelMap model) {
        return (Set<String>) model.get("storedSecretIds");
    }

    private WarehousePrinterForm form(String name, String deviceId) {
        WarehousePrinterForm form = new WarehousePrinterForm();
        form.setName(name);
        form.setType("zebra");
        form.setSettings(new HashMap<>(Map.of("zebra.deviceId", deviceId)));
        return form;
    }

    @Test
    void theOnlyPrinterTypeIsPreselectedOnANewPrinter() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.newPrinter(null, model, POLISH);

        // then
        assertThat(view).isEqualTo("store-warehouse-printer");
        assertThat(((WarehousePrinterForm) model.get("form")).getType()).isEqualTo("zebra");
        assertThat(model.get("knownType")).isEqualTo(true);
        assertThat(model.get("formAction")).isEqualTo("/dashboard/store/warehouse/printers/new");
    }

    @Test
    void superAdminAddsAPrinterToTheStoreFromThePath() {
        // given
        logInAs("SUPER_ADMIN", "none");
        Store store = store("store-9");

        // when
        String view = controller.superAdminCreatePrinter("store-9", form("Zebra pakowanie", "ZD-PACK-01"), null,
                new ExtendedModelMap(), POLISH, new RedirectAttributesModelMap(), new MockHttpServletRequest(),
                new MockHttpServletResponse());

        // then
        verify(storesRepository).save(store);
        assertThat(store.getWarehouseConfiguration().getPrinters()).singleElement().satisfies(printer -> {
            assertThat(printer.getId()).isNotBlank();
            assertThat(printer.getSettings()).containsEntry("deviceId", "ZD-PACK-01");
        });
        assertThat(view).isEqualTo("redirect:/dashboard/store/store-9/warehouse");
    }

    @Test
    void aPrinterNamedLikeAnotherOneIsNotSaved() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1", printer("p-1", "Zebra pakowanie"));
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.createPrinter(form("zebra pakowanie", "ZD-2"), null, model, POLISH,
                new RedirectAttributesModelMap(), new MockHttpServletRequest(), new MockHttpServletResponse());

        // then
        verify(storesRepository, never()).save(any());
        assertThat(view).isEqualTo("store-warehouse-printer");
        assertThat(errors(model)).containsKey("name");
    }

    @Test
    void theEditPageNeverSendsAStoredSecretButSaysOneIsStored() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1", printer("p-1", "Zebra"));
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        controller.editPrinter("p-1", model, POLISH);

        // then
        WarehousePrinterForm form = (WarehousePrinterForm) model.get("form");
        assertThat(form.getSettings()).doesNotContainValue("stored-secret");
        assertThat(storedSecretIds(model)).containsExactly("setting-zebra-apiKey");
        assertThat(model.get("formAction")).isEqualTo("/dashboard/store/warehouse/printers/p-1");
    }

    @Test
    void editingAPrinterKeepsItsIdAndTheStoredSecret() {
        // given
        logInAs("ADMIN", "store-1");
        Printer printer = printer("p-1", "Zebra");
        store("store-1", printer);

        // when
        controller.updatePrinter("p-1", form("Zebra przyjęcia", "ZD-9"), null, new ExtendedModelMap(), POLISH,
                new RedirectAttributesModelMap(), new MockHttpServletRequest(), new MockHttpServletResponse());

        // then
        assertThat(printer.getId()).isEqualTo("p-1");
        assertThat(printer.getName()).isEqualTo("Zebra przyjęcia");
        assertThat(printer.getSettings()).containsEntry("deviceId", "ZD-9").containsEntry("apiKey", "stored-secret");
    }

    @Test
    void deletingRemovesOnlyThePrinterWithThatIdEvenWhenNamesRepeat() {
        // given
        logInAs("ADMIN", "store-1");
        Store store = store("store-1", printer("p-1", "Zebra"), printer("p-2", "Zebra"));

        // when
        String view = controller.deletePrinter("p-1", POLISH, new RedirectAttributesModelMap());

        // then
        assertThat(store.getWarehouseConfiguration().getPrinters()).extracting(Printer::getId).containsExactly("p-2");
        assertThat(view).isEqualTo("redirect:/dashboard/store/warehouse");
    }

    @Test
    void aPrinterOfAnotherStoreIsNotFound() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1");
        store("other-store", printer("foreign", "Zebra"));

        // when / then
        assertThatThrownBy(() -> controller.editPrinter("foreign", new ExtendedModelMap(), POLISH))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void anUnknownPrinterTypeIsRejected() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1");
        WarehousePrinterForm form = form("Laser", "x");
        form.setType("laser");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        controller.createPrinter(form, null, model, POLISH, new RedirectAttributesModelMap(), new MockHttpServletRequest(),
                new MockHttpServletResponse());

        // then
        verify(storesRepository, never()).save(any());
        assertThat(errors(model)).containsKey("type");
    }
}
