package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ClientPagesMessagesTest {

    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final Pattern MESSAGE_REFERENCE = Pattern.compile("#\\{([a-zA-Z0-9._-]+)");
    /** Keys handed to fragments as plain string arguments, e.g. a field label or an error summary title. */
    private static final Pattern KEY_LITERAL = Pattern.compile("'((?:client\\.offer|client\\.order|order\\.address|public|payment\\.bridge)\\.[a-zA-Z0-9._-]*[a-zA-Z0-9_-])'");
    private static final Pattern LONE_APOSTROPHE = Pattern.compile("(?<!')'(?!')");
    private static final Pattern DOUBLED_APOSTROPHE = Pattern.compile("''");

    /** Keys the templates compose at render time or that Java code resolves; they cannot be read from a template. */
    private static final List<String> EXPLICIT_KEYS = List.of(
            "public.lang.pl", "public.lang.en",
            "client.offer.notice.saved", "client.offer.notice.payment.failed",
            "client.offer.notice.proforma.failed", "client.offer.notice.proforma.sent",
            "client.offer.error.name.required", "client.offer.error.surname.required",
            "client.offer.error.company.required", "client.offer.error.taxId.required",
            "client.offer.error.street.required", "client.offer.error.postalCode.required",
            "client.offer.error.city.required", "client.offer.error.country.required",
            "client.offer.error.email.required", "client.offer.error.phone.required",
            "client.offer.error.taxId.invalid", "client.offer.error.postalCode.invalid",
            "client.offer.error.country.invalid", "client.offer.error.email.invalid",
            "client.offer.error.phone.invalid",
            "client.offer.error.shipping.name.required", "client.offer.error.shipping.surname.required",
            "client.offer.error.shipping.street.required", "client.offer.error.shipping.postalCode.required",
            "client.offer.error.shipping.city.required", "client.offer.error.shipping.email.required",
            "client.offer.error.shipping.phone.required", "client.offer.error.shipping.postalCode.invalid",
            "client.offer.error.shipping.email.invalid", "client.offer.error.shipping.phone.invalid",
            "client.offer.error.shipping.country",
            "client.order.headline.accepted", "client.order.headline.assembly", "client.order.headline.preparation",
            "client.order.headline.shipping", "client.order.headline.collection", "client.order.headline.collected",
            "client.order.headline.delivered", "client.order.headline.cancelled",
            "client.order.lead.accepted", "client.order.lead.assembly", "client.order.lead.assembly.awaiting",
            "client.order.lead.assembly.partial", "client.order.lead.preparation", "client.order.lead.shipping",
            "client.order.lead.collection", "client.order.lead.delivered",
            "ClientOrderStage.Accepted", "ClientOrderStage.Assembly", "ClientOrderStage.Preparation",
            "ClientOrderStage.Shipping", "ClientOrderStage.Delivered", "ClientOrderStage.Accepted.collection",
            "ClientOrderStage.Assembly.collection", "ClientOrderStage.Preparation.collection",
            "ClientOrderStage.Shipping.collection", "ClientOrderStage.Delivered.collection",
            "ClientOrderItemStatus.Allocation", "ClientOrderItemStatus.AwaitingDelivery", "ClientOrderItemStatus.Assembled",
            "ClientOrderItemStatus.InClaim", "ClientOrderItemStatus.Returned",
            "ShipmentType.Courier", "ShipmentType.PickupPoint", "ShipmentType.PersonalCollection");

    @Test
    void everyKeyTheClientPagesUseExistsInBothLanguages() throws Exception {
        // given
        Set<String> used = usedKeys();
        Properties polish = load("messages_pl.properties");
        Properties english = load("messages_en.properties");

        // when
        Set<String> missingInPolish = used.stream().filter(key -> !polish.containsKey(key)).collect(Collectors.toCollection(TreeSet::new));
        Set<String> missingInEnglish = used.stream().filter(key -> !english.containsKey(key)).collect(Collectors.toCollection(TreeSet::new));

        // then
        assertThat(used).isNotEmpty();
        assertThat(missingInPolish).as("missing in messages_pl").isEmpty();
        assertThat(missingInEnglish).as("missing in messages_en").isEmpty();
    }

    @Test
    void clientPageMessagesWithPlaceholdersDoubleApostrophesAndOthersDoNot() throws Exception {
        // given
        Set<String> used = usedKeys();
        Set<String> broken = new TreeSet<>();

        // when
        for (String file : List.of("messages_pl.properties", "messages_en.properties")) {
            Properties bundle = load(file);
            for (String key : used) {
                String text = bundle.getProperty(key);
                if (text == null) {
                    continue;
                }
                boolean hasPlaceholder = text.contains("{");
                if (hasPlaceholder && LONE_APOSTROPHE.matcher(text).find()
                        || !hasPlaceholder && DOUBLED_APOSTROPHE.matcher(text).find()) {
                    broken.add(file + ": " + key);
                }
            }
        }

        // then
        assertThat(broken).isEmpty();
    }

    private static Set<String> usedKeys() throws IOException {
        Set<String> keys = new TreeSet<>(EXPLICIT_KEYS);
        Path templates = RESOURCES.resolve("templates");
        List<Path> files = Stream.of(
                        Stream.of(templates.resolve("public-layout.html"), templates.resolve("clientOffer.html"),
                                templates.resolve("clientOrder.html"),
                                templates.resolve("payments/payment-bridge.html")),
                        listHtml(templates.resolve("client-offer")), listHtml(templates.resolve("client-order")))
                .flatMap(s -> s)
                .toList();
        for (Path file : files) {
            String html = Files.readString(file, StandardCharsets.UTF_8);
            collect(MESSAGE_REFERENCE, html, keys);
            collect(KEY_LITERAL, html, keys);
        }
        return keys;
    }

    private static Stream<Path> listHtml(Path directory) throws IOException {
        try (Stream<Path> listing = Files.list(directory)) {
            return listing.filter(path -> path.toString().endsWith(".html")).toList().stream();
        }
    }

    private static void collect(Pattern pattern, String html, Set<String> keys) {
        Matcher matcher = pattern.matcher(html);
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
    }

    private static Properties load(String file) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(RESOURCES.resolve(file), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }
}
