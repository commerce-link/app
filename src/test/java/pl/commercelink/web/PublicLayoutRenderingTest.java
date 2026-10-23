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
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.stores.Branding;
import pl.commercelink.stores.Store;
import pl.commercelink.web.publicpage.PublicPage;
import pl.commercelink.web.publicpage.PublicPageFactory;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PublicLayoutRenderingTest {

    private static final Locale PL = Locale.forLanguageTag("pl");
    private static final String PATH = "/store/s1/client/offer/o1";

    private static final String DECORATING_PAGE = "<html xmlns:th=\"http://www.thymeleaf.org\" "
            + "xmlns:layout=\"http://www.ultraq.net.nz/thymeleaf/layout\" layout:decorate=\"~{public-layout}\">"
            + "<head><title>Oferta</title></head>"
            + "<body><div layout:fragment=\"content\">PAGE_CONTENT</div></body></html>";

    @Test
    void headerShowsOnlyTheLogoWhenThereIsOne() {
        // given
        Store store = store("Nova Tech");
        Branding branding = new Branding();
        branding.setLogo("logo.png");
        branding.setLogoVersion(7L);
        store.setBranding(branding);

        // when
        String html = render(PublicPageFactory.of(store, PATH, PL));

        // then
        assertThat(html).contains("class=\"cl-public-logo\"")
                .contains("src=\"/StoreLogo/s1?v=7\"")
                .contains("alt=\"Nova Tech\"")
                .doesNotContain("cl-public-store\"");
    }

    @Test
    void headerShowsTheNameWithoutALogo() {
        // given
        Store store = store("Sklep Kowalski");

        // when
        String html = render(PublicPageFactory.of(store, PATH, PL));

        // then
        assertThat(html).contains("<span class=\"cl-public-store\">Sklep Kowalski</span>")
                .doesNotContain("cl-public-logo\"");
    }

    @Test
    void titleCombinesThePageTitleWithTheStoreName() {
        // when
        String html = render(PublicPageFactory.of(store("Sklep Kowalski"), PATH, PL));

        // then
        assertThat(html).contains("<title>Oferta · Sklep Kowalski</title>");
    }

    @Test
    void contentSitsInTheMainLandmarkWithTheRequestedWidth() {
        // when
        String html = render(PublicPageFactory.of(store("Nova Tech"), PATH, PL), "is-form");

        // then
        assertThat(html).contains("<main id=\"clContent\" class=\"cl-page cl-public-main is-form\"")
                .contains("PAGE_CONTENT");
    }

    @Test
    void brandVariablesComeFromThePalette() {
        // given
        Store store = store("Nova Tech");
        Branding branding = new Branding();
        branding.setPrimaryColor("#0f766e");
        store.setBranding(branding);
        Store hostile = store("Nova Tech");
        Branding injected = new Branding();
        injected.setPrimaryColor("red;background:url(x)");
        hostile.setBranding(injected);

        // when
        String html = render(PublicPageFactory.of(store, PATH, PL));
        String safe = render(PublicPageFactory.of(hostile, PATH, PL));

        // then
        assertThat(html).contains("--cl-brand:#0f766e;");
        assertThat(safe).contains("--cl-brand:#1b4db1").doesNotContain("url(x)");
    }

    @Test
    void footerShowsSellerAndContact() {
        // given
        Store store = store("Nova Tech");
        BillingDetails billing = new BillingDetails();
        billing.setCompanyName("Nova Tech sp. z o.o.");
        billing.setTaxId("5252814410");
        billing.setStreetAndNumber("ul. Prosta 12");
        billing.setPostalCode("00-850");
        billing.setCity("Warszawa");
        billing.setPhone("+48 22 123 45 67");
        billing.setEmail("biuro@nova.pl");
        store.setBillingDetails(billing);

        // when
        String html = render(PublicPageFactory.of(store, PATH, PL));

        // then
        assertThat(html).contains("Sprzedawca: Nova Tech sp. z o.o. · NIP 5252814410 · ul. Prosta 12, 00-850 Warszawa")
                .contains("href=\"mailto:biuro@nova.pl\"")
                .contains("href=\"tel:+48221234567\"");
    }

    @Test
    void footerSkipsTheSellerLineWithoutCompanyData() {
        // when
        String html = render(PublicPageFactory.of(store("Nova Tech"), PATH, PL));

        // then
        assertThat(html).contains("cl-public-foot-store").doesNotContain("Sprzedawca").doesNotContain("Kontakt:");
    }

    @Test
    void languageSwitcherMarksTheCurrentLanguage() {
        // when
        String html = render(PublicPageFactory.of(store("Nova Tech"), PATH, PL));

        // then
        assertThat(html).contains("<span aria-current=\"true\" lang=\"pl\">PL</span>")
                .contains("href=\"" + PATH + "?lang=en\"")
                .contains("hreflang=\"en\"");
    }

    @Test
    void pageHasNoPanelChrome() {
        // when
        String html = render(PublicPageFactory.of(store("Nova Tech"), PATH, PL));

        // then
        assertThat(html).doesNotContain("cl-sidebar").doesNotContain("confirmSaveModal")
                .doesNotContain("Sortable").doesNotContain("notification is-");
    }

    private static Store store(String name) {
        Store store = new Store();
        store.setStoreId("s1");
        store.setName(name);
        return store;
    }

    private String render(PublicPage page) {
        return render(page, null);
    }

    private String render(PublicPage page, String width) {
        JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(new MockServletContext());
        IWebExchange exchange = application.buildExchange(new MockHttpServletRequest(), new MockHttpServletResponse());
        WebContext context = new WebContext(exchange, PL);
        context.setVariable("publicPage", page);
        String template = width == null ? DECORATING_PAGE
                : DECORATING_PAGE.replace("<html ", "<html th:with=\"publicWidth='" + width + "'\" ");
        return templateEngine().process(template, context);
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
