package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.web.orders.OrderAddressForm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards pages that a redesign must not change: the order address form.
 * Run with {@code -Dsnapshots.update=true} to rewrite the snapshots deliberately.
 */
class UnchangedTemplatesSnapshotTest {

    private static final Path SNAPSHOTS = Path.of("src/test/resources/snapshots");
    private static final boolean UPDATE = Boolean.getBoolean("snapshots.update");

    private final TemplateEngine engine = EnglishFragmentTemplateEngine.create();

    @Test
    void orderAddressBillingDialogIsUnchanged() throws IOException {
        // given
        BillingDetails billing = new BillingDetails();
        billing.setName("Jan");
        billing.setSurname("Kowalski");
        billing.setCompanyName("Acme Sp. z o.o.");
        billing.setTaxId("1234567890");
        billing.setStreetAndNumber("Dluga 1/2");
        billing.setPostalCode("00-001");
        billing.setCity("Warszawa");
        billing.setCountry("PL");
        billing.setEmail("jan@example.com");
        billing.setPhone("+48 600 700 800");
        Context context = new Context(Locale.ENGLISH, Map.of("address", OrderAddressForm.billing("o1", billing)));

        // when
        String html = engine.process("orders/details/address", Set.of("dialog(address)"), context);

        // then
        assertMatchesSnapshot("order-address-billing-dialog.html", normalize(html));
    }

    @Test
    void orderAddressShippingPageFormWithErrorIsUnchanged() throws IOException {
        // given
        ShippingDetails shipping = new ShippingDetails();
        shipping.setName("Anna");
        shipping.setSurname("Nowak");
        shipping.setCompanyName("Beta");
        shipping.setStreetAndNumber("Krotka 3");
        shipping.setPostalCode("30-002");
        shipping.setCity("");
        shipping.setCountry("PL");
        shipping.setEmail("anna@example.com");
        shipping.setPhone("600700800");
        OrderAddressForm form = OrderAddressForm.shipping("o1", shipping)
                .withErrors(Map.of("shippingDetails.city", "billing.city.required"));
        Context context = new Context(Locale.ENGLISH, Map.of("address", form));

        // when
        String html = engine.process("orders/details/address", Set.of("pageForm"), context);

        // then
        assertMatchesSnapshot("order-address-shipping-page.html", normalize(html));
    }

    private static String normalize(String html) {
        return html.replaceAll("\\s+", " ").replaceAll("> <", ">\n<").trim() + "\n";
    }

    private static void assertMatchesSnapshot(String name, String actual) throws IOException {
        Path file = SNAPSHOTS.resolve(name);
        if (UPDATE) {
            Files.createDirectories(SNAPSHOTS);
            Files.writeString(file, actual, StandardCharsets.UTF_8);
            return;
        }
        assertThat(file).as("snapshot %s (generate with -Dsnapshots.update=true)", name).exists();
        assertThat(actual).isEqualTo(Files.readString(file, StandardCharsets.UTF_8));
    }
}
