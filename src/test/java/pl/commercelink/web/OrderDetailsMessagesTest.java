package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class OrderDetailsMessagesTest {

    private static final Pattern KEY = Pattern.compile("#\\{([a-zA-Z0-9_.]+)[(}]");
    private static final Pattern QUOTED = Pattern.compile("\"(order\\.(page|card|items|item|bulk|shipments|documents|payments|payment|status|settings|finances|customer|history|event|review|move|address)\\.[a-zA-Z0-9_.]+)\"");

    static Stream<Path> sources() throws IOException {
        List<Path> files = new ArrayList<>();
        // the directory may be missing; Files.walk itself (unlike the per-file Files.exists guard below)
        // throws on a missing root
        Path detailsDir = Path.of("src/main/resources/templates/orders/details");
        if (Files.exists(detailsDir)) {
            try (Stream<Path> t = Files.walk(detailsDir)) { t.filter(Files::isRegularFile).forEach(files::add); }
        }
        files.add(Path.of("src/main/resources/templates/orders/details.html"));
        files.add(Path.of("src/main/resources/templates/orders/status.html"));
        files.add(Path.of("src/main/resources/templates/orders/settings.html"));
        files.add(Path.of("src/main/resources/templates/orders/address.html"));
        files.add(Path.of("src/main/resources/templates/orders/parts.html"));
        files.add(Path.of("src/main/resources/templates/orders/card.html"));
        files.add(Path.of("src/main/resources/templates/orders/collection.html"));
        files.add(Path.of("src/main/resources/templates/orders/shipment.html"));
        files.add(Path.of("src/main/resources/templates/orders/payment.html"));
        try (Stream<Path> j = Files.walk(Path.of("src/main/java/pl/commercelink/web/orders"))) { j.filter(p -> p.toString().endsWith(".java")).forEach(files::add); }
        return files.stream();
    }

    @Test
    void everyKeyUsedByTheDetailsPageExistsInBothBundles() throws IOException {
        // given
        Properties pl = load("messages_pl.properties"), en = load("messages_en.properties");
        Set<String> missing = new TreeSet<>();

        // when
        for (Path p : sources().toList()) {
            if (!Files.exists(p)) continue;
            String text = Files.readString(p);
            for (Pattern pattern : List.of(KEY, QUOTED)) {
                Matcher m = pattern.matcher(text);
                while (m.find()) {
                    String key = m.group(1);
                    if (key.endsWith(".")) continue;                 // prefix concatenations
                    if (!pl.containsKey(key)) missing.add("pl:" + key);
                    if (!en.containsKey(key)) missing.add("en:" + key);
                }
            }
        }

        // then
        assertThat(missing).isEmpty();
    }

    @Test
    void amountKeysTakeTheCurrencyFromTheSharedAmountKey() throws IOException {
        // given
        Properties pl = load("messages_pl.properties"), en = load("messages_en.properties");

        // then
        for (Properties bundle : List.of(pl, en)) {
            for (String key : List.of("order.payments.fee", "order.payments.overpaid")) {
                assertThat(bundle.getProperty(key)).as(key).contains("{0}").doesNotContain("PLN");
            }
        }
    }

    @Test
    void theDetailsCallAReviewAnOpinionNotARecension() throws IOException {
        // given
        Properties pl = load("messages_pl.properties");

        // then
        assertThat(pl.stringPropertyNames().stream().filter(key -> key.startsWith("order."))
                .filter(key -> pl.getProperty(key).toLowerCase(Locale.ROOT).contains("recenzj"))).isEmpty();
    }

    private static Properties load(String name) throws IOException {
        Properties p = new Properties();
        try (var r = new InputStreamReader(Files.newInputStream(Path.of("src/main/resources", name)), StandardCharsets.UTF_8)) { p.load(r); }
        return p;
    }

    @Test
    void receiptTextsNameTheReceiptSystemNotTheSupplier() throws IOException {
        // given: on the order page "Dostawca" is the wholesaler; the e-receipt's provider is the e-receipt system
        Properties pl = load("messages_pl.properties"), en = load("messages_en.properties");

        // when
        List<String> supplierPanel = pl.stringPropertyNames().stream()
                .filter(k -> k.startsWith("receipts."))
                .filter(k -> pl.getProperty(k).contains("panelu dostawcy") || pl.getProperty(k).contains("panel dostawcy")
                        || pl.getProperty(k).contains("pod nowym kluczem"))
                .toList();
        List<String> providerPanel = en.stringPropertyNames().stream()
                .filter(k -> k.startsWith("receipts."))
                .filter(k -> en.getProperty(k).contains("provider's panel") || en.getProperty(k).contains("under a new key"))
                .toList();

        // then
        assertThat(supplierPanel).isEmpty();
        assertThat(providerPanel).isEmpty();
        assertThat(pl.getProperty("receipts.action.reissue.confirm.message")).contains("systemie e-paragonów");
        assertThat(pl.getProperty("receipts.action.close.help")).contains("systemie e-paragonów");
    }

    /** The keys of the page and its e-receipt row, without the bell's texts (receipts.attention.*, kept as they were). */
    private static List<String> pageKeys(Properties bundle) throws IOException {
        Set<String> keys = new TreeSet<>();
        for (Path p : sources().toList()) {
            if (!Files.exists(p)) continue;
            String text = Files.readString(p);
            for (Pattern pattern : List.of(KEY, QUOTED)) {
                Matcher m = pattern.matcher(text);
                while (m.find()) {
                    if (!m.group(1).endsWith(".")) keys.add(m.group(1));
                }
            }
        }
        bundle.stringPropertyNames().stream()
                .filter(k -> k.startsWith("receipts.") && !k.startsWith("receipts.attention."))
                .forEach(keys::add);
        return keys.stream().filter(bundle::containsKey).toList();
    }

    @Test
    void rowDescriptionsStartWithACapital() throws IOException {
        // given: texts that open the description line of a list row (the parts after a " · " stay lower case)
        Properties pl = load("messages_pl.properties"), en = load("messages_en.properties");
        List<String> leads = List.of("order.documents.issued", "order.shipments.pickup", "receipts.row.fiscalised",
                "receipts.row.closed", "order.history.review.none", "order.payments.empty", "order.documents.empty");

        // then
        for (Properties bundle : List.of(pl, en)) {
            for (String key : leads) {
                String value = bundle.getProperty(key);
                assertThat(Character.isUpperCase(value.charAt(0))).as(key + " = " + value).isTrue();
            }
        }
        assertThat(pl.getProperty("order.review.status.none")).isEqualTo("— nie jest zbierana —");
        assertThat(pl.getProperty("order.payments.remove.locked.pending")).startsWith("Oczekiwana wpłata");
    }

    @Test
    void oneTermForEmailAndEReceipt() throws IOException {
        // given
        Properties pl = load("messages_pl.properties");
        Pattern bareMail = Pattern.compile("(?i)(?<![-\\p{L}])maila?\\b|(?<![-\\p{L}])mailem\\b");

        // when
        List<String> offending = pageKeys(pl).stream()
                .filter(k -> bareMail.matcher(pl.getProperty(k)).find()
                        || pl.getProperty(k).contains("paragonu/faktury")
                        || pl.getProperty(k).contains("paragon albo fakturę")
                        || pl.getProperty(k).contains("zestawu nie rozdzielisz")
                        || pl.getProperty(k).contains("sprzedaż POS"))
                .map(k -> k + " = " + pl.getProperty(k))
                .toList();

        // then: "e-mail", "fakturę albo paragon", "zestawu nie podzielisz" (the menu says "Podziel zestaw")
        assertThat(offending).isEmpty();
        assertThat(pl.getProperty("receipts.action.close.title")).isEqualTo("Zamknij e-paragon ręcznie");
    }

    @Test
    void englishUsesTypographicQuotesAndDashes() throws IOException {
        // given
        Properties en = load("messages_en.properties");

        // when: ASCII quotes, a hyphen standing for a dash, a doubled apostrophe or "e-mail" beside "email"
        List<String> offending = pageKeys(en).stream()
                .filter(k -> en.getProperty(k).contains("\"") || en.getProperty(k).contains(" - ")
                        || en.getProperty(k).contains("''") || en.getProperty(k).toLowerCase(Locale.ROOT).contains("e-mail"))
                .map(k -> k + " = " + en.getProperty(k))
                .toList();

        // then
        assertThat(offending).isEmpty();
        assertThat(en.getProperty("order.item.form.service.locked"))
                .isEqualTo("The item has a supplier — you can no longer switch it to a service.");
        assertThat(en.getProperty("order.page.action.dropship")).isEqualTo(en.getProperty("deliveries.purchase.button"));
    }
}
