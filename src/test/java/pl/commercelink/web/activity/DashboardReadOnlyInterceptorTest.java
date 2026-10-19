package pl.commercelink.web.activity;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import pl.commercelink.starter.security.model.CustomUser;
import pl.commercelink.stores.DeactivationReason;
import pl.commercelink.stores.DeactivationStatus;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreActivity;
import pl.commercelink.stores.StoreTrialService;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.stores.TrialStatus;
import pl.commercelink.testsupport.SecurityContextLogin;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DashboardReadOnlyInterceptorTest {

    private static final String STORE_ID = "abc123def4";
    private static final String REFUSED = "Sklep jest nieaktywny.";
    private static final String PAGE = "https://app.commercelink.pl/dashboard/store/branding?tab=logo";
    private static final String HTML = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";
    private static final DeactivationStatus INACTIVE = new DeactivationStatus(DeactivationReason.TRIAL_ENDED,
            LocalDate.parse("2026-10-12"), LocalDate.parse("2026-10-26"), 6);

    @Mock private StoresRepository storesRepository;
    @Mock private StoreTrialService storeTrialService;
    @Mock private StoreActivity storeActivity;
    @Mock private MessageSource messageSource;

    private final Store store = new Store();
    private MockMvc mvc;

    @Controller
    static class StubDashboardController {

        @GetMapping("/dashboard/orders")
        @ResponseBody
        String orders() {
            return "orders";
        }

        @PostMapping("/dashboard/store/branding")
        @ResponseBody
        String saveBranding() {
            return "saved";
        }
    }

    @BeforeEach
    void setUp() {
        store.setStoreId(STORE_ID);
        DashboardReadOnlyInterceptor interceptor = new DashboardReadOnlyInterceptor(storesRepository, storeTrialService,
                storeActivity, messageSource, "/dashboard");
        mvc = MockMvcBuilders.standaloneSetup(new StubDashboardController())
                .addMappedInterceptors(new String[]{"/dashboard/**"}, interceptor)
                .build();
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private void signedInToTheStore() {
        SecurityContextLogin.logInAs("ADMIN", STORE_ID);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
    }

    private void storeIsInactive() {
        signedInToTheStore();
        when(storeActivity.status(store)).thenReturn(Optional.of(INACTIVE));
    }

    private void refusalIsExplained() {
        when(messageSource.getMessage(eq(DashboardReadOnlyInterceptor.REFUSED_MESSAGE_KEY), isNull(), any(Locale.class)))
                .thenReturn(REFUSED);
    }

    @Test
    void superAdminIsNeverHeldBack() throws Exception {
        // given
        SecurityContextLogin.logInAs("SUPER_ADMIN", STORE_ID);

        // when / then
        mvc.perform(post("/dashboard/store/branding")).andExpect(status().isOk()).andExpect(content().string("saved"));
        verifyNoInteractions(storesRepository, storeActivity);
    }

    @Test
    void userWithoutStoreIsNotHeldBack() throws Exception {
        // given
        CustomUser user = new CustomUser(null, null, Map.of("role", "ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));

        // when / then
        mvc.perform(post("/dashboard/store/branding")).andExpect(status().isOk());
        verifyNoInteractions(storesRepository);
    }

    @Test
    void activeStoreSavesAndLeavesItsTrialForTheAccountStatus() throws Exception {
        // given
        signedInToTheStore();
        TrialStatus trial = new TrialStatus(LocalDate.parse("2026-10-12"), 5, false);
        when(storeTrialService.status(store)).thenReturn(Optional.of(trial));

        // when / then
        mvc.perform(post("/dashboard/store/branding"))
                .andExpect(status().isOk())
                .andExpect(content().string("saved"))
                .andExpect(request().attribute(DashboardReadOnlyInterceptor.TRIAL_STATUS_ATTRIBUTE, trial))
                .andExpect(request().attribute(DashboardReadOnlyInterceptor.DEACTIVATION_STATUS_ATTRIBUTE, (Object) null))
                .andExpect(request().attribute(DashboardReadOnlyInterceptor.STORE_ID_ATTRIBUTE, STORE_ID));
    }

    @Test
    void inactiveStoreStillOpensEveryPage() throws Exception {
        // given
        storeIsInactive();

        // when / then
        mvc.perform(get("/dashboard/orders"))
                .andExpect(status().isOk())
                .andExpect(content().string("orders"))
                .andExpect(request().attribute(DashboardReadOnlyInterceptor.DEACTIVATION_STATUS_ATTRIBUTE, INACTIVE))
                .andExpect(request().attribute(DashboardReadOnlyInterceptor.TRIAL_STATUS_ATTRIBUTE, (Object) null))
                .andExpect(request().attribute(DashboardReadOnlyInterceptor.STORE_ID_ATTRIBUTE, STORE_ID));
        verify(storeTrialService, never()).status(any());
    }

    @Test
    void inactiveStoreRefusesFormSaveAndSendsTheUserBackWithTheReason() throws Exception {
        // given
        storeIsInactive();
        refusalIsExplained();

        // when / then
        mvc.perform(post("/dashboard/store/branding").header("Accept", HTML).header("Referer", PAGE))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/dashboard/store/branding?tab=logo"))
                .andExpect(flash().attribute("errorMessage", REFUSED));
    }

    @Test
    void refusedSaveFromOutsideTheDashboardGoesToTheDashboardHome() throws Exception {
        // given
        storeIsInactive();
        refusalIsExplained();

        // when / then
        mvc.perform(post("/dashboard/store/branding").header("Accept", HTML)
                        .header("Referer", "https://phishing.example/dashboard-login"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/dashboard"));
        mvc.perform(post("/dashboard/store/branding").header("Accept", HTML))
                .andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    void asyncFormSaveIsRedirectedSoItsRegularSubmitShowsTheReason() throws Exception {
        // given
        storeIsInactive();

        // when / then
        mvc.perform(post("/dashboard/store/branding").header("X-Requested-With", "fetch").header("Referer", PAGE))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/dashboard/store/branding?tab=logo"))
                .andExpect(flash().attributeCount(0));
    }

    @Test
    void scriptCallIsRefusedWithForbidden() throws Exception {
        // given
        storeIsInactive();
        refusalIsExplained();

        // when / then
        mvc.perform(post("/dashboard/store/branding").header("Accept", "application/json"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(REFUSED));
    }
}
