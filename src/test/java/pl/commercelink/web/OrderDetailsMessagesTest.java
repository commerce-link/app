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
        files.add(Path.of("src/main/resources/templates/orders/parts.html"));
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
}
