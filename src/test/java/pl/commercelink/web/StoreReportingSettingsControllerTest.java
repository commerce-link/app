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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.financials.GoogleOfflineConversionsExport;
import pl.commercelink.stores.ReportingConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.ReportingForm;
import pl.commercelink.web.settings.ConfirmAction;
import pl.commercelink.web.settings.SettingsFlash;

import java.util.Locale;

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
class StoreReportingSettingsControllerTest {

    private static final Locale POLISH = Locale.forLanguageTag("pl");
    private static final String TOKEN = "74f99509-1111-2222-3333-444455556666";

    @Mock
    private StoresRepository storesRepository;

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private StoreReportingSettingsController controller;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "apiDomain", "https://api.example.com");
        when(messageSource.getMessage(anyString(), any(), eq(POLISH))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Store store(String storeId, boolean enabled, String token) {
        Store store = new Store();
        store.setStoreId(storeId);
        ReportingConfiguration configuration = new ReportingConfiguration();
        configuration.setGoogleAdsEnabled(enabled);
        configuration.setGoogleAdsToken(token);
        store.setReportingConfiguration(configuration);
        when(storesRepository.findById(storeId)).thenReturn(store);
        return store;
    }

    private ReportingForm form(boolean enabled) {
        ReportingForm form = new ReportingForm();
        form.setGoogleAdsEnabled(enabled);
        return form;
    }

    private Store savedStore() {
        ArgumentCaptor<Store> saved = ArgumentCaptor.forClass(Store.class);
        verify(storesRepository).save(saved.capture());
        return saved.getValue();
    }

    @Test
    void showsTheConversionsAddressMaskedWithTheFullValueForCopying() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1", true, TOKEN);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.report(model);

        // then
        assertThat(view).isEqualTo("store-report");
        StoreReportingSettingsController.ConversionsAddress address =
                (StoreReportingSettingsController.ConversionsAddress) model.get("conversionsAddress");
        assertThat(address.url()).isEqualTo("https://api.example.com/Store/store-1/Reporting/Google/Conversions/" + TOKEN);
        assertThat(address.masked()).startsWith("https://api.example.com/Store/store-1/Reporting/Google/Conversions/")
                .endsWith("56666").doesNotContain("74f99509");
        assertThat(model.get("newAddressHref")).isEqualTo("/dashboard/store/report/google-ads/new-address");
    }

    @Test
    void aSwitchedOffStoreShowsNoAddressEvenWithAToken() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1", false, TOKEN);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        controller.report(model);

        // then
        assertThat(model.get("conversionsAddress")).isNull();
    }

    @Test
    void aStoreWithoutATokenHasNoAddressToShowYet() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1", false, null);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        controller.report(model);

        // then
        assertThat(model.get("conversionsAddress")).isNull();
    }

    @Test
    void setupStepsNameTheConversionExactlyAsTheFileSendsIt() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1", true, TOKEN);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        controller.report(model);

        // then
        assertThat(model.get("conversionName")).isEqualTo(GoogleOfflineConversionsExport.CONVERSION_NAME);
        assertThat(model.get("reportsHref")).isEqualTo("/dashboard/reports");
    }

    @Test
    void superAdminGetsNoLinkToReportsBecauseReportsFollowTheirOwnSession() {
        // given
        logInAs("SUPER_ADMIN", "none");
        store("store-9", true, TOKEN);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        controller.superAdminReport("store-9", model);

        // then
        assertThat(model.get("conversionName")).isEqualTo(GoogleOfflineConversionsExport.CONVERSION_NAME);
        assertThat(model.containsAttribute("reportsHref")).isFalse();
    }

    @Test
    void savingAgainKeepsTheAddressPastedIntoGoogleAds() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1", true, TOKEN);

        // when
        controller.saveReport(form(true), null, new ExtendedModelMap(), POLISH, new RedirectAttributesModelMap(), new MockHttpServletResponse());

        // then
        assertThat(savedStore().getReportingConfiguration().getGoogleAdsToken()).isEqualTo(TOKEN);
    }

    @Test
    void switchingGoogleAdsOnForTheFirstTimeCreatesTheAddress() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1", false, null);

        // when
        controller.saveReport(form(true), null, new ExtendedModelMap(), POLISH, new RedirectAttributesModelMap(), new MockHttpServletResponse());

        // then
        ReportingConfiguration saved = savedStore().getReportingConfiguration();
        assertThat(saved.isGoogleAdsEnabled()).isTrue();
        assertThat(saved.getGoogleAdsToken()).isNotBlank();
    }

    @Test
    void switchingGoogleAdsOffKeepsTheAddressForLater() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1", true, TOKEN);

        // when
        controller.saveReport(form(false), null, new ExtendedModelMap(), POLISH, new RedirectAttributesModelMap(), new MockHttpServletResponse());

        // then
        ReportingConfiguration saved = savedStore().getReportingConfiguration();
        assertThat(saved.isGoogleAdsEnabled()).isFalse();
        assertThat(saved.getGoogleAdsToken()).isEqualTo(TOKEN);
    }

    @Test
    void storeAdminAlwaysSavesTheStoreFromTheirSession() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1", false, null);
        store("other-store", false, null);

        // when
        String view = controller.saveReport(form(true), null, new ExtendedModelMap(), POLISH, new RedirectAttributesModelMap(),
                new MockHttpServletResponse());

        // then
        assertThat(savedStore().getStoreId()).isEqualTo("store-1");
        verify(storesRepository, never()).findById("other-store");
        assertThat(view).isEqualTo("redirect:/dashboard/store/report");
    }

    @Test
    void superAdminSavesTheStoreFromThePath() {
        // given
        logInAs("SUPER_ADMIN", "none");
        store("store-9", false, null);

        // when
        String view = controller.superAdminSaveReport("store-9", form(true), null, new ExtendedModelMap(), POLISH,
                new RedirectAttributesModelMap(), new MockHttpServletResponse());

        // then
        assertThat(savedStore().getStoreId()).isEqualTo("store-9");
        assertThat(view).isEqualTo("redirect:/dashboard/store/store-9/report");
    }

    @Test
    void asyncSaveAnswersWithTheFormShowingTheNewAddress() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1", false, null);
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = controller.saveReport(form(true), "fetch", model, POLISH, new RedirectAttributesModelMap(), response);

        // then
        assertThat(view).isEqualTo("store-report :: reportingForm");
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(model.get("savedMessage")).isEqualTo("store.reporting.googleAds.switchedOn");
        assertThat(model.get("conversionsAddress")).isNotNull();
        assertThat(model.get("setupOpen")).isEqualTo(true);
    }

    @Test
    void switchingOnAgainKeepsTheInstructionsCollapsedBecauseTheAddressWasAlreadyPasted() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1", false, TOKEN);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        controller.saveReport(form(true), "fetch", model, POLISH, new RedirectAttributesModelMap(), new MockHttpServletResponse());

        // then
        assertThat(model.get("setupOpen")).isEqualTo(false);
        assertThat(model.get("conversionsAddress")).isNotNull();
    }

    @Test
    void switchingOffSaysGoogleAdsStopsDownloadingAndHidesTheAddress() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1", true, TOKEN);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        controller.saveReport(form(false), "fetch", model, POLISH, new RedirectAttributesModelMap(), new MockHttpServletResponse());

        // then
        assertThat(model.get("savedMessage")).isEqualTo("store.reporting.googleAds.switchedOff");
        assertThat(model.get("conversionsAddress")).isNull();
    }

    @Test
    void withoutJavaScriptTheFirstSwitchOnOpensTheInstructionsAfterTheRedirect() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1", false, null);
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        controller.saveReport(form(true), null, new ExtendedModelMap(), POLISH, redirectAttributes, new MockHttpServletResponse());

        // then
        assertThat(redirectAttributes.getFlashAttributes().get("googleAdsSetupOpen")).isEqualTo(true);
        assertThat(redirectAttributes.getFlashAttributes().get(SettingsFlash.SAVED_MESSAGE)).isEqualTo("store.reporting.googleAds.switchedOn");
    }

    @Test
    void theRedirectedPageOpensTheInstructionsOnlyWhenAsked() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1", true, TOKEN);
        ExtendedModelMap opened = new ExtendedModelMap();
        opened.addAttribute("googleAdsSetupOpen", true);
        ExtendedModelMap plain = new ExtendedModelMap();

        // when
        controller.report(opened);
        controller.report(plain);

        // then
        assertThat(opened.get("setupOpen")).isEqualTo(true);
        assertThat(plain.get("setupOpen")).isEqualTo(false);
    }

    @Test
    void askingForANewAddressWithoutJavaScriptShowsAConfirmationPage() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1", true, TOKEN);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.confirmNewAddress(model, POLISH);

        // then
        assertThat(view).isEqualTo("settings-confirm");
        ConfirmAction confirm = (ConfirmAction) model.get("confirm");
        assertThat(confirm.actionPath()).isEqualTo("/dashboard/store/report/google-ads/new-address");
        assertThat(confirm.cancelPath()).isEqualTo("/dashboard/store/report");
    }

    @Test
    void aNewAddressReplacesTheTokenAndSaysSo() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1", true, TOKEN);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = controller.newAddress(null, new ExtendedModelMap(), POLISH, redirect);

        // then
        assertThat(savedStore().getReportingConfiguration().getGoogleAdsToken()).isNotBlank().isNotEqualTo(TOKEN);
        assertThat(view).isEqualTo("redirect:/dashboard/store/report");
        assertThat(redirect.getFlashAttributes().get("settingsSavedMessage")).isEqualTo("store.reporting.googleAds.newAddress.success");
    }

    @Test
    void aNewAddressIsNotCreatedForAStoreThatNeverHadOne() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1", false, null);

        // when
        String view = controller.newAddress(null, new ExtendedModelMap(), POLISH, new RedirectAttributesModelMap());

        // then
        verify(storesRepository, never()).save(any());
        assertThat(view).isEqualTo("redirect:/dashboard/store/report");
    }

    @Test
    void aNewAddressWithJavaScriptAnswersWithTheCardShowingIt() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1", true, TOKEN);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.newAddress("fetch", model, POLISH, new RedirectAttributesModelMap());

        // then
        String newToken = savedStore().getReportingConfiguration().getGoogleAdsToken();
        assertThat(newToken).isNotBlank().isNotEqualTo(TOKEN);
        assertThat(view).isEqualTo("store-report :: reportingForm");
        assertThat(model.get("savedMessage")).isEqualTo("store.reporting.googleAds.newAddress.success");
        StoreReportingSettingsController.ConversionsAddress address =
                (StoreReportingSettingsController.ConversionsAddress) model.get("conversionsAddress");
        assertThat(address.url()).endsWith("/Conversions/" + newToken);
        assertThat(model.get("setupOpen")).isEqualTo(false);
    }

    @Test
    void superAdminGetsANewAddressForThePathStoreWithoutReload() {
        // given
        logInAs("SUPER_ADMIN", "none");
        store("store-9", true, TOKEN);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.superAdminNewAddress("store-9", "fetch", model, POLISH, new RedirectAttributesModelMap());

        // then
        assertThat(savedStore().getStoreId()).isEqualTo("store-9");
        assertThat(view).isEqualTo("store-report :: reportingForm");
        assertThat(model.get("formAction")).isEqualTo("/dashboard/store/store-9/report");
    }

    @Test
    void anUnknownStoreIsNotFound() {
        // when / then
        assertThatThrownBy(() -> controller.superAdminReport("missing", new ExtendedModelMap()))
                .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND));
    }

    /** A tab opened before this release posts store.reportingConfiguration.googleAdsEnabled, which binds to nothing here. */
    @Test
    void aSubmissionWithoutTheSwitchChangesNothingAndSaysNothing() {
        // given
        logInAs("ADMIN", "store-1");
        Store store = new Store();
        store.setStoreId("store-1");
        store.setReportingConfiguration(new pl.commercelink.stores.ReportingConfiguration());
        store.getReportingConfiguration().enableGoogleAds();
        when(storesRepository.findById("store-1")).thenReturn(store);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = controller.saveReport(new ReportingForm(), null, new ExtendedModelMap(), Locale.forLanguageTag("pl"),
                redirect, new MockHttpServletResponse());

        // then
        verify(storesRepository, never()).save(any(Store.class));
        assertThat(store.getReportingConfiguration().isGoogleAdsEnabled()).isTrue();
        assertThat(view).isEqualTo("redirect:/dashboard/store/report");
        assertThat(redirect.getFlashAttributes()).isEmpty();
    }
}
