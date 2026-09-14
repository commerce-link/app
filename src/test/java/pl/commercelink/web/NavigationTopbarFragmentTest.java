package pl.commercelink.web;

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
import pl.commercelink.web.nav.NavigationModel;
import pl.commercelink.web.nav.StoreContext;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class NavigationTopbarFragmentTest {

    private String render(UserRole role, String path, StoreContext storeContext) {
        JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(new MockServletContext());
        IWebExchange exchange = application.buildExchange(new MockHttpServletRequest(), new MockHttpServletResponse());
        WebContext context = new WebContext(exchange);
        context.setVariable("navigation", NavigationModel.forRoleAndPath(role, path));
        context.setVariable("storeContext", storeContext);
        context.setVariable("currentUri", path);
        context.setVariable("currentQuery", "");
        context.setVariable("userEmail", "operator@commercelink.local");
        return templateEngine().process("<div th:replace=\"~{fragments/navigation :: topbar}\"></div>", context);
    }

    @Test
    void showsTheSectionAndThePageInTheBreadcrumb() {
        // when
        String html = render(UserRole.ADMIN, "/dashboard/deliveries", null);

        // then
        assertThat(html).contains("Realizacja");
        assertThat(html).contains("Dostawy");
        assertThat(html).doesNotContain("??nav.");
    }

    @Test
    void namesTheStoreTheSuperAdminIsWorkingInAndOffersTheWayOut() {
        // when
        String html = render(UserRole.SUPER_ADMIN, "/dashboard/store/uma2dqukxr/deliveries",
                new StoreContext("uma2dqukxr", "Bio Planet"));

        // then
        assertThat(html).contains("Bio Planet");
        assertThat(html).contains("href=\"/dashboard/stores\"");
    }

    @Test
    void leavesTheStoreChipOutWhenThereIsNoStoreContext() {
        // when
        String html = render(UserRole.ADMIN, "/dashboard/orders", null);

        // then
        assertThat(html).doesNotContain("cl-store-chip");
    }

    @Test
    void buildsBothLanguageLinksFromTheCurrentLocation() {
        // when
        String html = render(UserRole.ADMIN, "/dashboard/orders", null);

        // then
        assertThat(html).contains("/dashboard/orders?lang=pl");
        assertThat(html).contains("/dashboard/orders?lang=en");
    }

    @Test
    void exposesTheDrawerButtonWithAnHonestExpandedState() {
        // when
        String html = render(UserRole.ADMIN, "/dashboard/orders", null);

        // then
        assertThat(html).contains("aria-expanded=\"false\"");
        assertThat(html).contains("aria-controls=\"clSidebar\"");
        assertThat(html).contains("<button");
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
