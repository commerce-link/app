package pl.commercelink.web.publicpage;

import nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect;
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

import java.text.MessageFormat;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

/** Renders a page template with the real message bundles and the layout dialect (public-layout, auth-layout). */
public final class PublicTemplateRenderer {

    private PublicTemplateRenderer() {
    }

    public static String render(String view, Locale locale, Map<String, ?> variables) {
        JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(new MockServletContext());
        IWebExchange exchange = application.buildExchange(new MockHttpServletRequest(), new MockHttpServletResponse());
        WebContext context = new WebContext(exchange, locale);
        variables.forEach(context::setVariable);
        return templateEngine(locale).process(view, context);
    }

    /** The content of {@code <main>}, without the frame. */
    public static String main(String html) {
        return html.substring(html.indexOf("<main"), html.indexOf("</main>"));
    }

    private static TemplateEngine templateEngine(Locale locale) {
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

    private static final class Messages implements IMessageResolver {

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
