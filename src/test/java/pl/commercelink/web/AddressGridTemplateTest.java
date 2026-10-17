package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.web.orders.OrderAddressForm;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class AddressGridTemplateTest {

    private static final String CONDITION = "shipment!=Courier";
    private static final Pattern LABEL = Pattern.compile("<label[^>]*for=\"([^\"]+)\"[^>]*>(.*?)</label>", Pattern.DOTALL);

    private final TemplateEngine engine = EnglishFragmentTemplateEngine.create();

    @Test
    void gridWithoutConditionHasNoConditionalOptional() {
        // given
        Context context = contextFor(form());

        // when
        String html = render("null", context);

        // then
        assertThat(html).contains("billingDetails.city").doesNotContain("data-cl-variant-when");
    }

    @Test
    void gridWithConditionMarksAddressFieldsOptionalWhenNotCourier() {
        // given
        Context context = contextFor(form().withErrors(Map.of("billingDetails.city", "billing.city.required")));

        // when
        String html = render("'" + CONDITION + "'", context);

        // then
        assertThat(count(html, "data-cl-variant-when=\"" + CONDITION + "\"")).isEqualTo(4);
        for (String field : new String[]{"streetAndNumber", "postalCode", "city", "country"}) {
            assertThat(labelOf(html, "billingDetails." + field)).contains("data-cl-variant-when=\"" + CONDITION + "\"");
        }
        assertThat(labelOf(html, "billingDetails.surname")).doesNotContain("cl-optional");
        assertThat(html).contains("id=\"billingDetails.city-error\"");
    }

    @Test
    void conditionalFieldsAreNotRenderedRequired() {
        // given
        Context context = contextFor(form());

        // when
        String html = render("'" + CONDITION + "'", context);

        // then
        Matcher city = Pattern.compile("<input[^>]*id=\"billingDetails.city\"[^>]*>").matcher(html);
        assertThat(city.find()).isTrue();
        assertThat(city.group()).doesNotContain("required");
    }

    private String render(String optionalWhen, Context context) {
        return engine.process("<div th:replace=\"~{orders/details/address :: addressGrid(${address}, " + optionalWhen
                + ")}\"></div>", context);
    }

    private static OrderAddressForm form() {
        BillingDetails billing = new BillingDetails();
        billing.setName("Jan");
        billing.setSurname("Kowalski");
        return OrderAddressForm.billing("o1", billing);
    }

    private static Context contextFor(OrderAddressForm form) {
        return new Context(Locale.ENGLISH, Map.of("address", form));
    }

    private static String labelOf(String html, String name) {
        Matcher m = LABEL.matcher(html);
        while (m.find()) {
            if (m.group(1).equals(name)) {
                return m.group(2);
            }
        }
        throw new AssertionError("no label for " + name);
    }

    private static int count(String html, String needle) {
        return html.split(Pattern.quote(needle), -1).length - 1;
    }
}
