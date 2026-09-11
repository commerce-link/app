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
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.web.dtos.FulfilmentSettingsForm;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Renders fragments/fulfilment-settings-section.html through a real Thymeleaf engine (same
 * technique as SupplierSectionRenderingTest) to prove the fragment's own markup compiles and
 * behaves as expected for the exact argument shapes FulfilmentSettingsSectionModel populates on
 * the model, and that the no-argument wrapper forwards to it correctly.
 */
class FulfilmentSettingsSectionRenderingTest {

    private static final String SECTION =
            "<div th:replace=\"~{fragments/fulfilment-settings-section :: fulfilmentSettingsSection(%s)}\"></div>";

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

    private FulfilmentSettingsForm settings() {
        FulfilmentSettingsForm settings = new FulfilmentSettingsForm();
        settings.setOrderAssemblyDays(2);
        settings.setOrderRealizationDays(5);
        settings.setAutomatedFulfilment(true);
        settings.setDefaultFulfilmentType(FulfilmentType.WarehouseFulfilment);
        settings.setCanUseGlobalSuppliers(true);
        settings.setInventoryCacheTtlMinutes(30);
        return settings;
    }

    @Test
    void rendersEveryFieldAndTheSuperAdminOnlyRowsForASuperAdmin() {
        // given
        Context context = new Context();
        context.setVariable("settings", settings());
        context.setVariable("isSuperAdmin", true);
        context.setVariable("successMessage", "Settings saved.");
        context.setVariable("refreshExternalSuppliers", true);

        // when
        String html = templateEngine().process(
                SECTION.formatted("${settings}, ${isSuperAdmin}, ${successMessage}, ${refreshExternalSuppliers}"),
                context);

        // then
        assertThat(html).doesNotContain("??store");
        assertThat(html).contains("data-success-message=\"Settings saved.\"");
        assertThat(html).contains("data-refresh-external-suppliers=\"true\"");
        assertThat(html).contains("data-order-assembly-days=\"2\"");
        assertThat(html).contains("data-order-realization-days=\"5\"");
        assertThat(html).contains("data-automated-fulfilment=\"true\"");
        assertThat(html).contains("data-can-use-global-suppliers=\"true\"");
        assertThat(html).contains("data-inventory-cache-ttl-minutes=\"30\"");
        assertThat(html).contains("Use Global Supplier Settings"); // store.use.global.suppliers
        assertThat(html).contains("Inventory cache TTL (min)"); // store.inventory.cache.ttl
    }

    @Test
    void hidesTheSuperAdminOnlyRowsForAnOrdinaryAdmin() {
        // given
        Context context = new Context();
        context.setVariable("settings", settings());
        context.setVariable("isSuperAdmin", false);
        context.setVariable("successMessage", null);
        context.setVariable("refreshExternalSuppliers", false);

        // when
        String html = templateEngine().process(
                SECTION.formatted("${settings}, ${isSuperAdmin}, ${successMessage}, ${refreshExternalSuppliers}"),
                context);

        // then -- a non-super-admin must never even receive these two fields in the markup: neither
        // the table row (already pinned above) nor the data attribute a script could still read
        // them from
        assertThat(html).doesNotContain("Use Global Supplier Settings");
        assertThat(html).doesNotContain("Inventory cache TTL (min)");
        assertThat(html).doesNotContain("data-can-use-global-suppliers");
        assertThat(html).doesNotContain("data-inventory-cache-ttl-minutes");
        assertThat(html).doesNotContain("data-success-message");
        assertThat(html).contains("data-refresh-external-suppliers=\"false\"");
    }

    @Test
    void theWrapperFragmentRendersTheSameMarkupAsTheParameterizedFragment() {
        // given -- exactly the model attributes FulfilmentSettingsSectionModel.renderSection sets
        Context context = new Context();
        context.setVariable("sectionSettings", settings());
        context.setVariable("sectionIsSuperAdmin", true);
        context.setVariable("sectionSuccessMessage", "Settings saved.");
        context.setVariable("sectionRefreshExternalSuppliers", true);

        // when
        String html = templateEngine().process(
                "<div th:replace=\"~{fragments/fulfilment-settings-section :: section}\"></div>", context);

        // then
        assertThat(html).doesNotContain("??store");
        assertThat(html).contains("data-success-message=\"Settings saved.\"");
        assertThat(html).contains("data-refresh-external-suppliers=\"true\"");
        assertThat(html).contains("data-order-assembly-days=\"2\"");
    }
}
