package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import pl.commercelink.stores.DeactivationReason;
import pl.commercelink.stores.DeactivationStatus;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.TrialPeriod;
import pl.commercelink.stores.TrialStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TrialTemplatesRenderingTest {

    private static final String STORE_ID = "abc123def4";

    private final TemplateEngine engine = EnglishFragmentTemplateEngine.create();

    private static Store trialStore() {
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setName("My store");
        store.setCreatedAt("2026-09-28T10:00:00Z");
        store.setTrial(new TrialPeriod("owner@example.com", "2026-09-28T10:00:00Z", "2026-10-12T10:00:00Z"));
        return store;
    }

    private static Context storesList(Store store, Map<String, TrialStatus> trials) {
        return storesList(store, trials, Map.of());
    }

    private static Context storesList(Store store, Map<String, TrialStatus> trials,
                                      Map<String, DeactivationStatus> deactivations) {
        Context context = new Context(Locale.ENGLISH);
        context.setVariable("stores", List.of(store));
        context.setVariable("trials", trials);
        context.setVariable("deactivations", deactivations);
        context.setVariable("dir", "desc");
        return context;
    }

    private static Context registration(boolean demoMode) {
        Context context = new Context(Locale.ENGLISH);
        context.setVariable("demoMode", demoMode);
        context.setVariable("ttlDays", 7);
        context.setVariable("trialDays", 14);
        context.setVariable("trialRetentionDays", 14);
        context.setVariable("email", "owner@example.com");
        return context;
    }

    @Test
    void storesListShowsTrialEndAndConversion() {
        // given
        Context context = storesList(trialStore(),
                Map.of(STORE_ID, new TrialStatus(LocalDate.parse("2026-10-12"), 14, false)));

        // when
        String html = engine.process("stores", context);

        // then
        assertThat(html).contains(">Trial<");
        assertThat(html).contains("Ends: 2026-10-12");
        assertThat(html).contains("action=\"/dashboard/store/" + STORE_ID + "/trial/convert\"");
        assertThat(html).contains("the owner account owner@example.com");
    }

    @Test
    void storesListMarksEndedTrial() {
        // given
        Context context = storesList(trialStore(),
                Map.of(STORE_ID, new TrialStatus(LocalDate.parse("2026-10-12"), 0, true)));

        // when
        String html = engine.process("stores", context);

        // then
        assertThat(html).contains("Ended: 2026-10-12");
        assertThat(html).contains("is-danger");
    }

    @Test
    void storesListOffersNoConversionForFullAccount() {
        // given
        Store store = trialStore();
        store.setTrial(null);

        // when
        String html = engine.process("stores", storesList(store, Map.of()));

        // then
        assertThat(html).doesNotContain("/trial/convert");
        assertThat(html).contains("This is a production store.");
    }

    @Test
    void storesListOffersDeactivationOfActiveStore() {
        // when
        String html = engine.process("stores", storesList(trialStore(), Map.of()));

        // then
        assertThat(html).contains("action=\"/dashboard/store/" + STORE_ID + "/deactivate\"");
        assertThat(html).doesNotContain("/activate\"");
        assertThat(html).doesNotContain(">Inactive<");
    }

    @Test
    void storesListMarksStoreSwitchedOffByHandAndOffersActivation() {
        // given
        Store store = trialStore();
        store.setTrial(null);
        Map<String, DeactivationStatus> deactivations = Map.of(STORE_ID,
                new DeactivationStatus(DeactivationReason.MANUAL, LocalDate.parse("2026-10-18"), null, 0));

        // when
        String html = engine.process("stores", storesList(store, Map.of(), deactivations));

        // then
        assertThat(html).contains(">Inactive<");
        assertThat(html).doesNotContain("Deletion:");
        assertThat(html).contains("action=\"/dashboard/store/" + STORE_ID + "/activate\"");
        assertThat(html).doesNotContain("/deactivate\"");
    }

    @Test
    void storesListShowsWhenEndedTrialIsDeletedAndOffersOnlyConversion() {
        // given
        Map<String, DeactivationStatus> deactivations = Map.of(STORE_ID, new DeactivationStatus(
                DeactivationReason.TRIAL_ENDED, LocalDate.parse("2026-10-12"), LocalDate.parse("2026-10-26"), 6));
        Map<String, TrialStatus> trials = Map.of(STORE_ID, new TrialStatus(LocalDate.parse("2026-10-12"), 0, true));

        // when
        String html = engine.process("stores", storesList(trialStore(), trials, deactivations));

        // then
        assertThat(html).contains(">Inactive<");
        assertThat(html).contains("Deletion: 2026-10-26");
        assertThat(html).contains("action=\"/dashboard/store/" + STORE_ID + "/trial/convert\"");
        assertThat(html).doesNotContain("/activate\"");
        assertThat(html).doesNotContain("/deactivate\"");
    }

    @Test
    void inactiveStorePageTellsTheClientWhy() {
        // when
        String html = engine.process("store-inactive", new Context(Locale.ENGLISH));

        // then
        assertThat(html).contains("This store is inactive");
        assertThat(html).contains("contact the seller directly");
    }

    @Test
    void registrationOffersTheTrial() {
        // when
        String html = engine.process("register", registration(false));

        // then
        assertThat(html).contains("You get a 14-day trial.");
        assertThat(html).contains("14 days later the data of the store is deleted for good.");
        assertThat(html).contains("Start your 14-day trial");
        assertThat(html).doesNotContain("This is a demo instance");
    }

    @Test
    void demoRegistrationKeepsTheDemoNotice() {
        // when
        String html = engine.process("register", registration(true));

        // then
        assertThat(html).contains("This is a demo instance");
        assertThat(html).contains(">Create account<");
        assertThat(html).doesNotContain("14-day trial");
    }

    @Test
    void passwordStepOfTrialDoesNotPromiseTheDashboard() {
        // when
        String html = engine.process("register-password", registration(false));

        // then
        assertThat(html).contains("Set a password to start your trial.");
        assertThat(html).contains("Set password and create account");
        assertThat(html).doesNotContain("go straight to the panel");
    }

    @Test
    void successPageOfTrialExplainsTheFirstSignIn() {
        // when
        String html = engine.process("register-success", registration(false));

        // then
        assertThat(html).contains("asks for an authenticator app");
        assertThat(html).contains("a code we send to owner@example.com");
    }
}
