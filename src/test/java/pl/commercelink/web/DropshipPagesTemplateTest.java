package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.support.RequestContext;
import org.thymeleaf.spring6.naming.SpringContextVariableNames;
import org.thymeleaf.spring6.context.webmvc.SpringWebMvcThymeleafRequestContext;
import pl.commercelink.inventory.deliveries.Allocation;
import pl.commercelink.inventory.deliveries.DeliveryItem;
import pl.commercelink.inventory.deliveries.PurchaseValidation;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.inventory.supplier.api.SupplierOrderOption;
import pl.commercelink.inventory.supplier.api.SupplierOrderOptionChoice;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.DeliveryCreationForm;
import pl.commercelink.web.settings.SettingsTemplateRenderer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** The two dropship pages (create, confirmation) and the availability fragment as the controller renders them. */
class DropshipPagesTemplateTest {

    private static final String ORDER_ID = "e2ed0004-dropship-0000-0000-000000000000";

    private static Order order(boolean pickupPoint) {
        Order order = new Order("store-1");
        order.setOrderId(ORDER_ID);
        order.setStatus(OrderStatus.Assembly);
        order.setFulfilmentType(FulfilmentType.DirectToConsumer);
        BillingDetails billing = new BillingDetails();
        billing.setName("Jan");
        billing.setSurname("Kowalski");
        billing.setEmail("jan.kowalski@example.pl");
        order.setBillingDetails(billing);
        ShippingDetails shipping = new ShippingDetails();
        shipping.setName("Jan");
        shipping.setSurname("Kowalski");
        shipping.setStreetAndNumber("ul. Polna 1");
        shipping.setPostalCode("00-001");
        shipping.setCity("Warszawa");
        shipping.setCountry("PL");
        shipping.setPhone("+48601234567");
        shipping.setEmail("jan.kowalski@example.pl");
        order.setShippingDetails(shipping);
        if (pickupPoint) {
            Shipment shipment = new Shipment(ShipmentType.PickupPoint);
            shipment.setCarrier("InPost");
            shipment.setCollectionPointCode("WAW04A");
            order.addShipment(shipment);
        }
        return order;
    }

    private static DeliveryCreationForm form(Order order) {
        OrderItem cpu = new OrderItem(order.getOrderId(), "CPU", "AMD Ryzen 7 9800X3D", 2, 749, "100-100001084WOF", false, 0);
        cpu.setStatus(FulfilmentStatus.Allocation);
        cpu.setManufacturerCode("100-100001084WOF");
        cpu.setEan("5901234123457");
        cpu.setCost(579.5);
        cpu.setDeliveryId("Acme");
        Allocation allocation = Allocation.fromOrderItem(order, cpu);
        allocation.setSelected(true);
        DeliveryCreationForm form = new DeliveryCreationForm();
        form.setStoreId("store-1");
        form.setProvider("Acme");
        form.setTax(1.23);
        form.setItems(DeliveryItem.groupAndUnify(List.of(allocation)));
        return form;
    }

