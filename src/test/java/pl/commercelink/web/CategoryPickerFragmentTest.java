package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;
import org.thymeleaf.messageresolver.IMessageResolver;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryPickerFragmentTest {

    record ProductFilter(String category) {

        public String getCategory() {
            return category;
        }
    }

    private static final String PICKER = "<div th:replace=\"~{fragments/category-picker :: picker(%s)}\"></div>";

    private String render(String fieldName, String selected, boolean disabled, boolean includeServices) {
        return render(fieldName, selected, disabled, includeServices, includeServices, false);
    }

    private String render(String fieldName, String selected, boolean disabled, boolean includeServices,
                          boolean required) {
        return render(fieldName, selected, disabled, includeServices, required, false);
    }

    private String render(String fieldName, String selected, boolean disabled, boolean includeServices,
                          boolean required, boolean serviceSelected) {
        String arguments = "'%s', %s, %s, %s, %s, %s".formatted(
                fieldName, selected == null ? "null" : "'" + selected + "'", disabled, includeServices, required,
                serviceSelected);
        return templateEngine().process(PICKER.formatted(arguments), new Context());
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
            return messages.containsKey(key) ? messages.getString(key) : null;
        }

        @Override
        public String createAbsentMessageRepresentation(ITemplateContext context, Class<?> origin, String key,
                                                        Object[] parameters) {
            return "??" + key + "??";
        }
    }

    @Test
    void tellsTheAdminHowToEnableCategoriesWhenTheStoreHasNoneEnabled() {
        // when
        String html = renderEmptyHint(List.of());

        // then
        assertThat(html).contains("/dashboard/store/categories");
        assertThat(html).doesNotContain("??catalog.category");
    }

    @Test
    void doesNotShowTheHintWhenTheStoreHasEnabledCategories() {
        // when
        String html = renderEmptyHint(List.of("Karty graficzne"));

        // then
        assertThat(html).doesNotContain("/dashboard/store/categories");
    }

    @Test
    void emptyHintIsSkippedEntirelyWhenTheCategoryFieldIsLockedForEditing() {
        // given
        JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(new MockServletContext());
        IWebExchange exchange = application.buildExchange(new MockHttpServletRequest(), new MockHttpServletResponse());
        WebContext context = new WebContext(exchange);
        context.setVariable("categories", List.of());
        context.setVariable("edit", true);

        // when
        String html = templateEngine().process(
                "<div th:unless=\"${edit}\">"
                        + "<div th:replace=\"~{fragments/category-picker :: emptyHint(${categories})}\"></div>"
                        + "</div>", context);

        // then
        assertThat(html).doesNotContain("/dashboard/store/categories");
    }

    private String renderEmptyHint(List<String> categories) {
        JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(new MockServletContext());
        IWebExchange exchange = application.buildExchange(new MockHttpServletRequest(), new MockHttpServletResponse());

        WebContext context = new WebContext(exchange);
        context.setVariable("categories", categories);

        return templateEngine().process(
                "<div th:replace=\"~{fragments/category-picker :: emptyHint(${categories})}\"></div>", context);
    }

    @Test
    void serviceDefinitionIsShownUnderItsLocalizedNameOnCategoryLists() {
        // given
        Context context = new Context();
        context.setVariable("value", null);
        context.setVariable("service", true);

        // when
        String html = templateEngine().process(
                "<td th:replace=\"~{fragments/category-picker :: categoryName(${value}, ${service})}\"></td>", context);

        // then
        assertThat(html).contains("Usługi");
    }

    @Test
    void realCategoryNamesAreShownAsStoredOnCategoryLists() {
        // given
        Context context = new Context();
        context.setVariable("leaf", "Karty graficzne");
        context.setVariable("legacyEnum", "CPU");
        String cell = "<td th:replace=\"~{fragments/category-picker :: categoryName(${%s}, false)}\"></td>";

        // when
        String leaf = templateEngine().process(cell.formatted("leaf"), context);
        String legacyEnum = templateEngine().process(cell.formatted("legacyEnum"), context);

        // then
        assertThat(leaf).contains("Karty graficzne");
        assertThat(legacyEnum).contains("CPU");
    }

    @Test
    void missingCategoryShowsAPlaceholderAndIsNeverPrefilledWithServices() {
        // when
        String html = render("category", null, false, true);

        // then
        assertThat(html).contains("— wybierz kategorię —");
        assertThat(html).doesNotContain("value=\"Services\"");
    }

    @Test
    void serviceDefinitionIsShownUnderItsLocalizedNameInThePicker() {
        // when
        String html = render("category", null, false, true, true, true);

        // then
        assertThat(html).contains("Usługi");
        assertThat(html).doesNotContain("Services\"");
    }

    @Test
    void serviceStateIsCarriedByAHiddenServiceField() {
        // when
        String withServices = render("category", null, false, true, true, true);
        String withoutServices = render("customAttributesFilters[0].category", "Procesory", false, false);

        // then
        assertThat(withServices).contains("name=\"service\"");
        assertThat(withServices).contains("data-picker-service");
        assertThat(withoutServices).doesNotContain("name=\"service\"");
    }

    @Test
    void disabledServiceDefinitionShowsItsLocalizedNameInsteadOfABlank() {
        // when
        String html = render("category", null, true, true, true, true);

        // then
        assertThat(html).contains("Usługi");
        assertThat(html).doesNotContain("data-picker-trigger");
    }

    @Test
    void catalogDefinitionBlocksSavingWithoutACategoryButCompatibilityFiltersDoNot() {
        // when
        String definition = render("category", null, false, true, true);
        String compatibilityFilter = render("customAttributesFilters[0].category", "Procesory", false, false, false);

        // then
        assertThat(definition).contains("data-picker-required");
        assertThat(compatibilityFilter).doesNotContain("data-picker-required");
    }

    @Test
    void savedCategoryIsCarriedByAHiddenInputBoundToTheFormField() {
        // given
        String fieldName = "customAttributesFilters[0].category";

        // when
        String html = render(fieldName, "Procesory", false, false);

        // then
        assertThat(html).contains("name=\"customAttributesFilters[0].category\"");
        assertThat(html).contains("value=\"Procesory\"");
    }

    @Test
    void categoriesAreNotRenderedAsSelectOptions() {
        // when
        String html = render("category", "Procesory", false, true);

        // then
        assertThat(html).doesNotContain("<option");
        assertThat(html).doesNotContain("<select");
    }

    @Test
    void disabledPickerKeepsTheSavedCategoryAndOffersNoSearch() {
        // when
        String html = render("category", "CPU", true, true);

        // then
        assertThat(html).contains("value=\"CPU\"");
        assertThat(html).doesNotContain("data-picker-trigger");
        assertThat(html).doesNotContain("data-picker-search");
    }

    @Test
    void pickerInARepeatedRowBindsToTheIndexedFormField() {
        // given
        Context context = new Context();
        context.setVariable("filters", List.of(new ProductFilter("Procesory"), new ProductFilter("Kołdry")));
        String rows = "<table><tr th:each=\"filter, iterStat : ${filters}\"><td>"
                + "<div th:replace=\"~{fragments/category-picker :: picker("
                + "'customAttributesFilters[' + ${iterStat.index} + '].category', ${filter.category}, false, false, false, false)}\"></div>"
                + "</td></tr></table>";

        // when
        String html = templateEngine().process(rows, context);

        // then
        assertThat(html).contains("name=\"customAttributesFilters[0].category\"");
        assertThat(html).contains("value=\"Procesory\"");
        assertThat(html).contains("name=\"customAttributesFilters[1].category\"");
        assertThat(html).contains("value=\"Kołdry\"");
        assertThat(html).doesNotContain("<option");
    }

    @Test
    void pickerBoundToTheFormObjectKeepsTheCategoryWhenEditingIsBlocked() {
        // given
        Context context = new Context();
        context.setVariable("categoryDefinition", new ProductFilter("CPU"));
        context.setVariable("edit", true);
        String form = "<form th:object=\"${categoryDefinition}\">"
                + "<div th:replace=\"~{fragments/category-picker :: picker('category', *{category}, ${edit}, true, true, false)}\"></div>"
                + "</form>";

        // when
        String html = templateEngine().process(form, context);

        // then
        assertThat(html).contains("name=\"category\"");
        assertThat(html).contains("value=\"CPU\"");
        assertThat(html).doesNotContain("data-picker-trigger");
    }

    @Test
    void categoryDataIsNotShippedWhenTheFieldIsLockedForEditing() {
        // given
        Context context = new Context();
        context.setVariable("categories", List.of("Procesory"));
        context.setVariable("edit", true);
        String page = "<div th:unless=\"${edit}\">"
                + "<div th:replace=\"~{fragments/category-picker :: pickerScript(${categories})}\"></div>"
                + "</div>";

        // when
        String html = templateEngine().process(page, context);

        // then
        assertThat(html).doesNotContain("Procesory");
        assertThat(html).doesNotContain("<script");
    }

    @Test
    void categoriesAreShippedOnceToTheBrowserInsteadOfPerRow() {
        // given
        Context context = new Context();
        context.setVariable("categories", List.of("Procesory", "Kołdry"));
        String page = "<div th:replace=\"~{fragments/category-picker :: pickerScript(${categories})}\"></div>";

        // when
        String html = templateEngine().process(page, context);

        // then
        assertThat(html).containsOnlyOnce("Procesory");
        assertThat(html).contains("data-category-picker");
    }

    @Test
    void servicesOptionIsOfferedOnlyWhenRequested() {
        // when
        String withServices = render("category", "Procesory", false, true);
        String withoutServices = render("customAttributesFilters[0].category", "Procesory", false, false);

        // then
        assertThat(withServices).contains("data-picker-services");
        assertThat(withoutServices).doesNotContain("data-picker-services");
    }
}
