package pl.commercelink.web.deliveries.approval;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.deliveries.DeliveryOrderStatus;
import pl.commercelink.inventory.deliveries.DeliveryType;
import pl.commercelink.inventory.supplier.api.SupplierDeliveryAddress;
import pl.commercelink.inventory.supplier.api.SupplierOrderOption;
import pl.commercelink.inventory.supplier.api.SupplierOrderOptionChoice;
import pl.commercelink.orders.ShippingDetails;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.commercelink.web.deliveries.create.DeliveryCreateTemplates.occurrences;
import static pl.commercelink.web.deliveries.create.DeliveryCreateTemplates.render;

class ApprovalTemplateTest {

    private static ApprovalPage page(boolean dropship, ShippingDetails consignee, boolean openReject) {
        return new ApprovalPage("uma2dqukxr", "dd000010-0000-4000-8000-000000000010", "dd000010", "Acme",
                "Demo Store", "/dashboard/store/uma2dqukxr", "02.10.2026, 08:30", dropship,
                List.of(new ApprovalPage.RequestLine("#dd0e0011", "/dashboard/store/uma2dqukxr/orders/dd0e0011-a",
                        "klient11@example.com", null, 1)), 2, 3, "1 299,00", consignee, null, openReject);
    }

    private static Map<String, Object> warehouse(List<SupplierDeliveryAddress> addresses) {
        Delivery delivery = new Delivery();
        delivery.setStoreId("uma2dqukxr");
        delivery.setDeliveryId("dd000010-0000-4000-8000-000000000010");
        delivery.setProvider("Acme");
        delivery.setOrderStatus(DeliveryOrderStatus.AWAITING_APPROVAL);
        Map<String, Object> variables = new HashMap<>();
        variables.put("delivery", delivery);
        variables.put("page", page(false, null, false));
        variables.put("approvalAddresses", addresses);
        variables.put("suggestedAddressId", addresses.isEmpty() ? null : addresses.get(0).id());
        ShippingDetails storeDefault = new ShippingDetails();
        storeDefault.setStreetAndNumber("ul. Przemysłowa 12");
        storeDefault.setPostalCode("02-495");
        storeDefault.setCity("Warszawa");
        variables.put("suggestedAddress", storeDefault);
        variables.put("routedOrders", List.of());
        variables.put("orderOptions", List.of(new SupplierOrderOption("shipping", "Usługa wysyłki",
                List.of(new SupplierOrderOptionChoice("std", "Standard", "2-3 dni robocze")), null, true)));
        variables.put("selectedOptions", Map.of("shipping", "std"));
        return variables;
    }

    private static List<SupplierDeliveryAddress> addresses() {
        return List.of(new SupplierDeliveryAddress("a-1", "ul. Przemysłowa 12", "Warszawa", "02-495", "PL"),
                new SupplierDeliveryAddress("a-2", "ul. Zakopiańska 58", "Kraków", "30-418", "PL"));
    }

    @Test
    void warehouseRequestShowsTheRecordHeaderTheCardsAndTheDecision() {
        // when
        String html = render("deliveries/approval", warehouse(addresses()));

        // then
        assertThat(occurrences(html, "<h1")).isEqualTo(1);
        assertThat(html).contains("Zrealizuj u Acme").contains("Czeka na akceptację").contains("Demo Store")
                .contains("zgłoszono 02.10.2026, 08:30").contains("Do magazynu").contains("konto platformy")
                .contains("Dostawa dd000010").contains("href=\"/dashboard/store/uma2dqukxr/deliveries/details?deliveryId=dd000010-0000-4000-8000-000000000010\"")
                .contains("id=\"validation-area\"")
                .contains("data-validate-url=\"/dashboard/store/uma2dqukxr/deliveries/dd000010-0000-4000-8000-000000000010/approval/validate\"")
                .contains("action=\"/dashboard/store/uma2dqukxr/deliveries/dd000010-0000-4000-8000-000000000010/approve\"")
                .contains("Adres dostawy").contains("Adres sklepu").containsPattern("value=\"a-1\"[^>]*checked=\"checked\"")
                .contains("Opcje zamówienia u dostawcy").contains("name=\"supplierOrderChoices[shipping]\"")
                .contains("Towar dla").contains("#dd0e0011").contains("klient11@example.com").contains("Na stan magazynu")
                .contains("Decyzja").contains("Zamów u Acme").contains("Zanim zamówisz:")
                .contains("Odrzuć zgłoszenie").contains("data-cl-dialog-open=\"reject-dialog\"").contains("href=\"?open=reject\"")
                .contains("id=\"reject-dialog\"").contains("name=\"reason\"").contains("maxlength=\"500\"")
                .contains("action=\"/dashboard/store/uma2dqukxr/deliveries/dd000010-0000-4000-8000-000000000010/reject\"")
                .contains("3 szt. za 1 299,00 PLN netto")
                .doesNotContain("addressModal").doesNotContain("address-modal").doesNotContain("is-primary\" disabled=\"disabled\" onclick")
                .doesNotContain("class=\"button is-primary").doesNotContain("??");
        int submit = html.indexOf("id=\"purchase-confirm-submit\"");
        assertThat(html.substring(html.lastIndexOf("<button", submit), html.indexOf(">", submit))).contains("disabled");
        assertThat(html.substring(0, submit)).doesNotContain("type=\"submit\"");
        int dialog = html.indexOf("id=\"reject-dialog\"");
        assertThat(html.substring(html.lastIndexOf("<dialog", dialog), html.indexOf(">", dialog))).doesNotContain("open");
    }

