package pl.commercelink.orders.rma;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.RmaCenterForm;
import pl.commercelink.web.settings.RmaCenterView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RMACenterControllerTest {

    private static final Locale PL = Locale.forLanguageTag("pl");

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private RMACentersRepository rmaCentersRepository;
    @Mock
    private SupplierRegistry supplierRegistry;
    @Mock
    private SupplierLabels supplierLabels;
    @Mock
    private SupplierLabelMap supplierLabelMap;
    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private RMACenterController controller;

    private Store storeWith(StoreSupplierConnection... connections) {
        FulfilmentConfiguration config = new FulfilmentConfiguration();
        config.setSupplierConnections(new ArrayList<>(List.of(connections)));
        Store store = new Store();
        store.setStoreId("store-1");
        store.setFulfilmentConfiguration(config);
        return store;
    }

    private RMACenter center(String storeId, String rmaCenterId, String provider) {
        RMACenter center = new RMACenter();
        center.setStoreId(storeId);
        center.setRmaCenterId(rmaCenterId);
        center.setProvider(provider);
        center.setShippingDetails(new ShippingDetails());
        return center;
    }

    private RmaCenterForm completeForm() {
        RmaCenterForm form = new RmaCenterForm();
        form.setProvider("Elko");
        form.setCompanyName("Elko Service");
        form.setStreetAndNumber("ul. Serwisowa 8");
        form.setPostalCode("31-234");
        form.setCity("Kraków");
        form.setCountry("PL");
        form.setPhone("+48 600 700 800");
        return form;
    }

    private void labelled() {
        when(supplierLabels.forStoreId(any())).thenReturn(supplierLabelMap);
        when(supplierLabels.forStore(any())).thenReturn(supplierLabelMap);
        when(supplierLabelMap.of(any())).thenAnswer(call -> call.getArgument(0));
        when(supplierLabelMap.options()).thenReturn(List.of());
        when(messageSource.getMessage(any(String.class), any(), any(Locale.class))).thenReturn("message");
    }

    private void asStoreAdmin(Runnable body) {
        run(false, body);
    }

    private void asSuperAdmin(Runnable body) {
        run(true, body);
    }

    private void run(boolean superAdmin, Runnable body) {
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(superAdmin);
            security.when(() -> CustomSecurityContext.hasRole("ADMIN")).thenReturn(!superAdmin);
            security.when(CustomSecurityContext::getStoreId).thenReturn("store-1");
            body.run();
        }
    }

    @SuppressWarnings("unchecked")
    private List<RmaCenterView> attribute(ExtendedModelMap model, String name) {
        return (List<RmaCenterView>) model.getAttribute(name);
    }

    @Test
    void listShowsAPlatformCenterToAStoreWithATokenedInstanceOfItsType() {
        // given
        labelled();
        when(rmaCentersRepository.findByStoreId("store-1")).thenReturn(List.of(center(RMACenter.MANAGED_RMA_CENTER_STORE_ID, "center-1", "Elko")));
        when(storesRepository.findById("store-1"))
                .thenReturn(storeWith(new StoreSupplierConnection("Elko-k7f3a9c2", ConnectionMode.OWN, true, true)));
        when(supplierRegistry.exists("Elko")).thenReturn(true);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        asStoreAdmin(() -> controller.list(model, PL));

        // then -- a centre of the platform bucket is shown under the shared heading, without actions
        assertThat(attribute(model, "sharedCenters")).extracting(RmaCenterView::title).containsExactly("Elko");
        assertThat(attribute(model, "ownCenters")).isEmpty();
    }

    @Test
    void listHidesAPlatformCenterFromAStoreWithoutAConnectionOfItsType() {
        // given
        labelled();
        when(rmaCentersRepository.findByStoreId("store-1")).thenReturn(List.of(center(RMACenter.MANAGED_RMA_CENTER_STORE_ID, "center-1", "Elko")));
        when(storesRepository.findById("store-1"))
                .thenReturn(storeWith(new StoreSupplierConnection("Kosatec-a1b2c3d4", ConnectionMode.OWN, true, true)));
        when(supplierRegistry.exists("Elko")).thenReturn(true);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        asStoreAdmin(() -> controller.list(model, PL));

        // then
        assertThat(attribute(model, "sharedCenters")).isEmpty();
        assertThat(attribute(model, "ownCenters")).isEmpty();
    }

    /** A CSV price list is no adapter, so the registry does not know it; its centre must not read "unknown supplier". */
    @Test
    void aCentreOfAManualPriceListIsNotShownAsAnUnknownSupplier() {
        // given
        labelled();
        when(rmaCentersRepository.findByStoreId("store-1")).thenReturn(List.of(center("store-1", "own-1", "manual:Asus")));
        when(storesRepository.findById("store-1")).thenReturn(storeWith(
                new StoreSupplierConnection("manual:Asus", ConnectionMode.MANUAL, true, true)));
        when(supplierRegistry.exists("manual:Asus")).thenReturn(false);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        asStoreAdmin(() -> controller.list(model, PL));

        // then
        assertThat(attribute(model, "ownCenters")).extracting(RmaCenterView::knownProvider).containsExactly(true);
    }

    @Test
    void listSeparatesTheStoresOwnCentresFromTheSharedOnes() {
        // given
        labelled();
        when(rmaCentersRepository.findByStoreId("store-1")).thenReturn(List.of(
                center("store-1", "own-1", "Acme"),
                center(RMACenter.MANAGED_RMA_CENTER_STORE_ID, "shared-1", "Other")));
        when(storesRepository.findById("store-1")).thenReturn(storeWith(
                new StoreSupplierConnection("Acme", ConnectionMode.OWN, true, true)));
        when(supplierRegistry.exists(any())).thenReturn(true);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        asStoreAdmin(() -> controller.list(model, PL));

        // then
        assertThat(attribute(model, "ownCenters")).extracting(RmaCenterView::title).containsExactly("Acme");
        assertThat(attribute(model, "sharedCenters")).extracting(RmaCenterView::shared).containsExactly(true);
    }

    @Test
    void savingStoresTheCentreUnderTheStoreFromTheSessionBecauseTheFormCarriesNoStoreId() {
        // given
        labelled();
        ArgumentCaptor<RMACenter> saved = ArgumentCaptor.forClass(RMACenter.class);

        // when
        asStoreAdmin(() -> controller.create(completeForm(), null, new ExtendedModelMap(), PL,
                new RedirectAttributesModelMap(), null, null));

        // then
        verify(rmaCentersRepository).save(saved.capture());
        assertThat(saved.getValue().getStoreId()).isEqualTo("store-1");
        assertThat(saved.getValue().getRmaCenterId()).isNotBlank();
        assertThat(saved.getValue().getProvider()).isEqualTo("Elko");
        assertThat(saved.getValue().getShippingDetails().getCity()).isEqualTo("Kraków");
    }

    @Test
    void aSuperAdminSavesIntoThePlatformBucket() {
        // given
        labelled();
        ArgumentCaptor<RMACenter> saved = ArgumentCaptor.forClass(RMACenter.class);

        // when
        asSuperAdmin(() -> controller.create(completeForm(), null, new ExtendedModelMap(), PL,
                new RedirectAttributesModelMap(), null, null));

        // then
        verify(rmaCentersRepository).save(saved.capture());
        assertThat(saved.getValue().getStoreId()).isEqualTo(RMACenter.MANAGED_RMA_CENTER_STORE_ID);
    }

    @Test
    void anEmptySubmissionIsRejectedInsteadOfCreatingABlankCentre() {
        // given
        labelled();
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        asStoreAdmin(() -> controller.create(new RmaCenterForm(), null, model, PL,
                new RedirectAttributesModelMap(), null, null));

        // then
        verify(rmaCentersRepository, never()).save(any());
        assertThat(model.getAttribute("errors")).asInstanceOf(
                org.assertj.core.api.InstanceOfAssertFactories.map(String.class, String.class))
                .containsKeys("provider", "companyName", "streetAndNumber", "postalCode", "city", "phone");
    }

    @Test
    void aCentreOfAnotherStoreAnswers404OnEditSaveAndDelete() {
        // given
        labelled();
        when(rmaCentersRepository.findById("store-1", "foreign")).thenReturn(null);
        List<Consumer<String>> calls = List.of(
                id -> controller.editForm(id, new ExtendedModelMap(), PL),
                id -> controller.update(id, completeForm(), null, new ExtendedModelMap(), PL,
                        new RedirectAttributesModelMap(), null, null),
                id -> controller.delete(id, PL, new RedirectAttributesModelMap()));

        // when / then
        asStoreAdmin(() -> calls.forEach(call ->
                assertThatThrownBy(() -> call.accept("foreign")).isInstanceOf(ResponseStatusException.class)));
        verify(rmaCentersRepository, never()).save(any());
        verify(rmaCentersRepository, never()).delete(any(RMACenter.class));
    }

    @Test
    void editingKeepsTheCentreIdAndStoreOfTheStoredRecord() {
        // given
        labelled();
        RMACenter stored = center("store-1", "center-1", "Acme");
        when(rmaCentersRepository.findById("store-1", "center-1")).thenReturn(stored);
        ArgumentCaptor<RMACenter> saved = ArgumentCaptor.forClass(RMACenter.class);

        // when
        asStoreAdmin(() -> controller.update("center-1", completeForm(), null, new ExtendedModelMap(), PL,
                new RedirectAttributesModelMap(), null, null));

        // then
        verify(rmaCentersRepository).save(saved.capture());
        assertThat(saved.getValue().getRmaCenterId()).isEqualTo("center-1");
        assertThat(saved.getValue().getStoreId()).isEqualTo("store-1");
        assertThat(saved.getValue().getProvider()).isEqualTo("Elko");
    }
}
