package pl.commercelink.web;

import nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.messageresolver.IMessageResolver;
import org.thymeleaf.spring6.dialect.SpringStandardDialect;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.StringTemplateResolver;
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;
import pl.commercelink.starter.security.UserRole;
import pl.commercelink.stores.DeactivationReason;
import pl.commercelink.stores.DeactivationStatus;
import pl.commercelink.stores.TrialStatus;
import pl.commercelink.web.activity.AccountStatusView;
import pl.commercelink.web.nav.NavigationModel;

import java.text.MessageFormat;
import java.time.LocalDate;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A rendering smoke test for {@code layout.html}: {@link LayoutShellTemplateTest} only matches
 * strings, so it would not catch a fragment-resolution or parse error that every one of the ~80
 * templates decorating this layout would hit at runtime.
 */
class LayoutRenderingTest {

    private static final String CONTENT_MARKER = "LAYOUT_RENDERING_TEST_MARKER";
    private static final String STORE_ID = "store-1";
    private static final String CONTACT = "kontakt@commercelink.pl";

    private static final String DECORATING_PAGE = "<html xmlns:th=\"http://www.thymeleaf.org\" "
            + "xmlns:layout=\"http://www.ultraq.net.nz/thymeleaf/layout\" layout:decorate=\"~{layout}\">"
            + "<body><div layout:fragment=\"content\">" + CONTENT_MARKER + "</div></body></html>";

    private String render(WebContext context) {
        return templateEngine().process(DECORATING_PAGE, context);
    }

    private WebContext webContext() {
        JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(new MockServletContext());
        IWebExchange exchange = application.buildExchange(new MockHttpServletRequest(), new MockHttpServletResponse());
        return new WebContext(exchange);
    }

    @Test
    void rendersTheBarePageWhenNavigationIsExplicitlyDisabledAndShowsTheFlashMessage() {
        // given
        WebContext context = webContext();
        context.setVariable("showNavbar", false);
        context.setVariable("navigation", null);
        context.setVariable("successMessage", "Zapisano zmiany");

        // when
        String html = render(context);

        // then
        assertThat(html).contains(CONTENT_MARKER);
        assertThat(html).doesNotContain("cl-sidebar");
        assertThat(html).doesNotContain("cl-topbar");
        assertThat(html).contains("notification is-success is-small has-text-centered");
        assertThat(html).contains("Zapisano zmiany");
    }

    @Test
    void rendersTheBarePageWhenNavbarIsUnsetAndNavigationIsNull() {
        // given
        WebContext context = webContext();
        context.setVariable("navigation", null);

        // when
        String html = render(context);

        // then
        assertThat(html).contains(CONTENT_MARKER);
        assertThat(html).doesNotContain("cl-sidebar");
        assertThat(html).doesNotContain("cl-topbar");
        assertThat(html).contains("class=\"cl-shell cl-shell-bare\"");
    }

    @Test
    void rendersTheSidebarAndTopbarForAnAuthenticatedAdmin() {
        // given
        WebContext context = webContext();
        context.setVariable("navigation", NavigationModel.forRoleAndPath(UserRole.ADMIN, "/dashboard/orders"));
        context.setVariable("storeContext", null);
        context.setVariable("currentUri", "/dashboard/orders");
        context.setVariable("currentQuery", "");
        context.setVariable("userEmail", "admin@commercelink.local");

        // when
        String html = render(context);

        // then
        assertThat(html).contains(CONTENT_MARKER);
        assertThat(html).contains("cl-sidebar");
        assertThat(html).contains("cl-topbar");
        assertThat(html).doesNotContain("cl-shell-bare");
    }

    private WebContext adminContext() {
        WebContext context = webContext();
        context.setVariable("navigation", NavigationModel.forRoleAndPath(UserRole.ADMIN, "/dashboard/orders"));
        context.setVariable("storeContext", null);
        context.setVariable("currentUri", "/dashboard/orders");
        context.setVariable("currentQuery", "");
        return context;
    }

    private static AccountStatusView trialEndingIn(long days, String contact) {
        return AccountStatusView.of(STORE_ID, new TrialStatus(LocalDate.parse("2026-10-14").plusDays(days), days, false),
                null, contact);
    }