    @Test
    void openRejectRendersTheDialogOpenForBrowsersWithoutScript() {
        // given
        Map<String, Object> variables = warehouse(addresses());
        variables.put("page", page(false, null, true));

        // when
        String html = render("deliveries/approval", variables);

        // then
        int dialog = html.indexOf("id=\"reject-dialog\"");
        assertThat(html.substring(html.lastIndexOf("<dialog", dialog), html.indexOf(">", dialog))).contains("open");
    }

    @Test
    void storeWithoutADefaultAddressIsAskedToChooseManually() {
        // given
        Map<String, Object> variables = warehouse(addresses());
        variables.put("suggestedAddress", null);
        variables.put("suggestedAddressId", null);

        // when
        String html = render("deliveries/approval", variables);

        // then
        assertThat(html).contains("Sklep Demo Store nie ma domyślnego adresu wysyłkowego — wybierz adres ręcznie.")
                .doesNotContain("Adres sklepu").doesNotContain("checked=\"checked\"");
    }

    @Test
    void storeDefaultMatchingNoAddressIsAskedToChooseManually() {
        // given
        Map<String, Object> variables = warehouse(addresses());
        variables.put("suggestedAddressId", null);

        // when
        String html = render("deliveries/approval", variables);

        // then
        assertThat(html).contains("Żaden adres nie pasuje do adresu wysyłkowego sklepu Demo Store — wybierz adres ręcznie.")
                .doesNotContain("Adres sklepu");
    }

    @Test
    void addressBookFailureAndNoAddressBookAreExplainedInTheCard() {
        // given
        Map<String, Object> failed = warehouse(List.of());
        failed.put("approvalAddressError", "HTTP 503");
        Map<String, Object> none = warehouse(List.of());

        // when
        String failedHtml = render("deliveries/approval", failed);
        String noneHtml = render("deliveries/approval", none);

        // then
        assertThat(failedHtml).contains("id=\"address-blocked\"").contains("(HTTP 503)").contains("data-blocked=\"true\"");
        assertThat(noneHtml).contains("Ten dostawca nie udostępnia listy adresów").doesNotContain("id=\"address-required\"")
                .contains("data-blocked=\"false\"");
    }

    @Test
    void dropshipShowsTheCustomerAndNoAddressBook() {
        // given
        ShippingDetails consignee = new ShippingDetails();
        consignee.setStreetAndNumber("ul. Polna 1");
        consignee.setPostalCode("00-001");
        consignee.setCity("Warszawa");
        consignee.setCountry("PL");
        Map<String, Object> variables = warehouse(List.of());
        variables.remove("approvalAddresses");
        variables.put("page", page(true, consignee, false));

        // when
        String html = render("deliveries/approval", variables);

        // then
        assertThat(html).contains("Dropshipping").contains("ul. Polna 1").doesNotContain("Adres dostawy</h2>")
                .doesNotContain("id=\"address-required\"");
    }

    @Test
    void dropshipWhoseOrderCannotBeFoundFallsBackToTheDeliveryAddress() {
        // given
        Map<String, Object> variables = warehouse(List.of());
        variables.remove("approvalAddresses");
        ((Delivery) variables.get("delivery")).setDeliveryAddress("ul. Zapasowa 7, 00-002 Warszawa");
        variables.put("page", page(true, null, false));

        // when
        String html = render("deliveries/approval", variables);

        // then
        assertThat(html).contains("ul. Zapasowa 7, 00-002 Warszawa").doesNotContain("??");
    }

    @Test
    void routedOrdersAndOptionErrorsAreAlertsAboveTheCards() {
        // given
        Map<String, Object> variables = warehouse(addresses());
        variables.put("routedOrders", List.of(new pl.commercelink.web.dtos.RoutedOrderView("dd0e0012",
                new pl.commercelink.web.dtos.RoutedSupplierView("77", "AcmeB", "OWN", "inventory.provider.own", true, true))));
        variables.put("orderOptionsError", "timeout");

        // when
        String html = render("deliveries/approval", variables);

        // then
        assertThat(html).contains("cl-alert is-warn").contains("dd0e0012").contains("AcmeB")
                .contains("id=\"order-options-blocked\"").contains("(timeout)").contains("data-blocked=\"true\"");
    }
}
