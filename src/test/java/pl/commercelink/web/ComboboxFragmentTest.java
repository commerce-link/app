package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import pl.commercelink.web.dtos.ComboboxOption;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class ComboboxFragmentTest {

    private static final List<ComboboxOption> OPTIONS = List.of(
            new ComboboxOption("c1/k1", "GPU", "Parts", "Suggested"),
            new ComboboxOption("c2/k1", "Tools", "Garden"),
            new ComboboxOption("c2/k2", "Seeds", null));

    private static String render(String selected, boolean required, boolean invalid) {
        Context context = new Context();
        context.setVariable("options", OPTIONS);
        context.setVariable("selected", selected);
        context.setVariable("required", required);
        context.setVariable("invalid", invalid);
        return EnglishFragmentTemplateEngine.create().process(
                "<div th:replace=\"~{fragments/combobox :: combobox('pick', 'target', ${options}, ${selected}, 'Choose…', "
                        + "${required}, false, 'pick-help', ${invalid})}\"></div>", context);
    }

    private static String tagWith(String html, String attribute) {
        Matcher matcher = Pattern.compile("<[a-z]+\\b[^>]*" + Pattern.quote(attribute) + "[^>]*>").matcher(html);
        assertThat(matcher.find()).as(attribute).isTrue();
        return matcher.group();
    }

    @Test
    void withoutTheScriptTheFieldIsAFlatNativeSelectWithTheSecondLineAfterADash() {
        // when
        String html = render("c2/k2", true, false);

        // then
        assertThat(html).contains("<select class=\"cl-select\" id=\"pick\" name=\"target\" data-combobox-select", "required=\"required\"",
                "<option value=\"c1/k1\">GPU — Parts (suggested)</option>", "<option value=\"c2/k1\">Tools — Garden</option>");
        assertThat(html).containsPattern("<option value=\"c2/k2\"\\s+selected=\"selected\">Seeds</option>");
        assertThat(html).doesNotContain("optgroup", "role=\"group\"", "cl-picker-group");
        // the hidden input posts nothing until the script swaps the select for the combobox
        assertThat(html).contains("<input type=\"hidden\" name=\"target\" value=\"c2/k2\" disabled data-combobox-value>",
                "<div class=\"cl-picker-field cl-combobox-field\" data-combobox hidden>");
    }

    @Test
    void everyOptionHasTheLabelOnTheFirstLineAndTheNoteInGreyUnderItBothInItsName() {
        // when
        String html = render("c1/k1", true, false);

        // then
        assertThat(tagWith(html, "data-combobox-list")).contains("class=\"cl-picker-list\" role=\"listbox\" tabindex=\"-1\"",
                "id=\"pick-listbox\"", "aria-labelledby=\"pick-label\"");
        assertThat(tagWith(html, "data-value=\"c1/k1\"")).contains("class=\"cl-picker-option is-selected\"", "role=\"option\"",
                "id=\"pick-option-0\"", "aria-selected=\"true\"", "data-label=\"GPU\"", "aria-label=\"GPU, Parts, suggested\"");
        assertThat(tagWith(html, "data-value=\"c2/k1\"")).contains("class=\"cl-picker-option\"", "id=\"pick-option-1\"",
                "aria-selected=\"false\"", "aria-label=\"Tools, Garden\"");
        assertThat(tagWith(html, "data-value=\"c2/k2\"")).contains("aria-label=\"Seeds\"");
        assertThat(html).containsPattern("<span class=\"cl-picker-name\">GPU</span>\\s*<span class=\"cl-picker-meta\">Parts · <span\\s[^>]*>"
                + "<i class=\"fas fa-star\"\\s+aria-hidden=\"true\"></i> Suggested</span></span>");
        assertThat(html.split("Suggested", -1)).hasSize(2);
        assertThat(html).containsPattern("<span class=\"cl-picker-name\">Seeds</span>\\s*</span>");
        assertThat(html.split("class=\"cl-picker-meta\"", -1)).hasSize(3);
    }

    /** One text field is the combobox: it shows the chosen label, and the typed text filters the list it controls. */
    @Test
    void theTextFieldIsTheComboboxShowingTheChosenLabel() {
        // when
        String html = render("c2/k1", true, false);

        // then
        assertThat(tagWith(html, "data-combobox-input")).contains("<input class=\"cl-input cl-combobox-input\" type=\"text\"",
                "id=\"pick-input\"", "role=\"combobox\"", "aria-autocomplete=\"list\"", "aria-expanded=\"false\"",
                "autocomplete=\"off\"", "required=\"required\"", "value=\"Tools\"", "placeholder=\"Choose…\"",
                "aria-controls=\"pick-listbox\"", "aria-labelledby=\"pick-label\"", "aria-describedby=\"pick-help\"");
        assertThat(tagWith(html, "data-combobox-input")).doesNotContain("name=");
    }

    @Test
    void aValueNoOptionHoldsLeavesTheFieldEmptyWithThePlaceholder() {
        // when
        String html = render("c9/gone", true, false);

        // then
        assertThat(html).containsPattern("<option value=\"\"\\s+selected=\"selected\">Choose…</option>");
        assertThat(tagWith(html, "data-combobox-input")).contains("value=\"\"");
        assertThat(html).contains("<input type=\"hidden\" name=\"target\" value=\"\" disabled data-combobox-value>");
        assertThat(html).doesNotContain("is-selected", "aria-selected=\"true\"");
    }

    @Test
    void anOptionalFieldIsNotRequiredAndAnInvalidOneIsMarkedOnBothControls() {
        // when
        String optional = render("", false, false);
        String invalid = render("", true, true);

        // then
        assertThat(optional).doesNotContain("required", "is-invalid", "aria-invalid");
        assertThat(invalid).contains("class=\"cl-select is-invalid\"", "class=\"cl-input cl-combobox-input is-invalid\"");
        assertThat(tagWith(invalid, "data-combobox-input")).contains("aria-invalid=\"true\"");
    }
}
