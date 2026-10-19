package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.web.orders.OrderAddressForm;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards pages that a redesign must not change: the customer-facing client offer form and the order address form.
 * Run with {@code -Dsnapshots.update=true} to rewrite the snapshots deliberately.
 */
class UnchangedTemplatesSnapshotTest {

    private static final Path SNAPSHOTS = Path.of("src/test/resources/snapshots");
    private static final boolean UPDATE = Boolean.getBoolean("snapshots.update");

    private final TemplateEngine engine = EnglishFragmentTemplateEngine.create();

    @Test
    void clientOfferBillingShippingFormIsUnchanged() throws IOException {
        // given
        // The form uses th:field, which needs a Spring request context bound to a form object that a plain
        // Thymeleaf engine cannot provide; the customer-facing sources are pinned by their hashes instead.
        String snapshot = "clientOffer.html sha256=" + sourceHash("templates/clientOffer.html") + "\n"
                + "fragments/billing-shipping-form.html sha256="
                + sourceHash("templates/fragments/billing-shipping-form.html") + "\n";

        // when / then
        assertMatchesSnapshot("clientOffer-form.html", snapshot);
    }

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

    private static String sourceHash(String classpathResource) throws IOException {
        try (InputStream in = UnchangedTemplatesSnapshotTest.class.getClassLoader().getResourceAsStream(classpathResource)) {
            assertThat(in).as(classpathResource).isNotNull();
            // Line endings are normalised so a checkout with different autocrlf settings keeps the same hash.
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
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
