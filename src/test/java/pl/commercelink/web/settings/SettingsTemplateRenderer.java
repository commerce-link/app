package pl.commercelink.web.settings;

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
import org.thymeleaf.templateresolver.StringTemplateResolver;
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;

/** Renders real templates with the Polish message bundle, the way the settings pages are served. */
public final class SettingsTemplateRenderer {

    private static final Locale POLISH = Locale.forLanguageTag("pl");
    private static final TemplateEngine ENGINE = templateEngine();

    private SettingsTemplateRenderer() {
    }

    public static String render(String templateOrMarkup, Map<String, Object> variables) {
        JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(new MockServletContext());
        IWebExchange exchange = application.buildExchange(new MockHttpServletRequest(), new MockHttpServletResponse());
        WebContext context = new WebContext(exchange, POLISH);
        variables.forEach(context::setVariable);
        return ENGINE.process(templateOrMarkup, context);
    }

    private static TemplateEngine templateEngine() {
        StringTemplateResolver stringResolver = new StringTemplateResolver();
        stringResolver.setOrder(1);
        stringResolver.setTemplateMode(TemplateMode.HTML);
        stringResolver.setResolvablePatterns(Set.of("*<*"));

        ClassLoaderTemplateResolver classpathResolver = new ClassLoaderTemplateResolver();
        classpathResolver.setOrder(2);
        classpathResolver.setPrefix("templates/");
        classpathResolver.setSuffix(".html");
        classpathResolver.setTemplateMode(TemplateMode.HTML);

        TemplateEngine engine = new TemplateEngine();
        engine.setDialect(new SpringStandardDialect());
        engine.addDialect(new LayoutDialect());
        engine.addTemplateResolver(stringResolver);
        engine.addTemplateResolver(classpathResolver);
        engine.setMessageResolver(new PolishMessages());
        return engine;
    }

    private static class PolishMessages implements IMessageResolver {

        private final ResourceBundle messages = ResourceBundle.getBundle("messages", POLISH);

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
                    : new MessageFormat(message, POLISH).format(parameters);
        }

        @Override
        public String createAbsentMessageRepresentation(ITemplateContext context, Class<?> origin, String key,
                                                        Object[] parameters) {
            return "??" + key + "??";
        }
    }
}
