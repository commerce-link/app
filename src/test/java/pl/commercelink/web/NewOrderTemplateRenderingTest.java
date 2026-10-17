package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.web.dtos.ClientDataDto;
import pl.commercelink.web.dtos.PickerOption;
import pl.commercelink.web.orders.NewOrderForm;
import pl.commercelink.web.orders.NewOrderPage;
import pl.commercelink.web.orders.NewOrderPage.DeliveryState;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class NewOrderTemplateRenderingTest {

    private final TemplateEngine engine = EnglishFragmentTemplateEngine.create();

    @Test
    void basketPageIsANarrowDesignSystemForm() {
        // when
        String html = render(basketPage(), form(ShipmentType.Courier), NewOrderForm.SAME, Map.of());

        // then
        assertThat(html).contains("cl-page-body is-form").contains("class=\"cl-back\" href=\"/dashboard/basket/view/b1\"");
        assertThat(html).contains(">Basket 1A2B3C4D<");
        assertThat(html.split("<h1").length - 1).isEqualTo(1);
        assertThat(html).contains("We take the items and delivery from basket 1A2B3C4D");
        assertThat(html).contains("action=\"/dashboard/orders/new/fulfilment\"").contains("id=\"order-new-form\"");
        assertThat(html).contains("name=\"orderReference\" value=\"b1\"");
        assertThat(html).contains("data-cl-submit-once").contains(">Create order<");
        assertThat(html).contains("/js/variant-fields.js").contains("/js/submit-once.js").contains("/js/error-summary.js");
        assertThat(html).doesNotContain("onsubmit=").doesNotContain("onchange=").doesNotContain("onclick=");
        assertThat(html).doesNotContain("copyBillingDetailsIfNeeded").doesNotContain("class=\"box\"")
                .doesNotContain("class=\"button").doesNotContain("style=");
        assertThat(html).doesNotMatch("(?s).*<script>.*").doesNotContain("??");
        assertThat(html).doesNotContain("cl-alert is-bad");
    }

    @Test
    void fieldNamesKeepTheContract() {
        // when
        String html = render(basketPage(), form(ShipmentType.Courier), NewOrderForm.SAME, Map.of());

        // then
        for (String field : new String[]{"name", "surname", "companyName", "streetAndNumber", "postalCode", "city",
                "country", "email", "phone", "taxId"}) {
            assertThat(html).contains("name=\"billingDetails." + field + "\"");
        }
        for (String field : new String[]{"name", "surname", "companyName", "streetAndNumber", "postalCode", "city",
                "country", "email", "phone"}) {
            assertThat(html).contains("name=\"shippingDetails." + field + "\"");
        }
        assertThat(html).doesNotContain("name=\"shippingDetails.taxId\"");
        assertThat(count(html, "type=\"radio\" name=\"shipmentType\"")).isEqualTo(3);
        assertThat(count(html, "type=\"radio\" name=\"shippingAddress\"")).isEqualTo(2);
        assertThat(html).contains("name=\"paymentSource\"");
        assertThat(html).containsSubsequence("value=\"Courier\"", "value=\"PickupPoint\"", "value=\"PersonalCollection\"");
        assertThat(element(html, "value=\"Courier\"")).contains("checked");
        assertThat(element(html, "value=\"same\"")).contains("checked");
        assertThat(element(html, "value=\"other\"")).doesNotContain("checked");
    }

    @Test
    void paymentMethodsAreTranslated() {
        // when
        String html = render(basketPage(), form(ShipmentType.Courier), NewOrderForm.SAME, Map.of());

        // then
        assertThat(html).contains(">Bank transfer<").contains(">Cash on delivery<");
        assertThat(element(html, "value=\"BankTransfer\"")).contains("selected");
        assertThat(html).contains(">Payment method<");
    }

    @Test
    void summaryShowsTheSourceItemsDeliveryAndTotal() {
        // when
        String html = render(basketPage(), form(ShipmentType.Courier), NewOrderForm.SAME, Map.of());
        String none = render(page(false, DeliveryState.NONE, null, 0, null, false), form(ShipmentType.Courier),
                NewOrderForm.SAME, Map.of());
        String unknown = render(page(false, DeliveryState.UNKNOWN, null, 0, null, false), form(ShipmentType.Courier),
                NewOrderForm.SAME, Map.of());

        // then
        assertThat(html).contains("dl class=\"cl-kv\"").contains(">Basket<").contains(">Items<").contains(">3<");
        assertThat(html).contains("<a class=\"cl-text-link\" href=\"/dashboard/basket/view/b1\">1A2B3C4D</a>");
        assertThat(html).contains(">DPD courier · 19.99 PLN<");
        assertThat(html).contains(">Total<").contains("1,268.99 PLN");
        assertThat(none).contains(">No delivery<");
        assertThat(unknown).contains(">Unknown delivery option<");
    }

    @Test
    void mismatchWarningIsHiddenWhenTheTypeMatchesAndShownOtherwise() {
        // when
        String matching = render(basketPage(), form(ShipmentType.Courier), NewOrderForm.SAME, Map.of());
        String other = render(basketPage(), form(ShipmentType.PersonalCollection), NewOrderForm.SAME, Map.of());
        String noOption = render(page(false, DeliveryState.NONE, null, 0, null, false), form(ShipmentType.Courier),
                NewOrderForm.SAME, Map.of());

        // then
        assertThat(element(matching, "cl-alert is-warn")).contains("data-cl-variant-when=\"shipment!=Courier\"").contains("hidden");
        assertThat(element(other, "cl-alert is-warn")).contains("data-cl-variant-when=\"shipment!=Courier\"").doesNotContain("hidden");
        assertThat(other).contains("The customer chose &quot;DPD courier&quot; (19.99 PLN) in the shop.");
        assertThat(noOption).doesNotContain("cl-alert is-warn");
    }

    @Test
    void shippingFieldsDependOnTypeAndChoice() {
        // when
        String courierSame = render(basketPage(), form(ShipmentType.Courier), NewOrderForm.SAME, Map.of());
        String courierOther = render(basketPage(), form(ShipmentType.Courier), NewOrderForm.OTHER, Map.of());
        String pickup = render(basketPage(), form(ShipmentType.PickupPoint), NewOrderForm.SAME, Map.of());
        String collection = render(basketPage(), form(ShipmentType.PersonalCollection), NewOrderForm.SAME, Map.of());

        // then
        assertThat(element(courierSame, "id=\"shipping-fields\""))
                .contains("data-cl-variant-when=\"shipment!=PersonalCollection shippingAddress=other\"").contains("hidden");
        assertThat(element(courierOther, "id=\"shipping-fields\"")).doesNotContain("hidden");
        assertThat(element(pickup, "id=\"shipping-fields\"")).doesNotContain("hidden");
        assertThat(element(collection, "id=\"shipping-fields\"")).contains("hidden");
        assertThat(element(courierSame, "data-cl-variant-when=\"shipment=Courier\"")).startsWith("<fieldset").doesNotContain("hidden");
        assertThat(element(pickup, "data-cl-variant-when=\"shipment=Courier\"")).contains("hidden");
        assertThat(courierSame).containsSubsequence("data-cl-variant-when=\"shipment=Courier\"", ">Shipping address<",
                "type=\"radio\" name=\"shippingAddress\"", ">To the billing address<", ">To another address<");
        assertThat(element(pickup, "data-cl-variant-when=\"shipment=PickupPoint\"")).doesNotContain("hidden");
        assertThat(element(collection, "data-cl-variant-when=\"shipment=PersonalCollection\"")).doesNotContain("hidden");
        assertThat(element(courierSame, "data-cl-variant-when=\"shipment=PersonalCollection\"")).contains("hidden");
        assertThat(courierSame).contains("data-cl-variant-select=\"shipment\"").contains("data-cl-variant-select=\"shippingAddress\"");
        assertThat(courierSame).contains("aria-label=\"Shipping address\"")
                .doesNotContain("cl-reveal").doesNotContain("differentShippingAddress");
    }

    @Test
    void billingOptionalNotesFollowTheRenderedDeliveryMethod() {
        // when
        String courier = render(basketPage(), form(ShipmentType.Courier), NewOrderForm.SAME, Map.of());
        String pickup = render(basketPage(), form(ShipmentType.PickupPoint), NewOrderForm.SAME, Map.of());
        String collection = render(basketPage(), form(ShipmentType.PersonalCollection), NewOrderForm.SAME, Map.of());

        // then
        String note = "<span class=\"cl-optional\" data-cl-variant-when=\"shipment!=Courier\"";
        assertThat(count(courier, note)).isEqualTo(4);
        assertThat(count(courier, note + " hidden=\"hidden\">")).isEqualTo(4);
        assertThat(count(pickup, note + ">")).isEqualTo(4);
        assertThat(count(collection, note + ">")).isEqualTo(4);
    }

    @Test
    void errorsAreSummarisedAndShownAtTheFields() {
        // given
        ClientDataDto dto = form(ShipmentType.Courier);
        dto.getBillingDetails().setCity("Warszaw");
        Map<String, String> errors = new LinkedHashMap<>();
        errors.put("billingDetails.city", "billing.city.required");
        errors.put("shippingDetails.phone", "billing.phone.invalid");

        // when
        String html = render(basketPage(), dto, NewOrderForm.SAME, errors);

        // then
        assertThat(html).contains("id=\"order-new-errors\"").contains("data-cl-error-summary");
        assertThat(html).contains("The order was not created");
        assertThat(html).contains("href=\"#billingDetails.city\"").contains("href=\"#shippingDetails.phone\"");
        assertThat(html).contains("aria-invalid=\"true\"").contains("value=\"Warszaw\"");
        assertThat(element(html, "id=\"shipping-fields\"")).doesNotContain("hidden");
    }

    @Test
    void choiceErrorsAreShownAtTheChoices() {
        // given
        ClientDataDto dto = form(null);
        dto.setPaymentSource(null);
        Map<String, String> errors = new LinkedHashMap<>();
        errors.put("shipmentType", "order.new.shipment.required");
        errors.put("paymentSource", "order.new.payment.required");

        // when
        String html = render(basketPage(), dto, NewOrderForm.SAME, errors);

        // then
        assertThat(html).contains("href=\"#shipmentType\"").contains("id=\"shipmentType\"");
        assertThat(html).contains("href=\"#paymentSource\"").contains("id=\"paymentSource\"");
        assertThat(html).contains(">Choose a delivery method.<").contains(">Choose a payment method.<");
        for (String type : new String[]{"Courier", "PickupPoint", "PersonalCollection"}) {
            assertThat(element(html, "value=\"" + type + "\"")).doesNotContain("checked");
        }
    }

    @Test
    void offerPageGoesBackToTheOffer() {
        // when
        String html = render(page(true, DeliveryState.CHOSEN, "DPD courier", 19.99, ShipmentType.Courier, false),
                form(ShipmentType.PickupPoint), NewOrderForm.SAME, Map.of());
        String contact = render(page(true, DeliveryState.CHOSEN, "DPD courier", 19.99, ShipmentType.Courier, true),
                form(ShipmentType.Courier), NewOrderForm.SAME, Map.of());

        // then
        assertThat(html).contains("class=\"cl-back\" href=\"/dashboard/offer/o1\"").contains(">Offer Gaming PC<");
        assertThat(html).contains("We take the items and delivery from offer &quot;Gaming PC&quot;. Check the customer details.");
        assertThat(html).contains(">Offer<").contains("The offer has the delivery &quot;DPD courier&quot; (19.99 PLN).");
        assertThat(contact).contains("The name, e-mail and phone come from the offer&#39;s contact");
    }

    private String render(NewOrderPage page, ClientDataDto dto, String shippingAddress, Map<String, String> errors) {
        NewOrderForm form = NewOrderForm.of(dto, shippingAddress);
        Context context = new Context(Locale.ENGLISH);
        context.setVariable("page", page);
        context.setVariable("form", form.dto());
        context.setVariable("shippingAddress", form.shippingAddress());
        context.setVariable("billing", form.billing().withErrors(errors));
        context.setVariable("shipping", form.shipping().withErrors(errors));
        context.setVariable("errors", errors);
        ResourceBundle messages = ResourceBundle.getBundle("messages", Locale.ENGLISH);
        context.setVariable("paymentOptions", Arrays.stream(PaymentSource.values())
                .map(source -> new PickerOption(source.name(), messages.getString("PaymentSource." + source.name())))
                .toList());
        return engine.process("newOrder_clientDataCollection", context);
    }

    private static NewOrderPage basketPage() {
        return page(false, DeliveryState.CHOSEN, "DPD courier", 19.99, ShipmentType.Courier, false);
    }

    private static NewOrderPage page(boolean offer, DeliveryState state, String deliveryName, double deliveryPrice,
                                     ShipmentType deliveryType, boolean billingFromContact) {
        return offer
                ? new NewOrderPage("o1", true, "/dashboard/offer/o1", "Gaming PC", 3, state, deliveryName, deliveryPrice,
                deliveryType, 1268.99, billingFromContact)
                : new NewOrderPage("b1", false, "/dashboard/basket/view/b1", "1A2B3C4D", 3, state, deliveryName,
                deliveryPrice, deliveryType, 1268.99, billingFromContact);
    }

    private static ClientDataDto form(ShipmentType type) {
        BillingDetails billing = BillingDetails._default();
        billing.setName("Anna");
        billing.setStreetAndNumber("Main 1");
        billing.setPostalCode("00-001");
        billing.setCity("Warszawa");
        billing.setEmail("anna@example.com");
        billing.setPhone("601234567");
        ClientDataDto dto = new ClientDataDto();
        dto.setOrderReference("b1");
        dto.setShipmentType(type);
        dto.setPaymentSource(PaymentSource.BankTransfer);
        dto.setBillingDetails(billing);
        dto.setShippingDetails(ShippingDetails._default());
        return dto;
    }

    private static int count(String html, String text) {
        return html.split(Pattern.quote(text), -1).length - 1;
    }

    /** The opening tag of the first element whose tag contains the marker. */
    private static String element(String html, String marker) {
        Matcher matcher = Pattern.compile("<[a-z]+[^>]*" + Pattern.quote(marker) + "[^>]*>").matcher(html);
        assertThat(matcher.find()).as("element with " + marker).isTrue();
        return matcher.group();
    }
}
