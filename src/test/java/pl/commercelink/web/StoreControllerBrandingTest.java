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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.starter.security.model.CustomUser;
import pl.commercelink.stores.Branding;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.BrandingForm;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreControllerBrandingTest {

    private static final Locale POLISH = Locale.forLanguageTag("pl");
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13};

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
        store.setName("Old name");
        when(storesRepository.findById(storeId)).thenReturn(store);
        return store;
    }

    private Store storeWithLogo(String storeId) {
        Store store = store(storeId);
        Branding branding = new Branding();
        branding.setLogo(storeId + "/logo.png");
        store.setBranding(branding);
        return store;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> errors(ExtendedModelMap model) {
        return (Map<String, String>) model.get("errors");
    }

    private BrandingForm validForm() {
        BrandingForm form = new BrandingForm();
        form.setStoreName("Demo Store");
        form.setPrimaryColor("#1B4DB1");
        return form;
    }

    private String save(BrandingForm form) {
        return controller.updateStoreBranding(form, null, new ExtendedModelMap(), POLISH, new RedirectAttributesModelMap(),
                new MockHttpServletResponse());
    }

    @Test
    void rendersTheStoredBrandingWithTheLogoAndNoErrors() {
        // given
        logInAs("ADMIN", "store-1");
        Store store = storeWithLogo("store-1");
        store.getBranding().setPrimaryColor("#1b4db1");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.storeBranding(model);

        // then
        assertThat(view).isEqualTo("store-branding");
        assertThat(((BrandingForm) model.get("form")).getStoreName()).isEqualTo("Old name");
        assertThat(model.get("formAction")).isEqualTo("/dashboard/store/branding");
        assertThat(model.get("hasLogo")).isEqualTo(true);
        assertThat((String) model.get("logoUrl")).startsWith("/StoreLogo/store-1");
        assertThat(model.get("logoMaxBytes")).isEqualTo(BrandingForm.LOGO_MAX_BYTES);
        assertThat(errors(model)).isEmpty();
    }

    @Test
    void theLogoKeepsItsAddressWhenASaveDoesNotReplaceIt() {
        // given
        logInAs("ADMIN", "store-1");
        storeWithLogo("store-1").getBranding().setLogoVersion(42L);
        ExtendedModelMap page = new ExtendedModelMap();
        ExtendedModelMap saved = new ExtendedModelMap();

        // when
        controller.storeBranding(page);
        controller.updateStoreBranding(validForm(), "fetch", saved, POLISH, new RedirectAttributesModelMap(), new MockHttpServletResponse());

        // then
        // A changing address makes the browser fetch the image again, so the preview blinks on every save.
        assertThat(page.get("logoUrl")).isEqualTo("/StoreLogo/store-1?v=42");
        assertThat(saved.get("logoUrl")).isEqualTo("/StoreLogo/store-1?v=42");
    }

    @Test
    void aNewLogoGetsANewAddressSoThePreviewShowsIt() {
        // given
        logInAs("ADMIN", "store-1");
        Store store = storeWithLogo("store-1");
        store.getBranding().setLogoVersion(42L);
        when(storesRepository.storeLogo("store-1", "logo.png", PNG)).thenReturn("store-1/logo.png");
        BrandingForm form = validForm();
        form.setLogoFile(new MockMultipartFile("logoFile", "logo.png", "image/png", PNG));
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        controller.updateStoreBranding(form, "fetch", model, POLISH, new RedirectAttributesModelMap(), new MockHttpServletResponse());

        // then
        assertThat(store.getBranding().getLogoVersion()).isNotNull().isNotEqualTo(42L);
        assertThat(model.get("logoUrl")).isEqualTo("/StoreLogo/store-1?v=" + store.getBranding().getLogoVersion());
    }

    @Test
    void aLogoStoredBeforeVersionsExistedIsAddressedWithoutAVersion() {
        // given
        logInAs("ADMIN", "store-1");
        storeWithLogo("store-1");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        controller.storeBranding(model);

        // then
        assertThat(model.get("logoUrl")).isEqualTo("/StoreLogo/store-1");
    }

    @Test
    void storeWithoutBrandingRendersWithoutALogo() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        controller.storeBranding(model);

        // then
        assertThat(model.get("hasLogo")).isEqualTo(false);
    }

    @Test
    void storeAdminSavesTheirOwnStoreAndSeesTheSuccessMessage() {
        // given
        logInAs("ADMIN", "store-1");
        Store store = store("store-1");
        when(messageSource.getMessage(eq("store.branding.update.success"), any(), eq(POLISH))).thenReturn("Zapisano");
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = controller.updateStoreBranding(validForm(), null, new ExtendedModelMap(), POLISH, redirect, new MockHttpServletResponse());

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/store/branding");
        assertThat(redirect.getFlashAttributes().get("successMessage")).isEqualTo("Zapisano");
        verify(storesRepository).save(store);
        assertThat(store.getName()).isEqualTo("Demo Store");
        assertThat(store.getBranding().getPrimaryColor()).isEqualTo("#1b4db1");
    }

    @Test
    void storeAdminAlwaysWritesToTheStoreFromTheirSession() {
        // given
        logInAs("ADMIN", "store-1");
        Store own = store("store-1");
        Store other = store("store-2");

        // when
        save(validForm());

        // then
        ArgumentCaptor<Store> saved = ArgumentCaptor.forClass(Store.class);
        verify(storesRepository).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(own);
        assertThat(other.getName()).isEqualTo("Old name");
        assertThat(other.getBranding()).isNull();
    }

    @Test
    void superAdminSavesTheStoreFromThePathAndReturnsToItsPage() {
        // given
        logInAs("SUPER_ADMIN", "admin-store");
        Store store = store("store-2");

        // when
        String view = controller.superAdminUpdateStoreBranding("store-2", validForm(), null, new ExtendedModelMap(), POLISH,
                new RedirectAttributesModelMap(), new MockHttpServletResponse());

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/store/store-2/branding");
        verify(storesRepository).save(store);
    }

    @Test
    void superAdminFormPostsBackToTheStorePath() {
        // given
        logInAs("SUPER_ADMIN", "admin-store");
        store("store-2");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        controller.superAdminStoreBranding("store-2", model);

        // then
        assertThat(model.get("formAction")).isEqualTo("/dashboard/store/store-2/branding");
    }

    @Test
    void invalidBrandingIsNotSavedAndTheFormComesBackWithErrors() {
        // given
        logInAs("ADMIN", "store-1");
        Store store = store("store-1");
        BrandingForm form = validForm();
        form.setStoreName("");
        form.setPrimaryColor("red;background:url(https://example.com)");
        ExtendedModelMap model = new ExtendedModelMap();
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = controller.updateStoreBranding(form, null, model, POLISH, redirect, new MockHttpServletResponse());

        // then
        assertThat(view).isEqualTo("store-branding");
        assertThat(errors(model)).containsOnlyKeys("storeName", "primaryColor");
        assertThat(model.get("form")).isSameAs(form);
        assertThat(redirect.getFlashAttributes()).doesNotContainKey("successMessage");
        verify(storesRepository, never()).save(any());
        assertThat(store.getName()).isEqualTo("Old name");
    }

    @Test
    void aValidLogoIsNotStoredWhenAnotherFieldHasAnError() {
        // given
        logInAs("ADMIN", "store-1");
        storeWithLogo("store-1");
        BrandingForm form = validForm();
        form.setPrimaryColor("blue");
        form.setLogoFile(new MockMultipartFile("logoFile", "logo.png", "image/png", PNG));

        // when
        save(form);

        // then
        verify(storesRepository, never()).storeLogo(anyString(), anyString(), any());
        verify(storesRepository, never()).removeLogo(anyString());
    }

    @Test
    void aFileThatIsNotAnImageLeavesTheCurrentLogoInPlace() {
        // given
        logInAs("ADMIN", "store-1");
        Store store = storeWithLogo("store-1");
        BrandingForm form = validForm();
        form.setLogoFile(new MockMultipartFile("logoFile", "logo.png", "image/png", "hello".getBytes()));
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        controller.updateStoreBranding(form, null, model, POLISH, new RedirectAttributesModelMap(), new MockHttpServletResponse());

        // then
        assertThat(errors(model)).containsOnlyKeys("logoFile");
        verify(storesRepository, never()).storeLogo(anyString(), anyString(), any());
        verify(storesRepository, never()).removeLogo(anyString());
        verify(storesRepository, never()).save(any());
        assertThat(store.getBranding().getLogo()).isEqualTo("store-1/logo.png");
    }

    @Test
    void storesANewLogoUnderTheExtensionOfItsActualFormat() {
        // given
        logInAs("ADMIN", "store-1");
        Store store = store("store-1");
        when(storesRepository.storeLogo("store-1", "logo.png", PNG)).thenReturn("store-1/logo.png");
        BrandingForm form = validForm();
        form.setLogoFile(new MockMultipartFile("logoFile", "my-logo.gif", "image/gif", PNG));

        // when
        save(form);

        // then
        verify(storesRepository).storeLogo("store-1", "logo.png", PNG);
        assertThat(store.getBranding().getLogo()).isEqualTo("store-1/logo.png");
        verify(storesRepository).save(store);
    }

    @Test
    void removesTheLogoWhenAskedAndNoNewFileIsChosen() {
        // given
        logInAs("ADMIN", "store-1");
        Store store = storeWithLogo("store-1");
        BrandingForm form = validForm();
        form.setRemoveLogo(true);

        // when
        save(form);

        // then
        verify(storesRepository).removeLogo("store-1");
        assertThat(store.getBranding().getLogo()).isNull();
        verify(storesRepository).save(store);
    }

    @Test
    void aNewFileWinsOverTheRemoveLogoCheckbox() {
        // given
        logInAs("ADMIN", "store-1");
        Store store = storeWithLogo("store-1");
        when(storesRepository.storeLogo("store-1", "logo.png", PNG)).thenReturn("store-1/logo.png");
        BrandingForm form = validForm();
        form.setRemoveLogo(true);
        form.setLogoFile(new MockMultipartFile("logoFile", "logo.png", "image/png", PNG));

        // when
        save(form);

        // then
        verify(storesRepository, never()).removeLogo(anyString());
        assertThat(store.getBranding().getLogo()).isEqualTo("store-1/logo.png");
    }

    @Test
    void asyncSaveReturnsTheFormWithTheSavedValuesAndTheSuccessMessageInsteadOfARedirect() {
        // given
        logInAs("ADMIN", "store-1");
        Store store = store("store-1");
        when(messageSource.getMessage(eq("store.branding.update.success"), any(), eq(POLISH))).thenReturn("Zapisano");
        BrandingForm form = validForm();
        form.setStoreName("  Demo Store ");
        ExtendedModelMap model = new ExtendedModelMap();
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = controller.updateStoreBranding(form, "fetch", model, POLISH, redirect, response);

        // then
        assertThat(view).isEqualTo("store-branding :: brandingForm");
        assertThat(response.getStatus()).isEqualTo(200);
        verify(storesRepository).save(store);
        assertThat(model.get("savedMessage")).isEqualTo("Zapisano");
        assertThat(((BrandingForm) model.get("form")).getStoreName()).isEqualTo("Demo Store");
        assertThat(((BrandingForm) model.get("form")).getPrimaryColor()).isEqualTo("#1b4db1");
        assertThat(errors(model)).isEmpty();
        assertThat(redirect.getFlashAttributes()).isEmpty();
    }

    @Test
    void asyncSaveWithInvalidBrandingAnswersUnprocessableWithTheFormAndItsErrors() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1");
        BrandingForm form = validForm();
        form.setPrimaryColor("nope");
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = controller.updateStoreBranding(form, "fetch", model, POLISH, new RedirectAttributesModelMap(), response);

        // then
        assertThat(view).isEqualTo("store-branding :: brandingForm");
        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(errors(model)).containsOnlyKeys("primaryColor");
        assertThat(model.get("savedMessage")).isNull();
        verify(storesRepository, never()).save(any());
    }
}
