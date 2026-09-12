package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.messageresolver.IMessageResolver;
import org.thymeleaf.spring6.dialect.SpringStandardDialect;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.StringTemplateResolver;
import pl.commercelink.starter.security.UserRole;
import pl.commercelink.web.nav.NavigationModel;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class NavigationSidebarFragmentTest {

    private String renderFor(UserRole role, String path) {
        Context context = new Context();
        context.setVariable("navigation", NavigationModel.forRoleAndPath(role, path));
        return templateEngine().process(
                "<div th:replace=\"~{fragments/navigation :: sidebar}\"></div>", context);
    }

    @Test
    void rendersEveryGroupTheRoleMaySeeWithItsTranslatedName() {
        // when
        String html = renderFor(UserRole.ADMIN, "/dashboard/orders");

        // then
        assertThat(html).contains("Sprzeda");
        assertThat(html).contains("Realizacja");
        assertThat(html).contains("Finanse");
        assertThat(html).doesNotContain("??nav.");
    }

    @Test
    void linksEveryEntryToItsDashboardPath() {
        // when
        String html = renderFor(UserRole.ADMIN, "/dashboard/orders");

        // then
        assertThat(html).contains("href=\"/dashboard/offers\"");
        assertThat(html).contains("href=\"/dashboard/warehouse-documents\"");
        assertThat(html).contains("href=\"/dashboard/store\"");
    }

    @Test
    void marksExactlyOneEntryAsTheCurrentPage() {
        // when
        String html = renderFor(UserRole.ADMIN, "/dashboard/warehouse-documents");

        // then
        assertThat(html.split("aria-current=\"page\"", -1)).hasSize(2);
        assertThat(html).contains("class=\"cl-nav-item is-active\"");
    }

    @Test
    void keepsAdminOnlyEntriesOutOfTheUserPanel() {
        // when
        String html = renderFor(UserRole.USER, "/dashboard/orders");

        // then
        assertThat(html).doesNotContain("href=\"/dashboard/payments\"");
        assertThat(html).doesNotContain("href=\"/dashboard/reports\"");
        assertThat(html).doesNotContain("href=\"/dashboard/store\"");
    }

    @Test
    void namesEveryEntryForScreenReadersSoTheRailIsNotJustIcons() {
        // when
        String html = renderFor(UserRole.ADMIN, "/dashboard/orders");

        // then
        assertThat(html).contains("aria-label=\"Magazyn\"");
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
