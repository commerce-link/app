package pl.commercelink.web.deliveries.create;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.deliveries.PurchaseValidation;
import pl.commercelink.inventory.supplier.api.SupplierDeliveryAddress;
import pl.commercelink.inventory.supplier.api.SupplierOrderOption;
import pl.commercelink.inventory.supplier.api.SupplierOrderOptionChoice;
import pl.commercelink.web.dtos.DeliveryCreationForm;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.commercelink.web.deliveries.create.DeliveryCreateTemplates.*;

class DeliveryCreatePurchaseTemplateTest {

    private static Map<String, Object> warehouse(List<SupplierDeliveryAddress> addresses) {
        DeliveryCreationForm form = warehouseForm();
        form.setSuggestedItems(new java.util.ArrayList<>());
        Map<String, Object> variables = model(warehousePage(false, true, false), form);
        variables.put("purchaseRef", "ref-1");
        variables.put("requiresApproval", false);
        variables.put("deliveryAddresses", addresses);
        variables.put("orderOptions", List.of(new SupplierOrderOption("pay", "Płatność",
                List.of(new SupplierOrderOptionChoice("transfer", "Przelew 14 dni", null)), null, true)));
        variables.put("selectedOptions", Map.of());
        return variables;
    }

    private static List<SupplierDeliveryAddress> addresses(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(n -> new SupplierDeliveryAddress("a-" + n, "ul. Składowa " + n, "Pruszków", "05-800", "PL"))
                .toList();
    }

    @Test
    void warehouseConfirmationAsksTheSupplierAndListsTheAddressesInACard() {
        // when
        String html = render("deliveries/create/purchase", warehouse(addresses(2)));

        // then
        assertThat(occurrences(html, "<h1")).isEqualTo(1);
        assertThat(html).contains("Potwierdź zamówienie u Acme").contains("Krok 2 z 2")
                .contains("sprawdź dostępność, wybierz adres i opcje, potem zamów")
                .contains("Dostępność i ceny u dostawcy").contains("id=\"validation-area\"").contains("aria-live=\"polite\"")
                .contains("data-validate-url=\"/dashboard/deliveries/create/Acme/purchase/validate\"")
                .contains("action=\"/dashboard/deliveries/create/Acme/purchase/confirm\"")
                .contains("Adres dostawy").contains("name=\"deliveryAddressId\" value=\"a-1\"")
                .contains("ul. Składowa 1").contains("Pruszków")
                .contains("Opcje zamówienia u dostawcy").contains("name=\"supplierOrderChoices[pay]\"")
                .contains("name=\"purchaseRef\" value=\"ref-1\"").contains("Zamów u Acme")
                .contains("Zanim zamówisz:").contains("Wróć do pozycji")
                .doesNotContain("addressModal").doesNotContain("Koszty dostawy (opcjonalnie)").doesNotContain("??");
        assertThat(html).doesNotContain("id=\"address-filter\"");
        int submit = html.indexOf("id=\"purchase-confirm-submit\"");
        assertThat(html.substring(html.lastIndexOf("<button", submit), html.indexOf(">", submit))).contains("disabled");
    }

    @Test
    void theOrderButtonIsTheFirstSubmitAndEveryOtherWayBackIsAPlainButton() {
        // when
        String html = render("deliveries/create/purchase", warehouse(addresses(2)));

        // then
        assertThat(html.substring(0, html.indexOf("id=\"purchase-confirm-submit\""))).doesNotContain("type=\"submit\"");
        assertThat(html).contains("id=\"back-submit\"").contains("data-cl-back-submit=\"back-submit\"");
        int back = html.indexOf("id=\"back-submit\"");
        assertThat(html.substring(html.lastIndexOf("<button", back), html.indexOf(">", back)))
                .contains("formaction=\"/dashboard/deliveries/create/Acme/back\"");
    }

    @Test
    void moreThanSixAddressesGetAFilter() {
        // when
        String html = render("deliveries/create/purchase", warehouse(addresses(7)));

        // then
        assertThat(html).contains("id=\"address-filter\"").contains("Szukaj adresu").contains("data-cl-address");
    }

