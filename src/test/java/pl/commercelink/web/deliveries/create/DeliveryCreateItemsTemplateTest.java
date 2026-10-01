package pl.commercelink.web.deliveries.create;

import org.junit.jupiter.api.Test;
import pl.commercelink.web.dtos.DeliveryCreationForm;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.commercelink.web.deliveries.create.DeliveryCreateTemplates.*;

class DeliveryCreateItemsTemplateTest {

    @Test
    void warehouseStepOneIsTheSuppliersBatchWithoutAnyOrderDataField() {
        // when
        String html = render("deliveries/create/items", model(warehousePage(false, true, false), warehouseForm()));

        // then
        assertThat(occurrences(html, "<h1")).isEqualTo(1);
        assertThat(html).contains("Nowa dostawa · Acme").contains("Krok 1 z 2").contains("co zamawiasz")
                .contains("towar przyjedzie do magazynu sklepu").contains("zamówienia: 1")
                .contains("href=\"/dashboard/deliveries/preview\"").contains("Oczekujące dostawy")
                .contains("id=\"delivery-step-form\"").contains("data-cl-delivery-items")
                .contains("AMD Ryzen 7 9800X3D").contains("<span class=\"cl-code-nowrap\" data-cl-item-ean>5901234123457</span>")
                .contains("Źródła: 1").contains("min. 2").contains("Dołącz źródło")
                .contains("Uzupełnij magazyn przy okazji: 1").contains("Kingston FURY Beast 16GB")
                .contains("Magazyn sklepu").contains("Podsumowanie")
                .doesNotContain("??");
        for (String absent : List.of("name=\"externalDeliveryId\" class", "type=\"date\"", "<select")) {
            assertThat(html).as(absent).doesNotContain(absent);
        }
    }

    @Test
    void stepOneKeepsEveryFieldTheNextStepsRead() {
        // when
        String html = render("deliveries/create/items", model(warehousePage(false, true, false), warehouseForm()));

        // then
        assertThat(fieldNames(html)).contains("items[0].name", "items[0].ean", "items[0].mfn", "items[0].orderedQty",
                "items[0].requestedQty", "items[0].unitCost", "items[0].allocations[0].key.orderId",
                "items[0].allocations[0].key.itemId", "items[0].allocations[0].key.name", "items[0].allocations[0].type",
                "items[0].allocations[0].name", "items[0].allocations[0].qty", "items[0].allocations[0].ean",
                "items[0].allocations[0].mfn", "items[0].allocations[0].deliveryId", "items[0].allocations[0].unitCost",
                "items[0].allocations[0].selected", "_items[0].allocations[0].selected", "removeUnselected",
                "_removeUnselected", "suggestedItems[0].name", "suggestedItems[0].ean", "suggestedItems[0].mfn",
                "suggestedItems[0].requestedQty", "suggestedItems[0].unitCost", "externalDeliveryId",
                "estimatedDeliveryAt", "sourceCurrency", "shippingCost", "paymentCost", "paymentTerms", "tax");
        assertThat(fieldNames(html)).doesNotHaveDuplicates();
    }

    @Test
    void stepOneCarriesTheOrderDataTypedInStepTwo() {
        // given: back from "Zarejestruj zamówienie" with the number and costs typed
        DeliveryCreationForm form = warehouseForm();
        form.setExternalDeliveryId("ACM-77");
        form.setShippingCost(25.5);
        form.setPaymentTerms(14);

        // when
        String html = render("deliveries/create/items", model(warehousePage(false, true, false), form));

        // then
        assertThat(html).contains("name=\"externalDeliveryId\" value=\"ACM-77\"")
                .contains("name=\"shippingCost\" value=\"25.50\"").contains("name=\"paymentTerms\" value=\"14\"");
    }

    @Test
    void summaryOffersBothWaysWithIntegrationFirstAndSaysWhatComesNext() {
        // when
        String html = render("deliveries/create/items", model(warehousePage(false, true, false), warehouseForm()));

        // then
        String footer = html.substring(html.indexOf("cl-card-footer is-block"));
        assertThat(footer.indexOf("id=\"purchase-button\"")).isLessThan(footer.indexOf("id=\"manual-button\""));
        assertThat(footer).contains("formaction=\"/dashboard/deliveries/create/Acme/purchase\"")
                .contains("formaction=\"/dashboard/deliveries/create/Acme/manual\"")
                .contains("Zamów przez integrację").contains("Dalej: dostępność na żywo, adres i opcje dostawcy.")
                .contains("Zamówiłem poza systemem")
                .contains("dalej wpiszesz numer zamówienia, termin i koszty");
        int purchase = footer.indexOf("id=\"purchase-button\"");
        assertThat(footer.substring(footer.lastIndexOf("<button", purchase), footer.indexOf(">", purchase)))
                .contains("cl-button is-primary");
    }

    @Test
    void globalSupplierSaysTheOrderWaitsForApproval() {
        // when
        String html = render("deliveries/create/items", model(warehousePage(false, true, true), warehouseForm()));

        // then
        assertThat(html).contains("potem zgłoszenie do akceptacji administratora platformy");
    }

