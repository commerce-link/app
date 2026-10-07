package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import pl.commercelink.web.dtos.ComboboxGroup;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class ComboboxFragmentTest {

    private static final List<ComboboxGroup> GROUPS = List.of(
            new ComboboxGroup("Matching", List.of(new ComboboxGroup.Option("c1/k1", "Parts › GPU", "already here"))),
            new ComboboxGroup("Garden", List.of(
                    new ComboboxGroup.Option("c2/k1", "Garden › Tools", null),
                    new ComboboxGroup.Option("c2/k2", "Garden › Seeds", null))));

    private static String render(String selected, boolean required, boolean invalid) {
        Context context = new Context();
        context.setVariable("groups", GROUPS);
        context.setVariable("selected", selected);
        context.setVariable("required", required);
        context.setVariable("invalid", invalid);
        return EnglishFragmentTemplateEngine.create().process(
                "<div th:replace=\"~{fragments/combobox :: combobox('pick', 'target', ${groups}, ${selected}, 'Choose…', "
                        + "${required}, false, 'pick-help', ${invalid})}\"></div>", context);
    }

    private static String tagWith(String html, String attribute) {
        Matcher matcher = Pattern.compile("<[a-z]+\\b[^>]*" + Pattern.quote(attribute) + "[^>]*>").matcher(html);
        assertThat(matcher.find()).as(attribute).isTrue();
        return matcher.group();
    }

    @Test
    void withoutTheScriptTheFieldIsANativeSelectWithAnOptgroupPerGroupAndTheNoteInBrackets() {
        // when
        String html = render("c2/k2", true, false);

        // then
        assertThat(html).contains("<select class=\"cl-select\" id=\"pick\" name=\"target\" data-combobox-select", "required=\"required\"",
                "<optgroup label=\"Matching\">", "<option value=\"c1/k1\">Parts › GPU (already here)</option>",
                "<optgroup label=\"Garden\">", "<option value=\"c2/k1\">Garden › Tools</option>");
        assertThat(html).containsPattern("<option value=\"c2/k2\"\\s+selected=\"selected\">Garden › Seeds</option>");
        // the hidden input posts nothing until the script swaps the select for the combobox
        assertThat(html).contains("<input type=\"hidden\" name=\"target\" value=\"c2/k2\" disabled data-combobox-value>",
                "<div class=\"cl-picker-field\" data-combobox hidden>");
    }

    @Test
    void theComboboxGroupsItsOptionsUnderNamedHeadingsWithTheNoteOnASecondLine() {
        // when
        String html = render("c1/k1", true, false);

        // then
        assertThat(tagWith(html, "data-combobox-list")).contains("class=\"cl-picker-list\" role=\"listbox\" tabindex=\"-1\"",
                "id=\"pick-listbox\"", "aria-labelledby=\"pick-label\"");
        assertThat(html).contains(
                "<div class=\"cl-picker-group\" role=\"group\" aria-labelledby=\"pick-group-0\">",
                "<div class=\"cl-picker-group-label\" role=\"presentation\" id=\"pick-group-0\">Matching</div>",
                "<div class=\"cl-picker-group-label\" role=\"presentation\" id=\"pick-group-1\">Garden</div>",
                "<span class=\"cl-picker-meta\">already here</span>");
        assertThat(tagWith(html, "data-value=\"c1/k1\"")).contains("class=\"cl-picker-option is-selected\"", "role=\"option\"",
                "id=\"pick-option-0-0\"", "aria-selected=\"true\"", "data-label=\"Parts › GPU\"");
        assertThat(tagWith(html, "data-value=\"c2/k2\"")).contains("class=\"cl-picker-option\"", "id=\"pick-option-1-1\"",
                "aria-selected=\"false\"", "data-label=\"Garden › Seeds\"");
        assertThat(html.split("cl-picker-meta", -1)).hasSize(2);
    }

    @Test
    void theTriggerShowsTheChosenLabelAndIsNamedByTheFieldLabelAndItsValue() {
        // when
        String html = render("c2/k1", true, false);

        // then
        assertThat(tagWith(html, "data-combobox-trigger")).contains("<button type=\"button\" class=\"cl-picker-trigger\"",
                "id=\"pick-trigger\"", "aria-haspopup=\"listbox\"", "aria-expanded=\"false\"",
                "aria-labelledby=\"pick-label pick-value\"", "aria-describedby=\"pick-help\"", "data-combobox-placeholder=\"Choose…\"");
        assertThat(html).contains("<span data-combobox-label id=\"pick-value\">Garden › Tools</span>",
                "role=\"combobox\" aria-autocomplete=\"list\" aria-expanded=\"false\"",
                "aria-controls=\"pick-listbox\" aria-labelledby=\"pick-label\" aria-required=\"true\"");
    }

    @Test
    void aValueNoOptionHoldsLeavesTheFieldEmptyWithThePlaceholder() {
        // when
        String html = render("c9/gone", true, false);

        // then
        assertThat(html).containsPattern("<option value=\"\"\\s+selected=\"selected\">Choose…</option>");
        assertThat(html).contains("<span data-combobox-label id=\"pick-value\">Choose…</span>",
                "<input type=\"hidden\" name=\"target\" value=\"\" disabled data-combobox-value>");
        assertThat(html).doesNotContain("is-selected", "aria-selected=\"true\"");
    }

    @Test
    void anOptionalFieldIsNotRequiredAndAnInvalidOneIsMarkedOnBothControls() {
        // when
        String optional = render("", false, false);
        String invalid = render("", true, true);

        // then
        assertThat(optional).doesNotContain("required", "is-invalid", "aria-invalid");
        assertThat(invalid).contains("class=\"cl-select is-invalid\"", "aria-invalid=\"true\"",
                "class=\"cl-picker-trigger is-invalid\"");
    }

    @Test
    void theCountIsAStatusLineFilledByTheScriptFromItsTemplate() {
        // when
        String html = render("", true, false);

        // then
        assertThat(html).containsPattern("<p class=\"cl-help cl-picker-count\" role=\"status\" data-combobox-count\\s+"
                + "data-combobox-count-template=\"\\{0} of \\{1}\"></p>");
        assertThat(html).contains("<p class=\"cl-picker-empty\" data-combobox-empty hidden>No entries match your search</p>");
    }
}