    private static Map<String, Object> model(Order order, boolean superAdmin, String blockedReason) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("navigation", null);
        variables.put("form", form(order));
        variables.put("order", order);
        variables.put("consignee", order.getShippingDetails());
        variables.put("isSuperAdmin", superAdmin);
        variables.put("supplierLabels", new SupplierLabels(mock(StoresRepository.class)).forStore(null));
        variables.put("requiresApproval", false);
        variables.put("pickupShipment", order.firstShipment().filter(s -> s.getType() == ShipmentType.PickupPoint).orElse(null));
        variables.put("purchaseBlockedReason", blockedReason);
        variables.put("orderOptions", List.of(new SupplierOrderOption("lane", "Tryb wysyłki",
                List.of(new SupplierOrderOptionChoice("fast", "Szybka", null)), null, true)));
        variables.put("selectedOptions", Map.of());
        variables.put("purchaseRef", "ref-1");
        return variables;
    }

    /** th:field needs Spring's request context to bind the form, which a real MVC view render would provide. */
    private static String render(String template, Map<String, Object> variables) {
        MockServletContext servletContext = new MockServletContext();
        GenericWebApplicationContext applicationContext = new GenericWebApplicationContext(servletContext);
        applicationContext.refresh();
        servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, applicationContext);
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        RequestContext requestContext = new RequestContext(request, new MockHttpServletResponse(), servletContext, variables);
        Map<String, Object> bound = new HashMap<>(variables);
        bound.put(SpringContextVariableNames.SPRING_REQUEST_CONTEXT, requestContext);
        bound.put(SpringContextVariableNames.THYMELEAF_REQUEST_CONTEXT,
                new SpringWebMvcThymeleafRequestContext(requestContext, request));
        return page(SettingsTemplateRenderer.render(template, bound));
    }

    private static String fragment(Map<String, Object> variables) {
        return SettingsTemplateRenderer.render(
                "<div th:replace=\"~{dropshipConfirmation :: validationResult}\"></div>", variables);
    }

    private static String page(String html) {
        int start = html.indexOf("<section class=\"cl-page\"");
        return html.substring(start, html.indexOf("</main>", start));
    }

    private static int occurrences(String html, String needle) {
        return html.split(Pattern.quote(needle), -1).length - 1;
    }

    @Test
    void createPageIsASubpageOfTheOrderWithTheDeliveryFormProductsAndConsignee() {
        // when
        String html = render("dropshipCreate", model(order(false), false, null));

        // then
        assertThat(occurrences(html, "<h1")).isEqualTo(1);
        assertThat(html).contains("href=\"/dashboard/orders/" + ORDER_ID + "\"").contains("Zamówienie e2ed0004")
                .contains("Zamów u Acme").contains("cl-status is-info is-leading").contains("Dropshipping")
                .contains("action=\"/dashboard/orders/" + ORDER_ID + "/dropship/create\"")
                .contains("formaction=\"/dashboard/orders/" + ORDER_ID + "/dropship/purchase\"")
                .contains("AMD Ryzen 7 9800X3D").contains("<span class=\"cl-code-nowrap\">5901234123457</span>")
                .contains("<span class=\"cl-code-nowrap\">100-100001084WOF</span>")
                .contains("jan.kowalski · 2 szt.").contains("Dane adresowe klienta").contains("ul. Polna 1")
                .contains("href=\"tel:+48601234567\"").contains("1 159,00")
                .doesNotContain("??");
        assertThat(html).doesNotContain("purchase-blocked-reason").doesNotContain("Punkt odbioru");
    }

    @Test
    void createPageKeepsEveryFieldNameAndLabelsEachControl() {
        // when
        String html = render("dropshipCreate", model(order(false), false, null));

        // then
        for (String name : List.of("storeId", "provider", "externalDeliveryId", "estimatedDeliveryAt", "sourceCurrency",
                "shippingCost", "paymentCost", "tax", "paymentTerms", "removeUnselected", "items[0].name", "items[0].ean",
                "items[0].mfn", "items[0].orderedQty", "items[0].requestedQty", "items[0].unitCost",
                "items[0].allocations[0].key.orderId", "items[0].allocations[0].key.itemId",
                "items[0].allocations[0].key.name", "items[0].allocations[0].type", "items[0].allocations[0].name",
                "items[0].allocations[0].qty", "items[0].allocations[0].ean", "items[0].allocations[0].mfn",
                "items[0].allocations[0].deliveryId", "items[0].allocations[0].unitCost",
                "items[0].allocations[0].selected")) {
            assertThat(html).as(name).contains("name=\"" + name + "\"");
        }
        for (String id : List.of("externalDeliveryId", "estimatedDeliveryAt", "sourceCurrency", "shippingCost",
                "paymentCost", "tax", "paymentTerms")) {
            assertThat(html).as(id).contains("for=\"" + id + "\"").contains("id=\"" + id + "\"");
        }
        assertThat(html).contains("aria-label=\"Koszt netto / szt.: AMD Ryzen 7 9800X3D\"")
                .contains("data-cl-allocation-qty=\"2\"").contains("checked=\"checked\"")
                .contains("<script src=\"/js/dropship-create.js\" defer");
    }

    @Test
    void enterInAFieldOfTheCreatePageSavesWhileThePrimaryButtonShowsFirst() {
        // when
        String html = render("dropshipCreate", model(order(false), false, null));

        // then: implicit submission uses the form's first submit button, which must stay "Zapisz" as before the redesign
        String form = html.substring(html.indexOf("<form"), html.indexOf("</form>"));
        int firstSubmit = form.indexOf("type=\"submit\"");
        assertThat(form.substring(firstSubmit, form.indexOf(">", firstSubmit))).contains("id=\"dropship-save-button\"")
                .doesNotContain("formaction");
        assertThat(form.indexOf("id=\"dropship-save-button\"")).isLessThan(form.indexOf("id=\"purchase-order-button\""));
        assertThat(form).contains("class=\"cl-card-footer is-primary-first\"");
    }

    @Test
    void createPageShowsWhySupplierOrderIsBlockedAndTheCustomersPickupPoint() {
        // when
        String html = render("dropshipCreate",
                model(order(true), false, "orders.dropship.error.pickupPointUnsupported"));

        // then
        assertThat(html).contains("cl-alert is-warn").contains("id=\"purchase-blocked-reason\"")
                .contains("Klient wybrał odbiór w punkcie")
                .contains("data-blocked=\"true\"").contains("aria-describedby=\"purchase-blocked-reason\"")
                .contains("Punkt odbioru").contains("InPost").contains("WAW04A");
        int purchase = html.indexOf("id=\"purchase-order-button\"");
        assertThat(html.substring(html.lastIndexOf("<button", purchase), html.indexOf(">", purchase))).contains("disabled");
    }

    @Test
    void superAdminPagesPostToTheStoreScopedRoutes() {
        // when
        String create = render("dropshipCreate", model(order(false), true, null));
        String confirmation = render("dropshipConfirmation", model(order(false), true, null));

        // then
        String base = "/dashboard/store/store-1/orders/" + ORDER_ID;
        assertThat(create).contains("href=\"" + base + "\"").contains("action=\"" + base + "/dropship/create\"")
                .contains("formaction=\"" + base + "/dropship/purchase\"");
        assertThat(confirmation).contains("href=\"" + base + "\"").contains("action=\"" + base + "/dropship/confirm\"")
                .contains("data-validate-url=\"" + base + "/dropship/validate\"")
                .contains("formaction=\"" + base + "/dropship/purchase/back\"");
    }

    @Test
    void confirmationPageWaitsForTheSupplierAndCarriesTheFirstStepInHiddenFields() {
        // when
        String html = render("dropshipConfirmation", model(order(false), false, null));

        // then
        assertThat(occurrences(html, "<h1")).isEqualTo(1);
        assertThat(html).contains("Potwierdź zamówienie u dostawcy").contains("Dostępność u dostawcy")
                .contains("id=\"validation-area\"").contains("aria-live=\"polite\"").contains("cl-spinner")
                .contains("Sprawdzanie dostępności u dostawcy…")
                .contains("<legend class=\"cl-fieldset-title\">Warunki u dostawcy</legend>")
                .contains("for=\"order-option-0\"").contains("name=\"supplierOrderChoices[lane]\"")
                .contains("data-required=\"true\"").contains("name=\"purchaseRef\" value=\"ref-1\"")
                .contains("name=\"items[0].allocations[0].selected\"").contains(">Zamów u dostawcy</button>").contains("Cofnij")
                .contains("<script src=\"/js/dropship-confirmation.js\" defer")
                .doesNotContain("estimatedDeliveryAt").doesNotContain("data-fully-available").doesNotContain("??");
        int submit = html.indexOf("id=\"purchase-confirm-submit\"");
        assertThat(html.substring(submit, html.indexOf(">", submit))).contains("disabled").contains("data-blocked=\"false\"");
    }

    @Test
    void confirmationPageBlocksTheSubmitOnAServerReasonOrMissingOptions() {
        // given
        Map<String, Object> variables = model(order(false), false, "orders.dropship.error.pickupPointIncomplete");
        variables.put("orderOptionsError", "timeout");

        // when
        String html = render("dropshipConfirmation", variables);

        // then
        assertThat(html).contains("cl-alert is-warn\" id=\"purchase-blocked-reason\"").contains("brakuje przewoźnika lub kodu punktu")
                .contains("id=\"order-options-blocked\"").contains("(timeout)").contains("data-blocked=\"true\"");
    }

    @Test
    void availabilityFragmentListsTheSuppliersAnswerWithMissingQuantitiesAndTotal() {
        // given
        Map<String, Object> variables = new HashMap<>();
        variables.put("validation", new PurchaseValidation("Acme", "ref-1", "PLN", 1159.0, false, List.of(
                new PurchaseValidation.Line("AMD Ryzen 7 9800X3D", "sku", "5901234123457", "100-100001084WOF", 2, 1,
                        579.5, 589.5))));

        // when
        String html = fragment(variables);

        // then
        assertThat(html).contains("data-fully-available=\"false\"").contains("cl-alert is-warn")
                .contains("cl-table is-key-wrap").contains("data-label=\"Dostępne\"")
                .contains("cl-status is-bad").contains("Brakuje: 1").contains("579,50").contains("589,50")
                .contains("10,00").contains("1 159,00 PLN").doesNotContain("onclick").doesNotContain("??");
    }

    @Test
    void availabilityFragmentOffersARetryButtonWhenTheSupplierDidNotAnswer() {
        // given
        Map<String, Object> variables = new HashMap<>();
        variables.put("validationError", "Nie udało się pobrać dostępności od dostawcy. (Acme)");

        // when
        String html = fragment(variables);

        // then
        assertThat(html).contains("data-fully-available=\"false\"").contains("cl-alert is-bad")
                .contains("(Acme)").contains("<button type=\"button\" class=\"cl-link-button\" data-cl-validation-retry>")
                .doesNotContain("javascript:").doesNotContain("onclick");
    }

    @Test
    void newDropshipPageKeysExistInBothLanguages() throws Exception {
        // given
        Properties pl = new Properties();
        Properties en = new Properties();
        pl.load(Files.newBufferedReader(Path.of("src/main/resources/messages_pl.properties"), StandardCharsets.UTF_8));
        en.load(Files.newBufferedReader(Path.of("src/main/resources/messages_en.properties"), StandardCharsets.UTF_8));

        // then
        for (String key : List.of("orders.dropship.page.create.title", "orders.dropship.page.card.delivery",
                "orders.dropship.page.card.items", "orders.dropship.page.card.availability",
                "orders.dropship.page.paymentTerms.help", "orders.dropship.page.tax.help",
                "orders.dropship.page.available",
                "orders.dropship.page.feedPrice", "orders.dropship.page.livePrice", "orders.dropship.page.tax",
                "orders.dropship.page.unitCost", "orders.dropship.page.mfn", "orders.dropship.page.allocation",
                "orders.dropship.page.unitCost.label",
                "orders.dropship.page.priceDelta")) {
            assertThat(pl.getProperty(key)).as(key + " pl").isNotBlank();
            assertThat(en.getProperty(key)).as(key + " en").isNotBlank();
        }
    }

    @Test
    void theDropshipStepsShareOneNameAndTheCreatePageShowsAmountsWithTwoDecimals() {
        // given
        Map<String, Object> variables = model(order(false), false, null);
        DeliveryCreationForm form = (DeliveryCreationForm) variables.get("form");
        form.setShippingCost(15);

        // when
        String create = render("dropshipCreate", variables);
        String confirmation = render("dropshipConfirmation", model(order(false), false, null));

        // then: "Zamów u Acme" -> "Potwierdź zamówienie u dostawcy" -> "Zamów u dostawcy"
        assertThat(create).contains("<h1 class=\"cl-page-title\">Zamów u Acme</h1>")
                .contains(">Mnożnik VAT</label>").contains(">Koszt netto / szt.</th>")
                .containsPattern("id=\"shippingCost\" name=\"shippingCost\"[^>]*value=\"15.00\"")
                .containsPattern("id=\"paymentCost\" name=\"paymentCost\"[^>]*value=\"0.00\"")
                .containsPattern("name=\"items\\[0]\\.unitCost\"\\s+value=\"579.50\"");
        assertThat(confirmation).contains("<h1 class=\"cl-page-title\">Potwierdź zamówienie u dostawcy</h1>")
                .contains(">Zamów u dostawcy</button>");
    }
}
