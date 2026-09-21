package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.starter.security.model.CustomUser;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.CompanyDetailsForm;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreControllerCompanyDetailsTest {

    private static final Locale POLISH = Locale.forLanguageTag("pl");

    @Mock
    private StoresRepository storesRepository;

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private StoreController controller;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void logInAs(String role, String storeId) {
        CustomUser user = new CustomUser(null, null, Map.of("storeId", storeId, "role", role));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private Store store(String storeId) {
        Store store = new Store();
        store.setStoreId(storeId);
        when(storesRepository.findById(storeId)).thenReturn(store);
        return store;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> errors(ExtendedModelMap model) {
        return (Map<String, String>) model.get("errors");
    }

    private CompanyDetailsForm validForm() {
        CompanyDetailsForm form = new CompanyDetailsForm();
        form.setCompanyName("Demo Store sp. z o.o.");
        form.setTaxId("1234567890");
        form.setStreetAndNumber("ul. Testowa 1");
        form.setPostalCode("00-001");
        form.setCity("Warszawa");
        form.setCountry("PL");
        form.setEmail("biuro@demo.pl");
        return form;
    }

    @Test
    void rendersTheStoredDetailsWithCountryOptionsAndNoErrors() {
        // given
        logInAs("ADMIN", "store-1");
        BillingDetails details = new BillingDetails();
        details.setCompanyName("Demo Store sp. z o.o.");
        details.setCountry("DE");
        store("store-1").setBillingDetails(details);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.storeCompanyDetails(model, POLISH);

        // then
        assertThat(view).isEqualTo("store-company-details");
        assertThat(((CompanyDetailsForm) model.get("form")).getCompanyName()).isEqualTo("Demo Store sp. z o.o.");
        assertThat(model.get("formAction")).isEqualTo("/dashboard/store/company-details");
        assertThat(errors(model)).isEmpty();
        assertThat((List<?>) model.get("countries")).isNotEmpty();
    }

    @Test
    void storeAdminSavesTheirOwnStoreAndSeesTheSuccessMessage() {
        // given
        logInAs("ADMIN", "store-1");
        Store store = store("store-1");
        when(messageSource.getMessage(eq("store.company.details.update.success"), any(), eq(POLISH))).thenReturn("Zapisano");
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = controller.updateStoreCompanyDetails(validForm(), null, new ExtendedModelMap(), POLISH, redirect, new MockHttpServletResponse());

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/store/company-details");
        assertThat(redirect.getFlashAttributes().get("successMessage")).isEqualTo("Zapisano");
        verify(storesRepository).save(store);
        assertThat(store.getBillingDetails().getEmail()).isEqualTo("biuro@demo.pl");
        assertThat(store.getBillingDetails().isProperlyFilled()).isTrue();
    }

    @Test
    void invalidDetailsAreNotSavedAndTheFormComesBackWithErrors() {
        // given
        logInAs("ADMIN", "store-1");
        Store store = store("store-1");
        CompanyDetailsForm form = validForm();
        form.setEmail("");
        form.setTaxId(" ");
        ExtendedModelMap model = new ExtendedModelMap();
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = controller.updateStoreCompanyDetails(form, null, model, POLISH, redirect, new MockHttpServletResponse());

        // then
        assertThat(view).isEqualTo("store-company-details");
        assertThat(errors(model)).containsOnlyKeys("taxId", "email");
        assertThat(model.get("form")).isSameAs(form);
        assertThat(redirect.getFlashAttributes()).doesNotContainKey("successMessage");
        verify(storesRepository, never()).save(any());
        assertThat(store.getBillingDetails()).isNull();
    }

    @Test
    void storeAdminAlwaysWritesToTheStoreFromTheirSession() {
        // given
        logInAs("ADMIN", "store-1");
        Store own = store("store-1");
        Store other = store("store-2");

        // when
        controller.updateStoreCompanyDetails(validForm(), null, new ExtendedModelMap(), POLISH, new RedirectAttributesModelMap(), new MockHttpServletResponse());

        // then
        ArgumentCaptor<Store> saved = ArgumentCaptor.forClass(Store.class);
        verify(storesRepository).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(own);
        assertThat(other.getBillingDetails()).isNull();
    }

    @Test
    void superAdminSavesTheStoreFromThePathAndReturnsToItsPage() {
        // given
        logInAs("SUPER_ADMIN", "admin-store");
        Store store = store("store-2");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.superAdminUpdateStoreCompanyDetails("store-2", validForm(), null, model, POLISH,
                new RedirectAttributesModelMap(), new MockHttpServletResponse());

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/store/store-2/company-details");
        verify(storesRepository).save(store);
    }

    @Test
    void superAdminFormPostsBackToTheStorePath() {
        // given
        logInAs("SUPER_ADMIN", "admin-store");
        store("store-2");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        controller.superAdminStoreCompanyDetails("store-2", model, POLISH);

        // then
        assertThat(model.get("formAction")).isEqualTo("/dashboard/store/store-2/company-details");
    }

    @Test
    void savingCompanyDetailsKeepsTheStoreShippingAddresses() {
        // given
        logInAs("ADMIN", "store-1");
        Store store = store("store-1");
        ShippingDetails address = new ShippingDetails();
        address.setStreetAndNumber("ul. Magazynowa 5");
        store.setShippingDetails(new ArrayList<>(List.of(address)));

        // when
        controller.updateStoreCompanyDetails(validForm(), null, new ExtendedModelMap(), POLISH, new RedirectAttributesModelMap(), new MockHttpServletResponse());

        // then
        verify(storesRepository).save(store);
        assertThat(store.getShippingDetails()).containsExactly(address);
    }

    @Test
    void asyncSaveReturnsTheFormWithTheSavedValuesAndTheSuccessMessageInsteadOfARedirect() {
        // given
        logInAs("ADMIN", "store-1");
        Store store = store("store-1");
        when(messageSource.getMessage(eq("store.company.details.update.success"), any(), eq(POLISH))).thenReturn("Zapisano");
        CompanyDetailsForm form = validForm();
        form.setCity("  Kraków ");
        ExtendedModelMap model = new ExtendedModelMap();
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = controller.updateStoreCompanyDetails(form, "fetch", model, POLISH, redirect, response);

        // then
        assertThat(view).isEqualTo("store-company-details :: companyDetailsForm");
        assertThat(response.getStatus()).isEqualTo(200);
        verify(storesRepository).save(store);
        assertThat(model.get("savedMessage")).isEqualTo("Zapisano");
        assertThat(((CompanyDetailsForm) model.get("form")).getCity()).isEqualTo("Kraków");
        assertThat(errors(model)).isEmpty();
        assertThat(redirect.getFlashAttributes()).isEmpty();
    }

    @Test
    void asyncSaveWithInvalidDetailsAnswersUnprocessableWithTheFormAndItsErrors() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1");
        CompanyDetailsForm form = validForm();
        form.setEmail("");
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = controller.updateStoreCompanyDetails(form, "fetch", model, POLISH, new RedirectAttributesModelMap(), response);

        // then
        assertThat(view).isEqualTo("store-company-details :: companyDetailsForm");
        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(errors(model)).containsOnlyKeys("email");
        assertThat(model.get("savedMessage")).isNull();
        verify(storesRepository, never()).save(any());
    }
}
