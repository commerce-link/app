package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import pl.commercelink.web.dtos.FlatPickerOption;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class OptionPickerFragmentTest {

    private static final List<FlatPickerOption> OPTIONS = List.of(
            new FlatPickerOption("c1/k1", "GPU", "Parts", "Suggested"),
            new FlatPickerOption("c2/k1", "Tools", "Garden"),
            new FlatPickerOption("c2/k2", "Seeds", null));

    private static String render(String selected, boolean required, boolean invalid) {
        Context context = new Context();
        context.setVariable("options", OPTIONS);
        context.setVariable("selected", selected);
        context.setVariable("required", required);
        context.setVariable("invalid", invalid);
        return EnglishFragmentTemplateEngine.create().process(
                "<div th:replace=\"~{fragments/category-picker :: optionPicker('pick', 'target', ${options}, ${selected}, "
                        + "'Choose a catalog category', 'Filter…', ${required}, false, 'pick-help', ${invalid})}\"></div>",
                context);
    }

    private static String tagWith(String html, String attribute) {
        Matcher matcher = Pattern.compile("<[a-z]+\\b[^>]*" + Pattern.quote(attribute) + "[^>]*>").matcher(html);
        assertThat(matcher.find()).as(attribute).isTrue();
        return matcher.group();
    }

    @Test
    void withoutTheScriptTheFieldIsANativeSelectWithTheCatalogAndSuggestionInTheOptionText() {
        // when
        String html = render("c2/k2", true, false);

        // then
        assertThat(tagWith(html, "data-picker-select")).contains("id=\"pick\"", "name=\"target\"", "required=\"required\"");
        assertThat(html).contains("<option value=\"c1/k1\">GPU — Parts (suggested)</option>",
                "<option value=\"c2/k1\">Tools — Garden</option>");
        assertThat(html).containsPattern("<option value=\"c2/k2\"\\s+selected=\"selected\">Seeds</option>");
        // the hidden input posts nothing and the picker stays hidden until the script takes the select's place
        assertThat(html).contains("<input type=\"hidden\" name=\"target\" value=\"c2/k2\" disabled data-picker-value>");
        assertThat(tagWith(html, "data-option-picker")).contains("class=\"cl-picker-field\"", "hidden");
    }

    @Test
    void theTriggerReadsAsTheTreePickerAndShowsThePlaceholderOrTheChosenName() {
        // when
        String empty = render("", true, false);
        String chosen = render("c1/k1", true, false);

        // then
        assertThat(empty).containsPattern("<div class=\"cl-picker is-flat\">");
        assertThat(tagWith(empty, "data-picker-trigger")).contains("class=\"cl-picker-trigger\"", "aria-haspopup=\"listbox\"",
                "aria-labelledby=\"pick-label pick-value\"", "aria-describedby=\"pick-help\"");
        assertThat(empty).contains("<span data-picker-label id=\"pick-value\">Choose a catalog category</span>");
        assertThat(chosen).contains("<span data-picker-label id=\"pick-value\">GPU</span>");
        assertThat(tagWith(empty, "data-picker-search")).contains("role=\"combobox\"", "aria-controls=\"pick-listbox\"",
                "placeholder=\"Filter…\"");
        assertThat(empty).contains("of", "data-picker-count=\"{0} of {1} categories\"");
    }

    @Test
    void eachOptionShowsItsCatalogInGreyAboveTheNameAndOnlySuggestedOnesAreStarred() {
        // when
        String html = render("c1/k1", true, false);

        // then
        assertThat(html).containsPattern("<span class=\"cl-picker-path\" title=\"Parts\">Parts · <span\\s+class=\"cl-picker-star\">"
                + "<i class=\"fas fa-star\"\\s+aria-hidden=\"true\"></i> Suggested</span></span>\\s*"
                + "<span class=\"cl-picker-name\">GPU</span>");
        assertThat(html).containsPattern("<span class=\"cl-picker-path\" title=\"Garden\">Garden</span>\\s*"
                + "<span class=\"cl-picker-name\">Tools</span>");
        assertThat(html).containsPattern("<span class=\"cl-picker-option-text\">\\s*<span class=\"cl-picker-name\">Seeds</span>");
        assertThat(html.split(">[^<]*Suggested<", -1)).hasSize(2);
        assertThat(tagWith(html, "data-value=\"c1/k1\"")).contains("class=\"cl-picker-option is-selected\"",
                "aria-selected=\"true\"", "aria-label=\"GPU, Parts, suggested\"");
        assertThat(tagWith(html, "data-value=\"c2/k1\"")).contains("aria-selected=\"false\"", "aria-label=\"Tools, Garden\"");
        assertThat(html).doesNotContain("cl-picker-option-mark");
    }

    @Test
    void aValueNoOptionHoldsShowsThePlaceholderAndAnInvalidFieldIsMarkedOnBothControls() {
        // when
        String gone = render("c9/gone", true, false);
        String optional = render("", false, false);
        String invalid = render("", true, true);

        // then
        assertThat(gone).containsPattern("<option value=\"\"\\s+selected=\"selected\">Choose a catalog category</option>");
        assertThat(gone).contains("<input type=\"hidden\" name=\"target\" value=\"\" disabled data-picker-value>");
        assertThat(gone).doesNotContain("is-selected", "aria-selected=\"true\"");
        assertThat(optional).doesNotContain("required", "data-picker-required", "is-invalid", "aria-invalid");
        assertThat(tagWith(invalid, "data-picker-select")).contains("is-invalid", "aria-invalid=\"true\"");
        assertThat(tagWith(invalid, "data-picker-trigger")).contains("cl-picker-trigger is-invalid", "aria-invalid=\"true\"");
        assertThat(tagWith(invalid, "data-option-picker")).contains("data-picker-required=\"true\"");
    }
}
