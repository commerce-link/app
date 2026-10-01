package pl.commercelink.web.deliveries.create;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.commercelink.web.deliveries.create.DeliveryCreateTemplates.*;

class DeliveryCreateManualTemplateTest {

    private static Map<String, Object> warehouse(Map<String, String> errors) {
        var form = warehouseForm();
        form.setSuggestedItems(new java.util.ArrayList<>());
        Map<String, Object> variables = model(warehousePage(false, true, false), form);
        variables.put("errors", errors);
        return variables;
    }

    @Test
    void warehouseRecordStepAsksForTheOrderDataFirstAndListsTheItemsReadOnly() {
        // when
        String html = render("deliveries/create/manual", warehouse(Map.of()));

        // then
        assertThat(occurrences(html, "<h1")).isEqualTo(1);
        assertThat(html).contains("Zarejestruj zamówienie u Acme").contains("Krok 2 z 2")
                .contains("przepisz dane z potwierdzenia od dostawcy").contains("Dane zamówienia")
                .contains("Numer i termin są wymagane").contains("for=\"externalDeliveryId\"").contains("for=\"estimatedDeliveryAt\"")
                .contains("for=\"sourceCurrency\"").contains("<option value=\"PLN\" selected=\"selected\">PLN</option>")
                .contains("Mnożnik VAT").contains("1,23 dla 23% VAT").contains("Termin płatności (dni)")
                .contains("Zmień pozycje").contains("AMD Ryzen 7 9800X3D").contains("Zapisz dostawę")
                .contains("action=\"/dashboard/deliveries/create/Acme/manual/save\"")
                .contains("formaction=\"/dashboard/deliveries/create/Acme/back\"")
                .contains("Towar przyjedzie na adres, który podałeś dostawcy.")
                .doesNotContain("cl-optional").doesNotContain("??");
        assertThat(html.indexOf("Dane zamówienia")).isLessThan(html.indexOf("Zmień pozycje"));
        assertThat(html).containsPattern("id=\"externalDeliveryId\"[^>]*required")
                .containsPattern("id=\"estimatedDeliveryAt\"[^>]*required");
    }

    @Test
    void serverValidatesTheRecordStepSoItsErrorsReachTheSummaryAndTheFields() {
        // when
        String html = render("deliveries/create/manual", warehouse(Map.of()));

        // then: "required" stays as a hint for assistive technology, but the browser must not stop the post with its
        // own bubble before the server can answer with the error summary and the errors at the fields
        int form = html.indexOf("<form");
        assertThat(html.substring(form, html.indexOf(">", form))).contains("data-cl-delivery-manual").contains("novalidate");
    }

    @Test
    void dropshipRecordStepMarksEveryFieldOptional() {
        // given
        Map<String, Object> variables = model(dropshipPage(false, null, true), dropshipForm());
        variables.put("errors", Map.of());

        // when
        String html = render("deliveries/create/manual", variables);

        // then
        assertThat(html).contains("Wszystkie pola są opcjonalne").contains("cl-optional")
                .contains("Punkt odbioru").contains("name=\"order\"")
                .doesNotContainPattern("id=\"externalDeliveryId\"[^>]*required");
    }

    @Test
    void errorsAreSummarisedAboveAndShownAtEachField() {
        // given
        Map<String, String> errors = new LinkedHashMap<>();
        errors.put("externalDeliveryId", "deliveries.create.error.orderNumber");
        errors.put("shippingCost", "deliveries.create.error.number");

        // when
        String html = render("deliveries/create/manual", warehouse(errors));

        // then
        assertThat(html).contains("data-cl-error-summary").contains("href=\"#externalDeliveryId\"")
                .contains("href=\"#shippingCost\"").contains("id=\"externalDeliveryId-error\"")
                .contains("id=\"shippingCost-error\"").contains("Wpisz numer zamówienia u dostawcy.").contains("Wpisz liczbę.")
                .containsPattern("id=\"externalDeliveryId\"[^>]*aria-invalid=\"true\"");
        assertThat(html.indexOf("data-cl-error-summary")).isLessThan(html.indexOf("id=\"delivery-step-form\""));
    }

    @Test
    void recordStepPostsEveryFieldOnceAndCarriesStepOne() {
        // when
        String html = render("deliveries/create/manual", warehouse(Map.of()));

        // then
        assertThat(fieldNames(html)).doesNotHaveDuplicates().contains("externalDeliveryId", "estimatedDeliveryAt",
                "sourceCurrency", "shippingCost", "paymentCost", "tax", "paymentTerms", "removeUnselected",
                "items[0].requestedQty", "items[0].allocations[0].selected");
    }

    @Test
    void enterInAFieldSavesTheDelivery() {
        // when
        String html = render("deliveries/create/manual", warehouse(Map.of()));

        // then: implicit submission uses the first submit button in tree order, so the header's back button and
        // "Zmień pozycje" are plain buttons and "Zapisz dostawę" is the first submit
        assertThat(html.substring(0, html.indexOf("id=\"save-button\""))).doesNotContain("type=\"submit\"");
        String form = html.substring(html.indexOf("id=\"delivery-step-form\""), html.indexOf("</form>"));
        int firstSubmit = form.indexOf("type=\"submit\"");
        assertThat(form.substring(form.lastIndexOf("<button", firstSubmit), form.indexOf(">", firstSubmit)))
                .contains("id=\"save-button\"");
    }
}
