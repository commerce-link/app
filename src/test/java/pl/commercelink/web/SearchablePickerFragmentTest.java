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
import pl.commercelink.web.dtos.PickerOption;

import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SearchablePickerFragmentTest {

    private static final List<PickerOption> ADDRESSES = List.of(
            new PickerOption("1", "ul. Przemysłowa 12, 02-495 Warszawa, PL"),
            new PickerOption("2", "ul. Zakopiańska 58, 30-418 Kraków, PL"));

    private String renderPicker(String selected) {
        return renderPicker(selected, null);
    }

    private String renderPicker(String selected, String selectedLabel) {
        Context context = new Context();
        context.setVariable("selected", selected);
        context.setVariable("selectedLabel", selectedLabel);
        return templateEngine().process(
                "<div th:replace=\"~{fragments/searchable-picker :: picker('deliveryAddressId', ${selected}, "
                        + "${selectedLabel}, 'Wybierz adres dostawy')}\"></div>", context);
    }

    private String renderScript(List<PickerOption> options) {
        Context context = new Context();
        context.setVariable("options", options);
        return templateEngine().process(
                "<div th:replace=\"~{fragments/searchable-picker :: pickerScript('deliveryAddressId', "
                        + "${options})}\"></div>", context);
    }

    @Test
    void carriesTheSelectedValueInAHiddenFieldUnderTheGivenName() {
        // when
        String html = renderPicker("2");

        // then
        assertThat(html).contains("name=\"deliveryAddressId\"");
        assertThat(html).contains("value=\"2\"");
        assertThat(html).contains("data-picker-field=\"deliveryAddressId\"");
    }

    @Test
    void showsThePlaceholderWhenNothingIsSelectedYet() {
        // when
        String html = renderPicker(null);

        // then
        assertThat(html).contains("Wybierz adres dostawy");
        assertThat(html).doesNotContain("??picker");
    }

    @Test
    void showsTheSelectedLabelServerSideSoThereIsNoPlaceholderFlash() {
        // when
        String html = renderPicker("1", "ul. Przemysłowa 12, 02-495 Warszawa, PL");

        // then
        assertThat(html).contains("ul. Przemys");
        assertThat(html).doesNotContain(">Wybierz adres dostawy<");
    }

    @Test
    void rendersASearchInputSoLongListsStayUsable() {
        // when
        String html = renderPicker(null);

        // then
        assertThat(html).contains("data-picker-search");
        assertThat(html).contains("Szukaj...");
    }

    @Test
    void handsEveryOptionToTheScriptSoFilteringHappensWithoutARoundTrip() {
        // when
        String html = renderScript(ADDRESSES);

        // then
        assertThat(html).contains("Przemys");
        assertThat(html).contains("Zakopia");
        assertThat(html).contains("\"value\":\"1\"");
        assertThat(html).doesNotContain("??picker");
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

        private final ResourceBundle messages =
                ResourceBundle.getBundle("messages", Locale.forLanguageTag("pl"));

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
