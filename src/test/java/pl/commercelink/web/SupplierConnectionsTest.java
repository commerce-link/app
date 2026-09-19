package pl.commercelink.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import pl.commercelink.inventory.supplier.ErrorMessage;
import pl.commercelink.inventory.supplier.StoreSupplierConnectionService;
import pl.commercelink.inventory.supplier.SupplierConnectionView;
import pl.commercelink.inventory.supplier.SupplierConnectionViewFactory;
import pl.commercelink.inventory.supplier.SupplierProviderFactory;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.SupplierProviderDescriptor;
import pl.commercelink.inventory.supplier.manual.ManualSupplierService;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.provider.api.ProviderField.FieldType;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.stores.SupplierSelectionForm;
import pl.commercelink.web.dtos.SupplierSettingsForm;
import pl.commercelink.web.settings.SupplierView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SupplierConnectionsTest {

    private static final Locale POLISH = Locale.forLanguageTag("pl");
    private static final List<ProviderField> FIELDS = List.of(
            new ProviderField("login", "Login", FieldType.TEXT, true, null),
            new ProviderField("password", "Hasło", FieldType.PASSWORD, true, null));
    private static final byte[] PRICE_LIST = "ean;mfn\n1;2\n".getBytes();

    @Mock
    private StoreSupplierConnectionService connectionService;
    @Mock
    private ManualSupplierService manualSupplierService;
    @Mock
    private SupplierConnectionViewFactory viewFactory;
    @Mock
    private SupplierProviderFactory providerFactory;
    @Mock
    private SupplierRegistry supplierRegistry;
    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private SupplierConnections suppliers;

    private Store store;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(suppliers, "minIntervalMinutes", 15);
        SupplierProviderDescriptor descriptor = mock(SupplierProviderDescriptor.class);
        when(descriptor.configurationFields()).thenReturn(FIELDS);
        when(providerFactory.getDescriptor("Acme")).thenReturn(descriptor);
        when(supplierRegistry.getExternalSupplierNames()).thenReturn(List.of("Acme"));
        store = new Store();
        store.setStoreId("store-1");
        store.setFulfilmentConfiguration(new FulfilmentConfiguration());
    }

    private static SupplierSettingsForm acme(String label, String login, String password) {
        SupplierSettingsForm form = SupplierSettingsForm.newSupplier("Acme");
        form.setLabel(label);
        form.setSettings(new java.util.HashMap<>(Map.of("Acme.login", login, "Acme.password", password)));
        return form;
    }

    private static SupplierSettingsForm priceList(String label, byte[] file) {
        SupplierSettingsForm form = SupplierSettingsForm.newSupplier(SupplierSettingsForm.CSV);
        form.setLabel(label);
        if (file != null) {
            form.setFile(new MockMultipartFile("file", "cennik.csv", "text/csv", file));
        }
        return form;
    }

    @Test
    void integrationsAndPriceListsAreListedTogetherByName() {
        // given
        SupplierConnectionView zeta = new SupplierConnectionView("Acme", "Acme", "Zeta", ConnectionMode.GLOBAL, true, true,
                true, null, null, null, null, true);
        SupplierConnectionView alfa = new SupplierConnectionView("manual-abcd1234", null, "alfa", ConnectionMode.MANUAL,
                true, true, false, null, null, null, null, true);
        when(viewFactory.views(store)).thenReturn(new SupplierConnectionViewFactory.SupplierConnectionViews(List.of(zeta), List.of(alfa)));
        when(connectionService.incompleteConnections(store)).thenReturn(Set.of("Acme"));

        // when
        List<SupplierView> views = suppliers.views(store, "/dashboard/store/suppliers");

        // then
        assertThat(views).extracting(SupplierView::title).containsExactly("alfa", "Zeta");
        assertThat(views.get(1).state()).isEqualTo(SupplierView.State.INCOMPLETE);
        assertThat(views.get(0).state()).isEqualTo(SupplierView.State.DISABLED);
    }

    @Test
    void anIntegrationTypeThatIsNotInstalledCannotBeAdded() {
        // given
        SupplierSettingsForm form = acme("Acme", "a", "b");
        form.setProviderName("Gone");

        // when
        SupplierConnections.SaveResult result = suppliers.saveIntegration(store, null, form, POLISH);

        // then
        assertThat(result.errors()).containsOnlyKeys("providerName");
        verify(connectionService, never()).connectOrUpdate(any(), any(), anyMap());
    }

    @Test
    void missingAccessDetailsAreShownAtTheirFieldsAndNothingIsSaved() {
        // when
        SupplierConnections.SaveResult result = suppliers.saveIntegration(store, null, acme("", "", ""), POLISH);

        // then
        assertThat(result.errors()).containsOnlyKeys("label", "setting-Acme-login", "setting-Acme-password");
        verify(connectionService, never()).connectOrUpdate(any(), any(), anyMap());
    }

    @Test
    void theServicesOwnChecksLandAtTheFieldsTheyConcern() {
        // given
        when(connectionService.connectOrUpdate(eq(store), any(), anyMap()))
                .thenReturn(new StoreSupplierConnectionService.ConnectionUpdateResult(List.of(
                        ErrorMessage.of("store.supplier.connection.error.label.taken", "Acme"),
                        ErrorMessage.of("store.supplier.connection.error.update.failed")), null, Set.of(), Set.of(), Set.of()));
        when(messageSource.getMessage(eq("store.supplier.connection.error.update.failed"), any(), eq(POLISH)))
                .thenReturn("Nie udało się zapisać.");

        // when
        SupplierConnections.SaveResult result = suppliers.saveIntegration(store, null, acme("Acme", "sklep", "tajne"), POLISH);

        // then
        assertThat(result.errors()).containsExactly(Map.entry("label", "store.suppliers.label.taken"));
        assertThat(result.failure()).isEqualTo("Nie udało się zapisać.");
    }

    @Test
    void aGlobalConnectionIsSavedWithoutAccessDetails() {
        // given
        SupplierSettingsForm form = acme(null, "", "");
        form.setMode(ConnectionMode.GLOBAL.name());
        when(connectionService.connectOrUpdate(eq(store), any(), anyMap()))
                .thenReturn(new StoreSupplierConnectionService.ConnectionUpdateResult(List.of(), "Acme", Set.of("Acme"), Set.of(), Set.of()));

        // when
        SupplierConnections.SaveResult result = suppliers.saveIntegration(store, null, form, POLISH);

        // then
        assertThat(result.ok()).isTrue();
        ArgumentCaptor<SupplierSelectionForm> selection = ArgumentCaptor.forClass(SupplierSelectionForm.class);
        verify(connectionService).connectOrUpdate(eq(store), selection.capture(), eq(Map.of()));
        assertThat(selection.getValue().getMode()).isEqualTo(ConnectionMode.GLOBAL);
    }

    @Test
    void aPriceListWithAFileIsCreatedThenGetsTheFileThenItsSettings() {
        // given
        when(manualSupplierService.labelProblem(store, null, "Hurtownia")).thenReturn(null);
        when(manualSupplierService.isLoadable(PRICE_LIST)).thenReturn(true);
        when(manualSupplierService.create("store-1", "Hurtownia")).thenReturn(ManualSupplierService.Result.created("manual-abcd1234"));
        when(manualSupplierService.uploadFeed(eq("store-1"), eq("manual-abcd1234"), any())).thenReturn(ManualSupplierService.Result.success());
        when(manualSupplierService.applySelections(eq("store-1"), any())).thenReturn(ManualSupplierService.Result.success());

        // when
        SupplierConnections.SaveResult result = suppliers.saveCsv(store, null, priceList("Hurtownia", PRICE_LIST), POLISH);

        // then
        assertThat(result.ok()).isTrue();
        assertThat(result.identity()).isEqualTo("manual-abcd1234");
        InOrder order = inOrder(manualSupplierService);
        order.verify(manualSupplierService).create("store-1", "Hurtownia");
        order.verify(manualSupplierService).uploadFeed("store-1", "manual-abcd1234", PRICE_LIST);
        order.verify(manualSupplierService).applySelections(eq("store-1"), any());
    }

    @Test
    void anUnreadableFileOrATakenNameSavesNothing() {
        // given
        when(manualSupplierService.labelProblem(store, null, "Kosatec")).thenReturn("store.manual.error.name.taken");
        when(manualSupplierService.isLoadable(PRICE_LIST)).thenReturn(false);

        // when
        SupplierConnections.SaveResult result = suppliers.saveCsv(store, null, priceList("Kosatec", PRICE_LIST), POLISH);

        // then
        assertThat(result.errors()).containsEntry("label", "store.suppliers.label.taken")
                .containsEntry("file", "store.suppliers.file.invalid");
        verify(manualSupplierService, never()).create(anyString(), anyString());
        verify(manualSupplierService, never()).uploadFeed(anyString(), anyString(), any());
    }

    @Test
    void editingAPriceListWithoutAFileKeepsTheStoredOne() {
        // given
        StoreSupplierConnection existing = new StoreSupplierConnection("manual-abcd1234", ConnectionMode.MANUAL, true, true);
        when(manualSupplierService.applySelections(eq("store-1"), any())).thenReturn(ManualSupplierService.Result.success());

        // when
        SupplierConnections.SaveResult result = suppliers.saveCsv(store, existing, priceList("Hurtownia", null), POLISH);

        // then
        assertThat(result.ok()).isTrue();
        verify(manualSupplierService, never()).create(anyString(), anyString());
        verify(manualSupplierService, never()).uploadFeed(anyString(), anyString(), any());
        verify(manualSupplierService).labelProblem(store, "manual-abcd1234", "Hurtownia");
    }

    @Test
    void theAdminSettingsKeepTheStoresConnections() {
        // given
        store.getFulfilmentConfiguration().setSupplierConnections(new ArrayList<>(List.of(
                new StoreSupplierConnection("Acme", ConnectionMode.GLOBAL, true, true))));
        when(connectionService.applyStoreSettings(any(), any(), anyBoolean()))
                .thenReturn(new StoreSupplierConnectionService.ConnectionUpdateResult(List.of(), null, Set.of(), Set.of(), Set.of()));

        // when
        boolean saved = suppliers.saveAdminSettings(store, false, 20);

        // then
        assertThat(saved).isTrue();
        ArgumentCaptor<FulfilmentConfiguration> submitted = ArgumentCaptor.forClass(FulfilmentConfiguration.class);
        verify(connectionService).applyStoreSettings(eq(store), submitted.capture(), eq(true));
        assertThat(submitted.getValue().isCanUseGlobalSuppliers()).isFalse();
        assertThat(submitted.getValue().getInventoryCacheTtlMinutes()).isEqualTo(20);
        assertThat(submitted.getValue().getSupplierConnections()).hasSize(1);
    }
}
