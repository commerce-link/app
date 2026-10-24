package pl.commercelink.web.clientoffer;

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
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.stores.Store;
import pl.commercelink.web.publicpage.PublicPage;
import pl.commercelink.web.publicpage.PublicPageFactory;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

import static org.assertj.core.api.Assertions.assertThat;

class ClientOfferUnavailableRenderingTest {

    private static final Locale PL = Locale.forLanguageTag("pl");
    private static final String PATH = "/store/s1/client/offer/a1b2c3d4e5";

    @Test
    void showsTheShortIdTitleAndContactLink() {
        // given
        Store store = store();
        BillingDetails billing = new BillingDetails();
        billing.setEmail("biuro@nova.pl");
        store.setBillingDetails(billing);

        // when
        String html = render(PublicPageFactory.of(store, PATH, PL));

        // then
        assertThat(html).contains("<title>Oferta niedostępna · Nova Tech</title>")
                .contains("<section class=\"cl-card cl-public-message\">")
                .contains("<main id=\"clContent\" class=\"cl-page cl-public-main is-narrow\"")
                .contains("<p class=\"cl-eyebrow\">Oferta a1b2c3d4</p>")
                .contains("<h1 class=\"cl-page-title\">Ta oferta nie jest już dostępna</h1>")
                .contains("Oferta została zrealizowana, wycofana albo minął jej termin ważności.")
                .contains("Jeśli chcesz wrócić do zamówienia, napisz do nas:")
                .contains("<a href=\"mailto:biuro@nova.pl\">biuro@nova.pl</a>");
    }

    @Test
    void skipsTheContactSentenceWithoutAnEmail() {
        // when
        String html = render(PublicPageFactory.of(store(), PATH, PL));

        // then
        assertThat(html).contains("Ta oferta nie jest już dostępna")
                .doesNotContain("napisz do nas")
                .doesNotContain("mailto:");
    }

    @Test
    void unknownStoreRendersWithoutBrandOrFooter() {
        // when
        String html = render(PublicPageFactory.unknownStore(PATH, PL));

        // then
        assertThat(html).contains("<title>Oferta niedostępna</title>")
                .contains("Ta oferta nie jest już dostępna")
                .doesNotContain("cl-public-foot")
                .doesNotContain("mailto:");
    }

    @Test
    void pageHasNoInlineScriptOrStyle() {
        // when
        String html = render(PublicPageFactory.of(store(), PATH, PL));

        // then
        String content = html.substring(html.indexOf("<main"), html.indexOf("</main>"));
        assertThat(content).doesNotContain("<script").doesNotContain("<style").doesNotContain(" style=")
                .doesNotContainPattern(" on[a-z]+=");
    }

    private static Store store() {
        Store store = new Store();
        store.setStoreId("s1");
        store.setName("Nova Tech");
        return store;
    }

    private String render(PublicPage page) {
        JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(new MockServletContext());
        IWebExchange exchange = application.buildExchange(new MockHttpServletRequest(), new MockHttpServletResponse());
        WebContext context = new WebContext(exchange, PL);
        context.setVariable("publicPage", page);
        context.setVariable("shortId", "a1b2c3d4");
        return templateEngine().process("client-offer/unavailable", context);
    }

    private TemplateEngine templateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");

        TemplateEngine templateEngine = new TemplateEngine();
        templateEngine.setDialect(new SpringStandardDialect());
        templateEngine.addDialect(new LayoutDialect());
        templateEngine.addTemplateResolver(resolver);
        templateEngine.setMessageResolver(new PolishMessages());
        return templateEngine;
    }

    private static class PolishMessages implements IMessageResolver {

        private final ResourceBundle messages = ResourceBundle.getBundle("messages", PL);

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
            return parameters == null || parameters.length == 0 ? message : MessageFormat.format(message, parameters);
        }

        @Override
        public String createAbsentMessageRepresentation(ITemplateContext context, Class<?> origin, String key, Object[] parameters) {
            return "??" + key + "??";
        }
    }
}