    @Test
    void theChosenAddressIsChecked() {
        // given
        Map<String, Object> variables = warehouse(addresses(3));
        ((DeliveryCreationForm) variables.get("form")).setDeliveryAddressId("a-2");

        // when
        String html = render("deliveries/create/purchase", variables);

        // then
        assertThat(html).containsPattern("value=\"a-2\"[^>]*checked=\"checked\"");
        assertThat(occurrences(html, "checked=\"checked\"")).isEqualTo(1);
    }

    @Test
    void noAddressBookMeansTheSuppliersDefaultAndAFailureBlocksTheOrder() {
        // given
        Map<String, Object> empty = warehouse(List.of());
        Map<String, Object> failed = warehouse(List.of());
        failed.put("deliveryAddressError", "timeout");

        // when
        String emptyHtml = render("deliveries/create/purchase", empty);
        String failedHtml = render("deliveries/create/purchase", failed);

        // then
        assertThat(emptyHtml).contains("Dostawca wyśle na adres przypisany do konta sklepu.").doesNotContain("id=\"address-required\"");
        assertThat(failedHtml).contains("id=\"address-blocked\"").contains("(timeout)");
    }

    @Test
    void globalSupplierSkipsAddressAndOptionsAndSubmitsForApproval() {
        // given
        Map<String, Object> variables = model(warehousePage(false, true, true), warehouseForm());
        variables.put("purchaseRef", "ref-1");
        variables.put("requiresApproval", true);

        // when
        String html = render("deliveries/create/purchase", variables);

        // then
        assertThat(html).contains("cl-alert is-info").contains("Zgłoś do realizacji")
                .contains("Po akceptacji zamówienie wyśle administrator platformy.")
                .doesNotContain("Adres dostawy").doesNotContain("Opcje zamówienia u dostawcy");
    }

    @Test
    void dropshipConfirmationShowsTheCustomerAndNoCostFields() {
        // given
        Map<String, Object> variables = model(dropshipPage(false, null, false), dropshipForm());
        variables.put("purchaseRef", "ref-1");
        variables.put("requiresApproval", false);

        // when
        String html = render("deliveries/create/purchase", variables);

        // then: ordering through the integration asks for no order data; typed values only ride along for the way back
        assertThat(html).contains("Dropshipping").contains("Dane adresowe klienta")
                .doesNotContain("Koszty dostawy (opcjonalnie)").doesNotContain("id=\"shippingCost\"")
                .doesNotContain("Mnożnik VAT").doesNotContain("Adres dostawy");
        assertThat(html).contains("type=\"hidden\" name=\"shippingCost\"").contains("type=\"hidden\" name=\"tax\"");
        assertThat(fieldNames(html)).doesNotHaveDuplicates()
                .contains("order", "externalDeliveryId", "estimatedDeliveryAt", "shippingCost", "paymentCost",
                        "paymentTerms", "tax", "sourceCurrency", "items[0].allocations[0].selected", "purchaseRef");
    }

    @Test
    void warehouseConfirmationCarriesStepOneAndTheOrderDataOnce() {
        // when
        String html = render("deliveries/create/purchase", warehouse(addresses(1)));

        // then
        assertThat(fieldNames(html)).doesNotHaveDuplicates()
                .contains("removeUnselected", "items[0].requestedQty", "items[0].unitCost",
                        "items[0].allocations[0].key.orderId", "items[0].allocations[0].selected",
                        "externalDeliveryId", "estimatedDeliveryAt", "shippingCost", "tax");
    }

    @Test
    void optionsErrorBlocksTheOrder() {
        // given
        Map<String, Object> variables = warehouse(addresses(1));
        variables.put("orderOptionsError", "timeout");

        // when
        String html = render("deliveries/create/purchase", variables);

        // then
        assertThat(html).contains("id=\"order-options-blocked\"").contains("(timeout)");
        int submit = html.indexOf("id=\"purchase-confirm-submit\"");
        assertThat(html.substring(html.lastIndexOf("<button", submit), html.indexOf(">", submit))).contains("data-blocked=\"true\"");
    }

