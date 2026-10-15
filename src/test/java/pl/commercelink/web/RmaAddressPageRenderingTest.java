package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.web.rma.RmaAddressForm;
import pl.commercelink.web.rma.RmaAddressPage;

import static org.assertj.core.api.Assertions.assertThat;

class RmaAddressPageRenderingTest {

    private static RmaAddressForm form() {
        ShippingDetails d = new ShippingDetails();
        d.setName("Jan");
        d.setStreetAndNumber("Długa 1");
        d.setCountry("PL");
        return RmaAddressForm.of(d);
    }

    private static String render(RmaAddressPage page) {
        Context context = new Context();
        context.setVariable("page", page);
        return EnglishFragmentTemplateEngine.create().process("<div th:replace=\"~{rma/address :: body}\"></div>", context);
    }

    @Test
    void rendersTheHeaderTheFieldsAndTheFooterWithoutBulma() {
        // when
        String html = render(new RmaAddressPage("rma-123456789", "rma-1234", "ord-5678", form(), false, false, false, false));

        // then
        assertThat(html).doesNotContain("??").doesNotContain("\"button is-primary").doesNotContain("class=\"box")
                .doesNotContain("style=");
        assertThat(html).contains("href=\"/dashboard/rma/rma-123456789\"").contains("RMA rma-1234");
        assertThat(html).contains("<h1 class=\"cl-page-title\">Return address</h1>");
        assertThat(html).contains("action=\"/dashboard/rma/rma-123456789/updateShippingDetails\"");
        assertThat(html).contains("name=\"shippingDetails.name\"").contains("value=\"Jan\"")
                .contains("name=\"shippingDetails.companyName\"").contains("name=\"shippingDetails.phone\"")
                .contains("type=\"tel\"").contains("type=\"email\"").contains("autocomplete=\"off\"");
        assertThat(html).contains("Save address").contains("cl-card-footer");
        assertThat(html).doesNotContain("Fill with the order").doesNotContain("cl-alert");
    }

    @Test
    void offersTheOrderAddressAndWarnsWhenTheRmaHasNone() {
        // when
        String html = render(new RmaAddressPage("rma-1", "rma-1", "ord-5678", RmaAddressForm.of(null), true, false, true, false));

        // then
        assertThat(html).contains("href=\"/dashboard/rma/rma-1/shipping?fill=order\"").contains("Fill with the order address");
        assertThat(html).contains("cl-alert is-warn").contains("has no return address yet");
    }

    @Test
    void confirmsAFilledAddressIsNotSavedYet() {
        // when
        String html = render(new RmaAddressPage("rma-1", "rma-1", "ord-5678", form(), false, true, false, false));

        // then
        assertThat(html).contains("cl-alert is-info").contains("ord-5678").contains("check it and save");
    }

    @Test
    void explainsWhyTheOperatorLandedHereFromShipToClient() {
        // when
        RmaAddressForm withErrors = RmaAddressForm.of(null).withErrors(RmaAddressForm.of(null).validate(null));
        String html = render(new RmaAddressPage("rma-1", "rma-1", null, withErrors, false, false, true, true));

        // then
        assertThat(html).contains("Complete the address to ship the goods to the customer");
        assertThat(html).doesNotContain("has no return address yet");
        assertThat(html).contains("data-cl-error-summary").contains("href=\"#shippingDetails.phone\"")
                .contains("aria-invalid=\"true\"");
    }
}