    private static AccountStatusView trialEnded(String contact) {
        return AccountStatusView.of(STORE_ID, null, new DeactivationStatus(DeactivationReason.TRIAL_ENDED,
                LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-15"), 13), contact);
    }

    private static AccountStatusView switchedOff(String contact) {
        return AccountStatusView.of(STORE_ID, null, new DeactivationStatus(DeactivationReason.MANUAL,
                LocalDate.parse("2026-10-01"), null, 0), contact);
    }

    @Test
    void trialWithMoreThanThreeDaysLeftShowsOnlyTheInfoPill() {
        // given
        WebContext context = adminContext();
        context.setVariable("accountStatus", trialEndingIn(14, CONTACT));

        // when
        String html = render(context);

        // then
        assertThat(html).contains("class=\"cl-status cl-account-status-toggle is-info\"");
        assertThat(html).contains("aria-controls=\"clAccountStatusMenu\"");
        assertThat(html).contains("Okres próbny · 14 dni");
        assertThat(html).contains("Kończy się 28.10.2026 (za 14 dni). Potem panel będzie tylko do podglądu.");
        assertThat(html).contains("href=\"mailto:kontakt@commercelink.pl\"");
        assertThat(html).doesNotContain("cl-account-alert");
        assertThat(html).doesNotContain("cl-frame-notices");
        assertThat(html).doesNotContain("dismissible-alert.js");
    }

    @Test
    void trialWithThreeDaysLeftWarnsInThePillAndWithADismissibleAlert() {
        // given
        WebContext context = adminContext();
        context.setVariable("accountStatus", trialEndingIn(3, CONTACT));

        // when
        String html = render(context);

        // then
        assertThat(html).contains("class=\"cl-status cl-account-status-toggle is-warn\"");
        assertThat(html).contains("Okres próbny · zostały 3 dni");
        assertThat(html).contains("class=\"cl-alert cl-account-alert is-warn\"");
        assertThat(html).contains("Okres próbny kończy się za 3 dni");
        assertThat(html).contains("(17.10.2026). Potem panel będzie tylko do podglądu.");
        assertThat(html).contains("Przejdź na pełne konto — napisz do nas");
        assertThat(html).contains("data-cl-dismiss-key=\"cl.accountStatus.dismissed.store-1.TRIAL_ENDING\"");
        assertThat(html).contains("aria-label=\"Ukryj do końca sesji\"");
        assertThat(html).contains("dismissible-alert.js");
    }

    @Test
    void lastDayOfTrialSaysItEndsToday() {
        // given
        WebContext context = adminContext();
        context.setVariable("accountStatus", trialEndingIn(0, CONTACT));

        // when
        String html = render(context);

        // then
        assertThat(html).contains("Okres próbny · kończy się dziś");
        assertThat(html).contains("Okres próbny kończy się dziś");
        assertThat(html).contains("<span class=\"cl-account-status-short\" aria-hidden=\"true\">dziś</span>");
    }

    @Test
    void oneDayLeftUsesTheSingular() {
        // given
        WebContext context = adminContext();
        context.setVariable("accountStatus", trialEndingIn(1, CONTACT));

        // when
        String html = render(context);

        // then
        assertThat(html).contains("Okres próbny · został 1 dzień");
        assertThat(html).contains("Okres próbny kończy się za 1 dzień");
    }

    @Test
    void endingAlertWithoutContactHasNoMailLink() {
        // given
        WebContext context = adminContext();
        context.setVariable("accountStatus", trialEndingIn(2, null));

        // when
        String html = render(context);

        // then
        assertThat(html).contains("Okres próbny kończy się za 2 dni");
        assertThat(html).doesNotContain("mailto:");
        assertThat(html).doesNotContain("Przejdź na pełne konto");
    }

    @Test
    void endedTrialShowsReadOnlyPillAndAlertWithTheDeletionDate() {
        // given
        WebContext context = adminContext();
        context.setVariable("accountStatus", trialEnded(CONTACT));

        // when
        String html = render(context);

        // then
        assertThat(html).contains("class=\"cl-status cl-account-status-toggle is-bad\"");
        assertThat(html).contains("Tylko podgląd");
        assertThat(html).contains("class=\"cl-alert cl-account-alert is-bad\"");
        assertThat(html).contains("Okres próbny zakończył się 01.10.2026 — panel jest tylko do podglądu");
        assertThat(html).contains("Zmiany nie są zapisywane, a importy, eksporty, cenniki i feedy są wstrzymane. "
                + "Dane sklepu zostaną trwale usunięte");
        assertThat(html).contains("<strong>15.10.2026 (za 13 dni)</strong>");
        assertThat(html).contains("Aby dalej korzystać z CommerceLink, napisz do nas:");
        assertThat(html).contains("href=\"mailto:kontakt@commercelink.pl\"");
        assertThat(html).doesNotContain("data-cl-dismiss-key");
        assertThat(html).doesNotContain("dismissible-alert.js");
    }

    @Test
    void storeSwitchedOffByHandShowsReadOnlyAlertWithoutDeletion() {
        // given
        WebContext context = adminContext();
        context.setVariable("accountStatus", switchedOff(CONTACT));

        // when
        String html = render(context);

        // then
        assertThat(html).contains("class=\"cl-status cl-account-status-toggle is-bad\"");
        assertThat(html).contains("Sklep jest nieaktywny — panel jest tylko do podglądu");
        assertThat(html).contains("Rozpoczęte zamówienia są dalej obsługiwane automatycznie.");
        assertThat(html).contains("Aby włączyć sklep, napisz do nas:");
        assertThat(html).doesNotContain("trwale usunięte");
        assertThat(html).doesNotContain("data-cl-dismiss-key");
    }

    @Test
    void readOnlyAlertWithoutContactHasNoContactLine() {
        // given
        WebContext context = webContext();
        context.setVariable("navigation", null);
        context.setVariable("accountStatus", switchedOff(null));

        // when
        String html = render(context);

        // then
        assertThat(html).contains("Sklep jest nieaktywny — panel jest tylko do podglądu");
        assertThat(html).doesNotContain("mailto:");
        assertThat(html).doesNotContain("napisz do nas");
    }

    @Test
    void fullAccountShowsNoAccountStatus() {
        // given
        WebContext context = adminContext();
        context.setVariable("accountStatus", null);

        // when
        String html = render(context);

        // then
        assertThat(html).contains("cl-topbar");
        assertThat(html).doesNotContain("cl-account-status");
        assertThat(html).doesNotContain("cl-account-alert");
        assertThat(html).doesNotContain("Okres próbny");
    }

    private TemplateEngine templateEngine() {
        StringTemplateResolver stringResolver = new StringTemplateResolver();
        stringResolver.setOrder(1);
        stringResolver.setTemplateMode(TemplateMode.HTML);
        stringResolver.setResolvablePatterns(Set.of("*<*"));

        ClassLoaderTemplateResolver classpathResolver = new ClassLoaderTemplateResolver();
        classpathResolver.setOrder(2);
        classpathResolver.setPrefix("templates/");
        classpathResolver.setSuffix(".html");
        classpathResolver.setTemplateMode(TemplateMode.HTML);

        TemplateEngine templateEngine = new TemplateEngine();
        templateEngine.setDialect(new SpringStandardDialect());
        templateEngine.addDialect(new LayoutDialect());
        templateEngine.addTemplateResolver(stringResolver);
        templateEngine.addTemplateResolver(classpathResolver);
        templateEngine.setMessageResolver(new PolishMessages());
        return templateEngine;
    }

    private static class PolishMessages implements IMessageResolver {

        private final ResourceBundle messages = ResourceBundle.getBundle("messages", Locale.forLanguageTag("pl"));

        @Override
        public String getName() {
            return "polish";
        }

        @Override
        public Integer getOrder() {
            return 1;
        }

        @Override
        public String resolveMessage(ITemplateContext context, Class<?> origin, String key, Object[] parameters) {
            if (!messages.containsKey(key)) {
                return null;
            }
            String message = messages.getString(key);
            return parameters == null || parameters.length == 0
                    ? message
                    : new MessageFormat(message, Locale.forLanguageTag("pl")).format(parameters);
        }

        @Override
        public String createAbsentMessageRepresentation(ITemplateContext context, Class<?> origin, String key,
                                                        Object[] parameters) {
            return "??" + key + "??";
        }
    }
}
