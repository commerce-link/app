package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;

import static org.assertj.core.api.Assertions.assertThat;

class SettingsFormTextFieldRenderingTest {

    private static String render(String fragment) {
        return EnglishFragmentTemplateEngine.create()
                .process("<div th:replace=\"~{" + fragment + "}\"></div>", new Context())
                .replaceAll(">\\s+<", "><").trim();
    }

    @Test
    void anOptionalTextFieldWithoutHelpOrErrorRendersAsBefore() {
        // when
        String html = render("fragments/settings-form :: textField('city', 'general.cancel', 'Gdańsk', 'text', 'address-level2', false, null, null)");

        // then
        assertThat(html).isEqualTo(
                "<div class=\"cl-field\"><label class=\"cl-label\" for=\"city\"><span>Cancel</span><span class=\"cl-optional\">optional</span></label><input class=\"cl-input\" id=\"city\" name=\"city\" type=\"text\" value=\"Gdańsk\" autocomplete=\"address-level2\"/></div>");
    }

    @Test
    void aRequiredEmailFieldWithHelpAndErrorRendersAsBefore() {
        // when
        String html = render("fragments/settings-form :: textField('email', 'general.cancel', 'a@b.pl', 'email', 'email', true, 'warehouse.item.new.error.mfn', 'Shown to customers')");

        // then
        assertThat(html).isEqualTo(
                "<div class=\"cl-field\"><label class=\"cl-label\" for=\"email\"><span>Cancel</span></label><input class=\"cl-input is-invalid\" id=\"email\" name=\"email\" type=\"email\" value=\"a@b.pl\" autocomplete=\"email\" inputmode=\"email\" aria-invalid=\"true\" aria-describedby=\"email-help email-error\" required=\"required\"/><p class=\"cl-help\" id=\"email-help\">Shown to customers</p><p class=\"cl-field-error\" id=\"email-error\"><i class=\"fas fa-exclamation-circle\" aria-hidden=\"true\"></i><span>Enter the manufacturer code.</span></p></div>");
    }

    @Test
    void theFormFieldInputShorthandOfATelFieldWithAnErrorRendersAsBefore() {
        // when
        String html = render("fragments/form-field :: input('phone', 'general.cancel', null, 'tel', 'tel', true, 'warehouse.item.new.error.mfn')");

        // then
        assertThat(html).isEqualTo(
                "<div class=\"cl-field\"><label class=\"cl-label\" for=\"phone\"><span>Cancel</span></label><input class=\"cl-input is-invalid\" id=\"phone\" name=\"phone\" type=\"tel\" value=\"\" autocomplete=\"tel\" inputmode=\"tel\" aria-invalid=\"true\" aria-describedby=\"phone-error\" required=\"required\"/><p class=\"cl-field-error\" id=\"phone-error\"><i class=\"fas fa-exclamation-circle\" aria-hidden=\"true\"></i><span>Enter the manufacturer code.</span></p></div>");
    }

    @Test
    void theExtendedTextFieldCarriesItsInputModePlaceholderAndAutofocus() {
        // when
        String html = render("fragments/settings-form :: textFieldWith('ean', 'general.cancel', null, 'text', 'off', true, null, null, 'numeric', 'e.g. 5901234123457', true)");

        // then
        assertThat(html).contains("id=\"ean\"").contains("inputmode=\"numeric\"")
                .contains("placeholder=\"e.g. 5901234123457\"").contains("autofocus=\"autofocus\"");
    }

    @Test
    void theExtendedTextFieldWithoutTheNewParametersRendersLikeTheTextField() {
        // when
        String extended = render("fragments/settings-form :: textFieldWith('email', 'general.cancel', 'a@b.pl', 'email', 'email', true, 'warehouse.item.new.error.mfn', 'Shown to customers', null, null, false)");
        String plain = render("fragments/settings-form :: textField('email', 'general.cancel', 'a@b.pl', 'email', 'email', true, 'warehouse.item.new.error.mfn', 'Shown to customers')");

        // then
        assertThat(extended).isEqualTo(plain);
    }
}
