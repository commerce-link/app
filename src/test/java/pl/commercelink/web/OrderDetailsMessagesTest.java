package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.orders.*;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.warehouse.api.ItemCondition;
import pl.commercelink.web.orders.OrderLabels;

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
    private static final Pattern QUOTED = Pattern.compile("\"(order\\.(page|closing|card|items|item|bulk|shipments|documents|payments|payment|status|settings|finances|customer|history|event|review|move|address)\\.[a-zA-Z0-9_.]+)\"");

    static Stream<Path> sources() throws IOException {
        List<Path> files = new ArrayList<>();
        // Task 6 creates this directory; Files.walk itself (unlike the per-file Files.exists guard below) throws on a missing root.
        Path detailsDir = Path.of("src/main/resources/templates/orders/details");
        if (Files.exists(detailsDir)) {
            try (Stream<Path> t = Files.walk(detailsDir)) { t.filter(Files::isRegularFile).forEach(files::add); }
        }
        files.add(Path.of("src/main/resources/templates/orders/details.html"));
        files.add(Path.of("src/main/resources/templates/orders/status.html"));
        files.add(Path.of("src/main/resources/templates/orders/settings.html"));
        files.add(Path.of("src/main/resources/templates/orders/parts.html"));
        try (Stream<Path> j = Files.walk(Path.of("src/main/java/pl/commercelink/web/orders"))) { j.filter(p -> p.toString().endsWith(".java")).forEach(files::add); }
        return files.stream();
    }

    @Test
    void everyKeyUsedByTheDetailsPageExistsInBothBundles() throws IOException {
        Properties pl = load("messages_pl.properties"), en = load("messages_en.properties");
        Set<String> missing = new TreeSet<>();
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
        assertThat(missing).isEmpty();
    }

    @Test
    void everyEnumValueTheLabelsResolveHasAKey() throws IOException {
        Properties pl = load("messages_pl.properties"), en = load("messages_en.properties");
        List<String> keys = new ArrayList<>();
        for (OrderStatus s : OrderStatus.values()) { keys.add(OrderLabels.status(s)); keys.add("order.status.effect." + s.name()); }
        for (FulfilmentStatus s : FulfilmentStatus.values()) keys.add(OrderLabels.itemStatus(s));
        for (OrderSourceType s : OrderSourceType.values()) keys.add(OrderLabels.sourceType(s));
        for (FulfilmentType s : FulfilmentType.values()) keys.add(OrderLabels.fulfilmentType(s));
        for (ShipmentType s : ShipmentType.values()) keys.add(OrderLabels.shipmentType(s));
        for (DocumentType s : DocumentType.values()) keys.add(OrderLabels.documentType(s));
        for (PaymentSource s : PaymentSource.values()) keys.add(OrderLabels.paymentSource(s));
        for (PaymentDirection s : PaymentDirection.values()) keys.add(OrderLabels.paymentDirection(s));
        for (OrderReviewStatus s : OrderReviewStatus.values()) keys.add(OrderLabels.reviewStatus(s));
        for (ItemCondition s : ItemCondition.values()) keys.add(OrderLabels.condition(s));
        for (ShipmentTrackingStatus s : ShipmentTrackingStatus.values()) keys.add(OrderLabels.tracking(s));
        assertThat(keys).allSatisfy(k -> { assertThat(pl).containsKey(k); assertThat(en).containsKey(k); });
    }

    private static Properties load(String name) throws IOException {
        Properties p = new Properties();
        try (var r = new InputStreamReader(Files.newInputStream(Path.of("src/main/resources", name)), StandardCharsets.UTF_8)) { p.load(r); }
        return p;
    }
}
