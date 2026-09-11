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
 * CategoryPickerFragmentTest) instead of only asserting on the raw file text. This proves the
 * fragment's own markup compiles and renders as expected for the exact argument shapes
 * SupplierSectionModel populates on the model -- nothing more.
 *
 * <p>It does <b>not</b> prove that the controller's returned view name resolves: every render
 * here goes through {@code th:replace}, which (unlike a view name handled by Spring's
 * {@code ThymeleafView}) happily accepts positional fragment parameters. A controller returning
 * {@code "fragments/supplier-section :: supplierSection(${a}, false, ...)"} as a view name fails
 * at runtime with {@code IllegalArgumentException: Parameters in a view specification must be
 * named (non-synthetic)} even though the exact same selector renders fine through th:replace, as
 * it does in this test. SupplierSectionModel avoids that trap by returning the no-argument
 * {@code externalSection}/{@code manualSection} selectors instead (see the two tests below); the
 * two tests that still call {@code supplierSection(...)} directly with explicit arguments below
 * are only pinning that fragment's own markup, the same way store-fulfilment.html's initial
 * page render does via th:replace, not the controller's view-name path.
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
        context.setVariable("sectionAvailableSuppliers", List.of("Acme"));
        context.setVariable("sectionSuccessMessage", "Supplier Elko saved.");

        String args = "${sectionRows}, false, ${sectionShowMode}, "
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
        context.setVariable("sectionSuccessMessage", null);

        String args = "${sectionRows}, true, false, 'store.manual.section.title', "
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
    void theExternalSectionWrapperRendersTheSameMarkupAsTheParameterizedFragment() {
        // given -- exactly the model attributes SupplierSectionModel.renderExternalSection sets,
        // and the no-argument selector it now returns as the view name
        SupplierConnectionView elko = new SupplierConnectionView(
                "Elko", "Elko", "Elko", ConnectionMode.OWN, true, true, true, null, true);
        Context context = new Context();
        context.setVariable("sectionRows", List.of(elko));
        context.setVariable("sectionShowMode", true);
        context.setVariable("sectionAvailableSuppliers", List.of("Acme"));
        context.setVariable("sectionSuccessMessage", "Supplier Elko saved.");

        // when
        String html = templateEngine().process(
                "<div th:replace=\"~{fragments/supplier-section :: externalSection}\"></div>", context);

        // then -- proves the wrapper actually forwards to supplierSection with the right rows/
        // showMode/availableSuppliers/successMessage, not just that it parses
        assertThat(html).doesNotContain("??store.supplier");
        assertThat(html).contains("Suppliers");
        assertThat(html).contains("id=\"supplier-add-button\"");
        assertThat(html).contains("Elko");
        assertThat(html).contains("data-success-message=\"Supplier Elko saved.\"");
        assertThat(html).doesNotContain("disabled=\"disabled\"");
    }

    @Test
    void theManualSectionWrapperRendersTheSameMarkupAsTheParameterizedFragment() {
        // given -- exactly the model attributes SupplierSectionModel.renderManualSection sets
        SupplierConnectionView manual = new SupplierConnectionView(
                "manual:Hurtownia X", null, "Hurtownia X", ConnectionMode.MANUAL, false, false, false, null, true);
        Context context = new Context();
        context.setVariable("sectionRows", List.of(manual));
        context.setVariable("sectionSuccessMessage", null);

        // when
        String html = templateEngine().process(
                "<div th:replace=\"~{fragments/supplier-section :: manualSection}\"></div>", context);

        // then
        assertThat(html).doesNotContain("??store.manual");
        assertThat(html).contains("Manual suppliers");
        assertThat(html).contains("id=\"manual-add-button\"");
        assertThat(html).contains("Hurtownia X");
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
