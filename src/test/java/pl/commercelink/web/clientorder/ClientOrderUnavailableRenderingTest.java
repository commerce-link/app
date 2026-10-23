package pl.commercelink.web.clientorder;

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

class ClientOrderUnavailableRenderingTest {

    private static final Locale PL = Locale.forLanguageTag("pl");
    private static final String PATH = "/store/s1/client/order/a1b2c3d4e5";

    @Test
    void showsTheShortIdTitleAndContactLink() {
        // given
        Store store = store();
        BillingDetails billing = new BillingDetails();
        billing.setEmail("biuro@nova.pl");
        store.setBillingDetails(billing);

        // when
        String html = render(PublicPageFactory.of(store, PATH, PL), "a1b2c3d4", PL);

        // then
        assertThat(html).contains("<title>Zamówienie niedostępne · Nova Tech</title>")
                .contains("<section class=\"cl-card cl-public-message\">")
                .contains("<p class=\"cl-eyebrow\">Zamówienie a1b2c3d4</p>")
                .contains("<h1 class=\"cl-page-title\">Ta strona zamówienia nie jest już dostępna</h1>")
                .contains("Zamówienie zostało zakończone albo link jest nieprawidłowy.")
                .contains("Jeśli masz pytanie o zamówienie, napisz do nas:")
                .contains("<a href=\"mailto:biuro@nova.pl\">biuro@nova.pl</a>");
    }

    @Test
    void skipsTheContactSentenceWithoutAnEmail() {
        // when
        String html = render(PublicPageFactory.of(store(), PATH, PL), "a1b2c3d4", PL);

        // then
        assertThat(html).contains("Ta strona zamówienia nie jest już dostępna")
                .doesNotContain("napisz do nas")
                .doesNotContain("mailto:");
    }

    @Test
    void unknownStoreRendersWithoutBrandOrFooter() {
        // when
        String html = render(PublicPageFactory.unknownStore(PATH, PL), null, PL);

        // then
        assertThat(html).contains("Ta strona zamówienia nie jest już dostępna")
                .doesNotContain("cl-eyebrow")
                .doesNotContain("cl-public-foot")
                .doesNotContain("mailto:");
    }

    @Test
    void rendersEnglishTexts() {
        // given
        Locale en = Locale.ENGLISH;
        Store store = store();
        BillingDetails billing = new BillingDetails();
        billing.setEmail("biuro@nova.pl");
        store.setBillingDetails(billing);

        // when
        String html = render(PublicPageFactory.of(store, PATH, en), "a1b2c3d4", en);

        // then
        assertThat(html).contains("<p class=\"cl-eyebrow\">Order a1b2c3d4</p>")
                .contains("This order page is no longer available")
                .contains("The order has been completed or the link is not valid.")
                .contains("If you have a question about your order, write to us:");
    }

    @Test
    void pageHasNoInlineScriptOrStyle() {
        // when
        String html = render(PublicPageFactory.of(store(), PATH, PL), "a1b2c3d4", PL);

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

    private String render(PublicPage page, String shortId, Locale locale) {
        JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(new MockServletContext());
        IWebExchange exchange = application.buildExchange(new MockHttpServletRequest(), new MockHttpServletResponse());
        WebContext context = new WebContext(exchange, locale);
        context.setVariable("publicPage", page);
        context.setVariable("shortId", shortId);
        return templateEngine(locale).process("client-order/unavailable", context);
    }

    private TemplateEngine templateEngine(Locale locale) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");

        TemplateEngine templateEngine = new TemplateEngine();
        templateEngine.setDialect(new SpringStandardDialect());
        templateEngine.addDialect(new LayoutDialect());
        templateEngine.addTemplateResolver(resolver);
        templateEngine.setMessageResolver(new Messages(locale));
        return templateEngine;
    }

    private static class Messages implements IMessageResolver {

        private final ResourceBundle messages;

        Messages(Locale locale) {
            this.messages = ResourceBundle.getBundle("messages", locale);
        }

        @Override
        public String getName() {
            return "messages";
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
