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

    @Test
    void rendersTheTrialBannerWithDaysLeftAndEndDate() {
        // given
        WebContext context = webContext();
        context.setVariable("navigation", null);
        context.setVariable("trialStatus", new TrialStatus(LocalDate.parse("2026-10-12"), 5, false));

        // when
        String html = render(context);

        // then
        assertThat(html).contains("Okres próbny.");
        assertThat(html).contains("Kończy się 2026-10-12 (za 5 dni).");
    }

    @Test
    void rendersNoTrialBannerOutsideATrial() {
        // given
        WebContext context = webContext();
        context.setVariable("navigation", null);

        // when
        String html = render(context);

        // then
        assertThat(html).doesNotContain("Okres próbny.");
    }

    @Test
    void rendersTheDeactivationBannerOfEndedTrialWithTheDeletionDate() {
        // given
        WebContext context = webContext();
        context.setVariable("navigation", null);
        context.setVariable("deactivationStatus", new DeactivationStatus(DeactivationReason.TRIAL_ENDED,
                LocalDate.parse("2026-10-12"), LocalDate.parse("2026-10-26"), 6));
        context.setVariable("accountContactEmail", "kontakt@commercelink.pl");

        // when
        String html = render(context);

        // then
        assertThat(html).contains("Konto dezaktywowane.");
        assertThat(html).contains("Okres próbny zakończył się 2026-10-12. Możesz przeglądać panel, ale zmiany nie są zapisywane.");
        assertThat(html).contains("Dane sklepu zostaną trwale usunięte 2026-10-26 (za 6 dni).");
        assertThat(html).contains("href=\"mailto:kontakt@commercelink.pl\"");
    }

    @Test
    void rendersTheDeactivationBannerOfStoreSwitchedOffByHandWithoutDeletion() {
        // given
        WebContext context = webContext();
        context.setVariable("navigation", null);
        context.setVariable("deactivationStatus", new DeactivationStatus(DeactivationReason.MANUAL,
                LocalDate.parse("2026-10-18"), null, 0));

        // when
        String html = render(context);

        // then
        assertThat(html).contains("Konto dezaktywowane.");
        assertThat(html).contains("Sklep jest nieaktywny. Możesz przeglądać panel, ale zmiany nie są zapisywane.");
        assertThat(html).doesNotContain("trwale usunięte");
        assertThat(html).doesNotContain("mailto:");
    }

    @Test
    void rendersNoDeactivationBannerForActiveStore() {
        // given
        WebContext context = webContext();
        context.setVariable("navigation", null);

        // when
        String html = render(context);

        // then
        assertThat(html).doesNotContain("Konto dezaktywowane.");
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