    @Test
    void supplierWithoutIntegrationOffersOnlyTheOrderDataStep() {
        // when
        String html = render("deliveries/create/items", model(warehousePage(false, false, false), warehouseForm()));

        // then
        assertThat(html).doesNotContain("id=\"purchase-button\"").contains("Dalej: dane zamówienia")
                .contains("Acme nie ma integracji zamówień w CommerceLink");
        int manual = html.indexOf("id=\"manual-button\"");
        assertThat(html.substring(html.lastIndexOf("<button", manual), html.indexOf(">", manual))).contains("is-primary");
    }

    @Test
    void dropshipStepOneTicksWholeProductsAndShowsTheCustomer() {
        // when
        String html = render("deliveries/create/items", model(dropshipPage(false, null, false), dropshipForm()));

        // then
        assertThat(html).contains("Dropshipping").contains("Pozycje zamówienia")
                .contains("href=\"/dashboard/orders/" + ORDER_ID + "\"").contains("dostawca wyśle towar prosto do klienta")
                .contains("data-cl-include").contains("aria-label=\"Dołącz do dostawy: AMD Ryzen 7 9800X3D\"")
                .contains("data-cl-requested-qty-label").contains("Dane adresowe klienta").contains("ul. Polna 1")
                .contains("name=\"order\" value=\"" + ORDER_ID + "\"").contains("Zwolnij odznaczone pozycje")
                .contains("Dalej: dostępność na żywo i opcje dostawcy.")
                .doesNotContain("Uzupełnij magazyn").doesNotContain("cl-table-edit").doesNotContain("data-cl-min");
        assertThat(html).contains("href=\"/dashboard/deliveries/preview\"");
    }

    @Test
    void dropshipFromTheOrderGoesBackToTheOrder() {
        // when
        String html = render("deliveries/create/items", model(dropshipPage(true, null, false), dropshipForm()));

        // then
        assertThat(html).contains("<a class=\"cl-back\" href=\"/dashboard/orders/" + ORDER_ID + "\"")
                .contains("Zamówienie e2ed0004").contains("name=\"from\" value=\"order\"");
    }

    @Test
    void pickupPointTheSupplierCannotServeDisablesTheIntegrationWithItsReason() {
        // when
        String html = render("deliveries/create/items",
                model(dropshipPage(false, "orders.dropship.error.pickupPointUnsupported", true), dropshipForm()));

        // then
        String footer = html.substring(html.indexOf("cl-card-footer is-block"));
        assertThat(footer.indexOf("id=\"manual-button\"")).isLessThan(footer.indexOf("id=\"purchase-button\""));
        int purchase = footer.indexOf("id=\"purchase-button\"");
        assertThat(footer.substring(footer.lastIndexOf("<button", purchase), footer.indexOf(">", purchase)))
                .contains("disabled").contains("aria-describedby=\"purchase-blocked\"").doesNotContain("is-primary");
        assertThat(footer).contains("id=\"purchase-blocked\"").contains("Klient wybrał odbiór w punkcie");
        assertThat(html).contains("Punkt odbioru").contains("WAW04A").doesNotContain("cl-alert is-warn");
    }

    @Test
    void stepErrorIsShownAboveTheForm() {
        // given
        Map<String, Object> variables = model(warehousePage(false, true, false), warehouseForm());
        variables.put("stepError", "deliveries.create.error.nothingRequested");

        // when
        String html = render("deliveries/create/items", variables);

        // then
        assertThat(html).contains("cl-alert is-bad").contains("Zaznacz co najmniej jedną pozycję albo wpisz ilość sugestii.");
        assertThat(html.indexOf("cl-alert is-bad")).isLessThan(html.indexOf("id=\"delivery-step-form\""));
    }

    @Test
    void superAdminStepOnePostsToTheStoreScopedRoutes() {
        // when
        String html = render("deliveries/create/items", model(warehousePage(true, true, false), warehouseForm()));

        // then
        assertThat(html).contains("formaction=\"/dashboard/store/store-1/deliveries/create/Acme/purchase\"")
                .contains("data-url=\"/dashboard/store/store-1/deliveries/create/Acme/fulfilment\"")
                .contains("href=\"/dashboard/store/store-1/deliveries/preview\"");
    }

    @Test
    void editDialogSitsOutsideTheStepFormAndKnowsTheProduct() {
        // when
        String html = render("deliveries/create/items", model(warehousePage(false, true, false), warehouseForm()));

        // then
        assertThat(html.indexOf("id=\"fulfilment-dialog\"")).isGreaterThan(html.indexOf("</form>"));
        assertThat(html).contains("data-cl-dialog-open=\"fulfilment-dialog\"")
                .contains("aria-label=\"Edytuj EAN, kod producenta i koszt: AMD Ryzen 7 9800X3D\"")
                .contains("data-ean=\"5901234123457\"").contains("data-row=\"0\"");
    }
}
