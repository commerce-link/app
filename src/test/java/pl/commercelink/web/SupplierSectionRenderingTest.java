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
import pl.commercelink.inventory.supplier.SupplierConnectionView;
import pl.commercelink.stores.ConnectionMode;

import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Renders fragments/supplier-section.html through a real Thymeleaf engine (same technique as
 * CategoryPickerFragmentTest) instead of only asserting on the raw file text. This is what proves
 * the fragment actually compiles and produces the expected markup for the exact argument shapes
 * SupplierSectionModel passes -- the controller returns the same "template :: fragment(args)"
 * selector syntax as a view name, which Spring's ThymeleafView resolves with the identical
 * fragment-expression parser used by th:replace/th:insert here.
 */
class SupplierSectionRenderingTest {

    private static final String SECTION = "<div th:replace=\"~{fragments/supplier-section :: supplierSection(%s)}\"></div>";

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
        templateEngine.setMessageResolver(new EnglishMessages());
        return templateEngine;
    }

    private static class EnglishMessages implements IMessageResolver {

        private final ResourceBundle messages = ResourceBundle.getBundle("messages", Locale.ENGLISH);

        @Override
        public String getName() {
            return "english";
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
                    : new MessageFormat(message, Locale.ENGLISH).format(parameters);
        }

        @Override
        public String createAbsentMessageRepresentation(ITemplateContext context, Class<?> origin, String key,
                                                        Object[] parameters) {
            return "??" + key + "??";
        }
    }

    @Test
    void rendersTheExternalSectionExactlyAsStoreFulfilmentSupplierControllerBuildsIt() {
        // given -- the same argument shape SupplierSectionModel.renderExternalSection returns
        SupplierConnectionView elko = new SupplierConnectionView(
                "Elko", "Elko", "Elko", ConnectionMode.OWN, true, true, true, null, true);
        Context context = new Context();
        context.setVariable("sectionRows", List.of(elko));
        context.setVariable("sectionShowMode", true);
        context.setVariable("sectionBasePath", "/dashboard/store");
        context.setVariable("sectionAvailableSuppliers", List.of("Acme"));
        context.setVariable("sectionSuccessMessage", "Supplier Elko saved.");

        String args = "${sectionRows}, false, ${sectionShowMode}, ${sectionBasePath}, "
                + "'store.supplier.section.title', 'supplier-add-button', 'store.supplier.add.button', "
                + "${sectionAvailableSuppliers.isEmpty()}, 'store.supplier.add.none', ${sectionSuccessMessage}";

        // when
        String html = templateEngine().process(SECTION.formatted(args), context);

        // then
        assertThat(html).doesNotContain("??store.supplier");
        assertThat(html).contains("Suppliers"); // store.supplier.section.title
        assertThat(html).contains("id=\"supplier-add-button\"");
        assertThat(html).contains("Elko");
        assertThat(html).contains("data-success-message=\"Supplier Elko saved.\"");
        // Acme is the only unconnected supplier, but the Add button reflects a non-empty
        // availableSuppliers list, so it must not be disabled
        assertThat(html).doesNotContain("disabled=\"disabled\"");
    }

    @Test
    void rendersTheManualSectionExactlyAsManualSupplierControllerBuildsIt() {
        // given -- the same argument shape SupplierSectionModel.renderManualSection returns
        SupplierConnectionView manual = new SupplierConnectionView(
                "manual:Hurtownia X", null, "Hurtownia X", ConnectionMode.MANUAL, false, false, false, null, true);
        Context context = new Context();
        context.setVariable("sectionRows", List.of(manual));
        context.setVariable("sectionBasePath", "/dashboard/store");
        context.setVariable("sectionSuccessMessage", null);

        String args = "${sectionRows}, true, false, ${sectionBasePath}, 'store.manual.section.title', "
                + "'manual-add-button', 'store.manual.add.button', false, null, ${sectionSuccessMessage}";

        // when
        String html = templateEngine().process(SECTION.formatted(args), context);

        // then
        assertThat(html).doesNotContain("??store.manual");
        assertThat(html).contains("Manual suppliers"); // store.manual.section.title
        assertThat(html).contains("id=\"manual-add-button\"");
        assertThat(html).contains("Hurtownia X");
        // no successMessage was supplied, so the toast attribute must be absent, not "null"
        assertThat(html).doesNotContain("data-success-message");
    }

    @Test
    void rendersTheSmallErrorFragmentWithTheErrorMessageModelAttribute() {
        // given
        Context context = new Context();
        context.setVariable("errorMessage", "Supplier Elko requires field Login.");

        // when
        String html = templateEngine().process(
                "<div th:replace=\"~{fragments/supplier-section :: sectionError}\"></div>", context);

        // then
        assertThat(html).contains("notification is-danger");
        assertThat(html).contains("Supplier Elko requires field Login.");
    }
}
