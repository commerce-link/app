package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import pl.commercelink.stores.Store;
import pl.commercelink.web.publicpage.PublicPageFactory;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class TrialTemplatesRenderingTest {

    private final TemplateEngine engine = EnglishFragmentTemplateEngine.create();

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
    void inactiveStorePageTellsTheClientWhy() {
        // given
        Store store = new Store();
        store.setStoreId("s1");
        store.setName("Nova Tech");
        Context context = new Context(Locale.ENGLISH);
        context.setVariable("publicPage", PublicPageFactory.of(store, "/store/s1/client/order/o1", Locale.ENGLISH));

        // when
        String html = engine.process("store-inactive", context);

        // then
        assertThat(html).contains("This store is inactive");
        assertThat(html).contains("contact the seller directly");
    }

    @Test
    void registrationOffersTheTrial() {
        // when
        String html = engine.process("register", registration(false));

        // then
        assertThat(html).contains("14 days free");
        assertThat(html).contains("stays open for browsing only for 14 days, then we delete the store data.");
        assertThat(html).contains("Start your free 14\u2011day trial");
        assertThat(html).doesNotContain("This is a demo instance");
    }

    @Test
    void demoRegistrationKeepsTheDemoNotice() {
        // when
        String html = engine.process("register", registration(true));

        // then
        assertThat(html).contains("This is a demo instance");
        assertThat(html).contains(">Create account<");
        assertThat(html).doesNotContain("14 days free");
    }

    @Test
    void passwordStepOfTrialDoesNotPromiseTheDashboard() {
        // when
        String html = engine.process("register-password", registration(false));

        // then
        assertThat(html).contains("Set a password to start your 14\u2011day trial.");
        assertThat(html).contains("Create account");
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