    @Test
    void dropshipConfirmationCarriesTheEstimatedDeliveryDateOnlyAsTheHiddenStepTwoValue() {
        // given
        Map<String, Object> variables = model(dropshipPage(false, null, false), dropshipForm());
        variables.put("purchaseRef", "ref-1");
        variables.put("requiresApproval", false);

        // when
        String html = render("deliveries/create/purchase", variables);

        // then
        assertThat(occurrences(html, "name=\"estimatedDeliveryAt\"")).isEqualTo(1);
        assertThat(html).containsPattern("<input type=\"hidden\" name=\"estimatedDeliveryAt\"")
                .doesNotContain("for=\"estimatedDeliveryAt\"");
    }

    @Test
    void confirmationGivesTheProductColumnAMinimumWidth() {
        // given
        Map<String, Object> variables = warehouse(addresses(1));
        variables.put("validation", new PurchaseValidation("Acme", "ref-1", "PLN", 1198.0, true, List.of(
                new PurchaseValidation.Line("AMD Ryzen 7 9800X3D", "sku", "5901234123457", "MFN", 2, 2, 579.5, 599.0))));

        // when
        String html = fragment("deliveries/create/purchase :: validationResult", variables);

        // then
        assertThat(html).contains("col class=\"cl-col-key\"");
    }

    @Test
    void failedSubmitShowsTheErrorAboveTheCards() {
        // given
        Map<String, Object> variables = warehouse(addresses(1));
        variables.put("failureMessage", "Złożenie zamówienia u dostawcy nie powiodło się.");

        // when
        String html = render("deliveries/create/purchase", variables);

        // then
        assertThat(html).contains("cl-alert is-bad").contains("Złożenie zamówienia u dostawcy nie powiodło się.");
    }

    @Test
    void availabilityFragmentMarksMissingQuantitiesAndTellsTheScriptTheVerdict() {
        // given
        PurchaseValidation validation = new PurchaseValidation("Acme", "ref-1", "PLN", 1198.0, false, List.of(
                new PurchaseValidation.Line("AMD Ryzen 7 9800X3D", "sku", "5901234123457", "MFN", 2, 1, 579.5, 599.0)));
        Map<String, Object> variables = model(warehousePage(false, true, false), warehouseForm());
        variables.put("validation", validation);

        // when
        String html = fragment("deliveries/create/purchase :: validationResult", variables);

        // then
        assertThat(html).contains("data-fully-available=\"false\"").contains("brakuje 1").contains("cl-status is-bad")
                .contains("Dostawca nie ma wszystkiego na stanie.").contains("599,00").contains("19,50");
    }

    @Test
    void availabilityFragmentOffersARetryWhenTheSupplierDidNotAnswer() {
        // given
        Map<String, Object> variables = model(warehousePage(false, true, false), warehouseForm());
        variables.put("validationError", "Nie udało się pobrać dostępności od dostawcy. (timeout)");

        // when
        String html = fragment("deliveries/create/purchase :: validationResult", variables);

        // then
        assertThat(html).contains("cl-alert is-bad").contains("(timeout)").contains("data-cl-validation-retry")
                .doesNotContain("data-fully-available=\"true\"");
    }

    @Test
    void refusedLiveCheckRendersTheReasonWithoutThePageModel() {
        // given
        Map<String, Object> variables = new java.util.HashMap<>();
        variables.put("validationError", "Zamówienie nie kwalifikuje się do wysyłki bezpośredniej.");

        // when
        String html = fragment("deliveries/create/purchase :: validationResult", variables);

        // then
        assertThat(html).contains("cl-alert is-bad").contains("Zamówienie nie kwalifikuje się do wysyłki bezpośredniej.")
                .doesNotContain("data-fully-available");
    }
}
