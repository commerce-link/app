package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.dialect.SpringStandardDialect;
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;
import org.thymeleaf.messageresolver.IMessageResolver;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.StringTemplateResolver;
import pl.commercelink.products.PimCategoryOptions;

import java.text.MessageFormat;
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

    private String render(String fieldName, String selected, boolean disabled, boolean required) {
        String arguments = "'%s', %s, %s, %s, 'category-label'".formatted(
                fieldName, selected == null ? "null" : "'" + selected + "'", disabled, required);
        return templateEngine().process(PICKER.formatted(arguments), new Context());
    }

    private String renderMulti(List<PimCategoryOptions.SelectedCategory> selected, boolean required) {
        Context context = new Context();
        context.setVariable("selected", selected);
        return templateEngine().process(
                "<div th:replace=\"~{fragments/category-picker :: multiPicker('pimCategoryIds', ${selected}, "
                        + required + ", 'pimCategoryIds-label')}\"></div>", context);
    }

    private static PimCategoryOptions.SelectedCategory chosen(String id, String name) {
        return new PimCategoryOptions.SelectedCategory(id, name, null);
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

    @Test
    void tellsTheAdminHowToEnableCategoriesWhenTheStoreHasNoneEnabled() {
        // when
        String html = renderEmptyHint(List.of());

        // then
        assertThat(html).contains("/dashboard/store/categories");
        assertThat(html).doesNotContain("??catalog.category");
        assertThat(html).contains("cl-alert is-warn");
        assertThat(html).doesNotContain("notification is-warning");
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
    void realCategoryNamesAreShownAsStoredOnCategoryLists() {
        // given
        Context context = new Context();
        context.setVariable("leaf", "Karty graficzne");
        context.setVariable("legacyEnum", "CPU");
        String cell = "<td th:replace=\"~{fragments/category-picker :: categoryName(${%s})}\"></td>";

        // when
        String leaf = templateEngine().process(cell.formatted("leaf"), context);
        String legacyEnum = templateEngine().process(cell.formatted("legacyEnum"), context);

        // then
        assertThat(leaf).contains("Karty graficzne");
        assertThat(legacyEnum).contains("CPU");
    }

    @Test
    void missingCategoryRendersAsAnEmptyValue() {
        // given
        Context context = new Context();
        context.setVariable("value", null);

        // when
        String html = templateEngine().process(
                "<div th:replace=\"~{fragments/category-picker :: categoryName(${value})}\"></div>", context);

        // then
        assertThat(html).isEqualTo("<span></span>");
    }

    @Test
    void missingCategoryShowsAPlaceholderInThePicker() {
        // when
        String html = render("category", null, false, true);

        // then
        assertThat(html).contains("— wybierz kategorię —");
    }

    @Test
    void catalogDefinitionBlocksSavingWithoutACategoryButCompatibilityFiltersDoNot() {
        // when
        String definition = render("category", null, false, true);
        String compatibilityFilter = render("customAttributesFilters[0].category", "Procesory", false, false);

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
                + "'customAttributesFilters[' + ${iterStat.index} + '].category', ${filter.category}, false, false, "
                + "'customAttributeFilter-' + ${iterStat.index} + '-category-label')}\"></div>"
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
                + "<div th:replace=\"~{fragments/category-picker :: picker('category', *{category}, ${edit}, true, 'category-label')}\"></div>"
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
                + "<div th:replace=\"~{fragments/category-picker :: pickerScript(${categories}, ${ancestors})}\"></div>"
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
        context.setVariable("ancestors", List.of());
        String page = "<div th:replace=\"~{fragments/category-picker :: pickerScript(${categories}, ${ancestors})}\"></div>";

        // when
        String html = templateEngine().process(page, context);

        // then
        assertThat(html).containsOnlyOnce("Procesory");
        assertThat(html).contains("data-category-picker");
    }

    @Test
    void oneHiddenInputIsRenderedPerSavedCategoryId() {
        // when
        String html = renderMulti(List.of(
                chosen("194", "Klawiatury"),
                chosen("195", "Myszki")), false);

        // then
        assertThat(html).contains("name=\"pimCategoryIds\" value=\"194\"");
        assertThat(html).contains("name=\"pimCategoryIds\" value=\"195\"");
    }

    @Test
    void savedSelectionRendersACountingTriggerAndNamesOnlyInChips() {
        // when
        String html = renderMulti(List.of(
                chosen("194", "Klawiatury"),
                chosen("195", "Myszki")), false);

        // then
        assertThat(html).contains("data-picker-trigger");
        assertThat(html).doesNotContain("disabled");
        assertThat(html).contains("Wybrano: 2");
        assertThat(html).doesNotContain("Klawiatury, Myszki");
        assertThat(html).contains("Klawiatury");
        assertThat(html).contains("Myszki");
    }

    @Test
    void multiPickerOptionsAreInlinedAsRealJson() {
        // given
        Context context = new Context();
        context.setVariable("options", List.of(new PimCategoryOptions.CategoryOption("194", "Klawiatury", null)));
        context.setVariable("ancestors", List.of());

        // when
        String html = templateEngine().process(
                "<div th:replace=\"~{fragments/category-picker :: multiPickerScript(${options}, ${ancestors})}\"></div>", context);

        // then
        assertThat(html).contains("\"id\":\"194\"");
        assertThat(html).contains("\"name\":\"Klawiatury\"");
        assertThat(html).doesNotContain("[{}]");
    }

    @Test
    void sharedHelpersAreDefinedBeforeTheirMultiPickerConsumer() {
        // given
        Context context = new Context();
        context.setVariable("options", List.<PimCategoryOptions.CategoryOption>of());

        // when
        String html = templateEngine().process(
                "<div th:replace=\"~{fragments/category-picker :: multiPickerScript(${options}, ${ancestors})}\"></div>", context);

        // then
        int definitionIndex = html.indexOf("window.pickerHelpers =");
        int consumerIndex = html.indexOf("const {format, normalize, breadcrumbs, pathElement: optionPath, menuOf, closeMenu, initialiseOn} = window.pickerHelpers;");
        assertThat(definitionIndex).isNotNegative();
        assertThat(consumerIndex).isNotNegative();
        assertThat(definitionIndex).isLessThan(consumerIndex);
    }

    @Test
    void sharedHelpersAreDefinedBeforeTheirSinglePickerConsumer() {
        // given
        Context context = new Context();
        context.setVariable("categories", List.of("Procesory"));
        context.setVariable("ancestors", List.of());

        // when
        String html = templateEngine().process(
                "<div th:replace=\"~{fragments/category-picker :: pickerScript(${categories}, ${ancestors})}\"></div>", context);

        // then
        int definitionIndex = html.indexOf("window.pickerHelpers =");
        int consumerIndex = html.indexOf("const {format, normalize, breadcrumbs, pathElement: optionPath, menuOf, closeMenu, initialiseOn} = window.pickerHelpers;");
        assertThat(definitionIndex).isNotNegative();
        assertThat(consumerIndex).isNotNegative();
        assertThat(definitionIndex).isLessThan(consumerIndex);
    }

    @Test
    void selectedCategoriesAreRenderedAsRemovableChips() {
        // when
        String html = renderMulti(List.of(
                chosen("194", "Klawiatury"),
                chosen("195", "Myszki")), false);

        // then
        assertThat(html).contains("data-picker-chips");
        assertThat(html).contains("data-chip-remove=\"194\"");
        assertThat(html).contains("data-chip-remove=\"195\"");
    }

    /**
     * A checkbox inside a listbox option is a control inside a control: axe reports it as nested-interactive and as a
     * form field without a label, and a screen reader hears it twice. The option says it is chosen through
     * aria-selected, and the tick is a mark drawn by CSS.
     */
    @Test
    void multiPickerScriptMarksChosenOptionsWithoutACheckboxAndDrivesChips() {
        // given
        Context context = new Context();
        context.setVariable("options", List.of(new PimCategoryOptions.CategoryOption("194", "Klawiatury", null)));
        context.setVariable("ancestors", List.of());

        // when
        String html = templateEngine().process(
                "<div th:replace=\"~{fragments/category-picker :: multiPickerScript(${options}, ${ancestors})}\"></div>", context);

        // then
        assertThat(html).contains("cl-picker-option-mark").contains("'aria-selected'");
        assertThat(html).doesNotContain("checkbox").doesNotContain("'picker-option-");
        assertThat(html).contains("[data-picker-chips]");
    }

    @Test
    void multiPickerRendersNoSelectOptions() {
        // when
        String html = renderMulti(List.of(chosen("194", "Klawiatury")), true);

        // then
        assertThat(html).doesNotContain("<option");
        assertThat(html).doesNotContain("<select");
        assertThat(html).contains("data-picker-required");
    }

    @Test
    void multiPickerScriptShipsTheBranchesItsBreadcrumbsAreWalkedFrom() {
        // given
        Context context = new Context();
        context.setVariable("options", List.of(new PimCategoryOptions.CategoryOption("194", "Klawiatury", "150")));
        context.setVariable("ancestors", List.of(
                new PimCategoryOptions.CategoryOption("150", "Urządzenia do wprowadzania danych", "2833"),
                new PimCategoryOptions.CategoryOption("2833", "Komputery i urządzenia peryferyjne", null)));

        // when
        String html = templateEngine().process(
                "<div th:replace=\"~{fragments/category-picker :: multiPickerScript(${options}, ${ancestors})}\"></div>", context);

        // then
        assertThat(html).contains("\"parentId\":\"150\"");
        assertThat(html).contains("\"id\":\"150\"").contains("\"parentId\":\"2833\"");
        assertThat(html).contains("\"id\":\"2833\"").contains("\"parentId\":null");
    }

    @Test
    void singlePickerScriptShipsTheSameBranchesForItsRows() {
        // given
        Context context = new Context();
        context.setVariable("categories", List.of(new PimCategoryOptions.CategoryOption("Stoły", "Stoły", "1")));
        context.setVariable("ancestors", List.of(new PimCategoryOptions.CategoryOption("1", "Dom", null)));

        // when
        String html = templateEngine().process(
                "<div th:replace=\"~{fragments/category-picker :: pickerScript(${categories}, ${ancestors})}\"></div>", context);

        // then
        assertThat(html).contains("\"parentId\":\"1\"");
        assertThat(html).contains("\"id\":\"1\",\"name\":\"Dom\"");
    }

    @Test
    void pickersAreRenderedInTheNewDesignWithoutBulmaWidgetsOrInlineStyles() {
        // when
        String single = render("category", "Procesory", false, true);
        String multi = renderMulti(List.of(chosen("194", "Klawiatury")), false);

        // then
        assertThat(single).contains("class=\"cl-picker\"").contains("cl-picker-trigger").contains("cl-picker-menu");
        assertThat(multi).contains("class=\"cl-picker\"").contains("cl-chip-list").contains("cl-chip-tag-remove");
        assertThat(single).doesNotContain("class=\"dropdown").doesNotContain("<style").doesNotContain("button is-");
        assertThat(multi).doesNotContain("class=\"dropdown").doesNotContain("<style").doesNotContain("tag is-delete");
    }

    @Test
    void pickerScriptsCarryNoInlineStylesAndInitialiseRowsAddedLater() {
        // given
        Context context = new Context();
        context.setVariable("categories", List.of(new PimCategoryOptions.CategoryOption("1", "Stoly", null)));
        context.setVariable("ancestors", List.of());

        // when
        String single = templateEngine().process(
                "<div th:replace=\"~{fragments/category-picker :: pickerScript(${categories}, ${ancestors})}\"></div>", context);
        String multi = templateEngine().process(
                "<div th:replace=\"~{fragments/category-picker :: multiPickerScript(${categories}, ${ancestors})}\"></div>", context);

        // then
        assertThat(single).doesNotContain("<style").contains("cl:repeat-added").contains("cl:form-replaced")
                .contains("dataset.pickerReady");
        assertThat(multi).doesNotContain("<style").contains("cl:repeat-added").contains("cl:form-replaced")
                .contains("dataset.pickerReady");
    }

    /**
     * One outside-click listener per script block, not one per picker: a listener registered inside the set-up of
     * each picker stays bound to the document after an async save replaces the form, so every save would leave a
     * listener behind holding its detached fields alive.
     */
    @Test
    void eachPickerScriptRegistersOneSharedOutsideClickListener() {
        // given
        Context context = new Context();
        context.setVariable("categories", List.of(new PimCategoryOptions.CategoryOption("1", "Stoly", null)));
        context.setVariable("ancestors", List.of());

        // when
        String single = templateEngine().process(
                "<div th:replace=\"~{fragments/category-picker :: pickerScript(${categories}, ${ancestors})}\"></div>", context);
        String multi = templateEngine().process(
                "<div th:replace=\"~{fragments/category-picker :: multiPickerScript(${categories}, ${ancestors})}\"></div>", context);

        // then
        assertThat(occurrences(single, "document.addEventListener('click'")).isEqualTo(1);
        assertThat(occurrences(multi, "document.addEventListener('click'")).isEqualTo(1);
        assertThat(single).contains("[data-picker-ready]").contains("closeMenu(picker)");
        assertThat(multi).contains("[data-picker-ready]").contains("closeMenu(picker)");
    }

    private static int occurrences(String text, String token) {
        int count = 0;
        for (int at = text.indexOf(token); at >= 0; at = text.indexOf(token, at + token.length())) {
            count++;
        }
        return count;
    }

    @Test
    void breadcrumbsCollapseTheirMiddleAndFeedTheSearchIndex() {
        // given
        Context context = new Context();
        context.setVariable("options", List.of(new PimCategoryOptions.CategoryOption("1", "Stoły", null)));
        context.setVariable("ancestors", List.of());

        // when
        String html = templateEngine().process(
                "<div th:replace=\"~{fragments/category-picker :: multiPickerScript(${options}, ${ancestors})}\"></div>", context);

        // then — the row keeps the path above the name, collapses deep paths and searches over both
        assertThat(html).contains("cl-picker-path");
        assertThat(html).contains("cl-picker-name");
        assertThat(html).contains("names.length > maxLevels");
        assertThat(html).contains("haystack: normalize(option.name + ' ' + path)");
        assertThat(html).contains("cl-chip-tag-path");
    }

    // --- accessibility of the open picker (combobox with a listbox popup) ---------------------------------------

    private static String listboxOf(String html) {
        java.util.regex.Matcher listbox = java.util.regex.Pattern
                .compile("<div[^>]*role=\"listbox\"[^>]*>(.*?)</div>", java.util.regex.Pattern.DOTALL).matcher(html);
        assertThat(listbox.find()).as("a listbox is rendered").isTrue();
        return listbox.group();
    }

    /**
     * A listbox may own options only: the search field and the count paragraph inside it were reported by axe as
     * aria-required-children (critical), and the listbox had no name. The search is the combobox that controls it.
     */
    @Test
    void theListboxHoldsNothingButOptionsAndIsNamedByTheFieldLabel() {
        // when
        String single = render("category", "Procesory", false, true);
        String multi = renderMulti(List.of(chosen("194", "Klawiatury")), false);

        // then
        for (String html : List.of(single, multi)) {
            String listbox = listboxOf(html);
            assertThat(listbox).doesNotContain("<input").doesNotContain("<p");
            assertThat(listbox).containsPattern("<div[^>]*></div>");
        }
        assertThat(listboxOf(single)).contains("id=\"category-label-listbox\"").contains("aria-labelledby=\"category-label\"")
                .doesNotContain("aria-multiselectable");
        assertThat(listboxOf(multi)).contains("id=\"pimCategoryIds-label-listbox\"")
                .contains("aria-labelledby=\"pimCategoryIds-label\"").contains("aria-multiselectable=\"true\"");
        assertThat(single).containsPattern("<input[^>]*role=\"combobox\"[^>]*>")
                .contains("aria-controls=\"category-label-listbox\"").contains("aria-autocomplete=\"list\"");
        assertThat(multi).containsPattern("<input[^>]*role=\"combobox\"[^>]*>")
                .contains("aria-controls=\"pimCategoryIds-label-listbox\"");
    }

    /** The trigger is named by the field label and by what it shows: "Kategorie PIM, Wybrano: 1", not "Wybrano: 1". */
    @Test
    void theTriggerIsNamedByTheFieldLabelAndItsCurrentValue() {
        // when
        String single = render("category", "Procesory", false, true);
        String multi = renderMulti(List.of(chosen("194", "Klawiatury")), false);

        // then
        assertThat(single).contains("aria-labelledby=\"category-label category-label-value\"")
                .contains("id=\"category-label-value\"");
        assertThat(multi).contains("aria-labelledby=\"pimCategoryIds-label pimCategoryIds-label-value\"")
                .contains("id=\"pimCategoryIds-label-value\"");
    }

    /** A locked field keeps its name for a screen reader, too. */
    @Test
    void aLockedPickerIsStillNamedByTheFieldLabel() {
        // when
        String html = render("category", "CPU", true, true);

        // then
        assertThat(html).containsPattern("<input[^>]*disabled[^>]*aria-labelledby=\"category-label\"[^>]*>");
    }

    /** In a repeated filter row the ids carry the row index, so repeat-fields.js renumbers them with the row. */
    @Test
    void theIdsOfAPickerInARepeatedRowCarryTheRowIndex() {
        // given
        Context context = new Context();
        context.setVariable("filters", List.of(new ProductFilter("Procesory"), new ProductFilter("Kołdry")));
        String rows = "<div th:each=\"filter, iterStat : ${filters}\">"
                + "<div th:replace=\"~{fragments/category-picker :: picker("
                + "'customAttributesFilters[' + ${iterStat.index} + '].category', ${filter.category}, false, false, "
                + "'customAttributeFilter-' + ${iterStat.index} + '-category-label')}\"></div></div>";

        // when
        String html = templateEngine().process(rows, context);

        // then
        assertThat(html).contains("aria-labelledby=\"customAttributeFilter-1-category-label customAttributeFilter-1-category-label-value\"")
                .contains("id=\"customAttributeFilter-1-category-label-listbox\"")
                .contains("aria-controls=\"customAttributeFilter-1-category-label-listbox\"");
    }

    /** Without JavaScript the chips are all there is, so a server-rendered chip carries its path like a scripted one. */
    @Test
    void aServerRenderedChipShowsThePathOfItsCategory() {
        // when
        String html = renderMulti(List.of(
                new PimCategoryOptions.SelectedCategory("194", "Klawiatury", "Komputery \u203a Urządzenia wejścia"),
                chosen("195", "Myszki")), false);

        // then
        assertThat(html).contains("<span class=\"cl-chip-tag-path\">Komputery \u203a Urządzenia wejścia</span>");
        assertThat(occurrences(html, "cl-chip-tag-path")).isEqualTo(1);
    }

    /** The CSS puts "›" after a chip path, so the path itself is joined with the same sign, not with " > ". */
    @Test
    void breadcrumbsAreJoinedWithTheSameSeparatorTheChipAppends() {
        // given
        Context context = new Context();
        context.setVariable("options", List.of(new PimCategoryOptions.CategoryOption("1", "Stoły", null)));
        context.setVariable("ancestors", List.of());

        // when
        String html = templateEngine().process(
                "<div th:replace=\"~{fragments/category-picker :: multiPickerScript(${options}, ${ancestors})}\"></div>", context);

        // then
        assertThat(html).contains("separator = ' \\u203a '").doesNotContain("' > '");
    }

    /**
     * The popup closes when the focus leaves the picker (Tab from the search used to leave it open), Escape works from
     * anywhere inside it, and the active option is announced through aria-activedescendant. The outside-click
     * listener stays the one shared per script block.
     */
    @Test
    void bothPickerScriptsFollowTheComboboxPattern() {
        // given
        Context context = new Context();
        context.setVariable("categories", List.of(new PimCategoryOptions.CategoryOption("1", "Stoly", null)));
        context.setVariable("ancestors", List.of());

        // when
        String single = templateEngine().process(
                "<div th:replace=\"~{fragments/category-picker :: pickerScript(${categories}, ${ancestors})}\"></div>", context);
        String multi = templateEngine().process(
                "<div th:replace=\"~{fragments/category-picker :: multiPickerScript(${categories}, ${ancestors})}\"></div>", context);

        // then
        for (String html : List.of(single, multi)) {
            assertThat(html).contains("addEventListener('focusout'").contains("'aria-activedescendant'")
                    .contains("'aria-selected'").contains("search.setAttribute('aria-expanded'");
            assertThat(occurrences(html, "document.addEventListener('click'")).isEqualTo(1);
            assertThat(occurrences(html, "document.addEventListener('focusout'")).isZero();
        }
    }

    /** Removing a chip destroys the focused button: the focus goes to the trigger and a status line says what went. */
    @Test
    void removingAChipKeepsTheFocusOnTheTriggerAndAnnouncesTheRemoval() {
        // given
        Context context = new Context();
        context.setVariable("options", List.of(new PimCategoryOptions.CategoryOption("1", "Stoly", null)));
        context.setVariable("ancestors", List.of());

        // when
        String multi = renderMulti(List.of(chosen("194", "Klawiatury")), false);
        String script = templateEngine().process(
                "<div th:replace=\"~{fragments/category-picker :: multiPickerScript(${options}, ${ancestors})}\"></div>", context);

        // then
        assertThat(multi).containsPattern("<span[^>]*role=\"status\"[^>]*data-picker-status[^>]*>");
        // the message is inlined as a JavaScript string, with the Polish letters escaped
        assertThat(script).contains("removed: \"Usuni\\u0119to: {0}\"").contains("trigger.focus()");
    }
}
