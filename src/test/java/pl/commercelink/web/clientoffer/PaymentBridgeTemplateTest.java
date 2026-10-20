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
import pl.commercelink.stores.Store;
import pl.commercelink.web.publicpage.PublicPageFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentBridgeTemplateTest {

    private static final Locale PL = Locale.forLanguageTag("pl");
    private static final String URL = "https://pay.example/start";
    private static final Pattern FORM = Pattern.compile("(?s)<form\\b[^>]*>.*?</form>");

    @Test
    void bothFormsPostTheSameFieldsToTheProvider() {
        // when
        String html = render();

        // then
        Matcher forms = FORM.matcher(html);
        int count = 0;
        while (forms.find()) {
            count++;
            String form = forms.group();
            assertThat(form).contains("action=\"https://pay.example/start\"").contains("method=\"POST\"");
            assertThat(form).contains("name=\"amount\"").contains("value=\"12345\"")
                    .contains("name=\"sig\"").contains("value=\"a&amp;b&quot;c\"");
            assertThat(Pattern.compile("<input\\b").matcher(form).results().count()).isEqualTo(2);
        }
        assertThat(count).isEqualTo(2);
    }

    @Test
    void autoSubmitScriptIsLoaded() {
        // when
        String html = render();

        // then
        assertThat(html).contains("<script src=\"/js/payment-bridge.js\"></script>").contains("id=\"paymentForm\"");
    }

    @Test
    void autoSubmitWaitsUntilTheFooterIsParsed() throws Exception {
        // given — the script sits inside main, before the footer, and runs without defer
        String script = Files.readString(Path.of("src/main/resources/static/js/payment-bridge.js"));

        // then
        assertThat(script).contains("document.readyState === 'loading'")
                .contains("addEventListener('DOMContentLoaded', submit)")
                .contains("if (form) {");
    }

    @Test
    void noscriptOffersTheContinueButton() {
        // when
        String html = render();

        // then
        String noscript = html.substring(html.indexOf("<noscript>"), html.indexOf("</noscript>"));
        assertThat(noscript).contains("type=\"submit\"").contains("Przejdź do płatności")
                .contains("Twoja przeglądarka ma wyłączony JavaScript");
    }

    @Test
    void pageUsesThePublicFrameWithoutInlineScript() {
        // when
        String html = render();

        // then
        assertThat(html).contains("cl-public-main is-narrow").contains("cl-bridge-auto")
                .contains("<title>Przekierowujemy do płatności · Nova Tech</title>")
                .doesNotContainPattern("(?s)<script>(?!</script>).*document\\.getElementById");
        assertThat(html.substring(html.indexOf("<main"), html.indexOf("</main>")))
                .doesNotContain("<style").doesNotContain(" style=").doesNotContainPattern(" on[a-z]+=");
    }

    @Test
    void autoContentIsHiddenUntilTheScriptRevealsIt() {
        // when
        String html = render();

        // then
        assertThat(html).containsPattern("<div class=\"cl-bridge-auto\"[^>]*\\bhidden\\b")
                .contains("<h1 class=\"cl-page-title\">Przekierowujemy do płatności</h1>")
                .containsPattern("<p class=\"cl-page-lead\"[^>]*role=\"status\"")
                .contains("Nie zamykaj tej karty.");
    }

    private String render() {
        JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(new MockServletContext());
        IWebExchange exchange = application.buildExchange(new MockHttpServletRequest(), new MockHttpServletResponse());
        WebContext context = new WebContext(exchange, PL);
        Store store = new Store();
        store.setStoreId("s1");
        store.setName("Nova Tech");
        context.setVariable("publicPage", PublicPageFactory.of(store, "/store/s1/client/offer/a1b2c3d4e5", PL));
        context.setVariable("url", URL);
        Map<String, String> params = new LinkedHashMap<>();
        params.put("amount", "12345");
        params.put("sig", "a&b\"c");
        context.setVariable("params", params);
        return templateEngine().process("payments/payment-bridge", context);
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
