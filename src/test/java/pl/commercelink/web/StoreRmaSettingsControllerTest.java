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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.rma.RMAShippingService;
import pl.commercelink.starter.security.model.CustomUser;
import pl.commercelink.stores.AuthorizedCarrier;
import pl.commercelink.stores.PackageTemplate;
import pl.commercelink.stores.RMAConfiguration;
import pl.commercelink.stores.ShippingConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.PickerOption;
import pl.commercelink.web.settings.RmaReadiness;
import pl.commercelink.web.settings.SettingsFlash;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreRmaSettingsControllerTest {

    private static final Locale PL = Locale.forLanguageTag("pl");
    private static final AuthorizedCarrier DPD = new AuthorizedCarrier("dpd-1", "dpd", "DPD");
    private static final AuthorizedCarrier INPOST = new AuthorizedCarrier("inpost-1", "inpost", "InPost Kurier");

    @Mock
    private StoresRepository storesRepository;

    @Mock
    private MessageSource messageSource;

    private StoreRmaSettingsController controller;

    @BeforeEach
    void loggedInAsStoreAdmin() {
        controller = new StoreRmaSettingsController(storesRepository, new RMAShippingService(), messageSource);
        when(messageSource.getMessage(any(String.class), any(), any(Locale.class))).thenAnswer(call -> {
            Object[] args = call.getArgument(1);
            return args == null ? call.getArgument(0) : call.getArgument(0) + " " + args[0];
        });
        authenticateAs("store-1", "ADMIN");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void offersOnlyTheAuthorizedCarriersWithTheSavedOneSelectedAndNoEmptyOption() {
        // given
        Store store = storeWith("store-1", INPOST, DPD, INPOST);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.rma(model, PL);

        // then
        assertThat(view).isEqualTo("store-rma");
        assertThat(options(model)).containsExactly(
                new PickerOption("dpd-1", "DPD"),
                new PickerOption("inpost-1", "InPost Kurier"));
        assertThat(model.getAttribute("carrierId")).isEqualTo("inpost-1");
        assertThat(model.getAttribute("formAction")).isEqualTo("/dashboard/store/rma");
        assertThat(store.getRmaConfiguration().getCarrier().getId()).isEqualTo("inpost-1");
    }

    @Test
    void keepsACarrierRemovedFromTheAuthorizedListSelectedAndMarked() {
        // given -- returns still go through the saved copy, so the page must not pretend nothing is set
        AuthorizedCarrier removed = new AuthorizedCarrier("gls-1", "gls", "GLS");
        storeWith("store-1", removed, DPD);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        controller.rma(model, PL);

        // then
        assertThat(options(model)).containsExactly(
                new PickerOption("gls-1", "rma.settings.carrier.unauthorized.option GLS"),
                new PickerOption("dpd-1", "DPD"));
        assertThat(model.getAttribute("carrierId")).isEqualTo("gls-1");
        assertThat(readiness(model).carrierReady()).isFalse();
        assertThat(readiness(model).carrierLabel()).isEqualTo("GLS");
    }

    @Test
    void offersAnEmptyFirstOptionOnlyWhileNoCarrierIsSaved() {
        // given
        storeWith("store-1", null, DPD);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        controller.rma(model, PL);

        // then
        assertThat(options(model)).containsExactly(
                new PickerOption("", "rma.settings.carrier.placeholder"),
                new PickerOption("dpd-1", "DPD"));
        assertThat(model.getAttribute("carrierId")).isEqualTo("");
        assertThat(model.getAttribute("hasAuthorizedCarriers")).isEqualTo(true);
    }

    @Test
    void savesAFreshCopyOfTheChosenCarrierOnTheStoreFromTheSession() {
        // given
        Store store = storeWith("store-1", null, DPD, INPOST);

        // when
        String view = controller.saveRma("inpost-1", null, new ExtendedModelMap(), PL,
                new RedirectAttributesModelMap(), new MockHttpServletResponse());

        // then
        verify(storesRepository).save(store);
        AuthorizedCarrier saved = store.getRmaConfiguration().getCarrier();
        assertThat(saved).isNotSameAs(INPOST);
        assertThat(List.of(saved.getId(), saved.getName(), saved.getDisplayName()))
                .containsExactly("inpost-1", "inpost", "InPost Kurier");
        assertThat(view).isEqualTo("redirect:/dashboard/store/rma");
    }

    @Test
    void superAdminSavesOnTheStoreFromThePathAndReturnsToIt() {
        // given
        authenticateAs(null, "SUPER_ADMIN");
        Store store = storeWith("store-2", null, DPD);

        // when
        String view = controller.superAdminSaveRma("store-2", "dpd-1", null, new ExtendedModelMap(), PL,
                new RedirectAttributesModelMap(), new MockHttpServletResponse());

        // then
        verify(storesRepository).save(store);
        assertThat(store.getRmaConfiguration().getCarrier().getId()).isEqualTo("dpd-1");
        assertThat(view).isEqualTo("redirect:/dashboard/store/store-2/rma");
    }

    @Test
    void anEmptyChoiceIsRejectedBecauseItWouldSwitchCustomerReturnsOff() {
        // given
        Store store = storeWith("store-1", DPD, DPD);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.saveRma(null, null, model, PL, new RedirectAttributesModelMap(),
                new MockHttpServletResponse());

        // then
        verify(storesRepository, never()).save(any(Store.class));
        assertThat(view).isEqualTo("store-rma");
        assertThat(model.getAttribute("errors")).isEqualTo(Map.of("carrierId", "rma.settings.carrier.required"));
        assertThat(store.getRmaConfiguration().getCarrier().getId()).isEqualTo("dpd-1");
    }

    @Test
    void aCarrierOutsideTheAuthorizedListIsRejectedEvenWhenItIsTheSavedOne() {
        // given
        AuthorizedCarrier removed = new AuthorizedCarrier("gls-1", "gls", "GLS");
        storeWith("store-1", removed, DPD);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        controller.saveRma("gls-1", null, model, PL, new RedirectAttributesModelMap(), new MockHttpServletResponse());
        ExtendedModelMap forged = new ExtendedModelMap();
        controller.saveRma("made-up", null, forged, PL, new RedirectAttributesModelMap(), new MockHttpServletResponse());

        // then
        verify(storesRepository, never()).save(any(Store.class));
        assertThat(model.getAttribute("errors")).isEqualTo(Map.of("carrierId", "rma.settings.carrier.unauthorized"));
        assertThat(forged.getAttribute("errors")).isEqualTo(Map.of("carrierId", "rma.settings.carrier.unauthorized"));
    }

    @Test
    void aRejectedSaveWithoutReloadingAnswers422WithTheFormFragment() {
        // given
        storeWith("store-1", null, DPD);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = controller.saveRma(null, "fetch", new ExtendedModelMap(), PL, new RedirectAttributesModelMap(),
                response);

        // then
        assertThat(view).isEqualTo("store-rma :: rmaForm");
        assertThat(response.getStatus()).isEqualTo(422);
    }

    @Test
    void savingWithoutReloadingAnswersWithTheFormFragmentAndTheSuccessMessage() {
        // given
        storeWith("store-1", null, DPD);
        ExtendedModelMap model = new ExtendedModelMap();
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.saveRma("dpd-1", "fetch", model, PL, redirectAttributes, new MockHttpServletResponse());

        // then
        assertThat(view).isEqualTo("store-rma :: rmaForm");
        assertThat(model.getAttribute("savedMessage")).isEqualTo("rma.settings.update.success");
        assertThat(model.getAttribute("carrierId")).isEqualTo("dpd-1");
        assertThat(options(model)).containsExactly(new PickerOption("dpd-1", "DPD"));
        assertThat(redirectAttributes.getFlashAttributes()).doesNotContainKey(SettingsFlash.SAVED_MESSAGE);
    }

    @Test
    void readinessListsTheReturnTemplatesAndTheDefaultReceivingAddress() {
        // given
        Store store = storeWith("store-1", DPD, DPD);
        ShippingDetails other = address("a-1", "Magazyn boczny", false);
        ShippingDetails main = address("a-2", "Magazyn główny", true);
        store.getShippingConfiguration().setPickUpAddresses(new ArrayList<>(List.of(other, main)));
        store.getShippingConfiguration().setPackageTemplates(new ArrayList<>(List.of(
                template("RMA - Karton S"), template("Paleta"), template(null), template("RMA - Karton M"))));
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        controller.rma(model, PL);

        // then
        RmaReadiness readiness = readiness(model);
        assertThat(readiness.carrierReady()).isTrue();
        assertThat(readiness.returnTemplates()).containsExactly("RMA - Karton S", "RMA - Karton M");
        assertThat(readiness.receivingAddress()).startsWith("Magazyn główny · ul. Magazynowa 1");
        assertThat(readiness.shippingHref()).isEqualTo("/dashboard/store/shipping");
    }

    @Test
    void readinessReportsWhatIsMissingOnAStoreWithoutShippingSetUp() {
        // given
        Store store = new Store();
        store.setStoreId("store-1");
        when(storesRepository.findById("store-1")).thenReturn(store);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        controller.rma(model, PL);

        // then
        assertThat(model.getAttribute("hasAuthorizedCarriers")).isEqualTo(false);
        RmaReadiness readiness = readiness(model);
        assertThat(readiness.carrierReady()).isFalse();
        assertThat(readiness.templatesReady()).isFalse();
        assertThat(readiness.addressReady()).isFalse();
    }

    @Test
    void doesNotSaveWhenTheStoreDoesNotExist() {
        // given
        when(storesRepository.findById("store-1")).thenReturn(null);

        // when / then
        assertThatThrownBy(() -> controller.saveRma("dpd-1", null, new ExtendedModelMap(), PL, new RedirectAttributesModelMap(),
                new MockHttpServletResponse())).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(storesRepository, never()).save(any(Store.class));
    }

    @SuppressWarnings("unchecked")
    private List<PickerOption> options(ExtendedModelMap model) {
        return (List<PickerOption>) model.getAttribute("carrierOptions");
    }

    private RmaReadiness readiness(ExtendedModelMap model) {
        return (RmaReadiness) model.getAttribute("readiness");
    }

    /** A store with the given authorised carriers and, unless null, a saved copy of {@code saved} for returns. */
    private Store storeWith(String storeId, AuthorizedCarrier saved, AuthorizedCarrier... authorized) {
        Store store = new Store();
        store.setStoreId(storeId);
        ShippingConfiguration shipping = new ShippingConfiguration();
        shipping.setAuthorizedCarriers(new ArrayList<>(List.of(authorized)));
        store.setShippingConfiguration(shipping);
        RMAConfiguration rma = new RMAConfiguration();
        if (saved != null) {
            rma.setCarrier(new AuthorizedCarrier(saved.getId(), saved.getName(), saved.getDisplayName()));
        }
        store.setRmaConfiguration(rma);
        when(storesRepository.findById(storeId)).thenReturn(store);
        return store;
    }

    private static ShippingDetails address(String id, String companyName, boolean isDefault) {
        ShippingDetails details = new ShippingDetails();
        details.setId(id);
        details.setCompanyName(companyName);
        details.setStreetAndNumber("ul. Magazynowa 1");
        details.setPostalCode("00-001");
        details.setCity("Warszawa");
        details.setCountry("PL");
        details.set_default(isDefault);
        return details;
    }

    private static PackageTemplate template(String name) {
        PackageTemplate template = new PackageTemplate();
        template.setId(name == null ? "blank" : name);
        template.setName(name);
        return template;
    }

    private void authenticateAs(String storeId, String role) {
        Map<String, String> attributes = storeId != null
                ? Map.of("storeId", storeId, "role", role)
                : Map.of("role", role);
        CustomUser user = new CustomUser(null, null, attributes);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)))
        );
    }
}
